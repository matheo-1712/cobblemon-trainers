package matheo1712.cobbletrainers.network

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.parser.ShowdownTeamParser
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerDefinition
import matheo1712.cobbletrainers.trainers.TrainerCalls
import matheo1712.cobbletrainers.trainers.TrainerLock
import matheo1712.cobbletrainers.trainers.TrainerPlace
import matheo1712.cobbletrainers.trainers.TrainerProgress
import matheo1712.cobbletrainers.trainers.TrainerRewards
import matheo1712.cobbletrainers.trainers.TrainerSkins
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * The packets behind the battle phone screen.
 *
 * Everything the screen shows is server-side data: the trainers come from datapacks and the
 * victories from a [matheo1712.cobbletrainers.trainers.TrainerProgress] save file, neither of which the client has. So
 * right-clicking the item sends the whole listing over, and the client opens a screen with it.
 *
 * Skins are not in that listing. They are images - a few kilobytes each - and a world may hold
 * a hundred trainers, so they are asked for one at a time, as the screen needs them, and
 * answered from the [TrainerSkins] cache. Teams work the same way, and are only ever sent for
 * a trainer the asking player has beaten.
 */
object BattlePhoneNetworking {

    fun register() {
        PayloadTypeRegistry.playS2C().register(OpenBattlePhonePayload.TYPE, OpenBattlePhonePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(TrainerSkinPayload.TYPE, TrainerSkinPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(TrainerTeamPayload.TYPE, TrainerTeamPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(RequestTrainerSkinPayload.TYPE, RequestTrainerSkinPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(RequestTrainerTeamPayload.TYPE, RequestTrainerTeamPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(CallTrainerPayload.TYPE, CallTrainerPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(RequestTrainerSkinPayload.TYPE) { payload, context ->
            sendSkin(context.player(), payload.trainerId)
        }

        ServerPlayNetworking.registerGlobalReceiver(RequestTrainerTeamPayload.TYPE) { payload, context ->
            sendTeam(context.player(), payload.trainerId)
        }

        ServerPlayNetworking.registerGlobalReceiver(CallTrainerPayload.TYPE) { payload, context ->
            // Nothing of what the screen greyed out is trusted: TrainerCalls redoes every
            // check, and answers the player itself whichever way it goes.
            ResourceLocation.tryParse(payload.trainerId)?.let { TrainerCalls.call(context.player(), it) }
        }
    }

    /** Sends the player their own progress listing and asks their client to open the screen. */
    fun openScreen(player: ServerPlayer) {
        if (!ServerPlayNetworking.canSend(player, OpenBattlePhonePayload.TYPE)) {
            player.sendSystemMessage(
                CobblemonTrainers.lang("chat.battle_phone.client_required").withStyle(ChatFormatting.RED)
            )
            return
        }

        val progress = TrainerProgress.of(player.server)
        val entries = TrainerRegistry.listed().mapNotNull { (id, definition) ->
            val missing = TrainerLock.unmet(player, id, definition)
            // A hidden trainer is not sent at all, rather than sent and filtered on the client:
            // the listing is the only thing that would tell a player it exists.
            if (missing.isNotEmpty() && definition.requirements()?.hidden == true) return@mapNotNull null

            val category = TrainerRegistry.categoryOf(id)
            val defeated = progress.hasDefeated(id, player.uuid)
            BattlePhoneEntry(
                id = id.toString(),
                name = definition.name,
                level = definition.battle.level,
                teamSize = ShowdownTeamParser.countPokemon(definition.team),
                defeated = defeated,
                rematch = definition.progress.allowsRematch,
                requirements = missing,
                category = category?.toString().orEmpty(),
                categoryName = category?.let { TrainerRegistry.categoryName(it) }.orEmpty(),
                location = TrainerPlace.describe(definition.location),
                callable = definition.callable(),
                // Through the same resolution that hands them over, so the fiche can never
                // advertise a reward the player would not actually receive - which is also why
                // it is told whether beating this trainer would still be a first win.
                rewards = TrainerRewards.preview(definition.rewards, firstWin = !defeated)
            )
        }

        ServerPlayNetworking.send(player, OpenBattlePhonePayload(entries))
    }

    /**
     * Answers a skin request. Always answers, even when there is no image: the screen would
     * otherwise wait on it forever and ask again on every frame.
     */
    private fun sendSkin(player: ServerPlayer, rawId: String) {
        if (!ServerPlayNetworking.canSend(player, TrainerSkinPayload.TYPE)) return

        val id = ResourceLocation.tryParse(rawId) ?: return
        // Only trainers the phone itself listed for this player: the request comes from a
        // client, so it is the client's word that the ID is one we offered - and a hidden
        // trainer was never offered.
        val definition = TrainerRegistry.get(id)
            ?.takeIf { it.progress.listed && !TrainerLock.isHiddenFrom(player, id, it) }
            ?: run {
                ServerPlayNetworking.send(player, TrainerSkinPayload(rawId, "default", ByteArray(0)))
                return
            }

        val server = player.server
        TrainerSkins.resolveAsync(server, definition.skin) { texture ->
            server.execute {
                if (player.hasDisconnected()) return@execute
                ServerPlayNetworking.send(
                    player,
                    TrainerSkinPayload(
                        trainerId = rawId,
                        model = texture?.model?.name?.lowercase() ?: "default",
                        texture = texture?.texture ?: ByteArray(0)
                    )
                )
            }
        }
    }

    /**
     * Answers a team request, but only for a trainer the asking player has already beaten:
     * the phone shows a team as a reward, not as a way to scout the next fight. An unbeaten
     * trainer gets an empty answer rather than no answer, for the same reason as above.
     */
    private fun sendTeam(player: ServerPlayer, rawId: String) {
        if (!ServerPlayNetworking.canSend(player, TrainerTeamPayload.TYPE)) return

        val id = ResourceLocation.tryParse(rawId)
        val definition = id?.let { TrainerRegistry.get(it) }?.takeIf { it.progress.listed }

        val members = if (id != null && definition != null &&
            TrainerProgress.of(player.server).hasDefeated(id, player.uuid)
        ) {
            buildTeam(definition, id)
        } else {
            emptyList()
        }

        ServerPlayNetworking.send(player, TrainerTeamPayload(rawId, members))
    }

    /**
     * Builds the team the way [matheo1712.cobbletrainers.trainers.TrainerSpawner] does, because that is the only way to know the
     * aspects a Pokémon ends up with: the parser puts a form in the property string, and only
     * `create()` turns it into the aspect set the client needs to draw the right model. One
     * bad entry costs its slot, not the whole team.
     */
    private fun buildTeam(definition: TrainerDefinition, trainerId: ResourceLocation): List<TrainerTeamMember> =
        ShowdownTeamParser.parse(definition.team).mapNotNull { properties ->
            if (properties.level == null) properties.level = definition.battle.level
            try {
                val pokemon = properties.create()
                TrainerTeamMember(
                    species = pokemon.species.resourceIdentifier.toString(),
                    aspects = pokemon.aspects.toList(),
                    level = pokemon.level,
                    nickname = pokemon.nickname?.string.orEmpty()
                )
            } catch (e: Exception) {
                CobblemonTrainers.LOGGER.warn(
                    "Skipping a Pokémon of trainer {} in the battle phone: {}",
                    trainerId,
                    e.message
                )
                null
            }
        }
}

/**
 * One line of the battle phone listing.
 *
 * @param name Sent raw, as the datapack wrote it: the client turns it into a translatable
 *   component, so a resource pack may localise it - the mod's single translation path.
 * @param teamSize How many Pokémon the trainer fields. Zero for a trainer with no team.
 * @param rematch Whether the trainer takes a rematch once beaten.
 * @param requirements What this player still has to do before the trainer accepts a battle,
 *   empty for an open one. Already built as components server-side, where the registry and the
 *   progress file are - they are translatable, so they still read in the player's language.
 * @param category ID of the category the trainer is filed under, empty for one at the root of
 *   its pack. Consecutive entries sharing it are one group in the listing.
 * @param categoryName What to show as the name of that category, raw like [name].
 * @param location Where the trainer is to be found, already worded server-side where the
 *   registry is - empty for a trainer who names no place. Built like [requirements] and for the
 *   same reason: a translatable component still reads in the player's own language on arrival.
 * @param callable Whether the screen offers a call button. The server decides it, and decides
 *   it again when the button is pressed - this is only what the screen draws.
 * @param rewards What beating the trainer hands over, already resolved into stacks by
 *   [matheo1712.cobbletrainers.trainers.TrainerRewards]. Sent as stacks rather than as IDs so
 *   the screen has the icon and the item name without resolving anything itself, and so a
 *   reward that would not survive being handed over never reaches the fiche.
 */
data class BattlePhoneEntry(
    val id: String,
    val name: String,
    val level: Int,
    val teamSize: Int,
    val defeated: Boolean,
    val rematch: Boolean,
    val requirements: List<Component>,
    val category: String,
    val categoryName: String,
    val location: Component,
    val callable: Boolean,
    val rewards: List<ItemStack>
) {

    /** A trainer this player may not challenge yet. */
    val locked: Boolean
        get() = requirements.isNotEmpty()
}

/** Server -> client: the listed trainers and what the player has done about them. */
data class OpenBattlePhonePayload(val entries: List<BattlePhoneEntry>) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<OpenBattlePhonePayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<OpenBattlePhonePayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("open_battle_phone"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, OpenBattlePhonePayload> =
            CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeVarInt(payload.entries.size)
                    payload.entries.forEach { entry ->
                        buf.writeUtf(entry.id)
                        buf.writeUtf(entry.name)
                        buf.writeVarInt(entry.level)
                        buf.writeVarInt(entry.teamSize)
                        buf.writeBoolean(entry.defeated)
                        buf.writeBoolean(entry.rematch)
                        buf.writeVarInt(entry.requirements.size)
                        entry.requirements.forEach { ComponentSerialization.STREAM_CODEC.encode(buf, it) }
                        buf.writeUtf(entry.category)
                        buf.writeUtf(entry.categoryName)
                        ComponentSerialization.STREAM_CODEC.encode(buf, entry.location)
                        buf.writeBoolean(entry.callable)
                        buf.writeVarInt(entry.rewards.size)
                        entry.rewards.forEach { ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, it) }
                    }
                },
                { buf ->
                    OpenBattlePhonePayload(
                        List(buf.readVarInt()) {
                            BattlePhoneEntry(
                                id = buf.readUtf(),
                                name = buf.readUtf(),
                                level = buf.readVarInt(),
                                teamSize = buf.readVarInt(),
                                defeated = buf.readBoolean(),
                                rematch = buf.readBoolean(),
                                requirements = List(buf.readVarInt()) {
                                    ComponentSerialization.STREAM_CODEC.decode(buf)
                                },
                                category = buf.readUtf(),
                                categoryName = buf.readUtf(),
                                location = ComponentSerialization.STREAM_CODEC.decode(buf),
                                callable = buf.readBoolean(),
                                rewards = List(buf.readVarInt()) {
                                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)
                                }
                            )
                        }
                    )
                }
            )
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
 * An empty array means the trainer has no resolvable skin - the screen draws a placeholder.
 */
data class TrainerSkinPayload(
    val trainerId: String,
    val model: String,
    val texture: ByteArray
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<TrainerSkinPayload> = TYPE

    // A data class over a ByteArray compares by identity, which would be a trap for anyone
    // reusing this. Compare by content instead.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrainerSkinPayload) return false
        return trainerId == other.trainerId && model == other.model && texture.contentEquals(other.texture)
    }

    override fun hashCode(): Int =
        31 * (31 * trainerId.hashCode() + model.hashCode()) + texture.contentHashCode()

    companion object {
        val TYPE: CustomPacketPayload.Type<TrainerSkinPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("trainer_skin"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, TrainerSkinPayload> =
            CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUtf(payload.trainerId)
                    buf.writeUtf(payload.model)
                    buf.writeByteArray(payload.texture)
                },
                { buf ->
                    TrainerSkinPayload(
                        trainerId = buf.readUtf(),
                        model = buf.readUtf(),
                        texture = buf.readByteArray()
                    )
                }
            )
    }
}

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

/** Client -> server: "show me that trainer's team". Answered only for a beaten trainer. */
data class RequestTrainerTeamPayload(val trainerId: String) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<RequestTrainerTeamPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<RequestTrainerTeamPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("request_trainer_team"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestTrainerTeamPayload> =
            CustomPacketPayload.codec(
                { payload, buf -> buf.writeUtf(payload.trainerId) },
                { buf -> RequestTrainerTeamPayload(buf.readUtf()) }
            )
    }
}

/**
 * Server -> client: the team of one trainer.
 *
 * An empty list is the answer for a trainer the player has not beaten yet, and for one with no
 * team at all. The screen shows locked slots either way, which is the truth as far as the
 * player is concerned.
 */
data class TrainerTeamPayload(
    val trainerId: String,
    val members: List<TrainerTeamMember>
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<TrainerTeamPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<TrainerTeamPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("trainer_team"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, TrainerTeamPayload> =
            CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUtf(payload.trainerId)
                    buf.writeVarInt(payload.members.size)
                    payload.members.forEach { member ->
                        buf.writeUtf(member.species)
                        buf.writeVarInt(member.aspects.size)
                        member.aspects.forEach { buf.writeUtf(it) }
                        buf.writeVarInt(member.level)
                        buf.writeUtf(member.nickname)
                    }
                },
                { buf ->
                    TrainerTeamPayload(
                        trainerId = buf.readUtf(),
                        members = List(buf.readVarInt()) {
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
    }
}

/**
 * Client -> server: "have that trainer come to me".
 *
 * The screen only shows the button for a trainer the server said was callable, but that is a
 * drawing decision: everything - the trainer existing, being listed, being unlocked, the player
 * standing in the right place - is decided again in
 * [matheo1712.cobbletrainers.trainers.TrainerCalls], which also answers the player.
 */
data class CallTrainerPayload(val trainerId: String) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<CallTrainerPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<CallTrainerPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("call_trainer"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, CallTrainerPayload> =
            CustomPacketPayload.codec(
                { payload, buf -> buf.writeUtf(payload.trainerId) },
                { buf -> CallTrainerPayload(buf.readUtf()) }
            )
    }
}
