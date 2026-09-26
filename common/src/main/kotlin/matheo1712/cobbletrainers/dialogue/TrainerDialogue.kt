package matheo1712.cobbletrainers.dialogue

import com.cobblemon.mod.common.api.dialogue.ActiveDialogue
import com.cobblemon.mod.common.api.dialogue.Dialogue
import com.cobblemon.mod.common.api.dialogue.DialogueAction
import com.cobblemon.mod.common.api.dialogue.DialogueManager
import com.cobblemon.mod.common.api.dialogue.DialoguePage
import com.cobblemon.mod.common.api.dialogue.DialogueSpeaker
import com.cobblemon.mod.common.api.dialogue.DialogueText
import com.cobblemon.mod.common.api.dialogue.FunctionDialogueAction
import com.cobblemon.mod.common.api.dialogue.ReferenceDialogueFaceProvider
import com.cobblemon.mod.common.api.dialogue.WrappedDialogueText
import com.cobblemon.mod.common.api.dialogue.input.DialogueInput
import com.cobblemon.mod.common.api.dialogue.input.DialogueNoInput
import com.cobblemon.mod.common.api.dialogue.input.DialogueOption
import com.cobblemon.mod.common.api.dialogue.input.DialogueOptionSetInput
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.activeDialogue
import com.cobblemon.mod.common.util.isInBattle
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.battle.TrainerBattleInteraction
import matheo1712.cobbletrainers.trainers.TrainerDefinition
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * Everything a trainer says, drawn in Cobblemon's own dialogue box rather than sent to chat.
 *
 * This is the box Cobblemon's `standard` NPC uses through its `npc-example` dialogue: a
 * portrait, a name plate, the line, and a row of choices. The difference is that nothing here
 * is read from a `dialogues/` JSON. A trainer's lines live in its own file, next to its team
 * and its rewards, and the choices depend on server state - whether the trainer takes
 * rematches, whether the player meets its requirements - which the MoLang of a dialogue file
 * cannot ask about. So the [Dialogue] is built in Kotlin, per player, per interaction.
 *
 * That costs nothing on the wire: `DialogueOpenedPacket` carries the page the player is
 * looking at, not a reference to a registered dialogue, so a box assembled here reaches a
 * plain Cobblemon client exactly like one of theirs - and the mod still ships no dialogue
 * asset of its own.
 *
 * Five moments, none of them mandatory for a pack:
 * - the greeting, on right-click, above the Battle / Cancel row;
 * - the refusal, which replaces the greeting when the trainer will not fight;
 * - `messages.start`, said after accepting and just before the battle opens;
 * - `messages.decline`, said to whoever presses Cancel;
 * - `messages.win` / `messages.lose`, said once the battle is over.
 */
object TrainerDialogue {

    /**
     * The one speaker key of every page. Only the trainer ever talks: a page spoken by the
     * player would be words the mod puts in their mouth.
     */
    private const val SPEAKER = "trainer"

    private const val PAGE_GREETING = "greeting"
    private const val PAGE_REFUSAL = "refusal"
    private const val PAGE_ACCEPT = "accept"
    private const val PAGE_DECLINE = "decline"
    private const val PAGE_FAREWELL = "farewell"

    /**
     * Seconds between a battle ending and the trainer's closing words.
     *
     * Without it the box opens over the battle interface while it is still showing the result.
     * A called trainer waits this out: [matheo1712.cobbletrainers.trainers.TrainerCalls] gives
     * them several seconds before sending them home, and holds that off again for as long as
     * [isTalking] answers yes.
     */
    private const val FAREWELL_DELAY_SECONDS = 1F

    /**
     * Opens the box a right-click on a trainer leads to.
     *
     * The trainer's answer is decided here rather than after the choice: one who will not fight
     * says so, instead of greeting the player and offering a button that goes nowhere.
     */
    fun greet(
        npc: NPCEntity,
        player: ServerPlayer,
        trainerId: ResourceLocation,
        definition: TrainerDefinition
    ) {
        val refusal = TrainerBattleInteraction.refusal(player, trainerId, definition)
        val pages = if (refusal.isEmpty()) {
            challengePages(npc, player, definition)
        } else {
            listOf(page(PAGE_REFUSAL, refusal, DialogueNoInput(close())))
        }

        open(npc, player, definition, pages)
    }

    /**
     * Opens the box holding the trainer's last word, once their battle is over.
     *
     * Nothing happens for a pack that wrote neither line, and nothing happens either when the
     * player has moved on in the meantime - into another battle, into another conversation, or
     * out of the game entirely.
     */
    fun farewell(npc: NPCEntity, player: ServerPlayer, definition: TrainerDefinition, won: Boolean) {
        val message = translate(if (won) definition.messages.win else definition.messages.lose) ?: return

        afterOnServer(FAREWELL_DELAY_SECONDS) {
            if (npc.isRemoved || player.hasDisconnected() || !player.isAlive) return@afterOnServer
            if (player.isInBattle() || player.activeDialogue != null) return@afterOnServer

            open(npc, player, definition, listOf(page(PAGE_FAREWELL, listOf(message), DialogueNoInput(close()))))
        }
    }

    /**
     * Whether this player currently has a box open on this trainer's face.
     *
     * The portrait is drawn from the entity - see [ReferenceDialogueFaceProvider] - so a
     * trainer removed while the box is open leaves an empty frame behind. This is what
     * [matheo1712.cobbletrainers.trainers.TrainerCalls] asks before sending a called trainer
     * home. A player who closes the box, or who leaves, stops answering yes on the next check,
     * so nothing here can strand a trainer in the world.
     */
    fun isTalking(player: ServerPlayer, npc: NPCEntity): Boolean =
        player.activeDialogue?.npc === npc

    /**
     * The greeting, plus the pages the pack's `messages.start` and `messages.decline` get when
     * it wrote them. Either one missing simply takes its button straight to what it leads to -
     * the battle, or a closed box.
     */
    private fun challengePages(
        npc: NPCEntity,
        player: ServerPlayer,
        definition: TrainerDefinition
    ): List<DialoguePage> {
        val battle = action { active ->
            // Closing first is what Cobblemon's own NPC dialogue does: the box is gone before
            // the battle interface takes over the screen.
            active.close()
            TrainerBattleInteraction.startBattle(npc, player, definition)
        }

        // Escaping out of this page is not a way back out of a challenge already accepted, so
        // it starts the battle like the continue button does.
        val start = translate(definition.messages.start)
        val accept = start?.let { page(PAGE_ACCEPT, listOf(it), DialogueNoInput(battle), escape = battle) }

        // The parting line answers the Cancel *button*, not the escape key: pressing escape is
        // asking to be out of the box, and holding someone there to be talked at is the one
        // thing it must not do. The default escape action of the greeting page closes, so
        // nothing has to be written here for that.
        val decline = translate(definition.messages.decline)
            ?.let { page(PAGE_DECLINE, listOf(it), DialogueNoInput(close())) }

        val greeting = translate(definition.messages.greeting) ?: CobblemonTrainers.lang("dialogue.greeting")
        val choice = DialogueOptionSetInput(
            options = mutableListOf(
                option("battle", if (accept == null) battle else action { it.setPage(accept) }),
                option("cancel", if (decline == null) close() else action { it.setPage(decline) })
            ),
            vertical = false
        )

        return listOfNotNull(page(PAGE_GREETING, listOf(greeting), choice), accept, decline)
    }

    private fun open(
        npc: NPCEntity,
        player: ServerPlayer,
        definition: TrainerDefinition,
        pages: List<DialoguePage>
    ) {
        val speaker = DialogueSpeaker(
            name = WrappedDialogueText(Component.translatable(definition.name)),
            // The portrait Cobblemon draws for `q.npc.face(...)`: the entity itself, so the
            // skin the mod applied to it is the one the player sees in the box.
            face = ReferenceDialogueFaceProvider(npc.id, true)
        )

        DialogueManager.startDialogue(
            player,
            npc,
            Dialogue(pages = pages, speakers = mapOf(SPEAKER to speaker))
        )
    }

    private fun page(
        id: String,
        lines: List<Component>,
        input: DialogueInput,
        escape: DialogueAction? = null
    ): DialoguePage = DialoguePage(
        id = id,
        speaker = SPEAKER,
        lines = lines.mapTo(mutableListOf<DialogueText>()) { WrappedDialogueText(it.copy()) },
        input = input
    ).also { page -> escape?.let { page.escapeAction = it } }

    /** One button of the greeting row. Its label is the mod's, never a pack's. */
    private fun option(value: String, action: DialogueAction): DialogueOption = DialogueOption(
        text = WrappedDialogueText(CobblemonTrainers.lang("dialogue.option.$value")),
        value = value,
        action = action
    )

    private fun close(): DialogueAction = action { it.close() }

    private fun action(block: (ActiveDialogue) -> Unit): DialogueAction =
        FunctionDialogueAction { active, _ -> block(active) }

    /**
     * A pack's line as a component, or null when it wrote none.
     *
     * Like the trainer name and the requirement messages, the value is a translation key:
     * Minecraft shows an untranslated key as it is written, so plain text keeps working.
     */
    private fun translate(message: String?): Component? =
        message?.takeIf { it.isNotBlank() }?.let { Component.translatable(it) }
}
