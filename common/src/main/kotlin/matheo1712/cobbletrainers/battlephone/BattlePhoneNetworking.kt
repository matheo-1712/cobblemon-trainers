package matheo1712.cobbletrainers.battlephone

import matheo1712.cobbletrainers.platform.Platform

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.battle.TrainerLevelScaling
import matheo1712.cobbletrainers.network.TrainerTeamCodec
import matheo1712.cobbletrainers.network.TrainerTeamMember
import matheo1712.cobbletrainers.parser.ShowdownTeamParser
import matheo1712.cobbletrainers.trainers.RewardPreview
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerCosmetics
import matheo1712.cobbletrainers.trainers.TrainerTrinkets
import matheo1712.cobbletrainers.trainers.TrainerCalls
import matheo1712.cobbletrainers.trainers.TrainerLock
import matheo1712.cobbletrainers.trainers.TrainerPlace
import matheo1712.cobbletrainers.trainers.TrainerProgress
import matheo1712.cobbletrainers.trainers.TrainerRewards
import matheo1712.cobbletrainers.trainers.TrainerTeamPreview
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
 * Skins are not in that listing: they come one at a time through the mod's own skin channel,
 * [matheo1712.cobbletrainers.network.TrainerSkinNetworking]. The listing does carry outfit item
 * IDs so the client can build a detached model preview without waiting for the trainer entity
 * to spawn. Teams are asked for one at a time too, and are only ever sent for a trainer the
 * asking player has beaten.
 */
object BattlePhoneNetworking {

    fun register() {
        Platform.current.registerS2C(OpenBattlePhonePayload.TYPE, OpenBattlePhonePayload.CODEC)
        Platform.current.registerS2C(TrainerTeamPayload.TYPE, TrainerTeamPayload.CODEC)
        Platform.current.registerC2S(RequestTrainerTeamPayload.TYPE, RequestTrainerTeamPayload.CODEC)
        Platform.current.registerC2S(CallTrainerPayload.TYPE, CallTrainerPayload.CODEC)

        Platform.current.receive(RequestTrainerTeamPayload.TYPE) { payload, player ->
            sendTeam(player, payload.trainerId)
        }

        Platform.current.receive(CallTrainerPayload.TYPE) { payload, player ->
            // Nothing of what the screen greyed out is trusted: TrainerCalls redoes every
            // check, and answers the player itself whichever way it goes.
            ResourceLocation.tryParse(payload.trainerId)?.let { TrainerCalls.call(player, it) }
        }
    }

    /** Sends the player their own progress listing and asks their client to open the screen. */
    fun openScreen(player: ServerPlayer, color: String) {
        if (!Platform.current.canSend(player, OpenBattlePhonePayload.TYPE)) {
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
            if (defeated && definition.progress.hideAfterDefeat) return@mapNotNull null
            BattlePhoneEntry(
                id = id.toString(),
                name = definition.name,
                level = TrainerLevelScaling.levelFor(player, definition) ?: definition.battle.level.value,
                teamSize = ShowdownTeamParser.countPokemon(definition.team),
                defeated = defeated,
                rematch = definition.progress.allowsRematch,
                requirements = missing,
                category = category?.toString().orEmpty(),
                categoryName = category?.let { TrainerRegistry.categoryName(it) }.orEmpty(),
                location = TrainerPlace.describe(definition.location),
                cosmetics = definition.cosmetics,
                music = definition.battle.music?.takeIf { defeated && ResourceLocation.tryParse(it) != null },
                callable = definition.callable(),
                // Through the same resolution that hands them over, so the fiche can never
                // advertise a reward the player would not actually receive - which is also why
                // it is told whether beating this trainer would still be a first win.
                rewards = TrainerRewards.preview(definition.rewards, player.registryAccess(), firstWin = !defeated)
            )
        }

        Platform.current.send(player, OpenBattlePhonePayload(entries, color))
    }

    /**
     * Answers a team request, but only for a trainer the asking player has already beaten:
     * the phone shows a team as a reward, not as a way to scout the next fight. An unbeaten
     * trainer gets an empty answer rather than no answer: the screen would otherwise wait on it
     * forever and ask again on every frame.
     */
    private fun sendTeam(player: ServerPlayer, rawId: String) {
        if (!Platform.current.canSend(player, TrainerTeamPayload.TYPE)) return

        val id = ResourceLocation.tryParse(rawId)
        val definition = id?.let { TrainerRegistry.get(it) }?.takeIf { it.progress.listed }

        val members = if (id != null && definition != null &&
            TrainerProgress.of(player.server).hasDefeated(id, player.uuid)
        ) {
            TrainerTeamPreview.of(definition, id, player)
        } else {
            emptyList()
        }

        Platform.current.send(player, TrainerTeamPayload(rawId, members))
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
 * @param cosmetics The clothing and held items needed to build the client-side model preview.
 * @param callable Whether the screen offers a call button. The server decides it, and decides
 *   it again when the button is pressed - this is only what the screen draws.
 * @param rewards What beating the trainer hands over, already resolved by
 *   [matheo1712.cobbletrainers.trainers.TrainerRewards]. Sent as stacks rather than as IDs so
 *   the screen has the icon and the item name without resolving anything itself, and so a
 *   reward that would not survive being handed over never reaches the fiche. Each carries
 *   whether it drops once and whether it is still owed: a claimed trophy is marked on the fiche
 *   rather than dropped from it, and the server is the only side that decides which it is.
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
    val cosmetics: TrainerCosmetics = TrainerCosmetics(),
    val callable: Boolean,
    val music: String? = null,
    val rewards: List<RewardPreview>
) {

    /** A trainer this player may not challenge yet. */
    val locked: Boolean
        get() = requirements.isNotEmpty()
}

private fun RegistryFriendlyByteBuf.writeCosmetics(cosmetics: TrainerCosmetics) {
    writeOptionalText(cosmetics.head)
    writeOptionalText(cosmetics.chest)
    writeOptionalText(cosmetics.legs)
    writeOptionalText(cosmetics.feet)
    writeOptionalText(cosmetics.mainHand)
    writeOptionalText(cosmetics.offHand)
    writeOptionalText(cosmetics.trinkets.face)
    writeOptionalText(cosmetics.trinkets.chest)
    writeOptionalText(cosmetics.trinkets.wrist)
    writeOptionalText(cosmetics.trinkets.forearm)
    writeOptionalText(cosmetics.trinkets.hand)
    writeOptionalText(cosmetics.trinkets.belt)
    writeOptionalText(cosmetics.trinkets.ankle)
}

private fun RegistryFriendlyByteBuf.readCosmetics(): TrainerCosmetics = TrainerCosmetics(
    head = readOptionalText(),
    chest = readOptionalText(),
    legs = readOptionalText(),
    feet = readOptionalText(),
    mainHand = readOptionalText(),
    offHand = readOptionalText(),
    trinkets = TrainerTrinkets(
        face = readOptionalText(),
        chest = readOptionalText(),
        wrist = readOptionalText(),
        forearm = readOptionalText(),
        hand = readOptionalText(),
        belt = readOptionalText(),
        ankle = readOptionalText()
    )
)

private fun RegistryFriendlyByteBuf.writeOptionalText(value: String?) {
    writeBoolean(value != null)
    if (value != null) writeUtf(value)
}

private fun RegistryFriendlyByteBuf.readOptionalText(): String? =
    if (readBoolean()) readUtf() else null

/** Server -> client: the listed trainers and what the player has done about them. */
data class OpenBattlePhonePayload(val entries: List<BattlePhoneEntry>, val color: String) : CustomPacketPayload {

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
                        buf.writeCosmetics(entry.cosmetics)
                        buf.writeBoolean(entry.callable)
                        buf.writeOptionalText(entry.music)
                        buf.writeVarInt(entry.rewards.size)
                        entry.rewards.forEach {
                            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, it.stack)
                            buf.writeBoolean(it.once)
                            buf.writeBoolean(it.due)
                        }
                    }
                    buf.writeUtf(payload.color)
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
                                cosmetics = buf.readCosmetics(),
                                callable = buf.readBoolean(),
                                music = buf.readOptionalText(),
                                rewards = List(buf.readVarInt()) {
                                    RewardPreview(
                                        stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                                        once = buf.readBoolean(),
                                        due = buf.readBoolean()
                                    )
                                }
                            )
                        },
                        buf.readUtf()
                    )
                }
            )
    }
}

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
                    TrainerTeamCodec.encode(buf, payload.members)
                },
                { buf ->
                    TrainerTeamPayload(
                        trainerId = buf.readUtf(),
                        members = TrainerTeamCodec.decode(buf)
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
