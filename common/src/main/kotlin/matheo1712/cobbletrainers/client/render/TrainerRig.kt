package matheo1712.cobbletrainers.client.render

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * A vanilla humanoid model kept in step with the bedrock rig Cobblemon draws a trainer with.
 *
 * Everything vanilla knows how to hang on a body - armour, a trim, a glint, an item in a fist -
 * is written against [HumanoidModel]: `HumanoidArmorLayer` copies the seven parts of one onto
 * the armour model, `ItemInHandLayer` asks one where the hand is. Cobblemon's trainer is not
 * one of those, it is a `PosableModel` of bedrock bones. This class is the bridge: one
 * throw-away humanoid whose parts are moved, every frame, onto the bones of the trainer being
 * drawn, so that vanilla's own layers can be handed it and do the rest.
 *
 * **The two rigs are the same body**, which is the only reason this works at all: the geometry
 * Cobblemon ships for a trainer (`steve.geo.json`, `alex.geo.json`) is the vanilla player model
 * re-exported, cube for cube and pivot for pivot. Six bones line up one-to-one with vanilla's
 * seven parts, and exactly one of them needs a correction - see [BODY_OFFSET_Y].
 *
 * Points worth not rediscovering:
 *
 * - **The two spaces differ by 24 pixels.** Cobblemon renders its model after an extra
 *   `translate(0, 1.5, 0)`, which puts the origin at the trainer's feet; vanilla's humanoid
 *   space has its origin at the neck, 24 pixels higher. Everything here is computed in
 *   Cobblemon's space and drawn there, so nothing has to be converted - what does need it says
 *   so where it is written.
 * - **Bone transforms are read on a pose stack of our own**, never the one being rendered with.
 *   A bone's position on a humanoid part is relative to the model, not to the camera, and
 *   walking a fresh stack from the root is what gives exactly that.
 * - **The rig is posed from the bones, not from the entity.** Copying `limbSwing` and friends
 *   into `HumanoidModel.setupAnim` would give a body walking to a rhythm of its own next to the
 *   bedrock animation, which is what a trainer's idle, win and send-out animations actually
 *   move. Reading the bones instead means the armour follows whatever the pose does, including
 *   animations a pack added.
 * - **A scaled bone loses its scale.** A rotation is recovered from the bone matrix as Euler
 *   angles, which is exactly what a `ModelPart` can express and no more; a bedrock animation
 *   that scales a limb moves the armour with it but does not stretch it.
 */
object TrainerRig {

    /**
     * Bedrock bone of the trainer rig for each humanoid part. `hat` is missing on purpose: it
     * is vanilla's second head, and it takes the head's own transform.
     */
    private const val BONE_HEAD = "head"
    private const val BONE_BODY = "torso"
    private const val BONE_ARM_RIGHT = "arm_right"
    private const val BONE_ARM_LEFT = "arm_left"
    private const val BONE_LEG_RIGHT = "leg_right"
    private const val BONE_LEG_LEFT = "leg_left"

    private val BONES = setOf(
        BONE_HEAD, BONE_BODY, BONE_ARM_RIGHT, BONE_ARM_LEFT, BONE_LEG_RIGHT, BONE_LEG_LEFT
    )

    /**
     * The one place the two rigs disagree, in pixels.
     *
     * Vanilla's `body` turns around the neck: its pivot is the model origin and its cube hangs
     * twelve pixels below it. Bedrock's `torso` turns around the waist: same cube, pivot at the
     * bottom of it. Placing the vanilla part twelve pixels above the bone lands the two cubes
     * on each other, and leaves the armour turning with the torso rather than around it.
     *
     * Negative because the humanoid space counts y downwards.
     */
    private const val BODY_OFFSET_Y = -12f

    /**
     * The rig itself. One is enough: nothing reads it outside a single frame.
     *
     * Held rather than remade because baking a model layer is not free, and dropped on a
     * resource reload because that is when the layer it was baked from changed - see
     * [matheo1712.cobbletrainers.client.CobblemonTrainersClient].
     */
    private var rig: HumanoidModel<LivingEntity>? = null

    private fun rig(): HumanoidModel<LivingEntity> =
        rig ?: HumanoidModel<LivingEntity>(bake(ModelLayers.PLAYER_INNER_ARMOR)).also { rig = it }

    /**
     * What vanilla's layers take instead of a renderer. They only ever ask it for the model;
     * the texture is there because the interface has it, and an armour layer never looks.
     */
    val parent: RenderLayerParent<LivingEntity, HumanoidModel<LivingEntity>> =
        object : RenderLayerParent<LivingEntity, HumanoidModel<LivingEntity>> {
            override fun getModel(): HumanoidModel<LivingEntity> = rig()
            override fun getTextureLocation(entity: LivingEntity): ResourceLocation =
                DefaultPlayerSkin.getDefaultTexture()
        }

    /** Bakes a vanilla model layer. Separate so that a reload can be told to do it again. */
    fun bake(layer: ModelLayerLocation): ModelPart =
        Minecraft.getInstance().entityModels.bakeLayer(layer)

    /** Drops what was baked from the old resource pack. */
    fun clear() {
        rig = null
    }

    /**
     * Moves the rig onto the bones of a trainer model, and says whether it found them.
     *
     * False means this is not a body the mod knows how to dress - an NPC on a model of its own,
     * with other bone names. Nothing is drawn then, which is the only honest answer: armour
     * hung on guessed coordinates would be worse than none.
     */
    fun pose(model: PosableModel): Boolean {
        val bones = readBones(model)
        if (bones.size < BONES.size) return false

        val rig = rig()
        apply(rig.head, bones.getValue(BONE_HEAD))
        rig.hat.copyFrom(rig.head)
        apply(rig.body, bones.getValue(BONE_BODY), BODY_OFFSET_Y)
        apply(rig.rightArm, bones.getValue(BONE_ARM_RIGHT))
        apply(rig.leftArm, bones.getValue(BONE_ARM_LEFT))
        apply(rig.rightLeg, bones.getValue(BONE_LEG_RIGHT))
        apply(rig.leftLeg, bones.getValue(BONE_LEG_LEFT))

        // The rig is never drawn, only read, but the armour model inherits these two through
        // copyPropertiesTo: a stale crouch would bend a chestplate on a trainer standing up.
        rig.crouching = false
        rig.young = false
        return true
    }

    /**
     * Every bone of [BONES], as a matrix relative to the model root.
     *
     * The walk is the same one Cobblemon does when it draws: each bone's own transform, then
     * its children under it. Names come from the tree rather than from a list of paths, so a
     * pack that re-parents an arm is followed rather than guessed at.
     */
    private fun readBones(model: PosableModel): Map<String, Matrix4f> {
        val found = HashMap<String, Matrix4f>(BONES.size)
        val stack = PoseStack()

        stack.pushPose()
        model.rootPart.transform(stack)
        collect(model.rootPart, stack, found)
        stack.popPose()

        return found
    }

    private fun collect(bone: Bone, stack: PoseStack, found: MutableMap<String, Matrix4f>) {
        for ((name, child) in bone.children) {
            stack.pushPose()
            child.transform(stack)
            if (name in BONES) found[name] = Matrix4f(stack.last().pose())
            collect(child, stack, found)
            stack.popPose()
        }
    }

    /**
     * Puts one humanoid part where a bone is.
     *
     * @param offsetY Pixels along the part's own y axis, applied after the bone's rotation so
     *   that it follows the limb rather than the world. Only [BODY_OFFSET_Y] ever uses it.
     */
    private fun apply(part: ModelPart, bone: Matrix4f, offsetY: Float = 0f) {
        val matrix = Matrix4f(bone)
        if (offsetY != 0f) matrix.translate(0f, offsetY / 16f, 0f)

        val position = matrix.getTranslation(Vector3f())
        part.setPos(position.x * 16f, position.y * 16f, position.z * 16f)

        // normalize3x3 first: a bone an animation has scaled would otherwise hand back angles
        // read off a matrix that is not a rotation, and the part would tip over.
        val rotation = matrix.normalize3x3().getEulerAnglesZYX(Vector3f())
        part.setRotation(rotation.x, rotation.y, rotation.z)

        part.visible = true
    }
}
