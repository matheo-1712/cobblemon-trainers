package matheo1712.cobbletrainers

/**
 * Properties of a trainer, loaded from a JSON file.
 *
 * [name], [battleStartMessage], [battleEndWinMessage] and [battleEndLoseMessage] are sent to
 * players as translatable components: put a translation key there and add it to your
 * datapack's language files to localise it, or put plain text and it is displayed as is.
 *
 * @param name Display name shown above the trainer.
 * @param skin Skin configuration (Minecraft username or UUID).
 * @param team Showdown-formatted team. An empty entry (`""`) separates two Pokémon.
 * @param level Trainer level, also the default level of Pokémon that do not state one.
 * @param canBattle Whether the trainer can battle. When false the interaction is disabled.
 * @param battleStartMessage Sent to the player when the battle starts (optional).
 * @param battleEndWinMessage Sent when the player wins (optional).
 * @param battleEndLoseMessage Sent when the player loses (optional).
 * @param npcClass ResourceLocation of a Cobblemon NPC class. Defaults to `cobblemon-trainers:trainer`.
 *
 * Note: healing the team after a battle is not configurable per trainer in Cobblemon 1.7.3 —
 * it depends on `autoHealParty` on the NPC class. To change it, define your own NPC class in
 * `data/<namespace>/npcs/` and point [npcClass] at it.
 */
data class TrainerDefinition(
    val name: String = "Trainer",
    val skin: TrainerSkin = TrainerSkin(),
    val team: List<String> = emptyList(),
    val level: Int = 1,
    val canBattle: Boolean = true,
    val battleStartMessage: String? = null,
    val battleEndWinMessage: String? = null,
    val battleEndLoseMessage: String? = null,
    val npcClass: String? = null
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
