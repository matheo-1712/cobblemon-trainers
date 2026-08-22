package matheo1712.cobbletrainers.trainers

import com.cobblemon.mod.common.entity.npc.NPCEntity
import matheo1712.cobbletrainers.mixin.MobAccessor
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.player.Player

/**
 * Makes a trainer turn to face a player who comes near.
 *
 * A trainer stands still - none of them wander - so without this they stare at whatever
 * direction they were put down in, and walking up to one feels like walking up to a statue.
 * Turning to look is the smallest thing that makes them read as someone waiting for you.
 *
 * Points worth not rediscovering:
 * - **It is a vanilla goal, not a tick of ours.** Cobblemon runs its NPCs on the Brain system,
 *   but `NPCEntity` overrides only `customServerAiStep`, so `Mob.serverAiStep` still ticks the
 *   goal selector and the look control underneath. Vanilla then handles the range, the natural
 *   glances away and the head-then-body turn for free, and it costs nothing per tick when no
 *   player is near.
 * - **The hook is entity load, not spawn.** Goals are not saved to disk, so a trainer placed by
 *   a spawner block would have lost its gaze at the first restart if this hung off
 *   [TrainerSpawner]. Loading covers the fresh spawn and the one read back from a chunk with a
 *   single rule.
 * - **The filter is the trainer aspect**, so it follows every trainer of the mod - called,
 *   commanded, or held in place by a block - and never touches an NPC that is not ours.
 */
object TrainerGaze {

    /**
     * How near a player has to be. Eight blocks is what a villager uses: close enough that
     * being looked at reads as being noticed, far enough to happen before the player is on top
     * of the trainer.
     */
    private const val GAZE_DISTANCE = 8.0f

    /**
     * Vanilla rolls this every time the goal is considered, and its own default of 0.02 is what
     * makes a villager glance at you now and then. A trainer waiting to be challenged looks at
     * whoever walks up, every time.
     */
    private const val GAZE_PROBABILITY = 1.0f

    /**
     * Low priority: nothing else of ours competes for the goal selector today, and leaving room
     * above means anything added later wins without this needing to be renumbered.
     */
    private const val GAZE_PRIORITY = 8

    fun register() {
        ServerEntityEvents.ENTITY_LOAD.register { entity, _ -> giveGaze(entity) }
    }

    private fun giveGaze(entity: Entity) {
        if (entity !is NPCEntity) return
        if (TrainerRegistry.idFromAspects(entity.aspects) == null) return

        // The accessor is only on Mob once the mixin has been applied, so the compiler cannot
        // see it on NPCEntity and rejects the cast outright. The hop through Any is what every
        // mixin accessor call from Kotlin needs; it is not a smell to be tidied away.
        val goals = (entity as Any as MobAccessor).cobbletrainersGoalSelector()
        goals.addGoal(
            GAZE_PRIORITY,
            LookAtPlayerGoal(entity, Player::class.java, GAZE_DISTANCE, GAZE_PROBABILITY)
        )
    }
}
