package matheo1712.cobbletrainers.block

import matheo1712.cobbletrainers.network.TrainerSpawnerNetworking
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.GameMasterBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.DirectionProperty
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

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    /** Faces whoever places it, like a furnace. */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    /**
     * [rotate] and [mirror] are what make a spawner follow a structure that is placed turned
     * around. They exist for the block state and nothing else — this is why the direction the
     * trainer faces is a state property rather than a number in the block entity, which
     * `StructureTemplate` would have carried across unrotated.
     */
    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

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

    companion object {
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING
    }
}
