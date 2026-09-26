package matheo1712.cobbletrainers.battle

import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.activestate.SentOutState
import com.cobblemon.mod.common.util.party
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Who steps forward when a trainer battle opens.
 *
 * The rule both sides answer to is that **a fainted Pokémon never opens a battle**. Cobblemon
 * 1.7.3 enforced none of it: `PartyStore.toBattleTeam` handed Showdown the party in slot order
 * without ever reading a health bar, so whoever sat in slot one led even flat on their back, and
 * Showdown answered a battle it could not open with `Can't switch: You can't switch to a fainted
 * Pokémon` - an error that reached nobody, leaving the side that sent it unable to act for the
 * rest of the fight. That was the soft lock of issue #32, and it hit a player and a trainer for
 * the same reason.
 *
 * **Cobblemon 1.8 closed that hole itself.** `toBattleTeam` now ends on a stable
 * `sortedBy { it.health <= 0 }`, applied after the healing and after `leadingPokemon` is moved to
 * the front, so the fainted fall behind every healthy Pokémon on both sides of the battle. What
 * is left here is the half Cobblemon still cannot know - *which* of the player's healthy Pokémon
 * they meant to lead with. Three answers, in order of how well each knows the player's mind:
 *
 * 1. The slot selected in the party overlay, which only the client knows and only announces when
 *    our own client is installed (see `BattleLeadNetworking`).
 * 2. The Pokémon the player has sent out. That is the selection as of the last time they threw
 *    one, and the server can see it without being told.
 * 3. The first one in the party able to fight, which is Cobblemon's own default whenever the
 *    party is in good order, and the correction when it is not.
 *
 * A fainted Pokémon is skipped at every step, so the answer is never one Cobblemon's sort would
 * move afterwards. The exception is a level-adjusting format: `pvn` heals the copies it battles
 * with, so a fainted selection is honoured rather than skipped - nothing is fainted by the time
 * it matters.
 *
 * The trainer's side needs nothing from us any more. An `orderTeam` used to reorder its
 * `NPCPartyStore` in place, because `pvn` offers no `leadingPokemon` for that side and a trainer
 * on `battle.healParty: false` carries its damage from one fight to the next. It went out with
 * the 1.8 bump: Cobblemon's own sort covers that case, and covers it without writing to a store
 * that outlives the battle. Do not bring it back.
 *
 * A `teamOrder` went out with it. It rebuilt the opening line-up so that
 * `TrainerBattleInteraction.partyRefusal` could look at it, and 1.8 made that the wrong shape:
 * `pvn` now refuses a side whose standing Pokémon number fewer than the format's slots, so what
 * decides is a count, and a count does not care what order the list is in.
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
     * The UUID to hand `pvn` as its `leadingPokemon`, or null when nobody can open the battle.
     *
     * The announced selection is a hint from a client and is resolved against the real party
     * here: a Pokémon the player does not own, or no longer owns, finds nothing and falls
     * through to the next answer.
     *
     * Null means the party has nobody to send, which `TrainerBattleInteraction.refusal` has
     * already turned away - and is left to Cobblemon only for the empty party, whose error says
     * it better than we would.
     */
    fun leadFor(player: ServerPlayer, format: BattleFormat): UUID? {
        val party = player.party()
        val healed = format.adjustLevel > 0

        val selected = selections[player.uuid]?.let { id -> party.find { it.uuid == id } }
        if (selected != null && selected.canLead(healed)) return selected.uuid

        val sentOut = party.find { it.state is SentOutState }
        if (sentOut != null && sentOut.canLead(healed)) return sentOut.uuid

        return party.find { it.canLead(healed) }?.uuid
    }

    private fun Pokemon.canLead(healed: Boolean): Boolean = healed || !isFainted()
}
