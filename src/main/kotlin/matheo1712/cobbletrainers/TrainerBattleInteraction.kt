package matheo1712.cobbletrainers

import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.entity.npc.NPCEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
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
        val definition = TrainerRegistry.findByAspects(npc.aspects)

        BattleBuilder.pvn(
            player = player,
            npcEntity = npc,
            battleFormat = battleFormatOf(definition?.battleFormat)
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

        /**
         * Accepted values of a trainer's `battleFormat`. Cobblemon's own spellings are kept,
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
            "triple_battle" to BattleFormat.GEN_9_TRIPLES
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
