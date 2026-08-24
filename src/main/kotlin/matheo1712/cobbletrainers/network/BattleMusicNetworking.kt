package matheo1712.cobbletrainers.network

import matheo1712.cobbletrainers.CobblemonTrainers
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer

/**
 * The one packet that tells a client a battle theme is playing, so that it holds its own
 * background music back until the battle is over.
 *
 * ### Why the sound packets could not do this alone
 *
 * A track sent by [net.minecraft.network.protocol.game.ClientboundSoundPacket] is a sound like
 * any other: the client's `MusicManager` never learns of it and keeps counting down to the next
 * background song. Cutting the *Music* category first does not reset that countdown either -
 * `MusicManager.tick` only ever clamps it **down** (`Math.min`), and the one branch that pushes
 * it back by ten to twenty minutes runs solely when a track of its own was playing. A battle
 * starting a few seconds before the countdown ran out therefore got the overworld music on top
 * of the battle theme: issue #33.
 *
 * Whether the world's music may play is a decision only the client can take, hence a packet
 * and the sixth piece of client code in the mod. Nothing else could answer it: the server
 * cannot silence the category periodically without silencing our own track along with it, and
 * it has no way of knowing which sound the client would have picked.
 */
object BattleMusicNetworking {

    fun register() {
        PayloadTypeRegistry.playS2C().register(BattleMusicPayload.TYPE, BattleMusicPayload.CODEC)
    }

    /** Asks the player's client to keep its background music quiet. */
    fun hold(player: ServerPlayer) = send(player, playing = true)

    /** Hands the world's music back to the client. */
    fun release(player: ServerPlayer) = send(player, playing = false)

    /**
     * A client without the mod is simply left alone: it keeps the behaviour of every version
     * before this one, which is the battle theme plus whatever the world decides to play.
     */
    private fun send(player: ServerPlayer, playing: Boolean) {
        if (!ServerPlayNetworking.canSend(player, BattleMusicPayload.TYPE)) return

        ServerPlayNetworking.send(player, BattleMusicPayload(playing))
    }
}

/** Server -> client: whether a trainer battle theme is currently playing for this player. */
data class BattleMusicPayload(val playing: Boolean) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<BattleMusicPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<BattleMusicPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("battle_music"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, BattleMusicPayload> =
            CustomPacketPayload.codec(
                { payload, buf -> buf.writeBoolean(payload.playing) },
                { buf -> BattleMusicPayload(buf.readBoolean()) }
            )
    }
}
