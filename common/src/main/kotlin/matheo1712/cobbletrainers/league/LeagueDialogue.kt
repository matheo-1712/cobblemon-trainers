package matheo1712.cobbletrainers.league

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
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.isInBattle
import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Everything a league's host says, in the same Cobblemon box a trainer talks in - see
 * [matheo1712.cobbletrainers.dialogue.TrainerDialogue] for why it is built in Kotlin, per
 * player, rather than read from a `dialogues/` file.
 *
 * Three moments: signing up, the pause between two battles, and the congratulations. The rules
 * stay in [LeagueRuns]; this only stages what it has decided.
 */
object LeagueDialogue {

    private const val SPEAKER = "host"

    /** Opens the box a right-click on a host leads to. */
    fun greet(host: NPCEntity, player: ServerPlayer) {
        val league = TrainerLeagues.idFromAspects(host.aspects)?.let { TrainerLeagues.get(it) }
        // A host whose league a pack no longer provides has nothing to offer, and says so
        // rather than standing there mute.
        if (league == null) {
            player.sendSystemMessage(CobblemonTrainers.lang("chat.league.unknown").withStyle(ChatFormatting.GRAY))
            return
        }
        // The battle interface is on their screen: a box over it would say nothing useful.
        if (player.isInBattle()) return

        val refusal = LeagueRuns.refusal(player, league)
        if (refusal.isNotEmpty()) {
            return open(host, player, league, listOf(page("refusal", refusal, DialogueNoInput(close()))))
        }

        val messages = league.definition.host.messages
        val decline = translate(messages.decline)?.let { page("decline", listOf(it), DialogueNoInput(close())) }
        val greeting = translate(messages.greeting)
            ?: CobblemonTrainers.lang(
                "dialogue.league.greeting",
                Component.translatable(league.displayName),
                league.members.size
            )

        val choice = DialogueOptionSetInput(
            options = mutableListOf(
                option("enter", action { active ->
                    active.close()
                    LeagueRuns.start(player, league, host)
                }),
                option("cancel", if (decline == null) close() else action { it.setPage(decline) })
            ),
            vertical = false
        )

        open(host, player, league, listOfNotNull(page("greeting", listOf(greeting), choice), decline))
    }

    /**
     * The pause between two battles: where the player stands, who is next, and the one moment
     * they can walk away.
     */
    fun between(
        host: NPCEntity,
        player: ServerPlayer,
        league: LeagueEntry,
        won: Int,
        next: Component,
        onContinue: () -> Unit,
        onForfeit: () -> Unit
    ) {
        val lines = listOfNotNull(
            translate(league.definition.host.messages.between),
            CobblemonTrainers.lang("dialogue.league.between", won, league.members.size, next)
        )
        val choice = DialogueOptionSetInput(
            options = mutableListOf(
                option("continue", action { active ->
                    active.close()
                    onContinue()
                }),
                option("forfeit", action { active ->
                    active.close()
                    onForfeit()
                })
            ),
            vertical = false
        )

        open(host, player, league, listOf(page("between", lines, choice)))
    }

    /** The host's word once the last trainer is beaten. */
    fun congratulate(host: NPCEntity, player: ServerPlayer, league: LeagueEntry) {
        val line = translate(league.definition.host.messages.win)
            ?: CobblemonTrainers.lang("dialogue.league.completed", Component.translatable(league.displayName))
        open(host, player, league, listOf(page("completed", listOf(line), DialogueNoInput(close()))))
    }

    private fun open(host: NPCEntity, player: ServerPlayer, league: LeagueEntry, pages: List<DialoguePage>) {
        val speaker = DialogueSpeaker(
            name = WrappedDialogueText(Component.translatable(league.hostName)),
            face = ReferenceDialogueFaceProvider(host.id, true)
        )
        DialogueManager.startDialogue(player, host, Dialogue(pages = pages, speakers = mapOf(SPEAKER to speaker)))
    }

    private fun page(id: String, lines: List<Component>, input: DialogueInput): DialoguePage = DialoguePage(
        id = id,
        speaker = SPEAKER,
        lines = lines.mapTo(mutableListOf<DialogueText>()) { WrappedDialogueText(it.copy()) },
        input = input
    )

    /** One button. Its label is the mod's, never a pack's. */
    private fun option(value: String, action: DialogueAction): DialogueOption = DialogueOption(
        text = WrappedDialogueText(CobblemonTrainers.lang("dialogue.option.$value")),
        value = value,
        action = action
    )

    private fun close(): DialogueAction = action { it.close() }

    private fun action(block: (ActiveDialogue) -> Unit): DialogueAction =
        FunctionDialogueAction { active, _ -> block(active) }

    private fun translate(message: String?): Component? =
        message?.takeIf { it.isNotBlank() }?.let { Component.translatable(it) }
}
