package matheo1712.cobbletrainers.command

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

/**
 * The one command of the mod, `/cobblemontrainers`, and the verbs under it.
 *
 * Everything the mod does from chat hangs here rather than from three top-level names: a
 * player typing `/cobblemontrainers` sees the whole feature at once, and the mod occupies one
 * word of the command namespace instead of three - which matters on a server running a dozen
 * mods.
 *
 * ```
 * /cobblemontrainers spawn <id> [<x> <y> <z>]
 * /cobblemontrainers list [<player>]
 * /cobblemontrainers defeat <id|all> [<players>] [reset]
 * ```
 *
 * **The permission check lives here, on the root**, and covers every verb: level 2, operators.
 * A verb that should one day be open to everyone would have to move out of this node rather
 * than drop a check of its own.
 */
object TrainerCommands {

    /**
     * The literal every verb hangs from. Written without the hyphen of the mod id: Brigadier
     * would accept `cobblemon-trainers`, but a name that is typed by hand is better off with
     * one less key in it, and this is the spelling the datapack directory already uses.
     */
    const val ROOT = "cobblemontrainers"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal(ROOT)
                .requires { it.hasPermission(2) }
                .then(SpawnTrainerCommand.node())
                .then(ListTrainersCommand.node())
                .then(DefeatTrainerCommand.node())
                .then(DebugAiCommand.node())
        )
    }
}
