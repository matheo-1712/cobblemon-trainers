package matheo1712.cobbletrainers.network

import matheo1712.cobbletrainers.platform.Platform

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.trainers.TrainerDefinition
import matheo1712.cobbletrainers.trainers.TrainerLock
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerSkins
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * How a trainer's skin reaches a client that has no entity to read it from: a screen drawing a
 * trainer who is not standing in front of the player, or who is but whose model cannot be posed.
 *
 * Two ways in. A client may **ask** for a skin ([RequestTrainerSkinPayload]), which is what a
 * listing does row by row; the server may **push** one ([pushSkin]) ahead of a screen it is
 * about to raise, which is what the versus screen does. Both land in
 * [matheo1712.cobbletrainers.client.cache.TrainerSkinCache], under the trainer's ID.
 *
 * Skins are not sent with a listing: they are images - a few kilobytes each - and a world may
 * hold a hundred trainers, so they travel one at a time, as a screen needs them, answered from
 * the [TrainerSkins] cache.
 */
object TrainerSkinNetworking {

    fun register() {
        Platform.current.registerS2C(TrainerSkinPayload.TYPE, TrainerSkinPayload.CODEC)
        Platform.current.registerC2S(RequestTrainerSkinPayload.TYPE, RequestTrainerSkinPayload.CODEC)

        Platform.current.receive(RequestTrainerSkinPayload.TYPE) { payload, player ->
            sendSkin(player, payload.trainerId)
        }
    }

    /**
     * Answers a skin request. Always answers, even when there is no image: a screen would
     * otherwise wait on it forever and ask again on every frame.
     */
    private fun sendSkin(player: ServerPlayer, rawId: String) {
        if (!Platform.current.canSend(player, TrainerSkinPayload.TYPE)) return

        val id = ResourceLocation.tryParse(rawId) ?: return
        // Only trainers a listing could have shown this player: the request comes from a
        // client, so it is the client's word that the ID is one it was offered - and a hidden
        // trainer was never offered.
        val definition = TrainerRegistry.get(id)
            ?.takeIf { it.progress.listed && !TrainerLock.isHiddenFrom(player, id, it) }
            ?: run {
                Platform.current.send(player, TrainerSkinPayload(rawId, "default", ByteArray(0)))
                return
            }

        pushSkin(player, rawId, definition)
    }

    /**
     * Sends a trainer's skin to a client that has not asked for it, for a screen about to need
     * it - the battle intro, raised on a player already standing in front of the trainer. What
     * that player is allowed to see is the caller's business: [sendSkin] is the one answering a
     * client, and the only one that has to doubt the ID it was handed.
     *
     * The resolution is the same, so a skin resolved for one screen is served to the other from
     * the [TrainerSkins] cache, and the client files both under one entry.
     */
    fun pushSkin(player: ServerPlayer, rawId: String, definition: TrainerDefinition) {
        if (!Platform.current.canSend(player, TrainerSkinPayload.TYPE)) return

        val server = player.server
        TrainerSkins.resolveAsync(server, definition.skin) { texture ->
            server.execute {
                if (player.hasDisconnected()) return@execute
                Platform.current.send(
                    player,
                    TrainerSkinPayload(
                        trainerId = rawId,
                        model = texture?.model?.name?.lowercase() ?: "default",
                        texture = texture?.texture ?: ByteArray(0),
                        modelResource = definition.skin.modelId()?.toString() ?: "",
                        aspects = definition.skin.modelAspects().toList(),
                        textureResource = definition.skin.textureId()?.toString() ?: ""
                    )
                )
            }
        }
    }
}

/** Client -> server: "send me the skin of that trainer". Validated server-side, never trusted. */
data class RequestTrainerSkinPayload(val trainerId: String) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<RequestTrainerSkinPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<RequestTrainerSkinPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("request_trainer_skin"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestTrainerSkinPayload> =
            CustomPacketPayload.codec(
                { payload, buf -> buf.writeUtf(payload.trainerId) },
                { buf -> RequestTrainerSkinPayload(buf.readUtf()) }
            )
    }
}

/**
 * Server -> client: the skin image of one trainer, as the bytes of its PNG.
 *
 * The bytes travel rather than a path, for the same reason `NPC_PLAYER_TEXTURE` carries them:
 * a texture shipped in a pack installed on the server alone still shows up on every client.
 * A custom model sends its resolver ID and aspects instead of player PNG bytes. Its assets
 * are loaded from client resource packs. An empty array without a model means a placeholder.
 *
 * [textureResource] travels beside the bytes, never instead of them: it is the path the pack
 * declared, which lets a client holding the same file in its own resource packs draw that one.
 * Empty for every skin type but `texture`, which is the only one that names a path.
 */
data class TrainerSkinPayload(
    val trainerId: String,
    val model: String,
    val texture: ByteArray,
    val modelResource: String = "",
    val aspects: List<String> = emptyList(),
    val textureResource: String = ""
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<TrainerSkinPayload> = TYPE

    // A data class over a ByteArray compares by identity, which would be a trap for anyone
    // reusing this. Compare by content instead.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrainerSkinPayload) return false
        return trainerId == other.trainerId && model == other.model && texture.contentEquals(other.texture) &&
            modelResource == other.modelResource && aspects == other.aspects &&
            textureResource == other.textureResource
    }

    override fun hashCode(): Int =
        31 * (31 * (31 * (31 * (31 * trainerId.hashCode() + model.hashCode()) + texture.contentHashCode()) +
            modelResource.hashCode()) + aspects.hashCode()) + textureResource.hashCode()

    companion object {
        val TYPE: CustomPacketPayload.Type<TrainerSkinPayload> =
            // Bumped with every field added: an older codec would read the new trailing field
            // as part of the one before it. v2 added the model metadata, v3 the texture path.
            CustomPacketPayload.Type(CobblemonTrainers.id("trainer_skin_v3"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, TrainerSkinPayload> =
            CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUtf(payload.trainerId)
                    buf.writeUtf(payload.model)
                    buf.writeByteArray(payload.texture)
                    buf.writeUtf(payload.modelResource)
                    buf.writeCollection(payload.aspects) { buffer, aspect -> buffer.writeUtf(aspect) }
                    buf.writeUtf(payload.textureResource)
                },
                { buf ->
                    TrainerSkinPayload(
                        trainerId = buf.readUtf(),
                        model = buf.readUtf(),
                        texture = buf.readByteArray(),
                        modelResource = buf.readUtf(),
                        aspects = buf.readList { it.readUtf() },
                        textureResource = buf.readUtf()
                    )
                }
            )
    }
}
