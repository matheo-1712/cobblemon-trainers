package matheo1712.cobbletrainers.network

import matheo1712.cobbletrainers.platform.Platform

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.block.TrainerSpawnerBlockEntity
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/**
 * The two packets behind the trainer spawner screen.
 *
 * The screen is not a container, so it does not go through a `MenuType`: right-clicking a
 * spawner sends the block's current settings and the list of loaded trainers to the player,
 * their client opens a screen with them, and pressing Done sends the result back. Nothing is
 * kept server-side in between - the block is looked up again from the position in the reply,
 * and every value is re-validated there. A client cannot be trusted to have sent back one of
 * the choices it was offered.
 */
object TrainerSpawnerNetworking {

    /**
     * How far a player may be from the block when the reply lands. Generous on purpose: the
     * point is to reject a forged position, not to police the player walking away from an open
     * screen.
     */
    private const val MAX_EDIT_DISTANCE_SQR = 100.0

    fun register() {
        Platform.current.registerS2C(OpenTrainerSpawnerPayload.TYPE, OpenTrainerSpawnerPayload.CODEC)
        Platform.current.registerC2S(
            ConfigureTrainerSpawnerPayload.TYPE,
            ConfigureTrainerSpawnerPayload.CODEC
        )
        Platform.current.registerC2S(RespawnTrainerSpawnerPayload.TYPE, RespawnTrainerSpawnerPayload.CODEC)

        Platform.current.receive(ConfigureTrainerSpawnerPayload.TYPE) { payload, player ->
            configure(player, payload)
        }
        Platform.current.receive(RespawnTrainerSpawnerPayload.TYPE) { payload, player ->
            val blockEntity = editableBlock(player, payload.pos) ?: return@receive
            player.sendSystemMessage(
                CobblemonTrainers.lang(
                    if (blockEntity.respawnNow()) "chat.spawner.respawned" else "chat.spawner.respawn_failed"
                ).withStyle(ChatFormatting.GRAY)
            )
        }
    }

    /** Asks the player's client to open the configuration screen for that block. */
    fun openScreen(player: ServerPlayer, blockEntity: TrainerSpawnerBlockEntity) {
        if (!Platform.current.canSend(player, OpenTrainerSpawnerPayload.TYPE)) {
            player.sendSystemMessage(
                CobblemonTrainers.lang("chat.spawner.client_required").withStyle(ChatFormatting.RED)
            )
            return
        }

        Platform.current.send(
            player,
            OpenTrainerSpawnerPayload(
                pos = blockEntity.blockPos,
                trainerId = blockEntity.trainerId?.toString() ?: "",
                leashRadius = blockEntity.leashRadius,
                respawnDelaySeconds = blockEntity.respawnDelaySeconds,
                trainers = TrainerRegistry.allIds().map { it.toString() }.sorted()
            )
        )
    }

    private fun configure(player: ServerPlayer, payload: ConfigureTrainerSpawnerPayload) {
        val blockEntity = editableBlock(player, payload.pos) ?: return

        val raw = payload.trainerId.trim()
        val trainerId = if (raw.isEmpty()) {
            null
        } else {
            // The same resolution as /spawntrainer, so a bare name works here too.
            ResourceLocation.tryParse(raw)?.let { TrainerRegistry.resolveId(it) } ?: run {
                player.sendSystemMessage(
                    CobblemonTrainers.lang("chat.spawner.unknown_trainer", Component.literal(raw))
                        .withStyle(ChatFormatting.RED)
                )
                return
            }
        }

        blockEntity.configure(trainerId, payload.leashRadius, payload.respawnDelaySeconds)

        val message = if (trainerId == null) {
            CobblemonTrainers.lang("chat.spawner.cleared")
        } else {
            val name = TrainerRegistry.get(trainerId)?.name ?: trainerId.toString()
            CobblemonTrainers.lang(
                "chat.spawner.configured",
                Component.translatable(name).withStyle(ChatFormatting.GOLD)
            )
        }
        player.sendSystemMessage(message.withStyle(ChatFormatting.GRAY))
    }

    private fun editableBlock(player: ServerPlayer, pos: BlockPos): TrainerSpawnerBlockEntity? {
        if (!player.canUseGameMasterBlocks()) return null
        val level = player.serverLevel()
        if (!level.isLoaded(pos)) return null
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_EDIT_DISTANCE_SQR) return null
        return level.getBlockEntity(pos) as? TrainerSpawnerBlockEntity
    }
}

/**
 * Server -> client: everything the screen needs to show, including the trainers the server has
 * loaded. The client never reads the registry itself - trainers come from datapacks, which are
 * a server-side affair.
 */
data class OpenTrainerSpawnerPayload(
    val pos: BlockPos,
    val trainerId: String,
    val leashRadius: Int,
    val respawnDelaySeconds: Int,
    val trainers: List<String>
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<OpenTrainerSpawnerPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<OpenTrainerSpawnerPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("open_trainer_spawner"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, OpenTrainerSpawnerPayload> =
            CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeBlockPos(payload.pos)
                    buf.writeUtf(payload.trainerId)
                    buf.writeVarInt(payload.leashRadius)
                    buf.writeVarInt(payload.respawnDelaySeconds)
                    buf.writeVarInt(payload.trainers.size)
                    payload.trainers.forEach { buf.writeUtf(it) }
                },
                { buf ->
                    val pos = buf.readBlockPos()
                    val trainerId = buf.readUtf()
                    val leashRadius = buf.readVarInt()
                    val respawnDelaySeconds = buf.readVarInt()
                    val trainers = List(buf.readVarInt()) { buf.readUtf() }
                    OpenTrainerSpawnerPayload(pos, trainerId, leashRadius, respawnDelaySeconds, trainers)
                }
            )
    }
}

/** Client -> server: what the player typed in the screen. Validated server-side, never trusted. */
data class ConfigureTrainerSpawnerPayload(
    val pos: BlockPos,
    val trainerId: String,
    val leashRadius: Int,
    val respawnDelaySeconds: Int
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ConfigureTrainerSpawnerPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<ConfigureTrainerSpawnerPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("configure_trainer_spawner"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ConfigureTrainerSpawnerPayload> =
            CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeBlockPos(payload.pos)
                    buf.writeUtf(payload.trainerId)
                    buf.writeVarInt(payload.leashRadius)
                    buf.writeVarInt(payload.respawnDelaySeconds)
                },
                { buf ->
                    ConfigureTrainerSpawnerPayload(
                        pos = buf.readBlockPos(),
                        trainerId = buf.readUtf(),
                        leashRadius = buf.readVarInt(),
                        respawnDelaySeconds = buf.readVarInt()
                    )
                }
            )
    }
}

/** Client -> server: force one fresh NPC using the block's saved trainer ID. */
data class RespawnTrainerSpawnerPayload(val pos: BlockPos) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RespawnTrainerSpawnerPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<RespawnTrainerSpawnerPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("respawn_trainer_spawner"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RespawnTrainerSpawnerPayload> =
            CustomPacketPayload.codec(
                { payload, buf -> buf.writeBlockPos(payload.pos) },
                { buf -> RespawnTrainerSpawnerPayload(buf.readBlockPos()) }
            )
    }
}
