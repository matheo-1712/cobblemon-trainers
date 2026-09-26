# Structure de développement

Le mod est organisé en trois modules : logique commune, Fabric et NeoForge.

| Emplacement | Rôle |
| --- | --- |
| `common/src/main/kotlin/` | Logique Minecraft/Cobblemon : dresseurs, parsing, combats, IA, progression, commandes, payloads et validation réseau, écrans, caches, musique et rendu. |
| `common/src/main/java/` | Mixins Minecraft et Cobblemon. |
| `common/src/main/resources/` | Ressources, langues, données et configuration des mixins. |
| `fabric/src/main/` | Points d'entrée Fabric, adaptateurs de plateforme, services et `fabric.mod.json`. |
| `fabric/run/` | Environnement de développement Fabric, mondes et mods de test. |
| `fabric/build/libs/` | Jars Fabric distribuables, avec le contenu commun intégré. |
| `neoforge/src/main/` | Points d'entrée NeoForge, adaptateurs, services et `neoforge.mods.toml`. |
| `neoforge/build/libs/` | Jar NeoForge distribuable, compilé depuis les sources communes. |
| `examples/`, `web/`, `docs/` | Packs d'exemple, éditeur et documentation partagés. |

Les packages de la logique, identifiants, données sauvegardées et formats JSON restent les
mêmes. `common` n'est pas un mod supplémentaire à installer. Le dossier de développement
existant a été déplacé de `run/` vers `fabric/run/` avec son contenu.

## Construire et lancer

Depuis la racine, avec Java 21 (`.\gradlew.bat` sous Windows) :

```sh
./gradlew clean build       # Reconstruit common, Fabric et NeoForge
./gradlew :common:build     # Compile et vérifie le code commun
./gradlew :fabric:build     # Construit le jar Fabric avec common
./gradlew :neoforge:build   # Construit le jar NeoForge depuis common
./gradlew :neoforge:runClient
./gradlew :neoforge:runServer
./gradlew runClient         # Alias de :fabric:runClient
./gradlew runServer         # Alias de :fabric:runServer
./gradlew genSources        # Sources du jeu pour Fabric
./gradlew exampleDatapack   # Pack dans build/dist/, à la racine
```

`publishMods` reste utilisable depuis la racine et cible Fabric. Sans jeton, les fichiers
simulant la publication sont écrits dans `fabric/build/mod-publish/`. Les workflows de build,
de release et de l'éditeur utilisent les nouveaux chemins.

Dans IntelliJ, recharger Gradle génère `Minecraft Client (Fabric)` et
`Minecraft Server (Fabric)`. `./gradlew :fabric:ideaSyncTask` les régénère aussi.
Ces configurations lancent les tâches Gradle, qui préparent Java 21, les mods de
développement et le pack d'exemple. Aucun lanceur Architectury n'est utilisé.

La release construit une fois les jars Fabric et NeoForge, vérifie leur contenu,
puis les place avec le pack d'exemple dans `build/release/`. Modrinth, CurseForge et GitHub
reçoivent les mêmes fichiers après vérification SHA-256. La propriété
`-Prelease_file=build/release/cobblemon-trainers-<version>.jar` permet à `publishMods`
d'utiliser ce jar sans le reconstruire. Le script CurseForge accepte le même chemin via
`RELEASE_FILE` ; `DRY_RUN=true` écrit uniquement ses métadonnées dans
`build/mod-publish/curseforge-<loader>.json` (avec `VERSION`, `RELEASE_TYPE` et
`LOADER=neoforge` pour NeoForge).

## Frontière entre logique et plateforme

`TrainerPlatform` décrit les opérations du loader : événements serveur, commandes,
rechargement, registres, chemins, détection des mods et transport réseau. `TrainerClientPlatform`
isole les événements et le transport client. Les implémentations Fabric sont découvertes
par `ServiceLoader`, sans référence de `common` vers une classe Fabric et sans initialiser
les classes client sur un serveur dédié. NeoForge fournit ses propres implémentations des
mêmes services. Les récepteurs réseau tournent sur le thread du jeu.
Les règles et les validations restent dans leurs classes communes.

Le build commun utilise Loom pour compiler contre Minecraft et Cobblemon avec les mappings
Mojang. Il utilise actuellement le jar Fabric de Cobblemon comme dépendance de compilation,
sans dépendre de Fabric API ni importer ses classes. `verifyLoaderIndependence`, exécutée
par `check`, refuse les imports de loaders dans les sources communes.

Fabric utilise le jar commun non remappé (`namedElements`), l'intègre avant son propre
remapping et regroupe les deux source sets pour lancer le jeu. Le jar de sources inclut
également les sources communes. Le module commun ne définit aucun lancement de jeu.

## Version NeoForge

NeoForge compile les sources de `common` avec son propre Minecraft et Cobblemon 1.8.1.
Son identifiant de loader est `cobblemon_trainers`, car NeoForge interdit les tirets dans
un ID de mod. Les identifiants des ressources et des données restent `cobblemon-trainers`.
Le module attend NeoForge 21.1.251 et Kotlin for Forge 5.12.0. Son jar est distinct de celui
de Fabric.
