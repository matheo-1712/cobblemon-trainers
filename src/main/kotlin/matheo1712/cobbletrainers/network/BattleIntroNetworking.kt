package matheo1712.cobbletrainers.network

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.battle.TrainerBattleIntro
import matheo1712.cobbletrainers.trainers.TrainerDefinition
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * The two packets of the versus screen: the one that raises it, and the one a player who has
 * seen it before sends back.
 *
 * The screen draws two skins - the player's own, which their client already has, and the
 * trainer's, which it may never have asked for. So the trainer's is pushed along with the
 * intro rather than requested: the battle phone's request would be turned down for a trainer
 * that is not `listed`, and a trainer the player is standing in front of is hardly a secret.
 * It travels as the same [TrainerSkinPayload] the phone is answered with, so both screens read
 * one cache.
 *
 * See [matheo1712.cobbletrainers.battle.TrainerBattleIntro] for what the server does while the
 * screen is up.
 */
object BattleIntroNetworking {

    fun register() {
        PayloadTypeRegistry.playS2C().register(BattleIntroPayload.TYPE, BattleIntroPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(SkipBattleIntroPayload.TYPE, SkipBattleIntroPayload.CODEC)

        // Nothing of the client's is trusted here beyond "I am done looking": the countdown is
        // only brought forward, and every guard it goes through stays where it was.
        ServerPlayNetworking.registerGlobalReceiver(SkipBattleIntroPayload.TYPE) { _, context ->
            TrainerBattleIntro.skip(context.player())
        }
    }

    /** Whether this client can show the screen at all. A client without the mod cannot. */
    fun canOpen(player: ServerPlayer): Boolean =
        ServerPlayNetworking.canSend(player, BattleIntroPayload.TYPE)

    /** Sends the trainer's skin, then raises the screen on it. */
    fun open(
        player: ServerPlayer,
        style: String,
        trainerId: ResourceLocation,
        definition: TrainerDefinition,
        duration: Int
    ) {
        BattlePhoneNetworking.pushSkin(player, trainerId.toString(), definition)
        ServerPlayNetworking.send(
            player,
            BattleIntroPayload(style, trainerId.toString(), definition.name, duration)
        )
    }
}

/**
 * Server -> client: raise the versus screen.
 *
 * @param style Which screen to draw - see [TrainerBattleIntro.STYLES]. Sent rather than assumed
 *   so that a second look can be added without a second packet.
 * @param trainerId What the screen looks the skin up under, in
 *   [matheo1712.cobbletrainers.client.cache.TrainerSkinCache].
 * @param trainerName Sent raw, as the datapack wrote it, like every other name the mod sends:
 *   the client turns it into a translatable component.
 * @param duration How long the screen stays up, in ticks. The server is counting the same ones
 *   behind it, so a screen that closes is a battle about to open.
 */
data class BattleIntroPayload(
    val style: String,
    val trainerId: String,
    val trainerName: String,
    val duration: Int
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<BattleIntroPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<BattleIntroPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("battle_intro"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, BattleIntroPayload> =
            CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUtf(payload.style)
                    buf.writeUtf(payload.trainerId)
                    buf.writeUtf(payload.trainerName)
                    buf.writeVarInt(payload.duration)
                },
                { buf ->
                    BattleIntroPayload(
                        style = buf.readUtf(),
                        trainerId = buf.readUtf(),
                        trainerName = buf.readUtf(),
                        duration = buf.readVarInt()
                    )
                }
            )
    }
}

/**
 * Client -> server: "I have seen it, start the battle".
 *
 * Nothing travels with it: the server already knows which battle this player is waiting on,
 * and a client naming one would be a client choosing its own opponent.
 */
class SkipBattleIntroPayload : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<SkipBattleIntroPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<SkipBattleIntroPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("skip_battle_intro"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SkipBattleIntroPayload> =
            CustomPacketPayload.codec({ _, _ -> }, { SkipBattleIntroPayload() })
    }
}
