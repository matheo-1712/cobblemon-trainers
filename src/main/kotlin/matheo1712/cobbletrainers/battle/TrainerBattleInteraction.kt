package matheo1712.cobbletrainers.battle

import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.party
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerLock
import matheo1712.cobbletrainers.trainers.TrainerProgress
import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.Locale

/**
 * NPC interaction that starts a trainer battle on right-click.
 *
 * Cobblemon already provides `q.npc.start_battle(...)` in MoLang, but that function swallows
 * errors: if the player has no Pokémon, is already battling, or the trainer has no team,
 * nothing happens and nothing is shown. Going through Kotlin lets us forward Cobblemon's own
 * error messages to the player, already localised by Cobblemon's language files.
 *
 * A single instance is shared by every trainer, so the battle format is read from the trainer
 * definition at interaction time rather than baked into the configuration.
 */
class TrainerBattleInteraction : NPCInteractConfiguration {

    override val type: String = TYPE

    override fun interact(npc: NPCEntity, player: ServerPlayer): Boolean {
        val trainerId = TrainerRegistry.idFromAspects(npc.aspects)
        val definition = trainerId?.let { TrainerRegistry.get(it) }

        if (trainerId != null && definition != null) {
            if (!definition.progress.allowsRematch &&
                TrainerProgress.of(player.server).hasDefeated(trainerId, player.uuid)
            ) {
                player.sendSystemMessage(
                    CobblemonTrainers.lang("chat.already_defeated", Component.translatable(definition.name))
                        .withStyle(ChatFormatting.GRAY)
                )
                return true
            }

            // Checked here rather than anywhere nearer the battle so that nothing starts: no
            // healed party, no music, no battle to close again.
            val missing = TrainerLock.unmet(player, trainerId, definition)
            if (missing.isNotEmpty()) {
                player.sendSystemMessage(TrainerLock.refusal(definition, missing))
                return true
            }
        }

        val format = battleFormatOf(definition?.battle?.format)

        // Cobblemon counts the player's party without ever asking what is still standing in it:
        // `pvn` filters on health for the trainer's side only. A wiped player would hand Showdown
        // a team where nobody can be sent out. The rule has no exception - not even a
        // level-adjusting format, which heals the copies it battles with and would hand a wiped
        // player a full team for free. The empty party is the one case left to Cobblemon, which
        // refuses it too and says it better than we would.
        val party = player.party()
        if (party.any() && party.none { !it.isFainted() }) {
            player.sendSystemMessage(
                CobblemonTrainers.lang("chat.no_healthy_pokemon").withStyle(ChatFormatting.GRAY)
            )
            return true
        }

        BattleBuilder.pvn(
            player = player,
            npcEntity = npc,
            battleFormat = format,
            // Whoever the player has selected opens the battle. Cobblemon leads with slot one
            // otherwise, and null keeps exactly that.
            leadingPokemon = TrainerLead.leadFor(player, format),
            // A level-adjusting format must battle on copies. See `LVL_50_SINGLES` below.
            cloneParties = format.adjustLevel > 0
        ).ifErrored { errors ->
            errors.sendTo(player)
        }
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