package matheo1712.cobbletrainers

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

/**
 * Envoie les messages configurés au début et à la fin des combats de dresseurs.
 *
 * L'acteur d'un NPC en combat est un [NPCBattleActor], qui expose directement l'entité —
 * inutile de la retrouver par UUID. Attention : [NPCBattleActor] n'hérite pas de
 * `TrainerBattleActor`, les deux dérivent séparément de `AIBattleActor`.
 */
object TrainerBattleEventHandler {

    private val LOGGER = LoggerFactory.getLogger("CobbleTrainers/Events")

    /** Enregistre les listeners. Appelé depuis [CobblemonTrainers.onInitialize]. */
    fun register() {
        CobblemonEvents.BATTLE_STARTED_POST.subscribe { event ->
            handleBattleStart(event.battle.actors.toList())
        }

        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            handleBattleVictory(event.battle.actors.toList(), event.winners.toList())
        }

        LOGGER.info("Listeners d'événements de combat enregistrés.")
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

    /** Retrouve la définition du dresseur à partir de l'aspect posé au spawn. */
    private fun resolveTrainer(actors: List<BattleActor>): Pair<TrainerDefinition, NPCEntity>? {
        val npcActor = actors.filterIsInstance<NPCBattleActor>().firstOrNull() ?: return null
        val npcEntity = npcActor.npc

        val aspect = npcEntity.aspects.find { it.startsWith(CobblemonTrainers.TRAINER_ASPECT_PREFIX) }
            ?: return null
        val trainerId = ResourceLocation.tryParse(aspect.removePrefix(CobblemonTrainers.TRAINER_ASPECT_PREFIX))
            ?: return null

        val definition = TrainerRegistry.get(trainerId) ?: return null
        return definition to npcEntity
    }

    private fun broadcast(players: List<PlayerBattleActor>, trainerName: String, message: String?) {
        if (message.isNullOrBlank()) return
        players.mapNotNull { it.entity }.forEach { player ->
            player.sendSystemMessage(Component.literal("§6[$trainerName]§r $message"))
        }
    }
}
