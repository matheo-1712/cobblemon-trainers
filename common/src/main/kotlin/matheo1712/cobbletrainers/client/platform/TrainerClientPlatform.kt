package matheo1712.cobbletrainers.client.platform

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.ServiceLoader

/** Kept separate from the common service so a dedicated server never loads client classes. */
interface TrainerClientPlatform {
    fun onClientTick(handler: (Minecraft) -> Unit)
    fun onDisconnect(handler: () -> Unit)
    /** Implementations must invoke the handler on the client thread. */
    fun <T : CustomPacketPayload> receive(type: CustomPacketPayload.Type<T>, handler: (T, Minecraft) -> Unit)
    fun canSend(type: CustomPacketPayload.Type<*>): Boolean
    fun send(payload: CustomPacketPayload)
}

object ClientPlatform {
    val current: TrainerClientPlatform by lazy {
        ServiceLoader.load(TrainerClientPlatform::class.java, TrainerClientPlatform::class.java.classLoader).single()
    }
}
