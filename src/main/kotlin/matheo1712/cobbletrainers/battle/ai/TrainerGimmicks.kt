package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.battles.ShowdownMoveset

/**
 * What a trainer's `battle.gimmicks` means, and whether the battle is offering one right now.
 *
 * Cobblemon 1.7.3 already carries the whole mechanism: a move answer holds a `gimmickID`
 * alongside its move and its target, and the moveset the AI is handed says which gimmicks the
 * simulator is offering this turn - `canMegaEvo` for Mega Evolution, `canTerastallize` for
 * Terastallization. Answering `mega` next to a move is all a trainer has to do; Showdown resolves
 * the rest, and the mod providing the Mega Stones turns it into a form on screen by listening to
 * Cobblemon's own event.
 *
 * **Nothing here names that mod, and nothing links against it.** A trainer mega evolves because
 * one of its Pokémon holds a stone the simulator recognises, which only happens when the mod is
 * installed; without it the item never resolves, `canMegaEvo` is never true, and every trainer
 * battles as before. That is the whole of the soft dependency.
 *
 * Terastallization asks for none of that: Cobblemon ships it whole - the Tera types, the
 * instruction, the event, the Tera Orb - so a trainer declaring `terastal` works on a plain
 * install. What it does need is a *moment*, which is the difference between the two and the whole
 * of [BattleTera].
 *
 * The words a pack writes are Cobblemon's own gimmick ids, so a pack author reading Cobblemon's
 * vocabulary is reading ours - and the ones the mod does not act on yet are told apart from
 * plain typos, see [isKnownToCobblemon].
 */
object TrainerGimmicks {

    /** Cobblemon's id for Mega Evolution, and the word a pack writes: `mega`. */
    val MEGA: String = ShowdownMoveset.Gimmick.MEGA_EVOLUTION.id

    /** Cobblemon's id for Terastallization, and the word a pack writes: `terastal`. */
    val TERASTAL: String = ShowdownMoveset.Gimmick.TERASTALLIZATION.id

    /**
     * The gimmicks a trainer actually uses today, in the order they are offered.
     *
     * The order matters on the one turn a battle offers both: a single answer carries a single
     * `gimmickID`, so one of them has to wait. Mega Evolution goes first because it is tied to
     * the Pokémon holding the stone - miss its turn and the trainer may never get another - while
     * a Terastallization belongs to the side and keeps until it is worth spending.
     */
    val SUPPORTED: List<String> = listOf(MEGA, TERASTAL)

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
        TERASTAL -> teraType(moveset) != null
        else -> false
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
