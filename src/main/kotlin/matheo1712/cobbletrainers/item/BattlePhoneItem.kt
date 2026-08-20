package matheo1712.cobbletrainers.item

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.network.BattlePhoneNetworking
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

/**
 * The battle phone: right-click it and it lists the tracked trainers, telling which ones the
 * player has already beaten.
 *
 * Nothing is decided here. What the screen shows is the server's own record — the trainers of
 * [matheo1712.cobbletrainers.registry.TrainerRegistry] and the victories of
 * [matheo1712.cobbletrainers.trainers.TrainerProgress] — so the item is stateless and any
 * number of copies of it show the same thing.
 *
 * Unlike the trainer spawner, this one is for every player: it holds no power, it only reads.
 */
class BattlePhoneItem(properties: Properties) : Item(properties) {

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)

        // The screen is opened by the packet the server sends back, never here: the client has
        // no trainer registry and no progress data of its own.
        if (player is ServerPlayer) BattlePhoneNetworking.openScreen(player)

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltip: MutableList<Component>,
        flag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltip, flag)
        tooltip.add(
            CobblemonTrainers.lang("item.battle_phone.tooltip")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        )
    }
}
