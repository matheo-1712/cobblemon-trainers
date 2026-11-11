package matheo1712.cobbletrainers.advancement

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.platform.Platform
import net.minecraft.advancements.critereon.ContextAwarePredicate
import net.minecraft.advancements.critereon.EntityPredicate
import net.minecraft.advancements.critereon.SimpleCriterionTrigger
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.Optional

/**
 * The advancement criterion fired when a player finishes a league:
 * `cobblemon-trainers:league_completed`.
 *
 * The sibling of [TrainerDefeatedTrigger], and kept separate from it for the reason the
 * completion is recorded separately: beating every member of a league is not finishing it.
 *
 * Conditions, both optional and both of which must match:
 * - `league`: the league that was finished, as a full ID (`mon_pack:conseil`) or a bare path;
 * - `pack`: the datapack namespace it comes from.
 */
object LeagueCompletedTrigger : SimpleCriterionTrigger<LeagueCompletedTrigger.Instance>() {

    val ID: ResourceLocation = CobblemonTrainers.id("league_completed")

    /** Registered from the mod initializer, before any datapack advancement is parsed. */
    fun register() {
        Platform.current.register(BuiltInRegistries.TRIGGER_TYPES, ID, this)
    }

    override fun codec(): Codec<Instance> = Instance.CODEC

    fun trigger(player: ServerPlayer, leagueId: ResourceLocation) {
        trigger(player) { instance -> instance.matches(leagueId) }
    }

    class Instance(
        private val playerPredicate: Optional<ContextAwarePredicate>,
        val league: Optional<String>,
        val pack: Optional<String>
    ) : SimpleCriterionTrigger.SimpleInstance {

        override fun player(): Optional<ContextAwarePredicate> = playerPredicate

        fun matches(completed: ResourceLocation): Boolean {
            if (pack.isPresent && pack.get() != completed.namespace) return false
            if (league.isPresent) {
                val pattern = league.get()
                // A pattern with a namespace is the whole ID; without one it is just the path.
                val matches = if (pattern.contains(':')) pattern == completed.toString() else pattern == completed.path
                if (!matches) return false
            }
            return true
        }

        companion object {
            val CODEC: Codec<Instance> = RecordCodecBuilder.create { instance ->
                instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")
                        .forGetter(Instance::player),
                    Codec.STRING.optionalFieldOf("league").forGetter(Instance::league),
                    Codec.STRING.optionalFieldOf("pack").forGetter(Instance::pack)
                ).apply(instance, ::Instance)
            }
        }
    }
}
