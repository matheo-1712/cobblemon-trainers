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

    /** Gives every reward of a definition to one player, announcing each in the chat. */
    fun grant(player: ServerPlayer, rewards: List<TrainerReward>) {
        for (reward in rewards) {
            val stack = toStack(reward) ?: continue

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
     * Resolves one reward entry into a stack, or null when the item cannot be resolved — a
     * broken entry is logged and skipped, the other rewards are still handed out.
     */
    private fun toStack(reward: TrainerReward): ItemStack? {
        // Caught before parsing: an empty ID yields the valid-but-meaningless `minecraft:`,
        // whose "unknown item" message would send the author looking in the wrong place.
        if (reward.item.isBlank()) {
            LOGGER.warn("Reward with no 'item' field, skipping it")
            return null
        }

        val id = ResourceLocation.tryParse(reward.item)
        if (id == null) {
            LOGGER.warn("Invalid reward item ID '{}'", reward.item)
            return null
        }

        val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null)
        if (item == null) {
            LOGGER.warn("Unknown reward item '{}' — is the mod that provides it installed?", id)
            return null
        }

        val count = reward.count.coerceIn(1, MAX_COUNT)
        if (count != reward.count) {
            LOGGER.warn("Reward count {} for '{}' is out of range, using {}", reward.count, id, count)
        }

        return ItemStack(item, count)
    }
}