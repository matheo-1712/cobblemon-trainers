package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.battles.InBattleGimmickMove
import com.cobblemon.mod.common.battles.ShowdownMoveset

/**
 * What a trainer's `battle.gimmicks` means, and whether the battle is offering one right now.
 *
 * Cobblemon already carries the whole mechanism: a move answer holds a `gimmickID` alongside its
 * move and its target, and the moveset the AI is handed says which gimmicks the simulator is
 * offering this turn - `canMegaEvo`, `canZMove`, `canDynamax`, `canTerastallize`. Answering
 * `mega` next to a move is all a trainer has to do; Showdown resolves the rest, and the mod
 * providing the Mega Stones turns it into a form on screen by listening to Cobblemon's own event.
 *
 * **Nothing here names that mod, and nothing links against it.** A trainer mega evolves because
 * one of its Pokémon holds a stone the simulator recognises, which only happens when the mod is
 * installed; without it the item never resolves, `canMegaEvo` is never true, and every trainer
 * battles as before. That is the whole of the soft dependency, and Z-Power and Dynamax stand on
 * exactly the same footing: Cobblemon has the instruction, the event and the battle buttons for
 * both, but nothing in a plain install ever offers them.
 *
 * Terastallization asks for none of that: Cobblemon ships it whole - the Tera types, the
 * instruction, the event, the Tera Orb - so a trainer declaring `terastal` works on a plain
 * install. That is the one difference of nature between them; the difference of *use* is a
 * moment, and three of the four have to wait for theirs - see [BattleTera], [BattleZMove] and
 * [BattleDynamax].
 *
 * The words a pack writes are Cobblemon's own gimmick ids, so a pack author reading Cobblemon's
 * vocabulary is reading ours - and the ones the mod does not act on yet are told apart from
 * plain typos, see [isKnownToCobblemon]. Dynamax is the one to watch: its id is `max`, not
 * `dynamax`.
 */
object TrainerGimmicks {

    /** Cobblemon's id for Mega Evolution, and the word a pack writes: `mega`. */
    val MEGA: String = ShowdownMoveset.Gimmick.MEGA_EVOLUTION.id

    /** Cobblemon's id for Z-Power, and the word a pack writes: `zmove`. */
    val Z_POWER: String = ShowdownMoveset.Gimmick.Z_POWER.id

    /** Cobblemon's id for Dynamax, and the word a pack writes: `max` - not `dynamax`. */
    val DYNAMAX: String = ShowdownMoveset.Gimmick.DYNAMAX.id

    /** Cobblemon's id for Terastallization, and the word a pack writes: `terastal`. */
    val TERASTAL: String = ShowdownMoveset.Gimmick.TERASTALLIZATION.id

    /**
     * The gimmicks a trainer actually uses today, in the order they are spent.
     *
     * The order matters on a turn that offers more than one: a single answer carries a single
     * `gimmickID`, so the others have to wait. Mega Evolution goes first because it is the only
     * one that is not *spent* to answer anything - it costs nothing and is never a mistake, and
     * it is tied to the Pokémon holding the stone, which may not get another turn.
     *
     * The three that are judged come after it, cheapest lasting cost first. When two of them
     * would answer the same turn either one does, so the one to spend is the one that leaves the
     * least behind: a Z-Move is a single hit and then nothing, a Dynamax runs for three turns,
     * and a Terastallization is what the Pokémon *is* for the rest of the battle.
     */
    val SUPPORTED: List<String> = listOf(MEGA, Z_POWER, DYNAMAX, TERASTAL)

    /** Whether [name] is one this mod acts on. */
    fun isSupported(name: String): Boolean = SUPPORTED.any { it.equals(name.trim(), ignoreCase = true) }

    /**
     * Whether [name] is a gimmick Cobblemon knows, supported here or not. It separates a pack
     * that asked for Dynamax - a thing, just not one this mod does yet - from a pack that
     * mistyped, and the two deserve different words at load time.
     */
    fun isKnownToCobblemon(name: String): Boolean =
        ShowdownMoveset.Gimmick.entries.any { it.id.equals(name.trim(), ignoreCase = true) }

    /** Whether a trainer declaring [declared] uses [gimmick]. */
    fun uses(declared: List<String>, gimmick: String): Boolean =
        declared.any { it.trim().equals(gimmick, ignoreCase = true) }

    /**
     * Whether the battle is offering [gimmick] to the Pokémon this [moveset] belongs to.
     *
     * This is Showdown's answer, not ours: it is true while the active Pokémon holds the right
     * item and the side has not spent its one use yet. Answering with a gimmick it did not offer
     * is an error the simulator throws back, so this is the only gate that matters.
     */
    fun offered(moveset: ShowdownMoveset, gimmick: String): Boolean = when (gimmick) {
        MEGA -> moveset.canMegaEvo
        Z_POWER -> !moveset.canZMove.isNullOrEmpty()
        DYNAMAX -> moveset.canDynamax
        TERASTAL -> teraType(moveset) != null
        else -> false
    }

    /**
     * What [moveId] turns into under [gimmick], or null when this slot has nothing to turn into.
     *
     * Z-Power and Dynamax are the two whose availability is per *slot* as well as per side:
     * `canZMove` and `maxMoves` run alongside `moveset.moves`, an entry each, and a Z-Crystal
     * only answers for the moves of its own type. That makes this a real gate for Z-Power - the
     * chosen move may simply have no Z-Move - where for Dynamax, which lifts all four slots, it
     * only supplies the name.
     *
     * The lookup is by position in `moveset.moves`, which is the same index
     * `MoveActionResponse.toShowdownString` writes into the answer, and the same one
     * `ShowdownMoveset.setGimmickMapping` maps on. Reading each list directly rather than through
     * `InBattleMove.gimmickMove` is what keeps the two apart: that mapping prefers `canZMove`
     * whenever both are present, so a Pokémon holding a crystal and able to Dynamax would
     * otherwise report its Z-Move as its Max Move.
     */
    fun gimmickMove(moveset: ShowdownMoveset, gimmick: String, moveId: String?): InBattleGimmickMove? {
        if (moveId == null) return null

        val slot = moveset.moves.indexOfFirst { it.id == moveId }
        if (slot < 0) return null

        val lifted = when (gimmick) {
            Z_POWER -> moveset.canZMove
            DYNAMAX -> moveset.maxMoves
            else -> null
        } ?: return null

        return lifted.getOrNull(slot)?.takeIf { !it.disabled }
    }

    /**
     * The Tera type the battle is offering, or null when it is offering none.
     *
     * Terastallization is the one gimmick whose availability is not a boolean:
     * `canTerastallize` carries the *type*, which is exactly what deciding whether to spend it
     * needs. See [BattleTera].
     */
    fun teraType(moveset: ShowdownMoveset): String? =
        moveset.canTerastallize?.trim()?.takeIf { it.isNotEmpty() }
}
