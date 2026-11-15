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
        val changed = Features.restore(features, before, access)

        assertEquals("none", mega.value)
        assertEquals(listOf(mega), changed)
        assertEquals("true", regional.value)
    }

    @Test fun `a team the battle did not touch is left alone`() {
        val features = mutableListOf(Feature("rlm", "true"), Feature("mega_evolution", "none"))
        val before = Features.capture(features, access)

        assertTrue(Features.restore(features, before, access).isEmpty())
        assertEquals(2, features.size)
    }

    @Test fun `a feature the battle added is dropped`() {
        val features = mutableListOf(Feature("rlm", "true"))
        val before = Features.capture(features, access)

        features += Feature("mega_evolution", "mega")
        Features.restore(features, before, access)

        assertEquals(listOf("rlm"), features.map { it.name })
    }
}
