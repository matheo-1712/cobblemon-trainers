package matheo1712.cobbletrainers

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType

/**
 * Reads a skin image shipped under `assets/` as raw bytes, from the logical server.
 *
 * The bytes are handed to `NPCEntity.NPC_PLAYER_TEXTURE`, an entity data field: the image
 * travels to the client with the entity, exactly like a skin downloaded from Mojang. So the
 * texture only has to be readable *server-side*, and a pack installed on a server alone still
 * dresses its trainers on every client.
 *
 * That is also why no resource manager is involved. A server has one for `data/` only, and a
 * client one would not exist at all on a dedicated server. Two places are searched instead:
 *
 * 1. **The classpath**, which holds the assets of the mod itself and of everything Fabric
 *    loaded as a mod — including a pack built as a `.jar` with a `fabric.mod.json`.
 * 2. **The packs of `mods/`** that Fabric skipped, read straight off the disk by
 *    [ModsFolderPackSource.readResource].
 *
 * A pack living in `datapacks/` or `resourcepacks/` is out of reach: neither is read by the
 * side that needs the bytes. Ship the texture through `mods/`.
 */
object TrainerTextures {

    /** Returns the file content, or null when no loaded pack holds it. */
    fun read(location: ResourceLocation): ByteArray? =
        readFromClasspath(location)
            ?: ModsFolderPackSource.readResource(PackType.CLIENT_RESOURCES, location)

    private fun readFromClasspath(location: ResourceLocation): ByteArray? =
        try {
            javaClass.classLoader
                .getResourceAsStream("assets/${location.namespace}/${location.path}")
                ?.use { it.readBytes() }
        } catch (e: Exception) {
            CobblemonTrainers.LOGGER.warn("Failed to read {} from the classpath: {}", location, e.message)
            null
        }
}
