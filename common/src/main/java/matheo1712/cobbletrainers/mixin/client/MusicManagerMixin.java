package matheo1712.cobbletrainers.mixin.client;

import matheo1712.cobbletrainers.client.ClientBattleMusic;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the world's background music quiet while a trainer battle theme is playing.
 *
 * A track sent by {@code ClientboundSoundPacket} is invisible to {@link MusicManager}: it keeps
 * counting down to its next song regardless. Silencing the <i>Music</i> category first does not
 * reset that countdown - {@code tick} only ever clamps it <b>down</b> ({@code Math.min}), and the
 * branch that pushes it back by ten to twenty minutes runs solely when a track of its own was
 * playing. A battle beginning shortly before the countdown expired therefore ended up with the
 * overworld music on top of the battle theme, which is issue #33.
 * <p>
 * Cancelling the whole tick freezes the countdown rather than letting it run out unheard, so the
 * world picks up where it left off once the battle is done. This is what Cobblemon does for its
 * own battle music, in {@code MusicManagerMixin}, and the two coexist: either one holding the
 * tick is enough.
 * <p>
 * The flag is written by the server, never guessed here - see
 * {@link matheo1712.cobbletrainers.network.BattleMusicNetworking}.
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cobblemontrainers$holdBackWorldMusic(CallbackInfo ci) {
        if (ClientBattleMusic.isPlaying()) {
            ci.cancel();
        }
    }
}
