package matheo1712.cobbletrainers.mixin.client;

import matheo1712.cobbletrainers.ModsFolderPackSource;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Arrays;

/**
 * Feeds {@link ModsFolderPackSource#CLIENT_RESOURCES} to the client's resource pack repository,
 * so the {@code assets/} of a pack in {@code mods/} — its translations and its battle music —
 * are read alongside its {@code data/}.
 * <p>
 * The repository is built once in the {@link Minecraft} constructor from a fixed varargs array
 * and exposes no way to add a source afterwards, hence appending to that array. The client
 * repository is identified by construction here rather than by inspecting its sources, which
 * keeps {@code ClientPackSource} — a client-only class — out of the common mixin.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V"
            ),
            index = 0
    )
    private RepositorySource[] cobblemontrainers$addModsFolderResourcePacks(RepositorySource[] sources) {
        RepositorySource[] extended = Arrays.copyOf(sources, sources.length + 1);
        extended[sources.length] = ModsFolderPackSource.CLIENT_RESOURCES;
        return extended;
    }
}
