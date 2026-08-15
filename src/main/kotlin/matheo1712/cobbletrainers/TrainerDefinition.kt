package matheo1712.cobbletrainers

/**
 * Properties of a trainer, loaded from a JSON file.
 *
 * [name], [battleStartMessage], [battleEndWinMessage] and [battleEndLoseMessage] are sent to
 * players as translatable components: put a translation key there and add it to your
 * datapack's language files to localise it, or put plain text and it is displayed as is.
 *
 * Datapacks never declare an NPC class: every trainer uses one of the two shipped by the mod.
 * The settings that used to live there — battle format, AI difficulty, party healing — are
 * fields of the trainer instead.
 *
 * @param name Display name shown above the trainer.
 * @param skin Skin configuration (Minecraft username or UUID).
 * @param team Showdown-formatted team. An empty entry (`""`) separates two Pokémon.
 * @param level Trainer level, also the default level of Pokémon that do not state one.
 * @param battleFormat How many Pokémon fight at once: `singles`, `doubles` or `triples`.
 * @param skill Battle AI difficulty, from 0 to 5. This is *not* the trainer level, see [level].
 * @param autoHealParty Whether the trainer team is healed to full when a battle starts and
 *   again once it ends. With false, damage carries over between fights.
 * @param canBattle Whether the trainer can battle. When false the interaction is disabled.
 * @param battleStartMessage Sent to the player when the battle starts (optional).
 * @param battleEndWinMessage Sent when the player wins (optional).
 * @param battleEndLoseMessage Sent when the player loses (optional).
 * @param battleMusic Sound ID played to the players for the duration of the battle. Defaults
 *   to the track shipped by the mod; set it to `null` or `""` for a silent trainer, or to your
 *   own sound ID — provided by a resource pack — for another track.
 */
data class TrainerDefinition(
    val name: String = "Trainer",
    val skin: TrainerSkin = TrainerSkin(),
    val team: List<String> = emptyList(),
    val level: Int = 1,
    val battleFormat: String = "singles",
    val skill: Int = 5,
    val autoHealParty: Boolean = true,
    val canBattle: Boolean = true,
    val battleStartMessage: String? = null,
    val battleEndWinMessage: String? = null,
    val battleEndLoseMessage: String? = null,
    val battleMusic: String? = TrainerBattleMusic.DEFAULT_TRACK
)

/**
 * Skin configuration of a trainer.
 *
 * Supported types:
 * - `"player_username"`: uses the skin of the Minecraft player with that username.
 * - `"player_uuid"`: uses the skin of the Minecraft player with that UUID.
 *
 * @param type The skin type.
 * @param value The skin value (username or UUID).
 */
data class TrainerSkin(
    val type: String = "player_username",
    val value: String = "Steve"
)
