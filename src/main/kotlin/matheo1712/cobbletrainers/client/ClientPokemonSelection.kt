package matheo1712.cobbletrainers.client

import com.cobblemon.mod.common.client.CobblemonClient
import matheo1712.cobbletrainers.network.SelectedPokemonPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import java.util.UUID

/**
 * Reports the party slot the player has selected to the server.
 *
 * Cobblemon keeps that selection to itself (`ClientStorageManager.selectedSlot`); the server
 * never learns it, so a trainer battle would always lead with slot one. There is no event to
 * listen to either - the slot is a plain field that keybinds write - hence the tick, which does
 * nothing but compare a UUID and only speaks when the answer changes.
 *
 * This is the mod's only client code that is not a screen, and it stays deliberately dumb: it
 * states what is selected and decides nothing. `TrainerLead` is what turns that into a battle
 * lead, and it re-checks the Pokémon against the player's real party.
 */
object ClientPokemonSelection {

    private var announced: UUID? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player == null) return@register
            announce(selected())
        }

        // Nothing is remembered across servers: the next one is told again on its first tick.
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> announced = null }
    }

    /**
     * The selected Pokémon, or null when the selection points at an empty slot. Cobblemon fills
     * its client storage as the world loads and declares the party non-null throughout, so the
     * read is guarded rather than trusted: a tick that lands before the party is there answers
     * "nothing selected" instead of throwing into the client tick loop.
     */
    private fun selected(): UUID? = runCatching {
        val storage = CobblemonClient.storage
        storage.party.get(storage.selectedSlot)?.uuid
    }.getOrNull()

    private fun announce(pokemon: UUID?) {
        if (pokemon == announced) return
        if (!ClientPlayNetworking.canSend(SelectedPokemonPayload.TYPE)) return

        announced = pokemon
        ClientPlayNetworking.send(SelectedPokemonPayload(pokemon))
    }
}
