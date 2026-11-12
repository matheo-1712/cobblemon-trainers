package matheo1712.cobbletrainers.trainers

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import net.minecraft.resources.ResourceLocation
import kotlin.test.*

class TrainerDefinitionTest {
    @Test fun `partial trainer JSON preserves nested defaults and explicit silence`() {
        val gson = Gson()
        val trainer = gson.fromJson("{}", TrainerDefinition::class.java)
        assertEquals(TrainerDefinition(), trainer)
        assertTrue(trainer.callable())
        assertNull(trainer.requirements())
        val silent = gson.fromJson("""{"battle":{"music":null,"level":50},"notCallable":true}""", TrainerDefinition::class.java)
        assertNull(silent.battle.music)
        assertEquals(TrainerLevel(value = 50), silent.battle.level)
        assertEquals(5, silent.battle.difficulty)
        assertFalse(silent.callable())
    }

    @Test fun `level reads a plain number or an automatic block`() {
        val gson = Gson()
        fun level(json: String) = gson.fromJson("""{"battle":{"level":$json}}""", TrainerDefinition::class.java).battle.level

        assertEquals(TrainerLevel(value = 50), level("50"))
        assertEquals(TrainerLevel(value = 50), level("""{"auto": false, "value": 50}"""))
        assertEquals(TrainerLevel(auto = true), level("""{"auto": true}"""))
        assertEquals(
            TrainerLevel(auto = true, offset = -5, min = 10, max = 60),
            level("""{"auto": true, "offset": -5, "min": 10, "max": 60}""")
        )
        assertFailsWith<JsonSyntaxException> { level("\"auto\"") }

        // What is written reads back the same, and a fixed level stays a plain number.
        val auto = TrainerLevel(auto = true, offset = 5, max = 60)
        assertEquals(auto, gson.fromJson(gson.toJson(auto), TrainerLevel::class.java))
        assertEquals("50", gson.toJson(TrainerLevel(value = 50)))
    }

    @Test fun `presentation alone never locks a trainer but real requirements do`() {
        assertNull(TrainerDefinition(requires = TrainerRequirements(hidden = true, message = "Locked")).requirements())
        assertNull(TrainerDefinition(requires = TrainerRequirements(victories = TrainerVictoriesRequirement())).requirements())
        assertNotNull(TrainerDefinition(requires = TrainerRequirements(defeated = listOf("pack:erika"))).requirements())
        val itemLocked = TrainerDefinition(requires = TrainerRequirements(
            items = listOf(TrainerItemRequirement(item = "minecraft:diamond"))
        ))
        assertNotNull(itemLocked.requirements())
    }

    @Test fun `rematch rules accept case variants and keep unknown values permissive`() {
        assertFalse(TrainerProgressRules(rematch = "NEVER").allowsRematch)
        assertTrue(TrainerProgressRules().allowsRematch)
        assertTrue(TrainerProgressRules(rematch = "typo").allowsRematch)
    }

    @Test fun `nested category and persistent trainer identity do not require loaded packs`() {
        val trainer = ResourceLocation.parse("pack:league/elite/erika")
        assertEquals(ResourceLocation.parse("pack:league/elite"), TrainerRegistry.categoryOf(trainer))
        assertNull(TrainerRegistry.categoryOf(ResourceLocation.parse("pack:erika")))
        assertEquals(trainer, TrainerRegistry.idFromAspects(listOf("model-slim", "trainer_id:$trainer")))
        assertNull(TrainerRegistry.idFromAspects(listOf("trainer_id:INVALID ID")))
        assertNull(TrainerRegistry.idFromAspects(listOf("model-slim")))
    }
}
