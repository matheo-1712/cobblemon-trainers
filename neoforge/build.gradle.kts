import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import me.modmuss50.mpp.ReleaseType

plugins {
    id("net.neoforged.moddev")
    id("org.jetbrains.kotlin.jvm")
    id("me.modmuss50.mod-publish-plugin")
}

group = rootProject.group
version = rootProject.version
base { archivesName.set("${rootProject.name}-neoforge") }

repositories {
    mavenCentral()
    maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
}

// Compile the same sources against NeoForge's patched Minecraft and Cobblemon API.
// No Fabric classes or remapped Fabric artifacts enter this module.
kotlin.sourceSets.main { kotlin.srcDir(rootProject.file("common/src/main/kotlin")) }
sourceSets.main {
    java.srcDir(rootProject.file("common/src/main/java"))
    resources.srcDir(rootProject.file("common/src/main/resources"))
}

neoForge {
    version = providers.gradleProperty("neoforge_version").get()
    runs {
        create("client") { client() }
        create("server") { server(); programArgument("--nogui") }
    }
    mods { create("cobblemon_trainers") { sourceSet(sourceSets.main.get()) } }
}

dependencies {
    implementation("maven.modrinth:cobblemon:${providers.gradleProperty("cobblemon_neoforge_version").get()}")
    implementation("maven.modrinth:kotlin-for-forge:${providers.gradleProperty("kotlin_neoforge_version").get()}")
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21); withSourcesJar() }
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }
tasks.withType<JavaCompile>().configureEach { options.release = 21 }
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") { expand("version" to project.version) }
}
tasks.jar { from(rootProject.file("LICENSE")) { rename { "${it}_${rootProject.name}" } } }

val copyExamplePack = tasks.register<Sync>("copyExamplePack") {
    from(rootProject.file("examples/cobblemonrlm")) { exclude("fabric.mod.json") }
    into(layout.projectDirectory.dir("run/mods/cobblemonrlm"))
}
tasks.matching { it.name == "runClient" || it.name == "runServer" }.configureEach {
    dependsOn(copyExamplePack)
}

val modVersion = project.version.toString()
publishMods {
    file = providers.gradleProperty("release_file")
        .map { rootProject.layout.projectDirectory.file(it) }
        .orElse(tasks.jar.flatMap { it.archiveFile })
    displayName = "Cobblemon Trainers $modVersion (NeoForge)"
    version = "$modVersion-neoforge"
    type = when (providers.gradleProperty("release_type").getOrElse("stable").lowercase()) {
        "alpha" -> ReleaseType.ALPHA
        "beta" -> ReleaseType.BETA
        else -> ReleaseType.STABLE
    }
    modLoaders.add("neoforge")
    changelog = providers.environmentVariable("CHANGELOG")
        .orElse("See https://github.com/matheo-1712/cobblemon-trainers/releases/tag/v$modVersion")
    dryRun = providers.environmentVariable("MODRINTH_TOKEN").orNull == null
    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN").orElse("dry-run")
        projectId = providers.gradleProperty("modrinth_id")
        minecraftVersions.add(providers.gradleProperty("minecraft_version").get())
        environment = CLIENT_AND_SERVER
        requires { slug = "cobblemon"; version = providers.gradleProperty("cobblemon_neoforge_version") }
        requires { slug = "kotlin-for-forge"; version = providers.gradleProperty("kotlin_neoforge_version") }
    }
}
