package matheo1712.cobbletrainers.trainers

import matheo1712.cobbletrainers.battle.TrainerBattleMusic

/**
 * Properties of a trainer, loaded from a JSON file.
 *
 * [name], [battleStartMessage], [battleEndWinMessage] and [battleEndLoseMessage] are sent to
 * players as translatable components: put a translation key there and add it to your
 * datapack's language files to localise it, or put plain text and it is displayed as is.
 *
 * Datapacks never declare an NPC class: every trainer uses one of the two shipped by the mod.
 * The settings that used to live there - battle format, AI difficulty, party healing - are
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
 * @param canRebattle Whether a player who has already beaten this trainer may fight it again.
 *   With false, the trainer is a one-shot encounter: it turns down every later challenge from
 *   the same player. Victories are remembered per trainer ID, so every NPC spawned from this
 *   definition shares the same record - see [TrainerProgress].
 * @param tracked Whether the trainer belongs in a progress listing - `/listtrainers` and
 *   anything built on it. Set it to false for a trainer nobody is meant to hunt down: a demo
 *   shipped with a mod, a non-fighting NPC, a trainer that never spawns. This is about what is
 *   shown, not what is stored: victories are recorded either way, so [canRebattle] and
 *   [rewardOnce] keep working on an untracked trainer.
 * @param rewards Items handed to the player on victory, in order.
 * @param rewardOnce Whether [rewards] are limited to the player's first win. Only meaningful
 *   with [canRebattle] on, which is otherwise what limits the wins.
 * @param battleStartMessage Sent to the player when the battle starts (optional).
 * @param battleEndWinMessage Sent when the player wins (optional).
 * @param battleEndLoseMessage Sent when the player loses (optional).
 * @param battleMusic Sound ID played to the players for the duration of the battle. Defaults
 *   to the track shipped by the mod; set it to `null` or `""` for a silent trainer, or to your
 *   own sound ID - provided by a resource pack - for another track.
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
    val canRebattle: Boolean = true,
    val tracked: Boolean = true,
    val rewards: List<TrainerReward> = emptyList(),
    val rewardOnce: Boolean = false,
    val battleStartMessage: String? = null,
    val battleEndWinMessage: String? = null,
    val battleEndLoseMessage: String? = null,
    val battleMusic: String? = TrainerBattleMusic.DEFAULT_TRACK
)

/**
 * One item handed to the player when they beat the trainer.
 *
 * @param item Full item ID, namespace included: `cobblemon:rare_candy`, `minecraft:diamond`.
 * @param count How many of it. Clamped to a sane range by [TrainerRewards].
 */
data class TrainerReward(
    val item: String = "",
    val count: Int = 1
)

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
