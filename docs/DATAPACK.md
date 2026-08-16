# Créer un datapack de dresseurs

Tout ce qu'un dresseur a besoin de savoir tient dans un fichier JSON. Aucun code, aucune
recompilation : un datapack suffit, et `/reload` applique les changements sans redémarrer
le serveur.

Ce document couvre la création complète d'un pack. Pour l'installation du mod et les
commandes, voir le [README](../README.md).

## Sommaire

- [Arborescence](#arborescence)
- [Le premier dresseur](#le-premier-dresseur)
- [Tous les champs](#tous-les-champs)
- [Le format d'équipe](#le-format-déquipe)
- [Format de combat](#format-de-combat)
- [Skins](#skins)
- [Musique de combat](#musique-de-combat)
- [Traduire les textes](#traduire-les-textes)
- [Tester son pack](#tester-son-pack)
- [Erreurs fréquentes](#erreurs-fréquentes)

## Arborescence

```
mon_pack/
├── pack.mcmeta
└── data/
    └── mon_pack/                         ← ton namespace
        └── cobblemontrainers/
            └── trainers/
                ├── red.json              → ID mon_pack:red
                └── kanto/
                    └── blue.json         → ID mon_pack:blue
```

```json
{
  "pack": {
    "pack_format": 48,
    "description": "Mes dresseurs"
  }
}
```

`pack_format` vaut **48** pour un datapack Minecraft 1.21.1.

Trois règles à retenir :

- Le dossier lu est `cobblemontrainers/trainers/`, à l'intérieur de ton namespace. Tout ce
  que le mod lit dans un pack est regroupé sous `cobblemontrainers/`, ce qui évite les
  collisions avec d'autres mods.
- L'ID d'un dresseur est `<namespace>:<nom de fichier>`. **Les sous-dossiers n'en font pas
  partie** : `kanto/blue.json` donne `mon_pack:blue`, pas `mon_pack:kanto/blue`. Ils servent
  seulement à ranger, mais attention aux doublons de noms.
- Un pack chargé plus tard écrase un dresseur de même ID, comme n'importe quelle ressource
  de datapack.

### Où poser le pack

Trois voies. Aucune n'est « la bonne » : choisis selon ce que ton pack contient et comment tu
veux le distribuer.

| Voie | Emplacement | Formats | Charge | Activation |
| --- | --- | --- | --- | --- |
| Dossier des mods | `mods/` | dossier, `.zip`, `.jar` | `data/` **et** `assets/` | automatique, dans tous les mondes |
| Datapack | `saves/<monde>/datapacks/` ou `world/datapacks/` | dossier, `.zip`, `.jar` | `data/` | par monde, activé à la découverte |
| Resource pack | `resourcepacks/` | dossier, `.zip`, `.jar` | `assets/` | à cocher dans les options du jeu |

Ce qui décide, c'est le contenu du pack :

- **Que des dresseurs** (`data/` seul) — n'importe laquelle des deux premières voies suffit.
- **Des dresseurs + des traductions ou de la musique** (`data/` et `assets/`) — `mods/` est la
  seule voie qui charge les deux moitiés en un fichier. Sinon il faut livrer la même archive
  deux fois, dans `datapacks/` **et** dans `resourcepacks/`, et le joueur doit aller cocher le
  resource pack.

#### `mods/` : un pack, rien d'autre

Un pack posé dans `mods/` n'a besoin **que de son `pack.mcmeta`**. Pas de `fabric.mod.json`,
pas de code, pas de manipulation : le mod ramasse tout dossier ou archive du dossier des mods
qui porte un `pack.mcmeta`, et l'expose à la fois comme datapack et comme resource pack.

```
mon_pack.jar
├── pack.mcmeta
├── data/mon_pack/cobblemontrainers/trainers/…
└── assets/mon_pack/{lang,sounds,sounds.json}
```

Le pack apparaît dans `/datapack list` et dans l'écran des resource packs sous l'ID
`mods/<nom de fichier>`. Le `.jar` n'a rien de particulier ici — un dossier ou un `.zip` font
pareil ; c'est juste le format le plus commode à distribuer.

Ça vaut pour le client comme pour le serveur, chacun lisant son propre dossier `mods/` :
un serveur y trouve les dresseurs, un joueur qui y met la même archive y trouve en plus les
traductions et la musique.

#### Le `fabric.mod.json`, si tu en veux un

Ajouter un `fabric.mod.json` à la racine reste possible, et change qui charge le pack : Fabric
le prend alors pour un mod à part entière et l'expose lui-même sous les deux types, le mod
laissant la main. Même résultat, avec un avantage — pouvoir déclarer une dépendance, donc une
erreur claire au démarrage plutôt qu'un pack chargé pour rien :

```json
{
	"schemaVersion": 1,
	"id": "mon_pack",
	"version": "1.0.0",
	"name": "Mon pack de dresseurs",
	"environment": "*",
	"depends": {
		"fabricloader": ">=0.17.2",
		"minecraft": "~1.21.1",
		"cobblemon-trainers": "*"
	}
}
```

Aucun `entrypoints` : c'est un mod sans classes. `id` doit être en minuscules
(`[a-z0-9_-]`) et unique.

> Attention si tu prends cette voie : un `fabric.mod.json` **mal formé** fait échouer le
> démarrage du jeu, alors qu'un pack sans ce fichier se charge tranquillement. Si tu n'as pas
> besoin de la dépendance, ne mets pas le fichier.

#### `pack_format` d'une archive qui sert des deux côtés

Les valeurs diffèrent selon le type en 1.21.1 : **48** côté données, **34** côté ressources.
Un pack qui porte `data/` et `assets/` déclare donc un intervalle, sinon l'écran des resource
packs l'affiche comme incompatible :

```json
{
  "pack": {
    "pack_format": 48,
    "supported_formats": { "min_inclusive": 34, "max_inclusive": 48 },
    "description": "Mes dresseurs"
  }
}
```

Un pack qui ne sert que de datapack n'en a pas besoin : `pack_format: 48` suffit.

#### Ce que `datapacks/` ne fera jamais

Le dossier `datapacks/` d'un monde est déclaré en `PackType.SERVER_DATA` et rien d'autre : un
`assets/` posé là n'est jamais lu, qu'il soit dans un dossier, un `.zip` ou un `.jar`. Les
traductions et la musique sont résolues par le client, qui ne regarde jamais dans le dossier
d'un monde — et sur un serveur, il n'a même pas le fichier. C'est précisément ce que la voie
`mods/` contourne.

## Le premier dresseur

`data/mon_pack/cobblemontrainers/trainers/red.json` :

```json
{
  "name": "Red",
  "level": 88,
  "skin": { "type": "player_username", "value": "Red" },
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

Puis, en jeu :

```
/reload
/spawntrainer mon_pack:red
```

## Tous les champs

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `name` | `Trainer` | Nom affiché au-dessus du dresseur |
| `level` | `1` | Niveau appliqué aux Pokémon qui n'ont **pas** de ligne `Level:` |
| `team` | `[]` | L'équipe, au format Showdown |
| `skin.type` | `player_username` | `player_username` ou `player_uuid` |
| `skin.value` | `Steve` | Pseudo ou UUID dont le skin est repris |
| `battleFormat` | `singles` | Nombre de Pokémon simultanés : `singles`, `doubles`, `triples` |
| `skill` | `5` | Difficulté de l'IA de combat, de 0 à 5 |
| `autoHealParty` | `true` | Soigne l'équipe du dresseur avant et après chaque combat |
| `canBattle` | `true` | À `false`, le clic droit ne fait rien |
| `battleStartMessage` | — | Envoyé au joueur au début du combat |
| `battleEndWinMessage` | — | Envoyé si le joueur gagne |
| `battleEndLoseMessage` | — | Envoyé si le joueur perd |
| `battleMusic` | piste du mod | ID du son joué pendant le combat, `null` pour aucun |

Tous les champs sont facultatifs : un JSON réduit à `{}` donne un dresseur valide, quoique
peu intéressant. Un fichier invalide est ignoré, l'erreur part dans les logs du serveur, et
les autres dresseurs se chargent quand même.

### `skill` n'est pas `level`

Deux réglages qu'on confond facilement :

- **`level`** est le niveau des Pokémon — et seulement une valeur de repli, pour les entrées
  Showdown qui ne précisent pas `Level:`. Si toute ton équipe indique son niveau, `level`
  n'a aucun effet visible.
- **`skill`** est l'intelligence de l'IA qui joue le combat, de 0 (joue au hasard) à 5 (joue
  sérieusement). Un dresseur niveau 100 avec `skill` 0 reste facile à battre.

### `autoHealParty`

À `true`, l'équipe du dresseur démarre au maximum de ses PV et se re-soigne une fois le
combat terminé. À `false`, les dégâts et les PP consommés persistent d'un combat à l'autre —
pratique pour un boss qu'on use en plusieurs tentatives.

## Le format d'équipe

Le tableau `team` accepte deux écritures, y compris mélangées dans le même fichier :

- une entrée par ligne, les Pokémon séparés par une entrée vide (`""`) — comme l'exemple
  ci-dessus ;
- une entrée par Pokémon, ses lignes séparées par `\n`.

```json
"team": [
  "Gyarados (M) @ Leftovers\nAbility: Intimidate\nLevel: 64\n- Waterfall",
  "Vaporeon (F)\nLevel: 62\n- Surf"
]
```

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

Toute autre ligne est ignorée en silence — tu peux donc coller un export Showdown tel quel,
les lignes que le mod ne connaît pas ne gênent pas.

Deux détails qui piègent :

- **Les objets tenus sans namespace reçoivent `cobblemon:`** : `Light Ball` devient
  `cobblemon:light_ball`. Pour un objet vanilla, écris-le en entier (`minecraft:stick`).
- **Les abréviations Showdown des stats sont traduites par le mod**, pas par Cobblemon :
  `HP`, `Atk`, `Def`, `SpA`, `SpD`, `Spe` deviennent `hp`, `attack`, `defence`,
  `special_attack`, `special_defence`, `speed`. Les noms longs (`Attack`, `Defense`,
  `Speed`) passent aussi. En revanche une abréviation hors de cette liste est ignorée en
  silence, comme n'importe quelle ligne inconnue.

## Format de combat

`battleFormat` accepte `singles`, `doubles` et `triples`, ainsi que les alias `solo`, `duo`
et `trio`. Une valeur non reconnue retombe sur `singles`, avec un avertissement dans les
logs.

Un double exige au moins 2 Pokémon **de chaque côté**, un triple au moins 3 — pour le
dresseur comme pour le joueur. Si l'une des deux équipes est trop courte, Cobblemon refuse
le combat et affiche la raison dans le chat.

## Skins

```json
"skin": { "type": "player_username", "value": "Notch" }
"skin": { "type": "player_uuid",     "value": "069a79f4-44e9-4726-a5be-fca90e38aaf5" }
```

Le skin est téléchargé depuis l'API Mojang au moment de l'apparition : il faut donc un accès
réseau et un compte existant. En cas d'échec, le dresseur garde le skin par défaut et la
raison est écrite dans les logs — le reste du dresseur fonctionne normalement.

## Musique de combat

Au début du combat, une musique est envoyée aux joueurs qui y participent, et coupée à la
fin — quelle qu'elle soit : victoire, défaite, fuite, `/stopbattle`, ou un dresseur qui
disparaît en pleine rencontre. **Rien d'autre ne joue en même temps** : ce qui passait
dans la catégorie *Musique* est arrêté juste avant, et Minecraft attend une bonne dizaine de
minutes avant de relancer sa musique d'ambiance, donc un combat se déroule sur ton thème
seul.

Sans rien écrire, tes dresseurs utilisent la piste fournie par le mod.

| `battleMusic` | Effet |
| --- | --- |
| absent | la piste du mod, `cobblemon-trainers:battle_music.corvault` |
| `null` ou `""` | aucune musique, le dresseur se combat en silence |
| un ID de son | cette piste-là |

### Livrer sa propre piste

Le son est joué par le client, donc **`data/` ne suffit pas** : le `.ogg` et son entrée
`sounds.json` vivent sous `assets/`, la partie resource pack.

```
mon_pack/
├── pack.mcmeta                            ← "pack_format": 34 côté resource pack en 1.21.1
└── assets/mon_pack/
    ├── sounds.json
    └── sounds/battle_music/champion.ogg
```

Selon la voie choisie (voir [Où poser le pack](#où-poser-le-pack)), ce `assets/` part dans
la même archive que `data/` — voie `mods/` — ou dans une copie déposée à part dans
`resourcepacks/`.

`sounds.json` :

```json
{
  "battle_music.champion": {
    "subtitle": "mon_pack.subtitles.champion",
    "sounds": [
      { "name": "mon_pack:battle_music/champion", "stream": true }
    ]
  }
}
```

Le dresseur y fait référence par la **clé** de `sounds.json`, préfixée du namespace — le
chemin du fichier n'apparaît jamais dans le JSON du dresseur :

```json
"battleMusic": "mon_pack:battle_music.champion"
```

Trois points à ne pas rater :

- **`"stream": true` est indispensable** sur un morceau long : sans lui, Minecraft charge
  tout le fichier en mémoire d'un coup.
- **Prends un fichier stéréo.** Minecraft joue les sons stéréo à volume constant, sans
  atténuation avec la distance — ce qu'on veut d'une musique. Un fichier mono serait
  spatialisé autour du joueur, comme un bruit de bloc.
- **La piste ne boucle pas.** Un combat plus long que le morceau finit en silence : prévois
  large, ou accepte-le.

Le `subtitle` est facultatif mais recommandé : c'est ce que lisent les joueurs qui jouent
sous-titres activés.

## Traduire les textes

`name`, `battleStartMessage`, `battleEndWinMessage` et `battleEndLoseMessage` sont envoyés
au joueur comme textes traduisibles. Deux usages, au choix, dresseur par dresseur :

- **Texte brut** — `"name": "Red"` s'affiche tel quel, dans toutes les langues.
- **Clé de traduction** — `"name": "trainer.mon_pack.red.name"`, définie sous `assets/`, la
  partie resource pack (même remarque que pour la musique : selon la voie choisie, elle
  voyage avec `data/` ou dans une copie posée dans `resourcepacks/`).

```
mon_pack/
└── assets/mon_pack/lang/
    ├── en_us.json
    └── fr_fr.json
```

```json
{
  "trainer.mon_pack.red.name": "Red",
  "trainer.mon_pack.red.battle_start": "Prêt ?"
}
```

Chaque joueur voit alors le texte dans la langue de son jeu, **nom flottant du dresseur
compris**. Sur un serveur, `resource-pack` dans `server.properties` distribue le pack
automatiquement.

C'est la seule voie de traduction : les clés sont résolues par le client, un fichier lang
posé dans le datapack ne servirait à rien. Et si aucune traduction n'existe, Minecraft
affiche la clé telle quelle — un bon moyen de repérer une clé oubliée.

## Tester son pack

```
/reload
/spawntrainer mon_pack:red
```

`/reload` recharge les dresseurs sans redémarrer. L'autocomplétion de `/spawntrainer`
propose les dresseurs effectivement chargés, sous leur ID complet `<pack>:<dresseur>` : si le
tien n'y apparaît pas, c'est qu'il n'a pas été lu — la raison est dans les logs du serveur.
Et si le namespace affiché n'est pas celui que tu attendais, c'est qu'un autre pack fournit
un dresseur de même nom.

Au chargement, le mod écrit une ligne récapitulative :

```
[cobblemon-trainers] Loaded 7 trainer(s): mon_pack:red, mon_pack:blue, …
```

Un resource pack, lui, ne se recharge pas avec `/reload` : c'est <kbd>F3</kbd>+<kbd>T</kbd>,
côté client.

## Erreurs fréquentes

| Symptôme | Cause probable |
| --- | --- |
| Le dresseur n'apparaît pas dans l'autocomplétion | Mauvais dossier : il faut `data/<ns>/cobblemontrainers/trainers/`, pas `data/<ns>/trainers/` |
| `Loaded 0 trainer(s)` depuis `datapacks/` | `pack.mcmeta` absent ou `pack_format` incorrect — le pack entier est ignoré par Minecraft |
| Un pack posé dans `mods/` n'a aucun effet | `pack.mcmeta` absent de la **racine** de l'archive, ou rangé sous un dossier intermédiaire parce que le dossier a été zippé au lieu de son contenu |
| Les dresseurs se chargent, mais pas la musique ni les traductions | L'archive est dans `datapacks/`. Ce dossier n'est lu **que** comme données, jamais comme ressources — le format n'y change rien. Pose-la dans `mods/`, ou une copie dans `resourcepacks/` |
| Le pack est au bon endroit et la musique reste muette | Le `.ogg` manque de l'archive, ou son chemin ne correspond pas au `name` de `sounds.json` (`<ns>:battle_music/x` → `assets/<ns>/sounds/battle_music/x.ogg`) |
| Le dresseur s'appelle `trainer.mon_pack.red.name` en jeu | Resource pack absent ou désactivé, ou clé absente du fichier lang |
| Le combat en double est refusé | Moins de 2 Pokémon d'un des deux côtés, le tien compris |
| Une stat EV/IV semble ignorée | Nom de stat hors de la liste reconnue — voir le tableau du format d'équipe |
| Aucune musique | Piste absente du resource pack, ou ID qui ne correspond pas à la clé de `sounds.json` |
| Le skin reste Steve | Pseudo inexistant ou API Mojang injoignable — voir les logs |

## Un exemple complet

Le dépôt contient un pack d'exemple couvrant chaque option, dresseur par dresseur :
[`examples/cobblemonrlm/`](../examples/cobblemonrlm). Zippe son contenu, renomme en `.jar`,
pose-le dans `mods/` : c'est tout.
La dresseuse `jacinthe` y montre le cas complet : équipe de six, textes traduits et
`battleMusic` pointant sur une piste livrée par le pack, `.ogg` compris.
