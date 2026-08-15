package matheo1712.cobbletrainers

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory

/**
 * Point d'entrée principal du mod Cobblemon Trainers.
 *
 * Ce mod ajoute des dresseurs Pokémon configurables à Cobblemon. Les dresseurs se déclarent
 * soit dans un datapack (`data/<namespace>/trainers/<nom>.json`), soit dans la config
 * (`config/cobblemon-trainers/trainers/<nom>.json`).
 *
 * Fonctionnalités :
 * - Équipes au format Showdown
 * - Skins de joueur Minecraft (par pseudo ou UUID)
 * - Messages de début/fin de combat
 * - Commande `/spawntrainer <id>` pour invoquer un dresseur
 */
object CobblemonTrainers : ModInitializer {

    const val MOD_ID: String = "cobblemon-trainers"

    /**
     * Préfixe de l'aspect qui relie une entité NPC à sa définition de dresseur.
     * Les aspects appliqués sont sauvegardés en NBT, donc le lien survit à un redémarrage.
     */
    const val TRAINER_ASPECT_PREFIX = "trainer_id:"

    private val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        LOGGER.info("=== Cobblemon Trainers — Initialisation ===")

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            SpawnTrainerCommand.register(dispatcher)
            LOGGER.info("Commande /spawntrainer enregistrée.")
        }

        // Le gestionnaire de ressources couvre à la fois le chargement initial des datapacks
        // au démarrage du serveur et les rechargements déclenchés par /reload.
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(TrainerReloadListener)

        // Les événements de combat dépendent de Cobblemon : on isole l'échec éventuel.
        try {
            TrainerBattleEventHandler.register()
        } catch (e: Exception) {
            LOGGER.error("Impossible d'enregistrer les listeners de combat. Cobblemon est-il installé ? ${e.message}")
        }

        LOGGER.info("=== Cobblemon Trainers — Prêt ! ===")
    }

    fun id(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(MOD_ID, path)

    private object TrainerReloadListener : SimpleSynchronousResourceReloadListener {
        override fun getFabricId(): ResourceLocation = id(TrainerRegistry.DATAPACK_DIRECTORY)

        override fun onResourceManagerReload(manager: ResourceManager) {
            TrainerRegistry.reload(manager)
        }
    }
}
