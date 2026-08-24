package matheo1712.cobbletrainers.battle

import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.activestate.SentOutState
import com.cobblemon.mod.common.util.party
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Who steps forward when a trainer battle opens, on either side of it.
 *
 * One rule holds both halves together: **a fainted Pokémon never opens a battle**. Nothing in
 * Cobblemon enforces it. `PartyStore.toBattleTeam` hands Showdown the party in slot order and
 * never reads a health bar, so whoever sits in slot one leads even flat on their back, and
 * Showdown answers a battle it cannot open with `Can't switch: You can't switch to a fainted
 * Pokémon` - an error that reaches nobody, leaving the side that sent it unable to act for the
 * rest of the fight. That is the soft lock of issue #32, and it happens to a player and to a
 * trainer for the same reason.
 *
 * The player's side is answered with `leadingPokemon`, which `pvn` passes on to
 * `toBattleTeam`, which moves that Pokémon to the front. Three answers, in order of how well
 * each knows the player's mind, and a fourth that is only there to keep Showdown out of
 * trouble:
 *
 * 1. The slot selected in the party overlay, which only the client knows and only announces
 *    when our own client is installed (see `BattleLeadNetworking`).
 * 2. The Pokémon the player has sent out. That is the selection as of the last time they threw
 *    one, and the server can see it without being told.
 * 3. The first one in the party able to fight, which is Cobblemon's own default whenever the
 *    party is in good order, and the correction when it is not.
 *
 * A fainted Pokémon is skipped at every step. The exception is a level-adjusting format: `pvn`
 * heals the copies it battles with, so a fainted selection is honoured rather than skipped -
 * nothing is fainted by the time it matters.
 *
 * The trainer's side has no such parameter, so [orderTeam] fixes the party itself. See there
 * for why that is allowed.
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
     * Null used to mean "keep Cobblemon's default", which is exactly the default that opens a
     * battle on a fainted Pokémon. It now means the party has nobody to send, which
     * `TrainerBattleInteraction.refusal` has already turned away - and is left to Cobblemon
     * only for the empty party, whose error says it better than we would.
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

    /**
     * The party in the order Showdown will receive it: [leadFor]'s answer first, everyone else
     * behind it in party order. Exactly what `toBattleTeam` builds, so that
     * `TrainerBattleInteraction` can see the opening line-up before committing to it.
     */
    fun teamOrder(player: ServerPlayer, format: BattleFormat): List<Pokemon> {
        val team = player.party().toMutableList()
        val lead = leadFor(player, format)?.let { id -> team.firstOrNull { it.uuid == id } }
            ?: return team

        team.remove(lead)
        team.add(0, lead)
        return team
    }

    /**
     * Moves a trainer's fainted Pokémon behind the rest of its party.
     *
     * `NPCBattleActor` builds the trainer's team with `toBattleTeam` too, and `pvn` offers no
     * `leadingPokemon` for that side: the order in the store *is* the order Showdown gets. A
     * trainer whose `battle.healParty` is false keeps its damage between fights, so the one
     * that lost its first Pokémon last time would open the next battle with it and lock itself
     * out of the fight.
     *
     * Rewriting the store is allowed here and not on the player's side: this party is ours,
     * built at spawn, with no arrangement a player could have chosen. Cobblemon still refuses
     * the battle when too few of them are standing, so this only ever decides who goes first.
     *
     * A healing trainer is left alone. `toBattleTeam` heals its team a moment later, so nothing
     * in it is fainted when it matters, and reordering now would quietly rewrite the lead a
     * pack chose.
     */
    fun orderTeam(npc: NPCEntity) {
        if (npc.npc.autoHealParty) return
        val party = npc.party ?: return

        // Slot indices rather than a plain 0-until-size walk: a party may have gaps, and only
        // the order of what is in it reaches `toBattleTeam`.
        val occupied = (0 until party.size()).filter { party.get(it) != null }
        var placed = 0

        for (slot in occupied) {
            // Read fresh: an earlier swap may have moved a fainted Pokémon into this slot.
            if (party.get(slot)?.isFainted() != false) continue

            val destination = occupied[placed]
            if (slot != destination) party.swap(slot, destination)
            placed++
        }
    }

    private fun Pokemon.canLead(healed: Boolean): Boolean = healed || !isFainted()
}
