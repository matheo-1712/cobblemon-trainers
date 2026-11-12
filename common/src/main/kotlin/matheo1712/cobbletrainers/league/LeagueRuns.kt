package matheo1712.cobbletrainers.league

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.status.PersistentStatus
import com.cobblemon.mod.common.util.activeDialogue
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.party
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.advancement.LeagueCompletedTrigger
import matheo1712.cobbletrainers.battle.TrainerBattleInteraction
import matheo1712.cobbletrainers.battle.TrainerBattleIntro
import matheo1712.cobbletrainers.platform.Platform
import matheo1712.cobbletrainers.trainers.TrainerCalls
import matheo1712.cobbletrainers.trainers.TrainerProgress
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerRewards
import matheo1712.cobbletrainers.trainers.TrainerSpawner
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The tournaments being played: one per player and one per league, from the moment they sign up with a league's
 * host to the last trainer beaten - or to the first battle that does not end in a win.
 *
 * A run is a small state machine, and [tick] is what moves it:
 *
 * 1. **Opening** - the next trainer has been spawned beside the player and their battle asked
 *    for. A versus screen may hold it a few seconds.
 * 2. **Battle** - it is on. Winning leads to 3; anything else ends the run.
 * 3. **Interlude** - a short breath, long enough for the battle screen to close and for the
 *    trainer to say their last word.
 * 4. **Talking** - the host's box is open: carry on, or forfeit. Carrying on leads back to 1
 *    with the next trainer; after the last one the box is the host's congratulations instead.
 *
 * **Nothing is healed along the way.** The team is noted as each battle leaves it and brought
 * back down to that before the next one opens. A ceiling, never a restore: whatever a player
 * manages to heal between two battles is taken back, and a Pokémon that got worse stays worse.
 *
 * **A battle that does not end in a win ends the run.** Cobblemon has no single "battle over"
 * event, so nothing is told here that one ended: a run in a battle that no longer exists, and
 * that no victory came to claim, is over.
 *
 * Runs live in memory. The trainer a run spawned carries the player in an aspect, like a
 * called trainer does, which is what lets [discardOrphans] take away one that a restart left
 * standing with no run behind it.
 */
object LeagueRuns {

    /** How long an opening may sit with neither a battle nor a versus screen before it failed. */
    private const val OPENING_GRACE_TICKS = 40

    /** How long a vanished battle is given for its victory to be reported. */
    private const val BATTLE_GONE_GRACE_TICKS = 20

    /**
     * Ticks between a win and the host's box. Longer than the second a trainer's own closing
     * words wait, so those open first and the box queues behind them.
     */
    private const val INTERLUDE_DELAY_TICKS = 30

    /**
     * Where the next trainer appears when the league has no arena, in blocks from the player
     * and in order of preference.
     */
    private val SPAWN_RADII = intArrayOf(4, 5, 3, 6)
    private const val SPAWN_ANGLES = 16

    /** A spot this far above or below the player is another floor, not the same room. */
    private const val SPAWN_MAX_RISE = 3

    private enum class Phase { OPENING, BATTLE, INTERLUDE, TALKING }

    /** How one Pokémon came out of a battle. */
    private class Mark(val health: Int, val status: PersistentStatus?, val pp: Map<String, Int>)

    private class Run(val playerId: UUID, val league: LeagueEntry, val host: NPCEntity) {
        /** How many trainers of the league are behind the player. */
        var step = 0
        var phase = Phase.OPENING

        /** Server tick the current phase began at. */
        var since = 0

        /** The trainer spawned for the current step, until the host's box takes them away. */
        var opponent: NPCEntity? = null
        var battle: UUID? = null

        /** Server tick the battle was first found missing, or null while it is there. */
        var goneAt: Int? = null

        /** The team as the last battle left it, by Pokémon UUID. Null before the first win. */
        var team: Map<UUID, Mark>? = null
    }

    private val runs = mutableMapOf<UUID, Run>()

    /** Tournament trainers read back from the save, judged on the next tick. See [TrainerCalls]. */
    private val pendingOrphans = mutableListOf<NPCEntity>()

    fun register() {
        // Leaving is forfeiting, and it takes the trainer out of the world before the save.
        Platform.current.onDisconnect { player -> runs[player.uuid]?.let(::end) }
        Platform.current.onEntityLoad { entity, _ -> queueOrphan(entity) }
    }

    // ---------------------------------------------------------------- signing up

    /**
     * What the host turns the player down with, or an empty list when they may enter.
     * Read-only: it is asked each time the host's box is built.
     */
    fun refusal(player: ServerPlayer, league: LeagueEntry): List<Component> {
        if (player.uuid in runs) return listOf(CobblemonTrainers.lang("chat.league.busy"))

        // One player at a time per league, whichever of its hosts they signed up with. Runs
        // end on disconnect, so a league is never held by somebody who is not there.
        val holder = runs.values.firstOrNull { it.league.id == league.id }
        if (holder != null) {
            val name = player.server.playerList.getPlayer(holder.playerId)?.displayName
                ?: Component.literal(holder.playerId.toString())
            return listOf(CobblemonTrainers.lang("chat.league.occupied", name))
        }

        val party = player.party()
        if (!party.any()) return listOf(CobblemonTrainers.lang("chat.no_pokemon"))
        if (party.none { !it.isFainted() }) return listOf(CobblemonTrainers.lang("chat.no_healthy_pokemon"))

        return emptyList()
    }

    /** The player said yes to the host: the first trainer comes. */
    fun start(player: ServerPlayer, league: LeagueEntry, host: NPCEntity) {
        if (refusal(player, league).isNotEmpty()) return

        val run = Run(player.uuid, league, host)
        runs[player.uuid] = run
        advance(run, player)
    }

    /** Whether that NPC is a trainer a tournament spawned, which only its own run may start. */
    fun isOpponent(npc: NPCEntity): Boolean = ownerOf(npc) != null

    // ---------------------------------------------------------------- the machine

    /** Spawns the trainer of the current step and asks for their battle. */
    private fun advance(run: Run, player: ServerPlayer) {
        val trainerId = run.league.members[run.step]
        val definition = TrainerRegistry.get(trainerId)
            ?: return fail(run, player, CobblemonTrainers.lang("chat.league.spawn_failed"))

        if (!lowerTeam(run, player)) {
            return fail(run, player, CobblemonTrainers.lang("chat.league.team_changed"))
        }

        val level = player.serverLevel()
        val spot = arenaSpot(run, player) ?: findSpot(level, player, run.host)
            ?: return fail(run, player, CobblemonTrainers.lang("chat.league.no_room"))

        val opponent = TrainerSpawner.spawn(
            server = player.server,
            level = level,
            position = spot,
            definition = definition,
            trainerId = trainerId,
            yRot = facing(spot, player.position()),
            extraAspects = listOf(CobblemonTrainers.LEAGUE_RUN_ASPECT_PREFIX + player.uuid)
        ) ?: return fail(run, player, CobblemonTrainers.lang("chat.league.spawn_failed"))

        run.opponent = opponent
        run.battle = null
        run.goneAt = null
        enter(run, Phase.OPENING, player.server)

        // The same door a player's own "Battle" button goes through: versus screen, level
        // scaling and Cobblemon's errors included. The trainer's requirements and rematch rule
        // are not asked - the league decides who they fight here, not they.
        TrainerBattleInteraction.startBattle(opponent, player, definition)
    }

    fun tick(server: MinecraftServer) {
        discardOrphans()
        if (runs.isEmpty()) return

        val now = server.tickCount
        for (run in runs.values.toList()) {
            val player = server.playerList.getPlayer(run.playerId)
            if (player == null) {
                end(run)
                continue
            }

            when (run.phase) {
                Phase.OPENING -> {
                    val waiting = player.isInBattle() || TrainerBattleIntro.isPending(player)
                    if (run.opponent?.isRemoved != false || (now - run.since > OPENING_GRACE_TICKS && !waiting)) {
                        fail(run, player)
                    }
                }

                Phase.BATTLE -> {
                    val battleId = run.battle
                    if (battleId != null && BattleRegistry.getBattle(battleId) != null) {
                        run.goneAt = null
                    } else {
                        val goneAt = run.goneAt ?: now.also { run.goneAt = it }
                        if (now - goneAt > BATTLE_GONE_GRACE_TICKS) fail(run, player)
                    }
                }

                Phase.INTERLUDE -> {
                    val free = !player.isInBattle() && player.activeDialogue == null
                    if (now - run.since >= INTERLUDE_DELAY_TICKS && free) openInterlude(run, player)
                }

                // A box closed without an answer - the escape key - is a player who wants to
                // get on with it.
                Phase.TALKING -> if (now - run.since > 1 && player.activeDialogue == null) advance(run, player)
            }
        }
    }

    /** The trainer just beaten leaves, and the host takes over. */
    private fun openInterlude(run: Run, player: ServerPlayer) {
        dismissOpponent(run)

        val league = run.league
        if (run.step >= league.members.size) return complete(run, player)

        val next = trainerName(league.members[run.step])
        if (run.host.isRemoved) {
            // Nobody left to ask the question: the tournament simply goes on.
            player.sendSystemMessage(
                CobblemonTrainers.lang(
                    "chat.league.progress",
                    Component.translatable(league.displayName), run.step, league.members.size, next
                ).withStyle(ChatFormatting.GOLD)
            )
            return advance(run, player)
        }

        enter(run, Phase.TALKING, player.server)
        LeagueDialogue.between(
            host = run.host,
            player = player,
            league = league,
            won = run.step,
            next = next,
            onContinue = { if (runs[player.uuid] === run && run.phase == Phase.TALKING) advance(run, player) },
            onForfeit = { if (runs[player.uuid] === run && run.phase == Phase.TALKING) fail(run, player) }
        )
    }

    private fun complete(run: Run, player: ServerPlayer) {
        runs.remove(run.playerId)
        val league = run.league

        val first = TrainerProgress.of(player.server).recordLeague(league.id, player.uuid)
        // After the record, like a trainer's victory.
        LeagueCompletedTrigger.trigger(player, league.id)

        player.sendSystemMessage(
            CobblemonTrainers.lang("chat.league.completed", Component.translatable(league.displayName))
                .withStyle(ChatFormatting.GOLD)
        )
        TrainerRewards.grant(player, league.definition.rewards, first)

        if (!run.host.isRemoved) LeagueDialogue.congratulate(run.host, player, league)
    }

    /** Ends a run short of its last trainer, telling the player - and why, when there is a why. */
    private fun fail(run: Run, player: ServerPlayer, reason: MutableComponent? = null) {
        end(run)
        reason?.let { player.sendSystemMessage(it.withStyle(ChatFormatting.GRAY)) }

        val league = run.league
        val own = league.definition.host.messages.lose.takeIf { it.isNotBlank() }
        val line = if (own != null) {
            CobblemonTrainers.lang("chat.trainer_message", Component.translatable(league.hostName), Component.translatable(own))
        } else {
            CobblemonTrainers.lang("chat.league.failed", Component.translatable(league.displayName))
        }
        player.sendSystemMessage(line.withStyle(ChatFormatting.GRAY))
    }

    /** Forgets a run and takes its trainer away, with nothing said. */
    private fun end(run: Run) {
        runs.remove(run.playerId)
        // Mid-battle included: the removal is what TrainerBattleEventHandler turns into a
        // clean stop, on the unload event it already watches.
        dismissOpponent(run)
    }

    private fun dismissOpponent(run: Run) {
        run.opponent?.takeIf { !it.isRemoved }?.discard()
        run.opponent = null
    }

    private fun enter(run: Run, phase: Phase, server: MinecraftServer) {
        run.phase = phase
        run.since = server.tickCount
    }

    // ---------------------------------------------------------------- battle events

    /** The battle against a tournament trainer has really opened. */
    fun battleStarted(player: ServerPlayer, npc: NPCEntity, battleId: UUID) {
        val run = runs[player.uuid]?.takeIf { it.opponent === npc } ?: return
        run.battle = battleId
        run.goneAt = null
        enter(run, Phase.BATTLE, player.server)
    }

    /** The player won that battle: one more trainer behind them. */
    fun battleWon(player: ServerPlayer, battleId: UUID) {
        val run = runs[player.uuid]?.takeIf { it.phase == Phase.BATTLE && it.battle == battleId } ?: return

        run.step++
        run.team = snapshot(player)
        enter(run, Phase.INTERLUDE, player.server)
    }

    /** The player lost that battle: said now, rather than once the battle is found missing. */
    fun battleLost(player: ServerPlayer, battleId: UUID) {
        val run = runs[player.uuid]?.takeIf { it.battle == battleId } ?: return
        fail(run, player)
    }

    // ---------------------------------------------------------------- no healing

    /**
     * Brings the team back down to what the last battle left.
     *
     * @return false when a Pokémon has joined since - a fresh one is exactly the heal a league
     *   forbids, under another name. Leaving one behind is allowed: it only makes it harder.
     */
    private fun lowerTeam(run: Run, player: ServerPlayer): Boolean {
        val team = run.team ?: return true
        val party = player.party().toList()
        if (party.any { it.uuid !in team }) return false

        var lowered = false
        for (pokemon in party) {
            if (lower(pokemon, team.getValue(pokemon.uuid))) lowered = true
        }

        if (lowered) {
            player.sendSystemMessage(CobblemonTrainers.lang("chat.league.carried").withStyle(ChatFormatting.GRAY))
        }
        return true
    }

    /** @return whether anything had to be taken back. */
    private fun lower(pokemon: Pokemon, mark: Mark): Boolean {
        var lowered = false

        if (pokemon.currentHealth > mark.health) {
            pokemon.currentHealth = mark.health
            lowered = true
        }

        if (mark.status != null && pokemon.status == null && mark.health > 0) {
            pokemon.applyStatus(mark.status)
            lowered = true
        }

        for (move in pokemon.moveSet) {
            val left = mark.pp[move.name] ?: continue
            if (move.currentPp > left) {
                move.currentPp = left
                lowered = true
            }
        }

        return lowered
    }

    private fun snapshot(player: ServerPlayer): Map<UUID, Mark> =
        player.party().associate { pokemon ->
            pokemon.uuid to Mark(
                health = pokemon.currentHealth,
                status = pokemon.status?.status,
                pp = pokemon.moveSet.associate { it.name to it.currentPp }
            )
        }

    // ---------------------------------------------------------------- where they stand

    /**
     * The place an operator set for this league's trainers, or null when there is none - or
     * none this player can be fought from.
     *
     * The spot is taken as written, with no search for free ground around it: whoever set it
     * was standing there. But it is only used within the distance a trainer battle survives
     * ([matheo1712.cobbletrainers.battle.TrainerBattleRange]), and in the player's own
     * dimension: a battle opened further away would be cut on its first measured tick, and the
     * tournament lost to where an arena was put rather than to a trainer. Out of reach, the
     * trainer comes to the player instead, as in a league with no arena.
     */
    private fun arenaSpot(run: Run, player: ServerPlayer): Vec3? {
        val arena = LeagueArenas.of(player.server).get(run.league.id) ?: return null
        if (arena.dimension != player.level().dimension()) return null

        val reach = Cobblemon.config.defaultFleeDistance.toDouble()
        return arena.position.takeIf { it.distanceTo(player.position()) < reach }
    }

    /**
     * Somewhere close for the next trainer to appear, on the player's own floor.
     *
     * The search starts on the side of the player facing away from the host and fans out from
     * there, so the trainer does not land on the character the player is talking to.
     */
    private fun findSpot(level: ServerLevel, player: ServerPlayer, host: NPCEntity): Vec3? {
        val origin = player.blockPosition()
        val away = atan2(player.z - host.z, player.x - host.x)

        for (radius in SPAWN_RADII) {
            for (step in 0 until SPAWN_ANGLES) {
                // 0, +1, -1, +2, -2... sixteenths of a turn either side of `away`.
                val turn = ((step + 1) / 2) * (if (step % 2 == 1) 1 else -1)
                val angle = away + turn * 2 * Math.PI / SPAWN_ANGLES
                val x = origin.x + (cos(angle) * radius).roundToInt()
                val z = origin.z + (sin(angle) * radius).roundToInt()

                val standing = TrainerCalls.standingSpot(level, x, z, origin.y) ?: continue
                if (abs(standing.y - origin.y) > SPAWN_MAX_RISE) continue
                return Vec3(x + 0.5, standing.y.toDouble(), z + 0.5)
            }
        }
        return null
    }

    /** Degrees a trainer turns to look from [from] towards [to]. */
    private fun facing(from: Vec3, to: Vec3): Float =
        (Math.toDegrees(atan2(to.z - from.z, to.x - from.x)) - 90.0).toFloat()

    // ---------------------------------------------------------------- leftovers

    private fun queueOrphan(entity: Entity) {
        if (entity is NPCEntity && ownerOf(entity) != null) pendingOrphans += entity
    }

    /**
     * Removes the tournament trainers no run is waiting on any more. A run lives in memory, an
     * entity on disk: quit a world mid-tournament and the two disagree. Judged a tick after the
     * entity joined, by which time a trainer spawned for a live run has been written down.
     */
    private fun discardOrphans() {
        if (pendingOrphans.isEmpty()) return

        pendingOrphans.forEach { entity ->
            if (entity.isRemoved) return@forEach
            val owner = ownerOf(entity) ?: return@forEach
            if (runs[owner]?.opponent === entity) return@forEach
            entity.discard()
        }
        pendingOrphans.clear()
    }

    private fun ownerOf(npc: NPCEntity): UUID? =
        npc.aspects.firstOrNull { it.startsWith(CobblemonTrainers.LEAGUE_RUN_ASPECT_PREFIX) }
            ?.removePrefix(CobblemonTrainers.LEAGUE_RUN_ASPECT_PREFIX)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun trainerName(trainerId: ResourceLocation): Component =
        TrainerRegistry.get(trainerId)?.name?.takeIf { it.isNotBlank() }?.let { Component.translatable(it) }
            ?: Component.literal(trainerId.toString())

    /** A world that closed takes its runs with it: the next one may hold other leagues. */
    fun clear() {
        runs.clear()
        pendingOrphans.clear()
    }
}
