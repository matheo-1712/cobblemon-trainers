package matheo1712.cobbletrainers.trainers

import com.cobblemon.mod.common.entity.npc.NPCPlayerModelType
import com.cobblemon.mod.common.entity.npc.NPCPlayerTexture
import com.mojang.authlib.GameProfile
import com.mojang.authlib.ProfileLookupCallback
import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import java.net.URI
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Turns a [TrainerSkin] declaration into the image the clients are shown.
 *
 * Two things need that image and neither can block: [TrainerSpawner], which dresses a freshly
 * spawned NPC, and the battle phone, which draws the trainer in a screen. Both go through
 * [resolveAsync] — a profile lookup, a download from Mojang or a pack read all happen on a
 * daemon thread, never on the server thread.
 *
 * Results are cached, failures included, because resolving the same trainer over and over
 * would mean one Mojang round trip per spawn and one per screen row. The cache is keyed on the
 * skin declaration rather than on the trainer, so two trainers wearing the same skin share one
 * entry. [clearCache] is called on `/reload`: that is where a pack may have changed the image
 * under a name we already resolved.
 */
object TrainerSkins {

    private val LOGGER = CobblemonTrainers.LOGGER

    /**
     * Two threads, not one: a Mojang lookup can hang for a while, and a single worker would
     * make every other trainer wait behind it. Not more either — the cache means the queue is
     * short-lived, and each task is mostly waiting on the network.
     */
    private val WORKERS: ExecutorService = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "CobblemonTrainers-Skins").apply { isDaemon = true }
    }

    private val cache = ConcurrentHashMap<TrainerSkin, Optional<NPCPlayerTexture>>()

    /**
     * Resolves a skin off the server thread and hands the result to [consumer], which runs on
     * the worker thread — anything touching the world has to hop back through
     * [MinecraftServer.execute] itself.
     *
     * @param consumer called exactly once, with null when the skin could not be resolved.
     */
    fun resolveAsync(server: MinecraftServer, skin: TrainerSkin, consumer: (NPCPlayerTexture?) -> Unit) {
        cache[skin]?.let {
            consumer(it.orElse(null))
            return
        }

        WORKERS.execute {
            val texture = try {
                cache.computeIfAbsent(skin) { Optional.ofNullable(resolve(server, it)) }.orElse(null)
            } catch (e: Exception) {
                LOGGER.warn("Failed to resolve skin '{}': {}", skin.value, e.message)
                null
            }
            consumer(texture)
        }
    }

    /** Forgets every resolved skin, so the next request reads the packs again. */
    fun clearCache() {
        cache.clear()
    }

    /** Turns a skin declaration into the texture that will be synced to the clients. */
    private fun resolve(server: MinecraftServer, skin: TrainerSkin): NPCPlayerTexture? =
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

    /** Downloads the player texture from the Mojang API. */
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
