package matheo1712.cobbletrainers.mixin.client;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import matheo1712.cobbletrainers.client.render.TrainerOutfitRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a trainer's clothes, in the one moment they can be drawn.
 * <p>
 * Cobblemon's NPC renderer has no armour layer and no usable held-item layer, so the mod draws
 * both itself - see {@link TrainerOutfitRenderer}. What it cannot do is draw them from a render
 * layer of its own: {@code PosableEntityModel.renderToBuffer} calls
 * {@code PosableModel.setDefault()} the instant the model is drawn, which puts every bone back
 * in its rest pose, and a layer runs after that. Armour hung on those bones would stand to
 * attention while the trainer waved.
 * <p>
 * The tail of {@code PosableModel.render} is the moment before that reset. The pose stack is
 * still at the root of the model, the bones still hold the pose that was drawn, and the entity
 * is in the render context that was passed in.
 * <p>
 * The target is a Cobblemon method taking only Cobblemon and Blaze3D types, neither of which
 * Loom remaps, so the descriptor below reads the same in a development run and in a built jar.
 * Every {@code PosableModel} in the game passes through here, Pokémon included, hence the
 * instance check before anything else happens.
 */
@Mixin(PosableModel.class)
public abstract class PosableModelMixin {

    @Inject(
            method = "render(Lcom/cobblemon/mod/common/client/render/models/blockbench/repository/RenderContext;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("TAIL")
    )
    private void cobblemontrainers$renderOutfit(
            RenderContext context,
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo ci
    ) {
        Entity entity = context.request(RenderContext.Companion.getENTITY());
        if (!(entity instanceof NPCEntity npc)) {
            return;
        }
        TrainerOutfitRenderer.INSTANCE.render((PosableModel) (Object) this, npc, poseStack, packedLight);
    }
}
