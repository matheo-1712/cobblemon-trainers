<p align="center">
  <img src="https://raw.githubusercontent.com/matheo-1712/cobblemon-trainers/refs/heads/master/assets/logo.png" alt="Cobblemon Trainers">
</p>

<p align="center">
  <strong>Add real Pokémon trainers to your Cobblemon world - each one a single JSON file.</strong>
</p>

Drop the mod in, and you can populate your world with custom trainers: a full Showdown
team, a Minecraft skin, custom dialogue, battle music, rewards, and a difficulty setting.
No code, no recompiling, no in-game editor. Just a datapack and `/reload`. Then pin a trainer
to a spot with a spawner block, and it stays there for good.

Works in singleplayer and on servers. Trainers run server-side, but the mod is needed on the
client as well - install it on both.

---

## What you get

- **Real trainer battles.** Right-click a trainer, get a proper Cobblemon battle in
  singles, doubles or triples - with battle music playing over everything else.
- **Trainers that stay where you put them.** An invisible spawner block holds one trainer in
  place: kill it and it comes back, walk it away and it returns. Configure it by right-clicking,
  and it keeps its settings inside a structure.
- **Showdown teams, pasted in.** Copy an export straight from Pokémon Showdown: items,
  abilities, natures, EVs/IVs, moves, shininess, nicknames. Unknown lines are ignored, so
  it just works.
- **Any skin you like.** Borrow a Minecraft account's skin by username or UUID, or ship
  your own PNG in the pack. Custom textures are sent by the server, so every player sees
  them.
- **Rewards and one-time bosses.** Hand out items on victory, once or every time. Make a
  champion a single encounter per player with `"rematch": "never"`.
- **Progress tracking, in game.** The Battle Phone item gives every player their own board:
  one tab per pack, a heading per category, each trainer's skin - and their team, once beaten.
  Operators get the same from `/cobblemontrainers list`. Progress is saved with the world and
  survives
  `/reload` and restarts.
- **Leagues, not just trainers.** Sort trainers into categories with folders, lock one behind
  another - a badge, an advancement, eight victories - and hang ordinary Minecraft advancements
  off any win.
- **Adjustable AI.** Skill from 0 (plays randomly) to 5 (plays to win), independent from
  Pokémon level.
- **Regional forms, fakemon.** An `Aspects:` line handles any Cobblemon form,
  including ones added by other packs.
- **Translatable text.** Trainer names and dialogue accept translation keys, so every
  player reads them in their own language - floating nameplate included.

---

## Requirements

| | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Mod loader | Fabric (Loader ≥ 0.17.2) |
| Java | 21 - exactly, Cobblemon refuses anything else |
| Cobblemon | ≥ 1.7.3 |
| Fabric API | required |
| Fabric Language Kotlin | required |

## Installation

Put `cobblemon-trainers-<version>.jar` in your `mods/` folder, next to Cobblemon, Fabric API
and Fabric Language Kotlin. That's it - the mod ships two demo trainers you can spawn right
away.

### Built-in trainers

Four ready-to-use trainers ship with the mod, in their own **Iconic Trainers** category, so you
can test everything before writing a single JSON file:

| ID | Name | Level | Format | Team |
| --- | --- | --- | --- | --- |
| `cobblemon-trainers:iconic_trainers/rerebleue` | **RereBleue** | 80 | Singles | Secret |
| `cobblemon-trainers:iconic_trainers/theazertor` | **TheAzertor** | 80 | Singles | Secret |
| `cobblemon-trainers:iconic_trainers/octavien29` | **Octavien29** | 80 | Singles | Secret |
| `cobblemon-trainers:iconic_trainers/kagumi` | **Kagumi** | 80 | Singles | Secret |

```
/cobblemontrainers spawn cobblemon-trainers:iconic_trainers/rerebleue
/cobblemontrainers spawn rerebleue
```

The bare file name is enough - the command finds the folder. All four play at maximum AI skill
(`"difficulty": 5`) with fully built competitive teams: a real fight, and a reference for your
own files.

## Commands

```
/cobblemontrainers spawn <id>
/cobblemontrainers spawn <id> <x> <y> <z>
/cobblemontrainers list [<player>]
```

Both require permission level 2 (operator). Autocomplete lists every loaded trainer under its
full `<pack>:<trainer>` ID, and searches both halves - typing `jac` finds `my_pack:jacinthe`.

---

## Trainers that stay put

A trainer summoned with `/cobblemontrainers spawn` is gone the moment something kills it. For
a trainer
that has to hold a post - a gym leader, the NPC at spawn - there is the **trainer spawner
block**, `cobblemon-trainers:trainer_spawner`. It remembers which trainer belongs there and
puts it back whenever it is missing.

The block is **invisible, exactly like a barrier**: it only shows up as markers while you hold
its item. Find it in the **Cobblemon Trainers** creative tab, or:

```
/give @s cobblemon-trainers:trainer_spawner
```

Place it where the trainer should stand - it has no collision, the trainer stands inside it -
and **right-click** to open its screen. Pick a trainer from the list of everything loaded, or
type an ID; set how far it may wander before being pulled back, and how long it waits before
coming back from the dead.

Then it takes care of itself:

- summons its trainer as soon as it is set, and again whenever one goes missing;
- a killed trainer returns after the delay (30 seconds by default);
- a trainer that strays too far is put back on its block at full health, the way a freshly
  spawned one would arrive. Its Pokémon team is left as it is - that stays `battle.healParty`'s
  call. A trainer mid-battle is left alone;
- breaking the block takes its trainer with it;
- **a configured block keeps its settings inside a structure.** Save a building with one in it
  and every copy comes out set to the same trainer - and summons its own, not the original's.
  Placed rotated or mirrored, the trainer turns with the structure.

It is an operator block, like a command block: **everything it does needs operator rights and
creative mode**. Without them the item places nothing, the block cannot be broken, no markers
appear, the crosshair passes straight through it, and right-click does nothing. Opening the
screen also needs the mod installed client-side - everything else works with a vanilla client.

---

## The Battle Phone

`/cobblemontrainers list` is for operators. The **Battle Phone**
(`cobblemon-trainers:battle_phone`) is
the same board for everyone else: right-click it and every listed trainer of the world is
there, one tab per datapack, a heading per category, each with its skin and its state - beaten,
beaten for good, still standing, or locked with the list of what's missing.

Click a line and the trainer fills the right-hand page: level, team size, and six slots that
stay shut until you win, then show their Pokémon's models, forms and shinies included. The item
stores nothing - it reads the server - so losing it loses nothing. Find it in the
**Cobblemon Trainers** creative tab, or:

```
/give @s cobblemon-trainers:battle_phone
```

---

## Making a trainer

One trainer is one JSON file in a datapack. The folder it sits in is its **category**, and its
path is its **ID**:

```
my_pack/
├── pack.mcmeta
└── data/my_pack/                    ← your namespace
    └── cobblemontrainers/
        ├── red.json                 → my_pack:red
        └── champions/               ← a folder is a category
            ├── category.json        ← optional: display name and order
            └── erika.json           → my_pack:champions/erika
```

`data/my_pack/cobblemontrainers/red.json`:

```json
{
  "name": "Red",
  "skin": { "type": "player_username", "value": "Red" },
  "battle": {
    "level": 88,
    "difficulty": 5,
    "music": "my_pack:battle_music.finale"
  },
  "messages": {
    "start": "Ready?",
    "win": "Well played.",
    "lose": "Come back when you are ready."
  },
  "progress": { "rematch": "never" },
  "rewards": [{ "item": "cobblemon:master_ball", "count": 1 }],
  "team": [
    "Pikachu (M) @ Light Ball\nAbility: Static\nLevel: 88\nShiny: Yes\nEVs: 252 SpA / 4 SpD / 252 Spe\nTimid Nature\n- Thunderbolt\n- Iron Tail",
    "Snorlax (M) @ Leftovers\nAbility: Thick Fat\nLevel: 88\n- Body Slam\n- Earthquake"
  ]
}
```

Then, in game:

```
/reload
/cobblemontrainers spawn my_pack:red
```

**Every field is optional** - a file containing just `{}` produces a valid (if boring) trainer.
A broken file is logged and skipped; the others still load.

### Field reference

Four blocks, each holding what its name says.

| Root field | Default | What it does |
| --- | --- | --- |
| `name` | `Trainer` | Name shown above the trainer |
| `skin` | Steve | Which face it wears, below |
| `team` | `[]` | The team, **one Pokémon per entry**, Showdown format |
| `battle` | - | How the fight goes |
| `messages` | - | What the trainer says |
| `progress` | - | What beating it is worth |
| `rewards` | `[]` | Items handed over on victory: `{ "item": "<full id>", "count": 1 }` |
| `requires` | - | What it takes to challenge it |

| `battle` | Default | What it does |
| --- | --- | --- |
| `level` | `1` | Level of the Pokémon with no `Level:` line of their own |
| `format` | `singles` | `singles`, `doubles`, `triples` - aliases `solo`, `duo`, `trio` |
| `difficulty` | `5` | Battle AI skill, 0 (random) to 5 (plays to win). Not the level |
| `healParty` | `true` | Heal the trainer's team before and after each battle |
| `music` | mod's track | Sound ID played during the battle, `null` for silence |

| `messages` | What it does |
| --- | --- |
| `start` | Sent when the battle begins |
| `win` | Sent when the player wins |
| `lose` | Sent when the player loses |

| `progress` | Default | Values | What it does |
| --- | --- | --- | --- |
| `rematch` | `unlimited` | `unlimited`, `never` | Whether it can be challenged again once beaten |
| `listed` | `true` | boolean | Shows up in the Battle Phone and `/cobblemontrainers list` |

| `rewards[]` | Default | What it does |
| --- | --- | --- |
| `item` | - | Full item ID, namespace included |
| `count` | `1` | How many, clamped to 1-6400 |
| `hidden` | `false` | Keep it off the Battle Phone - a surprise, still handed over |
| `firstWinOnly` | `false` | Drop it on the first victory only, so the rest stays farmable |

| `skin` | Default | What it does |
| --- | --- | --- |
| `type` | `player_username` | `player_username`, `player_uuid` or `texture` |
| `value` | `Steve` | A username, a UUID, or a PNG in your pack (`my_pack:textures/trainers/red.png`) |
| `model` | `default` | `default` (Steve) or `slim` (Alex) - `texture` only |

Victories are remembered **per trainer ID and per player**, in the world save: spawning ten
copies of one trainer still gives one battle, killing it changes nothing, and `/reload` doesn't
wipe the record.

### Categories

Ranking `erika.json` under `champions/` is all it takes - nothing to declare. Categories group
the Battle Phone and `/cobblemontrainers list`, and give requirements and advancements
something to aim
at. A `category.json` **inside the folder** names it and places it, both optional:

```json
{ "name": "category.my_pack.champions", "order": 1 }
```

Trainers left at the root form a last group under a title the mod provides, so a pack that uses
no category shows no headings at all.

### Locking a trainer

The `requires` block turns a trainer down until the player has done what it asks - and by
default hides it from the Battle Phone until then. Everything declared has to be met, and
nothing is ever consumed.

```json
"requires": {
  "defeated": ["champions/erika", "champions/brock"],
  "victories": { "count": 8, "category": "champions" },
  "items": [{ "item": "my_pack:boulder_badge", "count": 1 }],
  "advancement": "my_pack:league_access",
  "hidden": false,
  "message": "trainer.my_pack.champion.locked"
}
```

`victories` counts beaten trainers rather than naming them - narrow the pool with `pack` and/or
`category`, drop `count` to mean *all of them*. The trainer asking never counts towards itself,
so a champion may require "beat every champion". An ID that matches nothing counts as unmet: a
typo closes a trainer, it never opens one.

### Advancements

Beating a trainer fires the `cobblemon-trainers:trainer_defeated` criterion, so your league
rewards are ordinary advancements - your title, your icon, your tree.

```json
// data/my_pack/advancement/league_champion.json
"criteria": {
  "beaten": {
    "trigger": "cobblemon-trainers:trainer_defeated",
    "conditions": { "pack": "my_pack", "category": "champions", "count": 8 }
  }
}
```

Conditions are `trainer`, `category`, `pack` and `count`, all optional and cumulative. The count
is read from the saved progress, the same one `/cobblemontrainers list` shows.

---

## Shipping your pack

Three options, pick whichever suits your pack:

| Location | Accepted formats | Loads | Enabled |
| --- | --- | --- | --- |
| `mods/` | folder, `.zip`, `.jar` | `data/` **and** `assets/` | automatically, every world |
| `saves/<world>/datapacks/` | folder, `.zip`, `.jar` | `data/` | per world |
| `resourcepacks/` | folder, `.zip`, `.jar` | `assets/` | ticked in the options |

What decides is what's inside. Trainers alone (`data/`) go anywhere. Translations or battle
music (`assets/`) make `mods/` the only route that loads both halves from one file -
`datapacks/` is read as data and nothing else, so an `assets/` folder dropped there is never
read. And a `texture` skin **has** to be in `mods/`: the image is read by the server, which
looks nowhere else, and sent to every client - so players without your pack still see the
right face.

Any pack needs a `pack.mcmeta` at the root of the archive, and nothing else - no
`fabric.mod.json`, no code:

```json
{
  "pack": {
    "pack_format": 48,
    "supported_formats": { "min_inclusive": 34, "max_inclusive": 48 },
    "description": "My trainers"
  }
}
```

`pack_format` is 48 for 1.21.1 data and 34 for resources; an archive serving both sides declares
the range, otherwise the resource pack screen calls it incompatible. Two things this mod adds on
top of vanilla: `.jar` archives are accepted in all three locations (the game only knows folders
and `.zip`), and a pack in `mods/` loads with nothing but that `pack.mcmeta`.

The [full datapack guide](https://github.com/matheo-1712/cobblemon-trainers/blob/master/docs/DATAPACK.md)
(in French) covers every field, the team format, `Aspects:`, sounds and translations, with a
troubleshooting table.

---

## Known limitations

- Forms are declared through their aspects (`Aspects: alolan`), not the Showdown name
  suffix (`Raichu-Alola`) - a suffix can't be told apart from species whose name contains a
  hyphen (`Ho-Oh`, `Porygon-Z`).
- `player_username` and `player_uuid` skins are fetched from the Mojang API, so they need
  network access and an existing account. On failure the trainer keeps the default skin and
  the reason goes to the logs. The `texture` type needs nothing but an image in your pack.
- Battle music doesn't loop: a battle longer than the track ends in silence. Looping is a
  decision of the client's sound engine, which this mod doesn't touch.

---

## Credits

Developed by **Mathéo** ([matheo-1712](https://github.com/matheo-1712)).

## Usage terms

- **Modpacks: yes.** You are free to include this mod in any modpack, public or private,
  free or otherwise. No permission needed, no message required.
- **Servers: yes.** Run it on any server you like.
- **Datapacks: yes.** Trainer packs you build with this mod are yours - distribute them
  however you want.
- **Reuploading: no.** Do not republish the mod on Modrinth, CurseForge, or any other
  distribution site, mirror, or ad-wrapped download page. Link to
  [this page](https://modrinth.com/mod/cobblemon-trainers-rerebleue) instead - that way everyone gets
  the real file, the right version, and the changelog with it.

## Links

- **Source & issues:** [github.com/matheo-1712/cobblemon-trainers](https://github.com/matheo-1712/cobblemon-trainers)
- **Example pack:** [downloadable](https://github.com/matheo-1712/cobblemon-trainers/releases) next to the jar on every release, on assets - one folder that works as
  a datapack and a resource pack at once, covering every option.

Found a bug, or a trainer that won't load? Open an issue on GitHub with your JSON and the
server log - the mod names the file it choked on.
