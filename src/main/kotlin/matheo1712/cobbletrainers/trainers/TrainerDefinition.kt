package matheo1712.cobbletrainers.trainers

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.battle.TrainerBattleMusic
import matheo1712.cobbletrainers.battle.ai.TrainerGimmicks
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
 * @param location Where the trainer is to be found. Its presence is what makes the trainer
 *   callable from the battle phone; null - the default - is a trainer who only ever stands
 *   where an operator put them.
 */
data class TrainerDefinition(
    val name: String = "Trainer",
    val skin: TrainerSkin = TrainerSkin(),
    val team: List<String> = emptyList(),
    val battle: TrainerBattleSettings = TrainerBattleSettings(),
    val messages: TrainerMessages = TrainerMessages(),
    val progress: TrainerProgressRules = TrainerProgressRules(),
    val rewards: List<TrainerReward> = emptyList(),
    val requires: TrainerRequirements? = null,
    val location: TrainerLocation? = null
) {

    /** The requirements to challenge this trainer, or null when it declares none that matter. */
    fun requirements(): TrainerRequirements? = requires?.takeIf { !it.isEmpty }

    /**
     * Whether a player may summon this trainer from the battle phone.
     *
     * There is no separate switch for it: declaring a [location] *is* declaring that the
     * trainer comes when called, and a champion who waits in their own gym simply declares
     * none. Two fields saying the same thing could contradict each other, one cannot.
     *
     * A block holding only a label is not a place, so it does not make anyone callable.
     */
    fun callable(): Boolean = location?.let { !it.isEmpty } == true

    /**
     * Logs the values that are words rather than numbers and were not recognised. Gson has no
     * opinion on them, so an unnoticed typo would silently pick the default - once at load is
     * the moment to say so. The skin is left out: it is checked when it is resolved, which is
     * where the pack path is known.
     */
    fun validate(id: ResourceLocation) {
        battle.validate(id)
        progress.validate(id)
        location?.validate(id)
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
 * @param gimmicks Battle gimmicks this trainer uses when the fight offers them - `mega` for
 *   Mega Evolution. Empty by default: giving a Pokémon a Mega Stone is not on its own a
 *   declaration that the trainer knows what to do with it. Every one of them needs the mod
 *   providing the items to be installed, and does nothing without it. See `docs/GIMMICKS.md`.
 */
data class TrainerBattleSettings(
    val level: Int = 1,
    val format: String = "singles",
    val difficulty: Int = 5,
    val healParty: Boolean = true,
    val music: String? = TrainerBattleMusic.DEFAULT_TRACK,
    val gimmicks: List<String> = emptyList()
) {

    /**
     * Logs the gimmick names that mean nothing here. A pack writing `terastal` today has asked
     * for something the mod does not do yet, which is worth saying plainly: silence would read
     * as a trainer who simply never gets the chance to use it.
     */
    fun validate(id: ResourceLocation) {
        for (name in gimmicks) {
            if (TrainerGimmicks.isSupported(name)) continue

            if (TrainerGimmicks.isKnownToCobblemon(name)) {
                CobblemonTrainers.LOGGER.warn(
                    "Trainer {}: battle.gimmicks lists '{}', which this mod does not support yet. " +
                        "Only '{}' is used.",
                    id, name, TrainerGimmicks.SUPPORTED.joinToString("', '")
                )
            } else {
                CobblemonTrainers.LOGGER.warn(
                    "Trainer {}: unknown battle gimmick '{}'. Expected one of: {}",
                    id, name, TrainerGimmicks.SUPPORTED.joinToString(", ")
                )
            }
        }
    }
}

/**
 * What the trainer says, shown to the player in Cobblemon's dialogue box - see
 * [matheo1712.cobbletrainers.dialogue.TrainerDialogue].
 *
 * @param greeting Said on right-click, above the Battle / Cancel row. The mod provides a
 *   default, so this is only for giving the trainer their own words.
 * @param start Said after the player accepts, in a box of its own, just before the battle
 *   opens. Left out, accepting goes straight to the battle.
 * @param decline Said to a player who presses Cancel. Left out, Cancel simply closes the box.
 *   It answers the button and not the escape key: escaping is asking to be out of the
 *   conversation.
 * @param win Said once the player has won.
 * @param lose Said once the player has lost.
 */
data class TrainerMessages(
    val greeting: String? = null,
    val start: String? = null,
    val decline: String? = null,
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
 * @param listed Whether the trainer belongs in a progress listing - the battle phone and
 *   `/listtrainers`. Set it to false for a trainer nobody is meant to hunt down: a demo
 *   shipped with a mod, a trainer that never spawns. This is about what is shown, not what is
 *   stored: victories are recorded either way, so [rematch] and the rewards keep working on an
 *   unlisted trainer.
 */
data class TrainerProgressRules(
    val rematch: String = REMATCH_UNLIMITED,
    val listed: Boolean = true
) {

    /** False only for the exact word `never`; an unknown value keeps the permissive default. */
    val allowsRematch: Boolean
        get() = !rematch.equals(REMATCH_NEVER, ignoreCase = true)

    fun validate(id: ResourceLocation) {
        warnUnknown(id, "progress.rematch", rematch, REMATCH_UNLIMITED, REMATCH_NEVER)
    }

    companion object {
        const val REMATCH_UNLIMITED = "unlimited"
        const val REMATCH_NEVER = "never"

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
 * @param hidden Whether the battle phone keeps quiet about it. False by default - a reward is
 *   the reason to challenge a trainer, so it is worth advertising. Set it to true for a surprise
 *   the player only discovers on winning; it changes nothing about what is handed over, only
 *   about what is said beforehand. Marking every reward of a trainer hidden is how a whole
 *   trainer keeps its rewards secret.
 * @param firstWinOnly Whether it drops on the first victory alone. False by default, so a
 *   reward is farmable for as long as the trainer accepts rematches. It belongs to the entry
 *   rather than to the trainer so that one fight can hand over a trophy once and a handful of
 *   berries every time.
 */
data class TrainerReward(
    val item: String = "",
    val count: Int = 1,
    val hidden: Boolean = false,
    val firstWinOnly: Boolean = false
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
 * @param party Pokémon the player must have with them.
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
    val party: List<TrainerPartyRequirement> = emptyList(),
    val advancement: String? = null,
    val hidden: Boolean = true,
    val message: String? = null
) {

    /** A block holding only presentation fields locks nothing. */
    val isEmpty: Boolean
        get() = defeated.isEmpty() && victories == null && items.isEmpty() && party.isEmpty() &&
            advancement.isNullOrBlank()
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

/**
 * Where a trainer is to be found, and what the battle phone tells the player about it.
 *
 * **Naming a place in this block is what makes a trainer callable.** There is no second switch,
 * so two fields saying the same thing can never contradict each other here. A champion who
 * waits in their own gym names no place and gets no call button - though they may still write
 * a [label], which the battle phone shows without offering to fetch them.
 *
 * Every field is a condition of its own and they add up, exactly like [TrainerRequirements].
 * They are checked once, at the moment the player presses the button - a player who walks out
 * of the desert while the trainer is arriving is not punished for it.
 *
 * @param dimension Dimension the player has to stand in, `minecraft:the_nether`.
 * @param biome Biome ID, or a biome tag when it starts with `#`: `#minecraft:is_desert`.
 * @param structure Structure ID, or a structure tag when it starts with `#`. The player has to
 *   stand on a generated piece of it, not merely inside its bounding box.
 * @param area A box of block coordinates on the horizontal plane, `{"from": [x, z],
 *   "to": [x, z]}`, both corners included. Altitude is [minY] and [maxY], so that one idea is
 *   written in one place.
 * @param minY Lowest altitude that counts, inclusive.
 * @param maxY Highest altitude that counts, inclusive.
 * @param time `day` or `night`.
 * @param weather `clear`, `rain` or `thunder`.
 * @param label What the battle phone shows instead of the description the mod builds from the
 *   fields above. The mod names what it can - a biome has a vanilla translation, a structure
 *   ID does not - so a pack that wants its own words puts them here. A block holding *only* a
 *   label is valid and shown: it says where the trainer is without making them callable, which
 *   is how a champion points at their own gym.
 * @param arrival What the trainer says on arriving, sent to the calling player alone. Their
 *   coordinates are passed as three arguments, so `%s %s %s` reaches them.
 * @param busy What they say when another copy of them is already standing nearby.
 */
data class TrainerLocation(
    val dimension: String? = null,
    val biome: String? = null,
    val structure: String? = null,
    val area: TrainerArea? = null,
    val minY: Int? = null,
    val maxY: Int? = null,
    val time: String? = null,
    val weather: String? = null,
    val label: String? = null,
    val arrival: String? = null,
    val busy: String? = null
) {

    /**
     * True for a block that names no place at all - only text, or nothing.
     *
     * This is what decides callability, not the presence of the block: a pack that wrote just
     * an `arrival` line would otherwise have opened its champion to the whole world.
     *
     * A block holding only a [label] is still worth writing, and still shown by the battle
     * phone: saying where a trainer is and coming when called are two different things.
     */
    val isEmpty: Boolean
        get() = dimension.isNullOrBlank() && biome.isNullOrBlank() && structure.isNullOrBlank() &&
            area == null && minY == null && maxY == null &&
            time.isNullOrBlank() && weather.isNullOrBlank()

    fun validate(id: ResourceLocation) {
        warn(id, "location.time", time, TIME_DAY, TIME_NIGHT)
        warn(id, "location.weather", weather, WEATHER_CLEAR, WEATHER_RAIN, WEATHER_THUNDER)

        if (area != null && !area.isValid) {
            CobblemonTrainers.LOGGER.warn(
                "Trainer {}: location.area needs a `from` and a `to` of two numbers each, " +
                    "x and z. Ignoring it.",
                id
            )
        }
        if (minY != null && maxY != null && minY > maxY) {
            CobblemonTrainers.LOGGER.warn(
                "Trainer {}: location.minY ({}) is above location.maxY ({}), so no altitude " +
                    "can ever match.",
                id, minY, maxY
            )
        }
        if (!isEmpty) return

        // A block naming no place is fine when it is only there to say where the trainer is:
        // the battle phone shows the label, and no button appears. What is not fine is a block
        // that writes what the trainer says on arriving without ever letting them be called.
        if (!arrival.isNullOrBlank() || !busy.isNullOrBlank()) {
            CobblemonTrainers.LOGGER.warn(
                "Trainer {}: its location block names no place, so the trainer is never called " +
                    "and its arrival and busy lines are never used. Add a dimension, a biome, a " +
                    "structure, an area, an altitude, a time or a weather to it.",
                id
            )
            return
        }

        if (label.isNullOrBlank()) {
            CobblemonTrainers.LOGGER.warn(
                "Trainer {}: its location block is empty, so it does nothing at all. Give it a " +
                    "label to say where the trainer is, or a place to make them callable.",
                id
            )
        }
    }

    companion object {
        const val TIME_DAY = "day"
        const val TIME_NIGHT = "night"
        const val WEATHER_CLEAR = "clear"
        const val WEATHER_RAIN = "rain"
        const val WEATHER_THUNDER = "thunder"

        /** Marks a biome or structure field as naming a tag rather than a single entry. */
        const val TAG_PREFIX = '#'

        private fun warn(id: ResourceLocation, field: String, value: String?, vararg accepted: String) {
            if (value.isNullOrBlank()) return
            if (accepted.any { it.equals(value, ignoreCase = true) }) return
            CobblemonTrainers.LOGGER.warn(
                "Trainer {}: unknown {} '{}'. Ignoring it. Expected one of: {}",
                id, field, value, accepted.joinToString(", ")
            )
        }
    }
}

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
