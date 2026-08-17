package matheo1712.cobbletrainers.client

import matheo1712.cobbletrainers.network.OpenTrainerSpawnerPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * Client entrypoint.
 *
 * The mod is a server-side affair almost everywhere — trainers come from datapacks and fight
 * on the server. The one thing that cannot be: the trainer spawner has a screen, and a screen
 * only exists on a client. This registers the packet that opens it and nothing else.
 */
object CobblemonTrainersClient : ClientModInitializer {

    override fun onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenTrainerSpawnerPayload.TYPE) { payload, context ->
            context.client().setScreen(TrainerSpawnerScreen(payload))
        }
    }
}
