package matheo1712.cobbletrainers.datapack_handler

import matheo1712.cobbletrainers.CobblemonTrainers
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
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
 * `pack.mcmeta` is picked up as-is, under both [net.minecraft.server.packs.PackType].
 *
 * Archives that *do* carry a `fabric.mod.json` are skipped — Fabric loads those itself and
 * already exposes them under both pack types, so picking them up here would register them
 * twice.
 */
class ModsFolderPackSource private constructor(private val packType: PackType) : RepositorySource {

    private val kind = if (packType == PackType.SERVER_DATA) "data" else "resource"

    /**
     * Data packs ride on [net.minecraft.server.packs.repository.PackSource.shouldAddAutomatically], which is what
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
        val info = locationInfo(path)

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

        /** Distinguishes these from a `file/<name>` pack of the `datapacks/` folder. */
        private const val ID_PREFIX = "mods/"

        /**
         * Reads one file out of the packs of `mods/`, outside of any pack repository.
         *
         * A trainer texture has to be read by the logical server — see [matheo1712.cobbletrainers.trainers.TrainerTextures] — and
         * no server-side resource manager ever looks at `assets/`. The scan is the same one
         * [loadPacks] performs, minus the `fabric.mod.json` filter: an archive Fabric did load
         * is already on the classpath, where the caller looks first, so reading it here would
         * only ever repeat that answer.
         *
         * @return the file content, or null when no pack of the folder holds it.
         */
        fun readResource(packType: PackType, location: ResourceLocation): ByteArray? {
            val folder = MODS_FOLDER
            if (!Files.isDirectory(folder)) return null

            var found: ByteArray? = null
            try {
                FolderRepositorySource.discoverPacks(folder, VALIDATOR) { path, supplier ->
                    if (found == null) {
                        found = readResource(supplier, locationInfo(path), packType, location)
                    }
                }
            } catch (e: Exception) {
                CobblemonTrainers.LOGGER.warn("Failed to scan {} for {}: {}", folder, location, e.message)
            }
            return found
        }

        private fun readResource(
            supplier: Pack.ResourcesSupplier,
            info: PackLocationInfo,
            packType: PackType,
            location: ResourceLocation
        ): ByteArray? =
            try {
                supplier.openPrimary(info).use { resources ->
                    resources.getResource(packType, location)?.get()?.use { it.readBytes() }
                }
            } catch (e: Exception) {
                CobblemonTrainers.LOGGER.warn("Could not read {} from {}: {}", location, info.id(), e.message)
                null
            }

        private fun locationInfo(path: Path): PackLocationInfo {
            val name = path.fileName.toString()
            return PackLocationInfo(
                ID_PREFIX + name,
                Component.literal(name),
                PackSource.BUILT_IN,
                Optional.empty()
            )
        }

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