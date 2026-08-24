package matheo1712.cobbletrainers.client

import net.minecraft.client.Minecraft

/**
 * Whether a trainer battle theme is playing, as the server last said.
 *
 * Read by `mixin.client.MusicManagerMixin`, which holds the world's own music back for as long
 * as the answer is yes. See [matheo1712.cobbletrainers.network.BattleMusicNetworking] for why
 * the client has to be told at all.
 *
 * Both edges call `MusicManager.stopPlaying()`, which does two things worth having: it drops
 * the reference the manager keeps on a track the server has just silenced, and it pushes the
 * countdown to the next one back by a hundred ticks - so the world does not strike up the
 * instant a battle is over.
 */
object ClientBattleMusic {

    private var playing = false

    /** Java-friendly: the mixin is the only caller. */
    @JvmStatic
    fun isPlaying(): Boolean = playing

    fun accept(playing: Boolean) {
        if (this.playing == playing) return

        this.playing = playing
        Minecraft.getInstance().musicManager.stopPlaying()
    }

    /**
     * Forgotten on disconnect: a client that leaves mid-battle would otherwise stay silent on
     * the next world, no one being left to release it. The flag alone is reset here - pushing
     * back the countdown of a world being left behind buys nothing, and the sound manager is
     * better left to the client's own teardown.
     */
    fun clear() {
        playing = false
    }
}
