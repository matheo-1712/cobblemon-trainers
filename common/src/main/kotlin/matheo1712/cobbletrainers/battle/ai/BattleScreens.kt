package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.api.battles.interpreter.BattleContext
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import net.minecraft.core.registries.BuiltInRegistries
import java.util.Locale

/**
 * Reflect, Light Screen and Aurora Veil: whether one is worth a turn, and which one.
 *
 * A screen costs a turn now to halve damage for five turns - eight with a Light Clay. That item
 * is the whole point of the rule: without it Cobblemon's own judgement stands, and with it the
 * trainer was *built* to put a curtain up, which is a plan a wrapper can carry out without
 * second-guessing the pack.
 *
 * Nothing here reads Showdown directly. What is already standing comes from Cobblemon's own
 * [BattleContext] buckets - the side holds its screens, the battle holds its weather - which is
 * the same state the battle log is built from, so a screen this says is up is one the player can
 * see on their screen.
 */
object BattleScreens {

    /** The item that makes a screen last eight turns instead of five. */
    private const val LIGHT_CLAY = "cobblemon:light_clay"

    const val REFLECT = "reflect"
    const val LIGHT_SCREEN = "lightscreen"
    const val AURORA_VEIL = "auroraveil"

    /** The three moves this rule knows, so a move set can be scanned for them in one pass. */
    val ALL = setOf(REFLECT, LIGHT_SCREEN, AURORA_VEIL)

    /**
     * Weather under which Aurora Veil may be set at all. Outside it the move plainly fails -
     * Showdown still offers it, and a trainer that plays it there loses the turn for nothing.
     *
     * Three names for two weathers: `snow` is what Showdown sends today, `hail` is what it sent
     * before generation 9, and `snowscape` is the move's own name, which is what the id would be
     * were Cobblemon ever to record the move rather than the weather it causes.
     */
    private val VEIL_WEATHER = setOf("snow", "hail", "snowscape")

    /** Whether this Pokémon carries the Light Clay that makes a screen worth the turn. */
    fun holdsLightClay(pokemon: BattlePokemon): Boolean {
        val stack = pokemon.effectedPokemon.heldItem()
        if (stack.isEmpty) return false
        return BuiltInRegistries.ITEM.getKey(stack.item).toString() == LIGHT_CLAY
    }

    /**
     * The screens already standing on [active]'s own side, by move id.
     *
     * Ids are normalised down to letters and digits before being compared: Showdown names the
     * side condition (`Light Screen`, `move: Aurora Veil`) and Cobblemon keeps whatever it read,
     * while a move id has never been anything but `lightscreen`.
     */
    fun standing(active: ActiveBattlePokemon): Set<String> =
        active.actor.getSide().contextManager.get(BattleContext.Type.SCREEN)
            .orEmpty()
            .map { normalise(it.id) }
            .toSet()

    /** Whether the weather lets Aurora Veil be set right now. */
    fun veilWeather(battle: PokemonBattle): Boolean =
        battle.contextManager.get(BattleContext.Type.WEATHER)
            .orEmpty()
            .any { normalise(it.id) in VEIL_WEATHER }

    /**
     * Which screen answers the hardest hit coming: Reflect against a physical attacker, Light
     * Screen against a special one.
     *
     * The comparison is on estimated damage rather than on raw stats, so a Pokémon whose special
     * attack is higher but whose only useful move is physical is read for what it will actually
     * do. Aurora Veil covers both sides at once and never comes through here.
     */
    fun bestAgainst(self: BattlePokemon, opponents: List<BattlePokemon>): String {
        var physical = 0.0
        var special = 0.0

        for (opponent in opponents) {
            for (move in opponent.effectedPokemon.moveSet.getMoves()) {
                val template = move.template
                if (template.damageCategory == DamageCategories.STATUS) continue

                val multiplier = BattleTypeChart.multiplier(
                    template.getEffectiveElementalType(opponent.effectedPokemon),
                    self.effectedPokemon,
                    withAbilities = true
                )
                val damage = BattleDamage.estimate(template, opponent, self, multiplier)

                if (template.damageCategory == DamageCategories.PHYSICAL) {
                    if (damage > physical) physical = damage
                } else if (damage > special) {
                    special = damage
                }
            }
        }

        // A tie goes to Reflect: an opponent with nothing to show yet is more often physical,
        // and either screen is better than spending the turn deciding.
        return if (special > physical) LIGHT_SCREEN else REFLECT
    }

    private fun normalise(id: String): String =
        id.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
}
