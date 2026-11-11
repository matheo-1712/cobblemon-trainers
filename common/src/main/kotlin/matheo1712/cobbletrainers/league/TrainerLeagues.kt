package matheo1712.cobbletrainers.league

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.battle.TrainerBattleInteraction
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

/**
 * In-memory registry of the leagues, filled from datapacks like the trainers and the intros.
 *
 * Any pack may provide `data/<namespace>/cobblemontrainers/leagues/<path>.json`. A league names
 * trainers, so it is read **after** [TrainerRegistry]: one that names a trainer no pack
 * provides is dropped whole, with a word in the log - a league missing a link cannot be
 * finished, and a league nobody can finish is worse than none.
 *
 * **A trainer belongs to one league at most.** Its place in a league is what decides whether it
 * accepts a challenge, and two leagues answering that for the same trainer would have to
 * disagree sooner or later. The first league to claim a trainer, in ID order, keeps it.
 */
object TrainerLeagues {

    /** Directory scanned inside datapacks, under [CobblemonTrainers.DATAPACK_ROOT]. */
    const val DATAPACK_DIRECTORY = "${CobblemonTrainers.DATAPACK_ROOT}/leagues"

    private const val JSON_EXTENSION = ".json"

    private val GSON: Gson = GsonBuilder().create()

    private val leagues = mutableMapOf<ResourceLocation, LeagueEntry>()

    /** Trainer ID -> the league it belongs to and its place in it. */
    private val memberships = mutableMapOf<ResourceLocation, Membership>()

    /** A trainer's seat in a league: which one, and how many are fought before it. */
    class Membership(val league: LeagueEntry, val index: Int)

    fun reload(manager: ResourceManager) {
        leagues.clear()
        memberships.clear()

        manager.listResources(DATAPACK_DIRECTORY) { path -> path.path.endsWith(JSON_EXTENSION) }
            .toSortedMap(compareBy { it.toString() })
            .forEach { (location, resource) ->
                val id = ResourceLocation.tryBuild(
                    location.namespace,
                    location.path.removePrefix("$DATAPACK_DIRECTORY/").removeSuffix(JSON_EXTENSION)
                ) ?: return@forEach

                try {
                    val definition = resource.open().use { stream ->
                        stream.bufferedReader().use { GSON.fromJson(it, TrainerLeague::class.java) }
                    }
                    resolve(id, definition)?.let(::register)
                } catch (e: Exception) {
                    CobblemonTrainers.LOGGER.error("Failed to load league {}", location, e)
                }
            }

        CobblemonTrainers.LOGGER.info(
            "Loaded {} league(s): {}",
            leagues.size,
            leagues.keys.joinToString(", ")
        )
    }

    /**
     * Turns the names a pack wrote into trainers, or null when the league cannot stand.
     *
     * Everything that would make it unfinishable is refused here, at load, where the pack's
     * author reads their log - never in front of a player three battles in.
     */
    private fun resolve(id: ResourceLocation, definition: TrainerLeague): LeagueEntry? {
        val members = mutableListOf<ResourceLocation>()

        for (declared in definition.trainers) {
            val trainerId = trainerIdOf(declared, id.namespace)
            if (trainerId == null || TrainerRegistry.get(trainerId) == null) {
                CobblemonTrainers.LOGGER.error(
                    "League {} names the trainer '{}', which no datapack provides: the league is ignored",
                    id, declared
                )
                return null
            }
            if (trainerId in members) {
                CobblemonTrainers.LOGGER.warn(
                    "League {} names the trainer {} more than once, keeping its first place only",
                    id, trainerId
                )
                continue
            }
            memberships[trainerId]?.let { taken ->
                CobblemonTrainers.LOGGER.error(
                    "League {} names the trainer {}, which already belongs to {}: the league is ignored",
                    id, trainerId, taken.league.id
                )
                return null
            }
            members += trainerId
        }

        if (members.isEmpty()) {
            CobblemonTrainers.LOGGER.error("League {} names no trainer: the league is ignored", id)
            return null
        }

        val entry = LeagueEntry(id, definition, members)
        warnAboutMembers(entry)
        return entry
    }

    private fun register(entry: LeagueEntry) {
        leagues[entry.id] = entry
        entry.members.forEachIndexed { index, trainerId ->
            memberships[trainerId] = Membership(entry, index)
        }
    }

    /** What a pack can write that loads, and still does not do what its author meant. */
    private fun warnAboutMembers(entry: LeagueEntry) {
        if (entry.definition.heal !in 0.0..1.0) {
            CobblemonTrainers.LOGGER.warn(
                "League {} declares heal {}, read as {}: it is a share of the maximum HP, from 0 to 1",
                entry.id, entry.definition.heal, entry.heal
            )
        }

        for (trainerId in entry.members) {
            val trainer = TrainerRegistry.get(trainerId) ?: continue

            // A league is lost and started again. A link that fights once only turns the
            // first lost run into the last one.
            if (!trainer.progress.allowsRematch) {
                CobblemonTrainers.LOGGER.warn(
                    "League {}: {} refuses rematches, so a player who loses further down can never start over",
                    entry.id, trainerId
                )
            }

            // Such a format battles on healed copies: what happens to them never reaches the
            // real team, so there is nothing to carry into the next battle.
            val format = trainer.battle.format
            if (entry.carriesDamage && format.isNotBlank() &&
                TrainerBattleInteraction.battleFormatOf(format).adjustLevel > 0
            ) {
                CobblemonTrainers.LOGGER.warn(
                    "League {}: {} battles in '{}', which heals both sides - the damage of that battle is not carried over",
                    entry.id, trainerId, format
                )
            }
        }
    }

    /** A bare path is one of the league's own pack; anything with a colon is a full ID. */
    private fun trainerIdOf(declared: String, namespace: String): ResourceLocation? {
        val trimmed = declared.trim().lowercase()
        if (trimmed.isEmpty()) return null

        return if (trimmed.contains(':')) {
            ResourceLocation.tryParse(trimmed)
        } else {
            ResourceLocation.tryBuild(namespace, trimmed)
        }
    }

    fun get(id: ResourceLocation): LeagueEntry? = leagues[id]

    fun ids(): Set<ResourceLocation> = leagues.keys

    /** The league a trainer is fought in and its place there, or null for a free trainer. */
    fun membershipOf(trainerId: ResourceLocation): Membership? = memberships[trainerId]
}
