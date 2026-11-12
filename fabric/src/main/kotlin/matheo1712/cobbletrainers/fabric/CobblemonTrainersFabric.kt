package matheo1712.cobbletrainers.fabric

import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.battlephone.BattlePhone
import net.fabricmc.api.ModInitializer

class CobblemonTrainersFabric : ModInitializer {
    override fun onInitialize() {
        CobblemonTrainers.onInitialize()
        // Its own init until it becomes a mod of its own: see BattlePhone.
        BattlePhone.onInitialize()
    }
}
