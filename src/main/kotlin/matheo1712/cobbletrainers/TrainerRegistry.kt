package matheo1712.cobbletrainers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import java.io.File

/**
 * In-memory registry of the available trainers.
 *
 * Trainers come from datapacks only: any pack may provide
 * `data/<namespace>/cobblemontrainers/trainers/<name>.json`, including the mod itself. The trainer
 * ID is `<namespace>:<name>`. A pack loaded later overrides a trainer with the same ID,
 * following the usual datapack stacking rules.
 *
 * As in Cobblemon's own registries, only the file name matters: a trainer stored at
 * `cobblemontrainers/trainers/kanto/red.json` gets the ID `<namespace>:red`.
 *
 * Reloading is driven by the server resource manager (see [CobblemonTrainers.onInitialize]),
 * so `/reload` reloads trainers too.
 */
object TrainerRegistry {

    /**
     * Directory scanned inside datapacks, under `data/<namespace>/`. Everything the mod reads
     * from a pack lives under `cobblemontrainers/`, so a pack that also declares trainers for
     * another mod cannot collide with ours.
     */
    const val DATAPACK_DIRECTORY = "cobblemontrainers/trainers"

    private const val JSON_EXTENSION = ".json"

    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private val trainers = mutableMapOf<ResourceLocation, TrainerDefinition>()

    fun reload(manager: ResourceManager) {
        trainers.clear()

        manager.listResources(DATAPACK_DIRECTORY) { path -> path.path.endsWith(JSON_EXTENSION) }
            .forEach { (location, resource) ->
                val id = buildId(location.namespace, File(location.path).nameWithoutExtension)
                if (id == null) {
                    CobblemonTrainers.LOGGER.error("Invalid trainer name, skipping file: {}", location)
                    return@forEach
                }
                try {
                    resource.open().use { stream ->
                        stream.bufferedReader().use { reader ->
                            trainers[id] = GSON.fromJson(reader, TrainerDefinition::class.java)
                        }
                    }
                } catch (e: Exception) {
                    CobblemonTrainers.LOGGER.error("Failed to load trainer {}", location, e)
                }
            }

        CobblemonTrainers.LOGGER.info(
            "Loaded {} trainer(s): {}",
            trainers.size,
            trainers.keys.joinToString(", ")
        )
    }

    /** Builds an ID, returning null instead of throwing when the name has illegal characters. */
    private fun buildId(namespace: String, name: String): ResourceLocation? =
        ResourceLocation.tryParse("$namespace:$name")

    /**
     * Resolves the ID typed in the command.
     *
     * Brigadier always yields a complete [ResourceLocation]: a bare name arrives under the
     * `minecraft` namespace. So try the exact ID first, then, if the namespace is the default
     * one, the mod namespace and finally any namespace holding that name.
     */
    fun resolveId(input: ResourceLocation): ResourceLocation? {
        if (input in trainers) return input
        if (input.namespace != ResourceLocation.DEFAULT_NAMESPACE) return null

        buildId(CobblemonTrainers.MOD_ID, input.path)
            ?.takeIf { it in trainers }
            ?.let { return it }
        return trainers.keys.firstOrNull { it.path == input.path }
    }

    /**
     * Finds the trainer tagged on an NPC entity through its applied aspects.
     *
     * The aspect is written at spawn time and saved to NBT, so the link survives a restart.
     */
    fun findByAspects(aspects: Collection<String>): TrainerDefinition? {
        val aspect = aspects.find { it.startsWith(CobblemonTrainers.TRAINER_ASPECT_PREFIX) } ?: return null
        val id = ResourceLocation.tryParse(aspect.removePrefix(CobblemonTrainers.TRAINER_ASPECT_PREFIX))
            ?: return null
        return trainers[id]
    }

    fun get(id: ResourceLocation): TrainerDefinition? = trainers[id]
    fun allIds(): Set<ResourceLocation> = trainers.keys.toSet()
    fun all(): Map<ResourceLocation, TrainerDefinition> = trainers.toMap()
}
