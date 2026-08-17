package matheo1712.cobbletrainers.battle

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

/**
 * Plays and stops the battle music of a trainer.
 *
 * The sound packets carry a *direct* [net.minecraft.core.Holder]: the sound never goes through
 * `BuiltInRegistries.SOUND_EVENT`, so a datapack may name any track without the mod
 * knowing about it beforehand. Resolving the name is the client's job, exactly as for a
 * registered sound — which means the track has to be provided by a resource pack (the mod
 * jar is one, hence [DEFAULT_TRACK] working out of the box).
 *
 * The music is sent to the players of the battle only, and never persisted: nothing here
 * tracks what is playing, [stop] simply silences the track the definition names. Which is
 * also why a track edited through `/reload` mid-battle keeps playing until the battle ends.
 *
 * Known limit: the track plays once and does not loop. Looping a sound is a client-side
 * decision ([net.minecraft.client.resources.sounds.SoundInstance.isLooping]), taken by a sound
 * engine this mod never touches.
 */
object TrainerBattleMusic {

    /** Track shipped by the mod, and default value of `battleMusic` on a trainer. */
    const val DEFAULT_TRACK: String = "cobblemon-trainers:battle_music.corvault"

    /**
     * [net.minecraft.sounds.SoundSource.MUSIC] puts the track under the player's *Music* volume slider, next to
     * the vanilla background music — which keeps playing on its own, the server cannot
     * silence it.
     */
    private val SOURCE = SoundSource.MUSIC

    /**
     * Half volume. A battle theme runs for minutes on top of everything else the player is
     * listening to, and at 1.0 it drowns the fight it is supposed to underscore. The player's
     * *Music* slider still scales it from here.
     */
    // Volume 1f is TOO HIIIGGGHH i lost my ear :(
    private const val VOLUME = 0.5f

    private const val PITCH = 1.0f

    /**
     * Starts the music for every player of the battle, alone: whatever else was playing in
     * the [SoundSource.MUSIC] category is cut first, so the battle theme never overlaps the
     * vanilla background music (see [silenceOtherMusic]).
     */
    fun start(track: String?, players: List<ServerPlayer>) {
        val sound = parse(track) ?: return
        if (players.isEmpty()) return

        val holder = Holder.direct(SoundEvent.createVariableRangeEvent(sound))
        players.forEach { player ->
            silenceOtherMusic(player)
            player.connection.send(
                ClientboundSoundPacket(
                    holder,
                    SOURCE,
                    player.x,
                    player.y,
                    player.z,
                    VOLUME,
                    PITCH,
                    player.random.nextLong()
                )
            )
        }
    }

    /**
     * Stops everything already playing in the music category, our own track included — hence
     * the strict ordering with the packet that starts it.
     */
    private fun silenceOtherMusic(player: ServerPlayer) {
        player.connection.send(ClientboundStopSoundPacket(null, SOURCE))
    }

    /** Stops the music. Harmless when the track already finished or never started. */
    fun stop(track: String?, players: List<ServerPlayer>) {
        val sound = parse(track) ?: return

        val packet = ClientboundStopSoundPacket(sound, SOURCE)
        players.forEach { it.connection.send(packet) }
    }

    /** Null for a trainer without music, or for a name that is not a valid sound ID. */
    private fun parse(track: String?): ResourceLocation? {
        if (track.isNullOrBlank()) return null

        return ResourceLocation.tryParse(track) ?: run {
            CobblemonTrainers.LOGGER.warn("Invalid battle music sound ID '{}', ignoring it", track)
            null
        }
    }
}