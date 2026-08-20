package matheo1712.cobbletrainers.client.cache

import com.mojang.blaze3d.platform.NativeImage
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.network.RequestTrainerSkinPayload
import matheo1712.cobbletrainers.network.TrainerSkinPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation

/**
 * The trainer skins the battle phone has been sent, kept as textures the screen can draw.
 *
 * Skins are asked for one at a time, when a row first needs one: a world may hold a hundred
 * trainers and each image is a few kilobytes, so sending them all with the listing would mean
 * a heavy packet for the handful the player actually looks at. [get] is therefore allowed to
 * answer null - it means "asked for, not here yet", and the screen draws a placeholder until
 * the answer lands.
 *
 * A skin that could not be resolved server-side is cached too, as an entry with no texture:
 * without that, every frame would ask again.
 *
 * Everything here runs on the client thread, which is both where the screen renders and where
 * the network handler is dispatched, so no synchronisation is needed.
 */
object TrainerSkinCache {

    /**
     * @param texture Null when the trainer has no resolvable skin.
     * @param slim Which player rig the image is drawn on, Alex when true.
     * @param width Size of the image, needed to blit out of it: a legacy skin is 64×32.
     */
    class Skin(val texture: ResourceLocation?, val slim: Boolean, val width: Int, val height: Int)

    private val skins = mutableMapOf<String, Skin>()
    private val pending = mutableSetOf<String>()

    /** The skin of that trainer, asking the server for it the first time around. */
    fun get(trainerId: String): Skin? {
        skins[trainerId]?.let { return it }

        if (pending.add(trainerId)) {
            ClientPlayNetworking.send(RequestTrainerSkinPayload(trainerId))
        }
        return null
    }

    /** Takes in a server answer, turning its bytes into a texture. */
    fun accept(payload: TrainerSkinPayload) {
        pending.remove(payload.trainerId)
        skins[payload.trainerId] = build(payload)
    }

    /** Drops every texture. Called when leaving a world: the next one may not have the same packs. */
    fun clear() {
        val textureManager = Minecraft.getInstance().textureManager
        skins.values.forEach { skin -> skin.texture?.let { textureManager.release(it) } }
        skins.clear()
        pending.clear()
    }

    private fun build(payload: TrainerSkinPayload): Skin {
        val slim = payload.model.equals("slim", ignoreCase = true)
        if (payload.texture.isEmpty()) return Skin(null, slim, 64, 64)

        return try {
            val image = NativeImage.read(payload.texture)
            val location = textureLocation(payload.trainerId)
            // register() replaces whatever sat under that location, so a reloaded skin simply
            // takes the place of the previous one.
            Minecraft.getInstance().textureManager.register(location, DynamicTexture(image))
            Skin(location, slim, image.width, image.height)
        } catch (e: Exception) {
            CobblemonTrainers.LOGGER.warn("Unreadable skin for trainer {}: {}", payload.trainerId, e.message)
            Skin(null, slim, 64, 64)
        }
    }

    /**
     * A texture path of our own for that trainer. The trainer ID is already made of characters
     * a [ResourceLocation] accepts, so its namespace and path only have to be joined back into
     * one path.
     */
    private fun textureLocation(trainerId: String): ResourceLocation =
        CobblemonTrainers.id("trainer_skin/" + trainerId.replace(':', '/'))
}