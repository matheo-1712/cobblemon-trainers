package matheo1712.cobbletrainers.mixin;

import net.minecraft.server.packs.repository.PackDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets Minecraft discover {@code .jar} archives as packs, next to the {@code .zip} it already
 * accepts.
 *
 * A pack that ships trainers <em>and</em> their translations or music is both a data pack and a
 * resource pack, which is exactly what a mod jar is - so authors naturally build one. Vanilla
 * rejects it on the file name alone: {@link PackDetector#detectPackResources} is the single gate,
 * and it only lets {@code .zip} through. Everything downstream already works, a jar being a zip:
 * {@code FilePackResources} opens it with {@link java.util.zip.ZipFile} without caring about the
 * extension.
 *
 * {@link PackDetector} is shared by every folder-backed source, so this covers the world
 * {@code datapacks/} folder and the {@code resourcepacks/} folder alike - a single jar can be
 * dropped in both.
 */
@Mixin(PackDetector.class)
public abstract class PackDetectorMixin {

    private static final String JAR_SUFFIX = ".jar";

    /**
     * The redirected call is the {@code .zip} test in {@code detectPackResources}, the only
     * {@code String.endsWith} in the method.
     */
    @Redirect(
            method = "detectPackResources",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;endsWith(Ljava/lang/String;)Z")
    )
    private boolean cobblemontrainers$acceptJarArchives(String fileName, String suffix) {
        return fileName.endsWith(suffix) || fileName.endsWith(JAR_SUFFIX);
    }
}
