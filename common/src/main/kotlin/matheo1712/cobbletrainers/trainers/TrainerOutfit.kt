package matheo1712.cobbletrainers.trainers

import com.cobblemon.mod.common.entity.npc.NPCEntity
import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack

/**
 * Where on a body a trinket hangs.
 *
 * These are **places, not purposes**, and that is the whole point: a key item is not always a
 * wrist band. Mega Showdown's own `mega_slot` holds bracelets and rings, but also Maxie's
 * Glasses, Lisia's Tiara, Archie's Anchor, Diantha's Charm and Zinnia's Anklet - five different
 * spots for one slot. Nothing in the game's data says which is which: that mod picks the spot
 * in code, item by item, so no tag and no registry can be asked. The pack is the only one who
 * knows, so the pack is who says.
 *
 * The seven cover every spot Mega Showdown draws to, which is the widest set anyone is likely
 * to need - and a pack is free to hang something of its own at any of them.
 */
enum class TrainerTrinketSlot(val id: String) {

    /** On the head: glasses, a tiara, a visor. */
    FACE("face"),

    /** Hung on the chest: a pendant, a charm, an anchor. */
    CHEST("chest"),

    /** The wrist of the free arm: a bracelet, a glove. */
    WRIST("wrist"),

    /** The same arm, a little higher: a ring. */
    FOREARM("forearm"),

    /** Across the back of the main hand: a band. */
    HAND("hand"),

    /** Hooked on the belt: an orb, a pouch. */
    BELT("belt"),

    /** The right ankle: an anklet. */
    ANKLE("ankle");

    companion object {
        fun of(id: String): TrainerTrinketSlot? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Dresses a trainer: the one place a [TrainerCosmetics] block becomes items on an entity.
 *
 * There are two halves to it, and they travel to the clients by two different roads.
 *
 * **The six equipment slots** are vanilla's own, so the clothes ride along with the vanilla
 * equipment packet: every client that can see the trainer is told, a client that starts seeing
 * it later is told then, and a restart keeps it because equipment is saved to NBT like the rest
 * of the entity.
 *
 * **The trinkets** have no vanilla slot to sit in, so they travel as **aspects** -
 * `trainer_trinket:<slot>:<item id>` - exactly like the trainer ID itself. Aspects are synced
 * to every client and written to NBT, which buys the same three guarantees for nothing. It also
 * means the mod never has to remember, on either side, what a given trainer is wearing: the
 * entity carries it.
 *
 * What Cobblemon draws of all this is *nothing* - its NPC renderer has neither an armour layer
 * nor a held-item layer that the shipped trainer rig can feed. The drawing is the mod's own,
 * in [matheo1712.cobbletrainers.client.render.TrainerOutfitRenderer]; this half only has to put
 * the right stacks in the right places.
 *
 * Nothing is ever dropped: the drop chance of every slot is set to zero, so a trainer killed
 * in creative leaves no gear behind. Cosmetic means cosmetic.
 */
object TrainerOutfit {

    private val LOGGER = CobblemonTrainers.LOGGER

    /**
     * The slots, paired with the field of [TrainerCosmetics] that fills each. Iterating this is
     * what keeps [dress] and [validate] from ever disagreeing about which fields exist.
     */
    private val SLOTS: List<Pair<EquipmentSlot, (TrainerCosmetics) -> String?>> = listOf(
        EquipmentSlot.HEAD to TrainerCosmetics::head,
        EquipmentSlot.CHEST to TrainerCosmetics::chest,
        EquipmentSlot.LEGS to TrainerCosmetics::legs,
        EquipmentSlot.FEET to TrainerCosmetics::feet,
        EquipmentSlot.MAINHAND to TrainerCosmetics::mainHand,
        EquipmentSlot.OFFHAND to TrainerCosmetics::offHand
    )

    /** The same, for the trinkets. */
    private val TRINKETS: List<Pair<TrainerTrinketSlot, (TrainerTrinkets) -> String?>> = listOf(
        TrainerTrinketSlot.FACE to TrainerTrinkets::face,
        TrainerTrinketSlot.CHEST to TrainerTrinkets::chest,
        TrainerTrinketSlot.WRIST to TrainerTrinkets::wrist,
        TrainerTrinketSlot.FOREARM to TrainerTrinkets::forearm,
        TrainerTrinketSlot.HAND to TrainerTrinkets::hand,
        TrainerTrinketSlot.BELT to TrainerTrinkets::belt,
        TrainerTrinketSlot.ANKLE to TrainerTrinkets::ankle
    )

    /**
     * Puts a trainer's clothes on the entity that just spawned.
     *
     * An entry that resolves to nothing is skipped in silence: the pack was already told at
     * load, and a spawn happens far too often to say it again.
     *
     * Called at the spawn and nowhere else, so a trainer already standing in the world keeps
     * the outfit it was born with until something puts it there again - exactly like its name
     * and its skin, and for the same reason: what a `/reload` changes is the definition, not
     * the entities already made from it.
     *
     * The aspects are only added here; [TrainerSpawner] calls `updateAspects()` afterwards,
     * which is what sends them.
     */
    fun dress(npc: NPCEntity, cosmetics: TrainerCosmetics) {
        if (cosmetics.isEmpty) return

        for ((slot, field) in SLOTS) {
            val stack = resolve(field(cosmetics)) ?: continue
            npc.setItemSlot(slot, stack)
            // Mobs drop their gear on death with a chance of 8.5% per slot. A cosmetic is not
            // loot: an operator killing a trainer to move it must not hand out its hat.
            npc.setDropChance(slot, 0f)
        }

        for ((slot, field) in TRINKETS) {
            val id = itemId(field(cosmetics.trinkets)) ?: continue
            // Checked here rather than left to the client: an aspect naming an item nothing
            // provides would be dead weight in the entity's NBT for as long as it lives.
            if (!BuiltInRegistries.ITEM.containsKey(id)) continue
            npc.appliedAspects.add(trinketAspect(slot, id))
        }
    }

    /** The aspect a trainer carries for one trinket. */
    fun trinketAspect(slot: TrainerTrinketSlot, item: ResourceLocation): String =
        "${CobblemonTrainers.TRINKET_ASPECT_PREFIX}${slot.id}:$item"

    /**
     * Reads one back, as a slot and the item it names, or null when the aspect is not one of
     * ours or names an item nothing provides.
     *
     * This is the client's only way in: it has no trainer registry to ask, so what the entity
     * carries is all it knows.
     */
    fun readTrinketAspect(aspect: String): Pair<TrainerTrinketSlot, ItemStack>? {
        if (!aspect.startsWith(CobblemonTrainers.TRINKET_ASPECT_PREFIX)) return null

        val body = aspect.removePrefix(CobblemonTrainers.TRINKET_ASPECT_PREFIX)
        val slot = TrainerTrinketSlot.of(body.substringBefore(':')) ?: return null
        val stack = resolve(body.substringAfter(':', "")) ?: return null
        return slot to stack
    }

    /**
     * Logs the item IDs of a cosmetics block that resolve to nothing. Called once per trainer
     * at load, from [TrainerCosmetics.validate].
     */
    fun validate(id: ResourceLocation, cosmetics: TrainerCosmetics) {
        if (cosmetics.isEmpty) return

        for ((slot, field) in SLOTS) {
            complain(id, slot.getName(), field(cosmetics))
        }
        for ((slot, field) in TRINKETS) {
            complain(id, "trinkets.${slot.id}", field(cosmetics.trinkets))
        }
    }

    private fun complain(id: ResourceLocation, slot: String, declared: String?) {
        if (declared == null || resolve(declared) != null) return
        LOGGER.warn(
            "Trainer {}: cosmetics slot '{}' names '{}', which no item provides. The trainer " +
                "wears nothing there. Is the mod that provides it installed?",
            id, slot, declared
        )
    }

    /**
     * One item ID into one stack, or null when it names nothing.
     *
     * Always a stack of one: a count would mean nothing on a body, and vanilla draws a held
     * stack the same whatever its size.
     */
    private fun resolve(declared: String?): ItemStack? {
        val id = itemId(declared) ?: return null
        val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null) ?: return null
        return ItemStack(item)
    }

    /** The declared ID, parsed, or null when it is blank or malformed. */
    private fun itemId(declared: String?): ResourceLocation? {
        val name = declared?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ResourceLocation.tryParse(name)
    }
}
