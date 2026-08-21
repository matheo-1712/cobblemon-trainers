package matheo1712.cobbletrainers.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.registry.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerDefinition
import matheo1712.cobbletrainers.trainers.TrainerLock
import matheo1712.cobbletrainers.trainers.TrainerProgress
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * `/cobblemontrainers list`, which lists every loaded trainer and whether a player has beaten
 * it.
 *
 * Usage:
 * - `/cobblemontrainers list` - the caller's own record
 * - `/cobblemontrainers list <player>` - another player's record
 *
 * What is shown is the same record [matheo1712.cobbletrainers.battle.TrainerBattleInteraction] reads to turn down a
 * challenge, so a trainer marked as defeated with `rematch: never` is one the player can no
 * longer fight, and one marked as locked is one they cannot fight yet - the line says so
 * either way. Unlike the battle phone, a locked trainer is never hidden here: this is the
 * operator's view of what a pack declares.
 *
 * Trainers are grouped by category, in the same order the battle phone shows them, and only
 * the listed ones appear - see [TrainerRegistry.listed].
 *
 * Permission is checked once on the root, see [TrainerCommands].
 */
object ListTrainersCommand {

    private const val NAME = "list"
    private const val ARG_TARGET = "player"

    private val NO_TRAINERS =
        SimpleCommandExceptionType(CobblemonTrainers.lang("command.list_trainers.empty"))
    private val NONE_LISTED =
        SimpleCommandExceptionType(CobblemonTrainers.lang("command.list_trainers.none_listed"))

    fun node(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(NAME)
            .then(
                Commands.argument(ARG_TARGET, EntityArgument.player())
                    .executes { ctx -> execute(ctx, EntityArgument.getPlayer(ctx, ARG_TARGET)) }
            )
            .executes { ctx -> execute(ctx, ctx.source.playerOrException) }

    /** @return how many trainers the target has beaten, for `execute store`. */
    private fun execute(context: CommandContext<CommandSourceStack>, target: ServerPlayer): Int {
        val source = context.source

        if (TrainerRegistry.allIds().isEmpty()) throw NO_TRAINERS.create()

        val sorted = TrainerRegistry.listed()
        if (sorted.isEmpty()) throw NONE_LISTED.create()

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

        // The listing already arrives grouped, so grouping again only cuts it where the
        // headings go - the order the battle phone shows is preserved.
        sorted.groupBy { (id, _) -> TrainerRegistry.categoryOf(id) }
            .forEach { (category, group) ->
                val beaten = group.count { (id, _) -> progress.hasDefeated(id, target.uuid) }
                source.sendSuccess({ categoryLine(category, beaten, group.size) }, false)

                group.forEach { (id, definition) ->
                    val line = entryLine(id, definition, target, progress.hasDefeated(id, target.uuid))
                    source.sendSuccess({ line }, false)
                }
            }

        return defeated
    }

    private fun categoryLine(category: ResourceLocation?, defeated: Int, total: Int): Component {
        val name = category
            ?.let { Component.translatable(TrainerRegistry.categoryName(it)) }
            ?: CobblemonTrainers.lang("category.uncategorized")
        return CobblemonTrainers.lang(
            "command.list_trainers.category",
            name,
            Component.literal(defeated.toString()),
            Component.literal(total.toString())
        ).withStyle(ChatFormatting.YELLOW)
    }

    private fun entryLine(
        id: ResourceLocation,
        definition: TrainerDefinition,
        target: ServerPlayer,
        defeated: Boolean
    ): Component {
        val key = if (defeated) "command.list_trainers.entry.defeated" else "command.list_trainers.entry.pending"
        val line = CobblemonTrainers.lang(key, Component.literal(id.toString()), Component.translatable(definition.name))
            .withStyle(if (defeated) ChatFormatting.GREEN else ChatFormatting.GRAY)

        if (defeated && !definition.progress.allowsRematch) {
            line.append(
                CobblemonTrainers.lang("command.list_trainers.no_rematch").withStyle(ChatFormatting.DARK_GRAY)
            )
        }

        // Evaluated against the target rather than the caller: the whole command is that
        // player's record, and requirements are per player like everything else in it.
        val missing = TrainerLock.unmet(target, id, definition)
        if (missing.isNotEmpty()) {
            line.append(
                CobblemonTrainers.lang(
                    "command.list_trainers.locked",
                    Component.literal(missing.size.toString())
                ).withStyle(ChatFormatting.DARK_RED)
            )
        }

        return line
    }
}
