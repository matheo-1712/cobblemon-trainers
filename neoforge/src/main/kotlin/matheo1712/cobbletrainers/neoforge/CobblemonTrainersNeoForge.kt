package matheo1712.cobbletrainers.neoforge

import matheo1712.cobbletrainers.platform.Platform
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod

// NeoForge requires underscores in mod IDs; resource and saved-data IDs stay unchanged.
@Mod("cobblemon_trainers")
class CobblemonTrainersNeoForge(bus: IEventBus) {
    init {
        (Platform.current as NeoForgeTrainerPlatform).attach(bus)
    }
}

@Mod(value = "cobblemon_trainers", dist = [Dist.CLIENT])
class CobblemonTrainersNeoForgeClient(bus: IEventBus) {
    init {
        NeoForgeTrainerClientPlatform.initialize(bus)
    }
}
