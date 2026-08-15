package matheo1712.cobbletrainers

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.util.Locale

/**
 * Convertit une équipe au format Pokémon Showdown en [PokemonProperties] pour Cobblemon.
 *
 * La stratégie est de reconstruire une chaîne de propriétés Cobblemon
 * (`"pikachu level=88 ability=static ..."`) puis de déléguer à [PokemonProperties.parse].
 * Les valeurs pouvant contenir des espaces (surnom, objet tenu) sont assignées directement
 * sur l'objet résultant, car le parseur de Cobblemon découpe la chaîne sur les espaces.
 *
 * Limite connue : les formes régionales notées à la Showdown (`Raichu-Alola`) ne sont pas
 * traduites vers les aspects Cobblemon.
 */
object ShowdownTeamParser {

    private val LOGGER = LoggerFactory.getLogger("CobbleTrainers/ShowdownParser")

    /** Namespace appliqué aux objets tenus qui n'en précisent pas. */
    private const val DEFAULT_ITEM_NAMESPACE = "cobblemon"

    /**
     * Abréviations Showdown vers les noms de statistiques attendus par [PokemonProperties],
     * qui les dérive des constantes de l'enum `Stats` de Cobblemon.
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
     * Convertit le tableau `team` d'un dresseur en [PokemonProperties].
     *
     * Deux écritures sont acceptées, y compris mélangées :
     * - une entrée par Pokémon, avec ses lignes séparées par `\n` ;
     * - une entrée par ligne, les Pokémon étant séparés par une entrée vide (`""`).
     */
    fun parse(teamEntries: List<String>): List<PokemonProperties> {
        val text = buildString {
            for (entry in teamEntries) {
                if (entry.contains('\n')) {
                    // Entrée auto-suffisante : on l'isole par des lignes vides.
                    append('\n').append(entry).append("\n\n")
                } else {
                    append(entry).append('\n')
                }
            }
        }
        return parse(text)
    }

    /**
     * Convertit un texte complet d'équipe Showdown en une liste de [PokemonProperties].
     * Les Pokémon sont séparés par une ligne vide.
     */
    fun parse(showdownText: String): List<PokemonProperties> {
        val result = mutableListOf<PokemonProperties>()

        for (block in splitIntoBlocks(showdownText)) {
            try {
                result.add(parsePokemon(block))
            } catch (e: Exception) {
                LOGGER.warn("Impossible de parser un Pokémon : ${e.message}\nBloc : ${block.joinToString("\n")}")
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

    /** Parse un bloc de lignes représentant un seul Pokémon. */
    private fun parsePokemon(lines: List<String>): PokemonProperties {
        val builder = StringBuilder()

        // Première ligne : "Surnom (Espèce) (M) @ Objet"
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
            // Doit passer avant le motif "Surnom (Espèce)", sinon "Pikachu (M)"
            // serait lu comme l'espèce "M" surnommée "Pikachu".
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

                // Showdown note le genre entre parenthèses sur la première ligne, mais
                // beaucoup d'exports utilisent aussi une ligne dédiée.
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

        // Assignés après coup : ces valeurs peuvent contenir des espaces, que le parseur
        // de propriétés de Cobblemon utilise comme séparateur.
        if (nickname.isNotBlank() && !nickname.equals(species, ignoreCase = true)) {
            properties.nickname = Component.literal(nickname)
        }
        if (itemPart.isNotBlank()) {
            properties.heldItem = normalizeItem(itemPart)
        }

        return properties
    }

    /** "Light Ball" -> "cobblemon:light_ball". Un namespace explicite est conservé tel quel. */
    private fun normalizeItem(raw: String): String {
        val cleaned = raw.trim().replace(' ', '_').lowercase(Locale.ROOT)
        return if (cleaned.contains(':')) cleaned else "$DEFAULT_ITEM_NAMESPACE:$cleaned"
    }

    /** "Quick Attack" -> "quickattack", forme attendue par les registres de Cobblemon. */
    private fun normalizeName(raw: String): String =
        raw.trim().replace(" ", "").lowercase(Locale.ROOT)

    /**
     * Traduit "252 SpA / 4 SpD / 252 Spe" en ` special_attack_ev=252 special_defence_ev=4 speed_ev=252`.
     */
    private fun appendStats(builder: StringBuilder, statsString: String, suffix: String) {
        for (part in statsString.split("/")) {
            val tokens = part.trim().split(Regex("\\s+"))
            if (tokens.size < 2) continue

            val value = tokens[0].toIntOrNull() ?: continue
            val statName = STAT_NAMES[tokens[1].lowercase(Locale.ROOT)]
            if (statName == null) {
                LOGGER.warn("Statistique inconnue ignorée : '${tokens[1]}'")
                continue
            }

            builder.append(" ${statName}_$suffix=$value")
        }
    }
}
