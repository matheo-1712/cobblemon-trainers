package matheo1712.cobbletrainers.battle

import com.cobblemon.mod.common.battles.BattleRegistry
import java.util.UUID

/**
 * Whether a trainer is really in a battle.
 *
 * `NPCEntity.battleIds` is not the answer on its own: Cobblemon leaves a closed battle's id there
 * for a few seconds after `PokemonBattle.end()`. A guard that read it alone refused to rebuild
 * the team of a trainer challenged again right away - the level tier stayed the previous one.
 * The registry drops a battle the moment it closes, so an id it no longer holds is no battle.
 */
object TrainerBattleState {

    fun inLiveBattle(
        battleIds: Collection<UUID>,
        isLive: (UUID) -> Boolean = { BattleRegistry.getBattle(it) != null }
    ): Boolean = battleIds.any(isLive)
}
