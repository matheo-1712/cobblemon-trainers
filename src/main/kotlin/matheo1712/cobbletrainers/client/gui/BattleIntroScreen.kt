package matheo1712.cobbletrainers.client.gui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.client.cache.TrainerSkinCache
import matheo1712.cobbletrainers.intro.IntroLayer
import matheo1712.cobbletrainers.intro.TrainerIntro
import matheo1712.cobbletrainers.network.BattleIntroPayload
import matheo1712.cobbletrainers.network.SkipBattleIntroPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * The versus screen: a scene a pack wrote, played over the world while the battle waits.
 *
 * Nothing is decided here. The server raised the screen, sent the scene along with it, is
 * counting the same ticks behind it, and opens the battle when they run out - see
 * [matheo1712.cobbletrainers.battle.TrainerBattleIntro]. So this closes on its own at the end
 * of the intro, and a player who has seen it before says so with [SkipBattleIntroPayload],
 * which only brings that deadline forward.
 *
 * A scene is a list of layers, drawn in the order written; what each one may say is in
 * [IntroLayer]. Everything here is that vocabulary turned into draw calls, and nothing else -
 * a layer this screen cannot draw was dropped at load, on the server, where a pack author is
 * reading the log.
 *
 * Points worth not rediscovering:
 * - **`isPauseScreen` must stay false.** A pause screen stops the integrated server, which is
 *   the very thing counting down to the battle: the screen would be waiting on a tick that
 *   never comes, in singleplayer, until the player closed it themselves.
 * - **An intro is written on 640×360 and scaled to the window.** Offsets and sizes go through
 *   [uiScale]; anchors do not, so a corner stays a corner. Without it, an intro written on a
 *   large window would be half the size on a small one.
 * - **A figure is the real model.** The trainer's entity is the one the player is standing in
 *   front of, looked up by the id the packet carries; the player's is their own. Only when the
 *   entity is not there does it fall back to the flat skin the server pushed, and then to a
 *   silhouette.
 * - **The entity is posed, then put back.** `renderEntityInInventory` draws whatever rotation
 *   the entity carries, so the yaw a layer asks for is written onto it for the length of one
 *   call - the name tag included, which would otherwise float in the middle of the screen.
 * - **The whole screen leaves at once, but not all of it by fading.** What a layer is made of
 *   decides whether the fade reaches it: a fill or a text takes it, an image only once blending
 *   is turned back on around its blit, an item only through the shader colour, and a posed
 *   model not at all - entity render types blend nothing, so a figure leaves by going instead.
 *   Left alone, each of those stands at full strength until the screen cuts out, which is the
 *   whole of issue #47.
 * - **Skipping only starts once the last entrance has landed.** A held key repeats, so a player
 *   walking up to a trainer with a finger on their movement key would otherwise skip the screen
 *   on the very frame it opened.
 * - **A layer's sound is played once**, on the tick its entrance starts, and as a UI sound: the
 *   battle theme is already on the music channel, and a stinger that ducked with it would go
 *   missing for anyone who turned music down.
 */
class BattleIntroScreen(private val intro: BattleIntroPayload) :
    Screen(Component.translatable(intro.trainerName)) {

    private val scene: TrainerIntro = intro.scene
    private val ticks = scene.ticks()
    private val length = (ticks * MS_PER_TICK).toFloat()
    private val opened = Util.getMillis()

    /** The tick the last entrance lands on: nothing may be skipped before it. */
    private val skipAt = scene.entranceEnd()

    /** True once the server has been told, or once there is nothing left to tell it. */
    private var done = false

    /** One flag per layer: a sound is a moment, not a state. */
    private val soundsPlayed = BooleanArray(scene.layers.size)

    /** Whether the log has already been told that a figure had no model to pose. */
    private var flatWarned = false

    /** The trainer whose floating name is being held down, and what it was set to. */
    private var quietened: NPCEntity? = null
    private var quietenedWas = false

    /** One animation state per `pokemon` layer, posed for the model it belongs to. */
    private val states = mutableMapOf<Int, FloatingState>()

    /** The team, when the scene draws it. Resolved once rather than on every frame. */
    private val party: List<RenderablePokemon> = intro.team.mapNotNull { member ->
        ResourceLocation.tryParse(member.species)
            ?.let { PokemonSpecies.getByIdentifier(it) }
            ?.let { RenderablePokemon(it, member.aspects.toSet()) }
    }

    /** Sizes of the images a scene names, read once: a blit has to know what it is cutting. */
    private val imageSizes = mutableMapOf<String, Pair<Int, Int>>()

    /** What one reference pixel is worth on this window. */
    private var uiScale = 1f

    override fun init() {
        uiScale = minOf(
            width.toFloat() / TrainerIntro.REFERENCE_WIDTH,
            height.toFloat() / TrainerIntro.REFERENCE_HEIGHT
        )
    }

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

        val elapsed = progress * ticks
        // Fading in at one end and out at the other, so the screen arrives over the world and
        // hands it back rather than cutting to it twice.
        val fadeIn = scene.fadeIn.coerceAtLeast(1).toFloat()
        val fadeOut = scene.fadeOut.coerceAtLeast(1).toFloat()
        // How much of the screen is left: 1 until the fade-out starts, 0 as it ends. It is the
        // fade itself for everything that takes a colour, and the way out for what does not.
        val leaving = 1f - eased((elapsed - (ticks - fadeOut)) / fadeOut)
        val alpha = eased(elapsed / fadeIn) * leaving
        if (alpha <= 0f) return

        scene.layers.forEachIndexed { index, layer ->
            val entrance = entranceOf(layer, elapsed)
            if (elapsed < layer.at) return@forEachIndexed

            playSound(index, layer, elapsed)
            drawLayer(guiGraphics, index, layer, entrance, alpha, leaving, partialTick)
        }

        if (elapsed >= skipAt) {
            guiGraphics.drawCenteredString(
                font,
                SKIP_HINT,
                width / 2,
                height - (HINT_MARGIN * uiScale).toInt(),
                argb(alpha * HINT_ALPHA, HINT)
            )
        }
    }

    /////////////////////////////////////
    // THE SCENE
    /////////////////////////////////////

    /** How far into its entrance a layer is, 0 as it starts and 1 once it has landed. */
    private fun entranceOf(layer: IntroLayer, elapsed: Float): Float {
        val since = elapsed - layer.at
        if (since < 0f) return 0f

        val over = layer.length.coerceAtLeast(1).toFloat()
        return ease(layer.ease, (since / over).coerceIn(0f, 1f))
    }

    /**
     * One layer, where its entrance has got it to and under what is left of the screen.
     *
     * @param leaving What the fade-out has left, 1 until it starts and 0 as it ends.
     */
    private fun drawLayer(
        guiGraphics: GuiGraphics,
        index: Int,
        layer: IntroLayer,
        entrance: Float,
        screenAlpha: Float,
        leaving: Float,
        partialTick: Float
    ) {
        val restX = anchorX(layer.anchor) + layer.offsetX * uiScale
        val restY = anchorY(layer.anchor) + layer.offsetY * uiScale
        val span = spanOf(layer)

        var x = restX
        var y = restY
        var alpha = screenAlpha * layer.alpha
        var scale = 1f

        when (layer.from) {
            "left" -> x = lerp(-span, restX, entrance)
            "right" -> x = lerp(width + span, restX, entrance)
            "top" -> y = lerp(-span, restY, entrance)
            "bottom" -> y = lerp(height + span, restY, entrance)
            "pop" -> {
                alpha *= (entrance * 2f).coerceAtMost(1f)
                scale = popped(entrance)
            }
            PLAIN -> Unit
            // `fade`, and whatever a pack wrote that we warned about at load.
            else -> alpha *= entrance
        }

        if (alpha <= 0f) return

        // A model is drawn on an entity render type, which blends nothing: the screen's fade
        // never reaches it, and it would still be standing at full strength once the scene
        // around it had gone. So it leaves by moving - back out the way it came in, or
        // shrinking away when it came in on the spot - which no render type can ignore.
        if (layer.type == IntroLayer.FIGURE || layer.type == IntroLayer.POKEMON) {
            when (layer.from) {
                "left" -> x = lerp(-span, x, leaving)
                "right" -> x = lerp(width + span, x, leaving)
                "top" -> y = lerp(-span, y, leaving)
                "bottom" -> y = lerp(height + span, y, leaving)
                else -> scale *= leaving
            }
            if (scale <= 0f) return
        }

        when (layer.type) {
            IntroLayer.FILL -> fill(guiGraphics, layer, x, y, scale, alpha)
            IntroLayer.FIGURE -> figure(guiGraphics, layer, x, y, scale, alpha)
            IntroLayer.TEXT -> text(guiGraphics, layer, x, y, scale, alpha)
            IntroLayer.VS -> versus(guiGraphics, layer, x, y, scale, alpha)
            IntroLayer.IMAGE -> image(guiGraphics, layer, x, y, scale, alpha)
            IntroLayer.TEAM_BALLS -> teamBalls(guiGraphics, layer, x, y, scale, alpha)
            IntroLayer.POKEMON -> pokemon(guiGraphics, index, layer, x, y, scale, alpha, partialTick)
        }
    }

    /** A rectangle, or a band leaning over its own height - laid down row by row. */
    private fun fill(guiGraphics: GuiGraphics, layer: IntroLayer, x: Float, y: Float, scale: Float, alpha: Float) {
        val boxWidth = layer.width?.let { it * uiScale * scale } ?: (width * scale)
        val boxHeight = layer.height?.let { it * uiScale * scale } ?: (height * scale)
        val color = argb(alpha, rgb(layer.color, BLACK))

        val top = (y - boxHeight / 2f).toInt()
        val bottom = (y + boxHeight / 2f).toInt()
        val half = boxWidth / 2f

        if (layer.slant == 0f) {
            guiGraphics.fill((x - half).toInt(), top, (x + half).toInt(), bottom, color)
            return
        }

        var row = top
        while (row < bottom) {
            val rowHeight = minOf(BAND_STEP, bottom - row)
            val lean = (y - row) * layer.slant
            guiGraphics.fill((x + lean - half).toInt(), row, (x + lean + half).toInt(), row + rowHeight, color)
            row += rowHeight
        }
    }

    /** A trainer or the player, as the model they are in the world. */
    private fun figure(guiGraphics: GuiGraphics, layer: IntroLayer, x: Float, y: Float, scale: Float, alpha: Float) {
        val tall = (layer.height ?: FIGURE_HEIGHT) * uiScale * scale
        val entity = if (layer.isPlayer) minecraft?.player else trainerEntity()

        if (entity != null) {
            renderEntity(guiGraphics, entity, x, y, tall, layer.yaw, layer.tilt, alpha)
            return
        }

        flatFigure(guiGraphics, layer, x, y, tall, alpha)
    }

    /**
     * The fallback: the skin blitted flat, the way the battle phone draws a trainer it has
     * never met. The player's own is always there; the trainer's is what the server pushed.
     *
     * Said once per screen, because it is not supposed to happen: the trainer is the entity the
     * player is standing in front of. A figure that came out flat rather than posed is either
     * that entity gone missing or an id that never matched, and neither is visible from the
     * outside - the two drawings look like the same trainer.
     */
    private fun flatFigure(guiGraphics: GuiGraphics, layer: IntroLayer, x: Float, y: Float, tall: Float, alpha: Float) {
        if (!flatWarned) {
            flatWarned = true
            CobblemonTrainers.LOGGER.warn(
                "Intro {}: no entity to pose for the {} figure (trainer entity {}), drawing the skin flat.",
                intro.introId, layer.who, intro.npcId
            )
        }

        val skin = if (layer.isPlayer) playerSkin() else TrainerSkinCache.peek(intro.trainerId)
        val pixels = (tall / TrainerSkinRenderer.FIGURE_HEIGHT).toInt().coerceAtLeast(1)
        val figureWidth = TrainerSkinRenderer.FIGURE_WIDTH * pixels
        val figureHeight = TrainerSkinRenderer.FIGURE_HEIGHT * pixels
        val top = (y - figureHeight / 2f).toInt()

        if (skin?.texture == null) {
            guiGraphics.fill(
                (x - figureWidth / 2f).toInt(),
                top,
                (x + figureWidth / 2f).toInt(),
                top + figureHeight,
                argb(alpha * SILHOUETTE_ALPHA, SILHOUETTE)
            )
            return
        }

        // setColor settles the batch in hand before the shader colour changes, so the fade
        // lands on the figure alone.
        guiGraphics.setColor(1f, 1f, 1f, alpha)
        TrainerSkinRenderer.drawFigure(guiGraphics, skin, x.toInt(), top, pixels)
        guiGraphics.setColor(1f, 1f, 1f, 1f)
    }

    /**
     * Poses a living entity in the screen, then puts it back the way it was.
     *
     * This is vanilla's own inventory rendering, minus the mouse: the rotations belong to the
     * entity, so they are written onto it for the length of one call. The name tag goes with
     * them - a trainer's floating name would otherwise land in the middle of the screen.
     */
    private fun renderEntity(
        guiGraphics: GuiGraphics,
        entity: LivingEntity,
        x: Float,
        y: Float,
        tall: Float,
        yaw: Float,
        tilt: Float,
        alpha: Float
    ) {
        val scale = tall / entity.bbHeight.coerceAtLeast(MIN_HEIGHT)

        val rotation = Quaternionf().rotateZ(Math.PI.toFloat())
        val camera = Quaternionf().rotateX(tilt * DEGREES)
        rotation.mul(camera)

        val bodyRot = entity.yBodyRot
        val yRot = entity.yRot
        val xRot = entity.xRot
        val headRot = entity.yHeadRot
        val headRotO = entity.yHeadRotO
        val named = entity.isCustomNameVisible

        entity.yBodyRot = FACING + yaw
        entity.yRot = FACING + yaw
        entity.xRot = 0f
        entity.yHeadRot = entity.yRot
        entity.yHeadRotO = entity.yRot
        entity.isCustomNameVisible = false

        guiGraphics.setColor(1f, 1f, 1f, alpha)
        InventoryScreen.renderEntityInInventory(
            guiGraphics,
            x,
            y,
            scale,
            Vector3f(0f, entity.bbHeight / 2f, 0f),
            rotation,
            camera,
            entity
        )
        guiGraphics.setColor(1f, 1f, 1f, 1f)

        entity.yBodyRot = bodyRot
        entity.yRot = yRot
        entity.xRot = xRot
        entity.yHeadRot = headRot
        entity.yHeadRotO = headRotO
        entity.isCustomNameVisible = named
    }

    private fun text(guiGraphics: GuiGraphics, layer: IntroLayer, x: Float, y: Float, scale: Float, alpha: Float) {
        val label = resolve(layer.value ?: return)
        val size = layer.size * uiScale * scale
        val pose = guiGraphics.pose()

        pose.pushPose()
        pose.translate(x, y, 0f)
        pose.scale(size, size, 1f)
        guiGraphics.drawString(
            font,
            label,
            -font.width(label) / 2,
            -font.lineHeight / 2,
            argb(alpha, rgb(layer.color, WHITE)),
            layer.shadow
        )
        pose.popPose()
    }

    /** The VS itself: the mod draws it, so a pack only ever places and colours it. */
    private fun versus(guiGraphics: GuiGraphics, layer: IntroLayer, x: Float, y: Float, scale: Float, alpha: Float) {
        val size = layer.size * uiScale * scale
        val pose = guiGraphics.pose()

        pose.pushPose()
        pose.translate(x, y, 0f)
        pose.scale(size, size, 1f)
        val half = font.width(VS) / 2
        val top = -font.lineHeight / 2
        // Its own outline, a pixel out on every side, so it holds against a bright band.
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                guiGraphics.drawString(font, VS, -half + dx, top + dy, argb(alpha, BLACK), false)
            }
        }
        guiGraphics.drawString(font, VS, -half, top, argb(alpha, rgb(layer.color, VS_COLOR)), false)
        pose.popPose()
    }

    private fun image(guiGraphics: GuiGraphics, layer: IntroLayer, x: Float, y: Float, scale: Float, alpha: Float) {
        val texture = ResourceLocation.tryParse(layer.texture ?: return) ?: return
        val size = sizeOf(texture) ?: return
        val (fileWidth, fileHeight) = size

        val drawWidth = (layer.width ?: fileWidth) * uiScale * scale
        val drawHeight = (layer.height ?: fileHeight) * uiScale * scale
        val tint = rgb(layer.color, WHITE)

        guiGraphics.setColor(
            ((tint shr 16) and 0xFF) / 255f,
            ((tint shr 8) and 0xFF) / 255f,
            (tint and 0xFF) / 255f,
            alpha
        )
        // With blending off, that alpha is written and never read: the image draws at full
        // strength whatever a layer asked for, and stays there while the rest of the screen
        // fades away. `GuiGraphics.blit` leaves the state to its caller and the flush
        // `setColor` just did turned it off, so it is turned back on here, per blit.
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        guiGraphics.blit(
            texture,
            (x - drawWidth / 2f).toInt(),
            (y - drawHeight / 2f).toInt(),
            drawWidth.toInt(),
            drawHeight.toInt(),
            0f,
            0f,
            fileWidth,
            fileHeight,
            fileWidth,
            fileHeight
        )
        guiGraphics.setColor(1f, 1f, 1f, 1f)
    }

    /** The row of Poké Balls: how many Pokémon, never which. */
    private fun teamBalls(guiGraphics: GuiGraphics, layer: IntroLayer, x: Float, y: Float, scale: Float, alpha: Float) {
        val slots = layer.slots.coerceIn(1, PARTY_SLOTS)
        val filled = (if (layer.isPlayer) playerParty() else intro.teamSize).coerceIn(0, slots)

        val size = BALL_SIZE * layer.size * uiScale * scale
        val gap = layer.gap * uiScale * scale
        val step = size + gap
        val left = x - (slots * step - gap) / 2f
        val top = y - size / 2f

        for (slot in 0 until slots) {
            if (slot >= filled && !layer.empty) continue

            val ballX = left + slot * step
            val pose = guiGraphics.pose()
            pose.pushPose()
            pose.translate(ballX, top, 0f)
            pose.scale(size / ITEM_PIXELS, size / ITEM_PIXELS, 1f)
            // An item is drawn on the translucent sheet, so the fade does reach it - but only
            // through the shader colour, `renderItem` taking no tint of its own.
            guiGraphics.setColor(1f, 1f, 1f, alpha)
            guiGraphics.renderItem(POKE_BALL, 0, 0)
            guiGraphics.setColor(1f, 1f, 1f, 1f)
            pose.popPose()

            // An empty slot is the same ball under a veil, drawn on the overlay layer - a plain
            // fill would slide under the item model rather than over it.
            if (slot >= filled) {
                guiGraphics.fill(
                    RenderType.guiOverlay(),
                    ballX.toInt(),
                    top.toInt(),
                    (ballX + size).toInt(),
                    (top + size).toInt(),
                    argb(alpha * EMPTY_ALPHA, BLACK)
                )
            }
        }
    }

    /** One Pokémon of the team, drawn the way the battle phone draws it. */
    private fun pokemon(
        guiGraphics: GuiGraphics,
        index: Int,
        layer: IntroLayer,
        x: Float,
        y: Float,
        scale: Float,
        alpha: Float,
        partialTick: Float
    ) {
        val member = party.getOrNull(layer.slot - 1) ?: return
        val tall = (layer.height ?: POKEMON_HEIGHT) * uiScale * scale
        val state = states.getOrPut(index) { FloatingState() }

        val pose = guiGraphics.pose()
        pose.pushPose()
        // A model hangs below the point it is translated to, so aim near the top of it.
        pose.translate(x, y - tall / 2f, 0f)
        pose.scale(MODEL_POSE_SCALE, MODEL_POSE_SCALE, 1f)
        guiGraphics.setColor(1f, 1f, 1f, alpha)
        drawProfilePokemon(
            renderablePokemon = member,
            matrixStack = pose,
            rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(MODEL_TILT, layer.yaw, 0f)),
            state = state,
            partialTicks = partialTick,
            scale = tall / MODEL_HEIGHT_UNIT
        )
        guiGraphics.setColor(1f, 1f, 1f, 1f)
        pose.popPose()
    }

    /////////////////////////////////////
    // WHAT THE LAYERS DRAW FROM
    /////////////////////////////////////

    /**
     * The trainer as they stand in the world, with their floating name held down for as long as
     * the screen is up.
     *
     * Cobblemon draws that name whenever the player is *looking at* the NPC - which is exactly
     * what a player who just accepted a battle is doing - so it lands over the figure whatever
     * `isCustomNameVisible` says, and again on the trainer showing through the backdrop. A scene
     * says who this is with a `text` layer, on both sides, or not at all: an entity label the
     * player only gets on one of the two is not a layout anyone chose.
     */
    private fun trainerEntity(): LivingEntity? {
        val entity = minecraft?.level?.getEntity(intro.npcId) as? LivingEntity ?: return null

        if (entity is NPCEntity && quietened == null) {
            quietened = entity
            quietenedWas = entity.hideNameTag
            entity.hideNameTag = true
        }
        return entity
    }

    /** Gives the trainer their name back, whichever way the screen went away. */
    override fun removed() {
        quietened?.hideNameTag = quietenedWas
        quietened = null
        super.removed()
    }

    private fun playerSkin(): TrainerSkinCache.Skin? {
        val skin = minecraft?.player?.skin ?: return null

        return TrainerSkinCache.Skin(skin.texture(), skin.model() == PlayerSkin.Model.SLIM, 64, 64)
    }

    /**
     * How many Pokémon the player is carrying, read from Cobblemon's own client storage - the
     * same guarded read [matheo1712.cobbletrainers.client.ClientPokemonSelection] makes, for the
     * same reason: a frame that lands before the party is there answers zero rather than
     * throwing into the render loop.
     */
    private fun playerParty(): Int = runCatching {
        (0 until PARTY_SLOTS).count { CobblemonClient.storage.party.get(it) != null }
    }.getOrDefault(0)

    /**
     * The text of a layer, placeholders filled in.
     *
     * The result goes through `translatable` like every other string a pack writes: a bare key
     * is translated, and words - placeholders included, which are words by then - are shown as
     * they are.
     */
    private fun resolve(value: String): Component {
        if (!value.contains('%')) return Component.translatable(value)

        val filled = value
            .replace("%name%", Component.translatable(intro.trainerName).string)
            .replace("%category%", intro.category.takeIf { it.isNotEmpty() }
                ?.let { Component.translatable(it).string }
                .orEmpty())
            .replace("%level%", intro.level.toString())
            .replace("%team%", intro.teamSize.toString())
            .replace("%player%", minecraft?.player?.gameProfile?.name.orEmpty())

        return Component.translatable(filled)
    }

    /** The size of an image, read once - a blit has to know what it is cutting out of. */
    private fun sizeOf(texture: ResourceLocation): Pair<Int, Int>? {
        imageSizes[texture.toString()]?.let { return it.takeIf { size -> size != MISSING } }

        val resource = minecraft?.resourceManager?.getResource(texture)?.orElse(null)
        if (resource == null) {
            CobblemonTrainers.LOGGER.warn(
                "Intro {}: no texture {} on this client. It has to ship under assets/, in a pack the client loads.",
                intro.introId, texture
            )
            imageSizes[texture.toString()] = MISSING
            return null
        }

        return try {
            val size = resource.open().use { stream ->
                NativeImage.read(stream).use { it.width to it.height }
            }
            imageSizes[texture.toString()] = size
            size
        } catch (e: Exception) {
            CobblemonTrainers.LOGGER.warn("Intro {}: unreadable texture {}: {}", intro.introId, texture, e.message)
            imageSizes[texture.toString()] = MISSING
            null
        }
    }

    /** A layer's sound goes off once, when its entrance starts. */
    private fun playSound(index: Int, layer: IntroLayer, elapsed: Float) {
        if (soundsPlayed[index] || elapsed < layer.at) return

        soundsPlayed[index] = true
        val location = layer.sound?.takeIf { it.isNotBlank() }?.let { ResourceLocation.tryParse(it) } ?: return

        minecraft?.soundManager?.play(
            SimpleSoundInstance(
                location,
                SoundSource.MASTER,
                layer.volume,
                layer.pitch,
                SoundInstance.createUnseededRandom(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0,
                0.0,
                0.0,
                true
            )
        )
    }

    /////////////////////////////////////
    // PLACING AND TIMING
    /////////////////////////////////////

    private fun anchorX(anchor: String): Float = when {
        anchor.endsWith("left") -> 0f
        anchor.endsWith("right") -> width.toFloat()
        else -> width / 2f
    }

    private fun anchorY(anchor: String): Float = when {
        anchor.startsWith("top") -> 0f
        anchor.startsWith("bottom") -> height.toFloat()
        else -> height / 2f
    }

    /**
     * Roughly half of what a layer covers, which is how far off screen it has to start for a
     * slide to begin out of sight. Rough is enough: it only decides where the travel starts.
     */
    private fun spanOf(layer: IntroLayer): Float = when (layer.type) {
        IntroLayer.FIGURE -> (layer.height ?: FIGURE_HEIGHT) * uiScale
        IntroLayer.POKEMON -> (layer.height ?: POKEMON_HEIGHT) * uiScale
        IntroLayer.IMAGE -> (layer.width ?: FIGURE_HEIGHT) * uiScale / 2f
        IntroLayer.FILL -> layer.width?.let { it * uiScale / 2f } ?: width.toFloat()
        IntroLayer.TEAM_BALLS -> layer.slots * (BALL_SIZE + layer.gap) * uiScale / 2f
        IntroLayer.TEXT -> (layer.value?.let { font.width(it) } ?: 0) * layer.size * uiScale / 2f + PADDING
        else -> font.width(VS) * layer.size * uiScale / 2f + PADDING
    }

    private fun ease(name: String, t: Float): Float = when (name) {
        "linear" -> t
        "in" -> t * t * t
        else -> eased(t)
    }

    /** Ease-out cubic, so everything arrives without stopping dead. */
    private fun eased(progress: Float): Float {
        val remaining = 1f - progress.coerceIn(0f, 1f)

        return 1f - remaining * remaining * remaining
    }

    /** Ease-out-back: grows past its size and settles, which is what makes a pop a pop. */
    private fun popped(t: Float): Float {
        val back = t - 1f

        return 1f + BACK_C3 * back * back * back + BACK_C1 * back * back
    }

    private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

    /////////////////////////////////////
    // LEAVING
    /////////////////////////////////////

    /**
     * Any key and any click, once the scene has landed. Escape lands here too, through
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
        if (done || progress() * ticks < skipAt) return

        done = true
        ClientPlayNetworking.send(SkipBattleIntroPayload())
        minecraft?.setScreen(null)
    }

    private fun progress(): Float = ((Util.getMillis() - opened) / length).coerceIn(0f, 1f)

    private fun argb(alpha: Float, rgb: Int): Int =
        ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or rgb

    /** A `#RRGGBB` a pack wrote, or the mod's own when it wrote none - or nonsense. */
    private fun rgb(color: String?, fallback: Int): Int {
        val text = color?.trim()?.removePrefix("#") ?: return fallback

        return text.toIntOrNull(16)?.and(0xFFFFFF) ?: fallback
    }

    private companion object {
        const val MS_PER_TICK = 50

        /** The one entrance that does nothing at all, and so must not be faded either. */
        const val PLAIN = "none"

        /** Reference pixels, the units a scene is written in. */
        const val FIGURE_HEIGHT = 96
        const val POKEMON_HEIGHT = 64
        const val BALL_SIZE = 10
        const val PADDING = 8f
        const val HINT_MARGIN = 12

        const val PARTY_SLOTS = 6
        const val BAND_STEP = 2
        const val ITEM_PIXELS = 16f
        const val EMPTY_ALPHA = 0.6f
        const val SILHOUETTE_ALPHA = 0.8f
        const val HINT_ALPHA = 0.45f
        const val MIN_HEIGHT = 0.1f

        /** Degrees to radians, and the yaw at which a model faces the player. */
        const val DEGREES = (Math.PI / 180.0).toFloat()
        const val FACING = 180f

        /** Ease-out-back, the standard pair. */
        const val BACK_C1 = 1.70158f
        const val BACK_C3 = BACK_C1 + 1f

        /**
         * The two factors a Pokémon model is drawn through, as Cobblemon itself pairs them: a
         * scale on the pose and a scale in the call. Neither works alone. [MODEL_HEIGHT_UNIT] is
         * what turns a height in pixels into the second of them.
         */
        const val MODEL_POSE_SCALE = 2.5f
        const val MODEL_HEIGHT_UNIT = 14f
        const val MODEL_TILT = 13f

        const val BLACK = 0x000000
        const val WHITE = 0xFFFFFF
        const val VS_COLOR = 0xFFF0C0
        const val SILHOUETTE = 0x101018
        const val HINT = 0xFFFFFF

        val MISSING = 0 to 0

        val VS: Component = Component.literal("VS")
        val SKIP_HINT: Component = CobblemonTrainers.lang("screen.battle_intro.skip")

        /** Cobblemon's own item, looked up once: a scene may draw six of them a frame. */
        val POKE_BALL: ItemStack = ItemStack(
            BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball"))
        )
    }
}
