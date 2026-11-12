package matheo1712.cobbletrainers.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

/**
 * One Pokémon of a revealed team.
 *
 * @param species Full species ID, `cobblemon:pikachu`, so the client looks it up in its own
 *   synced registry rather than guessing from a name.
 * @param aspects What the Pokémon ended up with once built - this is what picks the model, a
 *   regional form and a shiny included.
 * @param nickname Empty when the Pokémon has none.
 */
data class TrainerTeamMember(
    val species: String,
    val aspects: List<String>,
    val level: Int,
    val nickname: String
)

/** Shared wire format for teams revealed by the phone and by intro layers. */
internal object TrainerTeamCodec : StreamCodec<FriendlyByteBuf, List<TrainerTeamMember>> {
    override fun encode(buf: FriendlyByteBuf, members: List<TrainerTeamMember>) {
        buf.writeVarInt(members.size)
        for (member in members) {
            buf.writeUtf(member.species)
            buf.writeVarInt(member.aspects.size)
            member.aspects.forEach { buf.writeUtf(it) }
            buf.writeVarInt(member.level)
            buf.writeUtf(member.nickname)
        }
    }

    override fun decode(buf: FriendlyByteBuf): List<TrainerTeamMember> =
        List(buf.readVarInt()) {
            TrainerTeamMember(
                species = buf.readUtf(),
                aspects = List(buf.readVarInt()) { buf.readUtf() },
                level = buf.readVarInt(),
                nickname = buf.readUtf()
            )
        }
}
