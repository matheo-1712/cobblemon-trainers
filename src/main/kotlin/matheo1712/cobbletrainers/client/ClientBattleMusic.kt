package matheo1712.cobbletrainers.client

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource

/**
 * The trainer battle theme, played and held by the client.
 *
 * The track is played here rather than sent as a [net.minecraft.network.protocol.game.ClientboundSoundPacket]
 * for one reason: **looping is a client-side decision**. `SoundInstance.isLooping` belongs to
 * the instance, and a server packet builds one that plays once - so a battle outlasting its
 * track used to finish in silence. Built here, the instance loops, and vanilla restarts the
 * ogg with no gap at all: a streamed sound asked to loop is served by a
 * `LoopingAudioStream`, which re-opens the file at end of stream.
 *
 * Also read by `mixin.client.MusicManagerMixin`, which holds the world's own music back for as
 * long as a theme is loaded here. See [matheo1712.cobbletrainers.network.BattleMusicNetworking]
 * for why the client has to be told at all.
 */
object ClientBattleMusic {

    /**
     * The theme currently playing, or null. This is what answers the mixin, rather than asking
     * the sound engine: a streamed sound reaches its channel a moment after [play] returns, and
     * the question being asked is "is a battle on", not "has the audio thread caught up".
     */
    private var playing: SimpleSoundInstance? = null

    /** Java-friendly: the mixin is the only caller. */
    @JvmStatic
    fun isPlaying(): Boolean = playing != null

    /**
     * Starts the theme, alone. Everything already playing in the music category is stopped
     * first - the world's own track through the music manager, so that it also lets go of its
     * reference to it, then whatever else was left.
     *
     * The instance is `relative` with no attenuation, like every music vanilla plays: it sits
     * on the listener, so nothing here depends on where the player is standing.
     */
    fun play(track: ResourceLocation, volume: Float, pitch: Float) {
        val client = Minecraft.getInstance()

        silence()
        client.musicManager.stopPlaying()
        client.soundManager.stop(null, SoundSource.MUSIC)

        val theme = SimpleSoundInstance(
            track,
            SoundSource.MUSIC,
            volume,
            pitch,
            SoundInstance.createUnseededRandom(),
            true,
            0,
            SoundInstance.Attenuation.NONE,
            0.0,
            0.0,
            0.0,
            true
        )
        playing = theme
        client.soundManager.play(theme)
    }

    /**
     * Stops the theme. `stopPlaying` is called on the way out for its side effect on the world's
     * music: it pushes the countdown to the next track back by a hundred ticks, so the overworld
     * does not strike up the instant a battle ends.
     */
    fun silence() {
        val theme = playing ?: return

        playing = null
        val client = Minecraft.getInstance()
        client.soundManager.stop(theme)
        client.musicManager.stopPlaying()
    }

    /**
     * Forgotten on disconnect: a client that leaves mid-battle would otherwise keep the world's
     * music silenced on the next one, no server being left to release it. The reference alone is
     * dropped - the sounds themselves are stopped by the client's own teardown.
     */
    fun clear() {
        playing = null
    }
}
