package matheo1712.cobbletrainers.item

import matheo1712.cobbletrainers.platform.Platform

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item

/**
 * The plain items shipped by the mod - the ones that are not the item form of a block, which
 * [matheo1712.cobbletrainers.block.TrainerBlocks] registers alongside it.
 *
 * Everything is created eagerly as a property of this object, so touching [register] is what
 * pulls it all in - call it from the mod initializer, before the registries freeze. The mod's
 * creative tab lives in [matheo1712.cobbletrainers.block.TrainerBlocks] and picks these up
 * from there.
 */
object TrainerItems {

    val BATTLE_PHONE_ID: ResourceLocation = CobblemonTrainers.id("battle_phone")

    /** The original ID remains the blue variant so existing phones keep working. */
    private val OTHER_PHONE_COLORS = listOf("black", "green", "pink", "red", "white", "yellow")

    /** One per player is plenty: the phone holds no state, so a stack of them says nothing. */
    @JvmField
    val BATTLE_PHONE: BattlePhoneItem = BattlePhoneItem(Item.Properties().stacksTo(1), "blue")

    val BATTLE_PHONES: List<BattlePhoneItem> = listOf(BATTLE_PHONE) +
        OTHER_PHONE_COLORS.map { BattlePhoneItem(Item.Properties().stacksTo(1), it) }

    fun register() {
        Platform.current.register(BuiltInRegistries.ITEM, BATTLE_PHONE_ID, BATTLE_PHONE)
        OTHER_PHONE_COLORS.zip(BATTLE_PHONES.drop(1)).forEach { (color, phone) ->
            Platform.current.register(BuiltInRegistries.ITEM, CobblemonTrainers.id("battle_phone_$color"), phone)
        }
    }
}
