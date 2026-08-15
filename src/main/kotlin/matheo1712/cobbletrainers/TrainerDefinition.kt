package matheo1712.cobbletrainers

/**
 * Définit les propriétés d'un dresseur chargées depuis un fichier JSON.
 *
 * @param name Le nom affiché du dresseur dans le monde.
 * @param skin Configuration du skin (pseudo ou UUID Minecraft).
 * @param team Liste de Pokémon au format Showdown. Une entrée vide (`""`) sépare deux Pokémon.
 * @param level Niveau du dresseur, utilisé comme niveau par défaut des Pokémon qui n'en précisent pas.
 * @param canBattle Si le dresseur peut engager un combat. À `false`, l'interaction est désactivée.
 * @param battleStartMessage Message envoyé au joueur au début du combat (optionnel).
 * @param battleEndWinMessage Message envoyé si le joueur gagne (optionnel).
 * @param battleEndLoseMessage Message envoyé si le joueur perd (optionnel).
 * @param npcClass ResourceLocation d'un NPCClass Cobblemon. Par défaut `cobblemon-trainers:trainer`.
 *
 * Note : le soin de l'équipe après un combat n'est pas configurable par dresseur dans
 * Cobblemon 1.7.3 — il dépend de `autoHealParty` sur le NPCClass. Pour le changer, définis
 * ton propre NPCClass dans `data/<namespace>/npcs/` et référence-le via [npcClass].
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
 * Configuration du skin du dresseur.
 *
 * Types supportés :
 * - `"player_username"` : utilise le skin du joueur Minecraft correspondant au pseudo.
 * - `"player_uuid"` : utilise le skin du joueur Minecraft correspondant à l'UUID.
 *
 * @param type Le type de skin.
 * @param value La valeur du skin (pseudo ou UUID).
 */
data class TrainerSkin(
    val type: String = "player_username",
    val value: String = "Steve"
)
