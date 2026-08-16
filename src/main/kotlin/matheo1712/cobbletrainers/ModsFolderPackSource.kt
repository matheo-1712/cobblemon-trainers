package matheo1712.cobbletrainers

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.FolderRepositorySource
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.repository.RepositorySource
import net.minecraft.world.level.storage.LevelStorageSource
import net.minecraft.world.level.validation.DirectoryValidator
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.function.Consumer

/**
 * Loads packs dropped in `mods/` that are not Fabric mods.
 *
 * A pack that ships trainers together with their translations and their battle music is one
 * file that has to reach both the server data manager and the client resource manager. `mods/`
 * is the only folder that feeds both — but Fabric only looks at jars carrying a
 * `fabric.mod.json`, and skips the others in complete silence. So an author had to either wrap
 * their pack in a fake mod or split it in two, dropping the same archive in `datapacks/` and
 * `resourcepacks/`.
 *
 * This source removes that requirement: a directory, `.zip` or `.jar` in `mods/` with a
 * `pack.mcmeta` is picked up as-is, under both [PackType].
 *
 * Archives that *do* carry a `fabric.mod.json` are skipped — Fabric loads those itself and
 * already exposes them under both pack types, so picking them up here would register them
 * twice.
 */
class ModsFolderPackSource private constructor(private val packType: PackType) : RepositorySource {

    /** Distinguishes these from a `file/<name>` pack of the `datapacks/` folder. */
    private val idPrefix = "mods/"

    private val kind = if (packType == PackType.SERVER_DATA) "data" else "resource"

    /**
     * Data packs ride on [PackSource.shouldAddAutomatically], which is what
     * `MinecraftServer.configurePackRepository` reads to enable a freshly discovered pack, and
     * leaves `/datapack disable` usable.
     *
     * Resource packs cannot: `Minecraft.reloadResourcePacks` goes through
     * `PackRepository.reload()`, whose `rebuildSelected` only re-inserts packs marked
     * *required*. Anything else would be discovered and then dropped from the selection on the
     * first F3+T.
     */
    private val selection = PackSelectionConfig(
        packType == PackType.CLIENT_RESOURCES,
        Pack.Position.TOP,
        false
    )

    override fun loadPacks(consumer: Consumer<Pack>) {
        val folder = MODS_FOLDER
        if (!Files.isDirectory(folder)) return

        try {
            FolderRepositorySource.discoverPacks(folder, VALIDATOR) { path, supplier ->
                createPack(path, supplier)?.let(consumer::accept)
            }
        } catch (e: Exception) {
            CobblemonTrainers.LOGGER.warn("Failed to scan {} for packs: {}", folder, e.message)
        }
    }

    private fun createPack(path: Path, supplier: Pack.ResourcesSupplier): Pack? {
        val name = path.fileName.toString()
        val info = PackLocationInfo(
            idPrefix + name,
            Component.literal(name),
            PackSource.BUILT_IN,
            Optional.empty()
        )

        if (!isOurs(supplier, info)) return null

        val pack = Pack.readMetaAndCreate(info, supplier, packType, selection)
        if (pack != null) {
            CobblemonTrainers.LOGGER.info("Reading mods/{} as a {} pack", name, kind)
        }
        return pack
    }

    /**
     * A candidate is ours when Fabric ignored it (no `fabric.mod.json`) and it actually holds
     * something for this pack type — so a data-only pack never shows up in the resource pack
     * screen, and vice versa.
     */
    private fun isOurs(supplier: Pack.ResourcesSupplier, info: PackLocationInfo): Boolean =
        try {
            supplier.openPrimary(info).use { resources ->
                resources.getRootResource("fabric.mod.json") == null &&
                    resources.getNamespaces(packType).isNotEmpty()
            }
        } catch (e: Exception) {
            CobblemonTrainers.LOGGER.warn("Could not open {} as a pack: {}", info.id(), e.message)
            false
        }

    companion object {
        @JvmField
        val SERVER_DATA: ModsFolderPackSource = ModsFolderPackSource(PackType.SERVER_DATA)

        @JvmField
        val CLIENT_RESOURCES: ModsFolderPackSource = ModsFolderPackSource(PackType.CLIENT_RESOURCES)

        /** Honours `-Dfabric.modsFolder`, the way the loader itself locates the folder. */
        private val MODS_FOLDER: Path by lazy {
            System.getProperty("fabric.modsFolder")
                ?.let(Path::of)
                ?: FabricLoader.getInstance().gameDir.resolve("mods")
        }

        /** The same symlink rules vanilla applies to `datapacks/` and `resourcepacks/`. */
        private val VALIDATOR: DirectoryValidator by lazy {
            LevelStorageSource.parseValidator(
                FabricLoader.getInstance().gameDir.resolve("allowed_symlinks.txt")
            )
        }
    }
}
