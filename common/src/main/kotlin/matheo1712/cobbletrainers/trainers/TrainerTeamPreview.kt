package matheo1712.cobbletrainers.trainers

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.battle.TrainerLevelScaling
import matheo1712.cobbletrainers.network.TrainerTeamMember
import matheo1712.cobbletrainers.parser.ShowdownTeamParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * A trainer's team as a screen draws it: species, final aspects, level and nickname.
 *
 * Whether a player may see it is the caller's decision - the battle phone shows it only once the
 * trainer is beaten, an intro only when one of its layers draws a Pokémon. This only builds it.
 */
object TrainerTeamPreview {

    /**
     * Builds the team the way [TrainerSpawner] does. That detour is the only way to know the
     * aspects a Pokémon ends up with: the parser puts a form in the property string, and only
     * `create()` turns it into the aspect set the client needs to draw the right model. One
     * bad entry costs its slot, not the whole team.
     *
     * A trainer whose `battle.level` is automatic is shown at the level it would fight [player] at,
     * over every `Level:` line, exactly as [TrainerLevelScaling] sets it in battle.
     */
    fun of(definition: TrainerDefinition, trainerId: ResourceLocation, player: ServerPlayer): List<TrainerTeamMember> {
        val scaled = TrainerLevelScaling.levelFor(player, definition)
        return ShowdownTeamParser.parse(definition.team).mapNotNull { properties ->
            if (scaled != null) properties.level = scaled
            else if (properties.level == null) properties.level = definition.battle.level.value
            try {
                val pokemon = ShowdownTeamParser.createPokemon(properties)
                TrainerTeamMember(
                    species = pokemon.species.resourceIdentifier.toString(),
                    aspects = pokemon.aspects.toList(),
                    level = pokemon.level,
                    nickname = pokemon.nickname?.string.orEmpty()
                )
            } catch (e: Exception) {
                CobblemonTrainers.LOGGER.warn(
                    "Skipping a Pokémon of trainer {} in a team preview: {}",
                    trainerId,
                    e.message
                )
                null
            }
        }
    }
}
