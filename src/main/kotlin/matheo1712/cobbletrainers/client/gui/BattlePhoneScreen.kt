package matheo1712.cobbletrainers.client.gui

import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.mojang.blaze3d.systems.RenderSystem
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.client.cache.TrainerSkinCache
import matheo1712.cobbletrainers.client.cache.TrainerTeamCache
import matheo1712.cobbletrainers.network.BattlePhoneEntry
import matheo1712.cobbletrainers.network.OpenBattlePhonePayload
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW

/**
 * The battle phone screen: the trainers of the world, sorted by the datapack they come from,
 * and whether the player has beaten them.
 *
 * Laid out after a Pokédex, and dressed with the mod's own textures, under
 * `assets/cobblemon-trainers/textures/gui/battle_phone/`. They are deliberately plain — a
 * bezel, a slot, a couple of arrows, a status marker — and everything behind them is drawn
 * with flat rectangles, so replacing the set is a matter of redrawing six images at the same
 * sizes. [FRAME] is the one with a constraint: its transparent hole has to line up with
 * [INNER_X] and friends, since that hole is where the screen draws.
 *
 * The listing is whatever the server sent, and nothing is ever sent back beyond two questions:
 * the skin of a trainer, and the team of one the player has beaten. Both are cached — see
 * [TrainerSkinCache] and [TrainerTeamCache].
 */
class BattlePhoneScreen(data: OpenBattlePhonePayload) :
    Screen(CobblemonTrainers.lang("screen.battle_phone.title")) {

    /**
     * A line of the list. Headers only appear in the tab that holds every datapack at once,
     * where they are what makes the sort by datapack visible; a tab that is already one
     * datapack has its name in the selector above.
     */
    private sealed class Row(val height: Int) {
        class Header(val namespace: String) : Row(HEADER_HEIGHT)
        class Trainer(val entry: BattlePhoneEntry) : Row(ROW_HEIGHT)
    }

    /** A datapack tab. A null [namespace] is the tab holding every trainer at once. */
    private class Group(val namespace: String?, val entries: List<BattlePhoneEntry>, val rows: List<Row>)

    private val groups: List<Group> = buildGroups(data.entries)

    private var groupIndex = 0
    private var scroll = 0
    private var selected: BattlePhoneEntry? = null

    /**
     * One animation state per team slot, thrown away when the selected trainer changes: a
     * state belongs to the model it was posed for.
     */
    private var teamStates: List<FloatingState> = List(TEAM_SLOTS) { FloatingState() }
    private var teamStatesOwner: String? = null

    private var left = 0
    private var top = 0

    private val group: Group
        get() = groups[groupIndex]

    override fun init() {
        left = (width - FRAME_WIDTH) / 2
        top = (height - FRAME_HEIGHT) / 2

        if (selected == null) selected = groups.firstOrNull()?.entries?.firstOrNull()
    }

    /**
     * One tab per namespace, in alphabetical order, preceded by an "everything" tab. That
     * first tab is dropped when a single datapack ships trainers: it would be the same list
     * twice, and the selector already names the one datapack there is.
     */
    private fun buildGroups(entries: List<BattlePhoneEntry>): List<Group> {
        // The server sends trainers sorted by ID, so grouping by namespace keeps every tab
        // sorted without sorting anything again.
        val byNamespace = entries.groupBy { it.id.substringBefore(TRAINER_ID_SEPARATOR) }.toSortedMap()
        val groups = byNamespace.map { (namespace, group) ->
            Group(namespace, group, group.map { Row.Trainer(it) })
        }
        if (groups.size <= 1) return groups

        val allRows = byNamespace.flatMap { (namespace, group) ->
            listOf(Row.Header(namespace)) + group.map { Row.Trainer(it) }
        }
        return listOf(Group(null, entries, allRows)) + groups
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        guiGraphics.fill(x(INNER_X), y(INNER_Y), x(INNER_X + INNER_WIDTH), y(INNER_Y + INNER_HEIGHT), COLOR_SCREEN)

        var tooltip: Component? = null
        if (groups.isEmpty()) {
            guiGraphics.drawCenteredString(font, EMPTY_LABEL, x(FRAME_WIDTH / 2), y(FRAME_HEIGHT / 2), COLOR_TEXT)
        } else {
            renderHeader(guiGraphics, mouseX, mouseY)
            renderList(guiGraphics, mouseX, mouseY)
            tooltip = renderDetails(guiGraphics, mouseX, mouseY, partialTick)
        }

        // The frame comes last: its rounded corners and brackets cut into the panels below.
        blit(guiGraphics, FRAME, left, top, FRAME_WIDTH, FRAME_HEIGHT, 0f, 0f, FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT)
        RenderSystem.disableBlend()

        tooltip?.let { guiGraphics.renderTooltip(font, it, mouseX, mouseY) }
    }

    private fun renderHeader(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        guiGraphics.drawCenteredString(font, title, x(FRAME_WIDTH / 2), y(TITLE_Y), COLOR_TITLE)

        // Datapack selector, over the list column. Both arrow images hold their hovered state
        // below the idle one.
        if (groups.size > 1) {
            renderArrow(guiGraphics, ARROW_LEFT, x(LIST_X), overLeftArrow(mouseX.toDouble(), mouseY.toDouble()))
            renderArrow(
                guiGraphics,
                ARROW_RIGHT,
                x(LIST_X + LIST_WIDTH - ARROW_WIDTH),
                overRightArrow(mouseX.toDouble(), mouseY.toDouble())
            )
        }
        val label = group.namespace?.let { Component.literal(it) } ?: ALL_LABEL
        guiGraphics.drawCenteredString(
            font,
            trim(label, LIST_WIDTH - 2 * (ARROW_WIDTH + 4)),
            x(LIST_X + LIST_WIDTH / 2),
            y(SELECTOR_Y + 1),
            COLOR_TEXT
        )

        // Progress of that datapack, over the detail column.
        val defeated = group.entries.count { it.defeated }
        guiGraphics.drawCenteredString(
            font,
            CobblemonTrainers.lang("screen.battle_phone.progress", defeated, group.entries.size),
            x(DETAIL_X + DETAIL_WIDTH / 2),
            y(SELECTOR_Y + 1),
            COLOR_TEXT
        )
    }

    private fun renderArrow(guiGraphics: GuiGraphics, texture: ResourceLocation, arrowX: Int, hovered: Boolean) {
        val v = if (hovered) ARROW_HEIGHT.toFloat() else 0f
        blit(guiGraphics, texture, arrowX, y(SELECTOR_Y), ARROW_WIDTH, ARROW_HEIGHT, 0f, v, ARROW_WIDTH, ARROW_HEIGHT, ARROW_WIDTH, ARROW_HEIGHT * 2)
    }

    private fun renderList(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        guiGraphics.enableScissor(x(LIST_X), y(PANEL_Y), x(LIST_X + LIST_WIDTH), y(PANEL_Y + PANEL_HEIGHT))
        forEachVisibleRow { row, rowY ->
            when (row) {
                is Row.Header -> renderRowHeader(guiGraphics, row, y(rowY))
                is Row.Trainer -> renderRow(guiGraphics, row.entry, y(rowY), mouseX, mouseY)
            }
        }
        guiGraphics.disableScissor()

        renderScrollBar(guiGraphics)
    }

    /**
     * Walks the rows that fit in the panel from [scroll] down, handing each its top edge as an
     * offset from the frame. Rows are not all the same height, so rendering and hit testing
     * both have to walk rather than divide.
     */
    private inline fun forEachVisibleRow(action: (Row, Int) -> Unit) {
        val rows = group.rows
        var rowY = PANEL_Y
        var index = scroll
        while (index < rows.size && rowY + rows[index].height <= PANEL_Y + PANEL_HEIGHT) {
            action(rows[index], rowY)
            rowY += rows[index].height
            index++
        }
    }

    /** The datapack a run of trainers comes from, with a rule running to the edge of the panel. */
    private fun renderRowHeader(guiGraphics: GuiGraphics, header: Row.Header, rowY: Int) {
        val label = trim(Component.literal(header.namespace), LIST_WIDTH - 8)
        val textX = x(LIST_X)
        val textY = rowY + HEADER_HEIGHT - font.lineHeight

        guiGraphics.drawString(font, label, textX, textY, COLOR_HEADER)

        val ruleX = textX + font.width(label) + 4
        val ruleY = textY + font.lineHeight / 2
        if (ruleX < x(LIST_X + LIST_WIDTH)) {
            guiGraphics.fill(ruleX, ruleY, x(LIST_X + LIST_WIDTH), ruleY + 1, COLOR_HEADER_RULE)
        }
    }

    private fun renderRow(guiGraphics: GuiGraphics, entry: BattlePhoneEntry, rowY: Int, mouseX: Int, mouseY: Int) {
        val slotX = x(LIST_X)

        blit(guiGraphics, SLOT, slotX, rowY, SLOT_SIZE, SLOT_SIZE, 0f, 0f, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE)

        val skin = TrainerSkinCache.get(entry.id)
        if (skin?.texture != null) {
            TrainerSkinRenderer.drawFace(guiGraphics, skin, slotX + 2, rowY + 2, SLOT_SIZE - 4)
        } else {
            guiGraphics.drawCenteredString(font, UNKNOWN, slotX + SLOT_SIZE / 2, rowY + 6, COLOR_TEXT_DIM)
        }

        // The outline goes over the head, the way a Pokédex draws it over the sprite.
        val hovered = mouseX >= slotX && mouseX < x(LIST_X + LIST_WIDTH) && mouseY >= rowY && mouseY < rowY + SLOT_SIZE
        val selectionOffset = when {
            entry.id == selected?.id -> SLOT_TEXTURE_SIZE.toFloat()
            hovered -> 0f
            else -> null
        }
        if (selectionOffset != null) {
            blit(guiGraphics, SLOT_SELECT, slotX, rowY, SLOT_SIZE, SLOT_SIZE, 0f, selectionOffset, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE * 2)
        }

        val nameX = slotX + SLOT_SIZE + 5
        val nameWidth = x(LIST_X + LIST_WIDTH) - nameX - MARKER_WIDTH - 4
        guiGraphics.drawString(
            font,
            trim(Component.translatable(entry.name), nameWidth),
            nameX,
            rowY + (SLOT_SIZE - font.lineHeight) / 2,
            if (entry.defeated) COLOR_TEXT else COLOR_TEXT_DIM
        )

        renderMarker(guiGraphics, entry.defeated, x(LIST_X + LIST_WIDTH) - MARKER_WIDTH, rowY + (SLOT_SIZE - MARKER_HEIGHT) / 2)
    }

    /** The status marker: the full ball for a beaten trainer, its outline otherwise. */
    private fun renderMarker(guiGraphics: GuiGraphics, defeated: Boolean, markerX: Int, markerY: Int) {
        blit(
            guiGraphics,
            MARKER,
            markerX,
            markerY,
            MARKER_WIDTH,
            MARKER_HEIGHT,
            0f,
            if (defeated) MARKER_HEIGHT.toFloat() else 0f,
            MARKER_WIDTH,
            MARKER_HEIGHT,
            MARKER_WIDTH,
            MARKER_HEIGHT * 2
        )
    }

    private fun renderScrollBar(guiGraphics: GuiGraphics) {
        val maxScroll = maxScroll()
        if (maxScroll == 0) return

        val barX = x(LIST_X + LIST_WIDTH + 2)
        val barTop = y(PANEL_Y)
        guiGraphics.fill(barX, barTop, barX + SCROLL_BAR_WIDTH, y(PANEL_Y + PANEL_HEIGHT), COLOR_SCROLL_TRACK)

        val thumbHeight = maxOf(PANEL_HEIGHT / (maxScroll + 1), MIN_THUMB_HEIGHT)
        val thumbTop = barTop + (PANEL_HEIGHT - thumbHeight) * scroll / maxScroll
        guiGraphics.fill(barX, thumbTop, barX + SCROLL_BAR_WIDTH, thumbTop + thumbHeight, COLOR_SCROLL_THUMB)
    }

    /** @return the tooltip to draw over everything, if the mouse is on a team member. */
    private fun renderDetails(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float): Component? {
        val entry = selected ?: return null
        val centerX = x(DETAIL_X + DETAIL_WIDTH / 2)

        guiGraphics.drawCenteredString(
            font,
            trim(Component.translatable(entry.name), DETAIL_WIDTH),
            centerX,
            y(NAME_Y),
            COLOR_TITLE
        )

        val skin = TrainerSkinCache.get(entry.id)
        if (skin?.texture != null) {
            TrainerSkinRenderer.drawFigure(guiGraphics, skin, x(FIGURE_CENTER_X), y(PORTRAIT_TOP), FIGURE_SCALE)
        } else {
            guiGraphics.drawCenteredString(
                font,
                UNKNOWN,
                x(FIGURE_CENTER_X),
                y(PORTRAIT_TOP + TrainerSkinRenderer.FIGURE_HEIGHT * FIGURE_SCALE / 2),
                COLOR_TEXT_DIM
            )
        }

        val status = CobblemonTrainers.lang(statusKey(entry))
        val statusWidth = MARKER_WIDTH + MARKER_TEXT_GAP + font.width(status)
        renderMarker(guiGraphics, entry.defeated, centerX - statusWidth / 2, y(STATUS_Y) - MARKER_LINE_OFFSET)
        guiGraphics.drawString(
            font,
            status,
            centerX - statusWidth / 2 + MARKER_WIDTH + MARKER_TEXT_GAP,
            y(STATUS_Y),
            COLOR_TEXT
        )

        drawSmall(
            guiGraphics,
            CobblemonTrainers.lang("screen.battle_phone.team", entry.level, entry.teamSize).string,
            centerX,
            y(TEAM_LINE_Y),
            COLOR_TEXT_DIM
        )

        // Models last: they render through their own buffer, so nothing of ours is in flight.
        return renderTeam(guiGraphics, entry, mouseX, mouseY, partialTick)
    }

    /**
     * The six team slots, filled only once the player has beaten the trainer — the server
     * refuses to send a team before that, so an empty slot is the honest answer either way.
     */
    private fun renderTeam(
        guiGraphics: GuiGraphics,
        entry: BattlePhoneEntry,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ): Component? {
        val team = if (entry.defeated) TrainerTeamCache.get(entry.id) else emptyList()
        val states = statesFor(entry)
        var tooltip: Component? = null

        for (slot in 0 until TEAM_SLOTS) {
            val cellX = x(TEAM_X + (slot % TEAM_COLUMNS) * TEAM_CELL_WIDTH)
            val cellY = y(PORTRAIT_TOP + (slot / TEAM_COLUMNS) * TEAM_CELL_HEIGHT)

            blit(
                guiGraphics,
                SLOT,
                cellX + (TEAM_CELL_WIDTH - TEAM_SLOT_SIZE) / 2,
                cellY + (TEAM_CELL_HEIGHT - TEAM_SLOT_SIZE) / 2,
                TEAM_SLOT_SIZE,
                TEAM_SLOT_SIZE,
                0f,
                0f,
                SLOT_TEXTURE_SIZE,
                SLOT_TEXTURE_SIZE,
                SLOT_TEXTURE_SIZE,
                SLOT_TEXTURE_SIZE
            )

            val member = team?.getOrNull(slot)
            if (member == null) {
                guiGraphics.drawCenteredString(
                    font,
                    UNKNOWN,
                    cellX + TEAM_CELL_WIDTH / 2,
                    cellY + (TEAM_CELL_HEIGHT - font.lineHeight) / 2,
                    COLOR_TEXT_DIM
                )
                continue
            }

            renderPokemon(guiGraphics, member, states[slot], cellX, cellY, partialTick)

            val hovered = mouseX >= cellX && mouseX < cellX + TEAM_CELL_WIDTH &&
                mouseY >= cellY && mouseY < cellY + TEAM_CELL_HEIGHT
            if (hovered) {
                val name = member.nickname.ifEmpty { member.pokemon.species.translatedName.string }
                tooltip = CobblemonTrainers.lang("screen.battle_phone.pokemon", name, member.level)
            }
        }

        return tooltip
    }

    private fun renderPokemon(
        guiGraphics: GuiGraphics,
        member: TrainerTeamCache.Member,
        state: FloatingState,
        cellX: Int,
        cellY: Int,
        partialTick: Float
    ) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        // A model hangs below the point it is translated to, so aim near the top of the slot.
        pose.translate(
            (cellX + TEAM_CELL_WIDTH / 2).toDouble(),
            (cellY + TEAM_MODEL_TOP).toDouble(),
            0.0
        )
        pose.scale(TEAM_POSE_SCALE, TEAM_POSE_SCALE, 1f)
        drawProfilePokemon(
            renderablePokemon = member.pokemon,
            matrixStack = pose,
            rotation = Quaternionf().fromEulerXYZDegrees(MODEL_ROTATION),
            state = state,
            partialTicks = partialTick,
            scale = TEAM_MODEL_SCALE
        )
        pose.popPose()
    }

    /** Animation states belong to the models they were posed for, so they follow the selection. */
    private fun statesFor(entry: BattlePhoneEntry): List<FloatingState> {
        if (teamStatesOwner != entry.id) {
            teamStatesOwner = entry.id
            teamStates = List(TEAM_SLOTS) { FloatingState() }
        }
        return teamStates
    }

    /**
     * What the detail panel says about that trainer. A beaten one that turns down rematches is
     * worth its own line: the player would otherwise keep looking for a fight that is over.
     */
    private fun statusKey(entry: BattlePhoneEntry): String = when {
        entry.defeated && !entry.canRebattle -> "screen.battle_phone.status.defeated_final"
        entry.defeated -> "screen.battle_phone.status.defeated"
        !entry.canBattle -> "screen.battle_phone.status.no_battle"
        else -> "screen.battle_phone.status.pending"
    }

    /** Draws a centred line at three quarters of the font size, for text that has to fit. */
    private fun drawSmall(guiGraphics: GuiGraphics, text: String, centerX: Int, textY: Int, color: Int) {
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(centerX.toFloat(), textY.toFloat(), 0f)
        guiGraphics.pose().scale(SMALL_TEXT_SCALE, SMALL_TEXT_SCALE, 1f)
        guiGraphics.drawString(font, text, -font.width(text) / 2, 0, color)
        guiGraphics.pose().popPose()
    }

    private fun trim(text: Component, maxWidth: Int): String {
        val raw = text.string
        if (font.width(raw) <= maxWidth) return raw
        return font.plainSubstrByWidth(raw, maxWidth - font.width(ELLIPSIS)) + ELLIPSIS
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true
        if (groups.isEmpty()) return false

        if (overLeftArrow(mouseX, mouseY)) return selectGroup(-1)
        if (overRightArrow(mouseX, mouseY)) return selectGroup(1)

        if (mouseX < x(LIST_X) || mouseX >= x(LIST_X + LIST_WIDTH)) return false

        var clicked = false
        forEachVisibleRow { row, rowY ->
            if (row is Row.Trainer && mouseY >= y(rowY) && mouseY < y(rowY + row.height)) {
                selected = row.entry
                clicked = true
            }
        }
        return clicked
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (groups.isEmpty()) return false

        scroll = (scroll - scrollY.toInt()).coerceIn(0, maxScroll())
        return true
    }

    /**
     * The furthest the list may scroll, found by filling the panel from the last row back.
     * Dividing would not do: a header is shorter than a trainer.
     */
    private fun maxScroll(): Int {
        val rows = group.rows
        var height = 0
        var index = rows.size
        while (index > 0 && height + rows[index - 1].height <= PANEL_HEIGHT) {
            height += rows[index - 1].height
            index--
        }
        return index
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true
        if (groups.size <= 1) return false

        return when (keyCode) {
            GLFW.GLFW_KEY_LEFT -> selectGroup(-1)
            GLFW.GLFW_KEY_RIGHT -> selectGroup(1)
            else -> false
        }
    }

    private fun overLeftArrow(mouseX: Double, mouseY: Double): Boolean =
        overArrow(x(LIST_X), mouseX, mouseY)

    private fun overRightArrow(mouseX: Double, mouseY: Double): Boolean =
        overArrow(x(LIST_X + LIST_WIDTH - ARROW_WIDTH), mouseX, mouseY)

    /**
     * The hit box of an arrow, which is the same one the hovered state is drawn from — a
     * button that lights up somewhere other than where it answers is worse than no highlight.
     * It is a couple of pixels wider than the image: an arrow is a thin thing to aim at.
     */
    private fun overArrow(arrowX: Int, mouseX: Double, mouseY: Double): Boolean =
        groups.size > 1 &&
            mouseX >= arrowX - CLICK_PADDING && mouseX < arrowX + ARROW_WIDTH + CLICK_PADDING &&
            mouseY >= y(SELECTOR_Y) - CLICK_PADDING && mouseY < y(SELECTOR_Y + ARROW_HEIGHT) + CLICK_PADDING

    /** Moves to another datapack tab, always landing on a valid one. */
    private fun selectGroup(step: Int): Boolean {
        groupIndex = Math.floorMod(groupIndex + step, groups.size)
        scroll = 0
        selected = group.entries.firstOrNull()
        return true
    }

    /**
     * Consulting the phone does not stop the world.
     *
     * A screen pauses the integrated server by default, which is right for a menu and wrong
     * here: the phone is something a player pulls out mid-adventure, and it would otherwise
     * behave differently in single player and in multiplayer, where nothing ever pauses.
     */
    override fun isPauseScreen(): Boolean = false

    private fun x(offset: Int) = left + offset

    private fun y(offset: Int) = top + offset

    private companion object {
        val FRAME: ResourceLocation = phoneTexture("frame")
        val SLOT: ResourceLocation = phoneTexture("slot")
        val SLOT_SELECT: ResourceLocation = phoneTexture("slot_selected")
        val MARKER: ResourceLocation = phoneTexture("marker")
        val ARROW_LEFT: ResourceLocation = phoneTexture("arrow_left")
        val ARROW_RIGHT: ResourceLocation = phoneTexture("arrow_right")

        /** Size of the frame image, which the whole screen is laid out inside of. */
        const val FRAME_WIDTH = 345
        const val FRAME_HEIGHT = 207

        /** The hole in the middle of the frame, where everything of ours is drawn. */
        const val INNER_X = 18
        const val INNER_Y = 17
        const val INNER_WIDTH = 309
        const val INNER_HEIGHT = 179

        const val TITLE_Y = 21
        const val SELECTOR_Y = 33

        const val LIST_X = 22
        const val LIST_WIDTH = 148
        const val DETAIL_X = 182
        const val DETAIL_WIDTH = 140

        const val PANEL_Y = 48
        const val PANEL_HEIGHT = 144
        const val ROW_HEIGHT = 24
        const val HEADER_HEIGHT = 13

        /** The entry slot is a 25×25 image, drawn smaller so a row stays compact. */
        const val SLOT_TEXTURE_SIZE = 25
        const val SLOT_SIZE = 20

        /** The status marker holds its two states one above the other. */
        const val MARKER_WIDTH = 14
        const val MARKER_HEIGHT = 14
        const val MARKER_TEXT_GAP = 3

        /** Lifts the marker so it reads as centred on the status line, which is shorter. */
        const val MARKER_LINE_OFFSET = 2

        /** Both arrows are one image holding two states, the second below the first. */
        const val ARROW_WIDTH = 7
        const val ARROW_HEIGHT = 10

        const val SCROLL_BAR_WIDTH = 4
        const val MIN_THUMB_HEIGHT = 12

        // The detail column: the trainer on the left, their team on the right, between the
        // name above and the status below.
        const val NAME_Y = 48
        const val PORTRAIT_TOP = 60
        const val FIGURE_CENTER_X = DETAIL_X + 34
        const val FIGURE_SCALE = 3
        const val STATUS_Y = 164
        const val TEAM_LINE_Y = 177

        const val TEAM_SLOTS = 6
        const val TEAM_COLUMNS = 2
        const val TEAM_X = DETAIL_X + 64
        const val TEAM_CELL_WIDTH = 38
        const val TEAM_CELL_HEIGHT = 32
        const val TEAM_SLOT_SIZE = 28

        /**
         * How a model is sized, copied from Cobblemon's own party slots: a scale on the pose
         * and another passed to the renderer, which multiply. Neither is arbitrary — the
         * second one alone leaves a Pokémon a few pixels tall, and the pair keeps the depth
         * squash Cobblemon's portraits have, since only the first applies to z.
         */
        const val TEAM_POSE_SCALE = 2.5f
        const val TEAM_MODEL_SCALE = 4.5f

        /**
         * Where the model hangs from, measured from the top of its cell. A model is drawn
         * downwards from that point over roughly the height of a slot, so this centres it.
         */
        const val TEAM_MODEL_TOP = 4

        /** The three-quarter view Cobblemon uses for a Pokémon portrait. */
        val MODEL_ROTATION: Vector3f = Vector3f(13f, 35f, 0f)

        const val SMALL_TEXT_SCALE = 0.75f

        /** A couple of pixels of slack around the arrows, which are thin things to aim at. */
        const val CLICK_PADDING = 2

        /** Trainer IDs arrive as strings, and the part before this is the datapack namespace. */
        const val TRAINER_ID_SEPARATOR = ':'

        // The screen behind the bezel, and the shades of text on it.
        const val COLOR_SCREEN = 0xFF16344B.toInt()
        const val COLOR_SCROLL_TRACK = 0xFF0C2033.toInt()
        const val COLOR_SCROLL_THUMB = 0xFF5AAAEB.toInt()
        const val COLOR_TITLE = 0xFFFFFFFF.toInt()
        const val COLOR_TEXT = 0xFFE6F4FF.toInt()
        const val COLOR_TEXT_DIM = 0xFF8FB6D0.toInt()
        const val COLOR_HEADER = 0xFF5AAAEB.toInt()
        const val COLOR_HEADER_RULE = 0x665AAAEB

        const val ELLIPSIS = "…"

        /** Stands in for a skin or a team member that is missing, hidden, or still on its way. */
        const val UNKNOWN = "?"

        val ALL_LABEL: Component = CobblemonTrainers.lang("screen.battle_phone.all")
        val EMPTY_LABEL: Component = CobblemonTrainers.lang("screen.battle_phone.empty")

        fun phoneTexture(name: String): ResourceLocation =
            CobblemonTrainers.id("textures/gui/battle_phone/$name.png")

        /**
         * A blit with blending on.
         *
         * `GuiGraphics.blit` leaves the blend state to its caller, and every texture here has
         * transparent pixels — the frame most of all, which is drawn over the content. Enabling
         * it once per screen would not do: drawing text ends its own batch, and that turns
         * blending back off.
         */
        fun blit(
            guiGraphics: GuiGraphics,
            texture: ResourceLocation,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            u: Float,
            v: Float,
            uWidth: Int,
            vHeight: Int,
            textureWidth: Int,
            textureHeight: Int
        ) {
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            guiGraphics.blit(texture, x, y, width, height, u, v, uWidth, vHeight, textureWidth, textureHeight)
        }
    }
}
