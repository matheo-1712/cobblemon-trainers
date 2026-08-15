package matheo1712.cobbletrainers

import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.entity.npc.NPCEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Interaction de NPC qui lance un combat de dresseur au clic droit.
 *
 * Cobblemon fournit déjà `q.npc.start_battle(...)` en MoLang, mais cette fonction avale
 * les erreurs : si le joueur n'a pas de Pokémon, s'il est déjà en combat ou si le dresseur
 * n'a pas d'équipe, rien ne se passe et rien n'est affiché. Passer par du Kotlin permet de
 * renvoyer au joueur les messages d'erreur de Cobblemon.
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

    // Rien à synchroniser : l'interaction n'a aucun paramètre.
    override fun encode(buffer: RegistryFriendlyByteBuf) = Unit
    override fun decode(buffer: RegistryFriendlyByteBuf) = Unit
    override fun writeToNBT(compoundTag: CompoundTag) = Unit
    override fun readFromNBT(compoundTag: CompoundTag) = Unit

    override fun isDifferentTo(other: NPCInteractConfiguration): Boolean = other !is TrainerBattleInteraction

    companion object {
        /** Valeur du champ `type` dans le JSON du NPCClass. */
        const val TYPE = "cobblemon-trainers:battle"

        /**
         * À appeler à l'initialisation du mod : le type doit être connu avant que les
         * datapacks ne soient lus, sinon la désérialisation du NPCClass échoue.
         */
        fun register() {
            NPCInteractConfiguration.register(
                type = TYPE,
                displayName = Component.literal("Combat de dresseur"),
                clazz = TrainerBattleInteraction::class.java
            )
        }
    }
}
