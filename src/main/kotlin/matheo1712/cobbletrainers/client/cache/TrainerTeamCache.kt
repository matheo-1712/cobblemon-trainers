package matheo1712.cobbletrainers.client.cache

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import matheo1712.cobbletrainers.network.RequestTrainerTeamPayload
import matheo1712.cobbletrainers.network.TrainerTeamPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.resources.ResourceLocation

/**
 * The teams the battle phone has been sent, ready to draw.
 *
 * Asked for like the skins are, one trainer at a time, and only for a trainer the player has
 * beaten - the server refuses the others, so nothing is gained by asking early. [get] answers
 * null while the reply is on its way, and an empty list once it has landed with nothing in it:
 * the trainer is not beaten, has no team, or none of it could be built.
 *
 * A member is turned into a [RenderablePokemon] here, once, rather than on every frame. That
 * means resolving the species against the client registry, which is synced, so a species the
 * client does not know - a pack loaded on the server alone - simply drops out of the team.
 *
 * Everything here runs on the client thread, like [TrainerSkinCache].
 */
object TrainerTeamCache {

    /** @param nickname Empty when the Pokémon has none. */
    class Member(val pokemon: RenderablePokemon, val level: Int, val nickname: String)

    private val teams = mutableMapOf<String, List<Member>>()
    private val pending = mutableSetOf<String>()

    /** The team of that trainer, asking the server for it the first time around. */
    fun get(trainerId: String): List<Member>? {
        teams[trainerId]?.let { return it }

        if (pending.add(trainerId)) {
            ClientPlayNetworking.send(RequestTrainerTeamPayload(trainerId))
        }
        return null
    }

    fun accept(payload: TrainerTeamPayload) {
        pending.remove(payload.trainerId)
        teams[payload.trainerId] = payload.members.mapNotNull { member ->
            val species = ResourceLocation.tryParse(member.species)
                ?.let { PokemonSpecies.getByIdentifier(it) }
                ?: return@mapNotNull null

            Member(RenderablePokemon(species, member.aspects.toSet()), member.level, member.nickname)
        }
    }

    /**
     * Drops every team. Called when leaving a world, for the same reason the skins are: the
     * next world has its own trainers, and its own record of who beat them.
     */
    fun clear() {
        teams.clear()
        pending.clear()
    }
}
