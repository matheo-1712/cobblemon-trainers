package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import java.util.Locale
import java.util.UUID

/**
 * Damage in hit points, so that the AI can answer questions a ranking cannot.
 *
 * Ordering four moves only needs a score; "does this land a KO" and "is healing worth a turn"
 * need a figure on the same scale as the health bar. Cobblemon does have a damage estimate -
 * `StrongBattleAI.calculateDamage` - but it takes the `TrackerPokemon` of its own private
 * `activeTracker`, which has no getter, so it cannot be called from outside.
 *
 * This is the mainline formula for a single target, with stat stages and burn. Weather, screens,
 * held items and the damage roll spread are all left out: they would each need battle state this
 * layer does not read, and the two questions above tolerate being a little wrong. Where it
 * matters the error is towards *under*-estimating - the average roll is used rather than the
 * highest - so a KO it announces is one it really has.
 *
 * Abilities are left out too, with two exceptions, and both are exceptions for the same reason -
 * they read nothing but the Pokémon in hand: the immunities of [BattleTypeChart], which reach
 * this through the multiplier handed in, and the [conversionBoost] that comes with a changed
 * move type.
 */
object BattleDamage {

    /** Mainline rolls damage over 85-100%; the mean is what an estimate should use. */
    private const val AVERAGE_ROLL = 0.925

    private const val STAB = 1.5
    private const val BURN_PENALTY = 0.5
    private const val BURN = "brn"

    /**
     * Abilities that change the type a move comes out as and are paid 1.2x for doing it: the four
     * -ate abilities and Normalize. See [conversionBoost].
     */
    private val CONVERSION_ABILITIES =
        setOf("pixilate", "refrigerate", "aerilate", "galvanize", "normalize")

    private const val CONVERSION_BOOST = 1.2

    /** Stands in for the variable power of Seismic Toss, Return, Gyro Ball and the like. */
    private const val FALLBACK_POWER = 60.0

    /**
     * What [move] would take off [defender], before accuracy. Accuracy is deliberately left out:
     * a move that hits for a KO nine times out of ten still hits for a KO, and folding the miss
     * chance in here would turn a yes/no question into an average.
     *
     * [stab] overrides the same-type bonus this would work out on its own. Only [BattleTera]
     * passes it: after Terastallization the bonus no longer follows from the attacker's types,
     * and Stellar changes it without changing any type at all.
     *
     * [power] overrides the move's own base power, and is how [BattleZMove] and [BattleDynamax]
     * read what their gimmick would hit for: a Z-Move and a Max Move keep the base move's type
     * and category and change nothing but this number. Cobblemon cannot be asked for it - the
     * entries Showdown ships for those moves carry a placeholder power, the real figure being
     * worked out from the base move - so each of them brings its own table.
     */
    fun estimate(
        move: MoveTemplate,
        attacker: BattlePokemon,
        defender: BattlePokemon,
        multiplier: Double,
        stab: Double? = null,
        power: Double? = null
    ): Double {
        if (multiplier == 0.0 || move.damageCategory == DamageCategories.STATUS) return 0.0

        val physical = move.damageCategory == DamageCategories.PHYSICAL
        val attack = boosted(attacker, if (physical) Stats.ATTACK else Stats.SPECIAL_ATTACK)
        val defence = boosted(defender, if (physical) Stats.DEFENCE else Stats.SPECIAL_DEFENCE)
        if (defence <= 0.0) return 0.0

        val basePower = power ?: if (move.power > 0.0) move.power else FALLBACK_POWER
        val level = attacker.effectedPokemon.level
        val base = (2.0 * level / 5.0 + 2.0) * basePower * attack / defence / 50.0 + 2.0

        val type = move.getEffectiveElementalType(attacker.effectedPokemon)
        val sameType = stab ?: stabFor(type, attacker)
        val converted = conversionBoost(move, attacker, type)
        val burn = if (physical && isBurned(attacker)) BURN_PENALTY else 1.0

        return base * sameType * converted * multiplier * burn * AVERAGE_ROLL
    }

    /**
     * The 1.2x an -ate ability or Normalize adds on top of the type it just changed.
     *
     * The second of the two ability readings the header allows, and it is free: the conversion it
     * rides on has been resolved a line above, and nothing else is needed. Left out, every
     * judgement on a converted move - the knockout tests below most of all - reads a fifth short
     * of what the move really does.
     *
     * Keyed on the conversion having happened rather than on the ability alone. Normalize pays
     * nothing on a move that was already Normal, since it changed nothing; Hidden Power changes
     * type with no ability behind it, so the ability is still what has to be there.
     */
    private fun conversionBoost(
        move: MoveTemplate,
        attacker: BattlePokemon,
        type: ElementalType
    ): Double {
        if (type.name.equals(move.elementalType.name, ignoreCase = true)) return 1.0
        val ability = attacker.effectedPokemon.ability.name.lowercase(Locale.ROOT)
        return if (ability in CONVERSION_ABILITIES) CONVERSION_BOOST else 1.0
    }

    /** The same-type bonus [attacker] gets on a [moveType] hit as things stand. */
    fun stabFor(moveType: ElementalType, attacker: BattlePokemon): Double =
        if (attacker.effectedPokemon.types.any { it.name.equals(moveType.name, true) }) STAB else 1.0

    /**
     * The target whose fate a lifted [move] would decide, or null when it decides nobody's.
     *
     * The three judged gimmicks all ask this, and all ask it of the move that has *already* been
     * chosen: Terastallization lifts it with a [stab] it did not have, a Z-Move and a Dynamax
     * with a [power] it did not have, and in every case the question is whether the target stops
     * surviving. A target already going down is no reason to spend a use, and a guard that eats
     * the hit whole - Disguise, Ice Face, Sturdy, a Focus Sash - means there is no knockout to
     * secure either way. Both are the readings [TrainerBattleAI] already does when it scores a
     * move.
     */
    fun securedKnockout(
        move: MoveTemplate,
        attacker: BattlePokemon,
        opponents: List<BattlePokemon>,
        struck: Set<UUID>,
        stab: Double? = null,
        power: Double? = null
    ): Knockout? {
        val type = move.getEffectiveElementalType(attacker.effectedPokemon)

        for (opponent in opponents) {
            if (BattleGuards.survivesLethalHit(opponent)) continue
            if (BattleGuards.guardIntact(opponent, opponent.uuid in struck)) continue

            val multiplier = BattleTypeChart.multiplier(type, opponent.effectedPokemon, withAbilities = true)
            if (multiplier == 0.0) continue

            val plain = estimate(move, attacker, opponent, multiplier)
            if (plain >= opponent.health) continue

            val lifted = estimate(move, attacker, opponent, multiplier, stab, power)
            if (lifted >= opponent.health) return Knockout(plain, lifted, opponent.health)
        }

        return null
    }

    /** A knockout that only the lifted move reaches, in hit points. See [securedKnockout]. */
    class Knockout(val plain: Double, val lifted: Double, val health: Int)

    /**
     * The move [attacker] would most likely reach for against [defender]: its damage, and the
     * priority that comes with it.
     *
     * The two travel together on purpose. Taking the opponent's *highest* priority instead would
     * make the trainer believe it never moves first against anyone carrying a Quick Attack, and
     * a Pokemon does not open with its priority move unless that is also its best one.
     *
     * [defenderTypes] reads the exchange against types the defender does not have yet, which is
     * how [BattleTera] asks whether a Terastallization would take the hit out of lethal range.
     */
    fun strongestAgainst(
        defender: BattlePokemon,
        attacker: BattlePokemon,
        defenderTypes: Iterable<ElementalType>? = null
    ): Pair<Double, Int> {
        var damage = 0.0
        var priority = 0
        for (move in attacker.effectedPokemon.moveSet.getMoves()) {
            val template = move.template
            val multiplier = BattleTypeChart.multiplier(
                template.getEffectiveElementalType(attacker.effectedPokemon),
                defender.effectedPokemon,
                withAbilities = true,
                asTypes = defenderTypes
            )
            val estimated = estimate(template, attacker, defender, multiplier)
            if (estimated > damage) {
                damage = estimated
                priority = template.priority
            }
        }
        return damage to priority
    }

    /**
     * The hardest hit [attackers] could land on [defender] next turn.
     *
     * This is what tells a heal apart from a wasted turn. It reads the opponent's real move set,
     * which is knowledge Cobblemon's AI already has - its tracker holds the live `Pokemon`.
     */
    fun worstIncoming(
        defender: BattlePokemon,
        attackers: List<BattlePokemon>,
        defenderTypes: Iterable<ElementalType>? = null
    ): Double = attackers.maxOfOrNull { strongestAgainst(defender, it, defenderTypes).first } ?: 0.0

    /** The priority of the move [attacker] is most likely to answer [defender] with. */
    fun threatPriority(defender: BattlePokemon, attacker: BattlePokemon): Int =
        strongestAgainst(defender, attacker).second

    /** A stat as it stands right now, stat stages applied. */
    private fun boosted(pokemon: BattlePokemon, stat: Stat): Double {
        val base = pokemon.effectedPokemon.getStat(stat).toDouble()
        return base * stageMultiplier(pokemon.statChanges[stat] ?: 0)
    }

    /** The mainline stage table: +1 is ×1.5, -1 is ×0.667, and so on to ±6. */
    fun stageMultiplier(stage: Int): Double {
        val clamped = stage.coerceIn(-6, 6)
        return if (clamped >= 0) (2.0 + clamped) / 2.0 else 2.0 / (2.0 - clamped)
    }

    private fun isBurned(pokemon: BattlePokemon): Boolean =
        pokemon.effectedPokemon.status?.status?.showdownName == BURN
}
