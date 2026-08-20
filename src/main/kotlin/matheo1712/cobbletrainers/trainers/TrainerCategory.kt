package matheo1712.cobbletrainers.trainers

/**
 * Presentation of one trainer category, loaded from
 * `data/<namespace>/cobblemontrainers/categories/<name>.json`.
 *
 * A category is a folder: a trainer at `cobblemontrainers/champions/erika.json` belongs to the
 * category `<namespace>:champions`, and nothing has to be declared for that to be true. This
 * file is what a pack adds when the folder name is not what it wants shown, or when the
 * alphabet is not the order it wants - both optional, both independent.
 *
 * @param name Display name. Sent to the client and turned into a translatable component there,
 *   like a trainer name: a translation key is localised, plain text is shown as is. Null falls
 *   back to the folder name.
 * @param order Where the category sits among the others of its pack, lowest first. Categories
 *   without one come after those that have one, in alphabetical order.
 */
data class TrainerCategory(
    val name: String? = null,
    val order: Int = UNORDERED
) {

    companion object {
        /**
         * The order of a category that does not state one. Sorting on it puts those categories
         * last without a special case, and their tie is broken by name.
         */
        const val UNORDERED = Int.MAX_VALUE
    }
}
