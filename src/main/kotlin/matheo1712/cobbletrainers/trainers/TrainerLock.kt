package matheo1712.cobbletrainers.trainers

import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item

/**
 * Decides whether a player may challenge a trainer, and says what is missing when they may not.
 *
 * A [TrainerRequirements] block is answered as a list of what is *not* met yet: empty means the
 * trainer is open, and anything else is both the reason the battle is refused and the hint the
 * battle phone shows. Building it once as a list of components rather than as a sentence is
 * what lets the same answer travel to the screen - a translatable component is resolved on the
 * client, so a player reads it in their own language either way.
 *
 * Requirements add up: every one of them has to be met.
 *
 * Nothing is ever taken from the player. An item requirement is a key they keep, which is what
 * keeps a rematch possible without farming it again.
 */
object TrainerLock {

    /**
     * What the player is still missing before [trainerId] accepts a battle, in the order the
     * requirements are declared. An empty list is an open trainer.
     */
    fun unmet(
        player: ServerPlayer,
        trainerId: ResourceLocation,
        definition: TrainerDefinition
    ): List<Component> {
        val requirements = definition.requirements() ?: return emptyList()
        val progress = TrainerProgress.of(player.server)
        val missing = mutableListOf<Component>()

        requirements.defeated.forEach { raw ->
            val required = resolve(raw, trainerId) ?: run {
                CobblemonTrainers.LOGGER.warn(
                    "Trainer {} requires an unreadable trainer ID '{}'", trainerId, raw
                )
                return@forEach
            }
            if (progress.hasDefeated(required, player.uuid)) return@forEach

            val name = TrainerRegistry.get(required)?.name?.let { Component.translatable(it) }
                ?: Component.literal(required.toString())
            missing += CobblemonTrainers.lang("requirement.defeated", name)
        }

        requirements.victories?.let { victories ->
            val category = victories.category?.let { resolve(it, trainerId) }
            // A champion may ask for every champion: they never count towards themselves.
            val pool = TrainerRegistry.listedIds(victories.pack, category) - trainerId
            val required = if (victories.count > 0) victories.count else pool.size
            val won = pool.count { progress.hasDefeated(it, player.uuid) }

            if (won < required) {
                missing += if (category != null) {
                    CobblemonTrainers.lang(
                        "requirement.victories_category",
                        Component.translatable(TrainerRegistry.categoryName(category)),
                        won, required
                    )
                } else {
                    CobblemonTrainers.lang("requirement.victories", won, required)
                }
            }
        }

        requirements.items.forEach { requirement ->
            val itemId = ResourceLocation.tryParse(requirement.item)
            val item = itemId?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }
            if (item == null) {
                // Counting it as met would open the trainer to everyone, which is the wrong way
                // to fail: an item that does not exist is a typo in the pack.
                CobblemonTrainers.LOGGER.warn(
                    "Trainer {} requires an unknown item '{}'", trainerId, requirement.item
                )
                missing += CobblemonTrainers.lang(
                    "requirement.item",
                    Component.literal(requirement.item),
                    0, requirement.count.coerceAtLeast(1)
                )
                return@forEach
            }

            val needed = requirement.count.coerceAtLeast(1)
            val held = countInInventory(player, item)
            if (held < needed) {
                missing += CobblemonTrainers.lang(
                    "requirement.item",
                    Component.translatable(item.descriptionId),
                    held, needed
                )
            }
        }

        requirements.advancement?.takeIf { it.isNotBlank() }?.let { raw ->
            val id = ResourceLocation.tryParse(raw)
            val holder = id?.let { player.server.advancements.get(it) }
            if (holder == null) {
                CobblemonTrainers.LOGGER.warn(
                    "Trainer {} requires an unknown advancement '{}'", trainerId, raw
                )
                missing += CobblemonTrainers.lang("requirement.advancement", Component.literal(raw))
                return@let
            }
            if (player.advancements.getOrStartProgress(holder).isDone) return@let

            val title = holder.value().display.map { it.title }.orElse(Component.literal(raw))
            missing += CobblemonTrainers.lang("requirement.advancement", title)
        }

        return missing
    }

    /** Whether the trainer refuses to battle this player for now. */
    fun isLocked(player: ServerPlayer, trainerId: ResourceLocation, definition: TrainerDefinition): Boolean =
        unmet(player, trainerId, definition).isNotEmpty()

    /**
     * Whether the trainer should be left out of the battle phone for this player: a locked
     * trainer that declares itself hidden is not something the player is supposed to know
     * about yet.
     */
    fun isHiddenFrom(player: ServerPlayer, trainerId: ResourceLocation, definition: TrainerDefinition): Boolean {
        val requirements = definition.requirements() ?: return false
        return requirements.hidden && isLocked(player, trainerId, definition)
    }

    /**
     * What the trainer says to a player it turns down: the pack's own words if it wrote any,
     * otherwise a line of the mod's, followed either way by what is missing.
     */
    fun refusal(definition: TrainerDefinition, missing: List<Component>): Component {
        val requirements = definition.requirements()
        val header = requirements?.message?.takeIf { it.isNotBlank() }
            ?.let { Component.translatable(it) }
            ?: CobblemonTrainers.lang("chat.locked", Component.translatable(definition.name))

        val message: MutableComponent = header.copy().withStyle(ChatFormatting.GRAY)
        missing.forEach { line ->
            message.append(Component.literal("\n"))
            message.append(CobblemonTrainers.lang("requirement.line", line).withStyle(ChatFormatting.DARK_GRAY))
        }
        return message
    }

    /**
     * Reads an ID written in a pack, where the namespace may be left out to mean "mine": a
     * trainer of `mon_pack` asking for `champion` means `mon_pack:champion`.
     */
    private fun resolve(raw: String, requiredBy: ResourceLocation): ResourceLocation? =
        if (raw.contains(':')) ResourceLocation.tryParse(raw)
        else ResourceLocation.tryBuild(requiredBy.namespace, raw)

    /** How many of an item the player is carrying, across every slot and every stack. */
    private fun countInInventory(player: ServerPlayer, item: Item): Int {
        var total = 0
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (stack.item == item) total += stack.count
        }
        return total
    }
}
