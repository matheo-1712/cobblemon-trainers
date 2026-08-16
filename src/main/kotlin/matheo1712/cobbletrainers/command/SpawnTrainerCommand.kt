package matheo1712.cobbletrainers.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.registry.TrainerRegistry
import matheo1712.cobbletrainers.trainers.TrainerSpawner
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * `/spawntrainer`, which summons a trainer in game.
 *
 * Usage:
 * - `/spawntrainer <trainer_id>` — spawns at the caller's position
 * - `/spawntrainer <trainer_id> <x> <y> <z>` — spawns at explicit coordinates
 *
 * The ID accepts either the full `namespace:name` form or the bare name.
 *
 * Permission level: 2 (operators)
 */
object SpawnTrainerCommand {

    private const val NAME = "spawntrainer"
    private const val ARG_ID = "trainer_id"
    private const val ARG_POS = "pos"

    private val TRAINER_NOT_FOUND =
        SimpleCommandExceptionType(CobblemonTrainers.lang("command.spawn_trainer.unknown_trainer"))
    private val INVALID_POS =
        SimpleCommandExceptionType(CobblemonTrainers.lang("command.spawn_trainer.invalid_position"))
    private val SPAWN_FAILED =
        SimpleCommandExceptionType(CobblemonTrainers.lang("command.spawn_trainer.spawn_failed"))

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal(NAME)
                .requires { it.hasPermission(2) }
                .then(
                    // ResourceLocationArgument rather than a string: Brigadier rejects colons
                    // in an unquoted word, which breaks `namespace:name`.
                    Commands.argument(ARG_ID, ResourceLocationArgument.id())
                        .suggests { _, builder ->
                            // Always the full `namespace:name`. The namespace *is* the pack a
                            // trainer comes from, so it is the only thing that tells apart two
                            // packs shipping the same file name — and it shows at a glance
                            // which pack provides what. Typing the bare name still works:
                            // `resolveId` falls back to it.
                            //
                            // suggestResource also filters on what has been typed, matching the
                            // path as well as the namespace, so `jac` still finds
                            // `mon_pack:jacinthe`.
                            SharedSuggestionProvider.suggestResource(TrainerRegistry.allIds(), builder)
                        }
                        .then(
                            Commands.argument(ARG_POS, Vec3Argument.vec3())
                                .executes { ctx -> execute(ctx, Vec3Argument.getVec3(ctx, ARG_POS)) }
                        )
                        .executes { ctx -> execute(ctx, ctx.source.position) }
                )
        )
    }

    private fun execute(context: CommandContext<CommandSourceStack>, pos: Vec3): Int {
        val source = context.source
        val input = ResourceLocationArgument.getId(context, ARG_ID)

        val trainerId = TrainerRegistry.resolveId(input) ?: throw TRAINER_NOT_FOUND.create()
        val definition = TrainerRegistry.get(trainerId) ?: throw TRAINER_NOT_FOUND.create()

        val level = source.level

        if (!Level.isInSpawnableBounds(pos.toBlockPos())) {
            throw INVALID_POS.create()
        }

        TrainerSpawner.spawn(
            server = source.server,
            level = level,
            position = pos,
            definition = definition,
            trainerId = trainerId
        ) ?: throw SPAWN_FAILED.create()

        val name = Component.translatable(definition.name).withStyle(ChatFormatting.GOLD)
        source.sendSuccess(
            {
                CobblemonTrainers.lang(
                    "command.spawn_trainer.success",
                    name,
                    Component.literal(pos.x.toInt().toString()).withStyle(ChatFormatting.GREEN),
                    Component.literal(pos.y.toInt().toString()).withStyle(ChatFormatting.GREEN),
                    Component.literal(pos.z.toInt().toString()).withStyle(ChatFormatting.GREEN)
                )
            },
            true
        )

        return Command.SINGLE_SUCCESS
    }
}

// Small helper to turn a Vec3 into a BlockPos
private fun Vec3.toBlockPos() = BlockPos(this.x.toInt(), this.y.toInt(), this.z.toInt())
