package matheo1712.cobbletrainers.mixin;

import matheo1712.cobbletrainers.datapack_handler.ModsFolderPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Feeds {@link ModsFolderPackSource#SERVER_DATA} to every data pack repository.
 * <p>
 * There is no API to add a source after construction, and the repository is built at several
 * call sites - the dedicated server, the integrated server, the world creation screen. Injecting
 * here covers all of them at once, so a pack in {@code mods/} shows up in {@code /datapack list}
 * and in the world creation screen just like one from {@code datapacks/}.
 * <p>
 * {@link ServerPacksSource} in the source list is what identifies a data pack repository; the
 * client's resource repository carries a {@code ClientPackSource} instead, and is handled by the
 * client-only mixin. Both derive separately from {@code BuiltInPackSource}, so neither matches
 * the other.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

    @Mutable
    @Shadow
    @Final
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cobblemontrainers$addModsFolderDataPacks(RepositorySource[] providedSources, CallbackInfo ci) {
        boolean isDataRepository = false;
        for (RepositorySource source : providedSources) {
            if (source instanceof ServerPacksSource) {
                isDataRepository = true;
                break;
            }
        }
        if (!isDataRepository) {
            return;
        }

        Set<RepositorySource> extended = new LinkedHashSet<>(this.sources);
        extended.add(ModsFolderPackSource.SERVER_DATA);
        this.sources = extended;
    }
}
