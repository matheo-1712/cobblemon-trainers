package matheo1712.cobbletrainers.league

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.phys.Vec3

/**
 * Where the trainers of each league stand, as an operator set it with
 * `/cobblemontrainers league <id> arena`.
 *
 * A place in a world is world state, not datapack state: the same pack is installed in worlds
 * that share no coordinate. So it lives in a [SavedData] file of the overworld
 * (`data/cobblemon_trainers_leagues.dat`), like the progress, and not in the league's JSON -
 * which `/reload` rereads and which knows nothing of the world it is loaded into.
 *
 * Keyed by **league ID**, so a league whose pack is disabled for a while keeps its arena.
 * A league with no entry spawns its trainers beside the player, as it always did.
 */
class LeagueArenas : SavedData() {

    /** One arena: a point, and the dimension it is a point of. */
    class Arena(val dimension: ResourceKey<Level>, val position: Vec3)

    private val arenas = mutableMapOf<ResourceLocation, Arena>()

    fun get(league: ResourceLocation): Arena? = arenas[league]

    fun set(league: ResourceLocation, arena: Arena) {
        arenas[league] = arena
        setDirty()
    }

    /** @return true when there was an arena to forget. */
    fun clear(league: ResourceLocation): Boolean {
        if (arenas.remove(league) == null) return false
        setDirty()
        return true
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        arenas.forEach { (id, arena) ->
            val entry = CompoundTag()
            entry.putString(DIMENSION_KEY, arena.dimension.location().toString())
            entry.putDouble(X_KEY, arena.position.x)
            entry.putDouble(Y_KEY, arena.position.y)
            entry.putDouble(Z_KEY, arena.position.z)
            tag.put(id.toString(), entry)
        }
        return tag
    }

    companion object {
        /** Name of the file under the overworld's `data/` directory. */
        private const val FILE_NAME = "cobblemon_trainers_leagues"

        private const val DIMENSION_KEY = "dimension"
        private const val X_KEY = "x"
        private const val Y_KEY = "y"
        private const val Z_KEY = "z"

        /** Any non-null fixer type will do, null will not: see [matheo1712.cobbletrainers.trainers.TrainerProgress]. */
        private val FACTORY = Factory(
            { LeagueArenas() },
            { tag, _ -> load(tag) },
            DataFixTypes.LEVEL
        )

        fun of(server: MinecraftServer): LeagueArenas =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, FILE_NAME)

        private fun load(tag: CompoundTag): LeagueArenas {
            val arenas = LeagueArenas()
            for (key in tag.allKeys) {
                val entry = tag.getCompound(key)
                val league = ResourceLocation.tryParse(key)
                val dimension = ResourceLocation.tryParse(entry.getString(DIMENSION_KEY))
                if (league == null || dimension == null) {
                    CobblemonTrainers.LOGGER.warn("Ignoring invalid league arena in saved data: {}", key)
                    continue
                }
                arenas.arenas[league] = Arena(
                    ResourceKey.create(Registries.DIMENSION, dimension),
                    Vec3(entry.getDouble(X_KEY), entry.getDouble(Y_KEY), entry.getDouble(Z_KEY))
                )
            }
            return arenas
        }
    }
}
