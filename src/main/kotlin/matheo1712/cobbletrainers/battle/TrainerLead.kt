package matheo1712.cobbletrainers.battle

import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.activestate.SentOutState
import com.cobblemon.mod.common.util.party
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Which Pokémon opens a trainer battle for a player.
 *
 * Cobblemon leads with party slot one and nothing else: `BattleBuilder.pvn` takes a
 * `leadingPokemon`, which `PartyStore.toBattleTeam` moves to the front of the team, but no
 * caller of ours ever filled it. A player who walks around with their partner out, or who has
 * picked another slot in the overlay, expects that one to step forward.
 *
 * Three answers, in order of how well each knows the player's mind:
 *
 * 1. The slot selected in the party overlay, which only the client knows and only announces
 *    when our own client is installed (see `BattleLeadNetworking`).
 * 2. The Pokémon the player has sent out. That is the selection as of the last time they threw
 *    one, and the server can see it without being told.
 * 3. Nothing, leaving Cobblemon its first-slot default.
 *
 * A fainted Pokémon is never picked, and the cascade carries on past it.
 * `TrainerBattleInteraction` has already turned away the player whose whole team is down, so
 * what reaches here always has someone able to step forward. The exception is a level-adjusting
 * format: it heals the team it battles with, so a fainted selection is honoured rather than
 * skipped - nothing is fainted by the time it matters.
 */
object TrainerLead {

    /**
     * Last selection announced by each player's client. Session state: a disconnect drops the
     * entry rather than remembering a slot the player may have reorganised since.
     */
    private val selections = ConcurrentHashMap<UUID, UUID>()

    /** Records what a client says it has selected. `null` clears it. */
    fun remember(player: ServerPlayer, pokemon: UUID?) {
        if (pokemon == null) selections.remove(player.uuid) else selections[player.uuid] = pokemon
    }

    fun forget(player: ServerPlayer) {
        selections.remove(player.uuid)
    }

    /**
     * The UUID to hand `pvn` as its `leadingPokemon`, or null to keep Cobblemon's default.
     *
     * The announced selection is a hint from a client and is resolved against the real party
     * here: a Pokémon the player does not own, or no longer owns, finds nothing and falls
     * through to the next answer.
     */
    fun leadFor(player: ServerPlayer, format: BattleFormat): UUID? {
        val party = player.party()
        val healed = format.adjustLevel > 0

        val selected = selections[player.uuid]?.let { id -> party.find { it.uuid == id } }
        if (selected != null && selected.canLead(healed)) return selected.uuid

        val sentOut = party.find { it.state is SentOutState }
        if (sentOut != null && sentOut.canLead(healed)) return sentOut.uuid

        return null
    }

    private fun Pokemon.canLead(healed: Boolean): Boolean = healed || !isFainted()
}
