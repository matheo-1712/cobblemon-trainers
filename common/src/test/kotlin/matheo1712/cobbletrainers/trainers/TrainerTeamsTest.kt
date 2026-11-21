package matheo1712.cobbletrainers.trainers

import com.google.gson.Gson
import kotlin.test.*

class TrainerTeamsTest {
    private fun def(json: String) = Gson().fromJson(json, TrainerDefinition::class.java)

    private val tiered = def(
        """{"team":["Charizard"],"teams":[{"maxLevel":15,"team":["Charmander"]},{"maxLevel":35,"team":["Charmeleon"]}]}"""
    )

    @Test fun `first covering tier wins, inclusive`() {
        assertEquals(listOf("Charmander"), TrainerTeams.teamFor(tiered, 1))
        assertEquals(listOf("Charmander"), TrainerTeams.teamFor(tiered, 15))
        assertEquals(listOf("Charmeleon"), TrainerTeams.teamFor(tiered, 16))
        assertEquals(listOf("Charizard"), TrainerTeams.teamFor(tiered, 36))
        assertEquals("15", TrainerTeams.keyFor(tiered, 15))
        assertEquals(TrainerTeams.DEFAULT_KEY, TrainerTeams.keyFor(tiered, 36))
    }

    @Test fun `no tiers keeps the plain team`() {
        val plain = def("""{"team":["Pikachu"]}""")
        assertEquals(listOf("Pikachu"), TrainerTeams.teamFor(plain, 99))
        assertEquals(TrainerTeams.DEFAULT_KEY, TrainerTeams.keyFor(plain, 99))
        assertTrue(TrainerTeams.problems(plain).isEmpty())
        assertTrue(TrainerTeams.problems(tiered).isEmpty())
    }

    @Test fun `problems name unreachable, empty and missing fallback`() {
        val unordered = def("""{"team":["A"],"teams":[{"maxLevel":30,"team":["B"]},{"maxLevel":20,"team":["C"]}]}""")
        assertEquals(1, TrainerTeams.problems(unordered).size)
        val emptyTier = def("""{"team":["A"],"teams":[{"maxLevel":10,"team":[]}]}""")
        assertEquals(1, TrainerTeams.problems(emptyTier).size)
        val noFallback = def("""{"teams":[{"maxLevel":10,"team":["B"]}]}""")
        assertEquals(1, TrainerTeams.problems(noFallback).size)
    }

    @Test fun `team behind an uncapped tier is flagged as never used`() {
        val dead = def("""{"team":["A"],"teams":[{"team":["B"]}]}""")
        assertEquals(1, TrainerTeams.problems(dead).size)
    }

    @Test fun `missing maxLevel never caps`() {
        val open = def("""{"teams":[{"team":["B"]}]}""")
        assertEquals(listOf("B"), TrainerTeams.teamFor(open, 100))
        assertTrue(TrainerTeams.problems(open).isEmpty())
    }
}
