package matheo1712.cobbletrainers.mixin;

import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import matheo1712.cobbletrainers.battle.TrainerSendOut;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends a trainer's Pokémon out in front of the trainer. The rule is {@link TrainerSendOut}; this
 * only hooks it onto the one method every send-out path reads - {@code SwitchInstruction} in
 * singles, and the positions stored for doubles and triples.
 */
@Mixin(ActiveBattlePokemon.class)
public abstract class ActiveBattlePokemonMixin {

    @Inject(method = "getSendOutPosition", at = @At("RETURN"), cancellable = true)
    private void cobblemontrainers$sendOutInFront(CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(TrainerSendOut.INSTANCE.reposition((ActiveBattlePokemon) (Object) this, cir.getReturnValue()));
    }
}
