package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.registries.BuiltInRegistries
import java.util.Locale

/**
 * The things that stop a hit from doing what the damage estimate says it will.
 *
 * Type effectiveness answers "how hard does this land"; this answers "does it land the way I am
 * counting on". Two very different mistakes come from ignoring it: throwing the strongest move
 * into a Disguise that was going to eat it whole, and believing in a KO that Sturdy turns into
 * one hit point and a free turn for the opponent.
 */
object BattleGuards {

    /** Abilities that nullify the first damaging hit outright, and swap the Pokémon's form. */
    private const val DISGUISE = "disguise"
    private const val ICE_FACE = "iceface"

    /**
     * The aspects those forms carry once broken.
     *
     * Mimikyu's `disguise_form` feature is a choice between `disguised` and `busted` rendered as
     * `{{choice}}-form`; Eiscue's broken form is flatly `noice_face`. Reading them is exact - when
     * Cobblemon keeps them in step with the Showdown battle, which is why [guardIntact] does not
     * rely on them alone.
     */
    private val BROKEN_ASPECTS = setOf("busted-form", "noice_face")

    /** Abilities and items that hold a Pokémon at one hit point from full health. */
    private const val STURDY = "sturdy"
    private const val FOCUS_SASH = "cobblemon:focus_sash"

    /**
     * Whether [defender] still has an unbroken Disguise or Ice Face.
     *
     * Two sources, because neither is enough on its own. The form aspect is exact but depends on
     * Cobblemon mirroring a Showdown form change back onto the `Pokemon`; [alreadyStruck] is our
     * own memory of having aimed a damaging move at this Pokémon, which is always available and
     * only ever one turn late. Whichever says "broken" wins.
     */
    fun guardIntact(defender: BattlePokemon, alreadyStruck: Boolean): Boolean {
        if (alreadyStruck) return false

        val ability = defender.effectedPokemon.ability.name.lowercase(Locale.ROOT)
        if (ability != DISGUISE && ability != ICE_FACE) return false

        return defender.effectedPokemon.aspects.none { it in BROKEN_ASPECTS }
    }

    /**
     * Whether [defender] would survive a lethal hit anyway: Sturdy or a Focus Sash, both of which
     * only work from full health.
     *
     * Believing in the KO is worse than not seeing it. The trainer spends its best move, the
     * opponent lives on one hit point, and whatever it does next is free.
     */
    fun survivesLethalHit(defender: BattlePokemon): Boolean {
        if (defender.health < defender.maxHealth) return false

        val pokemon = defender.effectedPokemon
        if (pokemon.ability.name.lowercase(Locale.ROOT) == STURDY) return true
        return heldItemId(pokemon) == FOCUS_SASH
    }

    private fun heldItemId(pokemon: Pokemon): String? {
        val stack = pokemon.heldItem()
        if (stack.isEmpty) return null
        return BuiltInRegistries.ITEM.getKey(stack.item).toString()
    }
}
