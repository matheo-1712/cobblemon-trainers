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
 * Being named by a league changes nothing about a trainer. The league spawns its own copy when
 * that trainer's turn comes; one standing elsewhere in the world is still an ordinary trainer.
 */
object TrainerLeagues {

    /** Directory scanned inside datapacks, under [CobblemonTrainers.DATAPACK_ROOT]. */
    const val DATAPACK_DIRECTORY = "${CobblemonTrainers.DATAPACK_ROOT}/leagues"

    private const val JSON_EXTENSION = ".json"

    private val GSON: Gson = GsonBuilder().create()

    private val leagues = mutableMapOf<ResourceLocation, LeagueEntry>()

    fun reload(manager: ResourceManager) {
        leagues.clear()

        manager.listResources(DATAPACK_DIRECTORY) { path -> path.path.endsWith(JSON_EXTENSION) }
            .forEach { (location, resource) ->
                val id = ResourceLocation.tryBuild(
                    location.namespace,
                    location.path.removePrefix("$DATAPACK_DIRECTORY/").removeSuffix(JSON_EXTENSION)
                ) ?: return@forEach

                try {
                    val definition = resource.open().use { stream ->
                        stream.bufferedReader().use { GSON.fromJson(it, TrainerLeague::class.java) }
                    }
                    resolve(id, definition)?.let { leagues[id] = it }
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
        val members = definition.trainers.map { declared ->
            val trainerId = trainerIdOf(declared, id.namespace)
            if (trainerId == null || TrainerRegistry.get(trainerId) == null) {
                CobblemonTrainers.LOGGER.error(
                    "League {} names the trainer '{}', which no datapack provides: the league is ignored",
                    id, declared
                )
                return null
            }
            trainerId
        }

        if (members.isEmpty()) {
            CobblemonTrainers.LOGGER.error("League {} names no trainer: the league is ignored", id)
            return null
        }

        definition.host.skin.validate(id)

        for (trainerId in members.distinct()) {
            val trainer = TrainerRegistry.get(trainerId) ?: continue
            // Such a format battles on healed copies: what happens to them never reaches the
            // real team, so there is nothing to carry into the next battle.
            val format = trainer.battle.format
            if (format.isNotBlank() && TrainerBattleInteraction.battleFormatOf(format).adjustLevel > 0) {
                CobblemonTrainers.LOGGER.warn(
                    "League {}: {} battles in '{}', which heals both sides - the damage of that battle is not carried over",
                    id, trainerId, format
                )
            }
        }

        return LeagueEntry(id, definition, members)
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

    fun ids(): Set<ResourceLocation> = leagues.keys.toSet()

    /**
     * Resolves the ID typed in a command, the way [TrainerRegistry.resolveId] does: Brigadier
     * hands a bare name over under the `minecraft` namespace, so that one is matched on its path.
     */
    fun resolveId(input: ResourceLocation): ResourceLocation? {
        if (input in leagues) return input
        if (input.namespace != ResourceLocation.DEFAULT_NAMESPACE) return null
        return leagues.keys.firstOrNull { it.path == input.path }
    }

    /** The league a host stands for, read from the aspect it was spawned with. */
    fun idFromAspects(aspects: Collection<String>): ResourceLocation? {
        val aspect = aspects.find { it.startsWith(CobblemonTrainers.LEAGUE_HOST_ASPECT_PREFIX) } ?: return null
        return ResourceLocation.tryParse(aspect.removePrefix(CobblemonTrainers.LEAGUE_HOST_ASPECT_PREFIX))
    }
}
