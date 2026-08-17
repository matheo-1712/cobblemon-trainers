package matheo1712.cobbletrainers.client

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.block.TrainerSpawnerBlockEntity
import matheo1712.cobbletrainers.network.ConfigureTrainerSpawnerPayload
import matheo1712.cobbletrainers.network.OpenTrainerSpawnerPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * The screen behind a right-click on a trainer spawner.
 *
 * Laid out after the command block screen: a field for the value that matters, a couple of
 * settings, and Done / Cancel. The list under the field is the loaded trainers, sent by the
 * server — clicking one fills the field, which also accepts a hand-typed ID so a trainer from
 * a datapack that is momentarily disabled can still be set.
 *
 * Nothing is applied here. Done sends the three values back and the server decides.
 */
class TrainerSpawnerScreen(private val data: OpenTrainerSpawnerPayload) :
    Screen(CobblemonTrainers.lang("screen.trainer_spawner.title")) {

    private var trainerValue: String = data.trainerId
    private var radiusValue: String = data.leashRadius.toString()
    private var delayValue: String = data.respawnDelaySeconds.toString()

    private var panelLeft: Int = 0

    private lateinit var trainerBox: EditBox
    private lateinit var radiusBox: EditBox
    private lateinit var delayBox: EditBox
    private lateinit var trainerList: TrainerList

    /** Guards the field's responder while the list writes into it, so entries survive the click. */
    private var writingFromList = false

    override fun init() {
        panelLeft = (width - PANEL_WIDTH) / 2
        val listTop = 52
        val listBottom = height - 76

        trainerBox = EditBox(font, panelLeft, 30, PANEL_WIDTH, 18, TRAINER_LABEL)
        trainerBox.setMaxLength(256)
        trainerBox.setHint(CobblemonTrainers.lang("screen.trainer_spawner.trainer.hint"))
        trainerBox.value = trainerValue
        trainerBox.setResponder { value ->
            trainerValue = value
            if (!writingFromList) trainerList.refresh(value)
        }
        addRenderableWidget(trainerBox)
        setInitialFocus(trainerBox)

        trainerList = TrainerList(minecraft!!, PANEL_WIDTH, (listBottom - listTop).coerceAtLeast(ROW_HEIGHT), listTop)
        trainerList.setX(panelLeft)
        addRenderableWidget(trainerList)

        radiusBox = numberBox(panelLeft, height - 56, radiusValue) { radiusValue = it }
        delayBox = numberBox(panelLeft + HALF_WIDTH + GAP, height - 56, delayValue) { delayValue = it }

        addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE) { commit() }
                .bounds(panelLeft, height - 28, HALF_WIDTH, 20)
                .build()
        )
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL) { onClose() }
                .bounds(panelLeft + HALF_WIDTH + GAP, height - 28, HALF_WIDTH, 20)
                .build()
        )

        trainerList.refresh(trainerValue)
        trainerList.selectExactly(trainerValue)
    }

    private fun numberBox(x: Int, y: Int, initial: String, onChange: (String) -> Unit): EditBox {
        val box = EditBox(font, x, y, HALF_WIDTH, 18, CommonComponents.EMPTY)
        box.setMaxLength(6)
        box.setFilter { it.isEmpty() || it.toIntOrNull() != null }
        box.value = initial
        box.setResponder(onChange)
        return addRenderableWidget(box)
    }

    private fun commit() {
        ClientPlayNetworking.send(
            ConfigureTrainerSpawnerPayload(
                pos = data.pos,
                trainerId = trainerValue.trim(),
                leashRadius = radiusValue.toIntOrNull() ?: TrainerSpawnerBlockEntity.DEFAULT_LEASH_RADIUS,
                respawnDelaySeconds = delayValue.toIntOrNull()
                    ?: TrainerSpawnerBlockEntity.DEFAULT_RESPAWN_DELAY_SECONDS
            )
        )
        onClose()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true
        if (keyCode != GLFW.GLFW_KEY_ENTER && keyCode != GLFW.GLFW_KEY_KP_ENTER) return false

        commit()
        return true
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        guiGraphics.drawCenteredString(font, title, width / 2, 12, TITLE_COLOR)
        guiGraphics.drawString(font, TRAINER_LABEL, panelLeft, 20, LABEL_COLOR)
        guiGraphics.drawString(font, RADIUS_LABEL, radiusBox.x, radiusBox.y - 10, LABEL_COLOR)
        guiGraphics.drawString(font, DELAY_LABEL, delayBox.x, delayBox.y - 10, LABEL_COLOR)
    }

    /** The trainers the server sent, filtered by whatever is in the field. */
    private inner class TrainerList(minecraft: Minecraft, width: Int, height: Int, y: Int) :
        ObjectSelectionList<TrainerEntry>(minecraft, width, height, y, ROW_HEIGHT) {

        override fun getRowWidth(): Int = width - 10

        fun refresh(filter: String) {
            clearEntries()
            val needle = filter.trim().lowercase()
            data.trainers
                .filter { needle.isEmpty() || needle in it.lowercase() }
                .forEach { addEntry(TrainerEntry(it)) }
            setScrollAmount(0.0)
        }

        /** Highlights the trainer the block already points at, and scrolls it into view. */
        fun selectExactly(id: String) {
            val entry = children().firstOrNull { it.id == id } ?: return
            setSelected(entry)
            centerScrollOn(entry)
        }

        fun choose(entry: TrainerEntry) {
            writingFromList = true
            trainerBox.value = entry.id
            writingFromList = false
        }
    }

    private inner class TrainerEntry(val id: String) : ObjectSelectionList.Entry<TrainerEntry>() {

        override fun render(
            guiGraphics: GuiGraphics,
            index: Int,
            top: Int,
            left: Int,
            width: Int,
            height: Int,
            mouseX: Int,
            mouseY: Int,
            hovering: Boolean,
            partialTick: Float
        ) {
            guiGraphics.drawString(font, id, left + 2, top + 2, if (hovering) TITLE_COLOR else ENTRY_COLOR)
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            val handled = super.mouseClicked(mouseX, mouseY, button)
            trainerList.choose(this)
            return handled
        }

        override fun getNarration(): Component = Component.literal(id)
    }

    private companion object {
        const val PANEL_WIDTH = 300
        const val GAP = 4
        const val HALF_WIDTH = (PANEL_WIDTH - GAP) / 2
        const val ROW_HEIGHT = 12

        const val TITLE_COLOR = 0xFFFFFF
        const val LABEL_COLOR = 0xA0A0A0
        const val ENTRY_COLOR = 0xD0D0D0

        val TRAINER_LABEL: Component = CobblemonTrainers.lang("screen.trainer_spawner.trainer")
        val RADIUS_LABEL: Component = CobblemonTrainers.lang("screen.trainer_spawner.radius")
        val DELAY_LABEL: Component = CobblemonTrainers.lang("screen.trainer_spawner.delay")
    }
}
