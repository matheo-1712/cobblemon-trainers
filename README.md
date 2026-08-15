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
| `level` | `1` | Niveau du dresseur, et niveau des Pokémon sans ligne `Level:` |
| `team` | `[]` | L'équipe, au format Showdown (voir ci-dessous) |
| `skin.type` | `player_username` | `player_username` ou `player_uuid` |
| `skin.value` | `Steve` | Pseudo ou UUID dont le skin est repris |
| `canBattle` | `true` | À `false`, le clic droit ne fait rien |
| `battleStartMessage` | — | Envoyé au joueur au début du combat |
| `battleEndWinMessage` | — | Envoyé si le joueur gagne |
| `battleEndLoseMessage` | — | Envoyé si le joueur perd |
| `npcClass` | `cobblemon-trainers:trainer` | NPCClass Cobblemon utilisé |

Tous les champs sont facultatifs : un JSON partiel reste valide.

### Traduire les textes d'un dresseur

`name`, `battleStartMessage`, `battleEndWinMessage` et `battleEndLoseMessage` sont envoyés
au joueur comme textes traduisibles. Deux usages :

- **Texte brut** — `"name": "Red"` s'affiche tel quel, dans toutes les langues.
- **Clé de traduction** — `"name": "trainer.mon_pack.red.name"`, avec la clé déclarée dans
  `assets/mon_pack/lang/en_us.json`, `fr_fr.json`, etc. Chaque joueur voit alors le texte
  dans la langue de son jeu.

Le dresseur `rerebleue` fourni par le mod utilise cette seconde forme ; les `example_*`
utilisent la première. Les messages de la commande et les erreurs de combat sont eux
toujours traduits, via les fichiers lang du mod et de Cobblemon.

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

## Personnaliser le comportement du dresseur

Le mod fournit son propre NPCClass, `data/cobblemon-trainers/npcs/trainer.json` :

```json
{
  "hitbox": "player",
  "resourceIdentifier": "cobblemon:standard",
  "interaction": { "type": "cobblemon-trainers:battle" },
  "battleConfiguration": { "canChallenge": true },
  "autoHealParty": true,
  "canDespawn": false,
  "skill": 5
}
```

Garde `resourceIdentifier` sur `cobblemon:standard` : c'est ce qui indique au client
d'utiliser les modèles Steve/Alex texturés dynamiquement. Sans lui, les dresseurs perdent
leur skin de joueur.

Certains réglages ne sont pas modifiables par dresseur dans Cobblemon 1.7.3 — ils vivent
sur le NPCClass. C'est le cas du soin de l'équipe après le combat (`autoHealParty`) et du
niveau de l'IA (`skill`, de 0 à 5). Pour les changer, copie ce fichier dans ton datapack
sous ton propre namespace, ajuste-le, et pointe dessus via `npcClass` :

```json
{ "name": "Blue", "npcClass": "mon_pack:dresseur_coriace", "team": [] }
```

L'interaction `cobblemon-trainers:battle` est fournie par le mod ; elle lance un combat en
simple et affiche les erreurs éventuelles. Tu peux la remplacer par n'importe quelle
interaction Cobblemon (`dialogue`, `script`, `custom_script`, `none`).

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
