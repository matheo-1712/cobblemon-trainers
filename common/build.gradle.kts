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
    compileOnly("net.fabricmc:sponge-mixin:${providers.gradleProperty("mixin_version").get()}")
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
        val forbidden = Regex("(?m)^\\s*(?:import|package)\\s+(?:net\\.fabricmc|net\\.neoforged|matheo1712\\.cobbletrainers\\.fabric)\\b")
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
