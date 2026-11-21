package matheo1712.cobbletrainers.battle

import java.util.UUID
import kotlin.test.*

class TrainerBattleStateTest {
    @Test fun `a battle id the registry no longer holds is not a battle`() {
        val ended = UUID.randomUUID()
        // Cobblemon leaves the id on the NPC for a few seconds after the battle closed.
        assertFalse(TrainerBattleState.inLiveBattle(listOf(ended)) { false })
    }

    @Test fun `a battle the registry still holds is one`() {
        val live = UUID.randomUUID()
        assertTrue(TrainerBattleState.inLiveBattle(listOf(UUID.randomUUID(), live)) { it == live })
        assertFalse(TrainerBattleState.inLiveBattle(emptyList()) { true })
    }
}
