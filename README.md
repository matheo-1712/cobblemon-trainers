# Cobblemon Trainers

Mod Fabric qui ajoute des dresseurs Pokémon configurables à Cobblemon. Chaque dresseur est
un fichier JSON : une équipe au format Showdown, un skin de joueur Minecraft, des messages
et une musique de combat. Les dresseurs se déclarent dans un datapack, donc sans toucher au
code.

## Prérequis

| | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Fabric Loader | ≥ 0.17.2 |
| Java | 21 (exactement — Cobblemon refuse les autres) |
| Cobblemon | ≥ 1.7.3 |
| Fabric API | requis |
| Fabric Language Kotlin | requis |

## Installation

Place `cobblemon-trainers-<version>.jar` dans le dossier `mods/`, à côté de Cobblemon,
Fabric API et Fabric Language Kotlin. Le mod fonctionne côté serveur comme en solo.

## Utilisation

```
/spawntrainer <id>
/spawntrainer <id> <x> <y> <z>
```

Niveau de permission 2 (opérateur). L'`id` accepte la forme complète `namespace:nom` ou le
nom seul quand il n'est pas ambigu ; l'autocomplétion propose les dresseurs chargés.

Un clic droit sur le dresseur lance le combat, sur fond de musique de combat. Si le combat
ne peut pas démarrer (pas de Pokémon dans ton équipe, combat déjà en cours, dresseur sans
équipe), la raison s'affiche dans le chat.

Tuer ou supprimer un dresseur pendant le combat met fin à la rencontre au lieu de laisser le
joueur enfermé face à un adversaire absent.

## Déclarer un dresseur

Les dresseurs se déclarent dans un datapack, à
`data/<namespace>/cobblemontrainers/trainers/<nom>.json`. L'ID du dresseur est
`<namespace>:<nom>`, et `/reload` les recharge sans redémarrer.

```json
{
  "name": "Red",
  "level": 88,
  "skin": { "type": "player_username", "value": "Red" },
  "battleEndWinMessage": "C'est terminé !",
  "team": [
    "Pikachu (M) @ Light Ball",
    "Ability: Static",
    "Level: 88",
    "Shiny: Yes",
    "Timid Nature",
    "- Thunderbolt",
    "- Iron Tail"
  ]
}
```

Tous les champs sont facultatifs. Les autres réglages disponibles : format de combat
(`singles`, `doubles`, `triples`), difficulté de l'IA, soin de l'équipe entre les combats,
messages de début et de fin, musique de combat, et des textes traduisibles par clé.

**➜ Le guide complet est dans [docs/DATAPACK.md](docs/DATAPACK.md)** : arborescence,
référence de tous les champs, format d'équipe Showdown, skins, musique, traductions, et les
erreurs fréquentes.

Un pack d'exemple couvrant chaque option vit dans
[`examples/cobblemonrlm/`](examples/cobblemonrlm) : un seul dossier qui fait à la fois
datapack (`data/`) et resource pack (`assets/`).

Un pack peut être un dossier, un `.zip` ou un `.jar` — Minecraft n'accepte que les deux
premiers, le mod ajoute le `.jar` pour qu'une archive unique puisse être déposée dans
`datapacks/` comme dans `resourcepacks/`.

## Développement

```bash
./gradlew build
```

Le jar remappé sort dans `build/libs/`. Pour lancer un environnement de test :

```bash
./gradlew runClient
```

Le projet cible Java 21 via un toolchain Gradle, ce qui force aussi `runClient` et
`runServer` — sans ça, Cobblemon refuse de démarrer sous un JDK plus récent. Ne place pas
de jar Cobblemon dans `run/mods/` : il est déjà fourni par les dépendances, et le doublon
fait planter le client.

Les versions de Cobblemon et Architectury sont des **ID de version Modrinth** dans
`gradle.properties`, pas des numéros. Un ID ne dit rien de la version de jeu qu'il cible,
donc vérifie-le avant de le changer :

```bash
curl -s "https://api.modrinth.com/v2/version/<ID>" | python -c "import sys,json;v=json.load(sys.stdin);print(v['version_number'],v['loaders'],v['game_versions'])"
```

Voir aussi la [documentation Fabric](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up)
pour la configuration de l'IDE.

## Limites connues

- Les formes régionales notées à la Showdown (`Raichu-Alola`) ne sont pas traduites vers
  les aspects Cobblemon.
- Les skins sont récupérés depuis l'API Mojang : ils nécessitent un accès réseau et un
  pseudo existant. En cas d'échec, le dresseur garde le skin par défaut et la raison est
  écrite dans les logs.
- La musique de combat ne boucle pas : un combat plus long que la piste finit en silence.
  Faire boucler un son est une décision du client, et le mod n'a pas de code client.

## Licence

CC0-1.0 — voir [LICENSE](LICENSE).
