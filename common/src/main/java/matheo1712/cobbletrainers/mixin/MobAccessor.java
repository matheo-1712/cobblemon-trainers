package matheo1712.cobbletrainers.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Opens {@link Mob#goalSelector}, which is {@code protected} and therefore out of reach of any
 * class that is not a mob itself.
 *
 * The mod needs it for one thing: giving a trainer a {@code LookAtPlayerGoal} so they turn to
 * face a player who comes near. Cobblemon drives its NPCs through the Brain system rather than
 * goals, but {@code NPCEntity} only overrides {@code customServerAiStep}, so vanilla's
 * {@code serverAiStep} - and with it the goal selector and the look control - still runs.
 *
 * The name is prefixed rather than {@code getGoalSelector} on purpose: an accessor is injected
 * into {@code Mob} itself, where a plain name could collide with another mod doing the same.
 */
@Mixin(Mob.class)
public interface MobAccessor {

    @Accessor("goalSelector")
    GoalSelector cobbletrainersGoalSelector();
}
