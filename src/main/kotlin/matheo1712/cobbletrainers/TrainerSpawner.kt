package matheo1712.cobbletrainers

import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.api.npc.configuration.interaction.NoneNPCInteractionConfiguration
import com.cobblemon.mod.common.api.npc.partyproviders.SimplePartyProvider
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.entity.npc.NPCPlayerModelType
import com.cobblemon.mod.common.entity.npc.NPCPlayerTexture
import com.mojang.authlib.GameProfile
import com.mojang.authlib.ProfileLookupCallback
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID

/**
 * Crée et fait apparaître les entités [NPCEntity] à partir d'une [TrainerDefinition].
 */
object TrainerSpawner {

    /** NPCClass utilisé quand le dresseur n'en précise pas. Fourni par le mod. */
    val DEFAULT_NPC_CLASS: ResourceLocation = CobblemonTrainers.id("trainer")

    private val LOGGER = LoggerFactory.getLogger("CobbleTrainers/Spawner")

    /**
     * Spawne un dresseur dans le monde à la position donnée.
     *
     * @return L'entité [NPCEntity] spawnée, ou null en cas d'échec.
     */
    fun spawn(
        server: MinecraftServer,
        level: ServerLevel,
        position: Vec3,
        definition: TrainerDefinition,
        trainerId: ResourceLocation
    ): NPCEntity? {
        // 1. Résoudre le NPCClass. Pas de repli sur une classe arbitraire : mieux vaut
        //    échouer clairement que spawner un NPC au comportement imprévisible.
        val npcClassId = definition.npcClass?.let { ResourceLocation.tryParse(it) } ?: DEFAULT_NPC_CLASS
        val npcClass = NPCClasses.getByIdentifier(npcClassId) ?: run {
            LOGGER.error(
                "NPCClass introuvable : $npcClassId. Classes disponibles : " +
                    NPCClasses.classes.joinToString(", ") { it.id.toString() }
            )
            return null
        }

        // 2. Créer et positionner l'entité
        val npc = NPCEntity(level)
        npc.npc = npcClass
        npc.moveTo(position.x, position.y, position.z, npc.yRot, npc.xRot)

        // 3. Nom affiché (le setter de npc.npc en assigne un au hasard depuis la classe)
        npc.customName = Component.literal(definition.name)

        // 4. Marquer l'entité avec l'ID du dresseur. Les aspects appliqués sont persistés
        //    en NBT, ce qui permet de retrouver la définition après un redémarrage.
        npc.appliedAspects.add(CobblemonTrainers.TRAINER_ASPECT_PREFIX + trainerId)

        // 5. Cobblemon 1.7.3 ne consulte plus `canChallenge` : le combat est déclenché par
        //    l'interaction du NPC. On la neutralise donc au niveau de l'entité.
        if (!definition.canBattle) {
            npc.interaction = NoneNPCInteractionConfiguration()
        }

        // 6. initialize() remet `party` à la valeur fournie par le NPCClass — l'équipe du
        //    dresseur doit donc être assignée après.
        npc.initialize(definition.level)

        applyTeam(npc, definition, trainerId)
        npc.updateAspects()

        // 7. Le skin arrive de façon asynchrone, après l'apparition de l'entité.
        applySkin(server, npc, definition.skin)

        if (!level.addFreshEntity(npc)) {
            LOGGER.error("Impossible de spawner le dresseur '$trainerId'")
            return null
        }

        LOGGER.info(
            "Dresseur '$trainerId' (${definition.name}) spawné en " +
                "${position.x.toInt()}, ${position.y.toInt()}, ${position.z.toInt()}"
        )
        return npc
    }

    /**
     * Construit l'équipe et l'assigne à l'entité.
     *
     * L'équipe est volontairement posée sur `npc.party` (le store de l'entité) et non sur
     * `npc.npc.party` : le NPCClass est un singleton partagé par tous les NPC de cette classe,
     * y modifier l'équipe la remplacerait pour tout le monde.
     */
    private fun applyTeam(npc: NPCEntity, definition: TrainerDefinition, trainerId: ResourceLocation) {
        if (definition.team.isEmpty()) return

        val pokemonProps = ShowdownTeamParser.parse(definition.team)
        if (pokemonProps.isEmpty()) {
            LOGGER.warn("L'équipe du dresseur $trainerId n'a aucun Pokémon valide !")
            return
        }

        val provider = SimplePartyProvider().also { it.pokemon.addAll(pokemonProps) }
        npc.party = provider.provide(npc, definition.level, emptyList())
    }

    /**
     * Applique le skin d'un joueur Minecraft au NPC.
     *
     * La résolution du profil et le téléchargement de la texture se font sur un thread
     * dédié ; seule l'application sur l'entité repasse par le thread serveur.
     * En cas d'échec, le NPC garde le skin par défaut de sa classe.
     */
    private fun applySkin(server: MinecraftServer, npc: NPCEntity, skin: TrainerSkin) {
        Thread {
            try {
                val uuid = resolveProfileId(server, skin) ?: return@Thread
                val texture = fetchTexture(server, uuid) ?: return@Thread

                server.execute {
                    if (!npc.isAlive) return@execute
                    // Reproduit NPCEntity.loadTexture() sans son I/O réseau : les aspects
                    // model-* déterminent le rig (Steve ou Alex) utilisé au rendu.
                    npc.appliedAspects -= "model-default"
                    npc.appliedAspects -= "model-slim"
                    npc.appliedAspects += "model-${texture.model.name.lowercase()}"
                    npc.entityData.set(NPCEntity.NPC_PLAYER_TEXTURE, texture)
                    npc.updateAspects()
                }
            } catch (e: Exception) {
                LOGGER.warn("Erreur lors de l'application du skin '${skin.value}' : ${e.message}")
            }
        }.also {
            it.isDaemon = true
            it.name = "CobbleTrainers-SkinFetcher"
        }.start()
    }

    private fun resolveProfileId(server: MinecraftServer, skin: TrainerSkin): UUID? {
        val uuid = when (skin.type.lowercase()) {
            "player_username" -> lookupByName(server, skin.value)

            "player_uuid" ->
                try {
                    UUID.fromString(skin.value)
                } catch (e: IllegalArgumentException) {
                    LOGGER.warn("UUID invalide : ${skin.value}")
                    null
                }

            else -> {
                LOGGER.warn("Type de skin inconnu : '${skin.type}'. Utilise 'player_username' ou 'player_uuid'.")
                null
            }
        }

        if (uuid == null) {
            LOGGER.warn("Impossible de résoudre le profil pour le skin : ${skin.value} (type: ${skin.type})")
        }
        return uuid
    }

    /**
     * Résout un pseudo en UUID Mojang.
     *
     * Le cache de profils du serveur ne suffit pas : hors ligne, il fabrique un UUID de
     * version 3 dérivé du pseudo, que l'API de session ne connaît pas. On interroge alors
     * directement le dépôt de profils, comme le fait Cobblemon.
     */
    private fun lookupByName(server: MinecraftServer, name: String): UUID? {
        val cached = server.profileCache?.get(name)?.orElse(null)?.id
        if (cached != null && cached.version() == 4) return cached

        var resolved: UUID? = null
        try {
            server.profileRepository.findProfilesByNames(arrayOf(name), object : ProfileLookupCallback {
                override fun onProfileLookupSucceeded(profile: GameProfile) {
                    resolved = profile.id
                }

                override fun onProfileLookupFailed(profileName: String, exception: Exception) {
                    LOGGER.warn("Profil Mojang introuvable pour '$profileName' : ${exception.message}")
                }
            })
        } catch (e: Exception) {
            LOGGER.warn("Échec de la recherche du profil '$name' : ${e.message}")
        }
        return resolved
    }

    /** Télécharge la texture du joueur depuis Mojang. Bloquant : à appeler hors du thread serveur. */
    private fun fetchTexture(server: MinecraftServer, uuid: UUID): NPCPlayerTexture? {
        return try {
            val profile = server.sessionService.fetchProfile(uuid, false)?.profile
            if (profile == null) {
                LOGGER.warn("Aucun profil de session Mojang pour l'UUID $uuid.")
                return null
            }
            val skin = server.sessionService.getTextures(profile).skin
            if (skin == null) {
                LOGGER.warn("Le profil $uuid n'a aucune texture de skin.")
                return null
            }
            val model = NPCPlayerModelType.valueOf((skin.getMetadata("model") ?: "default").uppercase())
            val bytes = URI(skin.url).toURL().openStream().use { it.readBytes() }
            NPCPlayerTexture(bytes, model)
        } catch (e: Exception) {
            LOGGER.warn("Impossible de télécharger la texture depuis Mojang : ${e.message}")
            null
        }
    }
}
