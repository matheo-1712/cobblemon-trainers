# Écrire une intro

Un dresseur peut être annoncé par un **écran de versus** avant son combat : les deux
combattants entrent, un VS tombe entre eux, la musique de combat démarre là. Cet écran est
écrit en datapack, calque par calque, et n'a besoin d'aucun code.

*This page is also available [in English](en/INTROS.md).*

## Sommaire

- [Où ça vit](#où-ça-vit) · [Le fichier](#le-fichier) · [Un calque](#un-calque)
- [Le catalogue](#le-catalogue) : [figure](#figure) · [text](#text) · [image](#image) ·
  [fill](#fill) · [vs](#vs) · [team_balls](#team_balls) · [pokemon](#pokemon)
- [Les intros livrées](#les-intros-livrées) · [Placer un calque](#placer-un-calque) ·
  [Le temps](#le-temps) · [Les sons](#les-sons)
- [L'exemple complet](#lexemple-complet) · [Erreurs fréquentes](#erreurs-fréquentes)

## Où ça vit

Une intro est un fichier à elle, que **tous les dresseurs qui la nomment se partagent** :
une ligue entière peut avoir la même entrée sans la recopier six fois.

```
mon_pack/
├── pack.mcmeta
└── data/mon_pack/
    ├── cobblemontrainers/champions/jacinthe.json   ← "intro": "mon_pack:arene"
    └── cobblemontrainers_intros/arene.json         ← l'intro elle-même
```

Le dresseur la nomme dans son bloc `battle` :

```json
"battle": {
  "music": "mon_pack:battle_music.champion",
  "intro": "mon_pack:arene"
}
```

Sans namespace, c'est une intro du mod : `"intro": "bw"` donne
`cobblemon-trainers:bw`, l'une des [sept livrées](#les-intros-livrées). Pas de champ `intro` du tout, et le combat
s'ouvre directement - c'est le cas par défaut, et celui de tous les dresseurs de route.

## Le fichier

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `duration` | `100` | Durée de l'écran, en ticks (20 par seconde), de 20 à 200 |
| `fadeIn` | `4` | Ticks pendant lesquels l'écran arrive sur le monde |
| `fadeOut` | `8` | Ticks pendant lesquels il le rend |
| `layers` | `[]` | Les calques, **dessinés dans l'ordre écrit** : le premier est derrière |

La durée appartient à l'intro, pas au dresseur : c'est elle qui sait de combien de temps ses
calques ont besoin.

## Un calque

Ces champs valent pour tous les types.

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `type` | - | `figure`, `text`, `image`, `fill`, `vs`, `team_balls`, `pokemon` |
| `anchor` | `center` | Le point de l'écran sur lequel le calque pose son **centre** |
| `offset` | `[0, 0]` | Où il va depuis là, `[x, y]`, en pixels de référence |
| `at` | `0` | Le tick où son entrée commence |
| `for` | `12` | Combien de ticks elle dure |
| `from` | `fade` | `left`, `right`, `top`, `bottom`, `fade`, `pop`, `none` |
| `ease` | `out` | `out` (arrive en ralentissant), `in`, `linear` |
| `alpha` | `1` | Son opacité une fois posé |
| `sound` | - | Un son joué **une fois**, au tick `at` |
| `volume` / `pitch` | `1` / `1` | Pour ce son |

`from` décide de l'entrée : les quatre côtés font glisser le calque depuis le hors-champ,
`fade` le fait apparaître, `pop` le fait grossir jusqu'à sa taille en la dépassant un peu, et
`none` le pose sans façon.

## Le catalogue

### `figure`

Le **modèle 3D** d'un dresseur ou du joueur, celui-là même qui se tient dans le monde.

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `who` | `trainer` | `trainer` ou `player` |
| `height` | `96` | Sa hauteur, en pixels de référence |
| `yaw` | `0` | De combien il est tourné. `0` regarde le joueur |
| `tilt` | `0` | De combien la vue penche |

Si l'entité n'est pas là - un cas rare, le joueur étant devant elle -, le mod dessine le skin à
plat, et à défaut une silhouette.

**Le nom flottant du dresseur est masqué** tant que l'écran est levé, des deux côtés de la vitre :
Cobblemon l'affiche dès qu'on regarde le PNJ - ce que fait forcément un joueur qui vient
d'accepter -, et il tomberait sur la figure sans que personne l'ait placé là. Un calque `text`
est la façon de nommer quelqu'un, et il est aussi disponible pour le joueur.

### `text`

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `value` | - | Le texte, ou une clé de traduction |
| `size` | `1` | Facteur d'échelle, 1 étant la police du jeu |
| `color` | `#FFFFFF` | |
| `shadow` | `true` | L'ombre portée de la police |

`value` accepte cinq marques, remplacées à l'affichage :

| Marque | Ce qu'elle donne |
| --- | --- |
| `%name%` | Le nom du dresseur |
| `%category%` | Le nom de sa catégorie, vide s'il est à la racine |
| `%level%` | Son `battle.level` |
| `%team%` | Combien de Pokémon il aligne |
| `%player%` | Le nom du joueur |

Sans marque, `value` est traduit comme les autres textes d'un pack ; avec, ce qui en sort est
affiché tel quel.

### `image`

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `texture` | - | `mon_pack:textures/gui/intro/logo.png`, **obligatoire** |
| `width` / `height` | taille du fichier | La taille dessinée, en pixels de référence |
| `color` | `#FFFFFF` | Une teinte appliquée à l'image |

L'image est lue **par le client** : elle vit sous `assets/`, donc le pack va dans `mods/` ou
double d'un resource pack. C'est la même règle que la musique.

### `fill`

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `color` | `#000000` | |
| `width` / `height` | tout l'écran | En pixels de référence |
| `slant` | `0` | De combien la bande penche sur sa hauteur. `0` est un rectangle |

Le fond de l'écran est un `fill` comme un autre : rien n'est réservé au mod.

### `vs`

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `size` | `1` | Facteur d'échelle. `4` est la taille de l'intro du mod |
| `color` | `#FFF0C0` | |

Un calque à lui plutôt qu'un `text` agrandi : c'est le mod qui dessine son contour.

### `team_balls`

La rangée de Poké Balls : elle dit **combien** de Pokémon, jamais lesquels.

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `who` | `trainer` | `trainer` ou `player` |
| `size` | `1` | Facteur d'échelle d'une Ball |
| `gap` | `3` | L'écart entre deux, en pixels de référence |
| `slots` | `6` | Combien d'emplacements sont posés |
| `empty` | `true` | Les emplacements non remplis sont assombris, ou absents |

### `pokemon`

Le modèle d'un Pokémon de l'équipe du dresseur, comme dans la fiche du Battle Phone - forme
régionale et chromatique comprises.

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `slot` | `1` | Le rang dans l'équipe, à partir de 1 |
| `height` | `64` | Sa hauteur, en pixels de référence |
| `yaw` | `0` | De combien il est tourné |

**Ça montre l'équipe avant le combat.** À réserver au boss dont le légendaire est l'argument.

## Les intros livrées

Le mod en fournit sept, utilisables par n'importe quel pack : `"intro": "bw"`, ou son nom sans
namespace, suffit. Six d'entre elles habillent un dresseur iconique du mod, et prennent les
couleurs du biome où il répond.

| Intro | Ce qu'elle fait | Portée par |
| --- | --- | --- |
| `bw` | La neutre : bandes bleue et rouge, glissement des deux côtés, VS qui tombe | à tout faire |
| `plains` | La même en vert de prairie, un peu plus franche | RereBleue |
| `cherry` | Rose, penchée dans l'autre sens, tout en lenteur | Kagumi |
| `desert` | Colonnes droites, sans amorti, la plus courte des sept | TheAzertor |
| `beach` | Un horizon turquoise derrière les deux, bandes très obliques | Octavien29 |
| `hills` | Tout tombe du haut de l'écran plutôt que des côtés | Griff501 |
| `jungle` | Rien ne glisse : les deux apparaissent, fond presque noir, or | Aeliothys |

Deux conventions les tiennent ensemble, et valent d'être copiées :

- **La bande de gauche est toujours du même bleu.** La gauche, c'est toi, dans les sept ; c'est
  la couleur de droite qui change de dresseur en dresseur.
- **La géométrie ne bouge pas** : figures à ±128, noms au-dessus des têtes, Poké Balls sous les
  pieds. Ce qui varie est la couleur, la pente, le sens d'entrée et le temps - assez pour que
  deux champions ne se ressemblent pas, pas assez pour qu'on ait à réapprendre à lire l'écran.

Les copier est le meilleur point de départ : `plains` est `bw` recolorée, et les cinq autres
sont chacune une seule idée de plus.

## Placer un calque

Une intro s'écrit sur un écran de **640 × 360**, et le mod met tailles et décalages à l'échelle
de la fenêtre du joueur. Une intro écrite sur un grand écran tient donc sur un petit.

`anchor` prend l'un des neuf points de l'écran :

```
top-left      top      top-right
left        center         right
bottom-left  bottom  bottom-right
```

L'ancre tombe sur les **vrais** bords de la fenêtre, sans mise à l'échelle : un logo en
`top-right` reste dans le coin quelle que soit la taille. `offset` part de là.

## Le temps

Tout se compte en ticks, 20 par seconde, depuis le lever de l'écran.

- `at` est le moment où un calque commence à entrer, `for` la durée de cette entrée.
- La **sortie est commune** : `fadeOut` emmène tout l'écran d'un coup.
- Le joueur peut passer l'écran **une fois la dernière entrée finie** - le plus grand
  `at + for` de tous les calques. Avant, rien ne répond : une touche maintenue se répète, et
  personne ne doit sauter un écran qu'il n'a pas vu.
- La musique de combat du dresseur part au lever de l'écran, pas au premier tour.

## Les sons

Un calque peut nommer un son, joué une fois quand son entrée commence :

```json
{ "type": "vs", "at": 18, "for": 6, "from": "pop", "sound": "mon_pack:intro.impact" }
```

C'est un son d'interface : il suit le curseur **Principal**, pas celui de la musique, qui porte
déjà le thème de combat. Comme la musique, il est nommé par sa clé de `sounds.json` et vit donc
sous `assets/`.

## L'exemple complet

`bw`, l'intro livrée avec le mod, est écrite dans ce format et dans aucun autre - c'est le
meilleur modèle à copier :

```json
{
  "duration": 100,
  "fadeIn": 4,
  "fadeOut": 8,
  "layers": [
    { "type": "fill", "color": "#05060D", "alpha": 0.88, "from": "fade", "for": 4 },

    { "type": "fill", "color": "#2F6FBF", "alpha": 0.55, "slant": 0.35,
      "width": 128, "offset": [-128, 0], "from": "left", "for": 18 },
    { "type": "fill", "color": "#BF3A3A", "alpha": 0.55, "slant": 0.35,
      "width": 128, "offset": [128, 0], "from": "right", "for": 18 },

    { "type": "fill", "color": "#FFF6D8", "alpha": 0.9, "height": 4,
      "at": 18, "for": 6, "from": "pop" },

    { "type": "figure", "who": "player", "height": 150, "yaw": 22,
      "offset": [-128, 0], "from": "left", "for": 18 },
    { "type": "figure", "who": "trainer", "height": 150, "yaw": -22,
      "offset": [128, 0], "from": "right", "for": 18 },

    { "type": "text", "value": "%player%", "offset": [-128, -95],
      "at": 18, "for": 6, "from": "fade" },
    { "type": "text", "value": "%name%", "offset": [128, -95],
      "at": 18, "for": 6, "from": "fade" },

    { "type": "team_balls", "who": "player", "size": 1.5, "offset": [-128, 85],
      "at": 20, "for": 6, "from": "fade" },
    { "type": "team_balls", "who": "trainer", "size": 1.5, "offset": [128, 85],
      "at": 20, "for": 6, "from": "fade" },

    { "type": "vs", "size": 4, "at": 18, "for": 6, "from": "pop" }
  ]
}
```

## Erreurs fréquentes

| Symptôme | Cause |
| --- | --- |
| Aucun écran, le combat s'ouvre direct | Le dresseur nomme une intro qu'aucun pack ne fournit - le log le dit au chargement |
| Un calque manque | Son `type` est inconnu, ou une `image` sans `texture`, `width` ou `height` : il est retiré au chargement, avec une ligne dans le log |
| L'image ne s'affiche pas | Elle est sous `data/` au lieu d'`assets/`, ou le pack n'est pas lu par le client |
| Le son ne se fait pas entendre | Il est nommé par le chemin du fichier au lieu de la clé de `sounds.json` |
| Tout arrive en même temps | Les calques n'ont pas de `at` : sans lui, tout entre au tick 0 |
| Rien ne bouge sur un autre écran | Les tailles sont des pixels de **640 × 360**, mis à l'échelle ensuite - pas des pixels de ta fenêtre |
