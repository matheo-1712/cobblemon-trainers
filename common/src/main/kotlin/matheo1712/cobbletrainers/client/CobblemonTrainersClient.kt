package matheo1712.cobbletrainers.client

import matheo1712.cobbletrainers.client.platform.ClientPlatform
import matheo1712.cobbletrainers.platform.Platform

import matheo1712.cobbletrainers.client.gui.BattleIntroScreen
import matheo1712.cobbletrainers.client.cache.TrainerSkinCache
import matheo1712.cobbletrainers.client.cache.TrainerSkinOverrides
import matheo1712.cobbletrainers.client.gui.TrainerSpawnerScreen
import matheo1712.cobbletrainers.client.render.TrainerOutfitRenderer
import matheo1712.cobbletrainers.network.BattleIntroPayload
import matheo1712.cobbletrainers.network.BattleMusicPayload
import matheo1712.cobbletrainers.network.OpenTrainerSpawnerPayload
import matheo1712.cobbletrainers.network.TrainerSkinPayload
import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Client entrypoint.
 *
 * The mod is a server-side affair almost everywhere - trainers come from datapacks and fight
 * on the server. What cannot be: its screens - the trainer spawner's and the versus screen -
 * since a screen only exists on a client. This registers the packets that open them, the one
 * that feeds screens their trainer skins, and the one thing here that is not a screen: the
 * party slot the player has selected, which Cobblemon keeps client-side. The battle phone is a
 * mod of its own now, Cobblemon BattlePhone, built on this one.
 *
 * Drawing is the other thing only a client can do: a trainer's clothes are hung on Cobblemon's
 * model by [matheo1712.cobbletrainers.client.render.TrainerOutfitRenderer], out of a mixin
 * rather than from here - all this owes it is a chance to drop what it baked when the resource
 * packs change.
 */
object CobblemonTrainersClient {

    fun onInitializeClient() {
        ClientPokemonSelection.register()

        ClientPlatform.current.receive(OpenTrainerSpawnerPayload.TYPE) { payload, client ->
            client.setScreen(TrainerSpawnerScreen(payload))
        }

        // Skins a screen draws without an entity to read them from: the versus screen, and
        // any listing built on the mod - see TrainerSkinNetworking.
        ClientPlatform.current.receive(TrainerSkinPayload.TYPE) { payload, _ ->
            TrainerSkinCache.accept(payload)
        }

        // A screen too, but one nobody opened: the trainer's versus screen, raised by the
        // server a few seconds before the battle it announces.
        ClientPlatform.current.receive(BattleIntroPayload.TYPE) { payload, client ->
            client.setScreen(BattleIntroScreen(payload))
        }

        // Not a screen either: the battle theme, which the client plays itself so that it can
        // loop and so that the world's own music stays out of the way.
        ClientPlatform.current.receive(BattleMusicPayload.TYPE) { payload, _ ->
            val track = payload.track
            if (track == null) ClientBattleMusic.silence()
            else ClientBattleMusic.play(track, payload.volume, payload.pitch)
        }

        // Skins are cached for the world they were sent from: another server may hold other
        // trainers under the same IDs, and the textures are ours to free.
        ClientPlatform.current.onDisconnect {
            TrainerSkinCache.clear()
            ClientBattleMusic.clear()
        }

        Platform.current.registerReloadListener(PackType.CLIENT_RESOURCES, CobblemonTrainers.id("outfits"), OutfitReloadListener::onResourceManagerReload)
        Platform.current.registerReloadListener(PackType.CLIENT_RESOURCES, CobblemonTrainers.id("skins"), SkinReloadListener::onResourceManagerReload)
    }

    /**
     * Drops the vanilla armour models the outfit renderer baked. They come out of the resource
     * packs like any other model, so the ones baked from the old packs are stale the moment the
     * new ones are in.
     */
    private object OutfitReloadListener {

        fun onResourceManagerReload(manager: ResourceManager) {
            TrainerOutfitRenderer.clear()
        }
    }

    /**
     * Forgets which trainer skins this client's own resource packs can override.
     *
     * A reload is the one moment that answer changes, and the skins already built on the old
     * one are stale with it: dropping them has the phone ask the server again, which is the
     * only way a freshly added pack can win a trainer it did not hold a moment ago. See
     * [TrainerSkinOverrides].
     */
    private object SkinReloadListener {

        fun onResourceManagerReload(manager: ResourceManager) {
            TrainerSkinOverrides.clear()
            TrainerSkinCache.clear()
        }
    }
}
