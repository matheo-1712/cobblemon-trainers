package matheo1712.cobbletrainers.trainers

import com.cobblemon.mod.common.entity.npc.NPCEntity
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.dialogue.TrainerDialogue
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Summoning a trainer from the battle phone, and everything that follows from it.
 *
 * The mod never spawns a trainer on its own: one stands where an operator placed it - a
 * command or a trainer spawner block - or it comes because a player asked for it here. That
 * asking is what a [TrainerLocation] block opens up, and a trainer without one is never
 * callable at all.
 *
 * What a call costs a player is being in the right place, nothing else. What it is worth is
 * bounded instead: **one called trainer per player at a time**, and no second copy of the same
 * trainer within [CROWD_RADIUS] of the first.
 *
 * Points worth not rediscovering:
 * - **Every check is redone here**, though the phone greys out the button it knows about. The
 *   request comes from a client, so its word that the trainer is callable, unlocked and listed
 *   is worth nothing.
 * - **The place is checked once, when the button is pressed.** A player who walks out of the
 *   desert while the trainer is arriving keeps them: the condition is on the call, not on the
 *   trainer standing there.
 * - **Nothing here is saved.** A call is a few minutes of a session, so the record lives in
 *   memory and a restart forgets it. What that leaves behind - a trainer standing in a chunk
 *   whose call nobody remembers - is swept up by [discardOrphans].
 * - **A call ends when its caller leaves**, which is what keeps a called trainer out of the
 *   world save: `PlayerList.removeAll` runs before the world is written, so quitting a solo
 *   world takes the trainer along instead of leaving it standing there forever. See [sendHome].
 * - **A trainer in a battle is never taken away** while the caller is still playing. Removing
 *   an actor mid-battle is what [matheo1712.cobbletrainers.battle.TrainerBattleEventHandler]
 *   has to clean up after, and there is no reason to cause it on purpose - but a battle whose
 *   player has gone is not a battle anyone is playing.
 */
object TrainerCalls {

    /**
     * A trainer standing in the world because a player asked for them.
     *
     * The entity is held directly rather than by UUID: a lookup by UUID cannot tell a trainer
     * that died from one whose chunk unloaded, and those two want opposite answers.
     */
    private class Call(
        val playerId: UUID,
        val trainerId: ResourceLocation,
        val entity: NPCEntity
    ) {
        /** Game time the trainer leaves at, set once their battle is over. Null while they wait. */
        var dismissAt: Long? = null
    }

    /** One call per player, which is what makes "call someone else" mean "send this one home". */
    private val calls = mutableMapOf<UUID, Call>()

    /** Game time a trainer starts answering that player again, after being killed by someone. */
    private val sulking = mutableMapOf<Pair<UUID, ResourceLocation>, Long>()

    /**
     * Called trainers read back from the save, waiting for the next tick to be looked at.
     *
     * The load event fires from inside the entity's own tracking setup - `addFreshEntity` and
     * a chunk coming back both go through it - so nothing is removed there. Waiting a tick
     * costs nothing visible and answers both halves at once: the entity is by then a normal
     * entity of the world, and a trainer being called has had its call written down.
     */
    private val pendingOrphans = mutableListOf<NPCEntity>()

    private val LOGGER = CobblemonTrainers.LOGGER

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server -> tick(server) }
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ -> onDeath(entity) }
        ServerEntityEvents.ENTITY_LOAD.register { entity, _ -> queueOrphan(entity) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> sendHome(handler.player.uuid) }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            calls.clear()
            sulking.clear()
            pendingOrphans.clear()
        }
    }

    /**
     * Answers a player pressing the call button, and tells them how it went either way.
     *
     * Every refusal is a message, never silence: a button that does nothing is indistinguishable
     * from a broken one.
     */
    fun call(player: ServerPlayer, trainerId: ResourceLocation) {
        // Callability is the location block naming a *place*, never merely existing: a block
        // holding only a label says where the trainer is without offering to fetch them.
        val definition = TrainerRegistry.get(trainerId)
            ?.takeIf { it.progress.listed && !TrainerLock.isHiddenFrom(player, trainerId, it) }
        if (definition == null) {
            deny(player, CobblemonTrainers.lang("chat.call.unknown"))
            return
        }

        val name = Component.translatable(definition.name)

        if (!definition.callable()) {
            deny(player, CobblemonTrainers.lang("chat.call.not_callable", name))
            return
        }

        val missing = TrainerLock.unmet(player, trainerId, definition)
        if (missing.isNotEmpty()) {
            player.sendSystemMessage(TrainerLock.refusal(definition, missing))
            return
        }

        if (!definition.progress.allowsRematch &&
            TrainerProgress.of(player.server).hasDefeated(trainerId, player.uuid)
        ) {
            deny(player, CobblemonTrainers.lang("chat.already_defeated", name))
            return
        }

        // Refused rather than silently orphaning the previous one: dismiss() rightly refuses to
        // remove a trainer mid-battle, so overwriting the record here would leave an entity
        // nothing tracks any more - and it would never be sent home.
        calls[player.uuid]?.takeIf { it.entity.battleIds.isNotEmpty() }?.let {
            deny(player, CobblemonTrainers.lang("chat.call.in_battle"))
            return
        }

        val level = player.serverLevel()
        remainingSulk(player.uuid, trainerId, level)?.let { seconds ->
            deny(player, CobblemonTrainers.lang("chat.call.sulking", name, seconds))
            return
        }

        val wrongPlace = TrainerPlace.unmet(level, player.blockPosition(), definition.location)
        if (wrongPlace.isNotEmpty()) {
            player.sendSystemMessage(wrongPlaceMessage(name, wrongPlace))
            return
        }

        if (alreadyNearby(level, player, trainerId)) {
            val line = definition.location?.busy?.takeIf { it.isNotBlank() }
                ?.let { Component.translatable(it) }
                ?: CobblemonTrainers.lang("chat.call.busy")
            player.sendSystemMessage(CobblemonTrainers.lang("chat.trainer_message", name, line))
            return
        }

        val spot = findSpot(level, player.blockPosition())
        if (spot == null) {
            deny(player, CobblemonTrainers.lang("chat.call.no_room", name))
            return
        }

        // Only now: a refused call must leave the trainer already standing there alone.
        dismiss(player.uuid)

        val npc = spawnFor(player, level, spot, definition, trainerId)
        if (npc == null) {
            deny(player, CobblemonTrainers.lang("chat.call.failed", name))
            return
        }

        calls[player.uuid] = Call(player.uuid, trainerId, npc)

        val arrival = definition.location?.arrival?.takeIf { it.isNotBlank() }
            ?.let { Component.translatable(it, spot.x.toInt(), spot.y.toInt(), spot.z.toInt()) }
            ?: CobblemonTrainers.lang(
                "chat.call.arrival", spot.x.toInt(), spot.y.toInt(), spot.z.toInt()
            )
        player.sendSystemMessage(CobblemonTrainers.lang("chat.trainer_message", name, arrival))

        LOGGER.debug(
            "{} called trainer {} to {}, {}, {}",
            player.gameProfile.name, trainerId, spot.x.toInt(), spot.y.toInt(), spot.z.toInt()
        )
    }

    /**
     * Called when a battle ends, so a trainer who came when called leaves once they are done.
     *
     * A delay rather than a vanishing: the losing and winning lines are sent by the battle
     * handler at that exact moment, and a trainer who disappears while speaking reads as a bug.
     */
    fun dismissAfterBattle(npc: NPCEntity) {
        val call = calls.values.firstOrNull { it.entity === npc } ?: return
        call.dismissAt = npc.level().gameTime + DISMISS_DELAY_TICKS
    }

    /** Sends a player's called trainer home, if they have one and it is not mid-battle. */
    private fun dismiss(playerId: UUID) {
        val call = calls[playerId] ?: return
        if (call.entity.battleIds.isNotEmpty()) return

        calls.remove(playerId)
        if (!call.entity.isRemoved) call.entity.discard()
    }

    /**
     * Ends the call of a player who is no longer there, battle or no battle.
     *
     * The rule sparing a trainer mid-battle is about not pulling an actor out from under a
     * player who is playing; a battle whose player has left is not that. The removal is what
     * [matheo1712.cobbletrainers.battle.TrainerBattleEventHandler] turns into a clean stop, on
     * the unload event it already watches.
     *
     * Doing it the moment the player disconnects, rather than leaving it to [tick], is what
     * keeps a called trainer out of the world save: `MinecraftServer.stopServer` removes its
     * players before it writes the worlds, so a solo player quitting - mid-battle included -
     * takes their trainer with them instead of leaving one standing there for good.
     */
    private fun sendHome(playerId: UUID) {
        val call = calls.remove(playerId) ?: return
        if (!call.entity.isRemoved) call.entity.discard()
    }

    /**
     * Spawns the trainer, marked with the caller so the entity itself says who it belongs to.
     *
     * The aspect is saved to NBT like every other one, which is what lets [discardOrphans]
     * recognise a called trainer whose call was forgotten by a restart.
     */
    private fun spawnFor(
        player: ServerPlayer,
        level: ServerLevel,
        spot: Vec3,
        definition: TrainerDefinition,
        trainerId: ResourceLocation
    ): NPCEntity? {
        // Facing the caller, so the first thing a player sees is a trainer looking at them.
        val yRot = facing(spot, player.position())
        val marker = CobblemonTrainers.CALL_ASPECT_PREFIX + player.uuid

        return TrainerSpawner.spawn(
            server = player.server,
            level = level,
            position = spot,
            definition = definition,
            trainerId = trainerId,
            yRot = yRot,
            extraAspects = listOf(marker)
        )
    }

    /** Degrees the trainer turns to look from [from] towards [to]. */
    private fun facing(from: Vec3, to: Vec3): Float =
        (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0).toFloat()

    /**
     * Whether that trainer is already standing somewhere around, called or placed by a block.
     *
     * Only loaded entities are seen, so a copy sitting in an unloaded chunk far away does not
     * block a call - which is the point: the rule is about two of the same person being visible
     * at once, not about counting them world-wide.
     */
    private fun alreadyNearby(level: ServerLevel, player: ServerPlayer, trainerId: ResourceLocation): Boolean {
        val box = AABB.ofSize(player.position(), CROWD_RADIUS * 2, CROWD_RADIUS * 2, CROWD_RADIUS * 2)
        return level.getEntitiesOfClass(NPCEntity::class.java, box) { npc ->
            TrainerRegistry.idFromAspects(npc.aspects) == trainerId
        }.isNotEmpty()
    }

    /**
     * Somewhere the trainer can stand, a walk away from the player rather than under their nose.
     *
     * Three passes, in the order the result is preferred.
     *
     * 1. Scattered attempts through the ring the trainer is meant to arrive in, at a random
     *    angle and distance each time, so two calls in the same clearing do not put them on the
     *    same rock.
     * 2. **Closer**, ring by ring, when that ring holds nowhere to stand. A player in a
     *    clearing surrounded by water or cliffs has ground right next to them and none at
     *    twenty blocks, and arriving near is better than arriving over the ridge.
     * 3. Further out, as a last resort - down a ravine, on a small island - because refusing
     *    the call is worse than a longer walk.
     */
    private fun findSpot(level: ServerLevel, origin: BlockPos): Vec3? {
        val random = level.random

        repeat(SCATTER_ATTEMPTS) {
            val radius = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1)
            val angle = random.nextDouble() * 2 * Math.PI
            spotAt(level, origin, radius.toDouble(), angle)?.let { return it }
        }

        // Inwards first, and nearest last: the closest ring that works wins, but the trainer is
        // never put right on top of the player - CLOSEST_DISTANCE keeps a couple of blocks.
        var radius = MIN_DISTANCE - RING_STEP
        while (radius >= CLOSEST_DISTANCE) {
            ringSpot(level, origin, radius)?.let { return it }
            radius -= RING_STEP
        }

        radius = MAX_DISTANCE + RING_STEP
        while (radius <= MAX_SEARCH_DISTANCE) {
            ringSpot(level, origin, radius)?.let { return it }
            radius += RING_STEP
        }
        return null
    }

    /** Walks one ring of candidate angles, starting somewhere random so a ring is not a rut. */
    private fun ringSpot(level: ServerLevel, origin: BlockPos, radius: Int): Vec3? {
        val offset = level.random.nextInt(RING_ANGLES)
        for (step in 0 until RING_ANGLES) {
            val angle = 2 * Math.PI * ((offset + step) % RING_ANGLES) / RING_ANGLES
            spotAt(level, origin, radius.toDouble(), angle)?.let { return it }
        }
        return null
    }

    private fun spotAt(level: ServerLevel, origin: BlockPos, radius: Double, angle: Double): Vec3? {
        val x = origin.x + (cos(angle) * radius).roundToInt()
        val z = origin.z + (sin(angle) * radius).roundToInt()
        val standing = standingSpot(level, x, z, origin.y) ?: return null
        return Vec3(x + 0.5, standing.y.toDouble(), z + 0.5)
    }

    /**
     * The nearest spot in that column a trainer could stand on, measured from the caller's own
     * altitude outwards.
     *
     * Not the surface height: a player in a cave would get a trainer waiting on the roof above
     * them, several dozen blocks and a wall away. Searching around the caller's own Y answers
     * the cave and the open field with one rule.
     */
    private fun standingSpot(level: ServerLevel, x: Int, z: Int, aroundY: Int): BlockPos? {
        // Never generate or load a chunk to answer a button press. Asked in chunk coordinates:
        // both hasChunkAt overloads are deprecated, hasChunk is the one that is not.
        if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) return null

        for (offset in 0..VERTICAL_SEARCH) {
            val candidates = if (offset == 0) listOf(aroundY) else listOf(aroundY + offset, aroundY - offset)
            candidates.forEach { y ->
                if (y > level.minBuildHeight && y < level.maxBuildHeight - 1) {
                    val pos = BlockPos(x, y, z)
                    if (canStand(level, pos)) return pos
                }
            }
        }
        return null
    }

    /**
     * Solid dry ground underfoot, and room for a body above it.
     *
     * **Never in a fluid**, and that takes three tests rather than one. The feet and the head
     * are the obvious two. The third is the ground itself: a waterlogged slab, a stair under the
     * surface, a block of ice sitting in a lake are all sturdy enough to stand on and all put
     * the trainer in the water. Testing only the two blocks above would have left the trainer
     * standing on the sea, which is what this rules out.
     */
    private fun canStand(level: ServerLevel, pos: BlockPos): Boolean {
        val ground = pos.below()
        val groundState = level.getBlockState(ground)
        if (!groundState.isFaceSturdy(level, ground, Direction.UP)) return false
        if (!groundState.fluidState.isEmpty) return false
        if (!level.getFluidState(pos).isEmpty || !level.getFluidState(pos.above()).isEmpty) return false

        // A collision test rather than an air test: grass, flowers and snow are not obstacles,
        // and demanding air would push the trainer out of every meadow.
        val body = AABB(
            pos.x + 0.5 - BODY_HALF_WIDTH, pos.y.toDouble(), pos.z + 0.5 - BODY_HALF_WIDTH,
            pos.x + 0.5 + BODY_HALF_WIDTH, pos.y + BODY_HEIGHT, pos.z + 0.5 + BODY_HALF_WIDTH
        )
        return level.noCollision(body)
    }

    /**
     * Sends home the trainers whose call is over: the caller left, went too far, or the battle
     * they came for has finished.
     */
    private fun tick(server: MinecraftServer) {
        discardOrphans()
        if (calls.isEmpty()) return

        val time = server.overworld().gameTime
        if (time % CHECK_INTERVAL_TICKS != 0L) return

        calls.entries.removeIf { (playerId, call) ->
            val entity = call.entity
            // Removed covers a chunk unload as well as a discard. Either way the record is
            // stale; a leftover entity is caught by discardOrphans when its chunk comes back.
            if (entity.isRemoved) return@removeIf true

            // Asked before the battle: a battle only outranks the rest while someone is still
            // playing it. Disconnecting has already gone through sendHome by now, so this is
            // the net under it rather than the rule itself.
            val player = server.playerList.getPlayer(playerId)
            if (player == null) {
                entity.discard()
                return@removeIf true
            }

            // The dialogue box draws its portrait from the entity, so a trainer removed while
            // their caller is still reading leaves an empty frame in front of them. The check
            // only ever postpones: closing the box, or leaving, drops it on the next pass.
            val dismissAt = call.dismissAt
            if (dismissAt != null && time >= dismissAt && !TrainerDialogue.isTalking(player, entity)) {
                entity.discard()
                return@removeIf true
            }

            // A battle its caller is playing is never interrupted, whatever else they do.
            if (entity.battleIds.isNotEmpty()) return@removeIf false

            val gone = player.level() !== entity.level() ||
                player.distanceToSqr(entity) > LEASH_DISTANCE * LEASH_DISTANCE
            if (gone) entity.discard()
            gone
        }
    }

    /**
     * A called trainer killed by anything at all costs their caller the call, and holds it
     * against them for [SULK_TICKS].
     *
     * That delay is the only thing standing between the mod and a trainer farmed with a sword
     * rather than fought - and it is short enough that losing one to a creeper is an annoyance,
     * not a punishment.
     */
    private fun onDeath(entity: Entity) {
        if (entity !is NPCEntity) return
        val trainerId = TrainerRegistry.idFromAspects(entity.aspects) ?: return
        val callerId = callerOf(entity) ?: return

        calls.remove(callerId)
        sulking[callerId to trainerId] = entity.level().gameTime + SULK_TICKS
    }

    /** Notes a called trainer that has just joined the world, for [discardOrphans] to judge. */
    private fun queueOrphan(entity: Entity) {
        if (entity !is NPCEntity) return
        if (callerOf(entity) == null) return
        pendingOrphans += entity
    }

    /**
     * Removes the called trainers nobody is waiting for any more.
     *
     * A call lives in memory, an entity lives on disk: quit a world while a trainer stands in a
     * loaded chunk and the two disagree. The entity carries the caller in its aspects, so the
     * rule is simply that a marked trainer with no matching call is a leftover - which is also
     * what clears the ones left behind by a version that let them reach the save file.
     *
     * Judged a tick after the entity joined rather than from inside the load event: taking an
     * entity back out of the world while it is half-way through being tracked is delicate, and
     * a trainer spawned for a fresh call has by then had its call written down - which is what
     * used to need a flag saying "one is being spawned right now".
     */
    private fun discardOrphans() {
        if (pendingOrphans.isEmpty()) return

        pendingOrphans.forEach { entity ->
            if (entity.isRemoved) return@forEach
            val callerId = callerOf(entity) ?: return@forEach
            if (calls[callerId]?.entity === entity) return@forEach

            LOGGER.debug("Removing a called trainer no call remembers: {}", entity.uuid)
            entity.discard()
        }
        pendingOrphans.clear()
    }

    /** The player a trainer was called by, read from the aspect applied at spawn time. */
    private fun callerOf(npc: NPCEntity): UUID? =
        npc.aspects.firstOrNull { it.startsWith(CobblemonTrainers.CALL_ASPECT_PREFIX) }
            ?.removePrefix(CobblemonTrainers.CALL_ASPECT_PREFIX)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    /** Seconds left before that trainer answers this player again, or null when they already do. */
    private fun remainingSulk(playerId: UUID, trainerId: ResourceLocation, level: ServerLevel): Int? {
        val until = sulking[playerId to trainerId] ?: return null
        val left = until - level.gameTime
        if (left <= 0) {
            sulking.remove(playerId to trainerId)
            return null
        }
        return ((left + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND).toInt()
    }

    /**
     * The trainer is elsewhere, and here is where. Built like a locked trainer's refusal, and
     * for the same reason: the lines are the conditions themselves, so the message never says
     * anything other than what was tested.
     */
    private fun wrongPlaceMessage(name: Component, missing: List<Component>): Component {
        val message: MutableComponent = CobblemonTrainers.lang("chat.call.wrong_place", name)
            .withStyle(ChatFormatting.GRAY)
        missing.forEach { line ->
            message.append(Component.literal("\n"))
            message.append(CobblemonTrainers.lang("requirement.line", line).withStyle(ChatFormatting.DARK_GRAY))
        }
        return message
    }

    private fun deny(player: ServerPlayer, message: MutableComponent) {
        player.sendSystemMessage(message.withStyle(ChatFormatting.GRAY))
    }

    private const val TICKS_PER_SECOND = 20L

    /** The ring a called trainer arrives in: far enough to walk to, close enough to find. */
    private const val MIN_DISTANCE = 10
    private const val MAX_DISTANCE = 20

    /**
     * How close the search falls back to when that ring holds nowhere to stand. Not zero: a
     * trainer materialising inside the player is worse than one standing three blocks away.
     */
    private const val CLOSEST_DISTANCE = 3

    /** How far the search widens once even the close rings hold nothing. */
    private const val MAX_SEARCH_DISTANCE = 64
    private const val RING_STEP = 2
    private const val RING_ANGLES = 12
    private const val SCATTER_ATTEMPTS = 24

    /** How far above and below the caller a column is searched, which is what answers a cave. */
    private const val VERTICAL_SEARCH = 8

    private const val BODY_HALF_WIDTH = 0.4
    private const val BODY_HEIGHT = 1.9

    /** No second copy of one trainer within this many blocks of a call. */
    private const val CROWD_RADIUS = 100.0

    /** Past this, the caller has left and the trainer stops waiting. */
    private const val LEASH_DISTANCE = 128.0

    /** Long enough to speak their closing line before leaving. */
    private const val DISMISS_DELAY_TICKS = 5 * TICKS_PER_SECOND

    /** Five minutes, after being killed rather than fought. */
    private const val SULK_TICKS = 5 * 60 * TICKS_PER_SECOND

    private const val CHECK_INTERVAL_TICKS = 20L
}
