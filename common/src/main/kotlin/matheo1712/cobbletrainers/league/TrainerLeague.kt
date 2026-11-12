package matheo1712.cobbletrainers.league

import matheo1712.cobbletrainers.trainers.TrainerReward
import matheo1712.cobbletrainers.trainers.TrainerSkin
import net.minecraft.resources.ResourceLocation

/**
 * A league as a pack writes it: a tournament. A host stands in the world, a player signs up
 * with them, and the trainers then come one after the other with nothing healed in between.
 *
 * Every field has a default, like a trainer's, so Gson goes through the no-argument constructor
 * and a partial file stays valid.
 *
 * @param name What the league is called, passed to `Component.translatable` like a trainer's
 *   name - plain text shows as written, a key is translated by the client.
 * @param host The character a player signs up with. Not a trainer: it has no team and no
 *   battle of its own, which is why it is described here rather than under `trainers/`.
 * @param trainers The trainers to beat, in order. A bare path belongs to the league's own
 *   namespace, so a pack rarely has to spell its namespace out. They are ordinary trainers:
 *   the league spawns each of them when their turn comes and takes them away afterwards.
 * @param rewards What finishing the league is worth, on top of what each trainer hands over.
 *   Same entries as a trainer's, `firstWinOnly` included - there counted on the league.
 */
data class TrainerLeague(
    val name: String = "",
    val host: LeagueHostDefinition = LeagueHostDefinition(),
    val trainers: List<String> = emptyList(),
    val rewards: List<TrainerReward> = emptyList()
)

/**
 * The host of a league: how they look and what they say.
 *
 * @param name Their name plate, translatable like a trainer's.
 * @param skin Same block as a trainer's `skin`, resolved the same way.
 */
data class LeagueHostDefinition(
    val name: String = "",
    val skin: TrainerSkin = TrainerSkin(),
    val messages: LeagueHostMessages = LeagueHostMessages()
)

/**
 * The host's lines, every one optional - the mod has a sentence of its own for each moment.
 *
 * @param greeting Said on right-click, above the Enter / Cancel row.
 * @param decline Said to whoever presses Cancel.
 * @param between Said between two battles, above the Continue / Forfeit row.
 * @param win Said once the last trainer is beaten.
 * @param lose Said to a player whose run has just ended short of that.
 */
data class LeagueHostMessages(
    val greeting: String = "",
    val decline: String = "",
    val between: String = "",
    val win: String = "",
    val lose: String = ""
)

/**
 * A [TrainerLeague] once its trainers have been found: what the rest of the mod reads.
 *
 * @param members The trainers in fighting order, every one of them loaded.
 */
class LeagueEntry(
    val id: ResourceLocation,
    val definition: TrainerLeague,
    val members: List<ResourceLocation>
) {
    /** The league's own name, or its ID for a pack that gave it none. */
    val displayName: String
        get() = definition.name.takeIf { it.isNotBlank() } ?: id.toString()

    /** The host's name, or the league's for a pack that left it out. */
    val hostName: String
        get() = definition.host.name.takeIf { it.isNotBlank() } ?: displayName
}
