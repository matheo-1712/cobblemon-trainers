# Writing an intro

A trainer can be announced by a **versus screen** before their battle: both fighters walk on, a
VS drops between them, and the battle music starts there. That screen is written in a datapack,
one layer at a time, and needs no code at all.

*Cette page existe aussi [en français](../INTROS.md).*

## Contents

- [Where it lives](#where-it-lives) · [The file](#the-file) · [A layer](#a-layer)
- [The catalogue](#the-catalogue): [figure](#figure) · [text](#text) · [image](#image) ·
  [fill](#fill) · [vs](#vs) · [team_balls](#team_balls) · [pokemon](#pokemon)
- [The intros that ship](#the-intros-that-ship) · [The textures that ship](#the-textures-that-ship) ·
  [Placing a layer](#placing-a-layer) ·
  [Timing](#timing) · [Sounds](#sounds)
- [The full example](#the-full-example) · [Common mistakes](#common-mistakes)

## Where it lives

An intro is a file of its own, **shared by every trainer that names it**: a whole league can
walk on the same way without copying it six times.

```
my_pack/
├── pack.mcmeta
└── data/my_pack/
    ├── cobblemontrainers/champions/erika.json   ← "intro": "my_pack:gym"
    └── cobblemontrainers_intros/gym.json        ← the intro itself
```

The trainer names it in their `battle` block:

```json
"battle": {
  "music": "my_pack:battle_music.champion",
  "intro": "my_pack:gym"
}
```

Without a namespace it is one of the mod's: `"intro": "bw"` means `cobblemon-trainers:bw`, one
of the [eight that ship](#the-intros-that-ship). No `intro` field at all, and the battle simply opens - which is the
default, and what every route trainer wants.

## The file

| Field | Default | Role |
| --- | --- | --- |
| `duration` | `100` | How long the screen stays up, in ticks (20 a second), 20 to 200 |
| `fadeIn` | `4` | Ticks the screen takes to arrive over the world |
| `fadeOut` | `8` | Ticks it takes to hand it back |
| `layers` | `[]` | The layers, **drawn in the order written**: the first one is behind |

The duration belongs to the intro rather than to the trainer: it is the intro that knows how
much time its layers need.

## A layer

These fields apply to every type.

| Field | Default | Role |
| --- | --- | --- |
| `type` | - | `figure`, `text`, `image`, `fill`, `vs`, `team_balls`, `pokemon` |
| `anchor` | `center` | Which point of the screen the layer puts its **centre** on |
| `offset` | `[0, 0]` | Where it goes from there, `[x, y]`, in reference pixels |
| `at` | `0` | The tick its entrance starts on |
| `for` | `12` | How many ticks that entrance lasts |
| `from` | `fade` | `left`, `right`, `top`, `bottom`, `fade`, `pop`, `none` |
| `ease` | `out` | `out` (arrives slowing down), `in`, `linear` |
| `alpha` | `1` | Its opacity once it has landed |
| `sound` | - | A sound played **once**, on tick `at` |
| `volume` / `pitch` | `1` / `1` | For that sound |

`from` is the entrance: the four sides slide the layer in from off screen, `fade` brings it up,
`pop` grows it past its size and settles, and `none` simply puts it there.

## The catalogue

### `figure`

The **3D model** of a trainer or of the player - the very one standing in the world.

| Field | Default | Role |
| --- | --- | --- |
| `who` | `trainer` | `trainer` or `player` |
| `height` | `96` | How tall it stands, in reference pixels |
| `yaw` | `0` | How far it is turned. `0` faces the player |
| `tilt` | `0` | How far the view leans |

When the entity is not there - rare, the player being in front of it - the mod draws the skin
flat instead, and failing that a silhouette.

**The trainer's floating name is held down** for as long as the screen is up, on both sides of
the glass: Cobblemon shows it whenever the player is looking at the NPC - which a player who has
just accepted a battle certainly is - so it would land over the figure with nobody having placed
it there. A `text` layer is how you name someone, and it is available for the player too.

### `text`

| Field | Default | Role |
| --- | --- | --- |
| `value` | - | The words, or a translation key |
| `size` | `1` | Scale factor, 1 being the game's own font |
| `color` | `#FFFFFF` | |
| `shadow` | `true` | The font's drop shadow |

`value` takes five placeholders, filled in as it is drawn:

| Placeholder | What it gives |
| --- | --- |
| `%name%` | The trainer's name |
| `%category%` | Their category's name, empty for a trainer at the root |
| `%level%` | Their `battle.level` |
| `%team%` | How many Pokémon they field |
| `%player%` | The player's name |

With no placeholder, `value` is translated like every other string a pack writes; with one,
what comes out is shown as it is.

### `image`

| Field | Default | Role |
| --- | --- | --- |
| `texture` | - | `my_pack:textures/gui/intro/logo.png`, **required** |
| `width` / `height` | the file's size | The drawn size, in reference pixels |
| `color` | `#FFFFFF` | A tint laid over the image |

The image is read **by the client**: it lives under `assets/`, so the pack goes in `mods/` or
doubles as a resource pack. Same rule as the music.

### `fill`

| Field | Default | Role |
| --- | --- | --- |
| `color` | `#000000` | |
| `width` / `height` | the whole screen | In reference pixels |
| `slant` | `0` | How far the band leans over its own height. `0` is a rectangle |

The screen's backdrop is a `fill` like any other: nothing is reserved for the mod.

### `vs`

| Field | Default | Role |
| --- | --- | --- |
| `size` | `1` | Scale factor. `4` is what the mod's own intro uses |
| `color` | `#FFF0C0` | |

A layer of its own rather than a big `text`: the mod draws its outline.

### `team_balls`

The row of Poké Balls: it says **how many** Pokémon, never which.

| Field | Default | Role |
| --- | --- | --- |
| `who` | `trainer` | `trainer` or `player` |
| `size` | `1` | Scale factor for one ball |
| `gap` | `3` | Reference pixels between two of them |
| `slots` | `6` | How many slots are laid out |
| `empty` | `true` | Whether the unfilled slots are dimmed, or left out |

### `pokemon`

The model of one Pokémon of the trainer's team, as the battle phone draws it - regional form
and shiny included.

| Field | Default | Role |
| --- | --- | --- |
| `slot` | `1` | Which one, from 1 |
| `height` | `64` | How tall it is drawn, in reference pixels |
| `yaw` | `0` | How far it is turned |

**This shows the team before the battle.** Keep it for the boss whose legendary is the point.

## The intros that ship

Eight come with the mod. Seven are **one trainer's own screen** - their emblem, their colours,
the way they walk on - and the eighth, `bw`, is the plain one, for when nothing in particular
needs saying. Any pack may name them: `"intro": "kagumi"` is enough, no namespace.

| Intro | Trainer | What it says |
| --- | --- | --- |
| `bw` | anything | Blue and red bands, both sides sliding in, a VS that drops |
| `rerebleue` | RereBleue | The manor: a night under the moon, lit windows, the mark of the seven witches |
| `kagumi` | Kagumi | The cherry tree: a blossom in watermark, petals coming down, slow throughout |
| `griff501` | Griff501 | The frog: he fades in through a blue halo, two shuriken cross, an acid streak |
| `octavien29` | Octavien29 | The board: a chequered field, and pieces put down one at a time |
| `theazertor` | TheAzertor | The holiday: the steel star in sunglasses, sea and sunset, still with no easing |
| `aeliothys` | Aeliothys | The stage: the two rise from below, a rabbit and its gem, all in pink |
| `ultra_rerebleue` | Ultra-RereBleue | The rift: a vortex opens behind her, she comes through it, four shards fall in from everywhere |

Three conventions hold them together, and are worth copying:

- **The left band is always the same blue.** Left is you, in all eight; it is the right that
  changes from trainer to trainer.
- **The geometry never moves**: figures at ±128, names above the heads, Poké Balls under the
  feet. What varies is colour, texture, which way things come in and how long they take - enough
  that two gym leaders do not look alike, not so much that the screen has to be read afresh.
- **The textures are white, and tinted by the layer.** One image therefore dresses six screens
  in six colours.

## The textures that ship

They live in the jar, under `cobblemon-trainers:textures/gui/intro/`, and any pack may name
them. All are **white on transparent**: the layer's `color` is what gives them their hue.

| Texture | Size | What it is |
| --- | --- | --- |
| `rays.png` | 256 | A disc of rays, hollow at the centre so a figure is not washed out |
| `burst.png` | 128 | The impact flash, eight points, made to sit under the VS |
| `slash.png` | 256 × 32 | One tapered stroke, for the screens that cut rather than slide |
| `banner.png` | 192 × 32 | A plate with bevelled ends, to slide under a name |
| `crest_<trainer>.png` | 128 | A trainer's emblem: heptagram, cherry blossom, frog, chess rook, star in shades, rabbit |
| `petal.png` | 64 | A cherry petal, to be dropped in handfuls |
| `shuriken.png` | 96 | A blade, to be thrown in from the wings |
| `grid.png` | 256 | A chequered field, fading out before the edges |
| `moon.png` | 128 | A full moon and its halo |
| `stars.png` | 256 | A sky |
| `scene_manor.png` | 384 × 160 | A manor at night: the silhouette alone |
| `scene_manor_lights.png` | 384 × 160 | Its lit windows, at the same place and the same size |
| `ultra_rift.png` | 256 | A rift: twelve crystalline rings turning towards a core |
| `ultra_shard.png` | 128 | A crystal splinter, to be dropped in handfuls |

`ultra_rerebleue` has no emblem: she has a **rift**. The vortex opens behind her, she comes
through it (`pop`), and four prismatic shards drop in from four sides at four different
moments - it is the only one of the eight that refuses symmetry, and that is the point.

**None of the eight uses a `pokemon` layer**, deliberately: the mod never shows a team before
the battle. The layer is there for the packs that want it, not for us.

An emblem belongs behind its trainer as a watermark - `alpha` around `0.3`, sized 180 to 230 -
rather than in full light: what has to read is the figure.

**Two layers for two colours.** A texture is white and takes a single tint, so a backdrop that
needs two is drawn as two images laid at the **same place and the same size**, with the same
entrance and the same timings. That is what `scene_manor.png` and `scene_manor_lights.png` do
for RereBleue: the house in dark indigo, its windows in gold.

```json
{ "type": "image", "texture": "cobblemon-trainers:textures/gui/intro/crest_kagumi.png",
  "width": 200, "height": 200, "color": "#FFC2DC", "alpha": 0.26,
  "offset": [128, -8], "at": 4, "for": 24, "from": "right" }
```

## Placing a layer

An intro is written on a **640 × 360** screen, and the mod scales sizes and offsets to the
player's window. An intro written on a large screen therefore still holds on a small one.

`anchor` takes one of nine points:

```
top-left      top      top-right
left        center         right
bottom-left  bottom  bottom-right
```

The anchor lands on the window's **real** edges, unscaled: a logo in `top-right` stays in the
corner at any size. `offset` goes from there.

## Timing

Everything is counted in ticks, 20 a second, from the moment the screen goes up.

- `at` is when a layer starts coming in, `for` how long that takes.
- **The exit is shared**: `fadeOut` takes the whole screen away at once.
- The player may skip **once the last entrance has landed** - the largest `at + for` of all the
  layers. Before that nothing answers: a held key repeats, and nobody should skip a screen they
  have not seen.
- The trainer's battle music starts when the screen goes up, not on the first turn.

## Sounds

A layer may name a sound, played once as its entrance starts:

```json
{ "type": "vs", "at": 18, "for": 6, "from": "pop", "sound": "my_pack:intro.impact" }
```

It is a UI sound: it follows the **Master** slider rather than the music one, which already
carries the battle theme. Like the music, it is named by its `sounds.json` key and therefore
lives under `assets/`.

## The full example

`bw`, the intro the mod ships, is written in this format and in no other - which makes it the
best thing to copy:

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

## Common mistakes

| Symptom | Cause |
| --- | --- |
| No screen at all, the battle just opens | The trainer names an intro no pack provides - the log says so at load |
| A layer is missing | Its `type` is unknown, or an `image` has no `texture`, `width` or `height`: it is dropped at load, with a line in the log |
| The image never shows | It sits under `data/` instead of `assets/`, or the pack is not one the client reads |
| The sound is never heard | It is named by the file path instead of the `sounds.json` key |
| Everything arrives at once | The layers have no `at`: without one, they all come in on tick 0 |
| Nothing lines up on another screen | Sizes are pixels of **640 × 360**, scaled afterwards - not pixels of your own window |
