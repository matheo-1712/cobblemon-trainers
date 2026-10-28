package matheo1712.cobbletrainers.mixin.client;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import matheo1712.cobbletrainers.CobblemonTrainers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Makes the rig a trainer's skin declared win on this client, the way the declared texture does.
 * <p>
 * A {@code texture} skin names its rig ({@code default} or {@code slim}) in the aspect
 * {@code trainer_rig:<rig>}. Whatever {@code model-*} aspect came with the entity, the set the
 * model is resolved from is rewritten to match, so a slim skin is never drawn on the Steve
 * geometry. Every posable entity passes through here, so the trainer aspect is tested first.
 */
@Mixin(PosableState.class)
public abstract class PosableStateMixin {

    @ModifyVariable(method = "setCurrentAspects", at = @At("HEAD"), argsOnly = true)
    private Set<String> cobblemontrainers$forceRig(Set<String> aspects) {
        if (aspects == null) {
            return null;
        }
        String rig = null;
        for (String aspect : aspects) {
            if (aspect.startsWith(CobblemonTrainers.RIG_ASPECT_PREFIX)) {
                rig = aspect.substring(CobblemonTrainers.RIG_ASPECT_PREFIX.length());
                break;
            }
        }
        if (rig == null || !(rig.equals("slim") || rig.equals("default"))) {
            return aspects;
        }
        Set<String> forced = new LinkedHashSet<>(aspects);
        forced.remove("model-default");
        forced.remove("model-slim");
        forced.add("model-" + rig);
        return forced;
    }
}
