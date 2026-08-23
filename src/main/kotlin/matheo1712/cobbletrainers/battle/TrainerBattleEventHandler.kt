package matheo1712.cobbletrainers.battle

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.advancement.TrainerDefeatedTrigger
import matheo1712.cobbletrainers.dialogue.TrainerDialogue
import matheo1712.cobbletrainers.trainers.TrainerCalls
import matheo1712.cobbletrainers.trainers.TrainerDefinition
import matheo1712.cobbletrainers.trainers.TrainerProgress
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerRewards
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.ChatFormatting
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity

/**
 * Drives what surrounds a trainer battle: the battle music, the trainer's closing words, the
 * rewards and the record of who beat whom, and the fate of a battle whose trainer leaves the
 * world.
 *
 * The battle actor of an NPC is an [com.cobblemon.mod.common.entity.npc.NPCBattleActor], which exposes the entity directly - no
 * need to look it up by UUID. Note that [com.cobblemon.mod.common.entity.npc.NPCBattleActor] does not extend `TrainerBattleActor`;
 * both derive separately from `AIBattleActor`.
 */
object TrainerBattleEventHandler {

    /** Registers the listeners. Called from [matheo1712.cobbletrainers.CobblemonTrainers.onInitialize]. */
    fun register() {
        CobblemonEvents.BATTLE_STARTED_POST.subscribe { event ->
            handleBattleStart(event.battle)
        }

        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            handleBattleVictory(event.battle, event.winners.toList())
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ -> endBattlesOf(entity) }
        ServerEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            if (entity.removalReason?.shouldDestroy() == true) endBattlesOf(entity)
        }
    }

    private fun handleBattleStart(battle: PokemonBattle) {
        val npc = resolveNpc(battle) ?: return
        val definition = TrainerRegistry.findByAspects(npc.aspects) ?: return
        val players = battle.players
        if (players.isEmpty()) return

        TrainerBattleMusic.start(definition.battle.music, players)
        // Cobblemon has no "battle over" event - see the class docs - so everything that has to
        // happen whichever way a battle ends hangs off this one handler.
        battle.onEndHandlers.add { ended ->
            TrainerBattleMusic.stop(definition.battle.music, ended.players)
            // A trainer who came when called leaves once they are done, win or lose. The delay
            // that grants is also what leaves room for their closing words: `end()` runs these
            // handlers before BATTLE_VICTORY is emitted, so the farewell box does not exist
            // yet - TrainerCalls waits on TrainerDialogue.isTalking rather than on this.
            TrainerCalls.dismissAfterBattle(npc)
        }

        // `messages.start` is not broadcast here any more: the trainer says it in their own
        // dialogue box, on the way in. See TrainerDialogue.
    }

    private fun handleBattleVictory(battle: PokemonBattle, winners: List<BattleActor>) {
        val npc = resolveNpc(battle) ?: return
        val trainerId = TrainerRegistry.idFromAspects(npc.aspects) ?: return
        val definition = TrainerRegistry.get(trainerId) ?: return
        val players = battle.players
        if (players.isEmpty()) return

        // Said to each player rather than broadcast: a box belongs to whoever is looking at it,
        // so in a battle with more than one player on the losing side nobody is congratulated
        // for someone else's win.
        val winningPlayers = winners.filterIsInstance<PlayerBattleActor>().mapNotNull { it.entity }.toSet()
        players.forEach { player ->
            TrainerDialogue.farewell(npc, player, definition, won = player in winningPlayers)
        }

        if (winningPlayers.isNotEmpty()) recordVictory(trainerId, definition, winners)
    }

    /**
     * Marks the trainer as beaten by every winning player, and hands out its rewards.
     */
    private fun recordVictory(
        trainerId: ResourceLocation,
        definition: TrainerDefinition,
        winners: List<BattleActor>
    ) {
        val players = winners.filterIsInstance<PlayerBattleActor>().mapNotNull { it.entity }
        if (players.isEmpty()) return

        val progress = TrainerProgress.of(players.first().server)
        players.forEach { player ->
            val firstWin = progress.recordVictory(trainerId, player.uuid)
            // After the record, never before: a `count` condition scores itself on it.
            TrainerDefeatedTrigger.trigger(player, trainerId)

            // Which rewards that covers is each reward's own business now - see
            // TrainerReward.firstWinOnly - so the whole list goes through either way.
            TrainerRewards.grant(player, definition.rewards, firstWin)
        }
    }

    /**
     * Ends the battles an entity was taking part in, when that entity is one of our trainers.
     * `stop()` is what Cobblemon's own `/stopbattle`
     */
    private fun endBattlesOf(entity: Entity) {
        if (entity !is NPCEntity) return
        if (TrainerRegistry.findByAspects(entity.aspects) == null) return

        entity.battleIds.toList().forEach { battleId ->
            val battle = BattleRegistry.getBattle(battleId) ?: return@forEach
            if (battle.ended) return@forEach

            val notice = CobblemonTrainers.lang("chat.trainer_gone").withStyle(ChatFormatting.GRAY)
            battle.players.forEach { it.sendSystemMessage(notice) }
            battle.stop()
        }
    }

    /** The NPC entity fighting in a battle, whether or not it is one of our trainers. */
    private fun resolveNpc(battle: PokemonBattle): NPCEntity? =
        battle.actors.filterIsInstance<NPCBattleActor>().firstOrNull()?.npc
}