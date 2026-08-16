package matheo1712.cobbletrainers

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * `/listtrainers`, which lists every loaded trainer and whether a player has beaten it.
 *
 * Usage:
 * - `/listtrainers` — the caller's own record
 * - `/listtrainers <player>` — another player's record
 *
 * What is shown is the same record [TrainerBattleInteraction] reads to turn down a rematch,
 * so a trainer marked as defeated with `canRebattle: false` is one the player can no longer
 * challenge — the line says so.
 *
 * Only the trainers a pack marks as tracked are listed, see [TrainerRegistry.tracked].
 *
 * Permission level: 2 (operators)
 */
object ListTrainersCommand {

    private const val NAME = "listtrainers"
    private const val ARG_TARGET = "player"

    private val NO_TRAINERS =
        SimpleCommandExceptionType(CobblemonTrainers.lang("command.list_trainers.empty"))
    private val NONE_TRACKED =
        SimpleCommandExceptionType(CobblemonTrainers.lang("command.list_trainers.none_tracked"))

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal(NAME)
                .requires { it.hasPermission(2) }
                .then(
                    Commands.argument(ARG_TARGET, EntityArgument.player())
                        .executes { ctx -> execute(ctx, EntityArgument.getPlayer(ctx, ARG_TARGET)) }
                )
                // Without a target the caller is the subject, so the console has to name one.
                // `playerOrException` is what says it, with vanilla's own localised message.
                .executes { ctx -> execute(ctx, ctx.source.playerOrException) }
        )
    }

    /** @return how many trainers the target has beaten, for `execute store`. */
    private fun execute(context: CommandContext<CommandSourceStack>, target: ServerPlayer): Int {
        val source = context.source

        if (TrainerRegistry.allIds().isEmpty()) throw NO_TRAINERS.create()

        // Already sorted on the full ID, which groups the trainers by the pack they come from.
        val sorted = TrainerRegistry.tracked()
        if (sorted.isEmpty()) throw NONE_TRACKED.create()

        val progress = TrainerProgress.of(source.server)
        val defeated = sorted.count { (id, _) -> progress.hasDefeated(id, target.uuid) }

        source.sendSuccess(
            {
                CobblemonTrainers.lang(
                    "command.list_trainers.header",
                    target.displayName ?: target.name,
                    Component.literal(defeated.toString()),
                    Component.literal(sorted.size.toString())
                ).withStyle(ChatFormatting.GOLD)
            },
            false
        )

        // One message per trainer rather than one long component: the chat wraps them on its
        // own, and each line keeps its own hover behaviour.
        sorted.forEach { (id, definition) ->
            val line = entryLine(id, definition, progress.hasDefeated(id, target.uuid))
            source.sendSuccess({ line }, false)
        }

        return defeated
    }

    private fun entryLine(
        id: ResourceLocation,
        definition: TrainerDefinition,
        defeated: Boolean
    ): Component {
        val key = if (defeated) "command.list_trainers.entry.defeated" else "command.list_trainers.entry.pending"
        val line = CobblemonTrainers.lang(key, Component.literal(id.toString()), Component.translatable(definition.name))
            .withStyle(if (defeated) ChatFormatting.GREEN else ChatFormatting.GRAY)

        // Every other line is still challengeable; a beaten trainer that takes no rematch is
        // done for good, which is the one case worth spelling out.
        if (defeated && !definition.canRebattle) {
            line.append(
                CobblemonTrainers.lang("command.list_trainers.no_rematch").withStyle(ChatFormatting.DARK_GRAY)
            )
        }

        return line
    }
}
