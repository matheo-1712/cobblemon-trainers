package matheo1712.cobbletrainers.client.cache

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

/**
 * Lets a resource pack on this client win over the skin image the server sent.
 *
 * A `texture` skin is resolved server-side: [matheo1712.cobbletrainers.trainers.TrainerSkins]
 * reads the PNG and pushes its **bytes** through `NPC_PLAYER_TEXTURE`, which is what makes a
 * pack installed on the server alone dress the trainers of every client. That stays the
 * default, and a client with no pack of its own sees exactly what it saw before.
 *
 * What travels beside those bytes is the **path the pack declared**, as the aspect
 * [CobblemonTrainers.SKIN_ASPECT_PREFIX] on the entity and as `textureResource` in the battle
 * phone's answer. If this client holds that same path in its own resource packs, that file is
 * drawn instead. So the same `skin.value` means the same thing whether the pack sits in
 * `mods/` or in `resourcepacks/` - only the folder changes.
 *
 * Only `texture` skins carry a path, so they are the only ones that can be overridden; a
 * `player_username` trainer declares none, and a `model` trainer is already drawn entirely
 * from client resource packs.
 *
 * The answer is remembered because it is asked once a frame per trainer on screen, and
 * `ResourceManager.getResource` walks the pack stack. It is dropped on a resource reload, which
 * is the only moment it can change - see
 * [matheo1712.cobbletrainers.client.CobblemonTrainersClient].
 */
object TrainerSkinOverrides {

    /** Keyed by declared path: present means "we looked", the value means "the client has it". */
    private val present = mutableMapOf<ResourceLocation, Boolean>()

    /**
     * The path a trainer's aspects declare, or null for a trainer whose skin names none.
     *
     * One pass, like the outfit renderer's: there are a handful of aspects and this runs once a
     * frame.
     */
    fun declared(aspects: Set<String>): ResourceLocation? {
        for (aspect in aspects) {
            if (!aspect.startsWith(CobblemonTrainers.SKIN_ASPECT_PREFIX)) continue
            return ResourceLocation.tryParse(aspect.removePrefix(CobblemonTrainers.SKIN_ASPECT_PREFIX))
        }
        return null
    }

    /**
     * That path if this client can draw it, null to leave the server's bytes alone.
     *
     * @param declared What the pack wrote, from [declared] or from the battle phone's answer.
     */
    fun override(declared: ResourceLocation?): ResourceLocation? {
        if (declared == null) return null
        return declared.takeIf { exists(it) }
    }

    /** The override a trainer's aspects point at, if this client has it. */
    fun overrideFor(aspects: Set<String>): ResourceLocation? = override(declared(aspects))

    /** Forgets what the old resource packs held. */
    fun clear() {
        present.clear()
    }

    private fun exists(location: ResourceLocation): Boolean =
        present.getOrPut(location) {
            val found = Minecraft.getInstance().resourceManager.getResource(location).isPresent
            if (found) {
                CobblemonTrainers.LOGGER.debug("Resource pack overrides trainer skin {}", location)
            }
            found
        }
}
