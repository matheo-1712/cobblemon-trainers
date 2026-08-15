package matheo1712.cobbletrainers

import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.entity.npc.NPCEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

/**
 * NPC interaction that starts a trainer battle on right-click.
 *
 * Cobblemon already provides `q.npc.start_battle(...)` in MoLang, but that function swallows
 * errors: if the player has no Pokémon, is already battling, or the trainer has no team,
 * nothing happens and nothing is shown. Going through Kotlin lets us forward Cobblemon's own
 * error messages to the player, already localised by Cobblemon's language files.
 */
class TrainerBattleInteraction : NPCInteractConfiguration {

    override val type: String = TYPE

    override fun interact(npc: NPCEntity, player: ServerPlayer): Boolean {
        BattleBuilder.pvn(player = player, npcEntity = npc)
            .ifErrored { errors ->
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
    }
}
