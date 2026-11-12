package matheo1712.cobbletrainers.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.league.LeagueArenas
import matheo1712.cobbletrainers.league.LeagueEntry
import matheo1712.cobbletrainers.league.TrainerLeagues
import matheo1712.cobbletrainers.trainers.TrainerDefinition
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
 * `/cobblemontrainers league`, which places the host of a league - the character a player signs
 * up with - and says where its trainers stand.
 *
 * Usage:
 * - `/cobblemontrainers league <league_id>` - the host, at the caller's position, facing the
 *   way they do
 * - `/cobblemontrainers league <league_id> <x> <y> <z>` - the host, at explicit coordinates
 * - `/cobblemontrainers league <league_id> arena set [<x> <y> <z>]` - where the trainers of
 *   that league appear, kept in [LeagueArenas]
 * - `/cobblemontrainers league <league_id> arena clear` - back to appearing beside the player
 *
 * The host is an ordinary persistent NPC: it stays until it is removed, and it is found again
 * after a restart through the aspect naming its league. Permission is checked once on the root,
 * see [TrainerCommands].
 */
object LeagueCommand {

    private const val NAME = "league"
    private const val ARG_ID = "league_id"
    private const val ARG_POS = "pos"
    private const val ARENA = "arena"
    private const val ARENA_SET = "set"
    private const val ARENA_CLEAR = "clear"

    private val NO_ARENA =
        SimpleCommandExceptionType(CobblemonTrainers.lang("command.league.arena.none"))

    private val LEAGUE_NOT_FOUND =
        SimpleCommandExceptionType(CobblemonTrainers.lang("command.league.unknown_league"))
    private val INVALID_POS =
        SimpleCommandExceptionType(CobblemonTrainers.lang("command.spawn_trainer.invalid_position"))
    private val SPAWN_FAILED =
        SimpleCommandExceptionType(CobblemonTrainers.lang("command.league.spawn_failed"))

    fun node(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(NAME)
            .then(
                Commands.argument(ARG_ID, ResourceLocationArgument.id())
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggestResource(TrainerLeagues.ids(), builder)
                    }
                    .then(
                        Commands.argument(ARG_POS, Vec3Argument.vec3())
                            .executes { ctx -> execute(ctx, Vec3Argument.getVec3(ctx, ARG_POS)) }
                    )
                    // A literal, so it is tried before the position above and never read as one.
                    .then(
                        Commands.literal(ARENA)
                            .then(
                                Commands.literal(ARENA_SET)
                                    .then(
                                        Commands.argument(ARG_POS, Vec3Argument.vec3())
                                            .executes { ctx -> setArena(ctx, Vec3Argument.getVec3(ctx, ARG_POS)) }
                                    )
                                    .executes { ctx -> setArena(ctx, ctx.source.position) }
                            )
                            .then(Commands.literal(ARENA_CLEAR).executes { ctx -> clearArena(ctx) })
                    )
                    .executes { ctx -> execute(ctx, ctx.source.position) }
            )

    private fun league(context: CommandContext<CommandSourceStack>): LeagueEntry =
        TrainerLeagues.resolveId(ResourceLocationArgument.getId(context, ARG_ID))?.let { TrainerLeagues.get(it) }
            ?: throw LEAGUE_NOT_FOUND.create()

    /** Sets where the trainers of a league stand, in the dimension the command is run from. */
    private fun setArena(context: CommandContext<CommandSourceStack>, pos: Vec3): Int {
        val source = context.source
        val league = league(context)

        if (!Level.isInSpawnableBounds(BlockPos.containing(pos))) throw INVALID_POS.create()

        LeagueArenas.of(source.server).set(league.id, LeagueArenas.Arena(source.level.dimension(), pos))

        source.sendSuccess(
            {
                CobblemonTrainers.lang(
                    "command.league.arena.set",
                    Component.translatable(league.displayName).withStyle(ChatFormatting.GOLD),
                    Component.literal(pos.x.toInt().toString()).withStyle(ChatFormatting.GREEN),
                    Component.literal(pos.y.toInt().toString()).withStyle(ChatFormatting.GREEN),
                    Component.literal(pos.z.toInt().toString()).withStyle(ChatFormatting.GREEN)
                )
            },
            true
        )
        return Command.SINGLE_SUCCESS
    }

    /** Forgets it: the trainers come to the player again. */
    private fun clearArena(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val league = league(context)

        if (!LeagueArenas.of(source.server).clear(league.id)) throw NO_ARENA.create()

        source.sendSuccess(
            {
                CobblemonTrainers.lang(
                    "command.league.arena.cleared",
                    Component.translatable(league.displayName).withStyle(ChatFormatting.GOLD)
                )
            },
            true
        )
        return Command.SINGLE_SUCCESS
    }

    private fun execute(context: CommandContext<CommandSourceStack>, pos: Vec3): Int {
        val source = context.source
        val league = league(context)

        if (!Level.isInSpawnableBounds(BlockPos.containing(pos))) throw INVALID_POS.create()

        // A host is a trainer with no team and another identity: same class, same skin
        // resolution, and the aspect that names a league instead of a trainer - which is what
        // sends its right-click to the league rather than to a battle.
        TrainerSpawner.spawn(
            server = source.server,
            level = source.level,
            position = pos,
            definition = TrainerDefinition(name = league.hostName, skin = league.definition.host.skin),
            trainerId = league.id,
            yRot = source.rotation.y,
            identityAspect = CobblemonTrainers.LEAGUE_HOST_ASPECT_PREFIX + league.id
        ) ?: throw SPAWN_FAILED.create()

        source.sendSuccess(
            {
                CobblemonTrainers.lang(
                    "command.league.success",
                    Component.translatable(league.displayName).withStyle(ChatFormatting.GOLD),
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
