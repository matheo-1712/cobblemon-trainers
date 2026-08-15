package matheo1712.cobbletrainers

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * Sends the configured messages when a trainer battle starts and ends.
 *
 * The battle actor of an NPC is an [NPCBattleActor], which exposes the entity directly — no
 * need to look it up by UUID. Note that [NPCBattleActor] does not extend `TrainerBattleActor`;
 * both derive separately from `AIBattleActor`.
 */
object TrainerBattleEventHandler {

    /** Registers the listeners. Called from [CobblemonTrainers.onInitialize]. */
    fun register() {
        CobblemonEvents.BATTLE_STARTED_POST.subscribe { event ->
            handleBattleStart(event.battle.actors.toList())
        }

        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            handleBattleVictory(event.battle.actors.toList(), event.winners.toList())
        }
    }

    private fun handleBattleStart(actors: List<BattleActor>) {
        val (definition, _) = resolveTrainer(actors) ?: return
        val players = actors.filterIsInstance<PlayerBattleActor>()
        broadcast(players, definition.name, definition.battleStartMessage)
    }

    private fun handleBattleVictory(actors: List<BattleActor>, winners: List<BattleActor>) {
        val (definition, _) = resolveTrainer(actors) ?: return
        val players = actors.filterIsInstance<PlayerBattleActor>()
        if (players.isEmpty()) return

        val playerWon = players.any { it in winners }
        val message = if (playerWon) definition.battleEndWinMessage else definition.battleEndLoseMessage
        broadcast(players, definition.name, message)
    }

    /** Finds the trainer definition from the aspect applied at spawn time. */
    private fun resolveTrainer(actors: List<BattleActor>): Pair<TrainerDefinition, NPCEntity>? {
        val npcActor = actors.filterIsInstance<NPCBattleActor>().firstOrNull() ?: return null
        val npcEntity = npcActor.npc
        val definition = TrainerRegistry.findByAspects(npcEntity.aspects) ?: return null
        return definition to npcEntity
    }

    /**
     * Both the trainer name and the message are treated as translation keys, resolved on the
     * client. Plain text falls back to itself when no translation exists.
     */
    private fun broadcast(players: List<PlayerBattleActor>, trainerName: String, message: String?) {
        if (message.isNullOrBlank()) return

        val name = Component.translatable(trainerName).withStyle(ChatFormatting.GOLD)
        val line = CobblemonTrainers.lang("chat.trainer_message", name, Component.translatable(message))

        players.mapNotNull { it.entity }.forEach { player ->
            player.sendSystemMessage(line)
        }
    }
}
