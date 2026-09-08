package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import java.util.UUID
import kotlin.math.roundToInt

/**
 * When a Z-Move is worth the one use a side gets, and when it is not.
 *
 * Z-Power is judged for the same reason Terastallization is, and against the same clock: a side
 * gets exactly one, it costs no turn, and firing it on turn one because it was offered is how it
 * ends up spent on a target that was going down anyway. So the question is not "is this stronger"
 * - it always is, hugely - but "does this decide something".
 *
 * Only one answer does, and it is the first of [BattleTera]'s two: **the move already chosen goes
 * from not lethal to lethal**. Deliberately the move *already chosen*, for the same reason as
 * there - a gimmick attaches to a decision, it does not make one - with the same upshot, that a
 * trainer playing well is holding the right move more often than one playing at random.
 *
 * There is no second, defensive answer, and that is not an omission. A Terastallization changes
 * what the Pokémon *is* for the rest of the battle; a Z-Move is one hit and then nothing. The
 * Z-status moves are the exception that proves it - Z-Parting Shot heals, Z-Celebrate boosts -
 * but they replace a status move the trainer chose with something else it did not, which is the
 * one thing a gimmick must not do here. A status move therefore keeps its use in hand.
 *
 * **The power is worked out, not read.** `InBattleGimmickMove` names the Z-Move, but the entry
 * Showdown ships under that name carries a placeholder power: the real figure comes from the base
 * move, through the conversion table in [power]. A handful of moves have a Z power set by hand
 * that the table does not reproduce, and the cost of being wrong about one of them is a Z-Move
 * fired a turn early or held a turn late - the same tolerance [BattleDamage] is built on.
 */
object BattleZMove {

    /**
     * Why this trainer should use its Z-Move right now, or null to keep the use in hand.
     *
     * [zMove] is the move the battle says this slot turns into, straight from the moveset;
     * [moveId] is the move the decision already landed on, and a switch answers null.
     */
    fun reason(
        zMove: String,
        moveId: String?,
        self: BattlePokemon,
        opponents: List<BattlePokemon>,
        struck: Set<UUID>
    ): String? {
        if (opponents.isEmpty()) return null

        val move = moveId?.let { Moves.getByName(it) } ?: return null
        // A status move would become a Z-status move, which is a different decision entirely.
        if (move.damageCategory == DamageCategories.STATUS) return null

        val power = power(move) ?: return null
        val secured = BattleDamage.securedKnockout(move, self, opponents, struck, power = power)
            ?: return null

        return "$moveId only knocks out as $zMove " +
            "(${secured.plain.roundToInt()} to ${secured.lifted.roundToInt()}, ${secured.health} left)"
    }

    /**
     * The base power [move] hits for once it is a Z-Move, or null when it cannot be worked out.
     *
     * The mainline conversion table, which is a function of the base move's power and nothing
     * else. A move whose power is variable - Seismic Toss, Gyro Ball, Return - has no entry in
     * it, and rather than guess at one the use is kept in hand: that is the answer this layer
     * gives everywhere it cannot judge.
     */
    private fun power(move: MoveTemplate): Double? = when {
        move.power <= 0.0 -> null
        move.power <= 55 -> 100.0
        move.power <= 65 -> 120.0
        move.power <= 75 -> 140.0
        move.power <= 85 -> 160.0
        move.power <= 95 -> 175.0
        move.power <= 100 -> 180.0
        move.power <= 110 -> 185.0
        move.power <= 125 -> 190.0
        move.power <= 130 -> 195.0
        else -> 200.0
    }
}
