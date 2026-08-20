package matheo1712.cobbletrainers.block

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.item.TrainerItems
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.PushReaction

/**
 * The blocks shipped by the mod, and their registration.
 *
 * Everything is created eagerly as a property of this object, so touching [register] is what
 * pulls it all in — call it from the mod initializer, before the registries freeze.
 */
object TrainerBlocks {

    val TRAINER_SPAWNER_ID: ResourceLocation = CobblemonTrainers.id("trainer_spawner")

    /** The mod's creative tab, holding whatever the mod adds to the inventory. */
    val TAB_ID: ResourceLocation = CobblemonTrainers.id("general")

    /**
     * Properties chosen after the barrier, which is the block this one behaves like:
     * indestructible outside creative mode and dropping nothing
     *
     * It parts ways with the barrier on collision: a spawner is a marker for where a trainer
     * stands, so it has to be walkable — the trainer spawns *inside* it, and a solid block
     * would push it out.
     */
    @JvmField
    val TRAINER_SPAWNER: TrainerSpawnerBlock = TrainerSpawnerBlock(
        BlockBehaviour.Properties.of()
            .strength(-1.0f, 3600000.0f)
            .noLootTable()
            .noCollission()
            .noOcclusion()
            .pushReaction(PushReaction.BLOCK)
    )

    /**
     * A game master item, like the command block's: its `getPlacementState` returns null for a
     * player without [net.minecraft.world.entity.player.Player.canUseGameMasterBlocks], so a
     * spawner handed to a regular player cannot be placed. Also referenced from the client
     * mixin that makes the marker particles appear.
     */
    @JvmField
    val TRAINER_SPAWNER_ITEM: TrainerSpawnerItem =
        TrainerSpawnerItem(TRAINER_SPAWNER, Item.Properties())

    /**
     * `build` takes the data fixer type, which vanilla leaves null for every one of its own
     * block entities. Kotlin reads that unannotated Java parameter as non-null, so the call
     * warns; Fabric's `FabricBlockEntityTypeBuilder`, which used to avoid it, is deprecated in
     * favour of this very builder. The warning is the lesser evil.
     */
    @JvmField
    val TRAINER_SPAWNER_ENTITY: BlockEntityType<TrainerSpawnerBlockEntity> =
        BlockEntityType.Builder
            .of({ pos, state -> TrainerSpawnerBlockEntity(pos, state) }, TRAINER_SPAWNER)
            .build(null)

    /**
     * The tab is shown to everyone in creative, on purpose.
     *
     * The tempting alternative is to fill it only when `parameters.hasPermissions()` — an empty
     * `CATEGORY` tab is not displayed, so it would vanish for a regular player. But that flag is
     * `canUseGameMasterBlocks() && the operatorItemsTab option`, and that option is off by
     * default: the tab would be hidden from the operators it is for until they find a vanilla
     * toggle in Options → Controls. Not worth it.
     *
     * What is gated is everything the item can *do* — see [TRAINER_SPAWNER_ITEM] and
     * [TrainerSpawnerBlock]. A player without operator rights can hold one and get nothing out
     * of it, and only ever in creative mode, where they are already trusted with every block in
     * the game.
     */
    val TAB: CreativeModeTab = FabricItemGroup.builder()
        .title(CobblemonTrainers.lang("item_group"))
        .icon { ItemStack(TRAINER_SPAWNER_ITEM) }
        .displayItems { _, output ->
            output.accept(TrainerItems.BATTLE_PHONE)
            output.accept(TRAINER_SPAWNER_ITEM)
        }
        .build()

    fun register() {
        Registry.register(BuiltInRegistries.BLOCK, TRAINER_SPAWNER_ID, TRAINER_SPAWNER)
        Registry.register(BuiltInRegistries.ITEM, TRAINER_SPAWNER_ID, TRAINER_SPAWNER_ITEM)
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TRAINER_SPAWNER_ID, TRAINER_SPAWNER_ENTITY)
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_ID, TAB)
    }
}
