# Écrire une intro

Un dresseur peut être annoncé par un **écran de versus** avant son combat : les deux
combattants entrent, un VS tombe entre eux, la musique de combat démarre là. Cet écran est
écrit en datapack, calque par calque, et n'a besoin d'aucun code.

*This page is also available [in English](en/INTROS.md).*

## Sommaire

- [Où ça vit](#où-ça-vit) · [Le fichier](#le-fichier) · [Un calque](#un-calque)
- [Le catalogue](#le-catalogue) : [figure](#figure) · [text](#text) · [image](#image) ·
  [fill](#fill) · [vs](#vs) · [team_balls](#team_balls) · [pokemon](#pokemon)
- [Les intros livrées](#les-intros-livrées) · [Les textures livrées](#les-textures-livrées) ·
  [Placer un calque](#placer-un-calque) ·
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
`cobblemon-trainers:bw`, l'une des [huit livrées](#les-intros-livrées). Pas de champ `intro` du tout, et le combat
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

Le mod en fournit huit. Sept sont **l'écran d'un dresseur** - son emblème, ses couleurs, sa
façon d'entrer - et la huitième, `bw`, est la neutre, celle qu'on prend quand on ne veut rien
dire de particulier. Toutes sont nommables par un pack : `"intro": "kagumi"`
suffit, sans namespace.

| Intro | Dresseur | Ce qu'elle raconte |
| --- | --- | --- |
| `bw` | à tout faire | Bandes bleue et rouge, glissement des deux côtés, VS qui tombe |
| `rerebleue` | RereBleue | Le manoir : une nuit sous la lune, les fenêtres allumées, la marque des sept sorcières |
| `kagumi` | Kagumi | Le cerisier : une fleur en filigrane, des pétales qui tombent, tout en lenteur |
| `griff501` | Griff501 | La grenouille : il se matérialise dans un halo bleu, deux shuriken passent, éclair acide |
| `octavien29` | Octavien29 | Le plateau : un damier, et des pièces posées une par une |
| `theazertor` | TheAzertor | Les vacances : l'étoile d'acier en lunettes de soleil, mer et couchant, toujours sans amorti |
| `aeliothys` | Aeliothys | La scène : les deux montent par le bas, un lapin et sa gemme, tout en rose |
| `ultra_rerebleue` | Ultra-RereBleue | L'ultra-brèche : un vortex s'ouvre derrière elle, elle en sort, quatre éclats tombent de partout |

Trois conventions les tiennent ensemble, et valent d'être copiées :

- **La bande de gauche est du même bleu partout.** La gauche, c'est toi, dans les huit ; c'est
  la droite qui change de dresseur en dresseur.
- **La géométrie ne bouge pas** : figures à ±128, noms au-dessus des têtes, Poké Balls sous les
  pieds. Ce qui varie est la couleur, la texture, le sens d'entrée et le temps - assez pour que
  deux champions ne se ressemblent pas, pas assez pour qu'on ait à réapprendre à lire l'écran.
- **Les textures sont blanches et teintées par le calque.** Une seule image sert donc à six
  écrans de six couleurs.

## Les textures livrées

Elles vivent dans le jar, sous `cobblemon-trainers:textures/gui/intro/`, et n'importe quel pack
peut les nommer. Toutes sont **blanches sur fond transparent** : c'est le `color` du calque qui
leur donne leur teinte.

| Texture | Taille | Ce que c'est |
| --- | --- | --- |
| `rays.png` | 256 | Un disque de rayons, creux au centre pour ne pas noyer une figure |
| `burst.png` | 128 | L'éclat d'impact, huit branches, à poser sous le VS |
| `slash.png` | 256 × 32 | Un trait effilé, pour les écrans qui tranchent au lieu de glisser |
| `banner.png` | 192 × 32 | Une plaque à bouts biseautés, à glisser sous un nom |
| `crest_<dresseur>.png` | 128 | L'emblème d'un dresseur : heptagramme, fleur de cerisier, grenouille, tour d'échecs, étoile à lunettes, lapin |
| `petal.png` | 64 | Un pétale de cerisier, à faire tomber par poignées |
| `shuriken.png` | 96 | Une lame, à lancer depuis les coulisses |
| `grid.png` | 256 | Un damier qui s'efface avant les bords |
| `moon.png` | 128 | Une pleine lune et son halo |
| `stars.png` | 256 | Un ciel |
| `scene_manor.png` | 384 × 160 | Un manoir la nuit : la silhouette seule |
| `scene_manor_lights.png` | 384 × 160 | Ses fenêtres allumées, au même endroit et à la même taille |
| `ultra_rift.png` | 256 | Une brèche : douze anneaux cristallins qui tournent vers un cœur |
| `ultra_shard.png` | 128 | Un éclat de cristal, à faire tomber par poignées |

`ultra_rerebleue` n'a pas d'emblème : elle a une **brèche**. Le vortex s'ouvre derrière elle,
elle en sort (`pop`), et quatre éclats prismatiques tombent des quatre côtés à quatre moments
différents - c'est la seule des huit qui refuse la symétrie, et c'est le sujet.

**Aucune des huit ne pose de calque `pokemon`**, et c'est voulu : le mod ne montre jamais une
équipe avant le combat. Le calque existe pour les packs qui le veulent, pas pour nous.

Un emblème se pose en filigrane derrière son dresseur - `alpha` autour de `0.3`, taille de 180
à 230 - plutôt qu'en pleine lumière : ce qui doit se lire, c'est la figure.

**Deux calques pour deux teintes.** Une texture est blanche et prend une seule couleur, donc un
décor qui en demande deux se dessine en deux images posées **au même endroit et à la même
taille**, avec la même entrée et les mêmes temps. C'est ce que font `scene_manor.png` et
`scene_manor_lights.png` chez RereBleue : la maison en indigo sombre, ses fenêtres en or.

```json
{ "type": "image", "texture": "cobblemon-trainers:textures/gui/intro/crest_kagumi.png",
  "width": 200, "height": 200, "color": "#FFC2DC", "alpha": 0.26,
  "offset": [128, -8], "at": 4, "for": 24, "from": "right" }
```

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
