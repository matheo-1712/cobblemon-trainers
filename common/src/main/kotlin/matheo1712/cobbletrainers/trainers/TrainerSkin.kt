package matheo1712.cobbletrainers.trainers

/**
 * Skin configuration of a trainer.
 *
 * Supported types:
 * - `"player_username"`: uses the skin of the Minecraft player with that username.
 * - `"player_uuid"`: uses the skin of the Minecraft player with that UUID.
 * - `"texture"`: uses a player skin image shipped in a pack, under
 *   `assets/<namespace>/<path>` - see [TrainerTextures].
 *
 * @param type The skin type.
 * @param value The skin value: a username, a UUID, or the resource location of a `.png`
 *   (`cobblemon-trainers:textures/trainers/example.png`).
 * @param model Which player rig wears the texture: `default` (Steve) or `slim` (Alex). Only
 *   read for the `texture` type - Mojang states the model of a player skin itself.
 */
data class TrainerSkin(
    val type: String = "player_username",
    val value: String = "Steve",
    val model: String = "default"
)
