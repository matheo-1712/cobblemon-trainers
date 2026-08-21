package matheo1712.cobbletrainers.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import matheo1712.cobbletrainers.battle.ai.TrainerBattleAI;
import matheo1712.cobbletrainers.registry.TrainerRegistry;
import matheo1712.cobbletrainers.trainers.TrainerDefinition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wraps the battle AI of this mod's trainers in {@link TrainerBattleAI}.
 * <p>
 * {@code NPCBattleActor(npc, pokemon, skill, battleAI)} does take the AI as a parameter, with
 * {@code StrongBattleAI(skill)} as its default - but {@link
 * com.cobblemon.mod.common.battles.BattleBuilder#pvn} never exposes it, and rebuilding the battle
 * ourselves would mean reimplementing every check and error message {@code pvn} forwards to the
 * player. Replacing the field afterwards is the short way round.
 * <p>
 * It is done on the first choice request rather than in a constructor because the field lives on
 * {@link AIBattleActor} while the trainer identity lives on the {@link NPCBattleActor} below it:
 * at {@code AIBattleActor.<init>} the subclass has not assigned its {@code npc} yet. By the first
 * request the actor is fully built. The only thing that can reach the raw AI before that is
 * {@code onHealthChange}, which {@link TrainerBattleAI} forwards untouched anyway.
 * <p>
 * Only NPCs carrying a {@code trainer_id:} aspect are touched, so Cobblemon's own NPCs and those
 * of other mods keep the AI they were built with - and a data pack that uses this mod as its API
 * gets the correction for free, since its trainers carry the aspect like any other.
 */
@Mixin(AIBattleActor.class)
public abstract class AIBattleActorMixin {

    @Mutable
    @Shadow
    @Final
    private BattleAI battleAI;

    @Unique
    private boolean cobblemontrainers$aiChecked;

    @Inject(method = "onChoiceRequested", at = @At("HEAD"))
    private void cobblemontrainers$installTrainerAI(CallbackInfo ci) {
        if (this.cobblemontrainers$aiChecked) {
            return;
        }
        this.cobblemontrainers$aiChecked = true;

        if (!((Object) this instanceof NPCBattleActor actor)) {
            return;
        }

        TrainerDefinition definition = TrainerRegistry.INSTANCE.findByAspects(actor.getNpc().getAspects());
        if (definition == null) {
            return;
        }

        this.battleAI = new TrainerBattleAI(this.battleAI, definition.getBattle().getDifficulty());
    }
}
