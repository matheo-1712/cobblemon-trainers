package matheo1712.cobbletrainers.trainers

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.battle.TrainerBattleMusic
import net.minecraft.resources.ResourceLocation

/**
 * Properties of a trainer, loaded from a JSON file.
 *
 * The file is read as it is written: what the trainer *is* at the top level, and everything
 * else in the block it belongs to - [battle] for the fight, [messages] for what is said,
 * [progress] for what beating them changes, [requires] for what it takes to challenge them.
 *
 * Every field has a default, so a trainer file holds only what it wants to change.
 *
 * Text fields ([name], the [messages]) are sent to players as translatable components: put a
 * translation key there and add it to your pack's language files, or put plain text and it is
 * displayed as is.
 *
 * Datapacks never declare an NPC class: every trainer uses one of the two shipped by the mod.
 *
 * @param name Display name shown above the trainer.
 * @param skin Skin configuration.
 * @param team Showdown-formatted team, one Pokémon per entry.
 * @param battle How the fight goes.
 * @param messages What the trainer says.
 * @param progress What a victory over this trainer is worth, and for how long.
 * @param rewards Items handed to the player on victory, in order.
 * @param requires What a player must have done before this trainer accepts a battle. Null -
 *   the default - is a trainer anyone may challenge.
 */
data class TrainerDefinition(
    val name: String = "Trainer",
    val skin: TrainerSkin = TrainerSkin(),
    val team: List<String> = emptyList(),
    val battle: TrainerBattleSettings = TrainerBattleSettings(),
    val messages: TrainerMessages = TrainerMessages(),
    val progress: TrainerProgressRules = TrainerProgressRules(),
    val rewards: List<TrainerReward> = emptyList(),
    val requires: TrainerRequirements? = null
) {

    /** The requirements to challenge this trainer, or null when it declares none that matter. */
    fun requirements(): TrainerRequirements? = requires?.takeIf { !it.isEmpty }

    /**
     * Logs the values that are words rather than numbers and were not recognised. Gson has no
     * opinion on them, so an unnoticed typo would silently pick the default - once at load is
     * the moment to say so. The skin is left out: it is checked when it is resolved, which is
     * where the pack path is known.
     */
    fun validate(id: ResourceLocation) {
        progress.validate(id)
    }
}

/**
 * How a battle against this trainer goes.
 *
 * @param level Level of the Pokémon that do not state one themselves.
 * @param format How many Pokémon fight at once: `singles`, `doubles` or `triples`.
 * @param difficulty Battle AI difficulty, from 0 to 5. This is *not* the level, see [level].
 * @param healParty Whether the trainer team is healed to full when a battle starts and again
 *   once it ends. With false, damage carries over between fights.
 * @param music Sound ID played to the players for the duration of the battle. Defaults to the
 *   track shipped by the mod; set it to `null` or `""` for a silent trainer, or to your own
 *   sound ID - provided by a resource pack - for another track.
 */
data class TrainerBattleSettings(
    val level: Int = 1,
    val format: String = "singles",
    val difficulty: Int = 5,
    val healParty: Boolean = true,
    val music: String? = TrainerBattleMusic.DEFAULT_TRACK
)

/**
 * What the trainer says, broadcast to everyone in the battle.
 *
 * @param start Sent when the battle starts.
 * @param win Sent when the player wins.
 * @param lose Sent when the player loses.
 */
data class TrainerMessages(
    val start: String? = null,
    val win: String? = null,
    val lose: String? = null
)

/**
 * What beating this trainer is worth, and for how long.
 *
 * Victories are remembered per trainer ID, so every NPC spawned from one definition shares the
 * same record - see [TrainerProgress].
 *
 * @param rematch `unlimited` (the default) or `never`. With `never` the trainer is a one-shot
 *   encounter: it turns down every later challenge from the same player.
 * @param rewards `every_win` (the default) or `first_win`. Only meaningful with an `unlimited`
 *   [rematch], which is otherwise what limits the wins.
 * @param listed Whether the trainer belongs in a progress listing - the battle phone and
 *   `/listtrainers`. Set it to false for a trainer nobody is meant to hunt down: a demo
 *   shipped with a mod, a trainer that never spawns. This is about what is shown, not what is
 *   stored: victories are recorded either way, so [rematch] and [rewards] keep working on an
 *   unlisted trainer.
 */
data class TrainerProgressRules(
    val rematch: String = REMATCH_UNLIMITED,
    val rewards: String = REWARDS_EVERY_WIN,
    val listed: Boolean = true
) {

    /** False only for the exact word `never`; an unknown value keeps the permissive default. */
    val allowsRematch: Boolean
        get() = !rematch.equals(REMATCH_NEVER, ignoreCase = true)

    /** False only for the exact word `first_win`. */
    val rewardsEveryWin: Boolean
        get() = !rewards.equals(REWARDS_FIRST_WIN, ignoreCase = true)

    fun validate(id: ResourceLocation) {
        warnUnknown(id, "progress.rematch", rematch, REMATCH_UNLIMITED, REMATCH_NEVER)
        warnUnknown(id, "progress.rewards", rewards, REWARDS_EVERY_WIN, REWARDS_FIRST_WIN)
    }

    companion object {
        const val REMATCH_UNLIMITED = "unlimited"
        const val REMATCH_NEVER = "never"
        const val REWARDS_EVERY_WIN = "every_win"
        const val REWARDS_FIRST_WIN = "first_win"

        private fun warnUnknown(
            id: ResourceLocation,
            field: String,
            value: String,
            vararg accepted: String
        ) {
            if (accepted.any { it.equals(value, ignoreCase = true) }) return
            CobblemonTrainers.LOGGER.warn(
                "Trainer {}: unknown {} '{}', falling back to '{}'. Expected one of: {}",
                id, field, value, accepted.first(), accepted.joinToString(", ")
            )
        }
    }
}

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
 * What a player must have done before this trainer accepts a battle.
 *
 * Every requirement declared here has to be met - they add up, they are never alternatives.
 * A trainer whose requirements are not met turns the player down with [message], and is left
 * out of the battle phone entirely while [hidden] is true.
 *
 * Nothing here is ever consumed: an item requirement is a key the player keeps, which is what
 * makes a rematch possible without farming it again.
 *
 * @param defeated Trainer IDs the player must have beaten. A bare name (`champion`) is read in
 *   the namespace of the trainer that requires it.
 * @param victories A number of trainers beaten, rather than named ones.
 * @param items Items the player must be carrying.
 * @param advancement An advancement the player must have completed. Any ID works, vanilla or
 *   from a pack, which is how a requirement the mod knows nothing about gets expressed.
 * @param hidden Whether the trainer is left out of the battle phone while locked. True by
 *   default: a locked trainer is a secret until it is not. Set it to false for a league whose
 *   road you want players to see ahead of time.
 * @param message What the trainer says when it turns a player down. The mod provides a default
 *   that lists what is missing, so this is only for giving them their own words.
 */
data class TrainerRequirements(
    val defeated: List<String> = emptyList(),
    val victories: TrainerVictoriesRequirement? = null,
    val items: List<TrainerItemRequirement> = emptyList(),
    val advancement: String? = null,
    val hidden: Boolean = true,
    val message: String? = null
) {

    /** A block holding only presentation fields locks nothing. */
    val isEmpty: Boolean
        get() = defeated.isEmpty() && victories == null && items.isEmpty() && advancement.isNullOrBlank()
}

/**
 * A number of trainers the player must have beaten.
 *
 * The trainer that requires it never counts towards itself, so "beat every champion" may be
 * asked by a champion. Only [TrainerProgressRules.listed] trainers count.
 *
 * @param count How many. Left out - or zero - it means every trainer of the pool below, which
 *   is how "beat all of them" is written.
 * @param pack Restricts the pool to one datapack namespace. Null counts every trainer.
 * @param category Restricts the pool to one category. A bare name is read in the namespace of
 *   the trainer that requires it - see [matheo1712.cobbletrainers.trainers.TrainerCategory].
 */
data class TrainerVictoriesRequirement(
    val count: Int = 0,
    val pack: String? = null,
    val category: String? = null
)

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
