package matheo1712.cobbletrainers

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Drives what surrounds a trainer battle: the configured messages, and the battle music.
 *
 * The battle actor of an NPC is an [NPCBattleActor], which exposes the entity directly — no
 * need to look it up by UUID. Note that [NPCBattleActor] does not extend `TrainerBattleActor`;
 * both derive separately from `AIBattleActor`.
 *
 * Cobblemon has no single "battle ended" event, so the end is caught twice: `BATTLE_VICTORY`
 * when someone wins, `BATTLE_FLED` when the player runs. Missing one would leave the music
 * playing until the track ends on its own.
 */
object TrainerBattleEventHandler {

    /** Registers the listeners. Called from [CobblemonTrainers.onInitialize]. */
    fun register() {
        CobblemonEvents.BATTLE_STARTED_POST.subscribe { event ->
            handleBattleStart(event.battle)
        }

        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            handleBattleVictory(event.battle, event.winners.toList())
        }

        CobblemonEvents.BATTLE_FLED.subscribe { event ->
            handleBattleEnd(event.battle)
        }
    }

    private fun handleBattleStart(battle: PokemonBattle) {
        val definition = resolveTrainer(battle) ?: return
        val players = battle.players
        if (players.isEmpty()) return

        TrainerBattleMusic.start(definition.battleMusic, players)
        broadcast(players, definition.name, definition.battleStartMessage)
    }

    private fun handleBattleVictory(battle: PokemonBattle, winners: List<BattleActor>) {
        val definition = resolveTrainer(battle) ?: return
        val players = battle.players
        if (players.isEmpty()) return

        TrainerBattleMusic.stop(definition.battleMusic, players)

        val playerWon = winners.any { it is PlayerBattleActor }
        val message = if (playerWon) definition.battleEndWinMessage else definition.battleEndLoseMessage
        broadcast(players, definition.name, message)
    }

    /** The player ran away: no message to send, only the music to cut. */
    private fun handleBattleEnd(battle: PokemonBattle) {
        val definition = resolveTrainer(battle) ?: return
        TrainerBattleMusic.stop(definition.battleMusic, battle.players)
    }

    /** Finds the trainer definition from the aspect applied at spawn time. */
    private fun resolveTrainer(battle: PokemonBattle): TrainerDefinition? {
        val npcActor = battle.actors.filterIsInstance<NPCBattleActor>().firstOrNull() ?: return null
        return TrainerRegistry.findByAspects(npcActor.npc.aspects)
    }

    /**
     * Both the trainer name and the message are treated as translation keys, resolved on the
     * client. Plain text falls back to itself when no translation exists.
     */
    private fun broadcast(players: List<ServerPlayer>, trainerName: String, message: String?) {
        if (message.isNullOrBlank()) return

        val name = Component.translatable(trainerName).withStyle(ChatFormatting.GOLD)
        val line = CobblemonTrainers.lang("chat.trainer_message", name, Component.translatable(message))

        players.forEach { player ->
            player.sendSystemMessage(line)
        }
    }
}
