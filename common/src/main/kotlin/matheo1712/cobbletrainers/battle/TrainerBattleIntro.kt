package matheo1712.cobbletrainers.battle

import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.isInBattle
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.intro.TrainerIntro
import matheo1712.cobbletrainers.intro.TrainerIntros
import matheo1712.cobbletrainers.network.BattleIntroNetworking
import matheo1712.cobbletrainers.trainers.TrainerBattleSettings
import matheo1712.cobbletrainers.trainers.TrainerDefinition
import net.minecraft.ChatFormatting
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * The screen a trainer may be announced with, and the wait it costs the battle behind it.
 *
 * A trainer opts in with `battle.intro`, naming an intro any pack may have written - see
 * [TrainerIntro] for the format and [TrainerIntros] for where those files live. A trainer that
 * names none simply battles, which is what every trainer written before this did.
 *
 * The screen itself belongs to the client - see
 * [matheo1712.cobbletrainers.client.gui.BattleIntroScreen] - so all that happens here is the
 * waiting: the packet goes out with the intro and everything its layers need, the battle is
 * held back for [TrainerIntro.duration] ticks, and [TrainerBattleInteraction.openBattle] is
 * what the wait ends on.
 *
 * Points worth not rediscovering:
 * - **The music starts with the screen, not with the battle.** That is what the intro is for.
 *   `BATTLE_STARTED_POST` asks for the same track a few seconds later and the client lets the
 *   one already playing run on - see
 *   [matheo1712.cobbletrainers.client.ClientBattleMusic.play] - so the theme is not restarted
 *   from the top just as the fight begins.
 * - **The intro travels with the packet, resolved.** Nothing is synced to clients on join and
 *   nothing is looked up client-side: what the screen draws is what the server read a moment
 *   ago, so a `/reload` lands on the next battle and never mid-screen.
 * - **Nothing is held back for a client that cannot show it.** A client without the mod never
 *   gets the packet, so it would stare at the world for five seconds and then fight: [begin]
 *   answers false there and the battle opens at once, as it always did.
 * - **The wait is guarded every tick.** A few seconds is long enough for the trainer to die, be
 *   dismissed or have its chunk unloaded, and for the player to leave - so the battle is only
 *   opened on a pair that is still there. A cancelled intro takes its music back with it.
 * - **Skipping goes through the same countdown.** [skip] only brings the deadline forward, so
 *   a client asking to skip cannot open a battle the guards would have refused.
 * - **The player is held as an entity, like [TrainerBattleRange] holds its trainer.** A player
 *   who reconnects during the intro is a `ServerPlayer` we no longer own, which
 *   `hasDisconnected` answers for: the intro is dropped and they walk up to the trainer again.
 */
object TrainerBattleIntro {

    /** A battle waiting on its intro, and everything needed to open it once the wait is over. */
    private class Pending(
        val npc: NPCEntity,
        val player: ServerPlayer,
        val definition: TrainerDefinition,
        var ticksLeft: Int
    )

    /** Keyed by player: a player has one battle ahead of them at a time. */
    private val pending = mutableMapOf<UUID, Pending>()

    /**
     * The intro these settings name, and the id it was found under, or null for a trainer that
     * names none - or names one no pack provides, which the load-time warning of
     * [TrainerBattleSettings.validate] has already spoken about.
     */
    fun resolve(settings: TrainerBattleSettings): Pair<ResourceLocation, TrainerIntro>? {
        val declared = settings.intro?.takeIf { it.isNotBlank() } ?: return null
        val id = TrainerIntros.idOf(declared) ?: return null

        return TrainerIntros.get(id)?.let { id to it }
    }

    /**
     * Shows the intro to the player, holding the battle back until it is over.
     *
     * @return false when there is no intro to show, and the caller should open the battle now.
     */
    fun begin(
        npc: NPCEntity,
        player: ServerPlayer,
        trainerId: ResourceLocation,
        definition: TrainerDefinition
    ): Boolean {
        val (introId, intro) = resolve(definition.battle) ?: return false
        // Right-clicking a trainer from inside a battle reaches this too, and a versus screen
        // over a battle interface would announce nothing: Cobblemon's own refusal is what that
        // player is owed, so the battle call is left to go through and fail.
        if (player.isInBattle()) return false
        if (!BattleIntroNetworking.canOpen(player)) return false
        // Already counting down for this player: their screen is up, and the battle it holds
        // back is the one about to open. Nothing more to start, and nothing to fall through to.
        if (pending.containsKey(player.uuid)) return true

        pending[player.uuid] = Pending(npc, player, definition, intro.ticks())

        TrainerBattleMusic.start(definition.battle.music, listOf(player))
        BattleIntroNetworking.open(player, npc, trainerId, definition, introId, intro)
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
