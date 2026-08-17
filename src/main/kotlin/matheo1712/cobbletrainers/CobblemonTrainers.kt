package matheo1712.cobbletrainers

import matheo1712.cobbletrainers.battle.TrainerBattleEventHandler
import matheo1712.cobbletrainers.battle.TrainerBattleInteraction
import matheo1712.cobbletrainers.block.TrainerBlocks
import matheo1712.cobbletrainers.command.ListTrainersCommand
import matheo1712.cobbletrainers.command.SpawnTrainerCommand
import matheo1712.cobbletrainers.network.TrainerSpawnerNetworking
import matheo1712.cobbletrainers.registry.TrainerRegistry
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
 * - Skins: a Minecraft player (by username or UUID), or an image shipped in a pack
 * - Battle start and end messages
 * - Battle music
 * - Item rewards on victory, and one-shot trainers that turn down a rematch
 * - `/spawntrainer <id>` to summon a trainer
 * - `/listtrainers [player]` to review who has been beaten
 * - A trainer spawner block, which keeps one trainer standing where it is placed
 */
object CobblemonTrainers : ModInitializer {

    const val MOD_ID: String = "cobblemon-trainers"

    /**
     * Prefix of the aspect linking an NPC entity to its trainer definition.
     * Applied aspects are saved to NBT, so the link survives a restart.
     */
    const val TRAINER_ASPECT_PREFIX = "trainer_id:"

    /**
     * Prefix of the aspect linking an NPC entity back to the trainer spawner block that put it
     * there, followed by [net.minecraft.core.BlockPos.asLong]. Saved to NBT like the one above,
     * which is what lets a block recognise its own leftovers after a restart.
     */
    const val SPAWNER_ASPECT_PREFIX = "trainer_spawner:"

    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            SpawnTrainerCommand.register(dispatcher)
            ListTrainersCommand.register(dispatcher)
        }

        // Register blocks and network
        TrainerBlocks.register()
        TrainerSpawnerNetworking.register()

        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(TrainerReloadListener)

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

    fun lang(key: String, vararg args: Any): MutableComponent =
        Component.translatable("$MOD_ID.$key", *args)

    private object TrainerReloadListener : SimpleSynchronousResourceReloadListener {
        override fun getFabricId(): ResourceLocation = id(TrainerRegistry.DATAPACK_DIRECTORY)

        override fun onResourceManagerReload(manager: ResourceManager) {
            TrainerRegistry.reload(manager)
        }
    }
}
