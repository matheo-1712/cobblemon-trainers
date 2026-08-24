package matheo1712.cobbletrainers.battle

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.network.BattleMusicNetworking
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
 * registered sound - which means the track has to be provided by a resource pack (the mod
 * jar is one, hence [DEFAULT_TRACK] working out of the box).
 *
 * The music is sent to the players of the battle only, and never persisted: nothing here
 * tracks what is playing, [stop] simply silences the track the definition names. Which is
 * also why a track edited through `/reload` mid-battle keeps playing until the battle ends.
 *
 * Each of the two also tells the client whether battle music is on, so that its `MusicManager`
 * holds its own background tracks back for the length of the battle. That guard is what issue
 * #33 was about, and [BattleMusicNetworking] explains why the sound packets alone could not
 * answer it.
 *
 * Known limit: the track plays once and does not loop. Looping a sound is a client-side
 * decision ([net.minecraft.client.resources.sounds.SoundInstance.isLooping]), taken by a sound
 * engine this mod never touches - so a battle outlasting its track finishes in silence, the
 * guard above keeping the world's music away until it is over.
 */
object TrainerBattleMusic {

    /////////////////////////////////////
    // CONFIGURATION
    /////////////////////////////////////
    // TODO : Mettre une musique de combat par défaut (5g probablement)
    const val DEFAULT_TRACK: String = "cobblemon-trainers:battle_music.corvault"
    private val SOURCE = SoundSource.MUSIC
    /** Volume 1f is TOO HIIIGGGHH i lost my ear :( **/
    private const val VOLUME = 0.2f
    /** is speed of battle track **/
    private const val PITCH = 1.0f

    /**
     * Starts the music for every player of the battle, alone: whatever else was playing in
     * the [SoundSource.MUSIC] category is cut first, so the battle theme never overlaps the
     * vanilla background music (see [silenceOtherMusic]).
     *
     * The order of the three packets matters, and they travel down one connection so it is
     * kept: the guard first, then the silence, then the track - which is why the silence does
     * not take the track down with it.
     */
    fun start(track: String?, players: List<ServerPlayer>) {
        val sound = parse(track) ?: return
        if (players.isEmpty()) return

        val holder = Holder.direct(SoundEvent.createVariableRangeEvent(sound))
        players.forEach { player ->
            BattleMusicNetworking.hold(player)
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
     * Stops everything already playing in the music category, our own track included - hence
     * the strict ordering with the packet that starts it.
     */
    private fun silenceOtherMusic(player: ServerPlayer) {
        player.connection.send(ClientboundStopSoundPacket(null, SOURCE))
    }

    /** Stops the music. Harmless when the track already finished or never started. */
    fun stop(track: String?, players: List<ServerPlayer>) {
        val sound = parse(track) ?: return

        val packet = ClientboundStopSoundPacket(sound, SOURCE)
        players.forEach { player ->
            player.connection.send(packet)
            BattleMusicNetworking.release(player)
        }
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
