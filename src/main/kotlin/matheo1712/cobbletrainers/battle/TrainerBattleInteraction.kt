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
         * battle.
         *
         * Cobblemon 1.8 asks the same question inside `pvn`, which now refuses a side whose
         * Pokémon still standing number fewer than the format's slots - so this is no longer the
         * only thing between a wiped party and a locked battle. It is still worth asking here,
         * and earlier: asked before anything starts, a refusal costs nothing - no healed party,
         * no music, no battle to close again - and the dialogue box shows it in place of the
         * greeting rather than dropping a chat error on a player who has just said yes.
         *
         * Two rules, and the same count answers both. Counting rather than inspecting the
         * opening slots is deliberate: sorting is what Cobblemon changed in 1.8, counting is
         * what it decides on, and a count cannot drift when the order moves again.
         *
         * The wiped party is the first, and it has no exception - not even a level-adjusting
         * format, which heals the copies it battles with and would hand a wiped player a full
         * team for free. That divergence from Cobblemon is the point, and it has been written
         * and removed once already.
         *
         * Too few standing for a doubles or triples line-up is the second, and there our rule
         * and Cobblemon's agree exactly. The empty party, and any party shorter than the slots
         * it has to fill, is left to Cobblemon: it refuses those too and names the count.
         */
        private fun partyRefusal(player: ServerPlayer, format: BattleFormat): MutableComponent? {
            val party = player.party()
            val standing = party.count { !it.isFainted() }

            if (party.any() && standing == 0) {
                return CobblemonTrainers.lang("chat.no_healthy_pokemon")
            }

            val slots = format.battleType.slotsPerActor
            // A level-adjusting format heals what it battles with before Cobblemon counts it, so
            // nothing is fainted by the time either of us asks.
            if (slots < 2 || format.adjustLevel > 0) return null

            if (standing >= slots || party.count() < slots) return null

            return CobblemonTrainers.lang("chat.fainted_in_lead", slots)
        }

        /**
         * Starts the battle, once the player has said yes in the dialogue box.
         *
         * The refusals of [refusal] are not re-run here - the box was built from them a moment
         * ago - with the one exception of [partyRefusal], which also guards the two paths that
         * reach this without a box at all. Everything else that can still go wrong, an empty
         * party or a trainer with no team, is Cobblemon's own answer to give.
         *
         * A trainer who declares one shows their versus screen first, and the battle opens on
         * its way out - which is why [openBattle] is a step of its own. Nothing is asked again
         * on the other side of that wait: the player spends it in a screen, so nothing of
         * theirs can change, and what can - the trainer leaving, the player with it - is what
         * [TrainerBattleIntro] watches for.
         */
        fun startBattle(npc: NPCEntity, player: ServerPlayer, definition: TrainerDefinition?) {
            val format = battleFormatOf(definition?.battle?.format)

            partyRefusal(player, format)?.let { refused ->
                player.sendSystemMessage(refused.withStyle(ChatFormatting.GRAY))
                return
            }

            val trainerId = TrainerRegistry.idFromAspects(npc.aspects)
            if (definition != null && trainerId != null &&
                TrainerBattleIntro.begin(npc, player, trainerId, definition)
            ) {
                return
            }

            openBattle(npc, player, definition)
        }

        /** The battle itself, with nothing left to decide. Also what an intro ends on. */
        fun openBattle(npc: NPCEntity, player: ServerPlayer, definition: TrainerDefinition?) {
            val format = battleFormatOf(definition?.battle?.format)

            BattleBuilder.pvn(
                player = player,
                npcEntity = npc,
                battleFormat = format,
                // Whoever the player has selected opens the battle: Cobblemon sorts the fainted
                // out of the way but has no idea which Pokémon the player meant. See TrainerLead.
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
