import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    id("net.fabricmc.fabric-loom-remap")
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

// Loom supplies the Mojang-mapped Minecraft/Cobblemon compile classpath. Common is
// not a runnable mod and never calls Fabric API; loader bindings live in :fabric.
dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    mappings(loom.officialMojangMappings())
    modCompileOnly("maven.modrinth:cobblemon:${providers.gradleProperty("cobblemon_version").get()}")
    // Never called from here - `verifyLoaderIndependence` rejects every Fabric import in common.
    // It is on the classpath so the remapper can resolve Cobblemon's class hierarchy. Since 1.8.2
    // Cobblemon registers its resource reloaders through an anonymous class implementing Fabric
    // API's `IdentifiableResourceReloadListener`; without that interface, tiny-remapper cannot see
    // that the class overrides `PreparableReloadListener.reload`, and leaves the method under its
    // intermediary name. The dev client then dies in `Minecraft.<init>` on an `AbstractMethodError`.
    // Loom keeps one remapped copy per mod, so the poorest classpath that asks for the remap
    // decides what every project gets - which is why this belongs here and not only in :fabric.
    modCompileOnly("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    compileOnly("net.fabricmc:sponge-mixin:${providers.gradleProperty("mixin_version").get()}")
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:${providers.gradleProperty("junit_version").get()}")
}

// Tests exercise mapped game types without starting a loader or a world.
configurations.testImplementation { extendsFrom(configurations.compileOnly.get()) }

tasks.test {
    // Minecraft's logging configuration writes relative to the working directory.
    val runDirectory = layout.buildDirectory.dir("test-run")
    workingDir(runDirectory)
    doFirst { runDirectory.get().asFile.mkdirs() }
}

loom {
    runs.clear()
}

// Only loader modules are distributed. They consume and remap the named common jar.
tasks.named("remapJar") { enabled = false }
tasks.named("remapSourcesJar") { enabled = false }

val verifyLoaderIndependence = tasks.register("verifyLoaderIndependence") {
    group = "verification"
    description = "Rejects loader-specific imports in shared sources."
    val sources = fileTree("src/main") { include("**/*.kt", "**/*.java") }
    inputs.files(sources)
    doLast {
        val forbidden = Regex("(?m)^\\s*(?:import|package)\\s+(?:net\\.fabricmc|net\\.neoforged|matheo1712\\.cobbletrainers\\.(?:fabric|neoforge))\\b")
        val violations = sources.files.filter { forbidden.containsMatchIn(it.readText()) }
        check(violations.isEmpty()) { "Loader-specific sources in common: ${violations.joinToString()}" }
    }
}
tasks.named("check") { dependsOn(verifyLoaderIndependence) }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}
