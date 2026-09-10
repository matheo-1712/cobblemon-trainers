package matheo1712.cobbletrainers.network

import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.battle.TrainerBattleIntro
import matheo1712.cobbletrainers.intro.TrainerIntro
import matheo1712.cobbletrainers.parser.ShowdownTeamParser
import matheo1712.cobbletrainers.trainers.TrainerDefinition
import matheo1712.cobbletrainers.trainers.TrainerRegistry
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
 * The screen is a scene a pack wrote, so the scene travels with it - see
 * [matheo1712.cobbletrainers.battle.TrainerBattleIntro] for why nothing is synced ahead of
 * time. Along with it goes everything its layers may name: who the trainer is, what their
 * category and level are, how many Pokémon they field, which entity in the world is them, and
 * their team when - and only when - a layer draws one.
 *
 * The trainer's skin rides along too, as the same [TrainerSkinPayload] the battle phone is
 * answered with: it is what a `figure` layer falls back to when the entity is not there to
 * pose, and the phone's own request would be turned down for a trainer that is not `listed`.
 */
object BattleIntroNetworking {

    /** The scene itself travels as its own JSON. See [BattleIntroPayload]. */
    private val GSON: Gson = GsonBuilder().create()

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
        npc: NPCEntity,
        trainerId: ResourceLocation,
        definition: TrainerDefinition,
        introId: ResourceLocation,
        intro: TrainerIntro
    ) {
        BattlePhoneNetworking.pushSkin(player, trainerId.toString(), definition)

        val category = TrainerRegistry.categoryOf(trainerId)
            ?.let { TrainerRegistry.categoryName(it) }
            .orEmpty()

        ServerPlayNetworking.send(
            player,
            BattleIntroPayload(
                introId = introId.toString(),
                trainerId = trainerId.toString(),
                trainerName = definition.name,
                category = category,
                level = definition.battle.level,
                teamSize = ShowdownTeamParser.countPokemon(definition.team),
                npcId = npc.id,
                scene = intro,
                // Only a scene that draws one pays for building it - and only such a scene ever
                // shows the player what they are about to fight.
                team = if (intro.needsTeam()) BattlePhoneNetworking.teamOf(definition, trainerId) else emptyList()
            )
        )
    }

    internal fun writeScene(intro: TrainerIntro): String = GSON.toJson(intro)

    internal fun readScene(json: String): TrainerIntro =
        try {
            GSON.fromJson(json, TrainerIntro::class.java) ?: TrainerIntro()
        } catch (e: Exception) {
            CobblemonTrainers.LOGGER.warn("Unreadable intro scene: {}", e.message)
            TrainerIntro()
        }
}

/**
 * Server -> client: raise the versus screen, and here is the scene to draw.
 *
 * @param introId Which intro this is, for the log line a client-side problem deserves.
 * @param trainerId What the screen looks the fallback skin up under, in
 *   [matheo1712.cobbletrainers.client.cache.TrainerSkinCache].
 * @param trainerName Sent raw, as the datapack wrote it, like every other name the mod sends:
 *   the client turns it into a translatable component. Same for [category].
 * @param npcId The trainer's entity id, so a `figure` layer can pose the real model rather than
 *   an image of its skin. It is the entity the player is standing in front of, so their client
 *   has it - and when it does not, the skin is the fallback.
 * @param scene The intro itself, carried as its own JSON rather than as twenty read/write pairs
 *   a layer field could silently fall out of. It is our own data class at both ends, so the one
 *   definition is the whole codec.
 * @param team Empty unless the scene draws a Pokémon: an intro that does not show the team does
 *   not send it either.
 */
data class BattleIntroPayload(
    val introId: String,
    val trainerId: String,
    val trainerName: String,
    val category: String,
    val level: Int,
    val teamSize: Int,
    val npcId: Int,
    val scene: TrainerIntro,
    val team: List<TrainerTeamMember>
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<BattleIntroPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<BattleIntroPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("battle_intro"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, BattleIntroPayload> =
            CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUtf(payload.introId)
                    buf.writeUtf(payload.trainerId)
                    buf.writeUtf(payload.trainerName)
                    buf.writeUtf(payload.category)
                    buf.writeVarInt(payload.level)
                    buf.writeVarInt(payload.teamSize)
                    buf.writeVarInt(payload.npcId)
                    buf.writeUtf(BattleIntroNetworking.writeScene(payload.scene), MAX_SCENE)
                    buf.writeVarInt(payload.team.size)
                    payload.team.forEach { member ->
                        buf.writeUtf(member.species)
                        buf.writeVarInt(member.aspects.size)
                        member.aspects.forEach { buf.writeUtf(it) }
                        buf.writeVarInt(member.level)
                        buf.writeUtf(member.nickname)
                    }
                },
                { buf ->
                    BattleIntroPayload(
                        introId = buf.readUtf(),
                        trainerId = buf.readUtf(),
                        trainerName = buf.readUtf(),
                        category = buf.readUtf(),
                        level = buf.readVarInt(),
                        teamSize = buf.readVarInt(),
                        npcId = buf.readVarInt(),
                        scene = BattleIntroNetworking.readScene(buf.readUtf(MAX_SCENE)),
                        team = List(buf.readVarInt()) {
                            TrainerTeamMember(
                                species = buf.readUtf(),
                                aspects = List(buf.readVarInt()) { buf.readUtf() },
                                level = buf.readVarInt(),
                                nickname = buf.readUtf()
                            )
                        }
                    )
                }
            )

        /** Big enough for a scene of a few dozen layers, small enough to be a limit. */
        private const val MAX_SCENE = 64 * 1024
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
