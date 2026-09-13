package matheo1712.cobbletrainers.battle

import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.min

/**
 * Puts a trainer's Pokémon down in front of the trainer, instead of wherever Cobblemon's arena
 * maths lands it.
 *
 * `ActiveBattlePokemon.getSendOutPosition` averages the starting position of each side, then
 * throws the Pokémon 30% of the way from its own side to the other. When the two sides are
 * closer than `4 + (both hitbox widths) / 2`, it stretches the gap to that length **by moving its
 * own anchor backwards**. A player who talks to a trainer from two blocks away is the normal
 * case here - the dialogue opens on a right click - so the trainer's anchor ends up several
 * blocks behind them, and a large Pokémon lands far off or behind the trainer's back.
 *
 * The rule here keeps Cobblemon's sideways offset (doubles and triples still spread out) and
 * only replaces the forward part: [FRONT_DISTANCE] from the trainer toward the other side, plus
 * half the Pokémon's width so a big one does not stand inside its trainer.
 *
 * Points worth not rediscovering:
 * - **The Pokémon never lands on the player.** The reach is capped to leave [PLAYER_CLEARANCE]
 *   between the Pokémon's edge and the nearest opponent, but never below half a block past its
 *   own edge - a player hugging the trainer gets an overlap rather than a Pokémon thrown backwards.
 * - **The wall check is redone**, because Cobblemon's own was made for the position it computed,
 *   not ours: a ray from the trainer's eyes, pulled back by half the Pokémon's width when it hits.
 * - **Only the trainer's side moves.** The player's Pokémon still goes where Cobblemon puts it.
 * - **Positions come from `initialPos`**, the one Cobblemon reads too, so a trainer that turned
 *   its head mid-battle does not shift the next send-out.
 */
object TrainerSendOut {

    /** How far in front of the trainer, in blocks, the edge of its Pokémon lands. */
    private const val FRONT_DISTANCE = 1.5

    /** The room kept between the Pokémon's edge and the nearest opponent. */
    private const val PLAYER_CLEARANCE = 1.0

    /** Returns the position to use, or [computed] untouched when this is not a trainer's Pokémon. */
    fun reposition(active: ActiveBattlePokemon, computed: Vec3?): Vec3? {
        if (computed == null) return null
        val actor = active.actor as? NPCBattleActor ?: return computed
        val npc = actor.npc
        if (TrainerRegistry.idFromAspects(npc.aspects) == null) return computed

        val origin = actor.initialPos
        val opponents: List<Vec3> = active.actor.getSide().getOppositeSide().actors
            .mapNotNull { (it as? EntityBackedBattleActor<*>)?.initialPos }
        if (opponents.isEmpty()) return computed

        val toward = Vec3(
            opponents.sumOf { it.x } / opponents.size - origin.x,
            0.0,
            opponents.sumOf { it.z } / opponents.size - origin.z,
        )
        val distance = toward.length()
        if (distance < 1.0E-3) return computed
        val forward = toward.scale(1.0 / distance)

        val form = active.battlePokemon?.originalPokemon?.form
        val halfWidth = form?.let { it.hitbox.width() * it.baseScale / 2.0 } ?: 0.0
        val nearest = opponents.minOf { Vec3(it.x - origin.x, 0.0, it.z - origin.z).length() }
        val reach = max(
            min(FRONT_DISTANCE + halfWidth, nearest - halfWidth - PLAYER_CLEARANCE),
            halfWidth + 0.5,
        )

        // Cobblemon's sideways spread for doubles and triples, with its forward part dropped.
        val offset = Vec3(computed.x - origin.x, 0.0, computed.z - origin.z)
        val lateral = offset.subtract(forward.scale(offset.dot(forward)))
        val target = origin.add(forward.scale(reach)).add(lateral)

        val level = npc.level()
        val eye = npc.eyeHeight.toDouble()
        val hit = level.clip(
            ClipContext(
                origin.add(0.0, eye, 0.0),
                target.add(0.0, eye, 0.0),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                npc,
            )
        )
        if (hit.type != HitResult.Type.BLOCK) return target

        val blocked = Vec3(hit.location.x, origin.y, hit.location.z)
        val backed = blocked.subtract(forward.scale(halfWidth))
        // A wall closer than half the Pokémon: the trainer's own feet are the best there is.
        return if (backed.subtract(origin).dot(forward) <= 0.0) origin else backed
    }
}
