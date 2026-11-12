package matheo1712.cobbletrainers.battlephone.client

import matheo1712.cobbletrainers.battlephone.OpenBattlePhonePayload
import matheo1712.cobbletrainers.battlephone.TrainerTeamPayload
import matheo1712.cobbletrainers.client.platform.ClientPlatform

/**
 * The client half of the battle phone: its screen, and the teams it is sent. The skins it draws
 * come through the mod's own [matheo1712.cobbletrainers.client.cache.TrainerSkinCache], which
 * the versus screen reads too.
 */
object BattlePhoneClient {

    fun onInitializeClient() {
        ClientPlatform.current.receive(OpenBattlePhonePayload.TYPE) { payload, client ->
            client.setScreen(BattlePhoneScreen(payload))
        }

        ClientPlatform.current.receive(TrainerTeamPayload.TYPE) { payload, _ ->
            TrainerTeamCache.accept(payload)
        }

        // Teams are cached for the world they were sent from: another server may hold other
        // trainers under the same IDs, and its own record of who beat them.
        ClientPlatform.current.onDisconnect { TrainerTeamCache.clear() }
    }
}
