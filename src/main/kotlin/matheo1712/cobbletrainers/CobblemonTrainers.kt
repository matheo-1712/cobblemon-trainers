package matheo1712.cobbletrainers

import matheo1712.cobbletrainers.advancement.TrainerDefeatedTrigger
import matheo1712.cobbletrainers.battle.TrainerBattleEventHandler
import matheo1712.cobbletrainers.battle.TrainerBattleInteraction
import matheo1712.cobbletrainers.block.TrainerBlocks
import matheo1712.cobbletrainers.command.TrainerCommands
import matheo1712.cobbletrainers.item.TrainerItems
import matheo1712.cobbletrainers.network.BattlePhoneNetworking
import matheo1712.cobbletrainers.network.TrainerSpawnerNetworking
import matheo1712.cobbletrainers.trainers.TrainerCalls
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerSkins
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
 * datapacks, at `data/<namespace>/cobblemontrainers/<path>.json`, where the folder a file
 * sits in is its category.
 *
 * Features:
 * - Showdown-formatted teams
 * - Skins: a Minecraft player (by username or UUID), or an image shipped in a pack
 * - Battle start and end messages
 * - Battle music
 * - Item rewards on victory, and one-shot trainers that turn down a rematch
 * - Requirements to challenge a trainer, and an advancement trigger fired by beating one
 * - `/cobblemontrainers spawn <id>` to summon a trainer
 * - `/cobblemontrainers list [player]` to review who has been beaten
 * - `/cobblemontrainers defeat <id|all> [players] [reset]` to record a victory without a battle
 * - A trainer spawner block, which keeps one trainer standing where it is placed
 * - A battle phone item, the same listing in a screen, for every player
 * - Calling a trainer from that screen, for a trainer that declares where it is to be found
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

    /**
     * Prefix of the aspect naming the player who called a trainer from their battle phone,
     * followed by their UUID. Saved to NBT like the two above, which is what lets
     * [matheo1712.cobbletrainers.trainers.TrainerCalls] recognise a called trainer whose call
     * a restart has forgotten.
     */
    const val CALL_ASPECT_PREFIX = "trainer_call:"

    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            TrainerCommands.register(dispatcher)
        }

        // Register blocks, items, network and the advancement trigger. The trigger has to be
        // known before datapacks are read, or an advancement using it fails to parse.
        TrainerDefeatedTrigger.register()
        TrainerBlocks.register()
        TrainerItems.register()
        TrainerSpawnerNetworking.register()
        BattlePhoneNetworking.register()
        TrainerCalls.register()

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
            // A pack may have changed the image behind a skin name we already resolved.
            TrainerSkins.clearCache()
        }
    }
}
