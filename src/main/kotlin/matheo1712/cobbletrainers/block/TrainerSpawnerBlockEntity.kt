package matheo1712.cobbletrainers.block

import com.cobblemon.mod.common.entity.npc.NPCEntity
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.registry.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerSpawner
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * What a [TrainerSpawnerBlock] remembers, and the loop that keeps its trainer alive.
 *
 * The trainer is identified by its datapack ID rather than by the entity: the entity is only
 * ever a consequence, respawned whenever it is missing. [spawnedTrainer] holds the UUID of the
 * current one so it can be found again after a restart.
 *
 * The settings ride along into a structure for free — `StructureTemplate` saves and restores
 * block entity NBT — which is what makes a configured spawner reusable as part of a building.
 * What must *not* ride along is the running state, and that is why [spawnedTrainer] is only
 * trusted when the entity it names carries this very block's [spawnerAspect]: a spawner placed
 * from a structure inherits the UUID of the trainer of the block it was copied from, and would
 * otherwise adopt it — dragging someone else's trainer across the world instead of spawning
 * its own.
 */
class TrainerSpawnerBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(TrainerBlocks.TRAINER_SPAWNER_ENTITY, pos, state) {

    var trainerId: ResourceLocation? = null
        private set

    /** How far the trainer may drift from the block before it is brought back, in blocks. */
    var leashRadius: Int = DEFAULT_LEASH_RADIUS
        private set

    /** How long the block waits before putting a killed trainer back, in seconds. */
    var respawnDelaySeconds: Int = DEFAULT_RESPAWN_DELAY_SECONDS
        private set

    /** Yaw the trainer is spawned with, set from whoever placed the block. */
    var facing: Float = 0f
        set(value) {
            field = Mth.wrapDegrees(value)
            setChanged()
        }

    private var spawnedTrainer: UUID? = null

    /**
     * Game time at which the next spawn is due, or 0 when nothing is pending. Not saved: it is
     * a timer measured in a running world's game time, worth nothing in another one, and losing
     * it across a restart costs at most one delay.
     */
    private var respawnAt: Long = 0L

    /**
     * Whether this block has seen its trainer alive since the level loaded. It tells a trainer
     * that died — wait the full delay — from one that is merely not there yet, which is what a
     * block looks like right after its chunk loads or straight out of a structure.
     */
    private var seenAlive = false

    /** Keeps a trainer that no datapack provides from filling the log, once per block. */
    private var warnedUnknownTrainer = false

    /** Applies what the configuration screen sent back. */
    fun configure(trainerId: ResourceLocation?, leashRadius: Int, respawnDelaySeconds: Int) {
        val trainerChanged = trainerId != this.trainerId

        this.trainerId = trainerId
        this.leashRadius = leashRadius.coerceIn(MIN_LEASH_RADIUS, MAX_LEASH_RADIUS)
        this.respawnDelaySeconds = respawnDelaySeconds.coerceIn(0, MAX_RESPAWN_DELAY_SECONDS)

        // A block that now points somewhere else should not keep the trainer it used to hold:
        // clearing it makes the next tick spawn the new one, with no delay.
        if (trainerChanged) {
            warnedUnknownTrainer = false
            despawnTrainer()
            respawnAt = 0L
        }
        setChanged()
    }

    /**
     * Removes the trainer this block is responsible for, if it is loaded.
     *
     * The sweep on top of the UUID lookup catches a trainer whose block entity lost track of
     * it — a world copied without its entities, a chunk that unloaded at the wrong moment.
     */
    fun despawnTrainer() {
        val level = this.level as? ServerLevel ?: return
        spawnedTrainer?.let { (level.getEntity(it) as? NPCEntity)?.discard() }
        sweepOrphans(level)
        spawnedTrainer = null
    }

    fun serverTick(level: ServerLevel) {
        // Once a second is plenty: nothing here reacts to a single tick, and the entity lookup
        // and distance check would otherwise run on every spawner in every loaded chunk.
        if (level.gameTime % CHECK_INTERVAL_TICKS != 0L) return

        val id = trainerId ?: return
        val tracked = spawnedTrainer?.let { level.getEntity(it) as? NPCEntity }

        if (tracked != null && tracked.isAlive && spawnerAspect() in tracked.aspects) {
            // Found again — a pending respawn was a false alarm, most likely a chunk that had
            // not finished loading its entities.
            seenAlive = true
            respawnAt = 0L
            keepNear(tracked)
            return
        }

        // A trainer that is loaded but marked for another position belongs to the block this
        // one was copied from. Disown it and spawn our own, without waiting. The aspect has to
        // be what decides, not `isAlive`: our own trainer stays resolvable for the length of
        // its death animation, and disowning it there would skip the respawn delay.
        if (tracked != null && spawnerAspect() !in tracked.aspects) {
            spawnedTrainer = null
            respawnAt = 0L
            setChanged()
        }

        if (respawnAt == 0L && spawnedTrainer != null) {
            respawnAt = level.gameTime + if (seenAlive) respawnDelaySeconds * 20L else RELOAD_GRACE_TICKS
            return
        }
        if (level.gameTime < respawnAt) return

        spawn(level, id)
    }

    private fun spawn(level: ServerLevel, id: ResourceLocation) {
        val definition = TrainerRegistry.get(id)
        if (definition == null) {
            if (!warnedUnknownTrainer) {
                CobblemonTrainers.LOGGER.warn(
                    "Trainer spawner at {} points at unknown trainer {} — is its datapack loaded?",
                    worldPosition,
                    id
                )
                warnedUnknownTrainer = true
            }
            respawnAt = level.gameTime + RETRY_INTERVAL_TICKS
            return
        }
        warnedUnknownTrainer = false

        sweepOrphans(level)

        val npc = TrainerSpawner.spawn(
            server = level.server,
            level = level,
            position = spawnPosition(),
            definition = definition,
            trainerId = id,
            yRot = facing,
            extraAspects = setOf(spawnerAspect())
        )
        if (npc == null) {
            respawnAt = level.gameTime + RETRY_INTERVAL_TICKS
            return
        }

        spawnedTrainer = npc.uuid
        respawnAt = 0L
        seenAlive = true
        setChanged()
    }

    /**
     * Brings a trainer that has drifted too far back onto its block.
     *
     * A teleport rather than a fresh spawn: the trainer keeps whatever state it has, and the
     * player it might be talking to sees it walk back rather than blink out. A trainer in a
     * battle is left alone — yanking it away mid-fight is worse than letting it stand where it
     * is until the battle ends.
     */
    private fun keepNear(npc: NPCEntity) {
        if (npc.battleIds.isNotEmpty()) return

        val home = spawnPosition()
        if (npc.position().distanceToSqr(home) <= leashRadius.toDouble() * leashRadius) return

        // Dropping the path matters as much as the teleport: the NPC would otherwise resume
        // walking to wherever it was heading, and be dragged back again a second later.
        npc.navigation.stop()
        npc.teleportTo(home.x, home.y, home.z)
        npc.yRot = facing
        npc.yHeadRot = facing
        npc.yBodyRot = facing
    }

    /**
     * Discards every trainer around that belongs to this block. Called only when none is meant
     * to be left — right before spawning a fresh one, and when the block goes away.
     *
     * The aspect is written at spawn time and saved to NBT, so this also catches a trainer the
     * block lost track of: one that wandered into a chunk which unloaded while the spawner kept
     * ticking, and came back after its replacement was already standing there.
     */
    private fun sweepOrphans(level: ServerLevel) {
        val aspect = spawnerAspect()
        val reach = leashRadius.toDouble() * 2 + 2
        level.getEntitiesOfClass(NPCEntity::class.java, AABB.ofSize(spawnPosition(), reach, reach, reach)) {
            aspect in it.aspects
        }.forEach { it.discard() }
    }

    /** Bottom centre of the block: the trainer stands on whatever the block itself sits on. */
    private fun spawnPosition(): Vec3 =
        Vec3(worldPosition.x + 0.5, worldPosition.y.toDouble(), worldPosition.z + 0.5)

    private fun spawnerAspect(): String =
        CobblemonTrainers.SPAWNER_ASPECT_PREFIX + worldPosition.asLong()

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)

        trainerId = tag.getString(TRAINER_KEY).takeIf { it.isNotEmpty() }?.let { raw ->
            ResourceLocation.tryParse(raw) ?: run {
                CobblemonTrainers.LOGGER.warn("Invalid trainer ID {} on the spawner at {}", raw, worldPosition)
                null
            }
        }
        // A missing key means a block saved before the setting existed, not a zero.
        leashRadius = if (tag.contains(LEASH_KEY)) {
            tag.getInt(LEASH_KEY).coerceIn(MIN_LEASH_RADIUS, MAX_LEASH_RADIUS)
        } else {
            DEFAULT_LEASH_RADIUS
        }
        respawnDelaySeconds = if (tag.contains(DELAY_KEY)) {
            tag.getInt(DELAY_KEY).coerceIn(0, MAX_RESPAWN_DELAY_SECONDS)
        } else {
            DEFAULT_RESPAWN_DELAY_SECONDS
        }
        facing = tag.getFloat(FACING_KEY)
        spawnedTrainer = if (tag.hasUUID(SPAWNED_KEY)) tag.getUUID(SPAWNED_KEY) else null
        seenAlive = false
    }

    /**
     * Everything above the line is the configuration, and it is what a structure carries: save
     * a building with a configured spawner in it and every copy comes out configured the same.
     * Below it is the running state, saved so a restart does not duplicate the trainer — and
     * deliberately harmless to copy, since a UUID that names another block's trainer is thrown
     * away on the first tick.
     */
    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)

        tag.putString(TRAINER_KEY, trainerId?.toString() ?: "")
        tag.putInt(LEASH_KEY, leashRadius)
        tag.putInt(DELAY_KEY, respawnDelaySeconds)
        tag.putFloat(FACING_KEY, facing)

        spawnedTrainer?.let { tag.putUUID(SPAWNED_KEY, it) }
    }

    companion object {
        const val DEFAULT_LEASH_RADIUS = 12
        const val MIN_LEASH_RADIUS = 1
        const val MAX_LEASH_RADIUS = 64

        const val DEFAULT_RESPAWN_DELAY_SECONDS = 30
        const val MAX_RESPAWN_DELAY_SECONDS = 86400

        private const val CHECK_INTERVAL_TICKS = 20L

        /** Backs off after a failed spawn, so a broken trainer is not retried every second. */
        private const val RETRY_INTERVAL_TICKS = 200L

        /**
         * How long a freshly loaded block gives its trainer to show up before deciding it is
         * gone. Long enough for a neighbouring chunk to finish loading its entities, short
         * enough that a spawner placed as part of a structure is not left empty.
         */
        private const val RELOAD_GRACE_TICKS = 60L

        private const val TRAINER_KEY = "Trainer"
        private const val LEASH_KEY = "LeashRadius"
        private const val DELAY_KEY = "RespawnDelay"
        private const val FACING_KEY = "Facing"
        private const val SPAWNED_KEY = "SpawnedTrainer"
    }
}
