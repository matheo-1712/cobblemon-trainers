package matheo1712.cobbletrainers.mixin.client;

import matheo1712.cobbletrainers.block.TrainerBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the trainer spawner show up like a barrier: invisible, unless its own item is held.
 * <p>
 * Vanilla does this with a marker particle rather than with any rendering. Every tick,
 * {@code ClientLevel.animateTick} asks {@code getMarkerParticleTarget()} which block the player
 * is currently holding the item of, then samples random positions nearby and drops a
 * {@code BLOCK_MARKER} particle wherever it finds that block. The particle draws the block
 * model's {@code particle} texture, which is the only texture the spawner's block model has.
 * <p>
 * The list vanilla checks - {@code MARKER_PARTICLE_ITEMS} - is a private immutable set holding
 * the barrier and the light block, so the only way in is the method that reads it. Injecting at
 * the head keeps vanilla's own answer untouched whenever the held item is not ours.
 * <p>
 * The markers are gated on {@link net.minecraft.world.entity.player.Player#canUseGameMasterBlocks()}
 * - creative plus operator - rather than on creative alone as vanilla does for the barrier.
 * Nothing about a spawner is meant to be visible to a player who cannot configure one, and an
 * item handed to them by mistake must not turn into a map of every spawner around.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "getMarkerParticleTarget", at = @At("HEAD"), cancellable = true)
    private void cobblemontrainers$showTrainerSpawners(CallbackInfoReturnable<Block> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.canUseGameMasterBlocks()) {
            return;
        }
        if (player.getMainHandItem().getItem() != TrainerBlocks.TRAINER_SPAWNER_ITEM) {
            return;
        }
        cir.setReturnValue(TrainerBlocks.TRAINER_SPAWNER);
    }
}
