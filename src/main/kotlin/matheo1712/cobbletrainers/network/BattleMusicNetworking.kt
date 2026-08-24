package matheo1712.cobbletrainers.network

import matheo1712.cobbletrainers.CobblemonTrainers
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * The one packet that carries a trainer battle theme to a client, and takes it back.
 *
 * ### Why not the vanilla sound packets
 *
 * Two things a [net.minecraft.network.protocol.game.ClientboundSoundPacket] cannot do, both of
 * them client-side decisions:
 *
 * - **Loop.** `SoundInstance.isLooping` belongs to the instance the client builds, and the one
 *   built from a sound packet plays once. A battle outlasting its track finished in silence.
 * - **Hold the world's music back.** A track sent that way is a sound like any other: the
 *   client's `MusicManager` never learns of it and keeps counting down to its next background
 *   song. Cutting the *Music* category first does not reset that countdown either -
 *   `MusicManager.tick` only ever clamps it **down** (`Math.min`), and the one branch that
 *   pushes it back by ten to twenty minutes runs solely when a track of its own was playing. A
 *   battle starting a few seconds before the countdown ran out therefore got the overworld
 *   music on top of the battle theme: issue #33.
 *
 * So the server names the track and the client plays it, in
 * [matheo1712.cobbletrainers.client.ClientBattleMusic]. The name is still resolved
 * client-side, exactly as a sound packet's would be, so a datapack may name any track a
 * resource pack provides without the mod knowing it beforehand.
 */
object BattleMusicNetworking {

    fun register() {
        PayloadTypeRegistry.playS2C().register(BattleMusicPayload.TYPE, BattleMusicPayload.CODEC)
    }

    /** Starts the theme on the player's client, looping until [silence]. */
    fun play(player: ServerPlayer, track: ResourceLocation, volume: Float, pitch: Float) =
        send(player, BattleMusicPayload(track, volume, pitch))

    /** Stops it, and hands the world's music back. */
    fun silence(player: ServerPlayer) = send(player, BattleMusicPayload(null, 0f, 0f))

    /**
     * A client without the mod hears nothing, and that is the supported answer: the mod is
     * required on both sides, like the two screens whose packets are guarded the same way.
     */
    private fun send(player: ServerPlayer, payload: BattleMusicPayload) {
        if (!ServerPlayNetworking.canSend(player, BattleMusicPayload.TYPE)) return

        ServerPlayNetworking.send(player, payload)
    }
}

/**
 * Server -> client: the battle theme to play, or null to stop the one playing.
 *
 * Volume and pitch travel with it so that the tuning stays in one place, next to the default
 * track in [matheo1712.cobbletrainers.battle.TrainerBattleMusic].
 */
data class BattleMusicPayload(
    val track: ResourceLocation?,
    val volume: Float,
    val pitch: Float
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<BattleMusicPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<BattleMusicPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("battle_music"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, BattleMusicPayload> =
            CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeBoolean(payload.track != null)
                    payload.track?.let { buf.writeResourceLocation(it) }
                    buf.writeFloat(payload.volume)
                    buf.writeFloat(payload.pitch)
                },
                { buf ->
                    BattleMusicPayload(
                        if (buf.readBoolean()) buf.readResourceLocation() else null,
                        buf.readFloat(),
                        buf.readFloat()
                    )
                }
            )
    }
}
