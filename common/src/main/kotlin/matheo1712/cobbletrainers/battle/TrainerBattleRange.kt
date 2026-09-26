package matheo1712.cobbletrainers.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Ends a trainer battle whose players have all walked away, on the very distance a battle
 * against a wild Pokémon uses.
 *
 * Cobblemon already knows how to do this - `PokemonBattle.checkFlee` - but its own tick only
 * calls it `if (isPvW)`: a battle against an NPC is never measured at all. So a player could
 * walk to the other end of the world, or into another dimension, and stay locked in a battle
 * with a trainer nobody could see any more. That is issue #36.
 *
 * The rule here is a transcription of `checkFlee`, so that walking away from a trainer feels
 * like walking away from a wild Pokémon:
 * - the distance is [Cobblemon.config]'s `defaultFleeDistance`, the same field
 *   `BattleBuilder.pve` hands to a wild actor, so a server that tunes one tunes both;
 * - it is measured from the trainer to the **players** of the battle, and the nearest one is
 *   the one that counts - which is what keeps a double battle alive while either player is
 *   still there;
 * - a player in another dimension counts as gone, being in no way within reach.
 *
 * Points worth not rediscovering:
 * - **The tick is gated on `dispatches`, like Cobblemon's.** Battle actions queue up there,
 *   and cutting a battle off half-way through one is how a `>forcetie` lands on a battle that
 *   was still speaking.
 * - **The trainer is held as an entity, and its position read even once it is removed.** A
 *   chunk unloading is precisely the case this fixes: `ServerEntityEvents.ENTITY_UNLOAD` is
 *   filtered on `removalReason.shouldDestroy()` in
 *   [TrainerBattleEventHandler], so an unloaded trainer never ends its own battle. Its last
 *   known position is the right one to measure from - the player is far from where the
 *   trainer stood, which is the whole question.
 * - **Players are read from the actors, not from `battle.players`.** That list is a snapshot
 *   taken when the battle was built, so it holds the `ServerPlayer` a reconnection has since
 *   replaced; `PlayerBattleActor.entity` looks the player up by UUID every time.
 * - **Battles are stopped outside the iteration.** `stop()` runs the battle's end handlers,
 *   and one of those calls [forget] - which reaches straight back into the map being walked.
 */
object TrainerBattleRange {

    /** A trainer battle being measured, and the trainer to measure it from. */
    private class Watch(val battle: PokemonBattle, val trainer: NPCEntity)

    /** Keyed by battle id rather than by the battle itself, so [forget] never misses one. */
    private val watched = mutableMapOf<UUID, Watch>()

    /** Starts measuring a battle. Called from [TrainerBattleEventHandler] as it begins. */
    fun watch(battle: PokemonBattle, trainer: NPCEntity) {
        watched[battle.battleId] = Watch(battle, trainer)
    }

    /** Stops measuring one, however it ended. */
    fun forget(battle: PokemonBattle) {
        watched.remove(battle.battleId)
    }

    /** Drops everything, for a server whose battles are not going to end any more. */
    fun clear() {
        watched.clear()
    }

    /** Drops what has ended, and ends what has gone out of reach. */
    fun tick() {
        if (watched.isEmpty()) return

        val gone = mutableListOf<PokemonBattle>()
        watched.values.removeIf { watch ->
            val battle = watch.battle
            if (battle.ended) return@removeIf true
            // Not started yet, or still working through its queue: nothing to judge this tick.
            if (!battle.started || battle.dispatches.isNotEmpty()) return@removeIf false
            if (!outOfReach(battle, watch.trainer)) return@removeIf false

            gone += battle
            true
        }

        gone.forEach { battle ->
            val notice = CobblemonTrainers.lang("chat.too_far").withStyle(ChatFormatting.GRAY)
            players(battle).forEach { it.sendSystemMessage(notice) }
            battle.stop()
        }
    }

    /** Whether the trainer has nobody left within flee distance, in their own level. */
    private fun outOfReach(battle: PokemonBattle, trainer: NPCEntity): Boolean {
        val level = trainer.level()
        val position = trainer.position()
        val reach = Cobblemon.config.defaultFleeDistance.toDouble()

        return players(battle)
            .filter { it.level() === level }
            .none { position.distanceTo(it.position()) < reach }
    }

    /** The players still in the world on either side of the battle. */
    private fun players(battle: PokemonBattle): List<ServerPlayer> =
        battle.actors.filterIsInstance<PlayerBattleActor>().mapNotNull { it.entity }
}
