package matheo1712.cobbletrainers.trainers

import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.Items
import org.junit.BeforeClass
import kotlin.test.*

class TrainerRewardsTest {
    private val registries get() = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)

    companion object {
        @JvmStatic @BeforeClass fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test fun `claimed trophies remain visible while secret rewards stay off the wire`() {
        val rewards = listOf(
            TrainerReward("minecraft:diamond", firstWinOnly = true),
            TrainerReward("minecraft:apple", count = 3),
            TrainerReward("minecraft:emerald", hidden = true)
        )
        val first = TrainerRewards.preview(rewards, registries, firstWin = true)
        val rematch = TrainerRewards.preview(rewards, registries, firstWin = false)
        assertEquals(2, first.size)
        assertEquals(2, rematch.size)
        assertTrue(first.all { it.due })
        assertTrue(rematch[0].once)
        assertFalse(rematch[0].due)
        assertEquals(Items.DIAMOND, rematch[0].stack.item)
        assertFalse(rematch[1].once)
        assertTrue(rematch[1].due)
    }

    @Test fun `command rewards never reach the phone preview`() {
        val previews = TrainerRewards.preview(listOf(
            TrainerReward(command = "give @s minecraft:diamond"),
            TrainerReward("minecraft:apple", command = "say both"),
            TrainerReward("minecraft:apple")
        ), registries, firstWin = true)
        assertEquals(listOf(Items.APPLE), previews.map { it.stack.item })
    }

    @Test fun `bad reward IDs do not remove valid rewards and quantities stay bounded`() {
        val previews = TrainerRewards.preview(listOf(
            TrainerReward(""), TrainerReward("BAD ID"), TrainerReward("missing:item"),
            TrainerReward("minecraft:apple", count = Int.MIN_VALUE),
            TrainerReward("minecraft:diamond", count = Int.MAX_VALUE)
        ), registries, firstWin = true)
        assertEquals(listOf(1, 6400), previews.map { it.stack.count })
    }

    @Test fun `reward components survive parsing for the phone preview`() {
        val previews = TrainerRewards.preview(listOf(
            TrainerReward("""minecraft:diamond[minecraft:custom_name='{"text":"Badge"}']"""),
            TrainerReward("minecraft:diamond_sword[minecraft:damage=7]")
        ), registries, firstWin = true)
        assertEquals(2, previews.size)
        assertEquals("Badge", previews[0].stack.get(DataComponents.CUSTOM_NAME)?.string)
        assertEquals(7, previews[1].stack.get(DataComponents.DAMAGE))
    }

    @Test fun `malformed components and trailing arguments cannot become plain rewards`() {
        val previews = TrainerRewards.preview(listOf(
            TrainerReward("minecraft:diamond_sword[minecraft:damage=oops]"),
            TrainerReward("minecraft:diamond 64"),
            TrainerReward("minecraft:diamond[minecraft:unknown=1]"),
            TrainerReward("minecraft:apple")
        ), registries, firstWin = true)
        assertEquals(1, previews.size)
        assertEquals(Items.APPLE, previews.single().stack.item)
    }

    @Test fun `cosmetic aspects round trip each body slot and ignore unrelated aspects`() {
        val item = net.minecraft.resources.ResourceLocation.parse("minecraft:diamond")
        for (slot in TrainerTrinketSlot.entries) {
            val decoded = assertNotNull(TrainerOutfit.readTrinketAspect(TrainerOutfit.trinketAspect(slot, item)))
            assertEquals(slot, decoded.first)
            assertEquals(Items.DIAMOND, decoded.second.item)
        }
        assertNull(TrainerOutfit.readTrinketAspect("model-slim"))
        assertNull(TrainerOutfit.readTrinketAspect("trainer_trinket:unknown:minecraft:diamond"))
        assertNull(TrainerOutfit.readTrinketAspect("trainer_trinket:wrist:missing:item"))
    }
}
