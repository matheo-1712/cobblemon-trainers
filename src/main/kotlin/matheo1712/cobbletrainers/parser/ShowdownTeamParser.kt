package matheo1712.cobbletrainers.parser

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.properties.CustomPokemonProperty
import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.text.Normalizer
import java.util.Locale

/**
 * Converts a Pokémon Showdown team into Cobblemon [com.cobblemon.mod.common.api.pokemon.PokemonProperties].
 *
 * The strategy is to rebuild a Cobblemon property string
 * (`"pikachu level=88 ability=static ..."`) and hand it to [com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse]. Values
 * that may contain spaces (nickname, held item) are assigned directly on the resulting
 * object, because Cobblemon's property parser splits on spaces.
 *
 * Known limitation: Showdown writes a form as a suffix of the species name (`Raichu-Alola`),
 * which is not translated here - forms are declared with an `Aspects:` line instead.
 *
 * Two lines are ours rather than Showdown's, both written in the shape of theirs: `Aspects:`
 * for forms, and `Fallback Item:` for what a Pokémon holds when the mod providing its real item
 * is not installed.
 */
object ShowdownTeamParser {

    private val LOGGER = CobblemonTrainers.LOGGER


    /////////////////////////////////////
    // CONFIGURATION
    /////////////////////////////////////
    private const val DEFAULT_ITEM_NAMESPACE = "cobblemon"

    /**
     * The line a Pokémon writes to name what it holds when its real item is not there. Not part
     * of the Showdown format - a line of ours, in the shape of theirs, like `Aspects:`.
     */
    private const val FALLBACK_ITEM_LINE = "Fallback Item:"
    private val STAT_NAMES = mapOf(
        "hp" to "hp",
        "atk" to "attack",
        "attack" to "attack",
        "def" to "defence",
        "defense" to "defence",
        "defence" to "defence",
        "spa" to "special_attack",
        "spd" to "special_defence",
        "spe" to "speed",
        "speed" to "speed"
    )
    private val ASPECT_SEPARATOR = Regex("""[,\s]+""")
    private val DIACRITICS = Regex("""\p{M}+""")
    private val NON_ALPHANUMERIC = Regex("""[^a-z0-9]+""")
    private val ITEM_ELIDED = Regex("""['’.]""")
    private val NICKNAME_SPECIES_GENDER = Regex("""^(.+?)\s*\((.+?)\)\s*\(([MF])\)$""")
    private val SPECIES_GENDER = Regex("""^(.+?)\s*\(([MF])\)$""")
    private val NICKNAME_SPECIES = Regex("""^(.+?)\s*\((.+?)\)$""")

    /**
     * Converts a trainer's `team` array into [com.cobblemon.mod.common.api.pokemon.PokemonProperties].
     *
     * One entry is one Pokémon, its lines separated by `\n` - the block Showdown exports,
     * pasted as a string. An entry holding several of them, blank line and all, is split like
     * any Showdown text rather than refused.
     */
    fun parse(teamEntries: List<String>): List<PokemonProperties> {
        val result = mutableListOf<PokemonProperties>()

        for (block in blocksOf(teamEntries)) {
            try {
                result.add(parsePokemon(block))
            } catch (e: Exception) {
                LOGGER.warn("Failed to parse a Pokémon: {}\n{}", e.message, block.joinToString("\n"))
            }
        }

        return result
    }

    /**
     * How many Pokémon a team declares, without building a single one of them.
     *
     * [parse] goes through Cobblemon's registries for every species, move and item, which is
     * far more than a listing wants when all it needs is the size of the team. The split is
     * the same one, stopped a step earlier.
     */
    fun countPokemon(teamEntries: List<String>): Int = blocksOf(teamEntries).size

    /** The Pokémon of a `team` array, one block of Showdown lines each. */
    private fun blocksOf(teamEntries: List<String>): List<List<String>> =
        teamEntries.flatMap { splitIntoBlocks(it) }

    /**
     * Converts a full Showdown team text into a list of [PokemonProperties]. Pokémon are
     * separated by a blank line - for a caller holding one pasted export rather than an array.
     */
    fun parse(showdownText: String): List<PokemonProperties> = parse(listOf(showdownText))

    private fun splitIntoBlocks(showdownText: String): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()

        for (rawLine in showdownText.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                if (current.isNotEmpty()) {
                    blocks.add(current)
                    current = mutableListOf()
                }
            } else {
                current.add(line)
            }
        }
        if (current.isNotEmpty()) blocks.add(current)

        return blocks
    }

    /** Parses a block of lines describing a single Pokémon. */
    private fun parsePokemon(lines: List<String>): PokemonProperties {
        val builder = StringBuilder()

        // First line: "Nickname (Species) (M) @ Item"
        val firstLine = lines.first()
        val atIndex = firstLine.indexOf('@')
        val namePart = (if (atIndex >= 0) firstLine.substring(0, atIndex) else firstLine).trim()
        val itemPart = if (atIndex >= 0) firstLine.substring(atIndex + 1).trim() else ""

        var nickname = ""
        val species: String
        val gender: String?

        val nicknameSpeciesGender = NICKNAME_SPECIES_GENDER.find(namePart)
        val speciesGender = SPECIES_GENDER.find(namePart)
        val nicknameSpecies = NICKNAME_SPECIES.find(namePart)

        when {
            nicknameSpeciesGender != null -> {
                nickname = nicknameSpeciesGender.groupValues[1].trim()
                species = nicknameSpeciesGender.groupValues[2].trim()
                gender = nicknameSpeciesGender.groupValues[3]
            }

            speciesGender != null -> {
                species = speciesGender.groupValues[1].trim()
                gender = speciesGender.groupValues[2]
            }
            nicknameSpecies != null -> {
                nickname = nicknameSpecies.groupValues[1].trim()
                species = nicknameSpecies.groupValues[2].trim()
                gender = null
            }
            else -> {
                species = namePart
                gender = null
            }
        }

        builder.append(normalizeName(species))

        if (gender != null) {
            builder.append(if (gender == "M") " gender=male" else " gender=female")
        }

        val moves = mutableListOf<String>()
        val aspects = mutableListOf<String>()
        val fallbackItems = mutableListOf<String>()

        for (line in lines.drop(1)) {
            when {
                line.startsWith("Aspects:", ignoreCase = true) ->
                    aspects += splitAspects(line.substringAfter(':'))

                line.startsWith(FALLBACK_ITEM_LINE, ignoreCase = true) ->
                    line.substringAfter(':').trim().takeIf { it.isNotEmpty() }?.let { fallbackItems += it }

                line.startsWith("Ability:", ignoreCase = true) ->
                    builder.append(" ability=${normalizeName(line.substringAfter(':'))}")

                line.startsWith("Level:", ignoreCase = true) ->
                    line.substringAfter(':').trim().toIntOrNull()?.let { builder.append(" level=$it") }

                line.startsWith("Gender:", ignoreCase = true) -> {
                    when (line.substringAfter(':').trim().uppercase(Locale.ROOT)) {
                        "M" -> builder.append(" gender=male")
                        "F" -> builder.append(" gender=female")
                    }
                }

                line.startsWith("Shiny:", ignoreCase = true) -> {
                    if (line.substringAfter(':').trim().equals("yes", ignoreCase = true)) {
                        builder.append(" shiny=yes")
                    }
                }

                line.startsWith("EVs:", ignoreCase = true) ->
                    appendStats(builder, line.substringAfter(':'), "ev")

                line.startsWith("IVs:", ignoreCase = true) ->
                    appendStats(builder, line.substringAfter(':'), "iv")

                line.endsWith("Nature", ignoreCase = true) ->
                    builder.append(" nature=${normalizeName(line.dropLast("Nature".length))}")

                line.startsWith("-") -> {
                    val rawMove = line.removePrefix("-").trim()
                    val move = normalizeName(rawMove)
                    when {
                        move.isEmpty() -> Unit
                        Moves.getByName(move) == null ->
                            LOGGER.warn("Ignoring unknown move '{}' (read as '{}')", rawMove, move)

                        else -> moves.add(move)
                    }
                }
            }
        }

        for (aspect in aspects.distinct()) {
            appendAspect(builder, aspect)
        }

        if (moves.isNotEmpty()) {
            builder.append(" moves=${moves.joinToString(",")}")
        }

        val properties = PokemonProperties.parse(builder.toString())

        if (nickname.isNotBlank() && !nickname.equals(species, ignoreCase = true)) {
            properties.nickname = Component.literal(nickname)
        }
        resolveHeldItem(listOfNotNull(itemPart.takeIf { it.isNotBlank() }) + fallbackItems)?.let {
            properties.heldItem = it
        }

        return properties
    }

    private fun splitAspects(raw: String): List<String> =
        raw.split(ASPECT_SEPARATOR).map { normalizeAspect(it) }.filter { it.isNotEmpty() }

    /**
     * An aspect keeps its punctuation, unlike a species or a move name: it is already written as
     * an identifier, and `appliance=wash` would lose the pair that makes it mean anything.
     */
    private fun normalizeAspect(raw: String): String = raw.trim().lowercase(Locale.ROOT)

    /**
     * Parse aspect aspect:
     */
    private fun appendAspect(builder: StringBuilder, aspect: String) {
        val key = aspect.substringBefore('=')
        val known = CustomPokemonProperty.properties.any { type ->
            type.keys.any { it.equals(key, ignoreCase = true) }
        }
        if (!known) {
            LOGGER.warn("Ignoring unknown aspect '{}': no species feature declares that key", key)
            return
        }

        builder.append(' ').append(if (aspect.contains('=')) aspect else "$aspect=true")
    }

    /**
     * The first of these item names that resolves, or null when none of them does.
     *
     * The list is what the Pokémon asked to hold, in order: the `@` of its first line, then
     * every `Fallback Item:` line it wrote. A pack declares one so that a team stays playable
     * when the mod providing the real item is not installed - a Mega Stone with a Life Orb
     * behind it battles either way. The rule knows nothing about mega stones: it is simply the
     * first name that exists.
     */
    private fun resolveHeldItem(candidates: List<String>): String? =
        candidates.firstNotNullOfOrNull { resolveItem(it) }

    /**
     * One item name, as a registered item ID, or null.
     *
     * A Showdown export writes items for a human (`Heavy-Duty Boots`) and never says which mod
     * they belong to, so an unqualified name is read as Cobblemon's first - that is where the
     * held items of a team come from. Failing that, the same path is looked up in every
     * namespace the game has loaded, which is what lets a pack name an item from another mod
     * (a Mega Stone, say) without writing that mod into its team. Several matches is not an
     * answer, so it is refused rather than guessed.
     *
     * Every refusal is logged here rather than by the caller: each has its own reason, and a
     * name that fails is exactly what a `Fallback Item:` line answers, so silence would leave a
     * pack author with a Pokémon holding the wrong thing and nothing to read.
     */
    private fun resolveItem(raw: String): String? {
        val trimmed = raw.trim()

        if (trimmed.contains(':')) {
            val namespace = trimmed.substringBefore(':').trim().lowercase(Locale.ROOT)
            val qualified = "$namespace:${normalizeItemPath(trimmed.substringAfter(':'))}"
            if (isRegisteredItem(qualified)) return qualified

            LOGGER.warn("Ignoring held item '{}' (read as '{}'): no such item is registered", trimmed, qualified)
            return null
        }

        val path = normalizeItemPath(trimmed)
        val cobblemon = "$DEFAULT_ITEM_NAMESPACE:$path"
        if (isRegisteredItem(cobblemon)) return cobblemon

        val matches = BuiltInRegistries.ITEM.keySet().filter { it.path == path }
        return when (matches.size) {
            0 -> {
                LOGGER.warn("Ignoring held item '{}' (read as '{}'): no such item is registered", trimmed, path)
                null
            }

            1 -> matches.first().toString()

            else -> {
                LOGGER.warn(
                    "Ignoring held item '{}': '{}' is registered in several namespaces ({}). Write the full ID.",
                    trimmed, path, matches.joinToString(", ")
                )
                null
            }
        }
    }

    private fun normalizeItemPath(raw: String): String =
        Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)
            .replace(ITEM_ELIDED, "")
            .replace(NON_ALPHANUMERIC, "_")
            .trim('_')

    /** Whether an item id resolves, the same lookup the vanilla `ItemParser` would perform. */
    private fun isRegisteredItem(id: String): Boolean {
        val location = ResourceLocation.tryParse(id) ?: return false
        return BuiltInRegistries.ITEM.containsKey(location)
    }

    /**
     * "Quick Attack" -> "quickattack"
     * "U-turn" -> "uturn"
     */
    private fun normalizeName(raw: String): String =
        Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC, "")

    /**
     * Turns "252 SpA / 4 SpD / 252 Spe" into
     * ` special_attack_ev=252 special_defence_ev=4 speed_ev=252`.
     */
    private fun appendStats(builder: StringBuilder, statsString: String, suffix: String) {
        for (part in statsString.split("/")) {
            val tokens = part.trim().split(Regex("\\s+"))
            if (tokens.size < 2) continue

            val value = tokens[0].toIntOrNull() ?: continue
            val statName = STAT_NAMES[tokens[1].lowercase(Locale.ROOT)]
            if (statName == null) {
                LOGGER.warn("Ignoring unknown stat '{}'", tokens[1])
                continue
            }

            builder.append(" ${statName}_$suffix=$value")
        }
    }
}