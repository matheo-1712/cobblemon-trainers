package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon

/**
 * Who acts first, which is what turns "I am about to be knocked out" into a decision.
 *
 * Being threatened only matters if the threat lands before the answer does. A Pokémon that
 * outspeeds a lethal opponent still has a turn to use, and one that does not should stop setting
 * up and swing.
 *
 * Priority is read from the move, speed from the stat with its stages applied. Trick Room, Tailwind
 * and speed-changing items are not: reading them needs battle-wide state this layer does not
 * touch, and getting the order wrong costs one badly chosen move rather than a broken battle.
 */
object BattleSpeed {

    /**
     * Whether [self] acts before every one of [opponents], playing a move of [priority] against
     * their best.
     *
     * Ties go to the opponent. A speed tie is a coin flip in game, and an AI that assumes it wins
     * every flip plays recklessly; assuming it loses only makes it cautious.
     */
    fun movesFirst(
        self: BattlePokemon,
        priority: Int,
        opponents: List<BattlePokemon>
    ): Boolean = opponents.all { opponent ->
        val theirPriority = BattleDamage.threatPriority(self, opponent)
        when {
            priority > theirPriority -> true
            priority < theirPriority -> false
            else -> speed(self) > speed(opponent)
        }
    }

    private fun speed(pokemon: BattlePokemon): Double {
        val base = pokemon.effectedPokemon.getStat(Stats.SPEED).toDouble()
        return base * BattleDamage.stageMultiplier(pokemon.statChanges[Stats.SPEED] ?: 0)
    }
}
