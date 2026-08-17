package matheo1712.cobbletrainers.block

import matheo1712.cobbletrainers.network.TrainerSpawnerNetworking
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.GameMasterBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.EntityCollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * A block that keeps one trainer standing on it.
 *
 * The trainer it spawns is stored per block, in its [TrainerSpawnerBlockEntity], and chosen
 * through the screen that opens on right-click. The block entity is also what brings the
 * trainer back when it dies or wanders off.
 *
 * Like a barrier, the block itself is never rendered ([RenderShape.INVISIBLE]) and only shows
 * up as a marker particle while its item is held — see
 * `matheo1712.cobbletrainers.mixin.client.ClientLevelMixin`. Unlike a barrier it has no
 * collision: the trainer stands inside the block, and a solid one would push it out.
 */
class TrainerSpawnerBlock(properties: Properties) : Block(properties), EntityBlock, GameMasterBlock {

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    /**
     * Hides the block from anyone who may not use it.
     *
     * [GameMasterBlock] already stops a regular player from breaking one
     * (`ServerPlayerGameMode.destroyBlock` refuses), and the item refuses to place one, but the
     * block would still catch their crosshair and draw a selection box in mid-air — a spawner
     * is meant to be undetectable. An empty outline shape lets their ray pass straight through.
     *
     * The entity is null for every query the engine makes on its own — shape caching, block
     * placement, pathfinding — which keeps those on the normal full cube.
     */
    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        val entity = (context as? EntityCollisionContext)?.entity
        if (entity is Player && !entity.canUseGameMasterBlocks()) return Shapes.empty()
        return super.getShape(state, level, pos, context)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        TrainerSpawnerBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (level !is ServerLevel || type != TrainerBlocks.TRAINER_SPAWNER_ENTITY) return null
        return BlockEntityTicker { tickLevel, _, _, blockEntity ->
            (blockEntity as TrainerSpawnerBlockEntity).serverTick(tickLevel as ServerLevel)
        }
    }

    /**
     * The trainer faces whoever placed the block. The block is invisible, so it carries no
     * facing property a player could read back: the angle lives in the block entity instead.
     */
    override fun setPlacedBy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        stack: ItemStack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (level.isClientSide || placer == null) return
        (level.getBlockEntity(pos) as? TrainerSpawnerBlockEntity)?.facing = Mth.wrapDegrees(placer.yRot + 180f)
    }

    /**
     * Opens the configuration screen. Gated on [Player.canUseGameMasterBlocks] — creative plus
     * operator — the same gate command and structure blocks use.
     */
    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (!player.canUseGameMasterBlocks()) return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS

        val blockEntity = level.getBlockEntity(pos) as? TrainerSpawnerBlockEntity
            ?: return InteractionResult.PASS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.PASS

        TrainerSpawnerNetworking.openScreen(serverPlayer, blockEntity)
        return InteractionResult.CONSUME
    }

    /**
     * Takes the trainer with the block. Without this, breaking a spawner would leave its
     * trainer behind for good — nothing else ever removes one.
     */
    override fun onRemove(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        newState: BlockState,
        movedByPiston: Boolean
    ) {
        if (!state.`is`(newState.block)) {
            (level.getBlockEntity(pos) as? TrainerSpawnerBlockEntity)?.despawnTrainer()
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }
}
