package matheo1712.cobbletrainers

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

/**
 * Drives what surrounds a trainer battle: the configured messages, the battle music, and the
 * fate of a battle whose trainer leaves the world.
 *
 * The battle actor of an NPC is an [NPCBattleActor], which exposes the entity directly — no
 * need to look it up by UUID. Note that [NPCBattleActor] does not extend `TrainerBattleActor`;
 * both derive separately from `AIBattleActor`.
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

        // A trainer that dies or is removed while battling would otherwise leave its opponent
        // stuck in a fight against an actor whose entity no longer exists. Death fires first and
        // covers `/kill` and a player beating the NPC up; removal covers everything else
        // (`/kill` on a corpse, a datapack discarding the entity, …) and is filtered on
        // `shouldDestroy` so a chunk unload does not count as the trainer leaving.
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ -> endBattlesOf(entity) }
        ServerEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            if (entity.removalReason?.shouldDestroy() == true) endBattlesOf(entity)
        }
    }

    private fun handleBattleStart(battle: PokemonBattle) {
        val definition = resolveTrainer(battle) ?: return
        val players = battle.players
        if (players.isEmpty()) return

        TrainerBattleMusic.start(definition.battleMusic, players)

        // Cobblemon has no "battle ended" event, and the two it does fire only cover a victory
        // and a flight — `/stopbattle` and a forfeit go through neither, which used to leave the
        // music playing until the track ran out. Every way out ends in `PokemonBattle.end()`,
        // which calls `BattleRegistry.closeBattle`, which runs these handlers: the one place the
        // music is guaranteed to be cut.
        battle.onEndHandlers.add { ended ->
            TrainerBattleMusic.stop(definition.battleMusic, ended.players)
        }

        broadcast(players, definition.name, definition.battleStartMessage)
    }

    private fun handleBattleVictory(battle: PokemonBattle, winners: List<BattleActor>) {
        val definition = resolveTrainer(battle) ?: return
        val players = battle.players
        if (players.isEmpty()) return

        val playerWon = winners.any { it is PlayerBattleActor }
        val message = if (playerWon) definition.battleEndWinMessage else definition.battleEndLoseMessage
        broadcast(players, definition.name, message)
    }

    /**
     * Ends the battles an entity was taking part in, when that entity is one of our trainers.
     *
     * `stop()` is what Cobblemon's own `/stopbattle` calls: it forces a tie in Showdown and runs
     * the regular end, so the players get their party back and the music stops like any other
     * ending. Battles already closed are gone from the registry, which makes this idempotent —
     * death and removal both firing for the same trainer is the normal case.
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
