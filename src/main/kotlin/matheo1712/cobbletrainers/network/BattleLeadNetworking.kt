package matheo1712.cobbletrainers.network

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.battle.TrainerLead
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

/**
 * The one packet that tells the server which Pokémon a player has selected.
 *
 * Cobblemon keeps that selection on the client alone (`ClientStorageManager.selectedSlot`): no
 * part of it is ever sent to the server, which is why a trainer battle would otherwise always
 * lead with party slot one. The client announces its selection whenever it changes, and
 * `TrainerLead` reads the last one announced.
 *
 * This is only ever a hint. Nothing is trusted: the UUID is looked up in the player's real party
 * at battle time, so a client that sends someone else's Pokémon, or one it no longer owns, is
 * simply ignored.
 */
object BattleLeadNetworking {

    fun register() {
        PayloadTypeRegistry.playC2S().register(SelectedPokemonPayload.TYPE, SelectedPokemonPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(SelectedPokemonPayload.TYPE) { payload, context ->
            TrainerLead.remember(context.player(), payload.pokemon)
        }

        // The selection belongs to a session, not to a player: the next one starts unannounced
        // and falls back to whatever the world can tell us.
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            TrainerLead.forget(handler.player)
        }
    }
}

/**
 * Client -> server: the Pokémon the player has selected in the party overlay, or null when the
 * selection points at nothing.
 */
data class SelectedPokemonPayload(val pokemon: UUID?) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<SelectedPokemonPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<SelectedPokemonPayload> =
            CustomPacketPayload.Type(CobblemonTrainers.id("selected_pokemon"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, SelectedPokemonPayload> =
            CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeBoolean(payload.pokemon != null)
                    payload.pokemon?.let { buf.writeUUID(it) }
                },
                { buf ->
                    SelectedPokemonPayload(if (buf.readBoolean()) buf.readUUID() else null)
                }
            )
    }
}
