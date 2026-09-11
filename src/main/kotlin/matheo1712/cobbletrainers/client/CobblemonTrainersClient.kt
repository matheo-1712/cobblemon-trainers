package matheo1712.cobbletrainers.client

import matheo1712.cobbletrainers.client.gui.BattleIntroScreen
import matheo1712.cobbletrainers.client.gui.BattlePhoneScreen
import matheo1712.cobbletrainers.client.cache.TrainerSkinCache
import matheo1712.cobbletrainers.client.cache.TrainerTeamCache
import matheo1712.cobbletrainers.client.gui.TrainerSpawnerScreen
import matheo1712.cobbletrainers.client.render.TrainerOutfitRenderer
import matheo1712.cobbletrainers.network.BattleIntroPayload
import matheo1712.cobbletrainers.network.BattleMusicPayload
import matheo1712.cobbletrainers.network.OpenBattlePhonePayload
import matheo1712.cobbletrainers.network.OpenTrainerSpawnerPayload
import matheo1712.cobbletrainers.network.TrainerSkinPayload
import matheo1712.cobbletrainers.network.TrainerTeamPayload
import matheo1712.cobbletrainers.CobblemonTrainers
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Client entrypoint.
 *
 * The mod is a server-side affair almost everywhere - trainers come from datapacks and fight
 * on the server. What cannot be: the two screens. The trainer spawner has one, and so does the
 * battle phone; a screen only exists on a client. This registers the packets that open them,
 * the two that feed the phone its skins and its teams, and the one thing here that is not a
 * screen: the party slot the player has selected, which Cobblemon keeps client-side.
 *
 * Drawing is the other thing only a client can do: a trainer's clothes are hung on Cobblemon's
 * model by [matheo1712.cobbletrainers.client.render.TrainerOutfitRenderer], out of a mixin
 * rather than from here - all this owes it is a chance to drop what it baked when the resource
 * packs change.
 */
object CobblemonTrainersClient : ClientModInitializer {

    override fun onInitializeClient() {
        ClientPokemonSelection.register()

        ClientPlayNetworking.registerGlobalReceiver(OpenTrainerSpawnerPayload.TYPE) { payload, context ->
            context.client().setScreen(TrainerSpawnerScreen(payload))
        }

        ClientPlayNetworking.registerGlobalReceiver(OpenBattlePhonePayload.TYPE) { payload, context ->
            context.client().setScreen(BattlePhoneScreen(payload))
        }

        ClientPlayNetworking.registerGlobalReceiver(TrainerSkinPayload.TYPE) { payload, _ ->
            TrainerSkinCache.accept(payload)
        }

        ClientPlayNetworking.registerGlobalReceiver(TrainerTeamPayload.TYPE) { payload, _ ->
            TrainerTeamCache.accept(payload)
        }

        // A screen too, but one nobody opened: the trainer's versus screen, raised by the
        // server a few seconds before the battle it announces.
        ClientPlayNetworking.registerGlobalReceiver(BattleIntroPayload.TYPE) { payload, context ->
            context.client().setScreen(BattleIntroScreen(payload))
        }

        // Not a screen either: the battle theme, which the client plays itself so that it can
        // loop and so that the world's own music stays out of the way.
        ClientPlayNetworking.registerGlobalReceiver(BattleMusicPayload.TYPE) { payload, _ ->
            val track = payload.track
            if (track == null) ClientBattleMusic.silence()
            else ClientBattleMusic.play(track, payload.volume, payload.pitch)
        }

        // Skins are cached for the world they were sent from: another server may hold other
        // trainers under the same IDs, and the textures are ours to free.
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            TrainerSkinCache.clear()
            TrainerTeamCache.clear()
            ClientBattleMusic.clear()
        }

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(OutfitReloadListener)
    }

    /**
     * Drops the vanilla armour models the outfit renderer baked. They come out of the resource
     * packs like any other model, so the ones baked from the old packs are stale the moment the
     * new ones are in.
     */
    private object OutfitReloadListener : SimpleSynchronousResourceReloadListener {
        override fun getFabricId(): ResourceLocation = CobblemonTrainers.id("outfits")

        override fun onResourceManagerReload(manager: ResourceManager) {
            TrainerOutfitRenderer.clear()
        }
    }
}
