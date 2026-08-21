package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon

/**
 * Damage in hit points, so that the AI can answer questions a ranking cannot.
 *
 * Ordering four moves only needs a score; "does this land a KO" and "is healing worth a turn"
 * need a figure on the same scale as the health bar. Cobblemon does have a damage estimate -
 * `StrongBattleAI.calculateDamage` - but it takes the `TrackerPokemon` of its own private
 * `activeTracker`, which has no getter, so it cannot be called from outside.
 *
 * This is the mainline formula for a single target, with stat stages and burn. Weather, screens,
 * held items, abilities beyond immunity and the damage roll spread are all left out: they would
 * each need battle state this layer does not read, and the two questions above tolerate being
 * a little wrong. Where it matters the error is towards *under*-estimating - the average roll is
 * used rather than the highest - so a KO it announces is one it really has.
 */
object BattleDamage {

    /** Mainline rolls damage over 85-100%; the mean is what an estimate should use. */
    private const val AVERAGE_ROLL = 0.925

    private const val STAB = 1.5
    private const val BURN_PENALTY = 0.5
    private const val BURN = "brn"

    /** Stands in for the variable power of Seismic Toss, Return, Gyro Ball and the like. */
    private const val FALLBACK_POWER = 60.0

    /**
     * What [move] would take off [defender], before accuracy. Accuracy is deliberately left out:
     * a move that hits for a KO nine times out of ten still hits for a KO, and folding the miss
     * chance in here would turn a yes/no question into an average.
     */
    fun estimate(
        move: MoveTemplate,
        attacker: BattlePokemon,
        defender: BattlePokemon,
        multiplier: Double
    ): Double {
        if (multiplier == 0.0 || move.damageCategory == DamageCategories.STATUS) return 0.0

        val physical = move.damageCategory == DamageCategories.PHYSICAL
        val attack = boosted(attacker, if (physical) Stats.ATTACK else Stats.SPECIAL_ATTACK)
        val defence = boosted(defender, if (physical) Stats.DEFENCE else Stats.SPECIAL_DEFENCE)
        if (defence <= 0.0) return 0.0

        val power = if (move.power > 0.0) move.power else FALLBACK_POWER
        val level = attacker.effectedPokemon.level
        val base = (2.0 * level / 5.0 + 2.0) * power * attack / defence / 50.0 + 2.0

        val type = move.getEffectiveElementalType(attacker.effectedPokemon)
        val stab = if (attacker.effectedPokemon.types.any { it.name.equals(type.name, true) }) STAB else 1.0
        val burn = if (physical && isBurned(attacker)) BURN_PENALTY else 1.0

        return base * stab * multiplier * burn * AVERAGE_ROLL
    }

    /**
     * The move [attacker] would most likely reach for against [defender]: its damage, and the
     * priority that comes with it.
     *
     * The two travel together on purpose. Taking the opponent's *highest* priority instead would
     * make the trainer believe it never moves first against anyone carrying a Quick Attack, and
     * a Pokemon does not open with its priority move unless that is also its best one.
     */
    fun strongestAgainst(defender: BattlePokemon, attacker: BattlePokemon): Pair<Double, Int> {
        var damage = 0.0
        var priority = 0
        for (move in attacker.effectedPokemon.moveSet.getMoves()) {
            val template = move.template
            val multiplier = BattleTypeChart.multiplier(
                template.getEffectiveElementalType(attacker.effectedPokemon),
                defender.effectedPokemon,
                withAbilities = true
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
    fun worstIncoming(defender: BattlePokemon, attackers: List<BattlePokemon>): Double =
        attackers.maxOfOrNull { strongestAgainst(defender, it).first } ?: 0.0

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
