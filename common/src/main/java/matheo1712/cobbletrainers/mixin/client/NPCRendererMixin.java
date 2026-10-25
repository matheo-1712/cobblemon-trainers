package matheo1712.cobbletrainers.mixin.client;

import com.cobblemon.mod.common.client.render.npc.NPCRenderer;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import matheo1712.cobbletrainers.CobblemonTrainers;
import matheo1712.cobbletrainers.client.cache.TrainerSkinOverrides;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Lets a resource pack on this client win over the skin image the server sent.
 * <p>
 * A {@code texture} skin arrives as the bytes of its PNG, pushed through
 * {@code NPC_PLAYER_TEXTURE}, which is what dresses the trainers of a client holding no pack at
 * all. Beside those bytes travels the path the pack declared, and when this client has that
 * same path in its own resource packs, that file is drawn instead - see
 * {@link TrainerSkinOverrides}.
 * <p>
 * This method is the one seam that needs: {@code NPCRenderer.scale} calls it again and puts the
 * answer in the render context, so a single reply covers the whole draw. Returning a plain
 * resource location is enough - the texture manager makes a {@code SimpleTexture} for a path it
 * finds in the resource manager.
 * <p>
 * The target is a Cobblemon method, but its descriptor names {@link ResourceLocation}, which
 * Loom does remap: after a change, check in the built jar that the {@code @Inject} still points
 * at the method of Cobblemon's own jar. Every NPC in the game passes through here, so the
 * trainer aspect is tested before anything else happens.
 */
@Mixin(NPCRenderer.class)
public abstract class NPCRendererMixin {

    @Inject(
            method = "getTextureLocation(Lcom/cobblemon/mod/common/entity/npc/NPCEntity;)"
                    + "Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cobblemontrainers$overrideSkin(
            NPCEntity entity,
            CallbackInfoReturnable<ResourceLocation> cir
    ) {
        Set<String> aspects = entity.getAspects();
        if (!isTrainer(aspects)) {
            return;
        }
        ResourceLocation override = TrainerSkinOverrides.INSTANCE.overrideFor(aspects);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    private static boolean isTrainer(Set<String> aspects) {
        for (String aspect : aspects) {
            if (aspect.startsWith(CobblemonTrainers.TRAINER_ASPECT_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}
