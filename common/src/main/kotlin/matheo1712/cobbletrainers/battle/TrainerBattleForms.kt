package matheo1712.cobbletrainers.battle

import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeature
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.nbt.CompoundTag

/**
 * Hands a trainer's team back in the forms it walked into the battle with.
 *
 * A battle form is a species feature flipped mid-fight - a Mega Evolution sets Mega Showdown's
 * `mega_evolution` to `mega` - and it is flipped on the trainer's **real** Pokémon, not on the
 * copies a `_50` format battles with. Whoever flips it puts it back for a player's team when the
 * battle ends, and for anyone's Pokémon when it faints. A trainer who *wins* therefore kept the
 * form: their next battle opened with it already worn, and with no Mega Evolution left to make.
 *
 * So the features are noted when the battle opens and written back when it ends, however it
 * ends. Only the features: HP, PP and status belong to `battle.healParty`, and restoring the whole
 * Pokémon would undo the damage a `healParty: false` trainer is meant to carry over.
 *
 * Nothing here names a gimmick or a mod - any feature a battle changes is put back, which is also
 * what makes the next one a fair copy of the first.
 */
object TrainerBattleForms {

    /** The features of each Pokémon of [npc]'s team as they stand, by feature name. */
    class Snapshot internal constructor(internal val teams: List<Pair<Pokemon, Map<String, CompoundTag>>>)

    fun snapshot(npc: NPCEntity): Snapshot =
        Snapshot(npc.party?.map { it to Features.capture(it.features, SPECIES_FEATURES) } ?: emptyList())

    /** Writes [snapshot] back, re-deriving aspects and form only for a Pokémon that changed. */
    fun restore(snapshot: Snapshot) {
        snapshot.teams.forEach { (pokemon, before) ->
            val changes = Features.restore(pokemon.features, before, SPECIES_FEATURES)
            if (!changes.any) return@forEach
            // The client is told feature by feature, then the aspects - and the form they pick -
            // are worked out again from the features, as Cobblemon does when one is set. A dropped
            // feature has nothing to sync on its own: the aspects it gave are what goes.
            changes.rewritten.forEach { pokemon.markFeatureDirty(it) }
            pokemon.updateAspects()
            pokemon.updateForm()
        }
    }

    private val SPECIES_FEATURES = Features.Access<SpeciesFeature>(
        name = { it.name },
        save = { it.saveToNBT(CompoundTag()) },
        load = { feature, tag -> feature.loadFromNBT(tag) }
    )

    /**
     * The snapshot and its restoration, over any feature type. Cobblemon's [SpeciesFeature] is the
     * only one the mod uses; the indirection is what lets the rule be tested without Cobblemon on
     * the test classpath, where it never is.
     */
    internal object Features {
        class Access<F>(val name: (F) -> String, val save: (F) -> CompoundTag, val load: (F, CompoundTag) -> Unit)

        fun <F> capture(features: List<F>, access: Access<F>): Map<String, CompoundTag> =
            features.associate { access.name(it) to access.save(it) }

        /** What [restore] did: the features it rewrote, and whether it dropped any. */
        class Changes<F>(val rewritten: List<F>, val dropped: Boolean) {
            val any: Boolean get() = dropped || rewritten.isNotEmpty()
        }

        /**
         * Puts [features] back the way [before] had them.
         *
         * A feature the battle added is dropped: it was not there when the battle opened, so it
         * is not part of what the trainer fights with. That is the Mega Evolution's case - a
         * trainer's Pokémon is created without `mega_evolution`, which Mega Showdown adds when it
         * evolves - so a drop alone is a change, and the form has to be worked out again for it.
         * One the battle removed cannot be rebuilt from its saved value alone - its provider is not
         * ours to call - and stays out; no battle form works that way.
         */
        fun <F> restore(features: MutableList<F>, before: Map<String, CompoundTag>, access: Access<F>): Changes<F> {
            val dropped = features.removeIf { access.name(it) !in before }
            val rewritten = features.filter { feature ->
                val saved = before.getValue(access.name(feature))
                if (access.save(feature) == saved) return@filter false
                access.load(feature, saved)
                true
            }
            return Changes(rewritten, dropped)
        }
    }
}
