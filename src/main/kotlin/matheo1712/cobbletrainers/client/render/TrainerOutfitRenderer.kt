package matheo1712.cobbletrainers.client.render

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.trainers.TrainerOutfit
import matheo1712.cobbletrainers.trainers.TrainerTrinketSlot
import net.minecraft.client.Minecraft
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

/**
 * Draws what a trainer wears and holds, on top of the model Cobblemon has just finished.
 *
 * Cobblemon draws none of it. Its `NPCRenderer` registers no armour layer - its model is a
 * `PosableModel`, not the `HumanoidModel` every vanilla armour layer is written against - and
 * the held-item call it does make looks for a locator named `item` that the shipped trainer
 * geometry does not have. Accessories is no help either: it only hangs its own layer on
 * renderers whose model is a `HumanoidModel`, so a Mega Bracelet in a trainer's accessory slot
 * would be worn by nobody. So the mod draws its own, through [TrainerRig], which lends vanilla
 * the humanoid body it insists on.
 *
 * Points worth not rediscovering:
 *
 * - **This runs from inside Cobblemon's own render, not from a render layer.** Cobblemon resets
 *   every bone to its rest pose the moment it has drawn them (`PosableModel.setDefault`), so a
 *   layer - which runs after - would read a trainer standing to attention and hang the armour
 *   there. The one moment the bones are both posed and still readable is the tail of
 *   `PosableModel.render`, which is where
 *   [matheo1712.cobbletrainers.mixin.client.PosableModelMixin] calls this.
 * - **The buffer source is the game's, because that moment has none to offer.** Cobblemon is
 *   handed a single `VertexConsumer`, and armour needs one per texture. It is the same
 *   `MultiBufferSource` the entity was being drawn into, so the batching is unchanged; the one
 *   thing lost is the outline pass of a glowing trainer, which the armour does not join.
 * - **Only trainers of this mod are dressed.** The filter is the `trainer_id:` aspect: an NPC
 *   another pack dressed through Cobblemon's own tools is not ours to redraw.
 * - **The trinkets arrive as aspects**, not as equipment: they have no vanilla slot to sit in,
 *   and an aspect is already synced and already saved - see
 *   [matheo1712.cobbletrainers.trainers.TrainerOutfit]. Reading them here is the client's only
 *   way to know what a trainer wears: it has no trainer registry to ask.
 * - **Where a trinket is drawn comes from the slot a pack wrote it in, never from the item.**
 *   It could not come from the item: Mega Showdown picks the spot for each of its own key items
 *   in code - Maxie's Glasses on the face, Diantha's Charm on the chest, Zinnia's Anklet on a
 *   leg, and all three are `mega_slot` items exactly like the bracelet - so no tag, no registry
 *   and no item property can be asked. Nothing here names a third-party mod, and a pack may
 *   hang a trinket of its own at any of the seven places.
 */
object TrainerOutfitRenderer {

    /** The four vanilla slots an armour layer knows how to draw. */
    private val ARMOUR = listOf(
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    )

    /**
     * The armour layer, one per player rig. Vanilla ships a narrower arm for the slim model and
     * uses it for slim players; a trainer whose skin is slim is on Cobblemon's slim geometry, so
     * it gets the same.
     */
    private val armour: MutableMap<Boolean, HumanoidArmorLayer<LivingEntity, HumanoidModel<LivingEntity>, HumanoidModel<LivingEntity>>> =
        HashMap(2)

    /** Aspect Cobblemon carries on a trainer wearing the narrow-armed player model. */
    private const val SLIM_ASPECT = "model-slim"

    /**
     * Locator Cobblemon would draw a held item at itself. The shipped trainer geometry has none,
     * which is why the hands below are drawn here; should a later Cobblemon add one, this is
     * what stops the two of us drawing the same Poké Ball twice.
     */
    private const val COBBLEMON_ITEM_LOCATOR = "item"

    /** Drops what was baked from the old resource pack. */
    fun clear() {
        armour.clear()
        TrainerRig.clear()
    }

    /**
     * @param model The model Cobblemon has just drawn, with its bones still posed.
     * @param poseStack Positioned at the root of that model, which is the trainer's feet.
     */
    fun render(model: PosableModel, npc: NPCEntity, poseStack: PoseStack, light: Int) {
        // One pass over the aspects answers both questions - is this one of ours, and what
        // trinkets is it wearing. There are a handful of them, and this runs once a frame.
        var ours = false
        var trinkets: MutableList<Pair<TrainerTrinketSlot, ItemStack>>? = null
        for (aspect in npc.aspects) {
            if (aspect.startsWith(CobblemonTrainers.TRAINER_ASPECT_PREFIX)) {
                ours = true
                continue
            }
            val worn = TrainerOutfit.readTrinketAspect(aspect) ?: continue
            val list = trinkets
                ?: ArrayList<Pair<TrainerTrinketSlot, ItemStack>>(2).also { trinkets = it }
            list.add(worn)
        }
        if (!ours) return

        val main = npc.mainHandItem
        val off = npc.offhandItem
        val wearsSomething = ARMOUR.any { !npc.getItemBySlot(it).isEmpty }
        if (!wearsSomething && main.isEmpty && off.isEmpty && trinkets == null) return

        if (!TrainerRig.pose(model)) return

        val buffer = Minecraft.getInstance().renderBuffers().bufferSource()

        if (wearsSomething) {
            armourLayer(npc).render(poseStack, buffer, light, npc, 0f, 0f, 0f, 0f, 0f, 0f)
        }

        renderHand(npc, main, npc.mainArm, poseStack, buffer, light, model)
        renderHand(npc, off, npc.mainArm.opposite, poseStack, buffer, light, model)

        trinkets?.forEach { (slot, stack) ->
            renderTrinket(slot, npc, stack, poseStack, buffer, light)
        }
    }

    private fun armourLayer(
        npc: NPCEntity
    ): HumanoidArmorLayer<LivingEntity, HumanoidModel<LivingEntity>, HumanoidModel<LivingEntity>> {
        val slim = SLIM_ASPECT in npc.aspects
        return armour.getOrPut(slim) {
            val inner = if (slim) ModelLayers.PLAYER_SLIM_INNER_ARMOR else ModelLayers.PLAYER_INNER_ARMOR
            val outer = if (slim) ModelLayers.PLAYER_SLIM_OUTER_ARMOR else ModelLayers.PLAYER_OUTER_ARMOR
            HumanoidArmorLayer(
                TrainerRig.parent,
                HumanoidModel(TrainerRig.bake(inner)),
                HumanoidModel(TrainerRig.bake(outer)),
                Minecraft.getInstance().modelManager
            )
        }
    }

    /**
     * One hand - vanilla's own `ItemInHandLayer` written out, because the hand it draws is ours
     * to choose and because Cobblemon may already have drawn the main one.
     *
     * @param arm Which arm holds it. The off hand is the other one, so a left-handed trainer -
     *   were Cobblemon ever to make one - keeps its ball on the right side.
     */
    private fun renderHand(
        npc: NPCEntity,
        stack: ItemStack,
        arm: HumanoidArm,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        model: PosableModel
    ) {
        if (stack.isEmpty) return
        // Cobblemon draws the main hand itself as soon as the model offers it somewhere to draw.
        if (arm == npc.mainArm && model.hasItemLocator()) return

        val left = arm == HumanoidArm.LEFT
        poseStack.pushPose()
        TrainerRig.parent.model.translateToHand(arm, poseStack)
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f))
        poseStack.mulPose(Axis.YP.rotationDegrees(180f))
        poseStack.translate((if (left) -1f else 1f) / 16f, 0.125f, -0.625f)
        renderItem(npc, stack, handContext(arm), left, poseStack, buffer, light)
        poseStack.popPose()
    }

    /**
     * Draws one trinket where it is worn.
     *
     * The transforms are the ones Mega Showdown uses for the same seven places, copied so that a
     * trainer and a player wearing the same thing wear it the same way. No two of them share a
     * spot, which is what lets a trainer wear the lot.
     *
     * Two of the seven - [TrainerTrinketSlot.CHEST] and [TrainerTrinketSlot.BELT] - are placed
     * in plain body coordinates rather than off a bone, which is how that mod places them too.
     * They therefore ignore whatever a bedrock animation is doing to the torso; the other five
     * follow their limb.
     */
    private fun renderTrinket(
        slot: TrainerTrinketSlot,
        npc: NPCEntity,
        stack: ItemStack,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int
    ) {
        poseStack.pushPose()
        when (slot) {
            // On the head, nudged forward a hair when a helmet is already taking up the room.
            TrainerTrinketSlot.FACE -> {
                val depth = if (npc.getItemBySlot(EquipmentSlot.HEAD).isEmpty) 0f else -0.05f
                TrainerRig.parent.model.head.translateAndRotate(poseStack)
                poseStack.translate(0f, -0.25f, depth)
                poseStack.scale(0.625f, 0.625f, 0.625f)
                poseStack.mulPose(Axis.YP.rotationDegrees(180f))
                poseStack.mulPose(Axis.XP.rotationDegrees(180f))
                renderItem(npc, stack, ItemDisplayContext.HEAD, false, poseStack, buffer, light)
            }

            // Hung on the chest. Body coordinates, so it climbs back out of Cobblemon's space.
            TrainerTrinketSlot.CHEST -> {
                poseStack.translate(0f, -1.5f, 0f)
                poseStack.translate(0f, -0.25f, 0f)
                poseStack.mulPose(Axis.YP.rotationDegrees(180f))
                poseStack.mulPose(Axis.XP.rotationDegrees(180f))
                poseStack.scale(0.58f, 0.58f, 0.58f)
                renderItem(npc, stack, ItemDisplayContext.HEAD, false, poseStack, buffer, light)
            }

            // The free arm, twice over: the bracelet at the wrist, the ring a notch above it.
            TrainerTrinketSlot.WRIST, TrainerTrinketSlot.FOREARM -> {
                val arm = npc.mainArm.opposite
                val left = arm == HumanoidArm.LEFT
                TrainerRig.parent.model.translateToHand(arm, poseStack)
                poseStack.mulPose(Axis.XP.rotationDegrees(-90f))
                poseStack.mulPose(Axis.YP.rotationDegrees(180f))
                val depth = if (slot == TrainerTrinketSlot.WRIST) -0.625f else -0.5f
                poseStack.translate(if (left) -0.0625f else 0.0625f, 0.125f, depth)
                renderItem(npc, stack, handContext(arm), left, poseStack, buffer, light)
            }

            // Across the back of the main hand, over whatever that hand is holding.
            TrainerTrinketSlot.HAND -> {
                val arm = npc.mainArm
                val left = arm == HumanoidArm.LEFT
                TrainerRig.parent.model.translateToHand(arm, poseStack)
                poseStack.mulPose(Axis.XP.rotationDegrees(-90f))
                poseStack.translate(if (left) 0.0625f else -0.0625f, 0.125f, 0.1875f)
                renderItem(npc, stack, handContext(arm), left, poseStack, buffer, light)
            }

            // On the belt, pushed out a little when armour is in the way. Body coordinates again.
            TrainerTrinketSlot.BELT -> {
                poseStack.translate(0f, -1.5f, 0f)
                val depth = if (npc.getItemBySlot(EquipmentSlot.CHEST).isEmpty &&
                    npc.getItemBySlot(EquipmentSlot.LEGS).isEmpty
                ) -0.2f else -0.27f
                poseStack.translate(-0.15f, 0.55f, depth)
                poseStack.mulPose(Axis.YP.rotationDegrees(180f))
                poseStack.mulPose(Axis.XP.rotationDegrees(180f))
                poseStack.scale(0.18f, 0.18f, 0.18f)
                renderItem(npc, stack, ItemDisplayContext.HEAD, false, poseStack, buffer, light)
            }

            // The right ankle, grown a touch to clear a pair of leggings.
            TrainerTrinketSlot.ANKLE -> {
                TrainerRig.parent.model.rightLeg.translateAndRotate(poseStack)
                poseStack.mulPose(Axis.ZP.rotationDegrees(180f))
                if (!npc.getItemBySlot(EquipmentSlot.LEGS).isEmpty) {
                    poseStack.scale(1.2f, 1.2f, 1.2f)
                }
                renderItem(npc, stack, ItemDisplayContext.NONE, false, poseStack, buffer, light)
            }
        }
        poseStack.popPose()
    }

    private fun handContext(arm: HumanoidArm): ItemDisplayContext =
        if (arm == HumanoidArm.LEFT) ItemDisplayContext.THIRD_PERSON_LEFT_HAND
        else ItemDisplayContext.THIRD_PERSON_RIGHT_HAND

    private fun renderItem(
        npc: NPCEntity,
        stack: ItemStack,
        context: ItemDisplayContext,
        left: Boolean,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int
    ) {
        Minecraft.getInstance().itemRenderer.renderStatic(
            npc, stack, context, left, poseStack, buffer, npc.level(),
            light, OverlayTexture.NO_OVERLAY, npc.id
        )
    }

    /** Whether Cobblemon's own held-item call has somewhere to draw on this model. */
    private fun PosableModel.hasItemLocator(): Boolean =
        currentState?.locatorStates?.containsKey(COBBLEMON_ITEM_LOCATOR) == true
}
