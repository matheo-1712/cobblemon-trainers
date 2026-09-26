package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import java.util.UUID
import kotlin.math.roundToInt

/**
 * When Dynamaxing is worth the one use a side gets, and when it is not.
 *
 * The third gimmick this layer judges, and the one whose two answers are exactly [BattleTera]'s,
 * because Dynamax does both things at once: it lifts every move into a Max Move, and it doubles
 * the health bar on the spot.
 *
 * 1. **It secures a knockout.** The move already chosen goes from not lethal to lethal on the
 *    strength of the Max Move's power alone - see [BattleDamage.securedKnockout], which the
 *    other two judged gimmicks ask the same way.
 * 2. **It survives the turn.** A hit that knocks this Pokémon out stops being lethal once the
 *    bar is doubled. Defences do not change, so this is the one reading where the incoming
 *    damage stays put and the health it is measured against moves.
 *
 * **Only a damaging move is ever lifted this way.** Dynamaxing turns every status move into Max
 * Guard, which is not the move the trainer decided on - it would be the gimmick making the
 * decision rather than attaching to one. A trainer that chose to heal, set up or lay a hazard
 * therefore keeps its use in hand, and reaches for it on a turn it is attacking.
 *
 * **The power is worked out, not read**, for the same reason and with the same tolerance as
 * [BattleZMove]: `maxMoves` names the Max Move, but the entry behind that name carries a
 * placeholder power. See [power].
 *
 * Nothing here reads the three turns Dynamax lasts. It is deliberate: what happens after this
 * turn depends on what the player does next, which this layer has no way of knowing, and both
 * answers above are already worth the use on their own.
 */
object BattleDynamax {

    /** Dynamax doubles maximum and current health the moment it goes up. */
    private const val HEALTH_MULTIPLIER = 2

    /**
     * Why this trainer should Dynamax right now, or null to keep the use in hand.
     *
     * [maxMove] is the move the battle says this slot turns into when it is known, and is only
     * ever used to say so; [moveId] is the move the decision already landed on, and a switch
     * answers null.
     */
    fun reason(
        maxMove: String?,
        moveId: String?,
        self: BattlePokemon,
        opponents: List<BattlePokemon>,
        struck: Set<UUID>
    ): String? {
        if (opponents.isEmpty()) return null

        val id = moveId ?: return null
        val move = Moves.getByName(id) ?: return null
        // Every status move becomes Max Guard, which is a decision of its own. See above.
        if (move.damageCategory == DamageCategories.STATUS) return null

        securesKnockout(maxMove, id, move, self, opponents, struck)?.let { return it }
        return survivesTheTurn(self, opponents)
    }

    /** Whether the move already chosen only knocks out with a Max Move's power behind it. */
    private fun securesKnockout(
        maxMove: String?,
        moveId: String,
        move: MoveTemplate,
        self: BattlePokemon,
        opponents: List<BattlePokemon>,
        struck: Set<UUID>
    ): String? {
        val power = power(move, self) ?: return null
        val secured = BattleDamage.securedKnockout(move, self, opponents, struck, power = power)
            ?: return null

        return "$moveId only knocks out as ${maxMove ?: "a Max Move"} " +
            "(${secured.plain.roundToInt()} to ${secured.lifted.roundToInt()}, ${secured.health} left)"
    }

    /**
     * Whether doubling the health bar takes an incoming knockout out of lethal range.
     *
     * The mirror of [BattleTera]'s own defensive answer, read from the other side: there the
     * damage falls against a new type, here the damage stands and the bar it has to get through
     * doubles.
     */
    private fun survivesTheTurn(self: BattlePokemon, opponents: List<BattlePokemon>): String? {
        // Sturdy and a Focus Sash already answer this turn; the use is better kept.
        if (BattleGuards.survivesLethalHit(self)) return null

        val incoming = BattleDamage.worstIncoming(self, opponents)
        if (incoming < self.health) return null

        val doubled = self.health * HEALTH_MULTIPLIER
        if (incoming >= doubled) return null

        return "Dynamaxing survives the turn " +
            "(${incoming.roundToInt()} incoming, ${self.health} left becomes $doubled)"
    }

    /**
     * The base power [move] hits for once it is a Max Move, or null when it cannot be worked out.
     *
     * The mainline conversion table. Fighting and Poison moves have their own, lower one - Max
     * Knuckle and Max Ooze buy their damage back in a stat boost the table cannot see - and a
     * move whose power is variable has no entry at all, which keeps the use in hand rather than
     * spending it on a guess.
     */
    private fun power(move: MoveTemplate, attacker: BattlePokemon): Double? {
        val type = move.getEffectiveElementalType(attacker.effectedPokemon).name
        val restrained = type.equals(ElementalTypes.FIGHTING.name, ignoreCase = true) ||
            type.equals(ElementalTypes.POISON.name, ignoreCase = true)

        return when {
            move.power <= 0.0 -> null
            move.power < 45 -> if (restrained) 70.0 else 90.0
            move.power < 55 -> if (restrained) 75.0 else 100.0
            move.power < 65 -> if (restrained) 80.0 else 110.0
            move.power < 75 -> if (restrained) 85.0 else 120.0
            move.power < 110 -> if (restrained) 90.0 else 130.0
            move.power < 150 -> if (restrained) 95.0 else 140.0
            else -> if (restrained) 100.0 else 150.0
        }
    }
}
