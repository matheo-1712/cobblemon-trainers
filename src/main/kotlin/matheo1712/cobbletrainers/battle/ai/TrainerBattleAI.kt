package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.InBattleMove
import com.cobblemon.mod.common.battles.MoveActionResponse
import com.cobblemon.mod.common.battles.ShowdownActionResponse
import com.cobblemon.mod.common.battles.ShowdownMoveset
import com.cobblemon.mod.common.battles.SwitchActionResponse
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import matheo1712.cobbletrainers.CobblemonTrainers.LOGGER
import java.util.UUID
import kotlin.math.roundToInt

/**
 * A correction layer over Cobblemon's `StrongBattleAI`, applied to the trainers of this mod.
 *
 * Every decision is still Cobblemon's; this only refuses the ones that cannot be right and says
 * what to do instead. What it covers, each observed in play at skill 5:
 *
 * 1. **Moves that cannot do anything.** `findAndUseMostDamagingMove` takes the `maxByOrNull` of
 *    its damage estimates with no floor, so when every option lands on zero the *first* entry
 *    wins - a Ground move at an immune target. And `choose` ends on a plain
 *    `availableMoves.randomOrNull()` whenever no branch decided, immunities included.
 * 2. **Immunities granted by an ability or an item**, which the damage estimate never consults.
 *    See [BattleTypeChart].
 * 3. **Switch loops.** `checkSwitchOutSkill` obeys `shouldSwitchOut` with probability
 *    `0, 0, 0, 0.2, 0.6, 1.0` for skill 0 to 5 - so at skill 5 it obeys *always*, and
 *    `shouldSwitchOut` has no memory of having just switched. A bad matchup therefore switches
 *    to another bad matchup, every turn, while the player attacks for free.
 * 4. **Healing on a flat rule.** Cobblemon plays the first self-recovery move it finds as soon
 *    as health is under half, without ever asking what the opponent hits back for, or whether
 *    a KO was available this turn. See [correctHeal].
 *
 * A trainer's `battle.difficulty` doses all of this, see [CorrectionLevel]. A wrapper cannot
 * make Cobblemon's AI know *less* - its tracker reads the real `Pokemon` objects and we have no
 * hold on that - so difficulty grades the quality of the decisions rather than the information
 * behind them. It also cannot make the AI act where Cobblemon proposed nothing: the layer can
 * refuse a heal, never add one.
 *
 * One thing here is not a correction at all: the battle gimmicks a trainer declares - Mega
 * Evolution today - are attached to whatever move comes out, difficulty included. See
 * [withGimmick] and [TrainerGimmicks].
 */
class TrainerBattleAI(
    private val delegate: BattleAI,
    private val difficulty: Int,
    gimmicks: List<String> = emptyList()
) : BattleAI {

    private val level = CorrectionLevel.of(difficulty)

    /**
     * Whether this trainer mega evolves when the battle offers it - the `mega` of its
     * `battle.gimmicks`. Read once: the definition cannot change mid-battle, and a `/reload`
     * would not reach a fight already under way.
     */
    private val usesMega = TrainerGimmicks.uses(gimmicks, TrainerGimmicks.MEGA)

    /**
     * The request the mega evolution was answered on, or null while it is still to come.
     *
     * Not a plain boolean, because it answers two questions at once. Cobblemon asks each active
     * Pokémon *twice* a turn (see [lastRequest]) and the second answer overwrites the first, so
     * the same request has to be answered the same way or the gimmick would be dropped on the
     * way out. And a side may only mega evolve once, so the *other* active of a double battle,
     * asked on the same turn, has to be turned down. Keyed like any other decision, both fall
     * out of one comparison.
     */
    private var megaPlayedFor: DecisionKey? = null

    /**
     * Whether the opening move has already been spent. One flag for the whole actor, so that in
     * a double battle the second lead does not lay a second helping of the same hazard.
     */
    private var openingPlayed = false

    /**
     * Opponents this trainer has already aimed a damaging move at.
     *
     * The only reliable way to know a Disguise or an Ice Face has been broken: the form aspect
     * says so too, but only if Cobblemon mirrors the Showdown form change back onto the Pokémon.
     * Keyed by battle UUID, so a Pokémon that switches out and back is still remembered - which
     * is right, a broken Disguise does not come back.
     */
    private val struck = mutableSetOf<UUID>()

    /** Whether the previous decision was a Protect, which is what makes a second one a bad bet. */
    private var lastWasProtect = false

    /**
     * The decision already given for the request being answered, and what it was answering.
     *
     * Cobblemon asks an AI actor **more than once per turn**: `RequestInstruction` and
     * `TurnInstruction` each send a `BattleMakeChoicePacket`, and `AIBattleActor.sendUpdate`
     * turns every one of them into an `onChoiceRequested`. It overwrites its own answer each
     * time, so nothing showed - until this layer started keeping notes between turns and
     * printing them.
     *
     * Answering the repeat from memory is not only about the chat. The second pass would read a
     * [struck] set the first pass had already filled, so an unbroken Disguise would look broken
     * and the trainer would spend its best move on it after all.
     */
    private var lastRequest: DecisionKey? = null
    private var lastResponse: ShowdownActionResponse? = null

    override fun onHealthChange(packet: BattleHealthChangePacket) = delegate.onHealthChange(packet)

    override fun choose(
        activePokemon: ActiveBattlePokemon,
        battle: PokemonBattle,
        aiSide: BattleSide,
        moveset: ShowdownMoveset?,
        forceSwitch: Boolean
    ): ShowdownActionResponse {
        val request = DecisionKey(battle.turn, activePokemon.battlePokemon?.uuid, forceSwitch)
        val decision = decide(activePokemon, battle, aiSide, moveset, forceSwitch, request)
        return withGimmick(decision, activePokemon, battle, moveset, request)
    }

    /** The move or the switch, before any gimmick is attached to it. */
    private fun decide(
        activePokemon: ActiveBattlePokemon,
        battle: PokemonBattle,
        aiSide: BattleSide,
        moveset: ShowdownMoveset?,
        forceSwitch: Boolean,
        request: DecisionKey
    ): ShowdownActionResponse {
        val choice = delegate.choose(activePokemon, battle, aiSide, moveset, forceSwitch)
        if (level == CorrectionLevel.NONE || moveset == null) return choice

        // The same question asked twice gets the same answer, as long as it still stands - a
        // response the battle would reject has to be worked out again rather than repeated.
        lastResponse?.let {
            if (request == lastRequest && it.isValid(activePokemon, moveset, forceSwitch)) return it
        }

        // A battle waits on this answer: anything thrown here would leave the player stuck in a
        // fight nobody can act in. Cobblemon's own choice is always a valid fallback.
        return try {
            val situation = read(activePokemon, battle, moveset)
            val corrected = if (situation == null) choice else correct(choice, situation, forceSwitch)
            lastRequest = request
            lastResponse = corrected
            corrected
        } catch (exception: Exception) {
            LOGGER.error("Trainer AI correction failed, keeping Cobblemon's choice", exception)
            choice
        }
    }

    /**
     * Mega evolves alongside the move that was just chosen, if the trainer has one to spend and
     * the battle is offering it.
     *
     * Deliberately outside everything above: a gimmick is the pack's decision - it gave the
     * stone and wrote the word - not a matter of how well the trainer plays, so it must not sit
     * behind the [CorrectionLevel] gate that lets a low-difficulty trainer past untouched.
     *
     * The moment needs no judging either. Mega evolving costs no turn and is offered exactly
     * while it is legal, so the first chance is always as good as any later one - which is also
     * what a trainer does in the games. The one thing to get right is spending it once, see
     * [megaPlayedFor].
     */
    private fun withGimmick(
        response: ShowdownActionResponse,
        active: ActiveBattlePokemon,
        battle: PokemonBattle,
        moveset: ShowdownMoveset?,
        request: DecisionKey
    ): ShowdownActionResponse {
        if (!usesMega || moveset == null || response !is MoveActionResponse) return response
        if (!TrainerGimmicks.offered(moveset, TrainerGimmicks.MEGA)) return response

        // Answering the same request again keeps the gimmick; the other active Pokémon of a
        // double battle, asked on the same turn, does not get a second one. Across turns there
        // is nothing to refuse: a mega evolution that went through stops being offered, and one
        // that somehow did not is worth another try rather than lost for the whole battle.
        val played = megaPlayedFor
        if (played != null && played != request && played.turn == request.turn) return response

        if (played != request) {
            megaPlayedFor = request
            val detail = "mega evolves, playing ${response.moveName}"
            LOGGER.debug("Trainer AI: {}", detail)
            if (!TrainerAiDebug.idle()) {
                val name = active.battlePokemon?.effectedPokemon?.species?.name ?: "?"
                TrainerAiDebug.report(battle, "$name: $detail")
            }
        }

        return response.copy(gimmickID = TrainerGimmicks.MEGA)
    }

    /** Everything a correction needs, read once per turn. Null when there is nothing to judge. */
    private fun read(
        active: ActiveBattlePokemon,
        battle: PokemonBattle,
        moveset: ShowdownMoveset
    ): Situation? {
        val selfBattle = active.battlePokemon ?: return null
        val opponents = active.getAllActivePokemon()
            .filterIsInstance<ActiveBattlePokemon>()
            .filter { !active.isAllied(it) && it.isAlive() }
            .mapNotNull { it.battlePokemon }
        if (opponents.isEmpty()) return null

        val usable = moveset.moves.filter { it.canBeUsed() && !it.disabled }
        // Encore, Outrage and friends leave nothing to decide.
        if (usable.any { it.mustBeUsed() }) return null

        return Situation(
            battle = battle,
            active = active,
            selfBattle = selfBattle,
            opponents = opponents,
            moveset = moveset,
            moves = usable.map { score(it, selfBattle, opponents) },
            guarded = opponents.any { BattleGuards.guardIntact(it, it.uuid in struck) }
        )
    }

    private fun correct(
        choice: ShowdownActionResponse,
        situation: Situation,
        forceSwitch: Boolean
    ): ShowdownActionResponse {
        if (!forceSwitch && !openingPlayed) {
            openingPlayed = true
            leadHazard(situation)?.let { return it }
        }

        val result = when (choice) {
            is MoveActionResponse -> correctMove(choice, situation)
            is SwitchActionResponse -> correctSwitch(choice, situation, forceSwitch)
            else -> choice
        }
        remember(result, situation)
        return result
    }

    /** Notes what was played, for the two rules that need to know what happened last turn. */
    private fun remember(response: ShowdownActionResponse, s: Situation) {
        val played = (response as? MoveActionResponse)
            ?.moveName
            ?.let { id -> s.moves.firstOrNull { it.move.id == id } }

        lastWasProtect = played?.protect == true
        if (played?.damaging == true) s.opponents.forEach { struck.add(it.uuid) }
    }

    /**
     * Lays an entry hazard on the very first turn, if the lead carries one.
     *
     * This is the one rule that *adds* a decision instead of refusing one: Cobblemon does know
     * about hazards, but nothing makes it open with them, so a lead holding Stealth Rock will
     * often just attack and never get another quiet turn. Setting them turn one is what a
     * prepared trainer does, and it changes the whole battle rather than one exchange.
     *
     * It turns on at difficulty 4 rather than with a whole [CorrectionLevel], because that is
     * where it was asked for: a level 3 trainer plays honestly, a level 4 one has a game plan.
     *
     * No check that the hazard is already down is needed - on turn one nothing has moved yet -
     * and [openingPlayed] is what keeps two leads in a double battle from laying the same one
     * twice.
     */
    private fun leadHazard(s: Situation): ShowdownActionResponse? {
        if (difficulty < LEAD_HAZARD_DIFFICULTY) return null

        val hazard = hazardPriority
            .firstNotNullOfOrNull { id -> s.moves.firstOrNull { it.move.id == id } }
            ?: return null

        return use(hazard.move, s, "opening the battle with a hazard")
    }

    private fun correctMove(choice: MoveActionResponse, s: Situation): ShowdownActionResponse {
        // An unknown id means a gimmick move or something we cannot judge: leave it alone.
        val chosen = s.moves.firstOrNull { it.move.id == choice.moveName } ?: return choice

        if (level == CorrectionLevel.FULL) {
            tactics(chosen, s)?.let { return it }
            if (chosen.recovery) return choice
        }

        if (chosen.useless) {
            s.best?.let { return use(it.move, s, "'${choice.moveName}' does nothing to that target") }
            // Nothing in the move set can achieve anything. Leaving is the right call, and it is
            // the one case where a switch needs no matchup gain to justify itself.
            switchAway(s)?.let { return it }
            val fallback = s.moves.maxByOrNull { it.damage } ?: return choice
            return use(fallback.move, s, "no move does anything and nobody better to send")
        }

        if (level == CorrectionLevel.FULL && !chosen.purposeful) {
            val best = s.best
            if (best != null && best.damage > chosen.damage) {
                return use(best.move, s, "'${choice.moveName}' is a weaker attack with no stated purpose")
            }
        }

        return choice
    }

    /**
     * The reading a trainer only does at full difficulty: what the opponent survives, what it
     * hits back with, and who moves first.
     *
     * These are the rules that make a champion feel prepared rather than merely correct, which
     * is why they stop at [CorrectionLevel.FULL] - a route trainer that never misjudges a
     * knockout and never wastes a turn is not a route trainer.
     *
     * Returns null to leave Cobblemon's choice alone.
     */
    private fun tactics(chosen: ScoredMove, s: Situation): ShowdownActionResponse? {
        if (chosen.recovery) return correctHeal(chosen, s)

        // Protect twice running is a one-in-three bet in mainline, and the opponent gets a free
        // turn when it fails. Cobblemon plays them on a flat random chance and never looks back.
        if (chosen.protect && lastWasProtect) {
            return s.bestAttack?.let { use(it.move, s, "protecting twice running rarely holds") }
        }

        // A Disguise or an Ice Face swallows the next hit whole, whatever it was. Breaking it with
        // the cheapest move keeps the real one for the turn after - the rule further down would
        // otherwise feed it the strongest.
        if (chosen.damaging && s.guarded) {
            s.cheapestAttack?.let {
                if (it.move.id != chosen.move.id) {
                    return use(it.move, s, "a guard eats this hit, breaking it cheaply instead")
                }
            }
        }

        // A knockout that lands first ends the exchange, and nothing else this turn is worth that.
        if (!chosen.kills) {
            s.moves
                .filter { it.kills && BattleSpeed.movesFirst(s.selfBattle, it.priority, s.opponents) }
                .maxByOrNull { it.damage }
                ?.let { return use(it.move, s, "it knocks out before they answer") }
        }

        // About to be knocked out, and answering second: this move never resolves. Only something
        // with priority still lands, and a Pokemon on its last turn should spend it on the biggest
        // hit that will actually happen - a Shadow Sneak under a Swords Dance rather than a Play
        // Rough that arrives after the knockout.
        if (s.facingLethal && !BattleSpeed.movesFirst(s.selfBattle, chosen.priority, s.opponents)) {
            val resolves = s.moves
                .filter {
                    it.damaging &&
                        !it.useless &&
                        BattleSpeed.movesFirst(s.selfBattle, it.priority, s.opponents)
                }
                .maxByOrNull { it.damage }

            if (resolves != null && resolves.move.id != chosen.move.id) {
                return use(resolves.move, s, "last turn, only a priority move still lands")
            }

            // Nothing resolves at all. The estimate could still be wrong - a miss, a low roll - so
            // swinging beats setting up for a turn that probably will not come.
            if (!chosen.damaging) {
                return s.bestAttack?.let { use(it.move, s, "no turn left to spend on setting up") }
            }
        }

        return null
    }

    /**
     * Judges a self-recovery move Cobblemon wants to play.
     *
     * Its rule is `currentHpPercent < 0.5`, and nothing else: not what the opponent hits back
     * for, not whether the move would overflow, not whether a KO was in hand. Three questions
     * are asked here instead, and any of them can send the trainer back to attacking. None of
     * them can make it heal when Cobblemon did not offer to - a wrapper only refuses.
     *
     * Returns null to let the heal through.
     */
    private fun correctHeal(chosen: ScoredMove, s: Situation): ShowdownActionResponse? {
        s.moves.filter { it.kills }.maxByOrNull { it.damage }?.let {
            return use(it.move, s, "no time to heal, ${it.move.id} is lethal this turn")
        }

        val healed = healAmount(chosen.move.id, s.selfBattle)
        val missing = (s.selfBattle.maxHealth - s.selfBattle.health).toDouble()
        val incoming = BattleDamage.worstIncoming(s.selfBattle, s.opponents)

        // Healing has to buy a turn. When the opponent hits back for at least as much, the
        // trainer ends the turn exactly where it started, one move of PP down.
        if (incoming >= healed) {
            return s.bestAttack?.let {
                use(it.move, s, "healing gains nothing: takes ${round(incoming)} back, restores ${round(healed)}")
            }
        }

        // The flat 50% ignores what the move actually restores, so most of it can spill over.
        if (missing < healed * MIN_HEAL_USED) {
            return s.bestAttack?.let {
                use(it.move, s, "healing would overflow: ${round(missing)} missing, restores ${round(healed)}")
            }
        }

        return null
    }

    /**
     * Refuses a switch that buys nothing.
     *
     * The gain test is about types, but only one of Cobblemon's four reasons to switch is:
     * `shouldSwitchOut` also fires under 30% health, at accuracy -3 or worse, and on Truant or
     * Slow Start. Measuring those on a type scale is what stopped the trainer switching at all,
     * so [nonTypeReason] hands them straight back to Cobblemon. What is left - the genuinely
     * type-driven switch - has to gain [MATCHUP_GAIN_REQUIRED], which is the anti-loop rule:
     * fleeing towards someone just as badly off is no longer an option.
     */
    private fun correctSwitch(
        choice: SwitchActionResponse,
        s: Situation,
        forceSwitch: Boolean
    ): ShowdownActionResponse {
        // A forced switch follows a faint: there is no alternative to second-guess.
        if (forceSwitch) return choice
        // Nothing to attack with anyway, so any exit beats staying.
        if (s.moves.all { it.useless }) return choice

        nonTypeReason(s)?.let {
            report(s, "switch kept, $it")
            return choice
        }

        val incoming = s.active.actor.pokemonList
            .firstOrNull { it.uuid == choice.newPokemonId }
            ?.effectedPokemon
            ?: return choice

        val gain = matchup(incoming, s.opponentPokemon) - matchup(s.self, s.opponentPokemon)
        if (gain >= MATCHUP_GAIN_REQUIRED) return choice

        val best = s.best ?: return choice
        return use(best.move, s, "switching in ${incoming.species.name} gains only ${round(gain)}")
    }

    /**
     * Cobblemon's reasons to switch that have nothing to do with types, and that the gain test
     * would therefore always score at zero. A trainer at 20% health is not looking for a better
     * matchup, it is trying to keep a Pokémon alive.
     */
    private fun nonTypeReason(s: Situation): String? {
        if (s.hpFraction < HP_RETREAT) return "health down to ${percent(s.hpFraction)}"

        val crushed = s.selfBattle.statChanges.entries.firstOrNull { it.value <= STAT_FLOOR }
        if (crushed != null) return "${crushed.key} down to ${crushed.value}"

        // Everything resisted. Not the same as the total impasse handled in correctMove: here the
        // moves do land, they just do not matter.
        val reach = s.moves.filter { it.damaging && !it.useless }
        if (reach.isNotEmpty() && reach.maxOf { it.multiplier } <= WALLED_MULTIPLIER) {
            return "walled, best effectiveness x${reach.maxOf { it.multiplier }}"
        }

        return null
    }

    /** The best benched Pokémon, when one would genuinely do better than what is out. */
    private fun switchAway(s: Situation): ShowdownActionResponse? {
        if (s.moveset.trapped) return null

        val current = matchup(s.self, s.opponentPokemon)
        val candidate = s.active.actor.pokemonList
            .filter { !it.gone && it.health > 0 && !it.isSentOut() }
            .maxByOrNull { matchup(it.effectedPokemon, s.opponentPokemon) }
            ?: return null

        // Strictly better, so that an impasse cannot hand the loop back to us: a replacement
        // that is no better would be switched away from again on the next turn.
        if (matchup(candidate.effectedPokemon, s.opponentPokemon) <= current) return null

        report(s, "switches out, nothing in the move set can do anything")
        return SwitchActionResponse(candidate.uuid)
    }

    /**
     * How good [mon] is against [opponents]: what it can hit them with, less what they can hit
     * it with. Types only - it ranks candidates, it does not predict a battle.
     */
    private fun matchup(mon: Pokemon, opponents: List<Pokemon>): Double {
        val threat = opponents.maxOfOrNull { BattleTypeChart.offence(it, listOf(mon)) } ?: 0.0
        return BattleTypeChart.offence(mon, opponents) - threat
    }

    private fun score(move: InBattleMove, selfBattle: BattlePokemon, opponents: List<BattlePokemon>): ScoredMove {
        val template = Moves.getByName(move.id)
            ?: return ScoredMove(move, purposeful = true)

        val purposeful = move.id in purposefulMoves
        val recovery = move.id in AIUtility.selfRecoveryMoves

        val status = AIUtility.statusMoves[move.id]
        if (status != null) {
            // A status nobody can catch: Toxic on a Steel type, a burn on a Fire type, or simply
            // a target that already carries one. Only refused at full correction, since missing
            // it is a plausible mistake rather than an impossible move.
            val lands = opponents.any {
                it.effectedPokemon.status == null &&
                    AIUtility.canAffectWithStatus(status, it.effectedPokemon.types, it.effectedPokemon.ability)
            }
            return ScoredMove(
                move = move,
                useless = !lands && level == CorrectionLevel.FULL,
                purposeful = purposeful,
                recovery = recovery,
                priority = template.priority,
                protect = move.id in AIUtility.protectMoves
            )
        }

        if (template.damageCategory == DamageCategories.STATUS) {
            return ScoredMove(
                move = move,
                purposeful = purposeful,
                recovery = recovery,
                priority = template.priority,
                protect = move.id in AIUtility.protectMoves
            )
        }

        val type = template.getEffectiveElementalType(selfBattle.effectedPokemon)
        val withAbilities = level == CorrectionLevel.FULL
        var bestMultiplier = 0.0
        var bestDamage = 0.0
        var kills = false
        for (opponent in opponents) {
            val multiplier = BattleTypeChart.multiplier(type, opponent.effectedPokemon, withAbilities)
            val damage = BattleDamage.estimate(template, selfBattle, opponent, multiplier)
            if (multiplier > bestMultiplier) bestMultiplier = multiplier
            if (damage > bestDamage) bestDamage = damage

            // Sturdy, a Focus Sash and an unbroken Disguise all turn a lethal hit into a free
            // turn for the opponent. Believing in the knockout is worse than not seeing it.
            val blocked = BattleGuards.survivesLethalHit(opponent) ||
                BattleGuards.guardIntact(opponent, opponent.uuid in struck)
            if (!blocked && damage >= opponent.health) kills = true
        }

        val accuracy = if (template.accuracy in 1.0..100.0) template.accuracy / 100.0 else 1.0
        return ScoredMove(
            move = move,
            damage = bestDamage * accuracy,
            multiplier = bestMultiplier,
            kills = kills,
            useless = bestMultiplier == 0.0,
            purposeful = purposeful,
            recovery = recovery,
            damaging = true,
            priority = template.priority,
            protect = move.id in AIUtility.protectMoves
        )
    }

    /**
     * What a recovery move gives back.
     *
     * Half of maximum health covers Recover, Roost, Soft-Boiled, Slack Off, Milk Drink and Shore
     * Up; Rest fills the bar. Moonlight and its weather-dependent siblings are counted at the
     * same half, which under sun is an underestimate and in rain an overestimate - the answer it
     * feeds is "is this turn worth spending", and that survives being a little wrong.
     */
    private fun healAmount(moveId: String, self: BattlePokemon): Double =
        if (moveId == REST) {
            (self.maxHealth - self.health).toDouble()
        } else {
            self.maxHealth * DEFAULT_HEAL_FRACTION
        }

    /** Builds the response for [move], picking a target the way Cobblemon's own AI does. */
    private fun use(move: InBattleMove, s: Situation, reason: String): MoveActionResponse {
        report(s, "plays ${move.id} instead - $reason")
        if (move.mustBeUsed()) return MoveActionResponse(move.id)

        val targets = move.getTargets(s.active)
        if (targets.isNullOrEmpty()) return MoveActionResponse(move.id)
        val target = targets.filterNot { it.isAllied(s.active) }.randomOrNull() ?: targets.random()
        return MoveActionResponse(move.id, target.getPNX())
    }

    /** Log line plus, for whoever asked, a chat line. See [TrainerAiDebug]. */
    private fun report(s: Situation, detail: String) {
        LOGGER.debug("Trainer AI: {}", detail)
        if (!TrainerAiDebug.idle()) {
            TrainerAiDebug.report(s.battle, "${s.self.species.name}: $detail")
        }
    }

    private fun round(value: Double): Int = value.roundToInt()

    private fun percent(fraction: Double): String = "${(fraction * 100).roundToInt()}%"

    /** What a request is, for the purpose of recognising the same one twice. */
    private data class DecisionKey(val turn: Int, val pokemon: UUID?, val forceSwitch: Boolean)

    /** The battle as this turn's correction needs it. */
    private class Situation(
        val battle: PokemonBattle,
        val active: ActiveBattlePokemon,
        val selfBattle: BattlePokemon,
        val opponents: List<BattlePokemon>,
        val moveset: ShowdownMoveset,
        val moves: List<ScoredMove>,
        /** At least one target still has an unbroken Disguise or Ice Face. */
        val guarded: Boolean
    ) {
        val self: Pokemon = selfBattle.effectedPokemon
        val opponentPokemon: List<Pokemon> = opponents.map { it.effectedPokemon }
        val hpFraction: Double = selfBattle.health.toDouble() / selfBattle.maxHealth.coerceAtLeast(1)

        /** The best move that is not dead weight, by expected damage. */
        val best: ScoredMove? = moves.filterNot { it.useless }.maxByOrNull { it.damage }

        /**
         * The best move that actually attacks. What replaces a refused heal: falling back on
         * [best] could hand the heal straight back when the Pokémon carries nothing else.
         */
        val bestAttack: ScoredMove? = moves.filter { it.damaging && !it.useless }.maxByOrNull { it.damage }

        /** The least valuable thing that still hits, for breaking a guard. */
        val cheapestAttack: ScoredMove? =
            moves.filter { it.damaging && !it.useless }.minByOrNull { it.damage }

        /** An opponent can knock this Pokémon out this turn, Sturdy and Focus Sash accounted for. */
        val facingLethal: Boolean =
            !BattleGuards.survivesLethalHit(selfBattle) &&
                BattleDamage.worstIncoming(selfBattle, opponents) >= selfBattle.health
    }

    /** One usable move, judged. */
    private class ScoredMove(
        val move: InBattleMove,
        /** Expected damage in hit points, accuracy folded in. Ranking only. */
        val damage: Double = 0.0,
        /** Best type multiplier against any target. */
        val multiplier: Double = 0.0,
        /** The move takes a target out this turn if it hits. */
        val kills: Boolean = false,
        /** The move cannot achieve anything at all against any target. */
        val useless: Boolean = false,
        /**
         * Cobblemon has a stated reason to play this move - a status, a boost, a hazard, a
         * recovery, a weather, a protect. Such a move is never traded for raw damage, which is
         * what keeps the correction from flattening Cobblemon's play into "always hit hardest".
         */
        val purposeful: Boolean = false,
        /** A self-recovery move, judged by [correctHeal] rather than by damage. */
        val recovery: Boolean = false,
        /** An attacking move, so that a support Pokémon is not mistaken for a walled one. */
        val damaging: Boolean = false,
        /** Move priority, which decides whether a knockout actually lands first. */
        val priority: Int = 0,
        /** Protect and its siblings, which do not survive being played twice running. */
        val protect: Boolean = false
    )

    companion object {

        /**
         * How much better an incoming Pokémon has to be for a type-driven switch to go through,
         * on the scale of [matchup]. Half a step: enough to let a defensive pivot through - one
         * that resists without hitting harder - and still refuse an even trade, which is what
         * the loop was made of.
         */
        private const val MATCHUP_GAIN_REQUIRED = 0.5

        /** Cobblemon's own `hpSwitchOutThreshold`: below this, retreating needs no other reason. */
        private const val HP_RETREAT = 0.3

        /** Cobblemon's own `accuracySwitchThreshold`, read here as any stat that far down. */
        private const val STAT_FLOOR = -3

        /** Everything resisted at this or below counts as being walled. */
        private const val WALLED_MULTIPLIER = 0.5

        /** A heal has to put back at least this share of what it offers, or it overflows. */
        private const val MIN_HEAL_USED = 0.6

        /** What a recovery move restores, as a share of maximum health. */
        private const val DEFAULT_HEAL_FRACTION = 0.5

        private const val REST = "rest"

        /** Difficulty from which a lead opens with its entry hazard. */
        private const val LEAD_HAZARD_DIFFICULTY = 4

        /**
         * Entry hazards, best first. Stealth Rock hits everything that comes in and ignores
         * Flying and Levitate, so it is worth more than a layer of Spikes that half a team walks
         * over. Anything Cobblemon lists and this does not is appended, so a hazard added by a
         * later version is still laid - just last.
         */
        private val hazardPriority: List<String> by lazy {
            val preferred = listOf("stealthrock", "spikes", "toxicspikes", "stickyweb")
            preferred + AIUtility.entryHazards.filterNot { it in preferred }
        }

        /**
         * Every move Cobblemon's AI has a listed intent for. Read from its own tables so that a
         * Cobblemon update carries here on its own.
         */
        private val purposefulMoves: Set<String> by lazy {
            buildSet {
                addAll(AIUtility.statusMoves.keys)
                addAll(AIUtility.boostFromMoves.keys)
                addAll(AIUtility.weatherSetupMoves.keys)
                addAll(AIUtility.entryHazards)
                addAll(AIUtility.antiHazardsMoves)
                addAll(AIUtility.antiBoostMoves)
                addAll(AIUtility.pivotMoves)
                addAll(AIUtility.setupMoves)
                addAll(AIUtility.selfRecoveryMoves)
                addAll(AIUtility.protectMoves)
                addAll(AIUtility.accuracyLoweringMoves)
            }
        }
    }
}

/**
 * How much of the correction layer a trainer gets, from its `battle.difficulty`.
 *
 * The scale keeps its meaning: a low-difficulty trainer is *supposed* to misplay, and taking
 * that away would leave every trainer on a route playing like a champion.
 */
enum class CorrectionLevel {

    /** Cobblemon untouched, misplays included. */
    NONE,

    /** Refuses what cannot work: type immunities, and switches that gain nothing. */
    PARTIAL,

    /** Also refuses ability immunities, hopeless status moves, aimless attacks and bad heals. */
    FULL;

    companion object {
        fun of(difficulty: Int): CorrectionLevel = when {
            difficulty <= 2 -> NONE
            difficulty <= 4 -> PARTIAL
            else -> FULL
        }
    }
}
