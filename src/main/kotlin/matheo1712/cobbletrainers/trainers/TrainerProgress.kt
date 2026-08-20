package matheo1712.cobbletrainers.trainers

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Remembers which players have already defeated which trainers, so a trainer can refuse a
 * rematch ([TrainerProgressRules.rematch]) and hand out its rewards only once
 * ([TrainerProgressRules.rewards]).
 *
 * Trainers are keyed by their **datapack ID**, not by the UUID of the spawned entity: beating
 * `mon_pack:champion` beats that trainer, wherever and however many times it stands in the
 * world. Killing one and spawning it again therefore changes nothing.
 *
 * This is world state rather than datapack state, so it lives in a [net.minecraft.world.level.saveddata.SavedData] file
 * (`data/cobblemon_trainers_progress.dat` of the overworld) instead of the datapack-fed
 * [matheo1712.cobbletrainers.registry.TrainerRegistry]: `/reload` must not wipe what players have already achieved.
 */
class TrainerProgress : SavedData() {

    /** Trainer ID -> UUIDs of the players who have beaten it. */
    private val defeatedBy = mutableMapOf<ResourceLocation, MutableSet<UUID>>()

    fun hasDefeated(trainer: ResourceLocation, player: UUID): Boolean =
        defeatedBy[trainer]?.contains(player) == true

    /**
     * Every trainer that player has beaten, including the ones no datapack declares any more.
     * This is what a counting requirement or advancement criterion is scored on.
     */
    fun defeatedTrainersOf(player: UUID): Set<ResourceLocation> =
        defeatedBy.filterValues { player in it }.keys

    /**
     * Records a victory.
     * @return true when this was the player's first win over that trainer.
     */
    fun recordVictory(trainer: ResourceLocation, player: UUID): Boolean {
        val firstWin = defeatedBy.getOrPut(trainer) { mutableSetOf() }.add(player)
        if (firstWin) setDirty()
        return firstWin
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val entries = CompoundTag()
        defeatedBy.forEach { (trainer, players) ->
            if (players.isEmpty()) return@forEach
            val list = ListTag()
            players.forEach { list.add(StringTag.valueOf(it.toString())) }
            entries.put(trainer.toString(), list)
        }
        tag.put(DEFEATED_KEY, entries)
        return tag
    }

    companion object {
        /** Name of the file under the overworld's `data/` directory. */
        private const val FILE_NAME = "cobblemon_trainers_progress"

        private const val DEFEATED_KEY = "defeated"

        /**
         * The data fixer type is only consulted when the saved version is older than the
         * current one, so any non-null value is a no-op here - but null is not allowed, the
         * loader dereferences it as soon as the file exists.
         */
        private val FACTORY = SavedData.Factory(
            { TrainerProgress() },
            { tag, _ -> load(tag) },
            DataFixTypes.LEVEL
        )

        fun of(server: MinecraftServer): TrainerProgress =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, FILE_NAME)

        private fun load(tag: CompoundTag): TrainerProgress {
            val progress = TrainerProgress()
            val entries = tag.getCompound(DEFEATED_KEY)

            for (key in entries.allKeys) {
                // A trainer that no longer exists in any datapack keeps its entry: the pack may
                // simply be disabled for now, and dropping it would silently reset every player.
                val trainer = ResourceLocation.tryParse(key)
                if (trainer == null) {
                    CobblemonTrainers.LOGGER.warn("Ignoring invalid trainer ID in progress data: {}", key)
                    continue
                }

                val players = entries.getList(key, Tag.TAG_STRING.toInt())
                    .mapNotNull { parseUuid(it.asString) }
                if (players.isNotEmpty()) progress.defeatedBy[trainer] = players.toMutableSet()
            }

            return progress
        }

        /** A UUID that no longer parses means a corrupted file: skip it rather than fail the load. */
        private fun parseUuid(raw: String): UUID? =
            try {
                UUID.fromString(raw)
            } catch (e: IllegalArgumentException) {
                CobblemonTrainers.LOGGER.warn("Ignoring invalid UUID in trainer progress: {}", raw)
                null
            }
    }
}