package matheo1712.cobbletrainers.fabric

import com.mojang.brigadier.CommandDispatcher
import matheo1712.cobbletrainers.platform.TrainerPlatform
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.fabricmc.loader.api.FabricLoader
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

class FabricTrainerPlatform : TrainerPlatform {
    override val gameDirectory: Path get() = FabricLoader.getInstance().gameDir
    override val modsDirectory: Path get() =
        System.getProperty("fabric.modsFolder")?.let(Path::of) ?: gameDirectory.resolve("mods")
    override val modMetadataPaths = listOf(listOf("fabric.mod.json"))
    override fun isModLoaded(id: String) = FabricLoader.getInstance().isModLoaded(id)
    override fun registerCommands(handler: (CommandDispatcher<CommandSourceStack>) -> Unit) {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> handler(dispatcher) }
    }
    override fun registerReloadListener(type: PackType, id: ResourceLocation, reload: (ResourceManager) -> Unit) {
        ResourceManagerHelper.get(type).registerReloadListener(object : SimpleSynchronousResourceReloadListener {
            override fun getFabricId() = id
            override fun onResourceManagerReload(manager: ResourceManager) = reload(manager)
        })
    }
    override fun onServerTick(handler: (MinecraftServer) -> Unit) {
        ServerTickEvents.END_SERVER_TICK.register { handler(it) }
    }
    override fun onServerStopped(handler: (MinecraftServer) -> Unit) {
        ServerLifecycleEvents.SERVER_STOPPED.register { handler(it) }
    }
    override fun onEntityLoad(handler: (Entity, ServerLevel) -> Unit) {
        ServerEntityEvents.ENTITY_LOAD.register { entity, level -> handler(entity, level) }
    }
    override fun onEntityUnload(handler: (Entity, ServerLevel) -> Unit) {
        ServerEntityEvents.ENTITY_UNLOAD.register { entity, level -> handler(entity, level) }
    }
    override fun onLivingDeath(handler: (LivingEntity, DamageSource) -> Unit) {
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source -> handler(entity, source) }
    }
    override fun onDisconnect(handler: (ServerPlayer) -> Unit) {
        ServerPlayConnectionEvents.DISCONNECT.register { connection, _ -> handler(connection.player) }
    }
    override fun <T : Any> register(registry: Registry<T>, id: ResourceLocation, value: T) {
        Registry.register(registry, id, value)
    }
    override fun creativeTabBuilder(): CreativeModeTab.Builder = FabricItemGroup.builder()
    override fun <T : CustomPacketPayload> registerS2C(type: CustomPacketPayload.Type<T>, codec: StreamCodec<in RegistryFriendlyByteBuf, T>) {
        PayloadTypeRegistry.playS2C().register(type, codec)
    }
    override fun <T : CustomPacketPayload> registerC2S(type: CustomPacketPayload.Type<T>, codec: StreamCodec<in RegistryFriendlyByteBuf, T>) {
        PayloadTypeRegistry.playC2S().register(type, codec)
    }
    override fun <T : CustomPacketPayload> receive(type: CustomPacketPayload.Type<T>, handler: (T, ServerPlayer) -> Unit) {
        ServerPlayNetworking.registerGlobalReceiver(type) { payload, context -> handler(payload, context.player()) }
    }
    override fun canSend(player: ServerPlayer, type: CustomPacketPayload.Type<*>) = ServerPlayNetworking.canSend(player, type)
    override fun send(player: ServerPlayer, payload: CustomPacketPayload) = ServerPlayNetworking.send(player, payload)
}
