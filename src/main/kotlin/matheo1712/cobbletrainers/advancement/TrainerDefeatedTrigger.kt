package matheo1712.cobbletrainers.advancement

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerProgress
import net.minecraft.advancements.critereon.ContextAwarePredicate
import net.minecraft.advancements.critereon.EntityPredicate
import net.minecraft.advancements.critereon.SimpleCriterionTrigger
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.Optional

/**
 * The advancement criterion fired when a player beats a trainer:
 * `cobblemon-trainers:trainer_defeated`.
 *
 * Handing datapacks a trigger rather than a `grants` field in the trainer file is what keeps
 * the mod out of the advancement business: title, icon, tree, toast, rewards and the recipe
 * unlocks that come with them are all things Minecraft already does, and an advancement
 * written against this trigger is an ordinary advancement in every other respect. It also
 * closes the loop with `requires.advancement`, which gates a trainer on one.
 *
 * Conditions, all optional and all of which must match:
 * - `trainer`: the trainer that was beaten;
 * - `category`: the category it is filed under;
 * - `pack`: the datapack namespace it comes from;
 * - `count`: how many *different* trainers matching the above the player has beaten, which is
 *   what turns "beat a champion" into "beat the eight champions" without writing eight
 *   criteria.
 *
 * `trainer` and `category` accept a full ID (`mon_pack:champions/erika`) or a bare path
 * (`champions/erika`), which then matches whatever namespace it comes from.
 */
object TrainerDefeatedTrigger : SimpleCriterionTrigger<TrainerDefeatedTrigger.Instance>() {

    /** Registered from the mod initializer, before any datapack advancement is parsed. */
    fun register() {
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, ID, this)
    }

    val ID: ResourceLocation = CobblemonTrainers.id("trainer_defeated")

    override fun codec(): Codec<Instance> = Instance.CODEC

    /**
     * Called once the victory has been written to [TrainerProgress] - a `count` condition reads
     * that record, so the trainer just beaten has to be part of it already.
     */
    fun trigger(player: ServerPlayer, trainerId: ResourceLocation) {
        trigger(player) { instance -> instance.matches(player, trainerId) }
    }

    class Instance(
        private val playerPredicate: Optional<ContextAwarePredicate>,
        val trainer: Optional<String>,
        val category: Optional<String>,
        val pack: Optional<String>,
        val count: Optional<Int>
    ) : SimpleCriterionTrigger.SimpleInstance {

        override fun player(): Optional<ContextAwarePredicate> = playerPredicate

        fun matches(player: ServerPlayer, defeated: ResourceLocation): Boolean {
            if (!matchesFilters(defeated)) return false

            val required = count.orElse(0)
            if (required != null) {
                if (required <= 1) return true
            }

            // Counted from the progress file rather than from a counter of our own: it is the
            // record that survives a restart, and it is what the player would call their score.
            val beaten = TrainerProgress.of(player.server)
                .defeatedTrainersOf(player.uuid)
                .count { matchesFilters(it) }
            return beaten >= required
        }

        /** Whether one trainer ID is one this criterion is watching. */
        private fun matchesFilters(id: ResourceLocation): Boolean {
            if (pack.isPresent && pack.get() != id.namespace) return false
            if (trainer.isPresent && !matchesId(trainer.get(), id)) return false
            if (category.isPresent) {
                val actual = TrainerRegistry.categoryOf(id) ?: return false
                if (!matchesId(category.get(), actual)) return false
            }
            return true
        }

        /** A pattern with a namespace is the whole ID; without one it is just the path. */
        private fun matchesId(pattern: String, id: ResourceLocation): Boolean =
            if (pattern.contains(':')) pattern == id.toString() else pattern == id.path

        companion object {
            val CODEC: Codec<Instance> = RecordCodecBuilder.create { instance ->
                instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")
                        .forGetter(Instance::player),
                    Codec.STRING.optionalFieldOf("trainer").forGetter(Instance::trainer),
                    Codec.STRING.optionalFieldOf("category").forGetter(Instance::category),
                    Codec.STRING.optionalFieldOf("pack").forGetter(Instance::pack),
                    Codec.INT.optionalFieldOf("count").forGetter(Instance::count)
                ).apply(instance, ::Instance)
            }
        }
    }
}
