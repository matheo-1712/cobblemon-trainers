package matheo1712.cobbletrainers.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.party
import matheo1712.cobbletrainers.trainers.TrainerDefinition
import net.minecraft.server.level.ServerPlayer

/**
 * The level a trainer with an automatic `battle.level` fights at: the player's strongest Pokémon,
 * shifted by the offset, then held between the bounds.
 *
 * One function answers for the battle, the battle phone and the versus screen, so the level a
 * player is shown is the level they are about to fight.
 *
 * The strongest is read over the whole party, fainted Pokémon included. Leaving them out would
 * let a player knock out their best Pokémon, challenge a trainer scaled to the rest of the team,
 * and revive it mid-battle.
 */
object TrainerLevelScaling {

    /**
     * The level every Pokémon of this trainer takes against [player], or null for a trainer
     * whose levels are its own - a fixed level, an empty party, or a level-adjusting format,
     * which sets both sides to its own level anyway.
     */
    fun levelFor(player: ServerPlayer, definition: TrainerDefinition): Int? {
        val auto = definition.battle.level.takeIf { it.auto } ?: return null
        if (TrainerBattleInteraction.battleFormatOf(definition.battle.format).adjustLevel > 0) return null

        val strongest = player.party().maxOfOrNull { it.level } ?: return null

        // The bounds are applied one after the other rather than through coerceIn, which throws
        // when they cross: with min above max, max wins - and the load already said so.
        var level = strongest + auto.offset
        auto.min?.let { level = maxOf(level, it) }
        auto.max?.let { level = minOf(level, it) }
        return level.coerceIn(1, Cobblemon.config.maxPokemonLevel)
    }

    /**
     * Sets the trainer's team to the level [levelFor] answers, right before the battle.
     *
     * The entity's own store is changed, since that is what `pvn` battles with. Nothing is lost
     * by it: the next battle sets the level again, for whoever is challenging. `setLevel` keeps
     * the share of HP a Pokémon had, so a trainer with `healParty: false` stays as worn down as
     * it was.
     */
    fun apply(npc: NPCEntity, player: ServerPlayer, definition: TrainerDefinition) {
        val level = levelFor(player, definition) ?: return
        npc.party?.forEach { pokemon ->
            if (pokemon.level != level) pokemon.level = level
        }
    }
}
