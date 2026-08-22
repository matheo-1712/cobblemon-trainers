package matheo1712.cobbletrainers.trainers

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
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
            val stack = toStack(reward, complain = true) ?: continue

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
     * The one place the two deliberately part company is [TrainerReward.hidden]: a hidden reward
     * is dropped here and still handed over by [grant]. It is filtered server-side rather than
     * sent and skipped by the screen, for the same reason a hidden trainer never reaches the
     * listing - what is not sent cannot be read off the wire.
     *
     * @param firstWin Whether the player has yet to beat this trainer. It is what decides
     *   whether a [TrainerReward.firstWinOnly] entry is still owed or already claimed.
     */
    fun preview(rewards: List<TrainerReward>, firstWin: Boolean): List<RewardPreview> =
        rewards.filter { !it.hidden }.mapNotNull { reward ->
            toStack(reward, complain = false)?.let {
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
     * Resolves one reward entry into a stack, or null when the item cannot be resolved - a
     * broken entry is skipped, the other rewards are still handed out.
     *
     * @param complain Whether to say so in the log. True when handing rewards over, false when
     *   merely showing them.
     */
    private fun toStack(reward: TrainerReward, complain: Boolean): ItemStack? {
        // Caught before parsing: an empty ID yields the valid-but-meaningless `minecraft:`,
        // whose "unknown item" message would send the author looking in the wrong place.
        if (reward.item.isBlank()) {
            if (complain) LOGGER.warn("Reward with no 'item' field, skipping it")
            return null
        }

        val id = ResourceLocation.tryParse(reward.item)
        if (id == null) {
            if (complain) LOGGER.warn("Invalid reward item ID '{}'", reward.item)
            return null
        }

        val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null)
        if (item == null) {
            if (complain) {
                LOGGER.warn("Unknown reward item '{}' - is the mod that provides it installed?", id)
            }
            return null
        }

        val count = reward.count.coerceIn(1, MAX_COUNT)
        if (count != reward.count && complain) {
            LOGGER.warn("Reward count {} for '{}' is out of range, using {}", reward.count, id, count)
        }

        return ItemStack(item, count)
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
