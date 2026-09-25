package matheo1712.cobbletrainers.trainers

/** Display order for all trainers contributed under one datapack namespace. */
data class TrainerPack(
    val order: Int = UNORDERED
) {
    companion object {
        const val UNORDERED = Int.MAX_VALUE
    }
}
