package matheo1712.cobbletrainers

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Main entrypoint of the Cobblemon Trainers mod.
 *
 * The mod adds configurable Pokémon trainers to Cobblemon. Trainers are declared in
 * datapacks, at `data/<namespace>/cobblemontrainers/<name>.json`.
 *
 * Features:
 * - Showdown-formatted teams
 * - Minecraft player skins (by username or UUID)
 * - Battle start and end messages
 * - Battle music
 * - `/spawntrainer <id>` to summon a trainer
 */
object CobblemonTrainers : ModInitializer {

    const val MOD_ID: String = "cobblemon-trainers"

    /**
     * Prefix of the aspect linking an NPC entity to its trainer definition.
     * Applied aspects are saved to NBT, so the link survives a restart.
     */
    const val TRAINER_ASPECT_PREFIX = "trainer_id:"

    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            SpawnTrainerCommand.register(dispatcher)
        }

        // The resource manager covers both the initial datapack load on server start and
        // reloads triggered by /reload.
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(TrainerReloadListener)

        // Both registrations depend on Cobblemon, so isolate a possible failure.
        // The interaction must be known before datapacks are read.
        try {
            TrainerBattleInteraction.register()
            TrainerBattleEventHandler.register()
        } catch (e: Exception) {
            LOGGER.error("Failed to register battle hooks. Is Cobblemon installed?", e)
        }

        LOGGER.info("Cobblemon Trainers initialized")
    }

    fun id(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(MOD_ID, path)

    /** Builds a translatable component from a key inside the mod's namespace. */
    fun lang(key: String, vararg args: Any): MutableComponent =
        Component.translatable("$MOD_ID.$key", *args)

    private object TrainerReloadListener : SimpleSynchronousResourceReloadListener {
        override fun getFabricId(): ResourceLocation = id(TrainerRegistry.DATAPACK_DIRECTORY)

        override fun onResourceManagerReload(manager: ResourceManager) {
            TrainerRegistry.reload(manager)
        }
    }
}
