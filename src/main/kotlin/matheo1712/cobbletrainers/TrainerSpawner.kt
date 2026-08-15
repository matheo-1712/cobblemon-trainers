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
import java.net.URI
import java.util.UUID

/**
 * Creates and spawns [NPCEntity] instances from a [TrainerDefinition].
 */
object TrainerSpawner {

    /** NPC class used when a trainer does not specify one. Shipped by the mod. */
    val DEFAULT_NPC_CLASS: ResourceLocation = CobblemonTrainers.id("trainer")

    private val LOGGER = CobblemonTrainers.LOGGER

    /**
     * Spawns a trainer in the world at the given position.
     *
     * @return the spawned [NPCEntity], or null on failure.
     */
    fun spawn(
        server: MinecraftServer,
        level: ServerLevel,
        position: Vec3,
        definition: TrainerDefinition,
        trainerId: ResourceLocation
    ): NPCEntity? {
        // 1. Resolve the NPC class. No fallback to an arbitrary class: failing loudly beats
        //    spawning an NPC that behaves unpredictably.
        val npcClassId = definition.npcClass?.let { ResourceLocation.tryParse(it) } ?: DEFAULT_NPC_CLASS
        val npcClass = NPCClasses.getByIdentifier(npcClassId) ?: run {
            LOGGER.error(
                "Unknown NPC class {}. Available classes: {}",
                npcClassId,
                NPCClasses.classes.joinToString(", ") { it.id.toString() }
            )
            return null
        }

        // 2. Create and position the entity
        val npc = NPCEntity(level)
        npc.npc = npcClass
        npc.moveTo(position.x, position.y, position.z, npc.yRot, npc.xRot)

        // 3. Display name. The npc.npc setter assigns a random one from the class, so this
        //    has to come after. Translatable so datapacks may use a lang key.
        npc.customName = Component.translatable(definition.name)

        // 4. Tag the entity with its trainer ID. Applied aspects are serialized to NBT, which
        //    is how the definition is found again after a restart.
        npc.appliedAspects.add(CobblemonTrainers.TRAINER_ASPECT_PREFIX + trainerId)

        // 5. Cobblemon 1.7.3 no longer reads `canChallenge`: battles are triggered by the NPC
        //    interaction, so disable it at the entity level instead.
        if (!definition.canBattle) {
            npc.interaction = NoneNPCInteractionConfiguration()
        }

        // 6. initialize() resets `party` to whatever the NPC class provides, so the trainer
        //    team has to be assigned afterwards.
        npc.initialize(definition.level)

        applyTeam(npc, definition, trainerId)
        npc.updateAspects()

        // 7. The skin arrives asynchronously, after the entity is spawned.
        applySkin(server, npc, definition.skin)

        if (!level.addFreshEntity(npc)) {
            LOGGER.error("Failed to spawn trainer {}", trainerId)
            return null
        }

        LOGGER.info(
            "Spawned trainer {} at {}, {}, {}",
            trainerId,
            position.x.toInt(),
            position.y.toInt(),
            position.z.toInt()
        )
        return npc
    }

    /**
     * Builds the team and assigns it to the entity.
     *
     * The team is deliberately set on `npc.party` (the entity store) rather than
     * `npc.npc.party`: the NPC class is a singleton shared by every NPC of that class, so
     * changing its team would replace it for all of them.
     */
    private fun applyTeam(npc: NPCEntity, definition: TrainerDefinition, trainerId: ResourceLocation) {
        if (definition.team.isEmpty()) return

        val pokemonProps = ShowdownTeamParser.parse(definition.team)
        if (pokemonProps.isEmpty()) {
            LOGGER.warn("Trainer {} has no valid Pokémon in its team", trainerId)
            return
        }

        val provider = SimplePartyProvider().also { it.pokemon.addAll(pokemonProps) }
        npc.party = provider.provide(npc, definition.level, emptyList())
    }

    /**
     * Applies a Minecraft player skin to the NPC.
     *
     * Profile lookup and texture download run on a dedicated thread; only the application to
     * the entity goes back through the server thread. On failure the NPC keeps the default
     * skin of its class.
     */
    private fun applySkin(server: MinecraftServer, npc: NPCEntity, skin: TrainerSkin) {
        Thread {
            try {
                val uuid = resolveProfileId(server, skin) ?: return@Thread
                val texture = fetchTexture(server, uuid) ?: return@Thread

                server.execute {
                    if (!npc.isAlive) return@execute
                    // Mirrors NPCEntity.loadTexture() without its network I/O: the model-*
                    // aspects decide which rig is used when rendering.
                    npc.appliedAspects -= "model-default"
                    npc.appliedAspects -= "model-slim"
                    npc.appliedAspects += "model-${texture.model.name.lowercase()}"
                    npc.entityData.set(NPCEntity.NPC_PLAYER_TEXTURE, texture)
                    npc.updateAspects()
                }
            } catch (e: Exception) {
                LOGGER.warn("Failed to apply skin '{}': {}", skin.value, e.message)
            }
        }.also {
            it.isDaemon = true
            it.name = "CobblemonTrainers-SkinFetcher"
        }.start()
    }

    private fun resolveProfileId(server: MinecraftServer, skin: TrainerSkin): UUID? {
        val uuid = when (skin.type.lowercase()) {
            "player_username" -> lookupByName(server, skin.value)

            "player_uuid" ->
                try {
                    UUID.fromString(skin.value)
                } catch (e: IllegalArgumentException) {
                    LOGGER.warn("Invalid UUID: {}", skin.value)
                    null
                }

            else -> {
                LOGGER.warn("Unknown skin type '{}'. Use 'player_username' or 'player_uuid'.", skin.type)
                null
            }
        }

        if (uuid == null) {
            LOGGER.warn("Could not resolve a profile for skin '{}' (type: {})", skin.value, skin.type)
        }
        return uuid
    }

    /**
     * Resolves a username into a Mojang UUID.
     *
     * The server profile cache is not enough: offline, it makes up a version 3 UUID derived
     * from the name, which the session service does not know. In that case query the profile
     * repository directly, the way Cobblemon does.
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
                    LOGGER.warn("No Mojang profile found for '{}': {}", profileName, exception.message)
                }
            })
        } catch (e: Exception) {
            LOGGER.warn("Profile lookup failed for '{}': {}", name, e.message)
        }
        return resolved
    }

    /** Downloads the player texture from Mojang. Blocking: never call it on the server thread. */
    private fun fetchTexture(server: MinecraftServer, uuid: UUID): NPCPlayerTexture? {
        return try {
            val profile = server.sessionService.fetchProfile(uuid, false)?.profile
            if (profile == null) {
                LOGGER.warn("No Mojang session profile for UUID {}", uuid)
                return null
            }
            val skin = server.sessionService.getTextures(profile).skin
            if (skin == null) {
                LOGGER.warn("Profile {} has no skin texture", uuid)
                return null
            }
            val model = NPCPlayerModelType.valueOf((skin.getMetadata("model") ?: "default").uppercase())
            val bytes = URI(skin.url).toURL().openStream().use { it.readBytes() }
            NPCPlayerTexture(bytes, model)
        } catch (e: Exception) {
            LOGGER.warn("Failed to download the texture from Mojang: {}", e.message)
            null
        }
    }
}
