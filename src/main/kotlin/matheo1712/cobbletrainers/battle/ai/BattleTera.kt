package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * When Terastallizing is worth the one use a side gets, and when it is not.
 *
 * This is the one gimmick the layer judges. Mega Evolution costs nothing and is never a mistake,
 * so it is played at the first offer; a Terastallization costs nothing either, but it is the only
 * pivot of the whole battle, and spending it on turn one because it was offered is how a trainer
 * loses the exchange that mattered. So the question asked here is not "is this better" - it
 * almost always is, a little - but "does this decide something".
 *
 * Two answers do, and nothing else counts:
 *
 * 1. **It secures a knockout.** The move already chosen goes from not lethal to lethal on the
 *    strength of the Tera bonus alone. Deliberately the move *already chosen*, not the best move
 *    in the set: a gimmick attaches to a decision, it does not make one, and rewriting the move
 *    here would be a correction wearing a gimmick's clothes. The upshot is that this fires more
 *    often for a trainer that plays well - a `difficulty: 0` trainer picks its move at random and
 *    will rarely be holding the one that turns lethal. That is the scale doing its job, not a gap.
 * 2. **It survives the turn.** A hit that knocks this Pokémon out stops being lethal against the
 *    Tera type. This is what a Terastallization is for, and it is measurable: the same reading
 *    [BattleDamage.worstIncoming] already does for a heal, asked again against another type.
 *
 * Neither is gated on `battle.difficulty`. A gimmick is the pack's decision - it wrote the word -
 * and the judgement here is about the moment, not about how clever the trainer is.
 *
 * **Stellar is judged on the first answer only.** It is a Tera type that grants no type: nothing
 * changes defensively, so the second question has no meaning for it and is not asked. Its
 * offensive bonus is a different shape too - ×2 on a move that was already same-type, ×1.2 on
 * anything else - which is why the bonus travels as a plain number rather than as a type.
 */
object BattleTera {

    /** Showdown's name for the Tera type that is not an elemental type. */
    private const val STELLAR = "stellar"

    /** Terastallizing into a type the Pokémon already had stacks with the old bonus. */
    private const val TERA_STAB_MATCHED = 2.0

    /** Otherwise the Tera type simply carries the usual same-type bonus. */
    private const val TERA_STAB = 1.5

    /** Stellar on a move that was already same-type. */
    private const val STELLAR_STAB = 2.0

    /** Stellar on anything else. */
    private const val STELLAR_OTHER = 1.2

    /**
     * Why this trainer should Terastallize right now, or null to keep the use in hand.
     *
     * [teraName] is what the battle is offering, straight from `ShowdownMoveset.canTerastallize`;
     * [moveId] is the move the decision already landed on, and a switch answers null.
     */
    fun reason(
        teraName: String,
        moveId: String?,
        self: BattlePokemon,
        opponents: List<BattlePokemon>,
        struck: Set<UUID>
    ): String? {
        if (opponents.isEmpty()) return null

        val stellar = teraName.trim().equals(STELLAR, ignoreCase = true)
        // A name that is neither Stellar nor an elemental type is a Tera type this version knows
        // nothing about. Both readings then come back empty and the use is kept in hand, which is
        // the same answer [TrainerBattleAI.reasonFor] gives when the judgement throws: what cannot
        // be judged is not spent.
        val elemental = if (stellar) null else ElementalTypes.get(teraName.trim().lowercase(Locale.ROOT))

        securesKnockout(moveId, self, opponents, struck, elemental, stellar)?.let { return it }
        if (elemental == null) return null
        return survivesTheTurn(teraName, self, opponents, elemental)
    }

    /**
     * Whether the move already chosen only knocks out with the Tera bonus behind it.
     *
     * A target that is already going down is no reason to spend the use, and a guard that eats
     * the hit whole - Disguise, Sturdy, a Focus Sash - means there is no knockout to secure
     * either way. Both are the same reading [TrainerBattleAI] does when it scores a move.
     */
    private fun securesKnockout(
        moveId: String?,
        self: BattlePokemon,
        opponents: List<BattlePokemon>,
        struck: Set<UUID>,
        elemental: ElementalType?,
        stellar: Boolean
    ): String? {
        val move = moveId?.let { Moves.getByName(it) } ?: return null
        val moveType = move.getEffectiveElementalType(self.effectedPokemon)

        val before = BattleDamage.stabFor(moveType, self)
        val after = stabAfter(before, moveType, elemental, stellar)
        if (after <= before) return null

        for (opponent in opponents) {
            if (BattleGuards.survivesLethalHit(opponent)) continue
            if (BattleGuards.guardIntact(opponent, opponent.uuid in struck)) continue

            val multiplier = BattleTypeChart.multiplier(moveType, opponent.effectedPokemon, withAbilities = true)
            if (multiplier == 0.0) continue

            val plain = BattleDamage.estimate(move, self, opponent, multiplier)
            if (plain >= opponent.health) continue

            val boosted = BattleDamage.estimate(move, self, opponent, multiplier, after)
            if (boosted >= opponent.health) {
                return "$moveId only knocks out with the Tera bonus " +
                    "(${plain.roundToInt()} to ${boosted.roundToInt()}, ${opponent.health} left)"
            }
        }

        return null
    }

    /**
     * Whether the Tera type takes an incoming knockout out of lethal range.
     *
     * A Terastallized Pokémon has exactly one type, so the reading is against a single-type
     * defender - which is also why it can turn a doubly weak Pokémon into a resistant one in a
     * single turn, and why it is worth a whole battle's worth of gimmick.
     */
    private fun survivesTheTurn(
        teraName: String,
        self: BattlePokemon,
        opponents: List<BattlePokemon>,
        elemental: ElementalType
    ): String? {
        // Sturdy and a Focus Sash already answer this turn; the use is better kept.
        if (BattleGuards.survivesLethalHit(self)) return null

        val incoming = BattleDamage.worstIncoming(self, opponents)
        if (incoming < self.health) return null

        val afterwards = BattleDamage.worstIncoming(self, opponents, listOf(elemental))
        if (afterwards >= self.health) return null

        return "Tera $teraName survives the turn " +
            "(${incoming.roundToInt()} down to ${afterwards.roundToInt()}, ${self.health} left)"
    }

    /**
     * The same-type bonus a move gets once the Terastallization has gone through, given what it
     * had [before].
     */
    private fun stabAfter(
        before: Double,
        moveType: ElementalType,
        elemental: ElementalType?,
        stellar: Boolean
    ): Double {
        val wasSameType = before > 1.0
        if (elemental == null) {
            return if (stellar && wasSameType) STELLAR_STAB else if (stellar) STELLAR_OTHER else before
        }

        val matchesTera = moveType.name.equals(elemental.name, ignoreCase = true)
        return when {
            matchesTera && wasSameType -> TERA_STAB_MATCHED
            matchesTera || wasSameType -> TERA_STAB
            else -> 1.0
        }
    }
}
