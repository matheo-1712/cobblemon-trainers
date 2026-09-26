# Development structure

The mod has three modules: shared logic, Fabric and NeoForge.

| Location | Role |
| --- | --- |
| `common/src/main/kotlin/` | Minecraft/Cobblemon logic: trainers, parsing, battles, AI, progression, commands, payloads and network validation, screens, caches, music and rendering. |
| `common/src/main/java/` | Minecraft and Cobblemon mixins. |
| `common/src/main/resources/` | Assets, languages, data and mixin configuration. |
| `fabric/src/main/` | Fabric entrypoints, platform adapters, services and `fabric.mod.json`. |
| `fabric/run/` | Fabric development environment, worlds and test mods. |
| `fabric/build/libs/` | Distributable Fabric jars, with the shared content included. |
| `neoforge/src/main/` | NeoForge entrypoints, adapters, services and `neoforge.mods.toml`. |
| `neoforge/build/libs/` | Distributable NeoForge jar, compiled from shared sources. |
| `examples/`, `web/`, `docs/` | Shared example packs, editor and documentation. |

Logic packages, identifiers, saved data and JSON formats stay the same. `common` is not
an additional mod to install. The existing development directory was moved from `run/`
to `fabric/run/` with its contents.

## Building and running

From the root, with Java 21 (`.\gradlew.bat` on Windows):

```sh
./gradlew clean build       # Rebuilds common, Fabric and NeoForge
./gradlew :common:build     # Compiles and checks shared code
./gradlew :fabric:build     # Builds the Fabric jar including common
./gradlew :neoforge:build   # Builds the NeoForge jar from common
./gradlew :neoforge:runClient
./gradlew :neoforge:runServer
./gradlew runClient         # Alias for :fabric:runClient
./gradlew runServer         # Alias for :fabric:runServer
./gradlew genSources        # Game sources for Fabric
./gradlew exampleDatapack   # Pack in the root build/dist/ directory
```

`publishMods` still works from the root and targets Fabric. Without a token, simulated
publication files are written to `fabric/build/mod-publish/`. Build, release and editor
workflows use the new paths.

In IntelliJ, reloading Gradle generates `Minecraft Client (Fabric)` and
`Minecraft Server (Fabric)`. `./gradlew :fabric:ideaSyncTask` also regenerates them.
These configurations run Gradle tasks, which prepare Java 21, development mods and
the example pack. No Architectury launcher is used.

A release builds the Fabric and NeoForge jars once, validates their contents, then
stages them with the example pack in `build/release/`. Modrinth, CurseForge and GitHub
receive these same files after SHA-256 verification. The property
`-Prelease_file=build/release/cobblemon-trainers-<version>.jar` lets `publishMods`
use that jar without rebuilding it. The CurseForge script accepts the same path through
`RELEASE_FILE`; `DRY_RUN=true` only writes its metadata to
`build/mod-publish/curseforge-<loader>.json` (with `VERSION`, `RELEASE_TYPE` and
`LOADER=neoforge` for NeoForge).

## Logic and platform boundary

`TrainerPlatform` defines loader operations: server events, commands, reloads, registries,
paths, mod detection and network transport. `TrainerClientPlatform` isolates client events
and transport. Fabric implementations are discovered through `ServiceLoader`, without a
reference from `common` to a Fabric class or initializing client classes on a dedicated
server. NeoForge supplies its own implementations of these services. Network receivers run on the game thread. Rules and validation stay in their
shared classes.

The common build uses Loom to compile against Minecraft and Cobblemon with Mojang mappings.
It currently uses the Fabric Cobblemon jar as a compile dependency, without depending on
Fabric API or importing its classes. `verifyLoaderIndependence`, run by `check`, rejects
loader imports in shared sources.

Fabric consumes the unremapped common jar (`namedElements`), merges it before its own
remapping and groups both source sets when running the game. The sources jar also includes
shared sources. The common module defines no game launches.

## NeoForge version

NeoForge compiles `common` sources against its own Minecraft and Cobblemon 1.8.1.
Its loader mod ID is `cobblemon_trainers`, since NeoForge forbids hyphens in mod IDs.
Resource and data identifiers remain `cobblemon-trainers`. The module requires
NeoForge 21.1.251 and Kotlin for Forge 5.12.0. Its jar is separate from Fabric's.
