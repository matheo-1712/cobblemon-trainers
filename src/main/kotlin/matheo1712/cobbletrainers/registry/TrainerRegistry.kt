package matheo1712.cobbletrainers.registry

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.trainers.TrainerCategory
import matheo1712.cobbletrainers.trainers.TrainerDefinition
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

/**
 * In-memory registry of the available trainers and their categories.
 *
 * Trainers come from datapacks only: any pack may provide
 * `data/<namespace>/cobblemontrainers/<path>.json`, including the mod itself. The trainer ID is
 * `<namespace>:<path>`, the path being everything under the directory, subfolders included -
 * so `cobblemontrainers/champions/erika.json` is `<namespace>:champions/erika`. A pack loaded
 * later overrides a trainer with the same ID, following the usual datapack stacking rules.
 *
 * **A folder is a category.** The directory a trainer sits in is its category, which is what
 * the battle phone groups on and what a `victories` requirement may be counted over. A
 * category describes itself through the one reserved file name, [CATEGORY_FILE]: everything
 * else in the folder is a trainer. See [TrainerCategory].
 *
 * Reloading is driven by the server resource manager (see [matheo1712.cobbletrainers.CobblemonTrainers.onInitialize]),
 * so `/reload` reloads trainers too.
 */
object TrainerRegistry {

    /**
     * Directory scanned inside datapacks, one level under `data/<namespace>/` - the layout
     * Cobblemon's own registries use (`data/<namespace>/species/`, `.../npcs/`). It is named
     * after the mod rather than `trainers/`, so a pack that also declares trainers for another
     * mod cannot collide with ours.
     */
    const val DATAPACK_DIRECTORY = "cobblemontrainers"

    /**
     * The one file name that is not a trainer: `champions/category.json` is the presentation of
     * the category `champions`. Keeping it inside the folder it describes is what lets a
     * category be moved, copied or dropped in one piece.
     */
    const val CATEGORY_FILE = "category"

    private const val JSON_EXTENSION = ".json"
    private const val PATH_SEPARATOR = '/'

    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private val trainers = mutableMapOf<ResourceLocation, TrainerDefinition>()
    private val categories = mutableMapOf<ResourceLocation, TrainerCategory>()

    /**
     * [listed] in reading order, sorted once per reload rather than once per caller: the
     * battle phone asks for it while building every line it sends, and a `victories`
     * requirement asks again for every trainer it evaluates.
     */
    private var listedOrder: List<Pair<ResourceLocation, TrainerDefinition>> = emptyList()

    fun reload(manager: ResourceManager) {
        trainers.clear()
        categories.clear()

        manager.listResources(DATAPACK_DIRECTORY) { path -> path.path.endsWith(JSON_EXTENSION) }
            .forEach { (location, resource) ->
                val relative = location.path
                    .removePrefix("$DATAPACK_DIRECTORY$PATH_SEPARATOR")
                    .removeSuffix(JSON_EXTENSION)

                if (relative.substringAfterLast(PATH_SEPARATOR) == CATEGORY_FILE) {
                    val folder = relative.substringBeforeLast(PATH_SEPARATOR, "")
                    if (folder.isEmpty()) {
                        CobblemonTrainers.LOGGER.warn(
                            "Ignoring {}: a {}.json describes the folder it sits in, and this one sits in none",
                            location, CATEGORY_FILE
                        )
                        return@forEach
                    }
                    read(location, resource::open, TrainerCategory::class.java) { id, category ->
                        categories[id.withPath(folder)] = category
                    }
                } else {
                    read(location, resource::open, TrainerDefinition::class.java) { id, definition ->
                        val trainerId = id.withPath(relative)
                        definition.validate(trainerId)
                        trainers[trainerId] = definition
                    }
                }
            }

        listedOrder = sortForDisplay(trainers.filterValues { it.progress.listed }.toList())

        CobblemonTrainers.LOGGER.info(
            "Loaded {} trainer(s) in {} categor(y/ies): {}",
            trainers.size,
            categories.size,
            trainers.keys.joinToString(", ")
        )
    }

    /**
     * Reads one JSON file. The ID handed to [accept] is the file's own location, which the
     * caller narrows down to what it is registering. A parse error costs its file, not the
     * whole reload.
     */
    private fun <T> read(
        location: ResourceLocation,
        openStream: () -> java.io.InputStream,
        type: Class<T>,
        accept: (ResourceLocation, T) -> Unit
    ) {
        try {
            openStream().use { stream ->
                stream.bufferedReader().use { reader ->
                    accept(location, GSON.fromJson(reader, type))
                }
            }
        } catch (e: Exception) {
            CobblemonTrainers.LOGGER.error("Failed to load {}", location, e)
        }
    }

    /** Same namespace, another path. Returns the location unchanged if the path is illegal. */
    private fun ResourceLocation.withPath(path: String): ResourceLocation =
        ResourceLocation.tryBuild(namespace, path) ?: this

    /**
     * Resolves the ID typed in the command.
     *
     * Brigadier always yields a complete [ResourceLocation]: a bare name arrives under the
     * `minecraft` namespace. So try the exact ID first, then, if the namespace is the default
     * one, the mod namespace, then any namespace holding that path, and finally any trainer
     * whose file is named that way whatever folder it sits in - `/spawntrainer erika` finds
     * `mon_pack:champions/erika`.
     */
    fun resolveId(input: ResourceLocation): ResourceLocation? {
        if (input in trainers) return input
        if (input.namespace != ResourceLocation.DEFAULT_NAMESPACE) return null

        ResourceLocation.tryBuild(CobblemonTrainers.MOD_ID, input.path)
            ?.takeIf { it in trainers }
            ?.let { return it }

        return trainers.keys.firstOrNull { it.path == input.path }
            ?: trainers.keys.firstOrNull { it.fileName() == input.path }
    }

    /**
     * Reads the trainer ID tagged on an NPC entity through its applied aspects.
     *
     * The aspect is written at spawn time and saved to NBT, so the link survives a restart.
     * The ID is returned whether or not a definition still carries it: a trainer dropped from
     * the datapacks keeps its identity, which is what [matheo1712.cobbletrainers.trainers.TrainerProgress] is keyed on.
     */
    fun idFromAspects(aspects: Collection<String>): ResourceLocation? {
        val aspect = aspects.find { it.startsWith(CobblemonTrainers.TRAINER_ASPECT_PREFIX) } ?: return null
        return ResourceLocation.tryParse(aspect.removePrefix(CobblemonTrainers.TRAINER_ASPECT_PREFIX))
    }

    /** Finds the trainer tagged on an NPC entity, or null if it is not one of ours. */
    fun findByAspects(aspects: Collection<String>): TrainerDefinition? =
        idFromAspects(aspects)?.let { trainers[it] }

    fun get(id: ResourceLocation): TrainerDefinition? = trainers[id]
    fun allIds(): Set<ResourceLocation> = trainers.keys.toSet()
    fun all(): Map<ResourceLocation, TrainerDefinition> = trainers.toMap()

    /**
     * The category a trainer belongs to, which is the folder it sits in, or null for one at
     * the root of the directory.
     */
    fun categoryOf(id: ResourceLocation): ResourceLocation? {
        val folder = id.path.substringBeforeLast(PATH_SEPARATOR, "")
        if (folder.isEmpty()) return null
        return ResourceLocation.tryBuild(id.namespace, folder)
    }

    /** The declared presentation of a category, or null when the pack shipped no file for it. */
    fun category(id: ResourceLocation): TrainerCategory? = categories[id]

    /** What to show as the name of a category: its own if it declares one, else the folder. */
    fun categoryName(id: ResourceLocation): String =
        categories[id]?.name?.takeIf { it.isNotBlank() } ?: id.path

    /**
     * The trainers a progress listing should show - the battle phone and `/listtrainers` - in
     * the order they are meant to be read: by datapack, then by category, then by ID.
     *
     * Everything that presents progress to a player goes through here rather than [all]: the
     * demo trainers shipped by this mod and by others are loaded in every world, and a player
     * has no reason to see them among the trainers they are meant to hunt down. See
     * [matheo1712.cobbletrainers.trainers.TrainerProgressRules.listed].
     */
    fun listed(): List<Pair<ResourceLocation, TrainerDefinition>> = listedOrder

    private fun sortForDisplay(
        entries: List<Pair<ResourceLocation, TrainerDefinition>>
    ): List<Pair<ResourceLocation, TrainerDefinition>> =
        entries.sortedWith(
            compareBy(
                { (id, _) -> id.namespace },
                // Trainers filed at the root of a pack come last, under their own heading: a
                // category is something the pack chose, the root is what is left.
                { (id, _) -> if (categoryOf(id) == null) 1 else 0 },
                { (id, _) -> categoryOf(id)?.let { category(it)?.order } ?: TrainerCategory.UNORDERED },
                { (id, _) -> categoryOf(id)?.path.orEmpty() },
                { (id, _) -> id.path }
            )
        )

    /**
     * The listed trainers a `victories` requirement counts over: every one of them, or only
     * those of one datapack, or only those of one category.
     */
    fun listedIds(namespace: String? = null, category: ResourceLocation? = null): List<ResourceLocation> =
        listedOrder.map { (id, _) -> id }
            .filter { namespace == null || it.namespace == namespace }
            .filter { category == null || categoryOf(it) == category }

    /** The file name of a trainer, its folders left out. */
    private fun ResourceLocation.fileName(): String = path.substringAfterLast(PATH_SEPARATOR)
}
