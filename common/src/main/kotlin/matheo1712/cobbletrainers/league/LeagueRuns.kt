package matheo1712.cobbletrainers.league

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.status.PersistentStatus
import com.cobblemon.mod.common.util.party
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.advancement.LeagueCompletedTrigger
import matheo1712.cobbletrainers.trainers.TrainerProgress
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerRewards
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import kotlin.math.roundToInt

/**
 * The league a player is in the middle of: how far they are, and the state their team was left
 * in by the battle before.
 *
 * Three rules make a league more than its trainers in a row.
 *
 * **The order is the lock.** The first trainer of a league fights anyone, and fighting them
 * starts a run - or starts it again. Every other one fights only the player whose run has
 * reached them. See [refusal].
 *
 * **Nothing healed in between counts.** The team is noted as each battle leaves it, and brought
 * back down to that before the next one opens. It is a ceiling, never a restore: a Pokémon that
 * got *worse* in between stays worse. This is the whole point of the feature - a player is free
 * to walk to a healer between two battles, it just buys them nothing. See [prepare].
 *
 * **A battle that does not end in a win ends the run.** Losing, walking out of range, a
 * `/stopbattle`: a run remembers the battle it is waiting on, and one still waiting once that
 * battle is gone is dead. Nothing has to be told that the battle ended, which matters because
 * Cobblemon has no single event for it - a run is simply never alive again, and is replaced by
 * the next one. See [liveRun].
 *
 * Runs live in memory, one per player. A restart loses them, and a player is then asked to
 * start over; what is saved is the league once finished, in [TrainerProgress].
 */
object LeagueRuns {

    /** How one Pokémon came out of a battle. */
    private class Mark(val health: Int, val status: PersistentStatus?, val pp: Map<String, Int>)

    private class Run(val league: ResourceLocation) {
        /** How many trainers of the league are behind the player. */
        var step = 0

        /** The battle this run is waiting on, or null between two battles. */
        var battle: UUID? = null

        /** The team as the last battle left it, by Pokémon UUID. Null before the first win. */
        var team: Map<UUID, Mark>? = null
    }

    private val runs = mutableMapOf<UUID, Run>()

    /**
     * The player's run in that league, if it can still go on.
     *
     * A run with a battle on it is one whose battle never came back as a win: nobody asks this
     * in the middle of a battle - the dialogue does not open on a player who is in one - so a
     * battle still noted here is a battle that is over.
     */
    private fun liveRun(player: ServerPlayer, league: LeagueEntry): Run? =
        runs[player.uuid]?.takeIf { it.league == league.id && it.battle == null && it.step < league.members.size }

    /**
     * What a league member turns the player down with, or an empty list when the league has
     * nothing against the battle - which is always the case for a trainer in no league.
     *
     * Read-only, like every refusal: it is asked each time the dialogue box is built.
     */
    fun refusal(player: ServerPlayer, trainerId: ResourceLocation): List<Component> {
        val seat = TrainerLeagues.membershipOf(trainerId) ?: return emptyList()
        // The first trainer is the way in, and the way back in.
        if (seat.index == 0) return emptyList()

        val league = seat.league
        val run = liveRun(player, league)
        val next = run?.step ?: 0

        if (seat.index != next) {
            return listOf(
                CobblemonTrainers.lang(
                    "chat.league.order",
                    trainerName(trainerId),
                    Component.translatable(league.displayName),
                    trainerName(league.members[next])
                )
            )
        }

        if (run != null && !sameTeam(player, run)) {
            return listOf(
                CobblemonTrainers.lang(
                    "chat.league.team_changed",
                    Component.translatable(league.displayName),
                    trainerName(league.members.first())
                )
            )
        }

        return emptyList()
    }

    /**
     * Whether nobody has joined the team since the last battle. Leaving one behind is allowed -
     * it only makes the league harder - but a fresh Pokémon is exactly the heal a league
     * forbids, under another name.
     */
    private fun sameTeam(player: ServerPlayer, run: Run): Boolean {
        val team = run.team ?: return true
        return player.party().all { it.uuid in team }
    }

    /**
     * Brings the player's team back down to what the last battle of their run left, just before
     * the next one opens.
     *
     * Called on the way into every trainer battle; it does nothing for a trainer in no league,
     * for the first of one, and for a player whose run is not at this trainer.
     */
    fun prepare(player: ServerPlayer, trainerId: ResourceLocation) {
        val seat = TrainerLeagues.membershipOf(trainerId) ?: return
        val run = liveRun(player, seat.league)?.takeIf { it.step == seat.index } ?: return
        val team = run.team ?: return

        var lowered = false
        for (pokemon in player.party()) {
            val mark = team[pokemon.uuid] ?: continue
            if (lower(pokemon, mark, seat.league.heal)) lowered = true
        }

        if (lowered) {
            player.sendSystemMessage(
                CobblemonTrainers.lang("chat.league.carried", Component.translatable(seat.league.displayName))
                    .withStyle(ChatFormatting.GRAY)
            )
        }
    }

    /** @return whether anything had to be taken back. */
    private fun lower(pokemon: Pokemon, mark: Mark, heal: Double): Boolean {
        var lowered = false

        // A Pokémon that fainted stays down whatever the league gives back: `heal` is rest
        // between two battles, not a revive.
        val allowed = if (mark.health <= 0) 0
        else (mark.health + (pokemon.maxHealth * heal).roundToInt()).coerceAtMost(pokemon.maxHealth)

        if (pokemon.currentHealth > allowed) {
            pokemon.currentHealth = allowed
            lowered = true
        }

        if (mark.status != null && pokemon.status == null && allowed > 0) {
            pokemon.applyStatus(mark.status)
            lowered = true
        }

        for (move in pokemon.moveSet) {
            val left = mark.pp[move.name] ?: continue
            if (move.currentPp > left) {
                move.currentPp = left
                lowered = true
            }
        }

        return lowered
    }

    /**
     * A battle against a league member has really opened: this is where a run starts, and
     * where it notes the battle it now depends on.
     */
    fun battleStarted(player: ServerPlayer, trainerId: ResourceLocation, battleId: UUID) {
        val seat = TrainerLeagues.membershipOf(trainerId) ?: return

        val run = if (seat.index == 0) {
            Run(seat.league.id).also { runs[player.uuid] = it }
        } else {
            liveRun(player, seat.league)?.takeIf { it.step == seat.index } ?: return
        }
        run.battle = battleId
    }

    /** The player won that battle: the run moves on, or the league is finished. */
    fun battleWon(player: ServerPlayer, trainerId: ResourceLocation, battleId: UUID) {
        val seat = TrainerLeagues.membershipOf(trainerId) ?: return
        val league = seat.league
        val run = runs[player.uuid]
            ?.takeIf { it.league == league.id && it.battle == battleId && it.step == seat.index }
            ?: return

        run.battle = null
        run.step++

        if (run.step >= league.members.size) {
            runs.remove(player.uuid)
            complete(player, league)
            return
        }

        run.team = if (league.carriesDamage) snapshot(player) else null
        player.sendSystemMessage(
            CobblemonTrainers.lang(
                "chat.league.progress",
                Component.translatable(league.displayName),
                run.step,
                league.members.size,
                trainerName(league.members[run.step])
            ).withStyle(ChatFormatting.GOLD)
        )
    }

    /** The player lost that battle: said now, rather than discovered at the next trainer. */
    fun battleLost(player: ServerPlayer, trainerId: ResourceLocation, battleId: UUID) {
        val seat = TrainerLeagues.membershipOf(trainerId) ?: return
        val run = runs[player.uuid]?.takeIf { it.league == seat.league.id && it.battle == battleId } ?: return

        runs.remove(player.uuid)
        // Losing to the first trainer loses nothing a message could name.
        if (run.step == 0) return

        player.sendSystemMessage(
            CobblemonTrainers.lang(
                "chat.league.failed",
                Component.translatable(seat.league.displayName),
                trainerName(seat.league.members.first())
            ).withStyle(ChatFormatting.GRAY)
        )
    }

    private fun complete(player: ServerPlayer, league: LeagueEntry) {
        val first = TrainerProgress.of(player.server).recordLeague(league.id, player.uuid)
        // After the record, like a trainer's victory.
        LeagueCompletedTrigger.trigger(player, league.id)

        player.sendSystemMessage(
            CobblemonTrainers.lang("chat.league.completed", Component.translatable(league.displayName))
                .withStyle(ChatFormatting.GOLD)
        )
        TrainerRewards.grant(player, league.definition.rewards, first)
    }

    private fun snapshot(player: ServerPlayer): Map<UUID, Mark> =
        player.party().associate { pokemon ->
            pokemon.uuid to Mark(
                health = pokemon.currentHealth,
                status = pokemon.status?.status,
                pp = pokemon.moveSet.associate { it.name to it.currentPp }
            )
        }

    private fun trainerName(trainerId: ResourceLocation): Component =
        TrainerRegistry.get(trainerId)?.name?.takeIf { it.isNotBlank() }?.let { Component.translatable(it) }
            ?: Component.literal(trainerId.toString())

    /** A world that closed takes its runs with it: the next one may hold other leagues. */
    fun clear() = runs.clear()
}
