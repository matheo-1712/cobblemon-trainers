package matheo1712.cobbletrainers

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * Commande `/spawntrainer` permettant d'invoquer un dresseur en jeu.
 *
 * Usage :
 * - `/spawntrainer <trainer_id>` — spawn à la position actuelle du commandeur
 * - `/spawntrainer <trainer_id> <x> <y> <z>` — spawn à des coordonnées précises
 *
 * L'ID accepte la forme complète `namespace:nom` ou le nom seul.
 *
 * Permission level : 2 (operators)
 */
object SpawnTrainerCommand {

    private const val NAME = "spawntrainer"
    private const val ARG_ID = "trainer_id"
    private const val ARG_POS = "pos"

    private val TRAINER_NOT_FOUND = SimpleCommandExceptionType(
        Component.literal("Dresseur introuvable ! Vérifie l'ID dans data/<namespace>/trainers/ de tes datapacks.")
    )
    private val INVALID_POS = SimpleCommandExceptionType(
        Component.literal("Position invalide pour le spawn du dresseur.")
    )
    private val SPAWN_FAILED = SimpleCommandExceptionType(
        Component.literal("Échec du spawn du dresseur. Consulte les logs du serveur pour le détail.")
    )

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal(NAME)
                .requires { it.hasPermission(2) }
                .then(
                    // ResourceLocationArgument, et pas une chaîne : Brigadier refuse les
                    // deux-points dans un mot non quoté, ce qui casse `namespace:nom`.
                    Commands.argument(ARG_ID, ResourceLocationArgument.id())
                        .suggests { _, builder ->
                            val ids = TrainerRegistry.allIds()
                            val byName = ids.groupBy { it.path }
                            ids.forEach { id ->
                                // Nom court tant qu'il est sans ambiguïté, sinon ID complet.
                                // Dès que le joueur tape un `:`, on ne propose que les ID complets.
                                val ambiguous = byName.getValue(id.path).size > 1
                                builder.suggest(
                                    if (ambiguous || builder.remaining.contains(':')) id.toString() else id.path
                                )
                            }
                            builder.buildFuture()
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

        source.sendSuccess(
            {
                Component.literal(
                    "✅ Dresseur §6${definition.name}§r spawné avec succès en " +
                        "§a${pos.x.toInt()}, ${pos.y.toInt()}, ${pos.z.toInt()}§r !"
                )
            },
            true
        )

        return Command.SINGLE_SUCCESS
    }
}

// Extension utilitaire pour convertir Vec3 en BlockPos
private fun Vec3.toBlockPos() = BlockPos(this.x.toInt(), this.y.toInt(), this.z.toInt())
