# Cobblemon Trainers

Mod Fabric qui ajoute des dresseurs Pokémon configurables à Cobblemon. Chaque dresseur est
un fichier JSON : une équipe au format Showdown, un skin de joueur Minecraft, des messages
de combat. Les dresseurs se déclarent dans un datapack, donc sans toucher au code.

## Prérequis

| | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Fabric Loader | ≥ 0.19.3 |
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

Un clic droit sur le dresseur lance le combat. Si le combat ne peut pas démarrer (pas de
Pokémon dans ton équipe, combat déjà en cours, dresseur sans équipe), la raison s'affiche
dans le chat.

## Déclarer un dresseur

Les dresseurs se déclarent dans un datapack : `data/<namespace>/trainers/<nom>.json`.
L'ID du dresseur est `<namespace>:<nom>`, et `/reload` les recharge sans redémarrer.
Un pack chargé plus tard écrase un dresseur de même ID, selon les règles habituelles
d'empilement des datapacks.

Seul le nom de fichier compte : `trainers/kanto/red.json` donne l'ID `<namespace>:red`.
Un fichier invalide est ignoré et l'erreur est écrite dans les logs du serveur, sans
empêcher le chargement des autres.

### Exemple

```json
{
  "name": "Red",
  "level": 88,
  "skin": { "type": "player_username", "value": "Red" },
  "canBattle": true,
  "battleStartMessage": "...",
  "battleEndWinMessage": "C'est terminé !",
  "battleEndLoseMessage": "...",
  "team": [
    "Pikachu (M) @ Light Ball",
    "Ability: Static",
    "Level: 88",
    "Shiny: Yes",
    "EVs: 252 SpA / 4 SpD / 252 Spe",
    "Timid Nature",
    "- Thunderbolt",
    "- Iron Tail",
    "",
    "Snorlax (M) @ Leftovers",
    "Ability: Thick Fat",
    "Level: 88",
    "- Body Slam",
    "- Earthquake"
  ]
}
```

### Champs

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `name` | `Trainer` | Nom affiché au-dessus du dresseur |
| `level` | `1` | Niveau appliqué aux Pokémon qui n'ont **pas** de ligne `Level:` |
| `team` | `[]` | L'équipe, au format Showdown (voir ci-dessous) |
| `skin.type` | `player_username` | `player_username` ou `player_uuid` |
| `skin.value` | `Steve` | Pseudo ou UUID dont le skin est repris |
| `battleFormat` | `singles` | Nombre de Pokémon simultanés : `singles`, `doubles`, `triples` |
| `skill` | `5` | Difficulté de l'IA de combat, de 0 à 5 |
| `autoHealParty` | `true` | Soigne l'équipe du dresseur avant et après chaque combat |
| `canBattle` | `true` | À `false`, le clic droit ne fait rien |
| `battleStartMessage` | — | Envoyé au joueur au début du combat |
| `battleEndWinMessage` | — | Envoyé si le joueur gagne |
| `battleEndLoseMessage` | — | Envoyé si le joueur perd |

Tous les champs sont facultatifs : un JSON partiel reste valide.

`skill` et `level` sont deux choses différentes : `skill` est l'intelligence de l'IA qui
joue le combat, `level` ne sert que de valeur de repli pour les Pokémon dont l'entrée
Showdown ne précise pas `Level:`. Si toute ton équipe indique son niveau, `level` n'a aucun
effet visible.

`autoHealParty` agit aux deux bouts du combat : l'équipe du dresseur démarre au maximum de
ses PV, et elle est re-soignée une fois le combat terminé. À `false`, les dégâts et les PP
consommés persistent d'un combat à l'autre — utile pour un dresseur qu'on affronte en
plusieurs tentatives.

### Format de combat

`battleFormat` accepte `singles`, `doubles` et `triples`, ainsi que les alias `solo`, `duo`
et `trio`. Une valeur non reconnue retombe sur `singles` avec un avertissement dans les logs.

Un combat en double demande au moins 2 Pokémon de chaque côté, un triple au moins 3 — pour
le dresseur **comme** pour le joueur. Si l'une des deux équipes est trop courte, Cobblemon
refuse le combat et affiche la raison dans le chat.

Les dresseurs `theazertor` (double) et `kagumi` (triple) fournis par le mod servent
d'exemples.

### Traduire les textes d'un dresseur

`name`, `battleStartMessage`, `battleEndWinMessage` et `battleEndLoseMessage` sont envoyés
au joueur comme textes traduisibles. Deux usages :

- **Texte brut** — `"name": "Red"` s'affiche tel quel, dans toutes les langues.
- **Clé de traduction** — `"name": "trainer.mon_pack.red.name"`, définie dans un resource
  pack livré à côté du datapack.

Les clés sont résolues par le client, comme n'importe quel texte de Minecraft. Range-les
dans `assets/<namespace>/lang/<code>.json` :

```
mon_resource_pack/
└── assets/mon_pack/lang/
    ├── en_us.json
    └── fr_fr.json
```

```json
{
  "trainer.mon_pack.red.name": "Red",
  "trainer.mon_pack.red.battle_start": "..."
}
```

Chaque joueur voit alors le texte dans la langue de son jeu, **nom flottant du dresseur
compris**. Sur un serveur, `resource-pack` dans `server.properties` permet de le distribuer
automatiquement.

Les messages de la commande et les erreurs de combat sont eux toujours traduits, via les
fichiers lang du mod et de Cobblemon.

### Format d'équipe

Le tableau `team` accepte deux écritures, y compris mélangées :

- une entrée par ligne, les Pokémon séparés par une entrée vide (`""`) — comme l'exemple ;
- une entrée par Pokémon, ses lignes séparées par `\n`.

Lignes reconnues, en plus de la première :

| Ligne | Exemple |
| --- | --- |
| Première ligne | `Surnom (Espèce) (M) @ Objet` — surnom, genre et objet facultatifs |
| Talent | `Ability: Static` |
| Niveau | `Level: 88` |
| Chromatique | `Shiny: Yes` |
| Genre | `Gender: M` |
| EV / IV | `EVs: 252 SpA / 4 SpD / 252 Spe` |
| Nature | `Timid Nature` |
| Capacité | `- Thunderbolt` |

Les objets tenus sans namespace reçoivent `cobblemon:` — `Light Ball` devient
`cobblemon:light_ball`. Toute autre ligne est ignorée en silence.

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

## Licence

CC0-1.0 — voir [LICENSE](LICENSE).
