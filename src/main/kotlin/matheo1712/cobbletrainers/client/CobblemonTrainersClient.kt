package matheo1712.cobbletrainers.client

import matheo1712.cobbletrainers.client.gui.BattlePhoneScreen
import matheo1712.cobbletrainers.client.cache.TrainerSkinCache
import matheo1712.cobbletrainers.client.cache.TrainerTeamCache
import matheo1712.cobbletrainers.client.gui.TrainerSpawnerScreen
import matheo1712.cobbletrainers.network.BattleMusicPayload
import matheo1712.cobbletrainers.network.OpenBattlePhonePayload
import matheo1712.cobbletrainers.network.OpenTrainerSpawnerPayload
import matheo1712.cobbletrainers.network.TrainerSkinPayload
import matheo1712.cobbletrainers.network.TrainerTeamPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * Client entrypoint.
 *
 * The mod is a server-side affair almost everywhere - trainers come from datapacks and fight
 * on the server. What cannot be: the two screens. The trainer spawner has one, and so does the
 * battle phone; a screen only exists on a client. This registers the packets that open them,
 * the two that feed the phone its skins and its teams, and the one thing here that is not a
 * screen: the party slot the player has selected, which Cobblemon keeps client-side.
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

        // Not a screen either: whether the world's own music has to stay quiet, which only a
        // client can decide and only the server knows about.
        ClientPlayNetworking.registerGlobalReceiver(BattleMusicPayload.TYPE) { payload, _ ->
            ClientBattleMusic.accept(payload.playing)
        }

        // Skins are cached for the world they were sent from: another server may hold other
        // trainers under the same IDs, and the textures are ours to free.
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            TrainerSkinCache.clear()
            TrainerTeamCache.clear()
            ClientBattleMusic.clear()
        }
    }
}
