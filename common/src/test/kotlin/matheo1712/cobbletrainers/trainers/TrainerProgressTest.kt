package matheo1712.cobbletrainers.trainers

import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.resources.ResourceLocation
import java.util.UUID
import kotlin.test.*

class TrainerProgressTest {
    private val trainer = ResourceLocation.parse("pack:champions/erika")
    private val player = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val other = UUID.fromString("00000000-0000-4000-8000-000000000002")

    @Test fun `only new wins dirty the save and players remain independent`() {
        val progress = TrainerProgress()
        assertFalse(progress.hasDefeated(trainer, player))
        assertTrue(progress.recordVictory(trainer, player))
        assertTrue(progress.isDirty)
        progress.setDirty(false)
        assertFalse(progress.recordVictory(trainer, player))
        assertFalse(progress.isDirty)
        assertFalse(progress.hasDefeated(trainer, other))
        val snapshot = progress.defeatedTrainersOf(player)
        assertTrue(progress.forgetVictory(trainer, player))
        assertEquals(setOf(trainer), snapshot)
        assertTrue(progress.defeatedTrainersOf(player).isEmpty())
    }

    @Test fun `a finished league is recorded apart from victories and survives a round trip`() {
        val league = ResourceLocation.parse("pack:council")
        val progress = TrainerProgress()
        assertFalse(progress.hasCompletedLeague(league, player))
        assertTrue(progress.recordLeague(league, player))
        assertTrue(progress.isDirty)
        assertFalse(progress.recordLeague(league, player))
        assertTrue(progress.defeatedTrainersOf(player).isEmpty())
        val loaded = TrainerProgress.load(progress.save(CompoundTag(), RegistryAccess.EMPTY))
        assertTrue(loaded.hasCompletedLeague(league, player))
        assertFalse(loaded.hasCompletedLeague(league, other))
        // A file written before leagues existed has no such key, and reads as none finished.
        assertFalse(TrainerProgress.load(CompoundTag()).hasCompletedLeague(league, player))
    }

    @Test fun `round trip preserves victories even without a loaded datapack`() {
        val progress = TrainerProgress()
        progress.recordVictory(trainer, player)
        progress.recordVictory(trainer, other)
        val loaded = TrainerProgress.load(progress.save(CompoundTag(), RegistryAccess.EMPTY))
        assertFalse(loaded.isDirty)
        assertTrue(loaded.hasDefeated(trainer, player))
        assertTrue(loaded.hasDefeated(trainer, other))
        assertTrue(loaded.forgetVictory(trainer, player))
        assertTrue(loaded.hasDefeated(trainer, other))
        assertTrue(loaded.forgetVictory(trainer, other))
        assertTrue(loaded.save(CompoundTag(), RegistryAccess.EMPTY).getCompound("defeated").isEmpty)
        loaded.setDirty(false)
        assertFalse(loaded.forgetVictory(trainer, other))
        assertFalse(loaded.isDirty)
    }

    @Test fun `corrupt entries do not discard valid progress`() {
        val players = ListTag().apply {
            add(StringTag.valueOf("bad-uuid"))
            add(StringTag.valueOf(player.toString()))
            add(StringTag.valueOf(player.toString()))
        }
        val entries = CompoundTag().apply {
            put(trainer.toString(), players)
            put("INVALID ID", players.copy())
        }
        val loaded = TrainerProgress.load(CompoundTag().apply { put("defeated", entries) })
        assertEquals(setOf(trainer), loaded.defeatedTrainersOf(player))
    }
}
