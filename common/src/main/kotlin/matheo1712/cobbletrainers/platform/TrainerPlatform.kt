package matheo1712.cobbletrainers.platform

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.Registry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.CreativeModeTab
import java.nio.file.Path
import java.util.ServiceLoader

/** Loader bindings only. Game rules and packet validation belong to the common callers. */
interface TrainerPlatform {
    val gameDirectory: Path
    val modsDirectory: Path
    val modMetadataPaths: List<List<String>>
    fun isModLoaded(id: String): Boolean
    fun registerCommands(handler: (CommandDispatcher<CommandSourceStack>) -> Unit)
    fun registerReloadListener(type: PackType, id: ResourceLocation, reload: (ResourceManager) -> Unit)
    fun onServerTick(handler: (MinecraftServer) -> Unit)
    fun onServerStopped(handler: (MinecraftServer) -> Unit)
    fun onEntityLoad(handler: (Entity, ServerLevel) -> Unit)
    fun onEntityUnload(handler: (Entity, ServerLevel) -> Unit)
    fun onLivingDeath(handler: (LivingEntity, DamageSource) -> Unit)
    fun onDisconnect(handler: (ServerPlayer) -> Unit)
    fun <T : Any> register(registry: Registry<T>, id: ResourceLocation, value: T)
    fun creativeTabBuilder(): CreativeModeTab.Builder
    fun <T : CustomPacketPayload> registerS2C(type: CustomPacketPayload.Type<T>, codec: StreamCodec<in RegistryFriendlyByteBuf, T>)
    fun <T : CustomPacketPayload> registerC2S(type: CustomPacketPayload.Type<T>, codec: StreamCodec<in RegistryFriendlyByteBuf, T>)
    /** Implementations must invoke the handler on the server thread. */
    fun <T : CustomPacketPayload> receive(type: CustomPacketPayload.Type<T>, handler: (T, ServerPlayer) -> Unit)
    fun canSend(player: ServerPlayer, type: CustomPacketPayload.Type<*>): Boolean
    fun send(player: ServerPlayer, payload: CustomPacketPayload)
}

/** Lazy discovery also works for pack mixins invoked before the mod entrypoint. */
object Platform {
    val current: TrainerPlatform by lazy {
        ServiceLoader.load(TrainerPlatform::class.java, TrainerPlatform::class.java.classLoader).single()
    }
}
