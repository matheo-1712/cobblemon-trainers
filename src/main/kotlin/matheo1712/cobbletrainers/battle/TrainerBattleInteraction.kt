package matheo1712.cobbletrainers.battle

import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.party
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.dialogue.TrainerDialogue
import matheo1712.cobbletrainers.trainers.TrainerDefinition
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerLock
import matheo1712.cobbletrainers.trainers.TrainerProgress
import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.Locale

/**
 * NPC interaction that opens a trainer's dialogue on right-click, and the rules deciding
 * whether the battle behind it happens at all.
 *
 * Cobblemon already provides `q.npc.start_battle(...)` in MoLang, but that function swallows
 * errors: if the player has no Pokémon, is already battling, or the trainer has no team,
 * nothing happens and nothing is shown. Going through Kotlin lets us forward Cobblemon's own
 * error messages to the player, already localised by Cobblemon's language files.
 *
 * A single instance is shared by every trainer, so the battle format is read from the trainer
 * definition at interaction time rather than baked into the configuration.
 *
 * The right-click no longer opens the battle itself: it opens the box
 * [matheo1712.cobbletrainers.dialogue.TrainerDialogue] builds, and [startBattle] is what the
 * player choosing to fight comes back to. Both halves stay here, because both are about the
 * same question - the box only ever shows what [refusal] already decided.
 */
class TrainerBattleInteraction : NPCInteractConfiguration {

    override val type: String = TYPE

    override fun interact(npc: NPCEntity, player: ServerPlayer): Boolean {
        val trainerId = TrainerRegistry.idFromAspects(npc.aspects)
        val definition = trainerId?.let { TrainerRegistry.get(it) }

        // An NPC wearing this interaction without a definition behind it has nothing to say,
        // and no rules of ours to answer to: a pack that removed it mid-session, or a class
        // borrowed by another mod. Cobblemon's own errors are all that is left to show.
        //
        // A player already in a battle gets the same treatment for the opposite reason: the
        // battle interface is on their screen, so a dialogue box over it would say nothing that
        // Cobblemon's refusal does not say better.
        if (trainerId == null || definition == null || player.isInBattle()) {
            startBattle(npc, player, definition)
            return true
        }

        TrainerDialogue.greet(npc, player, trainerId, definition)
        return true
    }

    // Nothing to sync: the interaction has no parameters.
    override fun encode(buffer: RegistryFriendlyByteBuf) = Unit
    override fun decode(buffer: RegistryFriendlyByteBuf) = Unit
    override fun writeToNBT(compoundTag: CompoundTag) = Unit
    override fun readFromNBT(compoundTag: CompoundTag) = Unit

    override fun isDifferentTo(other: NPCInteractConfiguration): Boolean = other !is TrainerBattleInteraction

    companion object {
        /** Value of the `type` field in the NPC class JSON. */
        const val TYPE = "cobblemon-trainers:battle"

        // Special formats LVL: 50
        private val LVL_50_SINGLES = BattleFormat.GEN_9_SINGLES.copy(adjustLevel = 50)
        private val LVL_50_DOUBLES = BattleFormat.GEN_9_DOUBLES.copy(adjustLevel = 50)
        private val LVL_50_TRIPLES = BattleFormat.GEN_9_TRIPLES.copy(adjustLevel = 50)

        /**
         * Accepted values of a trainer's `battle.format`. Cobblemon's own spellings are kept,
         * plus the plain solo/duo/trio wording.
         */
        private val FORMATS: Map<String, BattleFormat> = mapOf(
            "solo" to BattleFormat.GEN_9_SINGLES,
            "single" to BattleFormat.GEN_9_SINGLES,
            "singles" to BattleFormat.GEN_9_SINGLES,
            "single_battle" to BattleFormat.GEN_9_SINGLES,
            "duo" to BattleFormat.GEN_9_DOUBLES,
            "double" to BattleFormat.GEN_9_DOUBLES,
            "doubles" to BattleFormat.GEN_9_DOUBLES,
            "double_battle" to BattleFormat.GEN_9_DOUBLES,
            "trio" to BattleFormat.GEN_9_TRIPLES,
            "triple" to BattleFormat.GEN_9_TRIPLES,
            "triples" to BattleFormat.GEN_9_TRIPLES,
            "triple_battle" to BattleFormat.GEN_9_TRIPLES,

            // Level 50 formats
            "single_50" to LVL_50_SINGLES,
            "singles_50" to LVL_50_SINGLES,
            "double_50" to LVL_50_DOUBLES,
            "doubles_50" to LVL_50_DOUBLES,
            "triple_50" to LVL_50_TRIPLES,
            "triples_50" to LVL_50_TRIPLES

        )

        /**
         * Call this on mod initialization: the type must be known before datapacks are read,
         * otherwise deserializing the NPC class fails.
         */
        fun register() {
            NPCInteractConfiguration.register(
                type = TYPE,
                displayName = CobblemonTrainers.lang("npc.interaction.battle"),
                clazz = TrainerBattleInteraction::class.java
            )
        }

        /**
         * What this trainer would turn the player down with, line by line, or an empty list
         * when they will fight.
         *
         * Asked before anything starts rather than anywhere nearer the battle, so that a
         * refusal costs nothing: no healed party, no music, no battle to close again. The
         * dialogue box shows these lines in place of the greeting - see
         * [matheo1712.cobbletrainers.dialogue.TrainerDialogue.greet].
         */
        fun refusal(
            player: ServerPlayer,
            trainerId: ResourceLocation,
            definition: TrainerDefinition
        ): List<Component> {
            if (!definition.progress.allowsRematch &&
                TrainerProgress.of(player.server).hasDefeated(trainerId, player.uuid)
            ) {
                return listOf(
                    CobblemonTrainers.lang("chat.already_defeated", Component.translatable(definition.name))
                )
            }

            val missing = TrainerLock.unmet(player, trainerId, definition)
            if (missing.isNotEmpty()) return TrainerLock.refusalLines(definition, missing)

            partyRefusal(player, battleFormatOf(definition.battle.format))?.let { return listOf(it) }

            return emptyList()
        }

        /**
         * What the player's party alone is turned down with, or null when it can open the
         * battle. Both refusals are about the same blind spot in Cobblemon.
         *
         * A wiped party is the first. Cobblemon counts the party without ever asking what is
         * still standing in it: `pvn` filters on health for the trainer's side only, so a wiped
         * player would hand Showdown a team nobody can enter from. The rule has no exception -
         * not even a level-adjusting format, which heals the copies it battles with and would
         * hand a wiped player a full team for free. The empty party is the one case left to
         * Cobblemon, which refuses it too and says it better than we would.
         *
         * A fainted Pokémon among the opening slots is the second, and it only ever shows up in
         * doubles and triples. `leadingPokemon` fills the first slot and no more (see
         * [TrainerLead]), and a player's party is theirs to arrange, so a format that sends two
         * or three out at once is the one case the mod can see coming and not fix. Turning it
         * down says which Pokémon to heal or move; letting it through locks the battle.
         */
        private fun partyRefusal(player: ServerPlayer, format: BattleFormat): MutableComponent? {
            val party = player.party()
            if (party.any() && party.none { !it.isFainted() }) {
                return CobblemonTrainers.lang("chat.no_healthy_pokemon")
            }

            val slots = format.battleType.slotsPerActor
            // A level-adjusting format heals the copies it battles with: nothing is fainted by
            // the time Showdown reads the team.
            if (slots < 2 || format.adjustLevel > 0) return null

            val opening = TrainerLead.teamOrder(player, format).take(slots)
            if (opening.size < slots || opening.none { it.isFainted() }) return null

            return CobblemonTrainers.lang("chat.fainted_in_lead", slots)
        }

        /**
         * Opens the battle itself, once the player has said yes in the dialogue box.
         *
         * The refusals of [refusal] are not re-run here - the box was built from them a moment
         * ago - with the one exception of [partyRefusal], which also guards the two paths that
         * reach this without a box at all. Everything else that can still go wrong, an empty
         * party or a trainer with no team, is Cobblemon's own answer to give.
         */
        fun startBattle(npc: NPCEntity, player: ServerPlayer, definition: TrainerDefinition?) {
            val format = battleFormatOf(definition?.battle?.format)

            partyRefusal(player, format)?.let { refused ->
                player.sendSystemMessage(refused.withStyle(ChatFormatting.GRAY))
                return
            }

            // The trainer has no `leadingPokemon` of its own: its party is put in order instead.
            TrainerLead.orderTeam(npc)

            BattleBuilder.pvn(
                player = player,
                npcEntity = npc,
                battleFormat = format,
                // Whoever the player has selected opens the battle, and never someone who
                // cannot fight - Cobblemon's own slot-one default does not check. See TrainerLead.
                leadingPokemon = TrainerLead.leadFor(player, format),
                // A level-adjusting format must battle on copies. See `LVL_50_SINGLES` above.
                cloneParties = format.adjustLevel > 0
            ).ifErrored { errors ->
                errors.sendTo(player)
            }
        }

        /** Falls back to singles, warning about a value that is not understood. */
        fun battleFormatOf(name: String?): BattleFormat {
            if (name.isNullOrBlank()) return BattleFormat.GEN_9_SINGLES

            return FORMATS[name.trim().lowercase(Locale.ROOT)] ?: run {
                CobblemonTrainers.LOGGER.warn(
                    "Unknown battle format '{}', falling back to singles. Expected one of: {}",
                    name,
                    FORMATS.keys.joinToString(", ")
                )
                BattleFormat.GEN_9_SINGLES
            }
        }
    }
}