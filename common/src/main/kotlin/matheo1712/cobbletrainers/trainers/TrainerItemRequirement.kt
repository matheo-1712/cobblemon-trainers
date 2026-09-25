package matheo1712.cobbletrainers.trainers

/**
 * An item the player must be carrying, anywhere in their inventory. Never taken from them.
 *
 * @param item Full item ID, namespace included.
 * @param count How many, across every stack.
 */
data class TrainerItemRequirement(
    val item: String = "",
    val count: Int = 1
)
