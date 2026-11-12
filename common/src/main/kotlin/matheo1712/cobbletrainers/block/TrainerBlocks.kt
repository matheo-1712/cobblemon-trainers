package matheo1712.cobbletrainers.block

import matheo1712.cobbletrainers.platform.Platform

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.PushReaction

/**
 * The blocks shipped by the mod, and their registration.
 *
 * Everything is created eagerly as a property of this object, so touching [register] is what
 * pulls it all in - call it from the mod initializer, before the registries freeze.
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
     * stands, so it has to be walkable - the trainer spawns *inside* it, and a solid block
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
     * The tempting alternative is to fill it only when `parameters.hasPermissions()` - an empty
     * `CATEGORY` tab is not displayed, so it would vanish for a regular player. But that flag is
     * `canUseGameMasterBlocks() && the operatorItemsTab option`, and that option is off by
     * default: the tab would be hidden from the operators it is for until they find a vanilla
     * toggle in Options → Controls. Not worth it.
     *
     * What is gated is everything the item can *do* - see [TRAINER_SPAWNER_ITEM] and
     * [TrainerSpawnerBlock]. A player without operator rights can hold one and get nothing out
     * of it, and only ever in creative mode, where they are already trusted with every block in
     * the game.
     */
    val TAB: CreativeModeTab = Platform.current.creativeTabBuilder()
        .title(CobblemonTrainers.lang("item_group"))
        .icon { ItemStack(TRAINER_SPAWNER_ITEM) }
        .displayItems { _, output ->
            // Read each time the tab is filled, so whatever was added at init is there.
            tabItems().forEach { output.accept(it) }
        }
        .build()

    /** What other parts - and later other mods - list in the tab, by the position asked for. */
    private val tabExtras = mutableListOf<Pair<Int, List<ItemLike>>>()

    /**
     * Lists [items] in the mod's creative tab, before the entry at [index] of what is already
     * there - the spawner being entry 0 to begin with. Call it during init, before the tab is
     * first opened.
     */
    fun addToTab(index: Int, items: List<ItemLike>) {
        tabExtras += index to items
    }

    private fun tabItems(): List<ItemLike> {
        val items = mutableListOf<ItemLike>(TRAINER_SPAWNER_ITEM)
        tabExtras.forEach { (index, extra) -> items.addAll(index.coerceIn(0, items.size), extra) }
        return items
    }

    fun register() {
        Platform.current.register(BuiltInRegistries.BLOCK, TRAINER_SPAWNER_ID, TRAINER_SPAWNER)
        Platform.current.register(BuiltInRegistries.ITEM, TRAINER_SPAWNER_ID, TRAINER_SPAWNER_ITEM)
        Platform.current.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TRAINER_SPAWNER_ID, TRAINER_SPAWNER_ENTITY)
        Platform.current.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_ID, TAB)
    }
}
