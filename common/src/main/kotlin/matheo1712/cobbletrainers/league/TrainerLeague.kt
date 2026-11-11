package matheo1712.cobbletrainers.league

import matheo1712.cobbletrainers.trainers.TrainerReward
import net.minecraft.resources.ResourceLocation

/**
 * A league as a pack writes it: trainers fought one after the other, with nothing healed in
 * between unless the league says so.
 *
 * Every field has a default, like a trainer's, so Gson goes through the no-argument constructor
 * and a partial file stays valid.
 *
 * @param name What the league is called, passed to `Component.translatable` like a trainer's
 *   name - plain text shows as written, a key is translated by the client.
 * @param trainers The trainers to beat, in order. A bare path belongs to the league's own
 *   namespace, so a pack rarely has to spell its namespace out.
 * @param heal The share of its maximum HP each Pokémon still standing gets back between two
 *   battles, from `0` (nothing, the default) to `1` (the league no longer watches the team at
 *   all). See [LeagueRuns].
 * @param rewards What finishing the league is worth, on top of what each trainer hands over.
 *   Same entries as a trainer's, `firstWinOnly` included - there counted on the league.
 */
data class TrainerLeague(
    val name: String = "",
    val trainers: List<String> = emptyList(),
    val heal: Double = 0.0,
    val rewards: List<TrainerReward> = emptyList()
)

/**
 * A [TrainerLeague] once its trainers have been found: what the rest of the mod reads.
 *
 * @param members The trainers in fighting order, every one of them loaded and none twice.
 */
class LeagueEntry(
    val id: ResourceLocation,
    val definition: TrainerLeague,
    val members: List<ResourceLocation>
) {
    /** The league's own name, or its ID for a pack that gave it none. */
    val displayName: String
        get() = definition.name.takeIf { it.isNotBlank() } ?: id.toString()

    /** [TrainerLeague.heal], kept inside the range it means something in. */
    val heal: Double
        get() = definition.heal.coerceIn(0.0, 1.0)

    /** Whether the team is carried from one battle to the next at all. */
    val carriesDamage: Boolean
        get() = heal < 1.0
}
