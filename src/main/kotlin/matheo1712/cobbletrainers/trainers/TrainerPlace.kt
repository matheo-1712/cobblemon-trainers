package matheo1712.cobbletrainers.trainers

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.level.levelgen.structure.Structure

/**
 * Reads the [TrainerLocation] block of a trainer: whether a player is standing where that
 * trainer answers, and how to word the place for one who is not.
 *
 * This is the only place that interprets a location, the way [TrainerLock] is the only one that
 * interprets a requirement - and for the same reason. A condition and its wording are declared
 * together in [checks], so the battle phone can never advertise a place other than the one
 * being tested.
 *
 * The mod names what it can. A dimension and a biome have translations; a structure ID and a
 * box of coordinates do not, and never will. That is what [TrainerLocation.label] is for.
 */
object TrainerPlace {

    /**
     * One condition of a location, carrying both what it tests and how it reads. Keeping the
     * two together is the whole point of this file.
     */
    private class Check(val label: Component, val test: (ServerLevel, BlockPos) -> Boolean)

    /**
     * How the place reads for a player who has not found it yet: the pack's own words when it
     * wrote any, otherwise every condition of the block, joined.
     *
     * **A label alone is a valid block**, and reads even though it names no condition. Saying
     * where a trainer is and coming when called are two different things: a champion waiting in
     * their gym has every reason to tell a player which town it is in, and none to walk over.
     * That is why this reads the label before asking whether the block is empty.
     */
    fun describe(location: TrainerLocation?): Component {
        if (location == null) return Component.empty()
        location.label?.takeIf { it.isNotBlank() }?.let { return Component.translatable(it) }
        if (location.isEmpty) return Component.empty()

        return join(checks(location).map { it.label })
    }

    /**
     * The conditions of [location] this position fails, in the order the pack declared them.
     * An empty list means the player is standing where the trainer answers.
     */
    fun unmet(level: ServerLevel, pos: BlockPos, location: TrainerLocation?): List<Component> {
        if (location == null || location.isEmpty) return emptyList()
        return checks(location).filterNot { it.test(level, pos) }.map { it.label }
    }

    /**
     * Every condition the block declares, each with its wording.
     *
     * A field naming an ID that does not resolve is a condition nothing can meet, deliberately:
     * counting a typo as satisfied would let the trainer be called from anywhere, which is the
     * opposite of failing loudly. It matches how [TrainerLock] treats an unknown item.
     */
    private fun checks(location: TrainerLocation): List<Check> {
        val checks = mutableListOf<Check>()

        location.dimension?.takeIf { it.isNotBlank() }?.let { raw ->
            val id = ResourceLocation.tryParse(raw)
            checks += Check(CobblemonTrainers.lang("location.dimension", dimensionName(id, raw))) { level, _ ->
                id != null && level.dimension().location() == id
            }
        }

        location.biome?.takeIf { it.isNotBlank() }?.let { checks += biomeCheck(it) }
        location.structure?.takeIf { it.isNotBlank() }?.let { checks += structureCheck(it) }

        location.area?.takeIf { it.isValid }?.let { area ->
            checks += Check(
                CobblemonTrainers.lang(
                    "location.area",
                    minOf(area.from[0], area.to[0]), minOf(area.from[1], area.to[1]),
                    maxOf(area.from[0], area.to[0]), maxOf(area.from[1], area.to[1])
                )
            ) { _, pos -> area.contains(pos.x, pos.z) }
        }

        altitudeCheck(location)?.let { checks += it }
        timeCheck(location)?.let { checks += it }
        weatherCheck(location)?.let { checks += it }

        return checks
    }

    /** A biome, or every biome of a tag when the field opens with the tag prefix. */
    private fun biomeCheck(raw: String): Check {
        if (raw.startsWith(TrainerLocation.TAG_PREFIX)) {
            val tag = ResourceLocation.tryParse(raw.substring(1))
                ?.let { TagKey.create(Registries.BIOME, it) }
            return Check(CobblemonTrainers.lang("location.biome_tag", Component.literal(raw))) { level, pos ->
                tag != null && level.getBiome(pos).let { biome -> biome.tags().anyMatch { it == tag } }
            }
        }

        val id = ResourceLocation.tryParse(raw)
        val name = id?.let { Component.translatable("biome.${it.namespace}.${it.path}") }
            ?: Component.literal(raw)
        return Check(CobblemonTrainers.lang("location.biome", name)) { level, pos ->
            id != null && level.getBiome(pos).unwrapKey().map { it.location() == id }.orElse(false)
        }
    }

    /**
     * A structure, or every structure of a tag.
     *
     * The test is a piece of the structure, not its bounding box: a village's box covers a good
     * deal of the land around it, and "in a village" has to mean standing on one.
     */
    private fun structureCheck(raw: String): Check {
        val label = CobblemonTrainers.lang("location.structure", Component.literal(raw))

        if (raw.startsWith(TrainerLocation.TAG_PREFIX)) {
            val tag = ResourceLocation.tryParse(raw.substring(1))
                ?.let { TagKey.create(Registries.STRUCTURE, it) }
            return Check(label) { level, pos ->
                tag != null && level.structureManager().getStructureWithPieceAt(pos, tag).isValid
            }
        }

        val id = ResourceLocation.tryParse(raw)
        return Check(label) { level, pos ->
            val structure = id?.let { resolveStructure(level, it) }
            structure != null && level.structureManager().getStructureWithPieceAt(pos, structure).isValid
        }
    }

    private fun resolveStructure(level: ServerLevel, id: ResourceLocation): Structure? =
        level.registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(ResourceKey.create(Registries.STRUCTURE, id))

    /** The three shapes an altitude range takes, so only the bound a pack declared is shown. */
    private fun altitudeCheck(location: TrainerLocation): Check? {
        val min = location.minY
        val max = location.maxY
        return when {
            min != null && max != null ->
                Check(CobblemonTrainers.lang("location.altitude", min, max)) { _, pos -> pos.y in min..max }
            min != null ->
                Check(CobblemonTrainers.lang("location.altitude_above", min)) { _, pos -> pos.y >= min }
            max != null ->
                Check(CobblemonTrainers.lang("location.altitude_below", max)) { _, pos -> pos.y <= max }
            else -> null
        }
    }

    /** An unknown word is skipped here; it was already reported by [TrainerLocation.validate]. */
    private fun timeCheck(location: TrainerLocation): Check? =
        when (location.time?.takeIf { it.isNotBlank() }?.lowercase()) {
            TrainerLocation.TIME_DAY ->
                Check(CobblemonTrainers.lang("location.time.day")) { level, _ -> level.isDay }
            TrainerLocation.TIME_NIGHT ->
                Check(CobblemonTrainers.lang("location.time.night")) { level, _ -> level.isNight }
            else -> null
        }

    /**
     * Weather is read at the position rather than on the level: rain does not fall in a desert,
     * and a player standing in one is not in the rain whatever the sky is doing elsewhere.
     */
    private fun weatherCheck(location: TrainerLocation): Check? =
        when (location.weather?.takeIf { it.isNotBlank() }?.lowercase()) {
            TrainerLocation.WEATHER_CLEAR ->
                Check(CobblemonTrainers.lang("location.weather.clear")) { level, pos -> !level.isRainingAt(pos) }
            TrainerLocation.WEATHER_RAIN ->
                Check(CobblemonTrainers.lang("location.weather.rain")) { level, pos ->
                    level.isRainingAt(pos) && !level.isThundering
                }
            TrainerLocation.WEATHER_THUNDER ->
                Check(CobblemonTrainers.lang("location.weather.thunder")) { level, pos ->
                    level.isThundering && level.isRainingAt(pos)
                }
            else -> null
        }

    /**
     * Vanilla names its dimensions nowhere a server can read, so the mod's own language files
     * carry the three it ships with. Anything else reads as its ID, which is why a pack adding
     * a dimension of its own is better off writing a [TrainerLocation.label].
     */
    private fun dimensionName(id: ResourceLocation?, raw: String): Component =
        id?.let { Component.translatable("dimension.${it.namespace}.${it.path}") } ?: Component.literal(raw)

    /** Conditions add up, so the place reads as one list rather than as a sentence. */
    private fun join(parts: List<Component>): Component {
        if (parts.isEmpty()) return Component.empty()

        val joined = parts.first().copy()
        parts.drop(1).forEach { part ->
            joined.append(CobblemonTrainers.lang("location.separator"))
            joined.append(part)
        }
        return joined
    }
}
