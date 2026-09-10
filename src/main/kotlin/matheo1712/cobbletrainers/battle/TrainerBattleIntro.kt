package matheo1712.cobbletrainers.battle

import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.isInBattle
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.network.BattleIntroNetworking
import matheo1712.cobbletrainers.trainers.TrainerBattleSettings
import matheo1712.cobbletrainers.trainers.TrainerDefinition
import matheo1712.cobbletrainers.trainers.TrainerRegistry
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * The versus screen a trainer may show before their battle opens: the player and the trainer
 * sliding in from either side, and the battle theme starting on them - the way a gym leader is
 * announced in Black and White.
 *
 * A trainer opts in with `battle.intro`, and names which screen they want: [STYLES] holds the
 * vocabulary, `bw` being the only one so far. A trainer that names none simply battles, which
 * is what every trainer written before this did.
 *
 * The screen belongs to the client - see
 * [matheo1712.cobbletrainers.client.gui.BattleIntroScreen] - so all that happens here is the
 * waiting: the packet goes out, the battle is held back for [TrainerBattleSettings.introDuration]
 * ticks, and [TrainerBattleInteraction.openBattle] is what the wait ends on.
 *
 * Points worth not rediscovering:
 * - **The music starts with the screen, not with the battle.** That is what the intro is for.
 *   `BATTLE_STARTED_POST` asks for the same track a few seconds later and the client lets the
 *   one already playing run on - see
 *   [matheo1712.cobbletrainers.client.ClientBattleMusic.play] - so the theme is not restarted
 *   from the top just as the fight begins.
 * - **Nothing is held back for a client that cannot show it.** A client without the mod never
 *   gets the packet, so it would stare at the world for three seconds and then fight: [begin]
 *   answers false there and the battle opens at once, as it always did.
 * - **The wait is guarded every tick.** Three seconds is long enough for the trainer to die,
 *   be dismissed or have its chunk unloaded, and for the player to leave - so the battle is
 *   only opened on a pair that is still there. A cancelled intro takes its music back with it.
 * - **Skipping goes through the same countdown.** [skip] only brings the deadline forward, so
 *   a client asking to skip cannot open a battle the guards would have refused.
 * - **The player is held as an entity, like [TrainerBattleRange] holds its trainer.** A player
 *   who reconnects during the intro is a `ServerPlayer` we no longer own, which
 *   `hasDisconnected` answers for: the intro is dropped and they walk up to the trainer again.
 */
object TrainerBattleIntro {

    /** The only screen there is so far. What an unknown style falls back to. */
    const val DEFAULT_STYLE = "bw"

    /** Every screen a pack may name in `battle.intro`. */
    val STYLES: List<String> = listOf(DEFAULT_STYLE)

    /** How long the screen stays up when the pack does not say, in ticks. */
    const val DEFAULT_TICKS = 60

    /** A screen shorter than this is a flash, longer than that is a wait. Packs are clamped. */
    const val MIN_TICKS = 20
    const val MAX_TICKS = 200

    /** A battle waiting on its intro, and everything needed to open it once the wait is over. */
    private class Pending(
        val npc: NPCEntity,
        val player: ServerPlayer,
        val definition: TrainerDefinition,
        var ticksLeft: Int
    )

    /** Keyed by player: a player has one battle ahead of them at a time. */
    private val pending = mutableMapOf<UUID, Pending>()

    /** Whether a pack naming this style asked for something that exists. */
    fun isSupported(style: String): Boolean = STYLES.contains(style.trim().lowercase())

    /**
     * The screen to show for these settings, or null for a trainer who wants none.
     *
     * An unrecognised name still gets a screen: naming one *is* asking for an intro, and which
     * one is the part a typo can get wrong. The load-time warning is in
     * [TrainerBattleSettings.validate].
     */
    fun styleOf(settings: TrainerBattleSettings): String? {
        val declared = settings.intro?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null

        return if (isSupported(declared)) declared else DEFAULT_STYLE
    }

    /**
     * Shows the intro to the player, holding the battle back until it is over.
     *
     * @return false when there is no intro to show, and the caller should open the battle now.
     */
    fun begin(npc: NPCEntity, player: ServerPlayer, definition: TrainerDefinition): Boolean {
        val style = styleOf(definition.battle) ?: return false
        val trainerId = TrainerRegistry.idFromAspects(npc.aspects) ?: return false
        // Right-clicking a trainer from inside a battle reaches this too, and a versus screen
        // over a battle interface would announce nothing: Cobblemon's own refusal is what that
        // player is owed, so the battle call is left to go through and fail.
        if (player.isInBattle()) return false
        if (!BattleIntroNetworking.canOpen(player)) return false
        // Already counting down for this player: their screen is up, and the battle it holds
        // back is the one about to open. Nothing more to start, and nothing to fall through to.
        if (pending.containsKey(player.uuid)) return true

        val ticks = definition.battle.introDuration.coerceIn(MIN_TICKS, MAX_TICKS)
        pending[player.uuid] = Pending(npc, player, definition, ticks)

        TrainerBattleMusic.start(definition.battle.music, listOf(player))
        BattleIntroNetworking.open(player, style, trainerId, definition, ticks)
        return true
    }

    /** Brings the deadline forward, for a player who has seen enough. */
    fun skip(player: ServerPlayer) {
        pending[player.uuid]?.ticksLeft = 1
    }

    /** Drops everything, for a server whose battles are not going to open any more. */
    fun clear() {
        pending.clear()
    }

    /** Opens what has waited long enough, and drops what is not worth opening any more. */
    fun tick() {
        if (pending.isEmpty()) return

        val ready = mutableListOf<Pending>()
        pending.values.removeIf { entry ->
            when {
                // Their client is gone, and with it the screen and the theme.
                entry.player.hasDisconnected() -> true
                // Killed, dismissed, or gone with their chunk while the screen was up.
                entry.npc.isRemoved || !entry.npc.isAlive -> {
                    cancel(entry)
                    true
                }
                // In a battle already: whatever put them there owns their screen now.
                entry.player.isInBattle() -> true
                --entry.ticksLeft > 0 -> false
                else -> {
                    ready += entry
                    true
                }
            }
        }

        // Outside the iteration: opening a battle runs Cobblemon's own start handlers, and
        // those reach back into this mod.
        ready.forEach { TrainerBattleInteraction.openBattle(it.npc, it.player, it.definition) }
    }

    /** Takes the theme back from a battle that is not going to happen, and says why. */
    private fun cancel(entry: Pending) {
        TrainerBattleMusic.stop(entry.definition.battle.music, listOf(entry.player))
        entry.player.sendSystemMessage(
            CobblemonTrainers.lang("chat.intro.trainer_gone").withStyle(ChatFormatting.GRAY)
        )
    }
}
