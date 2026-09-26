package matheo1712.cobbletrainers.command

import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.advancement.TrainerDefeatedTrigger
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerProgress
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * `/cobblemontrainers defeat`, which writes a victory into [TrainerProgress] without a battle.
 *
 * Usage:
 * - `/cobblemontrainers defeat <trainer_id>` - the caller now counts as having beaten it
 * - `/cobblemontrainers defeat <trainer_id> <players>` - the same, for someone else
 * - `/cobblemontrainers defeat all [<players>]` - every loaded trainer at once
 * - any of the above followed by `reset` - the opposite: forget the victory
 *
 * The point is to test a progression without playing it: recording a victory fires
 * [TrainerDefeatedTrigger], so advancements are evaluated, trainers locked behind this one open
 * up, and the battle phone ticks the line and reveals the team - exactly as a real win would.
 *
 * What it deliberately does **not** do is hand out the trainer's `rewards` or broadcast its
 * end-of-battle message: a testing tool has to be runnable a hundred times without burying the
 * player in items.
 *
 * `reset` removes the record, which is what makes a locked trainer testable twice. It cannot
 * take back an advancement already earned - Minecraft only revokes those through
 * `/advancement revoke`.
 *
 * Recording is not conditional on the victory being new: running it again on an already
 * beaten trainer fires the trigger anyway, which is how an advancement added after the fact
 * gets a chance to be evaluated.
 *
 * Permission is checked once on the root, see [TrainerCommands].
 */
object DefeatTrainerCommand {

    private const val NAME = "defeat"
    private const val ARG_ID = "trainer_id"
    private const val ARG_TARGETS = "players"
    private const val LITERAL_ALL = "all"
    private const val LITERAL_RESET = "reset"

    private val TRAINER_NOT_FOUND =
        SimpleCommandExceptionType(CobblemonTrainers.lang("command.defeat_trainer.unknown_trainer"))
    private val NO_TRAINERS =
        SimpleCommandExceptionType(CobblemonTrainers.lang("command.defeat_trainer.empty"))

    fun node(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(NAME)
            // Brigadier tries literal children before argument ones, so `all` is read as the
            // keyword rather than as a trainer named that way.
            .then(targets(Commands.literal(LITERAL_ALL)) { ctx, players, reset ->
                execute(ctx, TrainerRegistry.allIds().sortedBy { it.toString() }, players, reset)
            })
            .then(
                targets(
                    // ResourceLocationArgument rather than a string: Brigadier rejects colons
                    // in an unquoted word, which breaks `namespace:name`.
                    Commands.argument(ARG_ID, ResourceLocationArgument.id())
                        .suggests { _, builder ->
                            SharedSuggestionProvider.suggestResource(TrainerRegistry.allIds(), builder)
                        }
                ) { ctx, players, reset -> execute(ctx, listOf(resolve(ctx)), players, reset) }
            )

    /**
     * Hangs the `[<players>] [reset]` tail under a node, so `all` and a named trainer get the
     * same four spellings without writing them twice. [run] receives the players to act on and
     * whether the victory is being taken back.
     */
    private fun <T : ArgumentBuilder<CommandSourceStack, T>> targets(
        node: T,
        run: (CommandContext<CommandSourceStack>, Collection<ServerPlayer>, Boolean) -> Int
    ): T = node
        .then(
            Commands.literal(LITERAL_RESET)
                .executes { ctx -> run(ctx, listOf(ctx.source.playerOrException), true) }
        )
        .then(
            Commands.argument(ARG_TARGETS, EntityArgument.players())
                .then(
                    Commands.literal(LITERAL_RESET)
                        .executes { ctx -> run(ctx, EntityArgument.getPlayers(ctx, ARG_TARGETS), true) }
                )
                .executes { ctx -> run(ctx, EntityArgument.getPlayers(ctx, ARG_TARGETS), false) }
        )
        .executes { ctx -> run(ctx, listOf(ctx.source.playerOrException), false) }

    private fun resolve(context: CommandContext<CommandSourceStack>): ResourceLocation {
        val input = ResourceLocationArgument.getId(context, ARG_ID)
        return TrainerRegistry.resolveId(input) ?: throw TRAINER_NOT_FOUND.create()
    }

    /** @return how many (trainer, player) records were touched, for `execute store`. */
    private fun execute(
        context: CommandContext<CommandSourceStack>,
        trainers: List<ResourceLocation>,
        targets: Collection<ServerPlayer>,
        reset: Boolean
    ): Int {
        if (trainers.isEmpty()) throw NO_TRAINERS.create()

        val source = context.source
        val progress = TrainerProgress.of(source.server)

        targets.forEach { player ->
            trainers.forEach { trainerId ->
                if (reset) {
                    progress.forgetVictory(trainerId, player.uuid)
                } else {
                    progress.recordVictory(trainerId, player.uuid)
                    // After the record, never before: a `count` condition scores itself on it.
                    TrainerDefeatedTrigger.trigger(player, trainerId)
                }
            }
        }

        source.sendSuccess({ feedback(trainers, targets, reset) }, true)
        return trainers.size * targets.size
    }

    /**
     * One line naming the trainer when there is one, and counting them when `all` was used -
     * listing a hundred IDs in chat would say less than the number does.
     */
    private fun feedback(
        trainers: List<ResourceLocation>,
        targets: Collection<ServerPlayer>,
        reset: Boolean
    ): Component {
        val who = Component.literal(targets.joinToString(", ") { it.name.string })
            .withStyle(ChatFormatting.GREEN)

        val single = trainers.singleOrNull()
        return if (single != null) {
            val name = TrainerRegistry.get(single)?.name?.let { Component.translatable(it) }
                ?: Component.literal(single.toString())
            CobblemonTrainers.lang(
                if (reset) "command.defeat_trainer.reset" else "command.defeat_trainer.recorded",
                name.withStyle(ChatFormatting.GOLD),
                who
            )
        } else {
            CobblemonTrainers.lang(
                if (reset) "command.defeat_trainer.reset_all" else "command.defeat_trainer.recorded_all",
                Component.literal(trainers.size.toString()).withStyle(ChatFormatting.GOLD),
                who
            )
        }
    }
}
