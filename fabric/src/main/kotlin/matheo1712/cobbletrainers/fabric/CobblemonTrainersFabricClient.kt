package matheo1712.cobbletrainers.fabric

import matheo1712.cobbletrainers.battlephone.client.BattlePhoneClient
import matheo1712.cobbletrainers.client.CobblemonTrainersClient
import net.fabricmc.api.ClientModInitializer

class CobblemonTrainersFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        CobblemonTrainersClient.onInitializeClient()
        BattlePhoneClient.onInitializeClient()
    }
}
