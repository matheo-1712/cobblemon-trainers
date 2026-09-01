import me.modmuss50.mpp.ReleaseType
import net.fabricmc.loom.task.RemapJarTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom-remap")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.4.10"
	id("me.modmuss50.mod-publish-plugin") version "2.2.0"
}

repositories {
	// Modrinth Maven pour Cobblemon
	maven {
		name = "Modrinth"
		url = uri("https://api.modrinth.com/maven")
		content {
			includeGroup("maven.modrinth")
		}
	}
}

/**
 * Mods the dev game runs with, and nothing else: they are never compiled against, never
 * published as a dependency, and never part of the jar.
 *
 * Trainers mega evolve through Cobblemon's own gimmick API, so not a line of this mod mentions
 * Mega Showdown - it is only what puts the Mega Stones in the game, and therefore the only way
 * to test `battle.gimmicks` at all. `accessories` comes along because Mega Showdown hard-depends
 * on it, and `owo` because accessories does; the loader refuses to start without either.
 *
 * They are **copied into `run/mods`** rather than declared `modRuntimeOnly`, because a mod jar
 * carries its libraries nested inside it (owo ships endec and jankson that way) and Loom does
 * not put those on the dev classpath - the game crashed on a missing `endec` class. Fabric
 * unpacks them like it would for a player, and remaps the mods on the way in.
 */
val devMods: Configuration by configurations.creating

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	mappings(loom.officialMojangMappings())
	modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

	// Cobblemon 1.7.3 pour Fabric 1.21.1 - API uniquement (les joueurs l'installeront séparément)
	modImplementation("maven.modrinth:cobblemon:${project.property("cobblemon_version")}")
    
    // Dependencies for Cobblemon
    modImplementation("maven.modrinth:architectury-api:${project.property("architectury_version")}")

    // Mega Showdown and what it needs, dropped into `run/mods` for the dev game. See the
    // `devMods` configuration above.
    devMods("maven.modrinth:cobblemon-mega-showdown:${project.property("mega_showdown_version")}")
    devMods("maven.modrinth:accessories:${project.property("accessories_version")}")
    devMods("maven.modrinth:owo-lib:${project.property("owo_version")}")
}

val copyDevMods = tasks.register<Copy>("copyDevMods") {
	description = "Copies the dev-only mods into run/mods."
	group = "fabric"

	from(devMods)
	into(layout.projectDirectory.dir("run/mods"))
}

tasks.named("runClient") { dependsOn(copyDevMods) }
tasks.named("runServer") { dependsOn(copyDevMods) }

tasks.processResources {
	val version = version
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 21
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_21
	}
}

java {
	// Cobblemon déclare `depends: java [21]`, une version exacte. Sans toolchain, runClient
	// et runServer héritent du JDK qui fait tourner Gradle et le loader refuse de démarrer.
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}

	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}
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

/**
 * Release type, overridable from the command line so the release workflow drives the build
 * without editing a tracked file: `./gradlew publishMods -Prelease_type=beta`.
 */
val releaseType = when (providers.gradleProperty("release_type").getOrElse("stable").lowercase()) {
	"alpha" -> ReleaseType.ALPHA
	"beta" -> ReleaseType.BETA
	else -> ReleaseType.STABLE
}

// Captured out here on purpose: inside `publishMods`, `version` is the extension's own
// property, and interpolating it yields its Gradle description rather than the number.
val modVersion = project.version.toString()

publishMods {
	// `remapJar`, not `jar`: the latter still carries named mappings and would crash outside a
	// development environment.
	file = tasks.named<RemapJarTask>("remapJar").flatMap { it.archiveFile }
	displayName = "Cobblemon Trainers $modVersion"
	version = modVersion
	type = releaseType
	modLoaders.add("fabric")

	// The release workflow passes the GitHub release body here. Without it - a local run - the
	// changelog is a pointer rather than a lie.
	changelog = providers.environmentVariable("CHANGELOG")
		.orElse("See https://github.com/matheo-1712/cobblemon-trainers/releases/tag/v$modVersion")

	// A missing token turns the whole thing into a rehearsal: the release assets are written to
	// build/mod-publish instead of being uploaded. That is what makes `./gradlew publishMods`
	// safe to run locally, and it is the same code path the workflow takes.
	dryRun = providers.environmentVariable("MODRINTH_TOKEN").orNull == null

	modrinth {
		// The placeholder only ever reaches a dry run: a real publication needs the token, and
		// its absence is exactly what turns dryRun on above.
		accessToken = providers.environmentVariable("MODRINTH_TOKEN").orElse("dry-run")
		projectId = providers.gradleProperty("modrinth_id")
		minecraftVersions.add(providers.gradleProperty("minecraft_version").get())

		// Modrinth requires accurate environment metadata (Content Rules 5.1), and since plugin
		// 2.1.0 it is set here rather than in the version settings on the site.
		//
		// Both sides need the mod: the trainers themselves are server-side, but the Battle Phone
		// and the spawner screen are not optional extras, and Cobblemon is a both-sides mod to
		// begin with. The `canSend` guards are a courtesy message for a client that is missing
		// it, not a supported setup.
		environment = CLIENT_AND_SERVER

		// Pinned to the exact versions the mod is built against, straight from
		// gradle.properties: bumping a dependency there moves the published requirement with it,
		// and the two can never drift apart.
		//
		// Modrinth matches either a version ID or a version number, and refuses the publish
		// unless exactly one version matches - a typo here fails loudly rather than shipping a
		// requirement nobody can satisfy. `cobblemon_version` is already a version ID.
		requires {
			slug = "cobblemon"
			version = providers.gradleProperty("cobblemon_version")
		}
		requires {
			slug = "fabric-api"
			version = providers.gradleProperty("fabric_api_version")
		}
		requires {
			slug = "fabric-language-kotlin"
			version = providers.gradleProperty("fabric_kotlin_version")
		}
	}
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
