package matheo1712.cobbletrainers

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import net.minecraft.network.chat.Component
import java.util.Locale

/**
 * Converts a Pokémon Showdown team into Cobblemon [PokemonProperties].
 *
 * The strategy is to rebuild a Cobblemon property string
 * (`"pikachu level=88 ability=static ..."`) and hand it to [PokemonProperties.parse]. Values
 * that may contain spaces (nickname, held item) are assigned directly on the resulting
 * object, because Cobblemon's property parser splits on spaces.
 *
 * Known limitation: Showdown-style regional forms (`Raichu-Alola`) are not translated into
 * Cobblemon aspects.
 */
object ShowdownTeamParser {

    private val LOGGER = CobblemonTrainers.LOGGER

    /** Namespace applied to held items that do not specify one. */
    private const val DEFAULT_ITEM_NAMESPACE = "cobblemon"

    /**
     * Showdown abbreviations mapped to the stat names [PokemonProperties] expects, which it
     * derives from the constants of Cobblemon's `Stats` enum.
     */
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

    // Nickname (Species) (M)
    private val NICKNAME_SPECIES_GENDER = Regex("""^(.+?)\s*\((.+?)\)\s*\(([MF])\)$""")
    // Species (M)
    private val SPECIES_GENDER = Regex("""^(.+?)\s*\(([MF])\)$""")
    // Nickname (Species)
    private val NICKNAME_SPECIES = Regex("""^(.+?)\s*\((.+?)\)$""")

    /**
     * Converts a trainer's `team` array into [PokemonProperties].
     *
     * Two layouts are accepted, and may be mixed:
     * - one entry per Pokémon, its lines separated by `\n`;
     * - one entry per line, Pokémon separated by an empty entry (`""`).
     */
    fun parse(teamEntries: List<String>): List<PokemonProperties> {
        val text = buildString {
            for (entry in teamEntries) {
                if (entry.contains('\n')) {
                    // Self-contained entry: isolate it with blank lines.
                    append('\n').append(entry).append("\n\n")
                } else {
                    append(entry).append('\n')
                }
            }
        }
        return parse(text)
    }

    /**
     * Converts a full Showdown team text into a list of [PokemonProperties].
     * Pokémon are separated by a blank line.
     */
    fun parse(showdownText: String): List<PokemonProperties> {
        val result = mutableListOf<PokemonProperties>()

        for (block in splitIntoBlocks(showdownText)) {
            try {
                result.add(parsePokemon(block))
            } catch (e: Exception) {
                LOGGER.warn("Failed to parse a Pokémon: {}\n{}", e.message, block.joinToString("\n"))
            }
        }

        return result
    }

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
            // Must be tested before the "Nickname (Species)" pattern, otherwise "Pikachu (M)"
            // reads as the species "M" nicknamed "Pikachu".
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

        for (line in lines.drop(1)) {
            when {
                line.startsWith("Ability:", ignoreCase = true) ->
                    builder.append(" ability=${normalizeName(line.substringAfter(':'))}")

                line.startsWith("Level:", ignoreCase = true) ->
                    line.substringAfter(':').trim().toIntOrNull()?.let { builder.append(" level=$it") }

                // Showdown puts the gender in parentheses on the first line, but many exports
                // also use a dedicated line.
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
                    val move = normalizeName(line.removePrefix("-"))
                    if (move.isNotEmpty()) moves.add(move)
                }
            }
        }

        if (moves.isNotEmpty()) {
            builder.append(" moves=${moves.joinToString(",")}")
        }

        val properties = PokemonProperties.parse(builder.toString())

        // Assigned afterwards: these values may contain spaces, which Cobblemon's property
        // parser uses as a separator.
        if (nickname.isNotBlank() && !nickname.equals(species, ignoreCase = true)) {
            properties.nickname = Component.literal(nickname)
        }
        if (itemPart.isNotBlank()) {
            properties.heldItem = normalizeItem(itemPart)
        }

        return properties
    }

    /** "Light Ball" -> "cobblemon:light_ball". An explicit namespace is kept as is. */
    private fun normalizeItem(raw: String): String {
        val cleaned = raw.trim().replace(' ', '_').lowercase(Locale.ROOT)
        return if (cleaned.contains(':')) cleaned else "$DEFAULT_ITEM_NAMESPACE:$cleaned"
    }

    /** "Quick Attack" -> "quickattack", the form Cobblemon's registries expect. */
    private fun normalizeName(raw: String): String =
        raw.trim().replace(" ", "").lowercase(Locale.ROOT)

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
