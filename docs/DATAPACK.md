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
- [Revanches et récompenses](#revanches-et-récompenses)
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
    └── mon_pack/                     ← ton namespace
        └── cobblemontrainers/
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

- Le dossier lu est `cobblemontrainers/`, directement à l'intérieur de ton namespace —
  au même niveau que les `species/` et `npcs/` de Cobblemon. Il porte le nom du mod plutôt
  qu'un `trainers/` générique, ce qui évite les collisions avec d'autres mods.
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
| Datapack | `saves/<monde>/datapacks/` ou `world/datapacks/` | dossier, `.zip` | `data/` | par monde, activé à la découverte |
| Resource pack | `resourcepacks/` | dossier, `.zip` | `assets/` | à cocher dans les options du jeu |

Ce qui décide, c'est le contenu du pack :

- **Que des dresseurs** (`data/` seul) — n'importe laquelle des deux premières voies suffit.
- **Des dresseurs + des traductions ou de la musique** (`data/` et `assets/`) — `mods/` est la
  seule voie qui charge les deux moitiés en un fichier. Sinon il faut livrer la même archive
  deux fois, dans `datapacks/` **et** dans `resourcepacks/`, et le joueur doit aller cocher le
  resource pack.
- **Des dresseurs habillés d'une image du pack** (`skin.type: texture`) — `mods/` est
  obligatoire, et le doublon `datapacks/` + `resourcepacks/` ne remplace pas : l'image est lue
  par le serveur, qui ne regarde que là. Voir [Skins](#skins).

#### `mods/` : un pack, rien d'autre

Un pack posé dans `mods/` n'a besoin **que de son `pack.mcmeta`**. Pas de `fabric.mod.json`,
pas de code, pas de manipulation : le mod ramasse tout dossier ou archive du dossier des mods
qui porte un `pack.mcmeta`, et l'expose à la fois comme datapack et comme resource pack.

```
mon_pack.jar
├── pack.mcmeta
├── data/mon_pack/cobblemontrainers/…
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

`data/mon_pack/cobblemontrainers/red.json` :

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
| `skin.type` | `player_username` | `player_username`, `player_uuid` ou `texture` |
| `skin.value` | `Steve` | Pseudo, UUID, ou chemin d'une image livrée par un pack |
| `skin.model` | `default` | Gabarit portant l'image : `default` (Steve) ou `slim` (Alex). Lu pour `texture` seulement |
| `battleFormat` | `singles` | Nombre de Pokémon simultanés : `singles`, `doubles`, `triples` |
| `skill` | `5` | Difficulté de l'IA de combat, de 0 à 5 |
| `autoHealParty` | `true` | Soigne l'équipe du dresseur avant et après chaque combat |
| `canBattle` | `true` | À `false`, le clic droit ne fait rien |
| `canRebattle` | `true` | À `false`, un joueur qui l'a battu ne peut plus le redéfier |
| `tracked` | `true` | À `false`, le dresseur n'apparaît pas dans le suivi de progression |
| `rewards` | `[]` | Objets remis au joueur à chaque victoire |
| `rewardOnce` | `false` | À `true`, `rewards` n'est remis qu'à la première victoire |
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

## Revanches et récompenses

Deux réglages indépendants : `canRebattle` décide si on peut redéfier le dresseur une fois
battu, `rewards` ce qu'on gagne en le battant.

```json
{
  "name": "Champion",
  "canRebattle": false,
  "rewards": [
    { "item": "cobblemon:master_ball", "count": 1 },
    { "item": "cobblemon:rare_candy", "count": 10 },
    { "item": "minecraft:diamond" }
  ]
}
```

### `canRebattle`

À `false`, le dresseur devient une rencontre unique : dès qu'un joueur l'a battu, ses clics
droits suivants n'ouvrent plus de combat et il reçoit un message le lui disant. Le combat
n'est même pas construit, donc rien ne bouge — ni équipe, ni musique.

**Ce qui est retenu, c'est l'ID du dresseur**, `mon_pack:champion`, et non le PNJ posé dans le
monde. Battre un exemplaire les bat tous : en poser dix sur une carte ne donne pas dix
combats, et tuer celui qu'on a battu pour le réinvoquer ne remet pas le compteur à zéro.

Trois précisions :

- **Seule une victoire compte.** Une défaite, une fuite, un `/stopbattle` ou un dresseur qui
  disparaît en plein combat ne marquent rien : le dresseur reste défiable.
- **C'est par joueur.** Un dresseur battu par l'un reste disponible pour les autres.
- **La mémoire vit dans le monde**, pas dans le datapack : `/reload` ne l'efface pas, et elle
  survit à un redémarrage. Renommer un fichier de dresseur change son ID, donc repart de zéro.

### `rewards`

Chaque entrée est un objet et sa quantité :

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `item` | — | ID complet de l'objet, **namespace compris** |
| `count` | `1` | Combien en donner |

Les objets partent dans l'inventaire du joueur ; ce qui n'y tient pas tombe à ses pieds,
donc une récompense n'est jamais perdue. Chaque objet reçu est annoncé dans le chat.

- **Le namespace est obligatoire** : `cobblemon:rare_candy`, `minecraft:diamond`. Un ID sans
  namespace est lu comme du `minecraft:`, contrairement aux objets tenus de l'équipe qui
  reçoivent `cobblemon:` par défaut.
- **Un objet introuvable est ignoré**, avec un avertissement dans les logs, et les autres
  récompenses sont quand même remises — typiquement quand le mod qui fournit l'objet n'est
  pas installé.
- La quantité est ramenée dans l'intervalle 1–6400, pour qu'une faute de frappe ne noie pas
  le joueur sous les objets.

### `rewardOnce`

Par défaut, un dresseur rejouable donne ses récompenses **à chaque victoire** — un dresseur
avec `rewards` et `canRebattle: true` est donc une source infinie d'objets, ce qui est parfois
voulu et souvent pas. `rewardOnce: true` limite le butin à la première victoire, tout en
laissant le combat rejouable autant qu'on veut.

Les combinaisons utiles :

| `canRebattle` | `rewardOnce` | Comportement |
| --- | --- | --- |
| `false` | peu importe | Un seul combat par joueur, une seule récompense |
| `true` | `true` | Combat rejouable à volonté, récompense à la première victoire seulement |
| `true` | `false` | Combat rejouable à volonté, récompense à chaque victoire |

### Le suivi de progression

`/listtrainers [<joueur>]` liste les dresseurs et dit lesquels le joueur a déjà vaincus.
Sans argument, c'est le joueur qui tape la commande. Niveau de permission 2 (opérateur).

```
Dresseurs de Steve — 1 / 3 vaincus
✔ mon_pack:champion — Champion (plus de revanche)
✘ mon_pack:rival — Rival
✘ mon_pack:debutant — Débutant
```

La mention `(plus de revanche)` ne paraît que sur un dresseur déjà battu **et** en
`canRebattle: false` : c'est le seul cas où la ligne est définitivement close, toutes les
autres restant défiables.

### `tracked`

Tous les dresseurs chargés n'ont pas vocation à figurer dans cette liste. Les dresseurs de
démonstration livrés par le mod, ceux d'un pack d'exemple, un PNJ qui ne se bat pas, un
dresseur qui n'est jamais invoqué : autant de lignes qui parasitent la progression réelle
d'un joueur. `"tracked": false` les en retire.

```json
{
  "name": "Villageoise",
  "canBattle": false,
  "tracked": false
}
```

Trois points :

- **C'est un réglage d'affichage, pas de mémoire.** Les victoires restent enregistrées, donc
  `canRebattle` et `rewardOnce` fonctionnent normalement sur un dresseur masqué.
- **`canBattle: false` ne l'implique pas.** Les deux champs restent indépendants, mais un PNJ
  qu'on ne peut pas combattre gagne presque toujours à porter les deux.
- **Les dresseurs livrés par le mod portent déjà `"tracked": false`** — ils sont chargés dans
  tous les mondes, ta liste ne montre donc que les tiens.

C'est aussi ce champ que liront les futurs suivis de progression, pas seulement
`/listtrainers`.

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
| Forme | `Aspects: rlm, poison` |
| Talent | `Ability: Static` |
| Niveau | `Level: 88` |
| Chromatique | `Shiny: Yes` |
| Genre | `Gender: M` |
| EV / IV | `EVs: 252 SpA / 4 SpD / 252 Spe` |
| Nature | `Timid Nature` |
| Capacité | `- Thunderbolt` |

Toute autre ligne est ignorée en silence — tu peux donc coller un export Showdown tel quel,
les lignes que le mod ne connaît pas ne gênent pas.

Trois détails qui piègent :

- **Les noms sont écrits comme sur Showdown**, la ponctuation comprise : `U-turn`,
  `Will-O-Wisp`, `Farfetch'd`, `Flabébé`, `Mr. Mime` passent tels quels — le mod les convertit
  en identifiants Cobblemon (`uturn`, `willowisp`, `farfetchd`, `flabebe`, `mrmime`). Une
  capacité qui n'existe pas est ignorée avec un avertissement dans le log : le Pokémon apparaît
  avec les autres. Seule exception, le suffixe de forme (`Raichu-Alola`), qui passe par la ligne
  `Aspects:`.
- **Les objets tenus sans namespace reçoivent `cobblemon:`** : `Light Ball` devient
  `cobblemon:light_ball`. Pour un objet vanilla, écris-le en entier (`minecraft:stick`).
  Le nom est converti en identifiant : accents retirés, `'` et `.` supprimés, tout le reste
  rendu en `_` - `Heavy-Duty Boots` devient `cobblemon:heavy_duty_boots` et `Exp. Share`
  `cobblemon:exp_share`. Un objet qui n'existe pas est ignoré avec un avertissement dans le
  log, le Pokémon apparaît quand même les mains vides.
- **Les abréviations Showdown des stats sont traduites par le mod**, pas par Cobblemon :
  `HP`, `Atk`, `Def`, `SpA`, `SpD`, `Spe` deviennent `hp`, `attack`, `defence`,
  `special_attack`, `special_defence`, `speed`. Les noms longs (`Attack`, `Defense`,
  `Speed`) passent aussi. En revanche une abréviation hors de cette liste est ignorée en
  silence, comme n'importe quelle ligne inconnue.

## Formes régionales, méga-évolutions, fakemon : la ligne `Aspects:`

Une forme n'est pas une espèce à part chez Cobblemon : c'est la même espèce portant d'autres
**aspects**. Un Raichu d'Alola est un `raichu` avec l'aspect `alolan`, un Haxorus RLM Poison
est un `haxorus` avec les aspects `rlm` et `poison`. La ligne `Aspects:` les liste, séparés
par des virgules ou de simples espaces :

```json
"team": [
  "Haxorus @ Life Orb",
  "Aspects: rlm, poison",
  "Ability: Venomedge",
  "Level: 65",
  "- Poison Jab",
  "- Dragon Claw"
]
```

**Où trouver les aspects d'une forme.** Ils sont écrits dans le pack qui l'ajoute, dans le
champ `aspects` de la forme (`data/<ns>/cobblemon/species/…` ou `species_additions/…`) :

```json
{ "name": "rlm", "aspects": ["rlm", "poison"], "…": "…" }
```

**Comment vérifier avant d'écrire le JSON.** La ligne `Aspects:` reprend exactement la syntaxe
de `/pokespawn`, donc un `/pokespawn haxorus rlm=true poison=true` en jeu te dit tout de suite
si tes aspects sont les bons.

Deux formes d'aspects existent, et la ligne accepte les deux :

| Type de caractéristique | Aspect | À écrire |
| --- | --- | --- |
| Drapeau (`"type": "flag"`) | `alolan`, `rlm`, `poison` | `Aspects: alolan` |
| Choix (`"type": "choice"`) | `wash-appliance`, `attack-forme` | `Aspects: appliance=wash` |

Une caractéristique à choix ne se déclare pas par son aspect mais par son couple
`caractéristique=valeur` — l'aspect `wash-appliance` de Rotom-Lavage vient de la
caractéristique `appliance` réglée sur `wash`. Le nom de la caractéristique est celui du
fichier `data/<ns>/cobblemon/species_features/<nom>.json`.

Trois points à connaître :

- **Un aspect inconnu est ignoré, avec un avertissement dans les logs** (`Ignoring unknown
  aspect '…'`). C'est le cas si le pack qui définit la forme n'est pas chargé, ou en cas de
  faute de frappe : le dresseur reçoit alors le Pokémon dans sa forme de base.
- **Le suffixe Showdown ne suffit pas.** Un export Showdown écrit la forme dans le nom de
  l'espèce (`Raichu-Alola`, `Rotom-Wash`) ; le mod ne le traduit pas, parce que le suffixe ne
  se distingue pas des espèces dont le nom contient un tiret (`Ho-Oh`, `Porygon-Z`). Retire
  le suffixe et mets une ligne `Aspects:`.
- **La forme n'a besoin de rien d'autre.** Cobblemon choisit la forme à partir des aspects du
  Pokémon, donc statistiques, types, talents et modèle suivent tout seuls.

## Format de combat

`battleFormat` accepte `singles`, `doubles` et `triples`, ainsi que les alias `solo`, `duo`
et `trio`. Une valeur non reconnue retombe sur `singles`, avec un avertissement dans les
logs.

Un double exige au moins 2 Pokémon **de chaque côté**, un triple au moins 3 — pour le
dresseur comme pour le joueur. Si l'une des deux équipes est trop courte, Cobblemon refuse
le combat et affiche la raison dans le chat.

## Skins

Trois façons d'habiller un dresseur : deux qui empruntent le skin d'un compte Minecraft, une
qui utilise une image que tu livres toi-même.

```json
"skin": { "type": "player_username", "value": "Notch" }
"skin": { "type": "player_uuid",     "value": "069a79f4-44e9-4726-a5be-fca90e38aaf5" }
"skin": { "type": "texture",         "value": "mon_pack:textures/trainers/red.png" }
```

Avec `player_username` et `player_uuid`, le skin est téléchargé depuis l'API Mojang au moment
de l'apparition : il faut donc un accès réseau et un compte existant.

En cas d'échec, quel que soit le type, le dresseur garde le skin par défaut et la raison est
écrite dans les logs — le reste du dresseur fonctionne normalement.

### Une image livrée par le pack

`texture` prend le chemin complet du fichier sous `assets/`, namespace en tête :
`mon_pack:textures/trainers/red.png` désigne
`assets/mon_pack/textures/trainers/red.png`. Le sous-dossier est libre ; l'extension `.png`,
elle, fait partie du chemin et doit être écrite.

```
mon_pack/
├── pack.mcmeta
├── assets/
│   └── mon_pack/
│       └── textures/
│           └── trainers/
│               └── red.png           ← skin de joueur, 64×64
└── data/
    └── mon_pack/
        └── cobblemontrainers/
            └── red.json
```

Le fichier est un **skin de joueur ordinaire**, celui que tu téléverserais sur ton compte :
un PNG 64×64 avec transparence. Le dresseur est rendu sur le gabarit Steve, ou sur celui
d'Alex — bras de 3 pixels — si tu ajoutes `"model": "slim"`.

Deux choses distinguent cette voie des traductions et de la musique :

- **L'image est lue par le serveur**, qui l'envoie ensuite aux clients avec le dresseur. Un
  joueur qui n'a pas ton pack voit quand même le bon skin.
- **Elle doit donc être posée là où le serveur regarde** : dans `mods/`, ou dans un `.jar`
  chargé comme mod. Un pack rangé dans `resourcepacks/` seul est hors de portée — c'est le
  client qui lit ce dossier, et il n'est jamais interrogé ici. Le dresseur reste alors en
  skin par défaut, avec l'explication dans les logs du serveur.

Sans image à toi, le mod en fournit une pour essayer :
`cobblemon-trainers:textures/trainers/example.png`.

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
/listtrainers
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

`/listtrainers` sert de seconde vérification : il ne montre que les dresseurs en
`tracked: true`, donc un dresseur chargé mais absent de la liste est un dresseur masqué, pas
un dresseur manquant.

Un resource pack, lui, ne se recharge pas avec `/reload` : c'est <kbd>F3</kbd>+<kbd>T</kbd>,
côté client.

### Poser un dresseur pour de bon

`/spawntrainer` invoque un dresseur de passage : il disparaît quand on le tue. Pour un
dresseur qui doit tenir un poste, le mod fournit un **bloc de dresseur**
(`cobblemon-trainers:trainer_spawner`), invisible comme une barrière, qui retient l'ID d'un
dresseur et le remet en place chaque fois qu'il manque à l'appel. Rien à déclarer dans le
pack : le bloc se règle en jeu, d'un clic droit. Voir
[le README](../README.md#le-bloc-de-dresseur).

## Erreurs fréquentes

| Symptôme | Cause probable |
| --- | --- |
| Le dresseur n'apparaît pas dans l'autocomplétion | Mauvais dossier : il faut `data/<ns>/cobblemontrainers/`, pas `data/<ns>/trainers/` ni `data/cobblemontrainers/` — `<ns>` est ton namespace, celui du pack |
| `Loaded 0 trainer(s)` depuis `datapacks/` | `pack.mcmeta` absent ou `pack_format` incorrect — le pack entier est ignoré par Minecraft |
| Un pack posé dans `mods/` n'a aucun effet | `pack.mcmeta` absent de la **racine** de l'archive, ou rangé sous un dossier intermédiaire parce que le dossier a été zippé au lieu de son contenu |
| Les dresseurs se chargent, mais pas la musique ni les traductions | L'archive est dans `datapacks/`. Ce dossier n'est lu **que** comme données, jamais comme ressources — le format n'y change rien. Pose-la dans `mods/`, ou une copie dans `resourcepacks/` |
| Le pack est au bon endroit et la musique reste muette | Le `.ogg` manque de l'archive, ou son chemin ne correspond pas au `name` de `sounds.json` (`<ns>:battle_music/x` → `assets/<ns>/sounds/battle_music/x.ogg`) |
| Le dresseur s'appelle `trainer.mon_pack.red.name` en jeu | Resource pack absent ou désactivé, ou clé absente du fichier lang |
| Le clic droit répond « déjà battu » | `canRebattle: false` et ce joueur l'a déjà vaincu. Réinvoquer le dresseur n'y change rien : c'est son ID qui est retenu |
| Aucune récompense à la victoire | `rewards` absent, `rewardOnce: true` sur une victoire qui n'est pas la première, ou objet introuvable — voir les logs |
| Le dresseur n'apparaît pas dans `/listtrainers` | `"tracked": false`, ou le dresseur n'est pas chargé du tout — `/spawntrainer` le dira |
| Le combat en double est refusé | Moins de 2 Pokémon d'un des deux côtés, le tien compris |
| Une stat EV/IV semble ignorée | Nom de stat hors de la liste reconnue — voir le tableau du format d'équipe |
| Aucune musique | Piste absente du resource pack, ou ID qui ne correspond pas à la clé de `sounds.json` |
| Le skin reste Steve | Pseudo inexistant ou API Mojang injoignable — voir les logs |
| Le skin `texture` reste celui par défaut | Chemin qui ne correspond à aucun fichier (l'extension `.png` en fait partie), ou pack posé dans `resourcepacks/` ou `datapacks/` au lieu de `mods/` : le serveur ne lit l'image que là — voir les logs |

## Un exemple complet

Le dépôt contient un pack d'exemple couvrant chaque option, dresseur par dresseur :
[`examples/cobblemonrlm/`](../examples/cobblemonrlm). Zippe son contenu, renomme en `.jar`,
pose-le dans `mods/` : c'est tout.
La dresseuse `jacinthe` y montre le cas complet : équipe de six, textes traduits et
`battleMusic` pointant sur une piste livrée par le pack, `.ogg` compris.
