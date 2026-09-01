package matheo1712.cobbletrainers.battle

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.network.BattleMusicNetworking
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * Plays and stops the battle music of a trainer.
 *
 * The server only ever names the track: nothing goes through `BuiltInRegistries.SOUND_EVENT`,
 * so a datapack may name any track without the mod knowing about it beforehand. Resolving the
 * name is the client's job, exactly as for a sound packet - which means the track has to be
 * provided by a resource pack (the mod jar is one, hence [DEFAULT_TRACK] working out of the
 * box).
 *
 * The music is sent to the players of the battle only, and never persisted: nothing here
 * tracks what is playing, [stop] simply tells the client to drop whatever theme it has. Which
 * is also why a track edited through `/reload` mid-battle keeps playing until the battle ends.
 *
 * The client owns the playback - see [BattleMusicNetworking] for the two reasons why, both of
 * them things a `ClientboundSoundPacket` cannot decide: the track loops until the battle is
 * over, and the world's own music stays quiet for its whole length.
 */
object TrainerBattleMusic {

    /////////////////////////////////////
    // CONFIGURATION
    /////////////////////////////////////
    // TODO : Mettre une musique de combat par défaut (5g probablement)
    const val DEFAULT_TRACK: String = "cobblemon-trainers:battle_music.corvault"
    /** Volume 1f is TOO HIIIGGGHH i lost my ear :( **/
    private const val VOLUME = 0.3f
    /** is speed of battle track **/
    private const val PITCH = 1.0f

    /**
     * Starts the music for every player of the battle, alone: whatever else was playing in the
     * music category is stopped client-side just before the theme starts.
     */
    fun start(track: String?, players: List<ServerPlayer>) {
        val sound = parse(track) ?: return
        if (players.isEmpty()) return

        players.forEach { BattleMusicNetworking.play(it, sound, VOLUME, PITCH) }
    }

    /**
     * Stops the music. Harmless when the track never started - but only sent for a trainer that
     * named one, so that a silent trainer never takes the world's music away on its way out.
     */
    fun stop(track: String?, players: List<ServerPlayer>) {
        parse(track) ?: return

        players.forEach { BattleMusicNetworking.silence(it) }
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
