package matheo1712.cobbletrainers.client.gui

import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.mojang.blaze3d.systems.RenderSystem
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.client.cache.TrainerSkinCache
import matheo1712.cobbletrainers.client.cache.TrainerTeamCache
import matheo1712.cobbletrainers.network.BattlePhoneEntry
import matheo1712.cobbletrainers.network.CallTrainerPayload
import matheo1712.cobbletrainers.network.OpenBattlePhonePayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW

/**
 * The battle phone screen: the trainers of the world, sorted by the datapack they come from,
 * and whether the player has beaten them.
 *
 * Laid out after the clamshell the frame draws - the roster fills the lower screen, two
 * entries to a line, and whoever is selected there gets the upper one to themselves. It is
 * dressed with the mod's own textures, under
 * `assets/cobblemon-trainers/textures/gui/battle_phone/`. They are deliberately plain - a
 * bezel, a slot, a couple of arrows, a status marker - and everything behind them is drawn
 * with flat rectangles, so replacing the set is a matter of redrawing six images at the same
 * sizes. [FRAME] is the one with a constraint: its two transparent holes have to line up with
 * [UPPER_X] and [LOWER_X] and their friends, since those holes are where the screens draw.
 *
 * The listing is whatever the server sent, and nothing is ever sent back beyond two questions:
 * the skin of a trainer, and the team of one the player has beaten. Both are cached - see
 * [TrainerSkinCache] and [TrainerTeamCache].
 */
class BattlePhoneScreen(data: OpenBattlePhonePayload) :
    Screen(CobblemonTrainers.lang("screen.battle_phone.title")) {

    /**
     * A line of the roster. A datapack heading only appears in the tab that holds every
     * datapack at once, where it is what makes the sort by pack visible; a tab that is already
     * one datapack has its name in the selector above. Category headings appear in both, and
     * in neither for a pack that files every trainer at its root. A [Row] holds a whole line
     * of entries, so one group never spills into the columns of the next.
     */
    private sealed class Row(val height: Int) {
        /**
         * A heading over a run of trainers: the datapack they come from in the tab that holds
         * every pack, the category they are filed under everywhere. [primary] is the first of
         * those two, and the only difference between them is how they are drawn.
         */
        class Header(
            val label: Component,
            val primary: Boolean,
            val defeated: Int,
            val total: Int
        ) : Row(HEADER_HEIGHT)
        class Trainers(val entries: List<BattlePhoneEntry>) : Row(ROW_HEIGHT)
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

    /**
     * What the frame is multiplied by to fit the window, and where it lands once it has been.
     *
     * The frame is a clamshell, so it is tall - taller than the interface Minecraft promises,
     * which is only 240 pixels once the GUI scale has been applied, and taller than the 270
     * that automatic scale leaves on a 1080p screen. Laying it out one for one would cut a
     * third of the phone off for most players, so everything is drawn through one pose and
     * the pose carries this factor. It never goes above 1: enlarging pixel art buys nothing.
     */
    private var uiScale = 1f
    private var left = 0
    private var top = 0

    private val group: Group
        get() = groups[groupIndex]

    override fun init() {
        uiScale = minOf(
            1f,
            (width - 2 * WINDOW_MARGIN).toFloat() / FRAME_WIDTH,
            (height - 2 * WINDOW_MARGIN).toFloat() / FRAME_HEIGHT
        )
        left = ((width - FRAME_WIDTH * uiScale) / 2f).toInt()
        top = ((height - FRAME_HEIGHT * uiScale) / 2f).toInt()

        if (selected == null) selected = groups.firstOrNull()?.entries?.firstOrNull()
    }

    /**
     * One tab per namespace, in alphabetical order, preceded by an "everything" tab. That
     * first tab is dropped when a single datapack ships trainers: it would be the same list
     * twice, and the selector already names the one datapack there is.
     */
    private fun buildGroups(entries: List<BattlePhoneEntry>): List<Group> {
        // The server sends trainers in reading order - by pack, then by category - so grouping
        // on either keeps every tab sorted without sorting anything again.
        val byNamespace = entries.groupBy { it.id.substringBefore(TRAINER_ID_SEPARATOR) }.toSortedMap()
        val groups = byNamespace.map { (namespace, group) -> Group(namespace, group, rowsOf(group)) }
        if (groups.size <= 1) return groups

        val allRows = byNamespace.flatMap { (namespace, group) ->
            listOf(header(Component.literal(namespace), primary = true, group)) + rowsOf(group)
        }
        return listOf(Group(null, entries, allRows)) + groups
    }

    /**
     * Cuts a datapack's trainers into lines of [LIST_COLUMNS], keeping the server's order and
     * putting a heading over each run of one category.
     *
     * A pack that files everything at its root gets no heading at all, which is the layout
     * this screen had before categories existed: a heading naming the only group there is
     * would be a line spent on nothing.
     */
    private fun rowsOf(entries: List<BattlePhoneEntry>): List<Row> {
        val runs = entries.groupConsecutiveBy { it.category }
        if (runs.size == 1 && runs.first().first.isEmpty()) return lines(entries)

        return runs.flatMap { (category, run) ->
            val label = when {
                category.isEmpty() -> UNCATEGORIZED_LABEL
                else -> Component.translatable(run.first().categoryName.ifEmpty { category })
            }
            listOf(header(label, primary = false, run)) + lines(run)
        }
    }

    /** A heading carries the score of the run below it - the same counter as the selector's. */
    private fun header(label: Component, primary: Boolean, run: List<BattlePhoneEntry>): Row.Header =
        Row.Header(label, primary, run.count { it.defeated }, run.size)

    private fun lines(entries: List<BattlePhoneEntry>): List<Row> =
        entries.chunked(LIST_COLUMNS).map { Row.Trainers(it) }

    /** Cuts a list into the runs of neighbours that answer the same key, order untouched. */
    private inline fun <T, K> List<T>.groupConsecutiveBy(key: (T) -> K): List<Pair<K, List<T>>> {
        val runs = mutableListOf<Pair<K, MutableList<T>>>()
        for (item in this) {
            val itemKey = key(item)
            if (runs.isEmpty() || runs.last().first != itemKey) runs.add(itemKey to mutableListOf())
            runs.last().second.add(item)
        }
        return runs.map { (k, items) -> k to items }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // Everything below is laid out in the frame's own pixels; the pose puts them on screen.
        // The cursor has to make the same trip, or a hover would answer somewhere else.
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(left.toFloat(), top.toFloat(), 0f)
        pose.scale(uiScale, uiScale, 1f)

        val frameMouseX = frameX(mouseX.toDouble()).toInt()
        val frameMouseY = frameY(mouseY.toDouble()).toInt()

        guiGraphics.fill(UPPER_X, UPPER_Y, (UPPER_X + UPPER_WIDTH), (UPPER_Y + UPPER_HEIGHT), COLOR_SCREEN)
        guiGraphics.fill(LOWER_X, LOWER_Y, (LOWER_X + LOWER_WIDTH), (LOWER_Y + LOWER_HEIGHT), COLOR_SCREEN)

        guiGraphics.drawCenteredString(font, title, (UPPER_X + UPPER_WIDTH / 2), TITLE_Y, COLOR_TITLE)

        var tooltip: Component? = null
        if (groups.isEmpty()) {
            guiGraphics.drawCenteredString(
                font,
                EMPTY_LABEL,
                (LOWER_X + LOWER_WIDTH / 2),
                (LOWER_Y + LOWER_HEIGHT / 2),
                COLOR_TEXT
            )
        } else {
            renderSelector(guiGraphics, frameMouseX, frameMouseY)
            renderList(guiGraphics, frameMouseX, frameMouseY)
            tooltip = renderDetails(guiGraphics, frameMouseX, frameMouseY, partialTick)
        }

        // The frame comes last: its rounded corners and brackets cut into the panels below.
        blit(guiGraphics, FRAME, 0, 0, FRAME_WIDTH, FRAME_HEIGHT, 0f, 0f, FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT)
        RenderSystem.disableBlend()

        pose.popPose()

        // The tooltip is drawn by the screen, not by us: it belongs at the cursor, at the
        // size everything else in the interface is, so it goes outside the pose.
        tooltip?.let { guiGraphics.renderTooltip(font, it, mouseX, mouseY) }
    }

    /**
     * The strip above the roster: the datapack being shown, flanked by its arrows, and how
     * much of it the player has beaten. The arrows hug the name rather than the edges of the
     * screen, which leaves the end of the strip free for the counter.
     */
    private fun renderSelector(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        // Both arrow images hold their hovered state below the idle one.
        if (groups.size > 1) {
            renderArrow(guiGraphics, ARROW_LEFT, leftArrowX(), overLeftArrow(mouseX.toDouble(), mouseY.toDouble()))
            renderArrow(guiGraphics, ARROW_RIGHT, rightArrowX(), overRightArrow(mouseX.toDouble(), mouseY.toDouble()))
        }
        val label = group.namespace?.let { Component.literal(it) } ?: ALL_LABEL
        guiGraphics.drawCenteredString(
            font,
            trim(label, 2 * SELECTOR_ARROW_GAP - 8),
            (LIST_X + LIST_WIDTH / 2),
            (SELECTOR_Y + 1),
            COLOR_TEXT
        )

        val defeated = group.entries.count { it.defeated }
        guiGraphics.drawCenteredString(
            font,
            CobblemonTrainers.lang("screen.battle_phone.progress", defeated, group.entries.size),
            (LIST_X + LIST_WIDTH - PROGRESS_INSET),
            (SELECTOR_Y + 1),
            COLOR_TEXT
        )
    }

    private fun renderArrow(guiGraphics: GuiGraphics, texture: ResourceLocation, arrowX: Int, hovered: Boolean) {
        val v = if (hovered) ARROW_HEIGHT.toFloat() else 0f
        blit(guiGraphics, texture, arrowX, SELECTOR_Y, ARROW_WIDTH, ARROW_HEIGHT, 0f, v, ARROW_WIDTH, ARROW_HEIGHT, ARROW_WIDTH, ARROW_HEIGHT * 2)
    }

    private fun renderList(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        // The scissor stack is not part of the pose: it wants real pixels, so the panel has
        // to make the trip itself. The extra pixel keeps rounding from shaving the last row.
        guiGraphics.enableScissor(
            screenX(LIST_X),
            screenY(PANEL_Y),
            screenX(LIST_X + LIST_WIDTH) + 1,
            screenY(PANEL_Y + PANEL_HEIGHT) + 1
        )
        forEachVisibleRow { row, rowY ->
            when (row) {
                is Row.Header -> renderRowHeader(guiGraphics, row, rowY)
                is Row.Trainers -> row.entries.forEachIndexed { column, entry ->
                    renderEntry(guiGraphics, entry, (LIST_X + column * COLUMN_WIDTH), rowY, mouseX, mouseY)
                }
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

    /**
     * The heading over a run of trainers, with a rule running to the edge of the panel. A
     * category sits a few pixels in and in a quieter colour than a datapack, so the two read
     * as two levels in the tab that shows both.
     */
    private fun renderRowHeader(guiGraphics: GuiGraphics, header: Row.Header, rowY: Int) {
        val textX = LIST_X + if (header.primary) 0 else HEADER_INDENT
        val textY = rowY + HEADER_HEIGHT - HEADER_PADDING_BOTTOM - font.lineHeight
        val color = if (header.primary) COLOR_HEADER else COLOR_SUBHEADER

        val score = CobblemonTrainers.lang(
            "screen.battle_phone.progress",
            header.defeated,
            header.total
        ).string
        val scoreX = LIST_X + LIST_WIDTH - font.width(score)
        guiGraphics.drawString(font, score, scoreX, textY, COLOR_TEXT_DIM)

        val label = trim(header.label, scoreX - textX - HEADER_SCORE_GAP)
        guiGraphics.drawString(font, label, textX, textY, color)

        val ruleX = textX + font.width(label) + 4
        val ruleY = textY + font.lineHeight / 2
        if (ruleX < scoreX - HEADER_SCORE_GAP) {
            guiGraphics.fill(ruleX, ruleY, scoreX - HEADER_SCORE_GAP, ruleY + 1, COLOR_HEADER_RULE)
        }
    }

    /** One entry of the roster, drawn from the left edge of the column it landed in. */
    private fun renderEntry(
        guiGraphics: GuiGraphics,
        entry: BattlePhoneEntry,
        slotX: Int,
        rowY: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val columnEnd = slotX + COLUMN_WIDTH

        blit(guiGraphics, SLOT, slotX, rowY, SLOT_SIZE, SLOT_SIZE, 0f, 0f, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE)

        val skin = TrainerSkinCache.get(entry.id)
        if (skin?.texture != null) {
            TrainerSkinRenderer.drawFace(guiGraphics, skin, slotX + 2, rowY + 2, SLOT_SIZE - 4)
        } else {
            guiGraphics.drawCenteredString(font, UNKNOWN, slotX + SLOT_SIZE / 2, rowY + 6, COLOR_TEXT_DIM)
        }

        // The outline goes over the head, the way a Pokédex draws it over the sprite.
        val hovered = mouseX >= slotX && mouseX < columnEnd && mouseY >= rowY && mouseY < rowY + SLOT_SIZE
        val selectionOffset = when {
            entry.id == selected?.id -> SLOT_TEXTURE_SIZE.toFloat()
            hovered -> 0f
            else -> null
        }
        if (selectionOffset != null) {
            blit(guiGraphics, SLOT_SELECT, slotX, rowY, SLOT_SIZE, SLOT_SIZE, 0f, selectionOffset, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE * 2)
        }

        val nameX = slotX + SLOT_SIZE + 5
        val nameWidth = columnEnd - nameX - MARKER_WIDTH - 4
        guiGraphics.drawString(
            font,
            trim(Component.translatable(entry.name), nameWidth),
            nameX,
            rowY + (SLOT_SIZE - font.lineHeight) / 2,
            when {
                entry.locked -> COLOR_TEXT_LOCKED
                entry.defeated -> COLOR_TEXT
                else -> COLOR_TEXT_DIM
            }
        )

        renderMarker(guiGraphics, entry.defeated, columnEnd - MARKER_WIDTH, rowY + (SLOT_SIZE - MARKER_HEIGHT) / 2)
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

        val barX = (LIST_X + LIST_WIDTH + 2)
        val barTop = PANEL_Y
        guiGraphics.fill(barX, barTop, barX + SCROLL_BAR_WIDTH, (PANEL_Y + PANEL_HEIGHT), COLOR_SCROLL_TRACK)

        val thumbHeight = maxOf(PANEL_HEIGHT / (maxScroll + 1), MIN_THUMB_HEIGHT)
        val thumbTop = barTop + (PANEL_HEIGHT - thumbHeight) * scroll / maxScroll
        guiGraphics.fill(barX, thumbTop, barX + SCROLL_BAR_WIDTH, thumbTop + thumbHeight, COLOR_SCROLL_THUMB)
    }

    /** @return the tooltip to draw over everything, if the mouse is on a team member. */
    private fun renderDetails(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float): Component? {
        val entry = selected ?: return null

        // Every line of text spans the screen rather than the figure's column: the status of a
        // trainer runs to a good seventy pixels, and centring that on a forty-eight pixel
        // figure pushed the marker out under their legs.
        val centerX = UPPER_X + UPPER_WIDTH / 2

        guiGraphics.drawCenteredString(
            font,
            trim(Component.translatable(entry.name), UPPER_WIDTH - 8),
            (UPPER_X + UPPER_WIDTH / 2),
            NAME_Y,
            COLOR_TITLE
        )

        val skin = TrainerSkinCache.get(entry.id)
        if (skin?.texture != null) {
            TrainerSkinRenderer.drawFigure(guiGraphics, skin, FIGURE_CENTER_X, PORTRAIT_TOP, FIGURE_SCALE)
        } else {
            guiGraphics.drawCenteredString(
                font,
                UNKNOWN,
                FIGURE_CENTER_X,
                (PORTRAIT_TOP + TrainerSkinRenderer.FIGURE_HEIGHT * FIGURE_SCALE / 2),
                COLOR_TEXT_DIM
            )
        }

        val status = CobblemonTrainers.lang(statusKey(entry))
        val statusWidth = MARKER_WIDTH + MARKER_TEXT_GAP + font.width(status)
        renderMarker(guiGraphics, entry.defeated, centerX - statusWidth / 2, STATUS_Y - MARKER_LINE_OFFSET)
        guiGraphics.drawString(
            font,
            status,
            centerX - statusWidth / 2 + MARKER_WIDTH + MARKER_TEXT_GAP,
            STATUS_Y,
            COLOR_TEXT
        )

        guiGraphics.drawCenteredString(
            font,
            CobblemonTrainers.lang("screen.battle_phone.team", entry.level, entry.teamSize).string,
            centerX,
            TEAM_LINE_Y,
            COLOR_TEXT_DIM
        )

        renderLocation(guiGraphics, entry)
        val callTooltip = renderCallButton(guiGraphics, entry, mouseX, mouseY)

        // A locked trainer has no team to show - the server does not send one - so the space
        // the party would take says what it would take to unlock it instead.
        if (entry.locked) renderRequirements(guiGraphics, entry)

        // Then the item renderer, which flushes the batch and manages the depth state itself,
        // so it comes after everything drawn with fills and text.
        val rewardTooltip = renderRewards(guiGraphics, entry, mouseX, mouseY)

        // Models last of all: they render through their own buffer, so nothing of ours is in
        // flight. Drawn whatever the tooltips say - short-circuiting on one would stop the team
        // rendering as soon as the cursor rested elsewhere, which reads as the party blinking
        // out.
        val teamTooltip = if (entry.locked) null else renderTeam(guiGraphics, entry, mouseX, mouseY, partialTick)

        return callTooltip ?: rewardTooltip ?: teamTooltip
    }

    /**
     * Where the trainer is to be found, on its own plate in the strip the team leaves free.
     *
     * A plate rather than a floating caption: the line sits between the party above and the
     * status below, and loose small text there read as something that had slipped out of one of
     * them. The accent bar down its left edge is what makes it a field with a value rather than
     * a sentence - the same blue that heads a category in the roster.
     *
     * It spans the team rather than the screen: a place reads long - a biome, a time and a sky
     * add up - and a plate centred on the whole width would reach under the legs of the figure.
     * A trainer who names no place draws nothing here.
     */
    private fun renderLocation(guiGraphics: GuiGraphics, entry: BattlePhoneEntry) {
        if (entry.location.string.isEmpty()) return

        val areaWidth = TEAM_COLUMNS * TEAM_CELL_WIDTH
        plate(guiGraphics, TEAM_X, LOCATION_TOP, areaWidth, LOCATION_HEIGHT, COLOR_PLATE, COLOR_PLATE_EDGE)
        guiGraphics.fill(
            TEAM_X + LOCATION_ACCENT_INSET,
            LOCATION_TOP + LOCATION_ACCENT_INSET,
            TEAM_X + LOCATION_ACCENT_INSET + LOCATION_ACCENT_WIDTH,
            LOCATION_TOP + LOCATION_HEIGHT - LOCATION_ACCENT_INSET,
            COLOR_HEADER
        )

        // Centred on what is left of the plate once the accent has taken its edge, so the text
        // does not read as pushed off centre.
        val textLeft = TEAM_X + LOCATION_TEXT_INSET
        val textWidth = areaWidth - LOCATION_TEXT_INSET - LOCATION_ACCENT_INSET
        guiGraphics.drawCenteredString(
            font,
            trim(
                CobblemonTrainers.lang("screen.battle_phone.location", entry.location),
                textWidth
            ),
            textLeft + textWidth / 2,
            LOCATION_Y,
            COLOR_TEXT
        )
    }

    /**
     * What beating this trainer hands over: a count, then the item, one reward per line, in the
     * strip between the figure and the team.
     *
     * That strip is the only space on the upper screen that was ever free, which is why the
     * figure was moved a few pixels left to open it up rather than the rewards being squeezed
     * into a band below - there is no band below, the screen ends four pixels under the status
     * line. Reading down the side of the trainer also puts what they give you next to who they
     * are, which is where it belongs.
     *
     * Rewards are shown before the trainer has been beaten, unlike their team: a team is a
     * reward for winning, a reward is the reason to try.
     */
    private fun renderRewards(
        guiGraphics: GuiGraphics,
        entry: BattlePhoneEntry,
        mouseX: Int,
        mouseY: Int
    ): Component? {
        if (entry.rewards.isEmpty()) return null

        // One line is given up to say how many were left out, so the fiche never quietly
        // shortens a long reward list.
        val overflowing = entry.rewards.size > REWARD_ROWS
        val shown = if (overflowing) REWARD_ROWS - 1 else entry.rewards.size
        var tooltip: Component? = null

        for (index in 0 until shown) {
            val stack = entry.rewards[index]
            val rowY = REWARD_TOP + index * REWARD_ROW_HEIGHT

            drawSmallRight(
                guiGraphics,
                CobblemonTrainers.lang("screen.battle_phone.reward_count", stack.count).string,
                REWARD_X + REWARD_COUNT_WIDTH,
                rowY + REWARD_TEXT_OFFSET,
                COLOR_TEXT
            )
            guiGraphics.renderItem(stack, REWARD_X + REWARD_ICON_OFFSET, rowY)

            val hovered = mouseX >= REWARD_X && mouseX < REWARD_X + REWARD_WIDTH &&
                mouseY >= rowY && mouseY < rowY + ITEM_SIZE
            if (hovered) {
                tooltip = CobblemonTrainers.lang(
                    "screen.battle_phone.reward", stack.count, stack.hoverName
                )
            }
        }

        if (overflowing) {
            drawSmall(
                guiGraphics,
                CobblemonTrainers.lang(
                    "screen.battle_phone.reward_more",
                    entry.rewards.size - shown
                ).string,
                REWARD_X + REWARD_WIDTH / 2,
                REWARD_TOP + shown * REWARD_ROW_HEIGHT + REWARD_TEXT_OFFSET,
                COLOR_TEXT_DIM
            )
        }

        return tooltip
    }

    /**
     * The call button, and the tooltip that explains a greyed out one.
     *
     * Three states, and the middle one is the point: a trainer who takes no rematch keeps a
     * visible button, so "why can I not call them" is answered on the screen rather than only
     * in the chat once the player has tried.
     *
     * Whether the player is standing in the right place is never decided here - the client
     * knows how a place reads, not where it is. Pressing the button somewhere else is answered
     * by the server, in words.
     */
    private fun renderCallButton(
        guiGraphics: GuiGraphics,
        entry: BattlePhoneEntry,
        mouseX: Int,
        mouseY: Int
    ): Component? {
        if (!callable(entry)) return null

        val enabled = callEnabled(entry)
        val hovered = overCallButton(mouseX.toDouble(), mouseY.toDouble())

        val border = when {
            !enabled -> COLOR_PLATE_EDGE
            hovered -> COLOR_TITLE
            else -> COLOR_HEADER
        }
        plate(guiGraphics, CALL_X, CALL_Y, CALL_WIDTH, CALL_HEIGHT, COLOR_PLATE, border)

        // A key has a lit face and a shadow under it. Two flat colours would do neither, and a
        // single fill made the button read as a hole in the screen rather than something to
        // press - which matters, since it is the only thing on this screen that is pressed.
        if (enabled) {
            val top = if (hovered) COLOR_CALL_TOP_HOVER else COLOR_CALL_TOP
            val bottom = if (hovered) COLOR_CALL_BOTTOM_HOVER else COLOR_CALL_BOTTOM
            guiGraphics.fillGradient(
                CALL_X + CALL_BORDER,
                CALL_Y + CALL_BORDER,
                CALL_X + CALL_WIDTH - CALL_BORDER,
                CALL_Y + CALL_HEIGHT - CALL_BORDER,
                top,
                bottom
            )
            // One lit line along the top edge, the light coming from above like everywhere else.
            guiGraphics.fill(
                CALL_X + CALL_BORDER + 1,
                CALL_Y + CALL_BORDER,
                CALL_X + CALL_WIDTH - CALL_BORDER - 1,
                CALL_Y + CALL_BORDER + 1,
                COLOR_CALL_HIGHLIGHT
            )
        }

        guiGraphics.drawCenteredString(
            font,
            CobblemonTrainers.lang("screen.battle_phone.call"),
            CALL_X + CALL_WIDTH / 2,
            CALL_Y + CALL_LABEL_INSET,
            if (enabled) COLOR_TITLE else COLOR_TEXT_LOCKED
        )

        if (!hovered) return null
        return if (enabled) {
            CobblemonTrainers.lang("screen.battle_phone.call.where", entry.location)
        } else {
            CobblemonTrainers.lang("screen.battle_phone.call.no_rematch")
        }
    }

    /** A trainer the phone draws a button for at all: one who says where they can be found. */
    private fun callable(entry: BattlePhoneEntry): Boolean = entry.callable && !entry.locked

    /** A button that answers when pressed, as opposed to one drawn to say why it will not. */
    private fun callEnabled(entry: BattlePhoneEntry): Boolean =
        callable(entry) && !(entry.defeated && !entry.rematch)

    /**
     * The six team slots, filled only once the player has beaten the trainer - the server
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
            val cellX = (TEAM_X + (slot % TEAM_COLUMNS) * TEAM_CELL_WIDTH)
            val cellY = (TEAM_TOP + (slot / TEAM_COLUMNS) * TEAM_CELL_HEIGHT)

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

    /**
     * What this player still has to do, drawn where the team would be. The lines come from the
     * server already built as components, so they are read here in the player's own language
     * without the screen knowing what a requirement is.
     */
    private fun renderRequirements(guiGraphics: GuiGraphics, entry: BattlePhoneEntry) {
        val areaWidth = TEAM_COLUMNS * TEAM_CELL_WIDTH
        val areaHeight = TEAM_ROWS * TEAM_CELL_HEIGHT
        val wrapWidth = areaWidth - 2 * REQUIREMENT_INSET

        val heading = font.split(CobblemonTrainers.lang("screen.battle_phone.locked"), wrapWidth)
        val lines = entry.requirements.flatMap { requirement ->
            font.split(CobblemonTrainers.lang("requirement.line", requirement), wrapWidth)
        }

        val total = heading.size + lines.size
        var lineY = TEAM_TOP + (areaHeight - total * font.lineHeight) / 2
        val centerX = TEAM_X + areaWidth / 2

        (heading.map { it to COLOR_TEXT } + lines.map { it to COLOR_TEXT_DIM }).forEach { (line, color) ->
            guiGraphics.drawString(font, line, centerX - font.width(line) / 2, lineY, color)
            lineY += font.lineHeight
        }
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
        entry.defeated && !entry.rematch -> "screen.battle_phone.status.defeated_final"
        entry.defeated -> "screen.battle_phone.status.defeated"
        entry.locked -> "screen.battle_phone.status.locked"
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

    /**
     * The same, ending at [rightX] instead of centred on it.
     *
     * A reward count has to sit against its item however wide it is: centring would leave a
     * single digit floating and a four digit count leaning on the icon.
     */
    private fun drawSmallRight(guiGraphics: GuiGraphics, text: String, rightX: Int, textY: Int, color: Int) {
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(rightX.toFloat(), textY.toFloat(), 0f)
        guiGraphics.pose().scale(SMALL_TEXT_SCALE, SMALL_TEXT_SCALE, 1f)
        guiGraphics.drawString(font, text, -font.width(text), 0, color)
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

        val frameX = frameX(mouseX)
        val frameY = frameY(mouseY)

        if (overLeftArrow(frameX, frameY)) return selectGroup(-1)
        if (overRightArrow(frameX, frameY)) return selectGroup(1)

        // Before the roster: the button sits on the upper screen, which the test below leaves.
        selected?.let { entry ->
            if (callEnabled(entry) && overCallButton(frameX, frameY)) return callTrainer(entry)
        }

        if (frameX < LIST_X || frameX >= (LIST_X + LIST_WIDTH)) return false

        val column = (frameX.toInt() - LIST_X) / COLUMN_WIDTH

        var clicked = false
        forEachVisibleRow { row, rowY ->
            if (row is Row.Trainers && frameY >= rowY && frameY < (rowY + row.height)) {
                // A short last line leaves its trailing columns empty, and they answer nothing.
                row.entries.getOrNull(column)?.let {
                    selected = it
                    clicked = true
                }
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

    private fun leftArrowX() = (LIST_X + LIST_WIDTH / 2 - SELECTOR_ARROW_GAP - ARROW_WIDTH)

    private fun rightArrowX() = (LIST_X + LIST_WIDTH / 2 + SELECTOR_ARROW_GAP)

    /**
     * Asks the server for that trainer, and gets out of the way.
     *
     * The screen closes rather than waits: the trainer arrives a walk away, and the answer -
     * their coordinates, or the reason there are none - is a chat message the player cannot
     * read through an open phone.
     */
    private fun callTrainer(entry: BattlePhoneEntry): Boolean {
        if (!ClientPlayNetworking.canSend(CallTrainerPayload.TYPE)) return false

        Minecraft.getInstance().soundManager.play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f)
        )
        ClientPlayNetworking.send(CallTrainerPayload(entry.id))
        onClose()
        return true
    }

    /** Hit box of the call button, which is the rectangle it is drawn in. */
    private fun overCallButton(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= CALL_X && mouseX < CALL_X + CALL_WIDTH &&
            mouseY >= CALL_Y && mouseY < CALL_Y + CALL_HEIGHT

    private fun overLeftArrow(mouseX: Double, mouseY: Double): Boolean =
        overArrow(leftArrowX(), mouseX, mouseY)

    private fun overRightArrow(mouseX: Double, mouseY: Double): Boolean =
        overArrow(rightArrowX(), mouseX, mouseY)

    /**
     * The hit box of an arrow, which is the same one the hovered state is drawn from - a
     * button that lights up somewhere other than where it answers is worse than no highlight.
     * It is a couple of pixels wider than the image: an arrow is a thin thing to aim at.
     */
    private fun overArrow(arrowX: Int, mouseX: Double, mouseY: Double): Boolean =
        groups.size > 1 &&
            mouseX >= arrowX - CLICK_PADDING && mouseX < arrowX + ARROW_WIDTH + CLICK_PADDING &&
            mouseY >= SELECTOR_Y - CLICK_PADDING && mouseY < (SELECTOR_Y + ARROW_HEIGHT) + CLICK_PADDING

    /** Moves to another datapack tab, always landing on a valid one. */
    private fun selectGroup(step: Int): Boolean {
        groupIndex = Math.floorMod(groupIndex + step, groups.size)
        scroll = 0
        selected = group.entries.firstOrNull()
        return true
    }

    /**
     * A window coordinate in the frame's own pixels, the space every hit box here is in.
     * [frameY] can land above the frame or below it, which simply matches nothing.
     */
    private fun frameX(windowX: Double) = (windowX - left) / uiScale

    private fun frameY(windowY: Double) = (windowY - top) / uiScale

    /** The other direction, for the scissor stack, which the pose does not reach. */
    private fun screenX(offset: Int) = left + (offset * uiScale).toInt()

    private fun screenY(offset: Int) = top + (offset * uiScale).toInt()

    /**
     * Consulting the phone does not stop the world.
     *
     * A screen pauses the integrated server by default, which is right for a menu and wrong
     * here: the phone is something a player pulls out mid-adventure, and it would otherwise
     * behave differently in single player and in multiplayer, where nothing ever pauses.
     */
    override fun isPauseScreen(): Boolean = false

    private companion object {
        val FRAME: ResourceLocation = phoneTexture("frame")
        val SLOT: ResourceLocation = phoneTexture("slot")
        val SLOT_SELECT: ResourceLocation = phoneTexture("slot_selected")
        val MARKER: ResourceLocation = phoneTexture("marker")
        val ARROW_LEFT: ResourceLocation = phoneTexture("arrow_left")
        val ARROW_RIGHT: ResourceLocation = phoneTexture("arrow_right")

        /** Size of the frame image, which the whole screen is laid out inside of. */
        const val FRAME_WIDTH = 378
        const val FRAME_HEIGHT = 392

        /**
         * The two holes in the frame. The phone is a clamshell with a screen in each half:
         * the upper one is the fiche of whoever is selected, the lower one the roster it is
         * selected from. Both have to line up with the transparent zones of [FRAME].
         */
        const val UPPER_X = 51
        const val UPPER_Y = 25
        const val UPPER_WIDTH = 306
        const val UPPER_HEIGHT = 150

        const val LOWER_X = 51
        const val LOWER_Y = 202
        const val LOWER_WIDTH = 309
        const val LOWER_HEIGHT = 179

        // The lower screen: a datapack selector over the roster.
        const val LIST_X = LOWER_X + 4
        const val LIST_WIDTH = 296

        /**
         * The roster is two entries wide. A column is exactly what a row used to be when the
         * list shared one screen with the fiche, so an entry is laid out the same; the second
         * screen buys twice as many of them on show rather than wider ones.
         */
        const val LIST_COLUMNS = 2
        const val COLUMN_WIDTH = LIST_WIDTH / LIST_COLUMNS

        const val SELECTOR_Y = LOWER_Y + 5

        /** How far the arrows sit either side of the datapack name they page through. */
        const val SELECTOR_ARROW_GAP = 70

        /** Where the progress counter is centred, measured back from the end of the list. */
        const val PROGRESS_INSET = 32

        const val PANEL_Y = LOWER_Y + 20
        const val PANEL_HEIGHT = LOWER_HEIGHT - 24
        const val ROW_HEIGHT = 24

        /**
         * A heading and the air around it, the 9 being the height of a line of text.
         *
         * A heading belongs to the run *below* it, so it has to end up closer to the group it
         * opens than to the one it closes - and what separates it from the group above is not
         * [HEADER_PADDING_TOP] alone, the row up there carrying its own slack ([ROW_HEIGHT] is
         * taller than [SLOT_SIZE]). Counting that in, the gap above is 9 pixels against 6
         * below, which is why the two numbers here read the wrong way round.
         */
        const val HEADER_PADDING_TOP = 5
        const val HEADER_PADDING_BOTTOM = 6
        const val HEADER_HEIGHT = HEADER_PADDING_TOP + 9 + HEADER_PADDING_BOTTOM

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

        /*
         * The upper screen, read top to bottom: the phone's title, the trainer's name, what
         * their team is worth, then the trainer themselves beside that team, and their status
         * along the bottom. The three lines of text are banded across the full width and the
         * two pictures share the middle, which is what keeps a long status clear of the
         * figure - they used to share a column, and the marker ended up behind their legs.
         */
        const val TITLE_Y = UPPER_Y + 4
        const val NAME_Y = UPPER_Y + 15
        const val TEAM_LINE_Y = UPPER_Y + 27
        const val PORTRAIT_TOP = UPPER_Y + 38

        /**
         * The figure sits left of centre in its column rather than in the middle of it: what it
         * gives up is the strip the rewards are drawn in, and there is nowhere else on this
         * screen to put them. Twenty-eight leaves four pixels between the figure and the edge of
         * the panel, which is the least that still reads as a margin.
         */
        const val FIGURE_CENTER_X = UPPER_X + 28
        const val FIGURE_SCALE = 3
        const val STATUS_Y = UPPER_Y + 137

        /**
         * The plate saying where the trainer is, in the strip the team leaves free above the
         * status line. Twelve pixels tall, which is one line of text and its air - so this
         * stays one line, and a place too long for it is trimmed rather than wrapped. It starts
         * a pixel above the team rather than under it: the bottom row of cells is taller than
         * the models it holds, so that pixel is empty, and taking it keeps two clear of the call
         * button below.
         */
        const val LOCATION_TOP = UPPER_Y + 121
        const val LOCATION_HEIGHT = 12
        const val LOCATION_Y = LOCATION_TOP + 2

        /** The accent bar down the left edge of that plate, and the air around it. */
        const val LOCATION_ACCENT_INSET = 2
        const val LOCATION_ACCENT_WIDTH = 2

        /** Where the text starts, clear of the accent bar. */
        const val LOCATION_TEXT_INSET = LOCATION_ACCENT_INSET + LOCATION_ACCENT_WIDTH + 3

        /**
         * The call button, at the end of the status band.
         *
         * The status line is centred, so the far right of that band is the only clear stretch
         * left on the upper screen: four pixels separate it from the bezel below, and the team
         * takes everything above. It is drawn with rectangles, like everything here that is not
         * one of the six textures - which is what keeps that set replaceable.
         */
        const val CALL_WIDTH = 62
        const val CALL_HEIGHT = 13
        const val CALL_BORDER = 1
        const val CALL_X = UPPER_X + UPPER_WIDTH - CALL_WIDTH - 6

        /**
         * Two pixels above the status line, which puts the label of the button on exactly the
         * baseline of that line and leaves a pixel of air under the location plate.
         */
        const val CALL_Y = STATUS_Y - 2

        /** Lifts the label off the bottom edge of the button, the 8 being a line of text. */
        const val CALL_LABEL_INSET = (CALL_HEIGHT - 8) / 2

        const val TEAM_SLOTS = 6
        const val TEAM_COLUMNS = 3
        const val TEAM_ROWS = TEAM_SLOTS / TEAM_COLUMNS
        const val TEAM_X = UPPER_X + 92
        const val TEAM_TOP = UPPER_Y + 46
        const val TEAM_CELL_WIDTH = 70
        const val TEAM_CELL_HEIGHT = 38
        const val TEAM_SLOT_SIZE = 34

        /**
         * The reward column, in the strip between the figure and the team, one reward to a line.
         *
         * A line is a count and an item side by side, the count ending where the icon begins.
         * It starts level with the team, so the two columns of what a trainer is worth line up.
         * Five lines reach the status band and stop; a sixth would be spent saying how many
         * were left out rather than on a sixth item.
         *
         * Declared after [TEAM_TOP] because it reads it - a const cannot look ahead.
         */
        const val ITEM_SIZE = 16
        const val REWARD_X = UPPER_X + 56
        const val REWARD_TOP = TEAM_TOP
        const val REWARD_COUNT_WIDTH = 14
        const val REWARD_ICON_GAP = 2
        const val REWARD_ICON_OFFSET = REWARD_COUNT_WIDTH + REWARD_ICON_GAP
        const val REWARD_WIDTH = REWARD_ICON_OFFSET + ITEM_SIZE
        const val REWARD_ROW_HEIGHT = 18
        const val REWARD_ROWS = 5

        /** Drops the count onto the middle of its icon, the 6 being a line of small text. */
        const val REWARD_TEXT_OFFSET = (ITEM_SIZE - 6) / 2

        /**
         * How a model is sized, copied from Cobblemon's own party slots: a scale on the pose
         * and another passed to the renderer, which multiply. Neither is arbitrary - the
         * second one alone leaves a Pokémon a few pixels tall, and the pair keeps the depth
         * squash Cobblemon's portraits have, since only the first applies to z.
         */
        const val TEAM_POSE_SCALE = 2.5f
        const val TEAM_MODEL_SCALE = 4.5f

        /**
         * Where the model hangs from, measured from the top of its cell. A model is drawn
         * downwards from that point over roughly the height of a slot, so this centres it.
         */
        const val TEAM_MODEL_TOP = 6

        /** The three-quarter view Cobblemon uses for a Pokémon portrait. */
        val MODEL_ROTATION: Vector3f = Vector3f(13f, 35f, 0f)

        const val SMALL_TEXT_SCALE = 0.75f

        /** A couple of pixels of slack around the arrows, which are thin things to aim at. */
        const val CLICK_PADDING = 2

        /** Breathing room between the phone and the edge of the window, in window pixels. */
        const val WINDOW_MARGIN = 4

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
        const val COLOR_SUBHEADER = 0xFF7FC4E8.toInt()
        const val COLOR_HEADER_RULE = 0x665AAAEB
        const val COLOR_TEXT_LOCKED = 0xFF6A8AA3.toInt()

        /**
         * The plates: a shade below the screen so a box reads as recessed into it, and an edge a
         * shade above so it still has an outline. Used by the location plate and by the shell of
         * the call button, which is what makes the two read as one family.
         */
        const val COLOR_PLATE = 0xFF102A3E.toInt()
        const val COLOR_PLATE_EDGE = 0xFF27567A.toInt()

        /**
         * The face of the call button, lit from above. The hover pair is the same two colours
         * brightened rather than a different hue: hovering has to read as the same key with a
         * light on it, not as another control.
         */
        const val COLOR_CALL_TOP = 0xFF2A7BB4.toInt()
        const val COLOR_CALL_BOTTOM = 0xFF17527C.toInt()
        const val COLOR_CALL_TOP_HOVER = 0xFF43A0DC.toInt()
        const val COLOR_CALL_BOTTOM_HOVER = 0xFF2270A4.toInt()
        const val COLOR_CALL_HIGHLIGHT = 0x66FFFFFF

        /** How far a category heading sits in from the datapack heading above it. */
        const val HEADER_INDENT = 6

        /** Clearance kept between a heading and the score at the end of its line. */
        const val HEADER_SCORE_GAP = 4

        /** Breathing room either side of the requirement lines, which wrap. */
        const val REQUIREMENT_INSET = 4

        const val ELLIPSIS = "…"

        /** Stands in for a skin or a team member that is missing, hidden, or still on its way. */
        const val UNKNOWN = "?"

        val ALL_LABEL: Component = CobblemonTrainers.lang("screen.battle_phone.all")
        val UNCATEGORIZED_LABEL: Component = CobblemonTrainers.lang("category.uncategorized")
        val EMPTY_LABEL: Component = CobblemonTrainers.lang("screen.battle_phone.empty")

        fun phoneTexture(name: String): ResourceLocation =
            CobblemonTrainers.id("textures/gui/battle_phone/$name.png")

        /**
         * A bordered box with its four corner pixels knocked out, which is how everything on
         * these screens gets rounded corners without a texture.
         *
         * The corners are painted back in [COLOR_SCREEN] rather than left alone: the panel
         * behind is a flat fill of exactly that colour, so putting it back is what makes the
         * corner disappear. A box drawn anywhere else would show four dots of the wrong blue.
         */
        fun plate(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, fill: Int, border: Int) {
            guiGraphics.fill(x, y, x + width, y + height, border)
            guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill)

            guiGraphics.fill(x, y, x + 1, y + 1, COLOR_SCREEN)
            guiGraphics.fill(x + width - 1, y, x + width, y + 1, COLOR_SCREEN)
            guiGraphics.fill(x, y + height - 1, x + 1, y + height, COLOR_SCREEN)
            guiGraphics.fill(x + width - 1, y + height - 1, x + width, y + height, COLOR_SCREEN)
        }

        /**
         * A blit with blending on.
         *
         * `GuiGraphics.blit` leaves the blend state to its caller, and every texture here has
         * transparent pixels - the frame most of all, which is drawn over the content. Enabling
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
