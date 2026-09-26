package matheo1712.cobbletrainers.trainers

/**
 * What a trainer wears that is neither armour nor held: **one field per place on the body**,
 * each a full item ID.
 *
 * Named after the place rather than after the item, because the two do not line up. Mega
 * Showdown's Mega Bracelet goes on a wrist, but so does Korrina's Glove; Maxie's Glasses go on
 * the face, Zinnia's Anklet on an ankle, Diantha's Charm on the chest - and all five are the
 * same "mega" key item as far as that mod's own slots are concerned. Nothing in the game's data
 * distinguishes them: the spot is chosen in that mod's code, item by item. So the pack says
 * where, and any item may go anywhere.
 *
 * No two places overlap, so a trainer may wear the lot.
 *
 * @param face On the head: glasses, a tiara, a visor.
 * @param chest Hung on the chest: a pendant, a charm, an anchor.
 * @param wrist The wrist of the free arm: a bracelet, a glove.
 * @param forearm The same arm, a little higher: a ring.
 * @param hand Across the back of the main hand: a band.
 * @param belt Hooked on the belt: an orb, a pouch.
 * @param ankle The right ankle: an anklet.
 */
data class TrainerTrinkets(
    val face: String? = null,
    val chest: String? = null,
    val wrist: String? = null,
    val forearm: String? = null,
    val hand: String? = null,
    val belt: String? = null,
    val ankle: String? = null
) {

    val isEmpty: Boolean
        get() = listOf(face, chest, wrist, forearm, hand, belt, ankle).all { it.isNullOrBlank() }
}
