plugins {
    base
    id("org.jetbrains.kotlin.jvm") apply false
    id("net.fabricmc.fabric-loom-remap") apply false
}

// The root coordinates all modules; loader-specific tasks stay in their modules.
tasks.named("assemble") { dependsOn(":common:assemble", ":fabric:assemble") }
tasks.named("check") { dependsOn(":common:check", ":fabric:check") }
tasks.named("build") { dependsOn(":common:build", ":fabric:build") }
tasks.named("clean") { dependsOn(":common:clean", ":fabric:clean") }

// Preserve the familiar root commands while selecting the Fabric runtime explicitly.
for (name in listOf("runClient", "runServer", "genSources", "publishMods")) {
    tasks.register(name) { dependsOn(":fabric:$name") }
}

/**
 * The example pack, zipped as a release asset.
 *
 * It stays out of the mod jar deliberately: everything under `data/` in the jar is a datapack
 * the game loads for every player, so bundling the examples would spawn `cobblemonrlm:` trainers
 * in worlds that never asked for them. It ships next to the jar instead, for whoever wants a
 * working pack to copy.
 *
 * `fabric.mod.json` is dropped on the way out. It only exists so the folder can be built into a
 * `.jar` that Fabric loads, and it actively hurts a `.zip`: Fabric ignores archives that are not
 * `.jar`, while [matheo1712.cobbletrainers.ModsFolderPackSource] skips anything carrying mod
 * metadata - the pack would load from nowhere. Without it, the same zip works in `mods/`,
 * `datapacks/` and `resourcepacks/`.
 */
val exampleDatapack = tasks.register<Zip>("exampleDatapack") {
	description = "Packs examples/cobblemonrlm as a release asset."
	group = "build"

	from("examples/cobblemonrlm") {
		exclude("fabric.mod.json")
	}
	archiveFileName = "exemple_trainer_datapack.zip"
	destinationDirectory = layout.buildDirectory.dir("dist")
}
