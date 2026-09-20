package matheo1712.cobbletrainers.mixin.client;

import matheo1712.cobbletrainers.client.ClientBattleMusic;
import com.cobblemon.mod.common.client.net.battle.BattleMusicHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMusicPacket;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BattleMusicHandler.class)
public abstract class BattleMusicHandlerMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void cobblemontrainers$prioritizeTrainerMusic(
            BattleMusicPacket packet,
            Minecraft client,
            CallbackInfo ci
    ) {
        if (ClientBattleMusic.isPlaying() && packet.getMusic() != null) {
            ci.cancel();
        }
    }
}