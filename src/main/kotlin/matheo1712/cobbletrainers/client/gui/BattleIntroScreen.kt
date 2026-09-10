package matheo1712.cobbletrainers.client.gui

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.client.cache.TrainerSkinCache
import matheo1712.cobbletrainers.network.BattleIntroPayload
import matheo1712.cobbletrainers.network.SkipBattleIntroPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.network.chat.Component

/**
 * The versus screen a trainer shows before their battle: the player sliding in from the left,
 * the trainer from the right, a VS between them and the battle theme already playing under it.
 *
 * Nothing is decided here. The server raised the screen, is counting the same ticks behind it,
 * and opens the battle when they run out - see
 * [matheo1712.cobbletrainers.battle.TrainerBattleIntro]. So this closes on its own at the end
 * of [BattleIntroPayload.duration], and a player who has seen it before says so with
 * [SkipBattleIntroPayload], which only brings that deadline forward.
 *
 * Points worth not rediscovering:
 * - **`isPauseScreen` must stay false.** A pause screen stops the integrated server, which is
 *   the very thing counting down to the battle: the screen would be waiting on a tick that
 *   never comes, in singleplayer, until the player closed it themselves.
 * - **Nothing is drawn from a texture.** Two skins and flat rectangles, so the look is a
 *   handful of colours rather than an image to redraw at every window size. The bands lean, so
 *   they are laid down row by row: [GuiGraphics.fill] only knows rectangles.
 * - **The trainer's skin is read, never asked for.** The server pushed it along with the intro;
 *   asking would send a second question whose answer is empty for a trainer that is not
 *   `listed` - and it would land *after* the good one. Hence [TrainerSkinCache.peek].
 * - **Skipping only starts once the figures have landed.** A held key repeats, so a player
 *   walking up to a trainer with a finger on their movement key would otherwise skip the
 *   screen on the very frame it opened.
 * - **The player's own skin comes from their entity**, the one skin a client always has. A
 *   legacy 64x32 image never reaches it: the skin manager hands out 64x64.
 */
class BattleIntroScreen(private val intro: BattleIntroPayload) :
    Screen(Component.translatable(intro.trainerName)) {

    private val opened = Util.getMillis()
    private val length = (intro.duration.coerceAtLeast(1) * MS_PER_TICK).toFloat()

    /** True once the server has been told, or once there is nothing left to tell it. */
    private var done = false

    /** Drawn by hand, so vanilla's blur and menu background have no business here. */
    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) = Unit

    override fun isPauseScreen(): Boolean = false

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val progress = progress()
        if (progress >= 1f) {
            // The battle is opening on the server this very moment: nothing left to skip.
            done = true
            minecraft?.setScreen(null)
            return
        }

        // Fading in at one end and out at the other, so the screen arrives over the world and
        // hands it back rather than cutting to it twice.
        val alpha = eased(progress / FADE_IN) * (1f - eased((progress - 1f + FADE_OUT) / FADE_OUT))
        if (alpha <= 0f) return

        val slide = eased(progress / SLIDE)
        val clash = ((progress - SLIDE) / CLASH).coerceIn(0f, 1f)

        val scale = ((height * FIGURE_HEIGHT) / TrainerSkinRenderer.FIGURE_HEIGHT).toInt().coerceIn(2, 10)
        val figureHeight = TrainerSkinRenderer.FIGURE_HEIGHT * scale
        val figureWidth = TrainerSkinRenderer.FIGURE_WIDTH * scale
        val top = (height - figureHeight) / 2

        // Off the screen entirely at the start, so neither figure is ever seen standing still
        // outside its band.
        val travel = (width / 2 + figureWidth) * (1f - slide)
        val spread = (width / 5).coerceAtLeast(figureWidth * 3 / 4)
        val playerX = (width / 2 - spread - travel).toInt()
        val trainerX = (width / 2 + spread + travel).toInt()

        guiGraphics.fill(0, 0, width, height, argb(alpha * BACKDROP_ALPHA, BACKDROP))
        band(guiGraphics, playerX, figureWidth, argb(alpha * BAND_ALPHA, PLAYER_BAND))
        band(guiGraphics, trainerX, figureWidth, argb(alpha * BAND_ALPHA, TRAINER_BAND))
        streak(guiGraphics, clash, alpha)

        figure(guiGraphics, playerSkin(), playerX, top, scale, figureWidth, figureHeight, alpha)
        figure(
            guiGraphics,
            TrainerSkinCache.peek(intro.trainerId),
            trainerX,
            top,
            scale,
            figureWidth,
            figureHeight,
            alpha
        )

        name(guiGraphics, trainerX, top + figureHeight, alpha)
        versus(guiGraphics, clash, alpha)

        if (skippable(progress)) {
            guiGraphics.drawCenteredString(
                font,
                SKIP_HINT,
                width / 2,
                height - HINT_MARGIN,
                argb(alpha * clash * HINT_ALPHA, HINT)
            )
        }
    }

    /**
     * Any key and any click, once the figures are in place. Escape lands here too, through
     * [onClose]: leaving the screen early and skipping it are the same thing, the battle behind
     * it being held back either way.
     */
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        skip()
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        skip()
        return true
    }

    override fun onClose() {
        skip()
    }

    /** Tells the server to get on with it, and steps out of the way. */
    private fun skip() {
        if (done || !skippable(progress())) return

        done = true
        ClientPlayNetworking.send(SkipBattleIntroPayload())
        minecraft?.setScreen(null)
    }

    private fun progress(): Float = ((Util.getMillis() - opened) / length).coerceIn(0f, 1f)

    private fun skippable(progress: Float): Boolean = progress >= SLIDE

    /** The local player's own skin, on the rig their entity is drawn with. */
    private fun playerSkin(): TrainerSkinCache.Skin? {
        val skin = minecraft?.player?.skin ?: return null

        return TrainerSkinCache.Skin(skin.texture(), skin.model() == PlayerSkin.Model.SLIM, 64, 64)
    }

    /**
     * One figure, or the shape of one while its skin is still on its way - the trainer's is
     * pushed as the screen goes up, so the first frames may not have it.
     */
    private fun figure(
        guiGraphics: GuiGraphics,
        skin: TrainerSkinCache.Skin?,
        centerX: Int,
        top: Int,
        scale: Int,
        figureWidth: Int,
        figureHeight: Int,
        alpha: Float
    ) {
        if (skin?.texture == null) {
            guiGraphics.fill(
                centerX - figureWidth / 2,
                top,
                centerX + figureWidth / 2,
                top + figureHeight,
                argb(alpha * SILHOUETTE_ALPHA, SILHOUETTE)
            )
            return
        }

        // The fade goes through setColor rather than RenderSystem: it settles the batch in
        // hand before the shader colour changes, so the figure is the only thing tinted.
        guiGraphics.setColor(1f, 1f, 1f, alpha)
        TrainerSkinRenderer.drawFigure(guiGraphics, skin, centerX, top, scale)
        guiGraphics.setColor(1f, 1f, 1f, 1f)
    }

    /** A leaning band the height of the screen, laid down row by row. */
    private fun band(guiGraphics: GuiGraphics, centerX: Int, figureWidth: Int, color: Int) {
        val halfWidth = figureWidth * BAND_WIDTH / 2

        var y = 0
        while (y < height) {
            val rowHeight = minOf(BAND_STEP, height - y)
            val lean = ((height / 2 - y) * BAND_SLANT).toInt()
            guiGraphics.fill(centerX + lean - halfWidth, y, centerX + lean + halfWidth, y + rowHeight, color)
            y += rowHeight
        }
    }

    /** The line the two figures meet on, thrown across the screen as they land. */
    private fun streak(guiGraphics: GuiGraphics, clash: Float, alpha: Float) {
        if (clash <= 0f) return

        val half = (width / 2 * clash).toInt()
        val middle = height / 2
        guiGraphics.fill(
            width / 2 - half,
            middle - STREAK_HEIGHT,
            width / 2 + half,
            middle + STREAK_HEIGHT,
            argb(alpha * clash * STREAK_ALPHA, STREAK)
        )
    }

    /** The VS itself, landing in the middle of the screen as the figures stop. */
    private fun versus(guiGraphics: GuiGraphics, clash: Float, alpha: Float) {
        if (clash <= 0f) return

        // Coming down onto its own size rather than growing into it, so the letters arrive
        // with the figures rather than after them.
        val scale = VS_SCALE + VS_OVERSHOOT * (1f - clash)
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(width / 2f, height / 2f, 0f)
        pose.scale(scale, scale, 1f)
        guiGraphics.drawCenteredString(font, VS, 0, -font.lineHeight / 2, argb(alpha * clash, VS_COLOR))
        pose.popPose()
    }

    /** The trainer's name, under their figure, on a plate of its own. */
    private fun name(guiGraphics: GuiGraphics, centerX: Int, below: Int, alpha: Float) {
        val halfWidth = font.width(title) / 2 + NAME_PADDING
        val top = below + NAME_GAP

        guiGraphics.fill(
            centerX - halfWidth,
            top - NAME_PADDING,
            centerX + halfWidth,
            top + font.lineHeight + NAME_PADDING,
            argb(alpha * NAME_PLATE_ALPHA, NAME_PLATE)
        )
        guiGraphics.drawCenteredString(font, title, centerX, top, argb(alpha, NAME_COLOR))
    }

    private fun argb(alpha: Float, rgb: Int): Int =
        ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or rgb

    /** Ease-out cubic, so everything arrives without stopping dead. */
    private fun eased(progress: Float): Float {
        val remaining = 1f - progress.coerceIn(0f, 1f)

        return 1f - remaining * remaining * remaining
    }

    private companion object {
        const val MS_PER_TICK = 50

        // Shares of the whole, in the order they happen.
        const val FADE_IN = 0.10f
        const val SLIDE = 0.45f
        const val CLASH = 0.15f
        const val FADE_OUT = 0.15f

        /** Share of the window height a figure stands in. */
        const val FIGURE_HEIGHT = 0.42f

        /** How much wider than its figure a band is, and how far it leans over its height. */
        const val BAND_WIDTH = 2
        const val BAND_SLANT = 0.35f
        const val BAND_STEP = 2

        const val STREAK_HEIGHT = 2
        const val VS_SCALE = 4f
        const val VS_OVERSHOOT = 3f

        const val NAME_GAP = 8
        const val NAME_PADDING = 4
        const val HINT_MARGIN = 18

        const val BACKDROP = 0x05060D
        const val BACKDROP_ALPHA = 0.88f
        const val PLAYER_BAND = 0x2F6FBF
        const val TRAINER_BAND = 0xBF3A3A
        const val BAND_ALPHA = 0.55f
        const val SILHOUETTE = 0x101018
        const val SILHOUETTE_ALPHA = 0.8f
        const val STREAK = 0xFFF6D8
        const val STREAK_ALPHA = 0.9f
        const val VS_COLOR = 0xFFF0C0
        const val NAME_PLATE = 0x05060D
        const val NAME_PLATE_ALPHA = 0.8f
        const val NAME_COLOR = 0xFFFFFF
        const val HINT = 0xFFFFFF
        const val HINT_ALPHA = 0.45f

        val VS: Component = Component.literal("VS")
        val SKIP_HINT: Component = CobblemonTrainers.lang("screen.battle_intro.skip")
    }
}
