package matheo1712.cobbletrainers.neoforge

import com.mojang.brigadier.CommandDispatcher
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.platform.TrainerPlatform
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.CreativeModeTab
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import net.neoforged.neoforge.registries.RegisterEvent
import java.nio.file.Path

class NeoForgeTrainerPlatform : TrainerPlatform {
    private val registrations = mutableListOf<(RegisterEvent) -> Unit>()
    private val payloads = mutableListOf<(PayloadRegistrar) -> Unit>()
    private val serverReceivers = mutableMapOf<ResourceLocation, (CustomPacketPayload, ServerPlayer) -> Unit>()
    internal val clientReceivers = mutableMapOf<ResourceLocation, (CustomPacketPayload, IPayloadContext) -> Unit>()
    internal val clientReloads = mutableListOf<ResourceManagerReloadListener>()

    fun attach(bus: IEventBus) {
        bus.addListener { event: RegisterEvent ->
            // Intrusive block/item holders can only be created after NeoForge unfreezes
            // the registries. Blocks precede all other registries used by this mod.
            if (event.registryKey == Registries.BLOCK) CobblemonTrainers.onInitialize()
            registrations.forEach { it(event) }
        }
        bus.addListener { event: RegisterPayloadHandlersEvent ->
            val registrar = event.registrar("1")
            payloads.forEach { it(registrar) }
        }
    }

    override val gameDirectory: Path get() = FMLPaths.GAMEDIR.get()
    override val modsDirectory: Path get() = FMLPaths.MODSDIR.get()
    override val modMetadataPaths = listOf(listOf("META-INF", "neoforge.mods.toml"), listOf("META-INF", "mods.toml"), listOf("fabric.mod.json"))
    override fun isModLoaded(id: String) = ModList.get().isLoaded(id)
    override fun registerCommands(handler: (CommandDispatcher<CommandSourceStack>) -> Unit) {
        NeoForge.EVENT_BUS.addListener { event: RegisterCommandsEvent -> handler(event.dispatcher) }
    }
    override fun registerReloadListener(type: PackType, id: ResourceLocation, reload: (ResourceManager) -> Unit) {
        val listener = ResourceManagerReloadListener { reload(it) }
        if (type == PackType.CLIENT_RESOURCES) clientReloads.add(listener)
        else NeoForge.EVENT_BUS.addListener { event: AddReloadListenerEvent -> event.addListener(listener) }
    }
    override fun onServerTick(handler: (MinecraftServer) -> Unit) {
        NeoForge.EVENT_BUS.addListener { event: ServerTickEvent.Post -> handler(event.server) }
    }
    override fun onServerStopped(handler: (MinecraftServer) -> Unit) {
        NeoForge.EVENT_BUS.addListener { event: ServerStoppedEvent -> handler(event.server) }
    }
    override fun onEntityLoad(handler: (Entity, ServerLevel) -> Unit) {
        NeoForge.EVENT_BUS.addListener { event: EntityJoinLevelEvent ->
            (event.level as? ServerLevel)?.let { handler(event.entity, it) }
        }
    }
    override fun onEntityUnload(handler: (Entity, ServerLevel) -> Unit) {
        NeoForge.EVENT_BUS.addListener { event: EntityLeaveLevelEvent ->
            (event.level as? ServerLevel)?.let { handler(event.entity, it) }
        }
    }
    override fun onLivingDeath(handler: (LivingEntity, DamageSource) -> Unit) {
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, false,
            java.util.function.Consumer<LivingDeathEvent> { event ->
                if (!event.entity.level().isClientSide) handler(event.entity, event.source)
            })
    }
    override fun onDisconnect(handler: (ServerPlayer) -> Unit) {
        NeoForge.EVENT_BUS.addListener { event: PlayerEvent.PlayerLoggedOutEvent ->
            (event.entity as? ServerPlayer)?.let(handler)
        }
    }
    override fun <T : Any> register(registry: Registry<T>, id: ResourceLocation, value: T) {
        registrations.add { event -> event.register(registry.key(), id) { value } }
    }
    override fun creativeTabBuilder(): CreativeModeTab.Builder = CreativeModeTab.builder()
    override fun <T : CustomPacketPayload> registerS2C(type: CustomPacketPayload.Type<T>, codec: StreamCodec<in RegistryFriendlyByteBuf, T>) {
        payloads.add { registrar -> registrar.playToClient(type, codec) { payload, context ->
            checkNotNull(clientReceivers[type.id()]) { "Missing client receiver for ${type.id()}" }(payload, context)
        } }
    }
    override fun <T : CustomPacketPayload> registerC2S(type: CustomPacketPayload.Type<T>, codec: StreamCodec<in RegistryFriendlyByteBuf, T>) {
        payloads.add { registrar -> registrar.playToServer(type, codec) { payload, context ->
            checkNotNull(serverReceivers[type.id()]) { "Missing server receiver for ${type.id()}" }(payload, context.player() as ServerPlayer)
        } }
    }
    override fun <T : CustomPacketPayload> receive(type: CustomPacketPayload.Type<T>, handler: (T, ServerPlayer) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        serverReceivers[type.id()] = { payload, player -> handler(payload as T, player) }
    }
    override fun canSend(player: ServerPlayer, type: CustomPacketPayload.Type<*>) = player.connection.hasChannel(type.id())
    override fun send(player: ServerPlayer, payload: CustomPacketPayload) = PacketDistributor.sendToPlayer(player, payload)
}
