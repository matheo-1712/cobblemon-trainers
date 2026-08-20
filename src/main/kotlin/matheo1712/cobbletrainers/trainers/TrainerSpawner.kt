package matheo1712.cobbletrainers.trainers

import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.api.npc.configuration.interaction.NoneNPCInteractionConfiguration
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore
import com.cobblemon.mod.common.entity.npc.NPCEntity
import matheo1712.cobbletrainers.CobblemonTrainers
import matheo1712.cobbletrainers.parser.ShowdownTeamParser
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

/**
 * Creates and spawns [com.cobblemon.mod.common.entity.npc.NPCEntity] instances from a [TrainerDefinition].
 */
object TrainerSpawner {

    /**
     * The NPC classes shipped by the mod. Datapacks configure trainers, never NPC classes:
     * what used to be worth changing there now lives on [TrainerDefinition].
     *
     * Battle format and AI difficulty are applied per entity, so they need no variant. Party
     * healing is the exception: Cobblemon only reads `autoHealParty` from the class
     * (`NPCBattleActor` and `PokemonBattle` both go through `npc.npc.autoHealParty`), so the
     * mod ships one class per value of that boolean. Keep the two files identical apart from
     * `autoHealParty`.
     */
    val NPC_CLASS_HEALING: ResourceLocation = CobblemonTrainers.id("trainer")
    val NPC_CLASS_NO_HEALING: ResourceLocation = CobblemonTrainers.id("trainer_no_heal")

    private val LOGGER = CobblemonTrainers.LOGGER

    /**
     * Spawns a trainer in the world at the given position.
     *
     * @param yRot Which way the trainer faces, in degrees.
     * @param extraAspects Aspects applied on top of the trainer ID one. Used by the trainer
     *   spawner block to mark the trainers it owns; aspects are saved to NBT, so such a mark
     *   survives a restart.
     * @return the spawned [com.cobblemon.mod.common.entity.npc.NPCEntity], or null on failure.
     */
    fun spawn(
        server: MinecraftServer,
        level: ServerLevel,
        position: Vec3,
        definition: TrainerDefinition,
        trainerId: ResourceLocation,
        yRot: Float = 0f,
        extraAspects: Collection<String> = emptyList()
    ): NPCEntity? {
        // 1. Resolve the NPC class shipped by the mod. No fallback to an arbitrary class:
        //    failing loudly beats spawning an NPC that behaves unpredictably.
        val npcClassId = if (definition.autoHealParty) NPC_CLASS_HEALING else NPC_CLASS_NO_HEALING
        val npcClass = NPCClasses.getByIdentifier(npcClassId) ?: run {
            LOGGER.error(
                "NPC class {} is missing - is the mod's data pack loaded? Available classes: {}",
                npcClassId,
                NPCClasses.classes.joinToString(", ") { it.id.toString() }
            )
            return null
        }

        // 2. Create and position the entity
        val npc = NPCEntity(level)
        npc.npc = npcClass
        npc.moveTo(position.x, position.y, position.z, yRot, npc.xRot)
        // moveTo only sets the entity rotation; without these the trainer stands facing the
        // right way but with its body and head turned to whatever the defaults were.
        npc.yBodyRot = yRot
        npc.yHeadRot = yRot

        // 3. Display name. The npc.npc setter assigns a random one from the class, so this
        //    has to come after. Translatable so a resource pack may localise it.
        npc.customName = Component.translatable(definition.name)

        // 4. Tag the entity with its trainer ID. Applied aspects are serialized to NBT, which
        //    is how the definition is found again after a restart.
        npc.appliedAspects.add(CobblemonTrainers.TRAINER_ASPECT_PREFIX + trainerId)
        npc.appliedAspects.addAll(extraAspects)

        // 5. Cobblemon 1.7.3 no longer reads `canChallenge`: battles are triggered by the NPC
        //    interaction, so disable it at the entity level instead.
        if (!definition.canBattle) {
            npc.interaction = NoneNPCInteractionConfiguration()
        }

        // 6. Battle AI difficulty, overridden per entity so every trainer can differ while
        //    sharing one NPC class. Cobblemon clamps it to 0..5 anyway.
        npc.skill = definition.skill.coerceIn(0, 5)

        // 7. initialize() resets `party` to whatever the NPC class provides, so the trainer
        //    team has to be assigned afterwards.
        npc.initialize(definition.level)

        applyTeam(npc, definition, trainerId)
        npc.updateAspects()

        // 8. The skin arrives asynchronously, after the entity is spawned.
        applySkin(server, npc, definition.skin)

        if (!level.addFreshEntity(npc)) {
            LOGGER.error("Failed to spawn trainer {}", trainerId)
            return null
        }

        LOGGER.info(
            "Spawned trainer {} at {}, {}, {}",
            trainerId,
            position.x.toInt(),
            position.y.toInt(),
            position.z.toInt()
        )
        return npc
    }

    /**
     * Builds the team and assigns it to the entity.
     *
     * The team is deliberately set on `npc.party` (the entity store) rather than
     * `npc.npc.party`: the NPC class is a singleton shared by every NPC of that class, so
     * changing its team would replace it for all of them.
     *
     * The store is filled here instead of through `SimplePartyProvider`: its `provide()`
     * starts by calling `PokemonProperties.copy()`, which round-trips through
     * `saveToJSON`/`loadFromJSON` - and that pair, unlike `saveToNBT`, does not carry
     * `nickname`, so a Showdown nickname was silently lost on the way to the party. What
     * `provide()` does beyond that copy is the level default and the store itself, both
     * reproduced below.
     */
    private fun applyTeam(npc: NPCEntity, definition: TrainerDefinition, trainerId: ResourceLocation) {
        if (definition.team.isEmpty()) return

        val pokemonProps = ShowdownTeamParser.parse(definition.team)
        if (pokemonProps.isEmpty()) {
            LOGGER.warn("Trainer {} has no valid Pokémon in its team", trainerId)
            return
        }

        val party = NPCPartyStore(npc)
        for (properties in pokemonProps) {
            // A `Level:` line on the Pokémon wins over the trainer-wide level.
            if (properties.level == null) {
                properties.level = definition.level
            }
            // create() is where a property the parser accepted meets Cobblemon's registries -
            // an unknown species or held item throws here. One bad entry costs its Pokémon, not
            // the trainer, and not the tick that asked for the spawn.
            try {
                party.add(properties.create())
            } catch (e: Exception) {
                LOGGER.warn("Skipping a Pokémon of trainer {}: {} ({})", trainerId, e.message, properties.asString(" "))
            }
        }
        npc.party = party
    }

    /**
     * Applies a skin to the NPC.
     *
     * Everything that can block - profile lookup, download, pack read - is [TrainerSkins]'
     * business; only the application to the entity comes back through the server thread. On
     * failure the NPC keeps the default skin of its class.
     */
    private fun applySkin(server: MinecraftServer, npc: NPCEntity, skin: TrainerSkin) {
        TrainerSkins.resolveAsync(server, skin) { texture ->
            if (texture == null) return@resolveAsync

            server.execute {
                if (!npc.isAlive) return@execute
                // Mirrors NPCEntity.loadTexture() without its network I/O: the model-*
                // aspects decide which rig is used when rendering.
                npc.appliedAspects -= "model-default"
                npc.appliedAspects -= "model-slim"
                npc.appliedAspects += "model-${texture.model.name.lowercase()}"
                npc.entityData.set(NPCEntity.NPC_PLAYER_TEXTURE, texture)
                npc.updateAspects()
            }
        }
    }
}
