package matheo1712.cobbletrainers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Registre en mémoire des dresseurs disponibles.
 *
 * Deux sources, chargées dans cet ordre :
 * 1. Les datapacks — n'importe quel pack peut fournir `data/<namespace>/trainers/<nom>.json`,
 *    y compris le mod lui-même. L'ID du dresseur est alors `<namespace>:<nom>`.
 * 2. Le dossier de config `config/cobblemon-trainers/trainers/<nom>.json`, chargé sous le
 *    namespace `cobblemon-trainers` — il écrase donc le dresseur de même nom fourni par le mod.
 *
 * Comme pour les registres de Cobblemon, seul le nom de fichier compte : un dresseur rangé
 * dans `trainers/kanto/red.json` reçoit l'ID `<namespace>:red`.
 *
 * Le rechargement est piloté par le gestionnaire de ressources du serveur (voir
 * [CobblemonTrainers.onInitialize]), donc `/reload` recharge aussi les dresseurs.
 */
object TrainerRegistry {

    /** Dossier scanné dans les datapacks, sous `data/<namespace>/`. */
    const val DATAPACK_DIRECTORY = "trainers"

    private const val JSON_EXTENSION = ".json"

    private val LOGGER = LoggerFactory.getLogger("CobbleTrainers/Registry")
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private val configDir: File
        get() = FabricLoader.getInstance().configDir
            .resolve(CobblemonTrainers.MOD_ID)
            .resolve(DATAPACK_DIRECTORY)
            .toFile()

    private val trainers = mutableMapOf<ResourceLocation, TrainerDefinition>()

    fun reload(manager: ResourceManager) {
        trainers.clear()
        loadFromDatapacks(manager)
        loadFromConfig()
        LOGGER.info("${trainers.size} dresseur(s) chargé(s) : ${trainers.keys.joinToString(", ")}")
    }

    private fun loadFromDatapacks(manager: ResourceManager) {
        manager.listResources(DATAPACK_DIRECTORY) { path -> path.path.endsWith(JSON_EXTENSION) }
            .forEach { (location, resource) ->
                val id = buildId(location.namespace, File(location.path).nameWithoutExtension)
                if (id == null) {
                    LOGGER.error("Nom de dresseur invalide, fichier ignoré : $location")
                    return@forEach
                }
                try {
                    resource.open().use { stream ->
                        stream.bufferedReader().use { reader ->
                            trainers[id] = GSON.fromJson(reader, TrainerDefinition::class.java)
                        }
                    }
                    LOGGER.info("Dresseur chargé (datapack) : $id")
                } catch (e: Exception) {
                    LOGGER.error("Erreur lors du chargement du dresseur $location : ${e.message}", e)
                }
            }
    }

    private fun loadFromConfig() {
        if (!configDir.exists()) {
            configDir.mkdirs()
            LOGGER.info("Répertoire de config créé : ${configDir.absolutePath}")
            return
        }

        val files = configDir.listFiles { f -> f.extension == "json" } ?: return
        for (file in files) {
            val id = buildId(CobblemonTrainers.MOD_ID, file.nameWithoutExtension)
            if (id == null) {
                LOGGER.error("Nom de fichier invalide pour un dresseur, ignoré : ${file.name}")
                continue
            }
            try {
                trainers[id] = GSON.fromJson(file.readText(), TrainerDefinition::class.java)
                LOGGER.info("Dresseur chargé (config) : $id")
            } catch (e: Exception) {
                LOGGER.error("Erreur lors du chargement du dresseur ${file.name} : ${e.message}", e)
            }
        }
    }

    /** Construit un ID en renvoyant null plutôt qu'en levant si le nom contient des caractères interdits. */
    private fun buildId(namespace: String, name: String): ResourceLocation? =
        ResourceLocation.tryParse("$namespace:$name")

    /**
     * Résout l'ID saisi dans la commande.
     *
     * Brigadier donne toujours un [ResourceLocation] complet : un nom seul y arrive sous le
     * namespace `minecraft`. On tente donc l'ID exact, puis, si le namespace est celui par
     * défaut, le namespace du mod et enfin n'importe quel namespace ayant ce nom.
     */
    fun resolveId(input: ResourceLocation): ResourceLocation? {
        if (input in trainers) return input
        if (input.namespace != ResourceLocation.DEFAULT_NAMESPACE) return null

        buildId(CobblemonTrainers.MOD_ID, input.path)
            ?.takeIf { it in trainers }
            ?.let { return it }
        return trainers.keys.firstOrNull { it.path == input.path }
    }

    fun get(id: ResourceLocation): TrainerDefinition? = trainers[id]
    fun allIds(): Set<ResourceLocation> = trainers.keys.toSet()
    fun all(): Map<ResourceLocation, TrainerDefinition> = trainers.toMap()
}
