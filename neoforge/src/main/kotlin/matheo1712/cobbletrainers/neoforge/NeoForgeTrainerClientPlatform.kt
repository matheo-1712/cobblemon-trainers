package matheo1712.cobbletrainers.neoforge

import matheo1712.cobbletrainers.client.CobblemonTrainersClient
import matheo1712.cobbletrainers.client.platform.TrainerClientPlatform
import matheo1712.cobbletrainers.platform.Platform
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.PacketDistributor

class NeoForgeTrainerClientPlatform : TrainerClientPlatform {
    companion object {
        fun initialize(bus: IEventBus) {
            CobblemonTrainersClient.onInitializeClient()
            bus.addListener { event: RegisterClientReloadListenersEvent ->
                (Platform.current as NeoForgeTrainerPlatform).clientReloads.forEach(event::registerReloadListener)
            }
        }
    }
    override fun onClientTick(handler: (Minecraft) -> Unit) {
        NeoForge.EVENT_BUS.addListener { _: ClientTickEvent.Post -> handler(Minecraft.getInstance()) }
    }
    override fun onDisconnect(handler: () -> Unit) {
        NeoForge.EVENT_BUS.addListener { _: ClientPlayerNetworkEvent.LoggingOut -> handler() }
    }
    override fun <T : CustomPacketPayload> receive(type: CustomPacketPayload.Type<T>, handler: (T, Minecraft) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        (Platform.current as NeoForgeTrainerPlatform).clientReceivers[type.id()] = { payload, _ ->
            handler(payload as T, Minecraft.getInstance())
        }
    }
    override fun canSend(type: CustomPacketPayload.Type<*>) = Minecraft.getInstance().connection?.hasChannel(type.id()) == true
    override fun send(payload: CustomPacketPayload) = PacketDistributor.sendToServer(payload)
}
