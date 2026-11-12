package matheo1712.cobbletrainers.battlephone

import io.netty.buffer.Unpooled
import matheo1712.cobbletrainers.network.TrainerTeamMember
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import kotlin.test.Test
import kotlin.test.assertEquals

class BattlePhonePayloadTest {
    @Test fun `team payloads use the same complete team format as the intro`() {
        val team = listOf(TrainerTeamMember("cobblemon:eevee", listOf("shiny"), 50, "Évoli"))
        val payload = TrainerTeamPayload("pack:erika", team)

        val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            TrainerTeamPayload.CODEC.encode(buf, payload)
            assertEquals(payload, TrainerTeamPayload.CODEC.decode(buf))
            assertEquals(0, buf.readableBytes())
        } finally {
            buf.release()
        }
    }
}
