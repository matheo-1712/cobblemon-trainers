package matheo1712.cobbletrainers.trainers

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.ChatFormatting
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.CommandResultCallback
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.item.ItemInput
import net.minecraft.commands.arguments.item.ItemParser
import net.minecraft.core.HolderLookup
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * Hands a trainer's rewards to a player who just won.
 *
 * Items go through the player's inventory, whatever does not fit being dropped at their feet:
 * a reward is never silently lost because a bag was full.
 */
object TrainerRewards {

    private val LOGGER = CobblemonTrainers.LOGGER

    /**
     * Upper bound on a reward count. A datapack typo (`"count": 99999999`) would otherwise
     * spend a long time filling an inventory and littering the ground.
     */
    private const val MAX_COUNT = 6400

    /**
     * Gives the rewards of a definition to one player, announcing each in the chat.
     *
     * @param firstWin Whether this is the first time that player has beaten the trainer. It is
     *   what a [TrainerReward.firstWinOnly] entry waits for, and it is read from the saved
     *   progress rather than counted here, so it survives a restart.
     */
    fun grant(player: ServerPlayer, rewards: List<TrainerReward>, firstWin: Boolean) {
        for (reward in rewards) {
            if (!isDue(reward, firstWin)) continue
            if (reward.command.isNotBlank()) {
                runCommand(player, reward)
                continue
            }
            val stack = toStack(reward, player.registryAccess(), complain = true) ?: continue

            // Read before giving: `placeItemBackInInventory` splits the stack until nothing is
            // left of it, so afterwards the count is 0 and the item is air.
            val count = stack.count
            val name = stack.hoverName

            player.inventory.placeItemBackInInventory(stack)
            player.sendSystemMessage(
                CobblemonTrainers.lang("chat.reward", count, name).withStyle(ChatFormatting.GREEN)
            )
        }
    }

    /**
     * What beating the trainer is worth, as far as the player is told: resolved exactly the way
     * [grant] resolves it, and silent about the entries it cannot resolve.
     *
     * The battle phone shows these. Going through the same resolution is what stops the screen
     * advertising a reward that would never be handed over - a broken item ID or a count out of
     * range reads the same in the fiche as in the inventory. Silent because this runs every time
     * a player opens the phone, and the pack was already warned at the moment it mattered.
     *
     * A trophy already claimed is **shown and marked**, not dropped. Dropping it was honest
     * about what the next victory pays and told the player nothing about why: a one-time reward
     * simply vanished from the fiche the moment it was earned, which reads as a fiche that has
     * forgotten it. [RewardPreview.due] carries the answer instead, and the screen strikes the
     * entry rather than losing it.
     *
     * A command reward is left out too: it has no item to draw, and its text is written for
     * the server, not for a player.
     *
     * The one place the two deliberately part company is [TrainerReward.hidden]: a hidden reward
     * is dropped here and still handed over by [grant]. It is filtered server-side rather than
     * sent and skipped by the screen, for the same reason a hidden trainer never reaches the
     * listing - what is not sent cannot be read off the wire.
     *
     * @param firstWin Whether the player has yet to beat this trainer. It is what decides
     *   whether a [TrainerReward.firstWinOnly] entry is still owed or already claimed.
     */
    fun preview(rewards: List<TrainerReward>, registries: HolderLookup.Provider, firstWin: Boolean): List<RewardPreview> =
        rewards.filter { !it.hidden && it.command.isBlank() }.mapNotNull { reward ->
            toStack(reward, registries, complain = false)?.let {
                RewardPreview(it, once = reward.firstWinOnly, due = isDue(reward, firstWin))
            }
        }

    /**
     * Whether a reward is owed for this victory. The single rule [grant] and [preview] share,
     * so the fiche can never disagree with the inventory about what a fight is worth.
     */
    private fun isDue(reward: TrainerReward, firstWin: Boolean): Boolean =
        firstWin || !reward.firstWinOnly

    /**
     * Runs a command reward for the winner.
     *
     * The source is the player's own - so `@s`, `~` and `execute` read as them - raised to the
     * level datapack functions run at, which is what a pack can already reach with a function:
     * a reward opens nothing the pack could not do already. Output is suppressed so the winner
     * does not read "Gave 1 [Diamond] to ..." in chat, which is why a failure is logged here
     * instead - a silenced command that does nothing would otherwise leave no trace at all.
     *
     * Never throws: a broken command costs its own line, the other rewards are still handed out.
     */
    private fun runCommand(player: ServerPlayer, reward: TrainerReward) {
        if (reward.item.isNotBlank()) {
            LOGGER.warn("Reward sets both 'item' ({}) and 'command' ({}), skipping it", reward.item, reward.command)
            return
        }
        val server = player.server
        val command = reward.command.trim().removePrefix("/").replace("{player}", player.gameProfile.name)
        val source = player.createCommandSourceStack()
            .withPermission(server.functionCompilationLevel)
            .withSuppressedOutput()
            .withCallback(CommandResultCallback { success, _ ->
                if (!success) LOGGER.warn("Reward command '{}' failed for {}", command, player.gameProfile.name)
            })
        try {
            val parsed = server.commands.dispatcher.parse(command, source)
            // Caught here because the suppressed source would swallow the parser's own message.
            Commands.getParseException(parsed)?.let {
                LOGGER.warn("Invalid reward command '{}': {}", command, it.message)
                return
            }
            server.commands.performCommand(parsed, command)
        } catch (exception: RuntimeException) {
            LOGGER.warn("Reward command '{}' threw for {}", command, player.gameProfile.name, exception)
        }
    }

    /**
     * Resolves one reward entry into a stack, or null when the item cannot be resolved - a
     * broken entry is skipped, the other rewards are still handed out.
     *
     * @param complain Whether to say so in the log. True when handing rewards over, false when
     *   merely showing them.
     */
    private fun toStack(reward: TrainerReward, registries: HolderLookup.Provider, complain: Boolean): ItemStack? {
        // Caught before parsing: an empty ID yields the valid-but-meaningless `minecraft:`,
        // whose "unknown item" message would send the author looking in the wrong place.
        if (reward.item.isBlank()) {
            if (complain) LOGGER.warn("Reward with no 'item' field, skipping it")
            return null
        }

        val count = reward.count.coerceIn(1, MAX_COUNT)
        if (count != reward.count && complain) {
            LOGGER.warn("Reward count {} for '{}' is out of range, using {}", reward.count, reward.item, count)
        }

        return try {
            // Use the server's registries for components such as enchantments and potions.
            val reader = StringReader(reward.item.trim())
            val parsed = ItemParser(registries).parse(reader)
            // The command parser stops after one item; a reward must consume the whole field.
            if (reader.canRead()) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader)
            }
            // Inventory insertion splits large rewards according to the stack's components.
            ItemInput(parsed.item(), parsed.components()).createItemStack(count, false)
        } catch (exception: CommandSyntaxException) {
            if (complain) LOGGER.warn("Invalid reward item '{}': {}", reward.item, exception.message)
            null
        }
    }
}

/**
 * One line of a trainer's reward list as the battle phone reads it: the item, and what the
 * player is owed of it.
 *
 * The two flags are separate because they answer different questions. [once] is a property of
 * the pack - it never changes and it is what a player wants to know *before* the fight. [due]
 * is a property of this player right now, and only a one-time reward can ever be false.
 *
 * @param stack The item and its count, resolved the way [TrainerRewards.grant] resolves it.
 * @param once Whether the reward drops on the first victory alone.
 * @param due Whether beating the trainer again would still hand it over.
 */
data class RewardPreview(val stack: ItemStack, val once: Boolean, val due: Boolean)
