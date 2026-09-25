package matheo1712.cobbletrainers.battle.ai

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * The `/cobblemontrainers debugai` switch: sends the reasoning of [TrainerBattleAI] to the
 * players who asked for it, as it happens.
 *
 * Thresholds like `MATCHUP_GAIN_REQUIRED` cannot be tuned by feel - a refused switch and a
 * switch nobody proposed look exactly alike from the other side of the battle. This turns each
 * correction into a chat line naming what was refused and what replaced it, so a bad threshold
 * shows itself in one fight instead of a session.
 *
 * The set is in memory only: a debug switch that survived a restart would keep spamming someone
 * who has long forgotten turning it on.
 */
object TrainerAiDebug {

    private val watchers = mutableSetOf<UUID>()

    /** Flips the switch for [player] and returns its new state. */
    fun toggle(player: ServerPlayer): Boolean =
        if (!watchers.remove(player.uuid)) {
            watchers.add(player.uuid)
            true
        } else {
            false
        }

    fun isWatching(player: ServerPlayer): Boolean = player.uuid in watchers

    /**
     * True when nobody is listening, so a caller can skip building its message. Every correction
     * would otherwise format a line and throw it away, on every turn of every battle.
     */
    fun idle(): Boolean = watchers.isEmpty()

    /** Sends [detail] to the players of [battle] who have the switch on. */
    fun report(battle: PokemonBattle, detail: String) {
        if (watchers.isEmpty()) return
        battle.players
            .filter { it.uuid in watchers }
            .forEach {
                it.sendSystemMessage(
                    CobblemonTrainers.lang("chat.ai_debug", detail).withStyle(ChatFormatting.DARK_GRAY)
                )
            }
    }
}
