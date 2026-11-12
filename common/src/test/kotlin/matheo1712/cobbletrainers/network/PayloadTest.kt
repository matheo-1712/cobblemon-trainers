package matheo1712.cobbletrainers.network

import io.netty.buffer.Unpooled
import matheo1712.cobbletrainers.intro.IntroLayer
import matheo1712.cobbletrainers.intro.TrainerIntro
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import java.util.UUID
import kotlin.test.*

class PayloadTest {
    private fun <T : Any> roundTrip(codec: StreamCodec<RegistryFriendlyByteBuf, T>, payload: T): T {
        val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            codec.encode(buf, payload)
            val decoded = codec.decode(buf)
            assertEquals(0, buf.readableBytes())
            return decoded
        } finally {
            buf.release()
        }
    }

    @Test fun `intro payloads carry the complete team format`() {
        val team = listOf(TrainerTeamMember("cobblemon:eevee", listOf("shiny"), 50, "Évoli"))
        val intro = BattleIntroPayload("pack:intro", "pack:erika", "Érika", "League", 50, 1, 123,
            TrainerIntro(layers = listOf(IntroLayer(type = "pokemon", at = 5, length = 8, rotation = 90f))), team, preview = true)
        assertEquals(intro, roundTrip(BattleIntroPayload.CODEC, intro))
    }

    @Test fun `music start and stop preserve optional track and audio settings`() {
        for (track in listOf(null, ResourceLocation.parse("pack:music.champion"))) {
            val payload = BattleMusicPayload(track, 0.25f, 1f)
            assertEquals(payload, roundTrip(BattleMusicPayload.CODEC, payload))
        }
    }

    @Test fun `selected Pokemon can be announced and cleared`() {
        for (id in listOf(null, UUID.fromString("00000000-0000-4000-8000-000000000001"))) {
            val payload = SelectedPokemonPayload(id)
            assertEquals(payload, roundTrip(SelectedPokemonPayload.CODEC, payload))
        }
    }

    @Test fun `skin packets compare byte contents after decoding including missing skins`() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(0, 1, -1, 127))) {
            val payload = TrainerSkinPayload("pack:erika", "slim", bytes)
            val decoded = roundTrip(TrainerSkinPayload.CODEC, payload)
            assertEquals(payload, decoded)
            assertEquals(payload.hashCode(), decoded.hashCode())
        }
    }
}
