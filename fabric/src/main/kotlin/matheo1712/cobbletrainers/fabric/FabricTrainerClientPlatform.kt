package matheo1712.cobbletrainers.fabric

import matheo1712.cobbletrainers.client.platform.TrainerClientPlatform
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

class FabricTrainerClientPlatform : TrainerClientPlatform {
    override fun onClientTick(handler: (Minecraft) -> Unit) {
        ClientTickEvents.END_CLIENT_TICK.register { handler(it) }
    }
    override fun onDisconnect(handler: () -> Unit) {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> handler() }
    }
    override fun <T : CustomPacketPayload> receive(type: CustomPacketPayload.Type<T>, handler: (T, Minecraft) -> Unit) {
        ClientPlayNetworking.registerGlobalReceiver(type) { payload, context -> handler(payload, context.client()) }
    }
    override fun canSend(type: CustomPacketPayload.Type<*>) = ClientPlayNetworking.canSend(type)
    override fun send(payload: CustomPacketPayload) = ClientPlayNetworking.send(payload)
}
