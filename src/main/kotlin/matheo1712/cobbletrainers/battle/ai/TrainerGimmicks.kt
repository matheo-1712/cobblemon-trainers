package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.battles.ShowdownMoveset

/**
 * What a trainer's `battle.gimmicks` means, and whether the battle is offering one right now.
 *
 * Cobblemon 1.7.3 already carries the whole mechanism: a move answer holds a `gimmickID`
 * alongside its move and its target, and the moveset the AI is handed says which gimmicks the
 * simulator is offering this turn - `canMegaEvo` for Mega Evolution. Answering `mega` next to a
 * move is all a trainer has to do; Showdown resolves the rest, and the mod providing the Mega
 * Stones turns it into a form on screen by listening to Cobblemon's own event.
 *
 * **Nothing here names that mod, and nothing links against it.** A trainer mega evolves because
 * one of its Pokémon holds a stone the simulator recognises, which only happens when the mod is
 * installed; without it the item never resolves, `canMegaEvo` is never true, and every trainer
 * battles as before. That is the whole of the soft dependency.
 *
 * The words a pack writes are Cobblemon's own gimmick ids, so a pack author reading Cobblemon's
 * vocabulary is reading ours - and the ones the mod does not act on yet are told apart from
 * plain typos, see [isKnownToCobblemon].
 */
object TrainerGimmicks {

    /** Cobblemon's id for Mega Evolution, and the word a pack writes: `mega`. */
    val MEGA: String = ShowdownMoveset.Gimmick.MEGA_EVOLUTION.id

    /** The gimmicks a trainer actually uses today. */
    val SUPPORTED: List<String> = listOf(MEGA)

    /** Whether [name] is one this mod acts on. */
    fun isSupported(name: String): Boolean = SUPPORTED.any { it.equals(name.trim(), ignoreCase = true) }

    /**
     * Whether [name] is a gimmick Cobblemon knows, supported here or not. It separates a pack
     * that asked for Terastallization - a thing, just not one this mod does yet - from a pack
     * that mistyped, and the two deserve different words at load time.
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
        else -> false
    }
}
