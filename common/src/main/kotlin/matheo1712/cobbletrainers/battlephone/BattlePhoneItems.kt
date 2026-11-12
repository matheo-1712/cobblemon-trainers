package matheo1712.cobbletrainers.battlephone

import matheo1712.cobbletrainers.platform.Platform

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item

/**
 * The battle phones, one per colour.
 *
 * They keep the `cobblemon-trainers:` IDs they always had, so the phones already sitting in
 * inventories still resolve. Everything is created eagerly as a property of this object, so
 * touching [register] is what pulls it all in - call it from [BattlePhone.onInitialize], before
 * the registries freeze.
 */
object BattlePhoneItems {

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
