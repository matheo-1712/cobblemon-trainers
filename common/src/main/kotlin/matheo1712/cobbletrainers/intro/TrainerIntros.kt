package matheo1712.cobbletrainers.intro

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

/**
 * In-memory registry of the intros, filled from datapacks like the trainers are.
 *
 * Any pack may provide `data/<namespace>/cobblemontrainers/intro/<path>.json`, the mod
 * included: `bw`, the screen the mod ships, is one of those files and nothing more. That is
 * deliberate - the default is written in the same format packs get, so what the format cannot
 * say shows up in our own intro first.
 *
 * The folder is the trainers' neighbour, both under the mod's one
 * [root][CobblemonTrainers.DATAPACK_ROOT]: an intro is not a trainer, so it cannot be filed
 * among them, but it is the same pack declaring the same league.
 *
 * Reloading is driven by the same server resource manager listener, so `/reload` picks up an
 * edited intro. A battle already showing one keeps the version it was given: the intro travels
 * to the client with the packet that raises it.
 */
object TrainerIntros {

    /** Directory scanned inside datapacks, under [CobblemonTrainers.DATAPACK_ROOT]. */
    const val DATAPACK_DIRECTORY = "${CobblemonTrainers.DATAPACK_ROOT}/intro"

    /**
     * Where the intros used to sit, kept for one reason: a pack that never moved loads
     * nothing, and the reload says so rather than leaving the author to guess. See
     * [matheo1712.cobbletrainers.CobblemonTrainers.onInitialize].
     */
    const val LEGACY_DIRECTORY = "cobblemontrainers_intros"

    private const val JSON_EXTENSION = ".json"

    private val GSON: Gson = GsonBuilder().create()

    private val intros = mutableMapOf<ResourceLocation, TrainerIntro>()

    fun reload(manager: ResourceManager) {
        intros.clear()

        manager.listResources(DATAPACK_DIRECTORY) { path -> path.path.endsWith(JSON_EXTENSION) }
            .forEach { (location, resource) ->
                val id = ResourceLocation.fromNamespaceAndPath(
                    location.namespace,
                    location.path
                        .removePrefix("$DATAPACK_DIRECTORY/")
                        .removeSuffix(JSON_EXTENSION)
                )

                try {
                    resource.open().use { stream ->
                        stream.bufferedReader().use { reader ->
                            val intro = GSON.fromJson(reader, TrainerIntro::class.java)
                            intros[id] = intro.validated(id)
                        }
                    }
                } catch (e: Exception) {
                    CobblemonTrainers.LOGGER.error("Failed to load intro {}", location, e)
                }
            }

        CobblemonTrainers.LOGGER.info(
            "Loaded {} intro(s): {}",
            intros.size,
            intros.keys.joinToString(", ")
        )
    }

    /** The intro under that ID, or null - which is a trainer whose battle simply opens. */
    fun get(id: ResourceLocation): TrainerIntro? = intros[id]

    /**
     * What a trainer's `battle.intro` names.
     *
     * A bare name is one of ours, so `"bw"` keeps meaning `cobblemon-trainers:bw` - the mod's
     * own screen is the one nobody should have to spell out.
     */
    fun idOf(declared: String): ResourceLocation? {
        val trimmed = declared.trim().lowercase()
        if (trimmed.isEmpty()) return null

        return if (trimmed.contains(':')) {
            ResourceLocation.tryParse(trimmed)
        } else {
            ResourceLocation.tryParse("${CobblemonTrainers.MOD_ID}:$trimmed")
        }
    }

    /** Every ID loaded, for an error message that can name what a pack could have meant. */
    fun ids(): Set<ResourceLocation> = intros.keys
}
