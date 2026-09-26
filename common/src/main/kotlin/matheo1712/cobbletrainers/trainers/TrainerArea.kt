package matheo1712.cobbletrainers.trainers

/**
 * A box of block coordinates a trainer answers within, on the horizontal plane only.
 *
 * Both corners count, and neither has to be the smaller one: they are sorted when the box is
 * tested, so a pack may write them in whichever order it read them off the world.
 *
 * @param from One corner, as `[x, z]`.
 * @param to The other, as `[x, z]`.
 */
data class TrainerArea(
    val from: List<Int> = emptyList(),
    val to: List<Int> = emptyList()
) {

    val isValid: Boolean
        get() = from.size == 2 && to.size == 2

    fun contains(x: Int, z: Int): Boolean {
        if (!isValid) return false
        val withinX = x >= minOf(from[0], to[0]) && x <= maxOf(from[0], to[0])
        val withinZ = z >= minOf(from[1], to[1]) && z <= maxOf(from[1], to[1])
        return withinX && withinZ
    }
}
