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
 * ([TrainerReward.firstWinOnly]).
 *
 * Trainers are keyed by their **datapack ID**, not by the UUID of the spawned entity: beating
 * `mon_pack:champion` beats that trainer, wherever and however many times it stands in the
 * world. Killing one and spawning it again therefore changes nothing.
 *
 * This is world state rather than datapack state, so it lives in a [net.minecraft.world.level.saveddata.SavedData] file
 * (`data/cobblemon_trainers_progress.dat` of the overworld) instead of the datapack-fed
 * [TrainerRegistry]: `/reload` must not wipe what players have already achieved.
 */
class TrainerProgress : SavedData() {

    /** Trainer ID -> UUIDs of the players who have beaten it. */
    private val defeatedBy = mutableMapOf<ResourceLocation, MutableSet<UUID>>()

    /** League ID -> UUIDs of the players who have finished it. */
    private val completedBy = mutableMapOf<ResourceLocation, MutableSet<UUID>>()

    fun hasDefeated(trainer: ResourceLocation, player: UUID): Boolean =
        defeatedBy[trainer]?.contains(player) == true

    /**
     * Every trainer that player has beaten, including the ones no datapack declares any more.
     * This is what a counting requirement or advancement criterion is scored on.
     */
    fun defeatedTrainersOf(player: UUID): Set<ResourceLocation> =
        defeatedBy.entries.asSequence().filter { player in it.value }.map { it.key }.toSet()

    /**
     * Records a victory.
     * @return true when this was the player's first win over that trainer.
     */
    fun recordVictory(trainer: ResourceLocation, player: UUID): Boolean {
        val firstWin = defeatedBy.getOrPut(trainer) { mutableSetOf() }.add(player)
        if (firstWin) setDirty()
        return firstWin
    }

    /**
     * Takes a victory back, for `/defeattrainer … reset`. A trainer nobody has beaten any more
     * drops out of the map rather than keeping an empty set around.
     *
     * @return true when there was something to forget.
     */
    fun forgetVictory(trainer: ResourceLocation, player: UUID): Boolean {
        val players = defeatedBy[trainer] ?: return false
        if (!players.remove(player)) return false
        if (players.isEmpty()) defeatedBy.remove(trainer)
        setDirty()
        return true
    }

    fun hasCompletedLeague(league: ResourceLocation, player: UUID): Boolean =
        completedBy[league]?.contains(player) == true

    /**
     * Records a league seen through to its last trainer. Kept beside the victories rather than
     * derived from them: beating every member one by one, healed in between, is not finishing
     * the league.
     *
     * @return true when this was the player's first time through that league.
     */
    fun recordLeague(league: ResourceLocation, player: UUID): Boolean {
        val first = completedBy.getOrPut(league) { mutableSetOf() }.add(player)
        if (first) setDirty()
        return first
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.put(DEFEATED_KEY, write(defeatedBy))
        tag.put(LEAGUES_KEY, write(completedBy))
        return tag
    }

    private fun write(records: Map<ResourceLocation, Set<UUID>>): CompoundTag {
        val entries = CompoundTag()
        records.forEach { (id, players) ->
            if (players.isEmpty()) return@forEach
            val list = ListTag()
            players.forEach { list.add(StringTag.valueOf(it.toString())) }
            entries.put(id.toString(), list)
        }
        return entries
    }

    companion object {
        /** Name of the file under the overworld's `data/` directory. */
        private const val FILE_NAME = "cobblemon_trainers_progress"

        private const val DEFEATED_KEY = "defeated"
        private const val LEAGUES_KEY = "leagues"

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

        internal fun load(tag: CompoundTag): TrainerProgress {
            val progress = TrainerProgress()
            read(tag.getCompound(DEFEATED_KEY), progress.defeatedBy)
            // Absent from a file written before leagues existed, which reads as an empty one.
            read(tag.getCompound(LEAGUES_KEY), progress.completedBy)
            return progress
        }

        private fun read(entries: CompoundTag, into: MutableMap<ResourceLocation, MutableSet<UUID>>) {
            for (key in entries.allKeys) {
                // A trainer that no longer exists in any datapack keeps its entry: the pack may
                // simply be disabled for now, and dropping it would silently reset every player.
                val id = ResourceLocation.tryParse(key)
                if (id == null) {
                    CobblemonTrainers.LOGGER.warn("Ignoring invalid ID in progress data: {}", key)
                    continue
                }

                val players = entries.getList(key, Tag.TAG_STRING.toInt())
                    .mapNotNull { parseUuid(it.asString) }
                if (players.isNotEmpty()) into[id] = players.toMutableSet()
            }
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
