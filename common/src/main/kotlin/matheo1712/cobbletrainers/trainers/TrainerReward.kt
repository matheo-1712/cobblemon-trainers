package matheo1712.cobbletrainers.trainers

/**
 * One thing handed to the player when they beat the trainer: an item, or a command run for them.
 *
 * An entry is one or the other. Writing both is refused rather than guessed at: running the
 * command *and* giving the item, or using the item as the command's icon in the battle phone,
 * would each be a reasonable reading, and a pack that meant the other one would never know.
 *
 * @param item Item ID with optional components, using the item argument syntax of `/give`.
 * @param command A command run on victory, as if typed by the winner (`@s` and `~` are them)
 *   at the permission level of datapack functions, with its output kept out of their chat.
 *   `{player}` is replaced by their name, for commands that take a name rather than a selector.
 *   Never shown in the battle phone: there is nothing to draw for it.
 * @param count How many of it. Clamped to a sane range by [TrainerRewards]. Items only.
 * @param hidden Whether the battle phone keeps quiet about it. False by default - a reward is
 *   the reason to challenge a trainer, so it is worth advertising. Set it to true for a surprise
 *   the player only discovers on winning; it changes nothing about what is handed over, only
 *   about what is said beforehand. Marking every reward of a trainer hidden is how a whole
 *   trainer keeps its rewards secret.
 * @param firstWinOnly Whether it drops on the first victory alone. False by default, so a
 *   reward is farmable for as long as the trainer accepts rematches. It belongs to the entry
 *   rather than to the trainer so that one fight can hand over a trophy once and a handful of
 *   berries every time.
 */
data class TrainerReward(
    val item: String = "",
    val command: String = "",
    val count: Int = 1,
    val hidden: Boolean = false,
    val firstWinOnly: Boolean = false
)
