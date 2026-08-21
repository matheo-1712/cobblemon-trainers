package matheo1712.cobbletrainers.command

import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.battle.ai.TrainerBattleAI
import matheo1712.cobbletrainers.battle.ai.TrainerAiDebug
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

/**
 * `/cobblemontrainers debugai`, which shows the reasoning of [TrainerBattleAI] in chat.
 *
 * A correction is invisible from the other side of the battle: a switch that was refused and a
 * switch nobody proposed look exactly alike, which makes a threshold impossible to tune by
 * feel. With this on, every refusal names what it turned down and what it played instead.
 *
 * The switch is per player and lives in memory only - see [TrainerAiDebug]. It shows the
 * decisions of any trainer in a battle the player is in, so watching someone else's fight needs
 * nothing more than being in it.
 *
 * Permission is checked once on the root, see [TrainerCommands].
 */
object DebugAiCommand {

    private const val NAME = "debugai"

    fun node(): ArgumentBuilder<CommandSourceStack, *> =
        Commands.literal(NAME).executes(::toggle)

    private fun toggle(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val enabled = TrainerAiDebug.toggle(player)

        context.source.sendSuccess(
            {
                CobblemonTrainers.lang(if (enabled) "command.debug_ai.on" else "command.debug_ai.off")
                    .withStyle(ChatFormatting.GRAY)
            },
            false
        )
        return 1
    }
}
