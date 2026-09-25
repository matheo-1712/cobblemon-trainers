# Structure de développement

Le mod est organisé en deux modules. Fabric est la seule version exécutable aujourd'hui.

| Emplacement | Rôle |
| --- | --- |
| `common/src/main/kotlin/` | Logique Minecraft/Cobblemon : dresseurs, parsing, combats, IA, progression, commandes, payloads et validation réseau, écrans, caches, musique et rendu. |
| `common/src/main/java/` | Mixins Minecraft et Cobblemon. |
| `common/src/main/resources/` | Ressources, langues, données et configuration des mixins. |
| `fabric/src/main/` | Points d'entrée Fabric, adaptateurs de plateforme, services et `fabric.mod.json`. |
| `fabric/run/` | Environnement de développement Fabric, mondes et mods de test. |
| `fabric/build/libs/` | Jars Fabric distribuables, avec le contenu commun intégré. |
| `examples/`, `web/`, `docs/` | Packs d'exemple, éditeur et documentation partagés. |

Les packages de la logique, identifiants, données sauvegardées et formats JSON restent les
mêmes. `common` n'est pas un mod supplémentaire à installer. Le dossier de développement
existant a été déplacé de `run/` vers `fabric/run/` avec son contenu.

## Construire et lancer

Depuis la racine, avec Java 21 (`.\gradlew.bat` sous Windows) :

```sh
./gradlew clean build       # Reconstruit common et Fabric
./gradlew :common:build     # Compile et vérifie le code commun
./gradlew :fabric:build     # Construit le jar Fabric avec common
./gradlew runClient         # Alias de :fabric:runClient
./gradlew runServer         # Alias de :fabric:runServer
./gradlew genSources        # Sources du jeu pour Fabric
./gradlew exampleDatapack   # Pack dans build/dist/, à la racine
```

`publishMods` reste utilisable depuis la racine et cible Fabric. Sans jeton, les fichiers
simulant la publication sont écrits dans `fabric/build/mod-publish/`. Les workflows de build,
de release et de l'éditeur utilisent les nouveaux chemins.

## Frontière entre logique et plateforme

`TrainerPlatform` décrit les opérations du loader : événements serveur, commandes,
rechargement, registres, chemins, détection des mods et transport réseau. `TrainerClientPlatform`
isole les événements et le transport client. Les implémentations Fabric sont découvertes
par `ServiceLoader`, sans référence de `common` vers une classe Fabric et sans initialiser
les classes client sur un serveur dédié. Les récepteurs réseau tournent sur le thread du jeu.
Les règles et les validations restent dans leurs classes communes.

Le build commun utilise Loom pour compiler contre Minecraft et Cobblemon avec les mappings
Mojang. Il utilise actuellement le jar Fabric de Cobblemon comme dépendance de compilation,
sans dépendre de Fabric API ni importer ses classes. `verifyLoaderIndependence`, exécutée
par `check`, refuse les imports de loaders dans les sources communes.

Fabric utilise le jar commun non remappé (`namedElements`), l'intègre avant son propre
remapping et regroupe les deux source sets pour lancer le jeu. Le jar de sources inclut
également les sources communes. Le module commun ne définit aucun lancement de jeu.

## Ajouter NeoForge

La logique est partagée, mais le port NeoForge reste à réaliser : ajouter son module, ses
points d'entrée et ses deux services, puis valider le classpath Cobblemon, le cycle des
registres, le réseau et les cibles des mixins. Les mixins partagés doivent être vérifiés
sur ce loader. La présence de `common` ne rend pas le jar Fabric compatible avec NeoForge.
