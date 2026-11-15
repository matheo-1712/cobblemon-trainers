package matheo1712.cobbletrainers.battle

import matheo1712.cobbletrainers.battle.TrainerBattleForms.Features
import net.minecraft.nbt.CompoundTag
import kotlin.test.*

class TrainerBattleFormsTest {

    /** A feature holding one string, the shape Mega Showdown's `mega_evolution` choice takes. */
    private class Feature(val name: String, var value: String)

    private val access = Features.Access<Feature>(
        name = { it.name },
        save = { CompoundTag().apply { putString(it.name, it.value) } },
        load = { feature, tag -> feature.value = tag.getString(feature.name) }
    )

    @Test fun `a Mega Evolution kept by a winning trainer is put back`() {
        val mega = Feature("mega_evolution", "none")
        val regional = Feature("rlm", "true")
        val features = mutableListOf(regional, mega)
        val before = Features.capture(features, access)

        mega.value = "mega"
        val changes = Features.restore(features, before, access)

        assertEquals("none", mega.value)
        assertEquals(listOf(mega), changes.rewritten)
        assertTrue(changes.any)
        assertEquals("true", regional.value)
    }

    @Test fun `a team the battle did not touch is left alone`() {
        val features = mutableListOf(Feature("rlm", "true"), Feature("mega_evolution", "none"))
        val before = Features.capture(features, access)

        assertFalse(Features.restore(features, before, access).any)
        assertEquals(2, features.size)
    }

    /**
     * The case seen in game: a trainer's Mawile is created without `mega_evolution`, Mega Showdown
     * adds it when it evolves. Dropping it has to count as a change, or the form is never worked
     * out again and the Pokémon stays Mega.
     */
    @Test fun `a feature the battle added is dropped and counts as a change`() {
        val features = mutableListOf(Feature("rlm", "true"))
        val before = Features.capture(features, access)

        features += Feature("mega_evolution", "mega")
        val changes = Features.restore(features, before, access)

        assertEquals(listOf("rlm"), features.map { it.name })
        assertTrue(changes.any)
        assertTrue(changes.rewritten.isEmpty())
    }
}
