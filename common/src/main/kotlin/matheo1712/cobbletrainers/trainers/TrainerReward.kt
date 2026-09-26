package matheo1712.cobbletrainers.trainers

/**
 * One item handed to the player when they beat the trainer.
 *
 * @param item Full item ID, namespace included: `cobblemon:rare_candy`, `minecraft:diamond`.
 * @param count How many of it. Clamped to a sane range by [TrainerRewards].
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
    val count: Int = 1,
    val hidden: Boolean = false,
    val firstWinOnly: Boolean = false
)
