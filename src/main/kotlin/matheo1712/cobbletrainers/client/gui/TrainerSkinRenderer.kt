package matheo1712.cobbletrainers.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import matheo1712.cobbletrainers.client.cache.TrainerSkinCache
import net.minecraft.client.gui.GuiGraphics

/**
 * Draws a player skin flat, straight out of its image.
 *
 * The battle phone shows trainers that are not in the world - a trainer the player has never
 * met has no entity to render - so nothing here goes through the entity renderer. The front
 * faces of the skin are blitted side by side instead, which is all a portrait needs and costs
 * a handful of quads.
 *
 * Coordinates are the ones of the vanilla skin layout: the head front face sits at (8, 8), the
 * body at (20, 20), and so on. A legacy 64×32 image has no second layer and no left limbs, so
 * those fall back to the right ones.
 *
 * Both methods turn blending on and leave it on - the second skin layer is transparent almost
 * everywhere, and `GuiGraphics.blit` leaves that state to its caller.
 */
object TrainerSkinRenderer {

    /** Width in skin pixels of a whole standing figure, arms included, on the default rig. */
    const val FIGURE_WIDTH = 16

    /** Height in skin pixels of a whole standing figure: head 8, body 12, legs 12. */
    const val FIGURE_HEIGHT = 32

    /** Draws the face of the skin, hat layer included, as a square of [size] pixels. */
    fun drawFace(guiGraphics: GuiGraphics, skin: TrainerSkinCache.Skin, x: Int, y: Int, size: Int) {
        val texture = skin.texture ?: return

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.blit(texture, x, y, size, size, 8f, 8f, 8, 8, skin.width, skin.height)
        guiGraphics.blit(texture, x, y, size, size, 40f, 8f, 8, 8, skin.width, skin.height)
    }

    /**
     * Draws the whole figure, seen from the front, [scale] screen pixels per skin pixel.
     *
     * @param centerX Horizontal centre of the figure - a slim rig is two pixels narrower, so
     *   anchoring on the left edge would shift it.
     * @param top Top of the head.
     */
    fun drawFigure(guiGraphics: GuiGraphics, skin: TrainerSkinCache.Skin, centerX: Int, top: Int, scale: Int) {
        val texture = skin.texture ?: return

        val armWidth = if (skin.slim) 3 else 4
        val legacy = skin.height < 64
        val left = centerX - (8 + armWidth * 2) * scale / 2

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()

        fun part(dx: Int, dy: Int, width: Int, height: Int, u: Int, v: Int) =
            guiGraphics.blit(
                texture,
                left + dx * scale,
                top + dy * scale,
                width * scale,
                height * scale,
                u.toFloat(),
                v.toFloat(),
                width,
                height,
                skin.width,
                skin.height
            )

        // Facing the viewer, so the trainer's right arm is the one on the left of the screen.
        part(armWidth, 0, 8, 8, 8, 8)
        part(armWidth, 8, 8, 12, 20, 20)
        part(0, 8, armWidth, 12, 44, 20)
        part(armWidth + 8, 8, armWidth, 12, if (legacy) 44 else 36, if (legacy) 20 else 52)
        part(armWidth, 20, 4, 12, 4, 20)
        part(armWidth + 4, 20, 4, 12, if (legacy) 4 else 20, if (legacy) 20 else 52)

        // Second layer. The hat is the one part a legacy skin also carries.
        part(armWidth, 0, 8, 8, 40, 8)
        if (!legacy) {
            part(armWidth, 8, 8, 12, 20, 36)
            part(0, 8, armWidth, 12, 44, 36)
            part(armWidth + 8, 8, armWidth, 12, 52, 52)
            part(armWidth, 20, 4, 12, 4, 36)
            part(armWidth + 4, 20, 4, 12, 4, 52)
        }
    }
}