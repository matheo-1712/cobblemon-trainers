package matheo1712.cobbletrainers.block

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.GameMasterBlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.block.Block

/**
 * The item of the trainer spawner.
 *
 * [GameMasterBlockItem] is what refuses the placement to a player without operator rights; the
 * tooltip says so, because nothing else would. The block is invisible and silent: a player
 * holding one and clicking to no effect has no way to guess why.
 */
class TrainerSpawnerItem(block: Block, properties: Properties) : GameMasterBlockItem(block, properties) {

    override fun appendHoverText(
        stack: ItemStack,
        context: Item.TooltipContext,
        tooltip: MutableList<Component>,
        flag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltip, flag)
        tooltip.add(
            CobblemonTrainers.lang("item.trainer_spawner.operator_only")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        )
    }
}
