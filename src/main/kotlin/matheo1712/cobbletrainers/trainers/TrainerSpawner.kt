package matheo1712.cobbletrainers.trainers

import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.api.npc.configuration.interaction.NoneNPCInteractionConfiguration
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.entity.npc.NPCPlayerModelType
import com.cobblemon.mod.common.entity.npc.NPCPlayerTexture
import com.mojang.authlib.GameProfile
import com.mojang.authlib.ProfileLookupCallback
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.parser.ShowdownTeamParser
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.net.URI
import java.util.UUID

/**
 * Creates and spawns [com.cobblemon.mod.common.entity.npc.NPCEntity] instances from a [TrainerDefinition].
 */
object TrainerSpawner {

    /**
     * The NPC classes shipped by the mod. Datapacks configure trainers, never NPC classes:
     * what used to be worth changing there now lives on [TrainerDefinition].
     *
     * Battle format and AI difficulty are applied per entity, so they need no variant. Party
     * healing is the exception: Cobblemon only reads `autoHealParty` from the class
     * (`NPCBattleActor` and `PokemonBattle` both go through `npc.npc.autoHealParty`), so the
     * mod ships one class per value of that boolean. Keep the two files identical apart from
     * `autoHealParty`.
     */
    val NPC_CLASS_HEALING: ResourceLocation = CobblemonTrainers.id("trainer")
    val NPC_CLASS_NO_HEALING: ResourceLocation = CobblemonTrainers.id("trainer_no_heal")

    private val LOGGER = CobblemonTrainers.LOGGER

    /**
     * Spawns a trainer in the world at the given position.
     *
     * @param yRot Which way the trainer faces, in degrees.
     * @param extraAspects Aspects applied on top of the trainer ID one. Used by the trainer
     *   spawner block to mark the trainers it owns; aspects are saved to NBT, so such a mark
     *   survives a restart.
     * @return the spawned [com.cobblemon.mod.common.entity.npc.NPCEntity], or null on failure.
     */
    fun spawn(
        server: MinecraftServer,
        level: ServerLevel,
        position: Vec3,
        definition: TrainerDefinition,
        trainerId: ResourceLocation,
        yRot: Float = 0f,
        extraAspects: Collection<String> = emptyList()
    ): NPCEntity? {
        // 1. Resolve the NPC class shipped by the mod. No fallback to an arbitrary class:
        //    failing loudly beats spawning an NPC that behaves unpredictably.
        val npcClassId = if (definition.autoHealParty) NPC_CLASS_HEALING else NPC_CLASS_NO_HEALING
        val npcClass = NPCClasses.getByIdentifier(npcClassId) ?: run {
            LOGGER.error(
                "NPC class {} is missing — is the mod's data pack loaded? Available classes: {}",
                npcClassId,
                NPCClasses.classes.joinToString(", ") { it.id.toString() }
            )
            return null
        }

        // 2. Create and position the entity
        val npc = NPCEntity(level)
        npc.npc = npcClass
        npc.moveTo(position.x, position.y, position.z, yRot, npc.xRot)
        // moveTo only sets the entity rotation; without these the trainer stands facing the
        // right way but with its body and head turned to whatever the defaults were.
        npc.yBodyRot = yRot
        npc.yHeadRot = yRot

        // 3. Display name. The npc.npc setter assigns a random one from the class, so this
        //    has to come after. Translatable so a resource pack may localise it.
        npc.customName = Component.translatable(definition.name)

        // 4. Tag the entity with its trainer ID. Applied aspects are serialized to NBT, which
        //    is how the definition is found again after a restart.
        npc.appliedAspects.add(CobblemonTrainers.TRAINER_ASPECT_PREFIX + trainerId)
        npc.appliedAspects.addAll(extraAspects)

        // 5. Cobblemon 1.7.3 no longer reads `canChallenge`: battles are triggered by the NPC
        //    interaction, so disable it at the entity level instead.
        if (!definition.canBattle) {
            npc.interaction = NoneNPCInteractionConfiguration()
        }

        // 6. Battle AI difficulty, overridden per entity so every trainer can differ while
        //    sharing one NPC class. Cobblemon clamps it to 0..5 anyway.
        npc.skill = definition.skill.coerceIn(0, 5)

        // 7. initialize() resets `party` to whatever the NPC class provides, so the trainer
        //    team has to be assigned afterwards.
        npc.initialize(definition.level)

        applyTeam(npc, definition, trainerId)
        npc.updateAspects()

        // 8. The skin arrives asynchronously, after the entity is spawned.
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
     *
     * The store is filled here instead of through `SimplePartyProvider`: its `provide()`
     * starts by calling `PokemonProperties.copy()`, which round-trips through
     * `saveToJSON`/`loadFromJSON` — and that pair, unlike `saveToNBT`, does not carry
     * `nickname`, so a Showdown nickname was silently lost on the way to the party. What
     * `provide()` does beyond that copy is the level default and the store itself, both
     * reproduced below.
     */
    private fun applyTeam(npc: NPCEntity, definition: TrainerDefinition, trainerId: ResourceLocation) {
        if (definition.team.isEmpty()) return

        val pokemonProps = ShowdownTeamParser.parse(definition.team)
        if (pokemonProps.isEmpty()) {
            LOGGER.warn("Trainer {} has no valid Pokémon in its team", trainerId)
            return
        }

        val party = NPCPartyStore(npc)
        for (properties in pokemonProps) {
            // A `Level:` line on the Pokémon wins over the trainer-wide level.
            if (properties.level == null) {
                properties.level = definition.level
            }
            // create() is where a property the parser accepted meets Cobblemon's registries —
            // an unknown species or held item throws here. One bad entry costs its Pokémon, not
            // the trainer, and not the tick that asked for the spawn.
            try {
                party.add(properties.create())
            } catch (e: Exception) {
                LOGGER.warn("Skipping a Pokémon of trainer {}: {} ({})", trainerId, e.message, properties.asString(" "))
            }
        }
        npc.party = party
    }

    /**
     * Applies a skin to the NPC.
     *
     * Profile lookup, texture download and pack reads all run on a dedicated thread; only the
     * application to the entity goes back through the server thread. On failure the NPC keeps
     * the default skin of its class.
     */
    private fun applySkin(server: MinecraftServer, npc: NPCEntity, skin: TrainerSkin) {
        Thread {
            try {
                val texture = resolveTexture(server, skin) ?: return@Thread

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

    /** Turns a skin declaration into the texture that will be synced to the clients. */
    private fun resolveTexture(server: MinecraftServer, skin: TrainerSkin): NPCPlayerTexture? =
        when (skin.type.lowercase()) {
            "texture" -> readPackTexture(skin)

            "player_username", "player_uuid" ->
                resolveProfileId(server, skin)?.let { fetchTexture(server, it) }

            else -> {
                LOGGER.warn(
                    "Unknown skin type '{}'. Use 'player_username', 'player_uuid' or 'texture'.",
                    skin.type
                )
                null
            }
        }

    /**
     * Loads a skin image shipped in a pack. The bytes are sent to the clients with the entity,
     * so nothing has to be installed on their side — see [TrainerTextures].
     */
    private fun readPackTexture(skin: TrainerSkin): NPCPlayerTexture? {
        val location = ResourceLocation.tryParse(skin.value)
        if (location == null) {
            LOGGER.warn(
                "Invalid texture location '{}'. Expected <namespace>:<path>, " +
                    "e.g. cobblemon-trainers:textures/trainers/example.png",
                skin.value
            )
            return null
        }

        val bytes = TrainerTextures.read(location)
        if (bytes == null) {
            LOGGER.warn(
                "Texture {} was not found. It has to be reachable by the server: ship it in " +
                    "assets/{}/{} inside a pack of the mods folder.",
                location,
                location.namespace,
                location.path
            )
            return null
        }

        return NPCPlayerTexture(bytes, parseModel(skin))
    }

    /**
     * The player rig the texture is drawn on. Unlike a Mojang skin, an image in a pack does not
     * come with that information, so the trainer states it.
     */
    private fun parseModel(skin: TrainerSkin): NPCPlayerModelType =
        when (skin.model.lowercase()) {
            "default" -> NPCPlayerModelType.DEFAULT
            "slim" -> NPCPlayerModelType.SLIM
            else -> {
                LOGGER.warn(
                    "Unknown skin model '{}'. Use 'default' or 'slim'. Falling back to default.",
                    skin.model
                )
                NPCPlayerModelType.DEFAULT
            }
        }

    private fun resolveProfileId(server: MinecraftServer, skin: TrainerSkin): UUID? {
        val uuid = when (skin.type.lowercase()) {
            "player_username" -> lookupByName(server, skin.value)

            else ->
                try {
                    UUID.fromString(skin.value)
                } catch (e: IllegalArgumentException) {
                    LOGGER.warn("Invalid UUID: {}", skin.value)
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

    // Downloads the player texture from Mojang API
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