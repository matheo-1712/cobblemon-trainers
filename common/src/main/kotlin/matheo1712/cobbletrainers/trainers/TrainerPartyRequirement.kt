package matheo1712.cobbletrainers.trainers

/**
 * A Pokémon the player must have in their party. Never taken from them, never asked to be in
 * any particular shape: a fainted party member still counts, because the question is who
 * travels with the player, not who could fight right now.
 *
 * The party alone is read, never the PC. "Have a Staraptor with you" is a thing a player can
 * see at a glance and act on; a box search would be a requirement nobody could check.
 *
 * @param pokemon A Cobblemon property string, written exactly as `/pokespawn` takes it:
 *   `staraptor`, or `staraptor shiny=true`, or `rotom appliance=wash` for a form. **Only what
 *   is written is checked** - a bare species accepts any level, gender and form.
 * @param count How many party members have to match it.
 */
data class TrainerPartyRequirement(
    val pokemon: String = "",
    val count: Int = 1
)
