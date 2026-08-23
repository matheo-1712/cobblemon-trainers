# Créer un datapack de dresseurs

Un dresseur tient dans un fichier JSON. Aucun code, aucune recompilation : un datapack
suffit, et `/reload` applique les changements sans redémarrer le serveur.

Pour l'installation du mod et les commandes, voir le [README](../README.md).

## Sommaire

- [Arborescence](#arborescence) · [Où poser le pack](#où-poser-le-pack)
- [Le premier dresseur](#le-premier-dresseur) · [Tous les champs](#tous-les-champs)
- [Catégories](#catégories) · [Conditions pour combattre](#conditions-pour-combattre) ·
  [Advancements](#advancements) · [Faire venir un dresseur](SPAWNING.md)
- [Revanches et récompenses](#revanches-et-récompenses) ·
  [Farmable ou pas](#farmable-ou-pas) · [Le suivi de progression](#le-suivi-de-progression)
- [Le format d'équipe](#le-format-déquipe) · [La ligne `Aspects:`](#la-ligne-aspects)
- [Skins](#skins) · [Musique de combat](#musique-de-combat) ·
  [Traduire les textes](#traduire-les-textes)
- [Tester son pack](#tester-son-pack) · [Erreurs fréquentes](#erreurs-fréquentes)

## Arborescence

```
mon_pack/
├── pack.mcmeta
└── data/mon_pack/                      ← ton namespace
    └── cobblemontrainers/
        ├── red.json                    → mon_pack:red
        └── champions/                  ← un dossier = une catégorie
            ├── category.json           ← présentation de la catégorie (facultatif)
            └── erika.json              → mon_pack:champions/erika
```

```json
{ "pack": { "pack_format": 48, "description": "Mes dresseurs" } }
```

`pack_format` vaut **48** pour un datapack Minecraft 1.21.1.

- Le dossier lu est `cobblemontrainers/`, directement sous ton namespace - au même niveau que
  les `species/` et `npcs/` de Cobblemon. Il porte le nom du mod plutôt qu'un `trainers/`
  générique, ce qui évite les collisions avec d'autres mods.
- **L'ID est `<namespace>:<chemin>`, sous-dossiers compris** : `champions/erika.json` donne
  `mon_pack:champions/erika`. Le dossier est aussi la [catégorie](#catégories) du dresseur.
- `category.json` est le **seul nom de fichier réservé** : il décrit le dossier où il se
  trouve, il n'est jamais lu comme un dresseur.
- Un pack chargé plus tard écrase un dresseur de même ID, comme n'importe quelle ressource
  de datapack. Un fichier invalide est ignoré, l'erreur part dans les logs, et les autres
  dresseurs se chargent quand même.

### Où poser le pack

| Voie | Emplacement | Formats | Charge | Activation |
| --- | --- | --- | --- | --- |
| Dossier des mods | `mods/` | dossier, `.zip`, `.jar` | `data/` **et** `assets/` | automatique, tous les mondes |
| Datapack | `<monde>/datapacks/` | dossier, `.zip`, `.jar` | `data/` | par monde, à la découverte |
| Resource pack | `resourcepacks/` | dossier, `.zip`, `.jar` | `assets/` | à cocher dans les options |

Ce qui décide, c'est le contenu :

- **Que des dresseurs** (`data/` seul) - les deux premières voies conviennent.
- **Dresseurs + traductions ou musique** (`data/` et `assets/`) - `mods/` est la seule voie
  qui charge les deux moitiés en un fichier. Sinon il faut livrer la même archive deux fois,
  dans `datapacks/` **et** dans `resourcepacks/`.
- **Skin livré en image** (`skin.type: texture`) - `mods/` est obligatoire : l'image est lue
  par le serveur, qui ne regarde que là.

Un pack posé dans `mods/` n'a besoin **que de son `pack.mcmeta`**, à la racine de l'archive.
Ni `fabric.mod.json`, ni code : le mod ramasse tout dossier ou archive de `mods/` qui porte un
`pack.mcmeta` et l'expose comme datapack *et* comme resource pack, côté client comme côté
serveur. Il apparaît dans `/datapack list` sous l'ID `mods/<nom de fichier>`.

Ajouter un `fabric.mod.json` reste possible : Fabric prend alors le pack pour un mod et le
charge lui-même, ce qui permet de déclarer `"depends": { "cobblemon-trainers": "*" }` - donc
une erreur claire au démarrage plutôt qu'un pack chargé pour rien. En échange, un
`fabric.mod.json` mal formé fait échouer le démarrage du jeu.

Une archive qui sert **des deux côtés** déclare un intervalle, sinon l'écran des resource
packs l'affiche comme incompatible (`pack_format` vaut 48 côté données, 34 côté ressources) :

```json
{
  "pack": {
    "pack_format": 48,
    "supported_formats": { "min_inclusive": 34, "max_inclusive": 48 },
    "description": "Mes dresseurs"
  }
}
```

Enfin, `datapacks/` est déclaré en données et rien d'autre : un `assets/` posé là n'est
**jamais** lu, quel que soit le format de l'archive. C'est ce que la voie `mods/` contourne.

## Le premier dresseur

`data/mon_pack/cobblemontrainers/red.json` :

```json
{
  "name": "Red",
  "skin": { "type": "player_username", "value": "Red" },
  "battle": {
    "level": 88,
    "difficulty": 5,
    "music": "mon_pack:battle_music.finale"
  },
  "messages": {
    "greeting": "Tu es venu jusqu'ici. Voyons ce que tu vaux.",
    "start": "Prêt ?",
    "win": "Bien joué.",
    "lose": "Reviens quand tu seras prêt."
  },
  "progress": { "rematch": "never" },
  "rewards": [{ "item": "cobblemon:master_ball", "count": 1 }],
  "team": [
    "Pikachu (M) @ Light Ball\nAbility: Static\nLevel: 88\nShiny: Yes\nEVs: 252 SpA / 4 SpD / 252 Spe\nTimid Nature\n- Thunderbolt\n- Iron Tail",
    "Snorlax (M) @ Leftovers\nAbility: Thick Fat\nLevel: 88\n- Body Slam\n- Earthquake"
  ]
}
```

Puis, en jeu : `/reload` puis `/cobblemontrainers spawn mon_pack:red`.

## Tous les champs

Tout est facultatif : `{}` donne un dresseur valide, quoique peu intéressant.

### Racine

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `name` | `Trainer` | Nom affiché au-dessus du dresseur |
| `skin` | Steve | [Skin](#skins) |
| `team` | `[]` | L'équipe, **un Pokémon par entrée**, au [format Showdown](#le-format-déquipe) |
| `battle` | - | Le combat, ci-dessous |
| `messages` | - | [Ce que le dresseur dit](#messages) |
| `progress` | - | Ce que le battre change |
| `rewards` | `[]` | Objets remis au vainqueur |
| `requires` | - | [Conditions pour le combattre](#conditions-pour-combattre) |
| `location` | - | [Où le trouver, et l'appeler depuis le Battle Phone](SPAWNING.md) |

**➜ Le bloc `location` est décrit dans [SPAWNING.md](SPAWNING.md)**, avec ses conditions, ses
textes et tout ce que le mod fait autour d'un appel. Le déclarer suffit à rendre le dresseur
appelable ; ne pas le déclarer fait un dresseur qu'il faut aller trouver.

### `battle`

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `level` | `1` | Niveau des Pokémon qui n'ont **pas** de ligne `Level:` |
| `format` | `singles` | `singles`, `doubles`, `triples` - alias `solo`, `duo`, `trio`, suffixe `_50` |
| `difficulty` | `5` | [Intelligence de l'IA](DIFFICULTE.md), de 0 (au hasard) à 5 (sérieux) |
| `healParty` | `true` | Soigne l'équipe du dresseur avant et après chaque combat |
| `music` | piste du mod | ID du son joué pendant le combat, `null` pour le silence |

Le suffixe `_50` (`singles_50`, `doubles_50`, `triples_50`) met **les deux équipes** au
niveau 50 le temps du combat. `level` n'a donc plus d'effet visible sur un dresseur en `_50`.

Le combat se joue sur des copies. Les deux équipes y entrent **automatiquement soignées** -
PV pleins, statuts effacés, PP refaits -, un Pokémon KO compris. Et rien n'en ressort : quel
que soit le résultat, victoire, défaite ou combat interrompu, l'équipe du joueur retrouve les
PV, les statuts et les PP qu'elle avait avant. Elle gagne quand même son expérience et ses EV.

Le soin ne vaut donc que pour la durée du combat : un Pokémon KO avant un `_50` l'est encore
après. Aucun objet de soin n'est nécessaire pour lancer le combat, et aucun n'est économisé.

`level` n'est pas `difficulty` : le premier est le niveau des Pokémon - et seulement un repli,
pour les entrées Showdown sans `Level:` -, le second est la qualité du jeu de l'IA. Un
dresseur niveau 100 en `difficulty: 0` reste facile.

`difficulty` décide aussi de ce que le mod corrige chez l'IA de Cobblemon : rien en dessous
de `3`, les erreurs impossibles à `3`, les pièges d'entrée à `4`, la lecture complète du
combat à `5`.

**➜ Le détail exact de chaque niveau est dans [DIFFICULTE.md](DIFFICULTE.md)**, avec la
commande `/cobblemontrainers debugai` pour voir en combat ce que le mod a corrigé et pourquoi.

`healParty: false` fait persister dégâts et PP d'un combat à l'autre - pratique pour un boss
qu'on use en plusieurs tentatives.

Un `doubles` exige au moins 2 Pokémon **de chaque côté**, un `triples` au moins 3. Si l'une
des équipes est trop courte, Cobblemon refuse le combat et l'explique dans le chat.

### `messages`

Ce que le dresseur dit passe par la boîte de dialogue de Cobblemon, celle de leurs propres
NPC : un portrait, un nom, la réplique, et une rangée de boutons. Rien n'est envoyé au chat.

| Champ | Quand |
| --- | --- |
| `greeting` | Au clic droit, au-dessus des boutons Combattre / Annuler |
| `start` | Après avoir accepté, juste avant que le combat s'ouvre |
| `win` | Une fois le joueur vainqueur |
| `lose` | Une fois le joueur battu |

Chacun est facultatif. Sans `greeting`, le mod met une phrase à lui. Sans `start`, accepter
lance le combat directement. Sans `win` ni `lose`, rien ne s'ouvre après le combat.

Un dresseur qui refuse le combat le dit dans la même boîte, à la place du `greeting` et sans
bouton Combattre : déjà battu et sans revanche, [condition](#conditions-pour-combattre) non
remplie, ou équipe du joueur K.O.

### `progress`

| Champ | Défaut | Valeurs | Rôle |
| --- | --- | --- | --- |
| `rematch` | `unlimited` | `unlimited`, `never` | Peut-on le redéfier une fois battu |
| `listed` | `true` | booléen | Apparaît dans le Battle Phone et `/cobblemontrainers list` |

## Catégories

**Le dossier d'un dresseur est sa catégorie.** Rien à déclarer : ranger `erika.json` dans
`champions/` suffit à en faire un champion. Les catégories groupent la liste du Battle Phone
et de `/cobblemontrainers list`, et servent de cible aux
[conditions](#conditions-pour-combattre) et aux [advancements](#advancements).

Un `category.json` **dans le dossier** lui donne un nom et une place, les deux facultatifs :

```json
// cobblemontrainers/champions/category.json
{ "name": "category.mon_pack.champions", "order": 1 }
```

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `name` | le nom du dossier | Nom affiché, [traduisible](#traduire-les-textes) comme celui d'un dresseur |
| `order` | après les autres | Place dans la page du pack, le plus petit en haut |

- Une catégorie sans fichier s'affiche sous le nom de son dossier et se range après celles qui
  ont un `order`, par ordre alphabétique.
- Les dresseurs posés à la racine forment un dernier groupe, sous un titre fourni par le mod
  (« Dresseurs »). Un pack qui n'utilise aucune catégorie n'affiche aucun titre : la liste est
  exactement celle d'avant.
- Un sous-dossier de sous-dossier est une catégorie à part entière (`champions/kanto`), avec
  son propre `category.json`.

## Conditions pour combattre

Le bloc `requires` ferme un dresseur tant qu'un joueur n'a pas fait ce qu'il demande. Il le
refuse alors poliment, en listant ce qui manque - et par défaut, il n'apparaît même pas dans
le Battle Phone.

```json
"requires": {
  "defeated": ["champions/jacinthe", "champions/pierre"],
  "victories": { "count": 8, "category": "champions" },
  "items": [{ "item": "mon_pack:badge_roche", "count": 1 }],
  "advancement": "mon_pack:acces_ligue",
  "hidden": false,
  "message": "trainer.mon_pack.maitre.locked"
}
```

| Champ | Rôle |
| --- | --- |
| `defeated` | Dresseurs à avoir battus. Sans namespace, l'ID est lu dans le pack du dresseur qui l'exige - chemin compris (`champions/jacinthe`) |
| `victories` | Un nombre de dresseurs battus : `count`, restreint par `pack` et/ou `category`. `count` omis veut dire **tous ceux du groupe** |
| `items` | Objets à avoir sur soi, ID complet. **Jamais consommés** |
| `advancement` | Un advancement à avoir obtenu, vanilla ou d'un pack |
| `hidden` | `true` par défaut : le dresseur est absent du Battle Phone tant qu'il est verrouillé. `false` l'y laisse, verrouillé et avec ses conditions affichées |
| `message` | Ce que le dresseur répond. Par défaut, une phrase du mod - la liste de ce qui manque est ajoutée dans les deux cas |

- **Toutes les conditions déclarées doivent être remplies.** Elles s'additionnent, ce ne sont
  jamais des alternatives.
- `victories` ne compte que les dresseurs `listed`, et **jamais le dresseur qui l'exige** :
  un champion peut donc demander « battre tous les champions ».
- Un ID d'objet, de dresseur ou d'advancement introuvable compte comme non rempli, avec un
  avertissement dans les logs : une faute de frappe ferme le dresseur, elle ne l'ouvre pas.
- Le refus est renvoyé **avant** que le combat ne se construise : ni équipe soignée, ni
  musique.

## Advancements

Battre un dresseur déclenche le critère `cobblemon-trainers:trainer_defeated`. Tes
advancements sont alors des advancements ordinaires - titre, icône, arbre, toast, récompenses
sont ceux de Minecraft.

```json
// data/mon_pack/advancement/badge_roche.json
{
  "display": {
    "icon": { "id": "cobblemon:poke_ball" },
    "title": { "translate": "advancement.mon_pack.badge_roche.title" },
    "description": { "translate": "advancement.mon_pack.badge_roche.description" },
    "frame": "task"
  },
  "criteria": {
    "battu": {
      "trigger": "cobblemon-trainers:trainer_defeated",
      "conditions": { "trainer": "mon_pack:champions/pierre" }
    }
  }
}
```

| Condition | Rôle |
| --- | --- |
| `trainer` | Le dresseur battu |
| `category` | Sa catégorie |
| `pack` | Son namespace |
| `count` | Combien de dresseurs **différents** répondant aux filtres ci-dessus le joueur a battus |

Toutes facultatives et cumulatives ; sans aucune, le critère se valide au premier dresseur
battu. `trainer` et `category` acceptent un ID complet (`mon_pack:champions/pierre`) ou un
chemin nu (`champions/pierre`), qui vaut alors pour tous les namespaces.

« Battre les 8 champions » tient donc en un critère :

```json
"conditions": { "pack": "mon_pack", "category": "champions", "count": 8 }
```

Le compte est lu dans la progression enregistrée, celle-là même qu'affiche
`/cobblemontrainers list` : il survit à un redémarrage et n'est pas remis à zéro par `/reload`.

## Revanches et récompenses

`progress.rematch` décide si on peut redéfier le dresseur, `rewards` ce qu'on gagne, et chaque
récompense décide elle-même à quelle fréquence elle tombe.

```json
{
  "progress": { "rematch": "never" },
  "rewards": [
    { "item": "cobblemon:master_ball", "count": 1 },
    { "item": "cobblemon:rare_candy", "count": 10 },
    { "item": "minecraft:diamond" }
  ]
}
```

**Ce qui est retenu, c'est l'ID du dresseur**, pas le PNJ posé dans le monde. Battre un
exemplaire les bat tous : en poser dix sur une carte ne donne pas dix combats, et tuer celui
qu'on a battu pour le réinvoquer ne remet pas le compteur à zéro.

- **Seule une victoire compte.** Une défaite, une fuite, un `/stopbattle` ou un dresseur qui
  disparaît en plein combat ne marquent rien.
- **C'est par joueur.** Un dresseur battu par l'un reste disponible pour les autres.
- **La mémoire vit dans le monde**, pas dans le datapack : `/reload` ne l'efface pas.
  Renommer ou déplacer un fichier change son ID, donc repart de zéro.

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `item` | - | ID complet, **namespace obligatoire** |
| `count` | `1` | Combien, ramené dans 1-6400 |
| `hidden` | `false` | Ne pas l'annoncer dans le Battle Phone |
| `firstWinOnly` | `false` | Ne tomber qu'à la première victoire de ce joueur |

Les objets partent dans l'inventaire, ce qui n'y tient pas tombe aux pieds du joueur, et chaque
objet reçu est annoncé dans le chat. Un objet introuvable est ignoré avec un avertissement, les
autres sont remis quand même.

**Le Battle Phone affiche les récompenses sur la fiche du dresseur**, avant même de l'avoir
battu : contrairement à son équipe, une récompense est la raison d'essayer. `hidden` retire une
ligne de cet affichage sans rien changer à ce qui est remis - le joueur la découvre en gagnant.
Marquer toutes les lignes en `hidden` est la façon d'avoir un dresseur dont les récompenses sont
entièrement secrètes.

```json
"rewards": [
  { "item": "cobblemon:rare_candy", "count": 10 },
  { "item": "cobblemon:master_ball", "hidden": true }
]
```

### Farmable ou pas

`firstWinOnly` décide, **récompense par récompense**, si elle retombe aux victoires suivantes.
Par défaut non : une récompense se refarme tant que le dresseur accepte la revanche.

C'est ce qui permet à un même combat de donner un trophée une fois et du consommable à chaque
fois :

```json
"progress": { "rematch": "unlimited" },
"rewards": [
  { "item": "cobblemon:link_cable", "firstWinOnly": true },
  { "item": "cobblemon:exp_candy_l", "count": 5 }
]
```

Le Câble Liaison tombe une fois, les bonbons à chaque victoire. La fiche du Battle Phone le
signale : une récompense en `firstWinOnly` porte un marqueur, en contour tant qu'elle est à
gagner et plein une fois obtenue, et son icône est alors grisée. Le survol le dit en toutes
lettres.

`firstWinOnly` n'a aucun effet sur un dresseur en `"rematch": "never"` : il n'y a jamais de
deuxième victoire à distinguer.

## Le suivi de progression

Deux entrées pour la même donnée.

`/cobblemontrainers list [<joueur>]` liste les dresseurs par catégorie et dit lesquels le
joueur a vaincus :

```
Dresseurs de Steve - 1 / 3 vaincus
Champions - 1 / 2
✔ mon_pack:champions/jacinthe - Jacinthe (plus de revanche)
✘ mon_pack:champions/maitre - Le Maître (verrouillé, 1 condition(s) restante(s))
Dresseurs - 0 / 1
✘ mon_pack:rival - Rival
```

L'objet **Battle Phone** (`cobblemon-trainers:battle_phone`) montre la même chose à un joueur
ordinaire, dans un écran : un onglet par datapack, un titre par catégorie, le skin de chaque
dresseur et son état. Il affiche aussi **l'équipe d'un dresseur une fois celui-ci vaincu**,
modèles à l'appui - avant, les six cases restent vides, le serveur refusant purement et
simplement d'envoyer l'équipe.

Trois différences entre les deux :

- Le Battle Phone **cache** les dresseurs verrouillés en `hidden` ; `/cobblemontrainers list` les
  montre toujours, avec leur nombre de conditions restantes. C'est la vue de l'opérateur.
- `"listed": false` retire le dresseur des deux. C'est un réglage d'affichage, pas de mémoire :
  les victoires restent enregistrées, donc `rematch` et `firstWinOnly` continuent de
  fonctionner.
- Les dresseurs de démonstration livrés par le mod apparaissent dans les deux, dans leur
  propre onglet - ton namespace fait le tien.

## Le format d'équipe

**Une entrée du tableau `team` = un Pokémon**, ses lignes séparées par `\n` : le bloc que
Showdown exporte, collé tel quel.

```json
"team": [
  "Gyarados (M) @ Leftovers\nAbility: Intimidate\nLevel: 64\n- Waterfall",
  "Vaporeon (F)\nLevel: 62\n- Surf"
]
```

Lignes reconnues, en plus de la première :

| Ligne | Exemple |
| --- | --- |
| Première ligne | `Surnom (Espèce) (M) @ Objet` - surnom, genre et objet facultatifs |
| Forme | `Aspects: rlm, poison` |
| Talent | `Ability: Static` |
| Niveau | `Level: 88` |
| Chromatique | `Shiny: Yes` |
| Genre | `Gender: M` |
| EV / IV | `EVs: 252 SpA / 4 SpD / 252 Spe` |
| Nature | `Timid Nature` |
| Capacité | `- Thunderbolt` |

Toute autre ligne est ignorée en silence : un export Showdown se colle sans nettoyage.

Trois détails qui piègent :

- **Les noms s'écrivent comme sur Showdown**, ponctuation comprise : `U-turn`, `Will-O-Wisp`,
  `Farfetch'd`, `Flabébé`, `Mr. Mime` sont convertis en identifiants Cobblemon. Une capacité
  inexistante est ignorée avec un avertissement, le Pokémon apparaît avec les autres. Seule
  exception, le suffixe de forme (`Raichu-Alola`) : voir [`Aspects:`](#la-ligne-aspects).
- **Un objet tenu sans namespace reçoit `cobblemon:`** : `Light Ball` devient
  `cobblemon:light_ball`, `Heavy-Duty Boots` `cobblemon:heavy_duty_boots`. Pour un objet
  vanilla, écris-le en entier (`minecraft:stick`). Un objet introuvable est ignoré avec un
  avertissement, le Pokémon apparaît les mains vides.
- **Les abréviations de stats sont traduites par le mod** : `HP`, `Atk`, `Def`, `SpA`, `SpD`,
  `Spe`, et les noms longs. Une abréviation hors de cette liste est ignorée en silence.

## La ligne `Aspects:`

Une forme n'est pas une espèce à part chez Cobblemon : c'est la même espèce portant d'autres
**aspects**. Un Raichu d'Alola est un `raichu` avec l'aspect `alolan`. La ligne `Aspects:` les
liste, séparés par des virgules ou des espaces :

```json
"team": ["Haxorus @ Life Orb\nAspects: rlm, poison\nAbility: Venomedge\nLevel: 65\n- Poison Jab"]
```

| Type de caractéristique | Aspect | À écrire |
| --- | --- | --- |
| Drapeau (`"type": "flag"`) | `alolan`, `rlm` | `Aspects: alolan` |
| Choix (`"type": "choice"`) | `wash-appliance` | `Aspects: appliance=wash` |

Une caractéristique à choix ne se déclare pas par son aspect mais par son couple
`caractéristique=valeur` : l'aspect `wash-appliance` de Rotom-Lavage vient de la
caractéristique `appliance` réglée sur `wash`. Le nom de la caractéristique est celui du
fichier `data/<ns>/cobblemon/species_features/<nom>.json`.

- **Où les trouver** : dans le champ `aspects` de la forme, chez le pack qui l'ajoute
  (`data/<ns>/cobblemon/species/…` ou `species_additions/…`).
- **Comment vérifier** : la ligne reprend exactement la syntaxe de `/pokespawn`, donc
  `/pokespawn haxorus rlm=true poison=true` en jeu te le dit tout de suite.
- **Un aspect inconnu est ignoré** avec un avertissement : le dresseur reçoit le Pokémon dans
  sa forme de base. Typiquement, le pack qui définit la forme n'est pas chargé.
- **La forme n'a besoin de rien d'autre** : statistiques, types, talents et modèle suivent.

## Skins

```json
"skin": { "type": "player_username", "value": "Notch" }
"skin": { "type": "player_uuid",     "value": "069a79f4-44e9-4726-a5be-fca90e38aaf5" }
"skin": { "type": "texture",         "value": "mon_pack:textures/trainers/red.png", "model": "slim" }
```

Les deux premiers téléchargent le skin depuis l'API Mojang à l'apparition : il faut un accès
réseau et un compte existant. En cas d'échec, quel que soit le type, le dresseur garde le skin
par défaut et la raison est dans les logs.

`texture` prend le chemin complet sous `assets/`, namespace en tête et `.png` compris :
`mon_pack:textures/trainers/red.png` désigne `assets/mon_pack/textures/trainers/red.png`. Le
fichier est un **skin de joueur ordinaire**, PNG 64×64 avec transparence, rendu sur le gabarit
Steve ou sur celui d'Alex avec `"model": "slim"`.

Cette voie a deux particularités :

- **L'image est lue par le serveur**, qui l'envoie aux clients avec le dresseur : un joueur
  qui n'a pas ton pack voit quand même le bon skin.
- **Elle doit donc être dans `mods/`.** Un pack rangé dans `resourcepacks/` ou `datapacks/`
  est hors de portée du serveur ; le dresseur reste en skin par défaut, l'explication est dans
  les logs.

Sans image à toi, le mod en fournit une pour essayer :
`cobblemon-trainers:textures/trainers/example.png`.

## Musique de combat

La musique est envoyée aux joueurs du combat et coupée à la fin, quelle qu'elle soit :
victoire, défaite, fuite, `/stopbattle`, ou un dresseur qui disparaît. **Rien d'autre ne joue
en même temps** : ce qui passait en catégorie *Musique* est arrêté juste avant.

| `battle.music` | Effet |
| --- | --- |
| absent | la piste du mod |
| `null` ou `""` | aucune musique |
| un ID de son | cette piste-là |

Le son est joué par le client, donc **`data/` ne suffit pas** : le `.ogg` et son entrée
`sounds.json` vivent sous `assets/`.

```
mon_pack/
├── pack.mcmeta
└── assets/mon_pack/
    ├── sounds.json
    └── sounds/battle_music/champion.ogg
```

```json
// sounds.json
{
  "battle_music.champion": {
    "subtitle": "mon_pack.subtitles.champion",
    "sounds": [{ "name": "mon_pack:battle_music/champion", "stream": true }]
  }
}
```

Le dresseur référence la **clé** de `sounds.json`, jamais le chemin du fichier :
`"music": "mon_pack:battle_music.champion"`.

- **`"stream": true` est indispensable** sur un morceau long, sinon Minecraft charge tout le
  fichier en mémoire d'un coup.
- **Prends un fichier stéréo** : Minecraft les joue à volume constant, sans atténuation avec
  la distance. Un mono serait spatialisé comme un bruit de bloc.
- **La piste ne boucle pas** : un combat plus long que le morceau finit en silence.

## Traduire les textes

`name`, les `messages` et le `name` d'une catégorie sont envoyés comme textes
traduisibles. Deux usages, au choix :

- **Texte brut** - `"name": "Red"` s'affiche tel quel, dans toutes les langues.
- **Clé de traduction** - `"name": "trainer.mon_pack.red.name"`, définie sous `assets/`.

```json
// assets/mon_pack/lang/fr_fr.json
{
  "trainer.mon_pack.red.name": "Red",
  "category.mon_pack.champions": "Champions"
}
```

Chaque joueur voit alors le texte dans sa langue, **nom flottant du dresseur compris**. Sur un
serveur, `resource-pack` dans `server.properties` distribue le pack automatiquement.

C'est la seule voie de traduction : les clés sont résolues par le client, un fichier lang posé
dans le datapack ne servirait à rien. Si aucune traduction n'existe, Minecraft affiche la clé
telle quelle - un bon moyen de repérer une clé oubliée.

## Tester son pack

```
/reload
/cobblemontrainers spawn mon_pack:champions/erika
/cobblemontrainers list
/cobblemontrainers defeat mon_pack:champions/erika
```

`/reload` recharge les dresseurs sans redémarrer. L'autocomplétion de `spawn` propose
les dresseurs effectivement chargés, sous leur ID complet : si le tien n'y est pas, il n'a pas
été lu - la raison est dans les logs. Un nom de fichier seul suffit à la commande
(`/cobblemontrainers spawn erika`), le mod retrouve le dossier.

Au chargement, le mod écrit une ligne récapitulative :

```
[cobblemon-trainers] Loaded 7 trainer(s) in 2 categor(y/ies): mon_pack:champions/erika, …
```

`/cobblemontrainers defeat <id|all> [<joueurs>] [reset]` inscrit une victoire sans combat : de
quoi vérifier un `requires`, un advancement ou une fiche du Battle Phone sans jouer toute la
ligue - `all` coche tous les dresseurs chargés d'un coup. Il ne remet pas les récompenses, et
`reset` oublie la victoire pour retester le verrou. Un advancement déjà obtenu, lui, ne se
retire qu'avec `/advancement revoke`.

Un resource pack, lui, ne se recharge pas avec `/reload` : c'est <kbd>F3</kbd>+<kbd>T</kbd>.

Pour poser un dresseur à demeure plutôt que de le faire apparaître, le mod fournit un **bloc
de dresseur** (`cobblemon-trainers:trainer_spawner`), qui retient un ID et remet le dresseur en
place quand il manque. Rien à déclarer dans le pack : il se règle en jeu, d'un clic droit.
Voir [le README](../README.md#le-bloc-de-dresseur).

## Erreurs fréquentes

| Symptôme | Cause probable |
| --- | --- |
| Le dresseur n'apparaît pas dans l'autocomplétion | Mauvais dossier : il faut `data/<ns>/cobblemontrainers/`, `<ns>` étant ton namespace |
| `Loaded 0 trainer(s)` depuis `datapacks/` | `pack.mcmeta` absent ou `pack_format` incorrect - Minecraft ignore le pack entier |
| Un pack dans `mods/` n'a aucun effet | `pack.mcmeta` absent de la **racine** de l'archive, ou dossier zippé au lieu de son contenu |
| Les dresseurs se chargent, pas la musique ni les traductions | L'archive est dans `datapacks/`, lu **uniquement** comme données. Pose-la dans `mods/` |
| Un fichier `category.json` ne fait rien | Il est à la racine de `cobblemontrainers/` : il décrit le dossier où il se trouve, il lui en faut un |
| Deux dresseurs de même nom s'écrasent | Ils étaient dans le même dossier : l'ID inclut le chemin, pas seulement le nom |
| Le clic droit répond « ne veut pas encore te combattre » | Un `requires` non rempli. `/cobblemontrainers list <joueur>` dit combien de conditions restent |
| Un dresseur `requires` n'apparaît pas dans le Battle Phone | C'est le comportement par défaut (`hidden: true`). Mets `"hidden": false` |
| Le clic droit répond « déjà battu » | `"rematch": "never"` et ce joueur l'a vaincu. Réinvoquer le dresseur n'y change rien |
| L'advancement ne se déclenche pas | Dossier `data/<ns>/advancement/` (au singulier en 1.21), ou `trainer`/`category` qui ne correspond à aucun ID chargé. `/cobblemontrainers defeat <id>` le vérifie en une commande |
| Aucune récompense à la victoire | `rewards` absent, `"firstWinOnly": true` sur une victoire qui n'est pas la première, ou objet introuvable |
| Le dresseur manque dans `/cobblemontrainers list` | `"listed": false`, ou pas chargé du tout - `/cobblemontrainers spawn` le dira |
| Le combat en double est refusé | Moins de 2 Pokémon d'un des deux côtés, le tien compris |
| Une stat EV/IV semble ignorée | Nom de stat hors de la liste reconnue |
| Aucune musique | Piste absente du resource pack, ou ID qui ne correspond pas à la clé de `sounds.json` |
| Le skin reste Steve | Pseudo inexistant ou API Mojang injoignable - voir les logs |
| Le skin `texture` reste par défaut | Chemin qui ne correspond à aucun fichier (`.png` compris), ou pack hors de `mods/` |

## Un exemple complet

Le dépôt contient un pack couvrant chaque option, dresseur par dresseur :
[`examples/cobblemonrlm/`](../examples/cobblemonrlm). Zippe son contenu, renomme en `.jar`,
pose-le dans `mods/` : c'est tout.

Il montre entre autres une ligue complète : trois champions dans la catégorie `champions`,
dont un verrouillé derrière le premier et un objet, un dernier caché jusqu'à ce que les deux
autres soient tombés, et deux advancements branchés sur les victoires.
