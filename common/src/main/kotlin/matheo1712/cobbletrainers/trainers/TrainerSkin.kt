package matheo1712.cobbletrainers.trainers

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.resources.ResourceLocation

/**
 * Skin configuration of a trainer.
 *
 * Supported types:
 * - `"player_username"`: uses the skin of the Minecraft player with that username.
 * - `"player_uuid"`: uses the skin of the Minecraft player with that UUID.
 * - `"texture"`: uses a player skin image shipped in a pack, under
 *   `assets/<namespace>/<path>` - see [TrainerTextures].
 * - `"model"`: selects a Cobblemon NPC variation resolver from the client's resource packs.
 *
 * @param type The skin type.
 * @param value A username, a UUID, a PNG resource location, or an NPC variation resolver ID
 *   for the `model` type (for example `my_pack:explorer`).
 * @param model Which player rig wears the texture: `default` (Steve) or `slim` (Alex). Only
 *   read for the `texture` type - Mojang states the model of a player skin itself.
 * @param aspects Optional model variants. Only read for `model`; internal trainer tags cannot
 *   be supplied here.
 */
data class TrainerSkin(
    val type: String = "player_username",
    val value: String = "Steve",
    val model: String = "default",
    val aspects: List<String> = emptyList()
) {
    /**
     * The PNG this skin declares, for a `texture` type and nothing else.
     *
     * The bytes are what dresses the trainer (see [TrainerTextures]); this is the *name* beside
     * them, which a client may use to prefer the same file out of its own resource packs. The
     * other types declare no path, so there is nothing of theirs to override.
     */
    fun textureId(): ResourceLocation? =
        if (type.equals("texture", ignoreCase = true)) ResourceLocation.tryParse(value) else null

    /** The resolver's name, not the geometry file or an NPC class. */
    fun modelId(): ResourceLocation? =
        if (type.equals("model", ignoreCase = true) && ':' in value) ResourceLocation.tryParse(value)
            ?.takeIf { it.namespace.isNotEmpty() && it.path.isNotEmpty() && !value.startsWith(':') }
        else null

    fun modelAspects(): Set<String> =
        if (modelId() == null) emptySet() else aspects.filter { ASPECT.matches(it) }.toSet()

    fun validate(id: ResourceLocation) {
        if (!type.equals("model", ignoreCase = true)) return
        if (modelId() == null) {
            CobblemonTrainers.LOGGER.warn(
                "Trainer {}: skin model '{}' must be a namespaced NPC resolver ID; using the default skin.",
                id, value
            )
        }
        for (aspect in aspects) {
            if (!ASPECT.matches(aspect)) {
                CobblemonTrainers.LOGGER.warn(
                    "Trainer {}: ignoring skin aspect '{}'; use lowercase letters, digits, underscores or hyphens.",
                    id, aspect
                )
            }
        }
    }

    companion object {
        // Colons are deliberately excluded: trainer_id:, trainer_spawner: and trinket tags
        // belong to the mod, never to a model's visual variants.
        private val ASPECT = Regex("[a-z0-9][a-z0-9_-]*")
    }
}
