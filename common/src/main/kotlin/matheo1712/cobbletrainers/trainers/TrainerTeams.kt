package matheo1712.cobbletrainers.trainers

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.resources.ResourceLocation

/**
 * One entry of a trainer's `teams`: the team it fights with up to [maxLevel], inclusive.
 *
 * There is no lower bound: the tier before this one gives it. A tier with no [maxLevel] never
 * caps, which makes it a catch-all for whatever the tiers before it left.
 */
data class TrainerTeamTier(
    val maxLevel: Int = Int.MAX_VALUE,
    val team: List<String> = emptyList()
)

/**
 * Which team a trainer fights with at a given level: the first of its `teams` whose `maxLevel`
 * covers it, and the plain `team` past all of them.
 *
 * One rule for the spawner, the battle, the team preview and the versus screen, so the team a
 * player is shown is the team they are about to fight. The species are the pack's to choose -
 * walking an evolution line back on its own would trip over every evolution that is not a level
 * (stones, friendship, trades), and would leave a Charmander with a Charizard's moves.
 */
object TrainerTeams {

    /** The key of the plain `team`, past every tier. */
    const val DEFAULT_KEY = "default"

    private fun tierFor(definition: TrainerDefinition, level: Int): TrainerTeamTier? =
        definition.teams.firstOrNull { level <= it.maxLevel }

    /**
     * Names the tier [level] falls in. It is the tier's `maxLevel` rather than its index: a
     * `/reload` that inserts a tier in front leaves the others their key, so a trainer already in
     * the world is not rebuilt for a tier it already has.
     */
    fun keyFor(definition: TrainerDefinition, level: Int): String =
        tierFor(definition, level)?.maxLevel?.toString() ?: DEFAULT_KEY

    /** The Showdown entries the trainer fights with at [level]. */
    fun teamFor(definition: TrainerDefinition, level: Int): List<String> =
        tierFor(definition, level)?.team ?: definition.team

    /** What is wrong with the tiers, in plain words. Empty for a trainer without any. */
    fun problems(definition: TrainerDefinition): List<String> {
        val tiers = definition.teams
        if (tiers.isEmpty()) return emptyList()

        val found = mutableListOf<String>()
        var highest = Int.MIN_VALUE
        tiers.forEachIndexed { index, tier ->
            if (tier.maxLevel <= highest) {
                found += "teams[$index] (maxLevel ${tier.maxLevel}) is never used: an earlier tier " +
                    "already covers up to level $highest. Tiers are tried in order."
            }
            if (tier.team.isEmpty()) {
                found += "teams[$index] (maxLevel ${tier.maxLevel}) has an empty team."
            }
            highest = maxOf(highest, tier.maxLevel)
        }
        if (definition.team.isNotEmpty() && highest == Int.MAX_VALUE) {
            found += "team is never used: a tier with no maxLevel already covers every level."
        }
        if (definition.team.isEmpty() && highest < Int.MAX_VALUE) {
            found += "team is empty, so past level $highest - the last tier - the trainer has no " +
                "Pokémon. Fill team, or drop maxLevel from the last tier."
        }
        return found
    }

    /** Logs [problems] at load, where the pack author reads them. */
    fun validate(id: ResourceLocation, definition: TrainerDefinition) {
        for (problem in problems(definition)) {
            CobblemonTrainers.LOGGER.warn("Trainer {}: {}", id, problem)
        }
    }
}
