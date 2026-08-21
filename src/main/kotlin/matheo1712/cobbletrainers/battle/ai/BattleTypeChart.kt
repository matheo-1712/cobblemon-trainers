package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.registries.BuiltInRegistries
import java.util.Locale

/**
 * Type effectiveness as the trainer AI sees it, abilities and held items included.
 *
 * The chart itself comes from Cobblemon: [AIUtility.getDamageMultiplier] is complete and does
 * hold the eight immunities. What `StrongBattleAI` never does is consult an ability while
 * multiplying, so Levitate, Volt Absorb and Air Balloon read as a neutral hit and a Ground move
 * gets thrown at something it cannot touch. The missing table is also already in Cobblemon -
 * [AIUtility.getTypeImmuneAbilities] - it is simply not wired into the damage estimate. This
 * object joins the two.
 *
 * Reusing Cobblemon's tables rather than shipping our own means a Cobblemon update carries here
 * on its own, at the price of the three corrections listed on [typeImmuneAbilities].
 */
object BattleTypeChart {

    /** Held item that grants a Ground immunity for as long as it is held. */
    private const val AIR_BALLOON = "cobblemon:air_balloon"

    private const val WONDER_GUARD = "wonderguard"
    private const val GROUND = "ground"

    /**
     * Abilities that cancel a damage type outright, keyed by ability name.
     *
     * Cobblemon's table is the base, with three fixes it needs:
     * - `immunity` is dropped. It prevents *poisoning*, not Poison-type damage; honouring it
     *   here would have the AI refuse a perfectly good attack.
     * - `lightningrod` is added. Cobblemon spells its entry `lightingrod`, which matches no
     *   ability at all. The misspelt key is left in place so a fixed Cobblemon still works.
     * - `dryskin` and `wellbakedbody` are added; they are plainly missing.
     *
     * Lazy so that nothing here forces Cobblemon's AI classes to load before Cobblemon is ready.
     */
    private val typeImmuneAbilities: Map<String, ElementalType> by lazy {
        buildMap {
            putAll(AIUtility.typeImmuneAbilities)
            remove("immunity")
            ElementalTypes.get("electric")?.let { put("lightningrod", it) }
            ElementalTypes.get("water")?.let { put("dryskin", it) }
            ElementalTypes.get("fire")?.let { put("wellbakedbody", it) }
        }
    }

    /** What the type chart alone says about a [moveType] hit on [defender]. */
    fun typeMultiplier(moveType: ElementalType, defender: Pokemon): Double =
        defender.types.fold(1.0) { acc, type -> acc * AIUtility.getDamageMultiplier(moveType, type) }

    /**
     * How much damage a [moveType] hit does to [defender], as a multiplier. 0.0 means the hit
     * does nothing at all.
     *
     * With [withAbilities], an ability or a held item can bring it to zero on its own; without,
     * only the type chart is read. That is what separates a partly corrected trainer from a
     * fully corrected one - see [CorrectionLevel].
     */
    fun multiplier(moveType: ElementalType, defender: Pokemon, withAbilities: Boolean): Double {
        val fromTypes = typeMultiplier(moveType, defender)
        if (fromTypes == 0.0 || !withAbilities) return fromTypes

        val ability = defender.ability.name.lowercase(Locale.ROOT)
        if (typeImmuneAbilities[ability]?.name.equals(moveType.name, ignoreCase = true)) return 0.0
        // Wonder Guard is the one immunity that depends on the result rather than on the type.
        if (ability == WONDER_GUARD && fromTypes <= 1.0) return 0.0
        if (moveType.name.equals(GROUND, ignoreCase = true) && holdsAirBalloon(defender)) return 0.0

        return fromTypes
    }

    /**
     * The best multiplier [move] could get against any of [defenders]. The best rather than the
     * worst, so that a spread move is not refused because one target out of two is immune.
     */
    fun bestMultiplier(
        move: MoveTemplate,
        attacker: Pokemon,
        defenders: List<Pokemon>,
        withAbilities: Boolean
    ): Double {
        val type = move.getEffectiveElementalType(attacker)
        return defenders.maxOfOrNull { multiplier(type, it, withAbilities) } ?: 1.0
    }

    /**
     * A rough offensive score of [attacker] against [defenders]: the best multiplier any of its
     * own moves would get. Falls back to its types when it has no moves, which is what
     * Cobblemon's own matchup estimate does.
     *
     * Type chart only. This ranks switch candidates, and an ability immunity read into it would
     * make the ranking swing on a single move rather than on the matchup as a whole.
     */
    fun offence(attacker: Pokemon, defenders: List<Pokemon>): Double {
        val types = attacker.moveSet.getMoves().map { it.type }.ifEmpty { attacker.types.toList() }
        return types.maxOfOrNull { type ->
            defenders.maxOfOrNull { typeMultiplier(type, it) } ?: 0.0
        } ?: 1.0
    }

    private fun holdsAirBalloon(pokemon: Pokemon): Boolean {
        val stack = pokemon.heldItem()
        if (stack.isEmpty) return false
        return BuiltInRegistries.ITEM.getKey(stack.item).toString() == AIR_BALLOON
    }
}
