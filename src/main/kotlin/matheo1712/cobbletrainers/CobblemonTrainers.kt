package matheo1712.cobbletrainers

import matheo1712.cobbletrainers.advancement.TrainerDefeatedTrigger
import matheo1712.cobbletrainers.battle.TrainerBattleEventHandler
import matheo1712.cobbletrainers.battle.TrainerBattleInteraction
import matheo1712.cobbletrainers.block.TrainerBlocks
import matheo1712.cobbletrainers.command.TrainerCommands
import matheo1712.cobbletrainers.intro.TrainerIntros
import matheo1712.cobbletrainers.item.TrainerItems
import matheo1712.cobbletrainers.network.BattleIntroNetworking
import matheo1712.cobbletrainers.network.BattleLeadNetworking
import matheo1712.cobbletrainers.network.BattleMusicNetworking
import matheo1712.cobbletrainers.network.BattlePhoneNetworking
import matheo1712.cobbletrainers.network.TrainerSpawnerNetworking
import matheo1712.cobbletrainers.trainers.TrainerCalls
import matheo1712.cobbletrainers.trainers.TrainerGaze
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
 * datapacks, at `data/<namespace>/cobblemontrainers/trainers/<path>.json`, where the folder a
 * file sits in is its category.
 *
 * Features:
 * - Showdown-formatted teams
 * - Skins: a Minecraft player (by username or UUID), or an image shipped in a pack
 * - Dialogue on right-click, in Cobblemon's own box: a greeting, the choice to battle, and
 *   the trainer's word once it is over
 * - Battle music, and a versus screen before the battle for a trainer that asks for one
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
     * The one folder the mod reads from a datapack, one level under `data/<namespace>/`.
     *
     * Everything a pack declares sits in it, one sub-folder per kind:
     * [trainers][TrainerRegistry.DATAPACK_DIRECTORY] and
     * [intros][TrainerIntros.DATAPACK_DIRECTORY]. A pack therefore has one folder to move,
     * and a kind added later costs no new top-level name.
     */
    const val DATAPACK_ROOT = "cobblemontrainers"

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
        BattleLeadNetworking.register()
        BattleMusicNetworking.register()
        BattleIntroNetworking.register()
        TrainerCalls.register()

        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(TrainerReloadListener)

        try {
            TrainerBattleInteraction.register()
            TrainerBattleEventHandler.register()
            // Grouped with the Cobblemon-dependent hooks: it reads NPCEntity like they do.
            TrainerGaze.register()
        } catch (e: Exception) {
            LOGGER.error("Failed to register battle hooks. Is Cobblemon installed?", e)
        }

        LOGGER.info("Cobblemon Trainers initialized")
    }

    fun id(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(MOD_ID, path)

    fun lang(key: String, vararg args: Any): MutableComponent =
        Component.translatable("$MOD_ID.$key", *args)

    /** How many misfiled files [TrainerReloadListener] names before it just counts them. */
    private const val MISFILED_SAMPLE = 5

    private object TrainerReloadListener : SimpleSynchronousResourceReloadListener {
        override fun getFabricId(): ResourceLocation = id(DATAPACK_ROOT)

        override fun onResourceManagerReload(manager: ResourceManager) {
            // Intros first: a trainer naming one that does not exist is worth a word, and that
            // word can only be said once the intros are in.
            TrainerIntros.reload(manager)
            TrainerRegistry.reload(manager)
            warnAboutMisfiledJson(manager)
            // A pack may have changed the image behind a skin name we already resolved.
            TrainerSkins.clearCache()
        }

        /**
         * Names the JSON files of a pack that no registry above could have read.
         *
         * Trainers live under [TrainerRegistry.DATAPACK_DIRECTORY] and intros under
         * [TrainerIntros.DATAPACK_DIRECTORY]; a file dropped anywhere else in [DATAPACK_ROOT],
         * or left in the [old intro folder][TrainerIntros.LEGACY_DIRECTORY], is simply never
         * listed. Nothing else would say so - the reload logs a count, and a count of zero
         * looks exactly like a pack that is not installed.
         *
         * Only the first few are named. A whole league filed the old way is hundreds of
         * files, and a warning that long buries the sentence explaining it: the count says
         * how big the problem is, the samples say which pack to go and move.
         */
        private fun warnAboutMisfiledJson(manager: ResourceManager) {
            val json = { location: ResourceLocation -> location.path.endsWith(".json") }

            val misfiled = manager.listResources(DATAPACK_ROOT, json).keys
                .filterNot { it.path.startsWith("${TrainerRegistry.DATAPACK_DIRECTORY}/") }
                .filterNot { it.path.startsWith("${TrainerIntros.DATAPACK_DIRECTORY}/") } +
                manager.listResources(TrainerIntros.LEGACY_DIRECTORY, json).keys

            if (misfiled.isEmpty()) return

            val named = misfiled.take(MISFILED_SAMPLE).joinToString(", ")
            val rest = misfiled.size - MISFILED_SAMPLE

            LOGGER.warn(
                "Ignoring {} file(s) filed outside {}/ and {}/, which is where trainers and " +
                    "intros are now read from: {}{}",
                misfiled.size,
                TrainerRegistry.DATAPACK_DIRECTORY,
                TrainerIntros.DATAPACK_DIRECTORY,
                named,
                if (rest > 0) " and $rest more" else ""
            )
        }
    }
}
