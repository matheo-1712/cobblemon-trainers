package matheo1712.cobbletrainers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Registre en mémoire des dresseurs disponibles.
 *
 * Les dresseurs viennent exclusivement des datapacks : n'importe quel pack peut fournir
 * `data/<namespace>/trainers/<nom>.json`, y compris le mod lui-même. L'ID du dresseur est
 * `<namespace>:<nom>`. Un pack chargé plus tard écrase un dresseur de même ID, selon les
 * règles habituelles d'empilement des datapacks.
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

    private val trainers = mutableMapOf<ResourceLocation, TrainerDefinition>()

    fun reload(manager: ResourceManager) {
        trainers.clear()

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
                    LOGGER.info("Dresseur chargé : $id")
                } catch (e: Exception) {
                    LOGGER.error("Erreur lors du chargement du dresseur $location : ${e.message}", e)
                }
            }

        LOGGER.info("${trainers.size} dresseur(s) chargé(s) : ${trainers.keys.joinToString(", ")}")
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
