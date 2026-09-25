package matheo1712.cobbletrainers.trainers

/**
 * What the trainer says, shown to the player in Cobblemon's dialogue box - see
 * [matheo1712.cobbletrainers.dialogue.TrainerDialogue].
 *
 * @param greeting Said on right-click, above the Battle / Cancel row. The mod provides a
 *   default, so this is only for giving the trainer their own words.
 * @param start Said after the player accepts, in a box of its own, just before the battle
 *   opens. Left out, accepting goes straight to the battle.
 * @param decline Said to a player who presses Cancel. Left out, Cancel simply closes the box.
 *   It answers the button and not the escape key: escaping is asking to be out of the
 *   conversation.
 * @param win Said once the player has won.
 * @param lose Said once the player has lost.
 */
data class TrainerMessages(
    val greeting: String? = null,
    val start: String? = null,
    val decline: String? = null,
    val win: String? = null,
    val lose: String? = null
)
