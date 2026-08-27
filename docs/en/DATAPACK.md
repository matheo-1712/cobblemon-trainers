# Making a trainer datapack

One trainer is one JSON file. No code, no recompiling: a datapack is enough, and `/reload`
applies your changes without restarting the server.

For installing the mod and for the commands, see the [README](../../README.md).

*Cette page existe aussi [en français](../DATAPACK.md).*

## Contents

- [Layout](#layout) · [Where to put the pack](#where-to-put-the-pack)
- [Your first trainer](#your-first-trainer) · [Field reference](#field-reference)
- [Categories](#categories) · [Locking a trainer](#locking-a-trainer) ·
  [Advancements](#advancements) · [Calling a trainer over](SPAWNING.md)
- [Rematches and rewards](#rematches-and-rewards) ·
  [Farmable or not](#farmable-or-not) · [Progress tracking](#progress-tracking)
- [The team format](#the-team-format) · [The `Aspects:` line](#the-aspects-line) ·
  [The `Fallback Item:` line](#the-fallback-item-line)
- [Skins](#skins) · [Battle music](#battle-music) ·
  [Battle gimmicks](GIMMICKS.md) · [Translating your text](#translating-your-text)
- [Testing your pack](#testing-your-pack) · [Common mistakes](#common-mistakes)

## Layout

```
my_pack/
├── pack.mcmeta
└── data/my_pack/                       ← your namespace
    └── cobblemontrainers/
        ├── red.json                    → my_pack:red
        └── champions/                  ← a folder is a category
            ├── category.json           ← how the category shows up (optional)
            └── erika.json              → my_pack:champions/erika
```

```json
{ "pack": { "pack_format": 48, "description": "My trainers" } }
```

`pack_format` is **48** for a Minecraft 1.21.1 datapack.

- The folder being read is `cobblemontrainers/`, straight under your namespace - the same
  level as Cobblemon's own `species/` and `npcs/`. It is named after the mod rather than a
  generic `trainers/`, which keeps it from colliding with other mods.
- **The ID is `<namespace>:<path>`, subfolders included**: `champions/erika.json` gives
  `my_pack:champions/erika`. That folder is also the trainer's [category](#categories).
- `category.json` is the **only reserved filename**: it describes the folder it sits in, and
  is never read as a trainer.
- A pack loaded later overrides a trainer with the same ID, like any other datapack resource.
  An invalid file is skipped, the error goes to the log, and the other trainers still load.

### Where to put the pack

| Route | Location | Formats | Loads | Enabled |
| --- | --- | --- | --- | --- |
| Mods folder | `mods/` | folder, `.zip`, `.jar` | `data/` **and** `assets/` | automatically, every world |
| Datapack | `<world>/datapacks/` | folder, `.zip`, `.jar` | `data/` | per world, on discovery |
| Resource pack | `resourcepacks/` | folder, `.zip`, `.jar` | `assets/` | ticked in the options |

What decides is what your pack contains:

- **Trainers only** (`data/` alone) - either of the first two routes works.
- **Trainers plus translations or music** (`data/` and `assets/`) - `mods/` is the only route
  that loads both halves from one file. Otherwise you have to ship the same archive twice,
  in `datapacks/` **and** in `resourcepacks/`.
- **A skin shipped as an image** (`skin.type: texture`) - `mods/` is mandatory: the image is
  read by the server, and that is the only place it looks.

A pack dropped in `mods/` needs **nothing but its `pack.mcmeta`**, at the root of the archive.
No `fabric.mod.json`, no code: the mod picks up any folder or archive in `mods/` carrying a
`pack.mcmeta` and exposes it as a datapack *and* as a resource pack, client-side and
server-side alike. It shows up in `/datapack list` under the ID `mods/<file name>`.

Adding a `fabric.mod.json` is still allowed: Fabric then takes the pack for a mod and loads it
itself, which lets you declare `"depends": { "cobblemon-trainers": "*" }` - so a clear error at
startup rather than a pack loading for nothing. In exchange, a malformed `fabric.mod.json`
stops the game from starting.

An archive serving **both sides** declares a range, otherwise the resource pack screen calls it
incompatible (`pack_format` is 48 for data, 34 for resources):

```json
{
  "pack": {
    "pack_format": 48,
    "supported_formats": { "min_inclusive": 34, "max_inclusive": 48 },
    "description": "My trainers"
  }
}
```

Finally, `datapacks/` is declared as data and nothing else: an `assets/` folder put there is
**never** read, whatever the archive format. That is exactly what the `mods/` route works
around.

## Your first trainer

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

Then, in game: `/reload`, followed by `/cobblemontrainers spawn my_pack:red`.

## Field reference

Everything is optional: `{}` is a valid trainer, if not a very interesting one.

### Root

| Field | Default | What it does |
| --- | --- | --- |
| `name` | `Trainer` | The name shown above the trainer |
| `skin` | Steve | [Skin](#skins) |
| `team` | `[]` | The team, **one Pokémon per entry**, in [Showdown format](#the-team-format) |
| `battle` | - | The battle itself, below |
| `messages` | - | What the trainer says |
| `progress` | - | What beating them changes |
| `rewards` | `[]` | Items handed to the winner |
| `requires` | - | [What it takes to battle them](#locking-a-trainer) |
| `location` | - | [Where to find them, and calling them from the Battle Phone](SPAWNING.md) |

**➜ The `location` block is covered in [SPAWNING.md](SPAWNING.md)**, with its conditions, its
text and everything the mod does around a call. Declaring it is what makes a trainer callable;
leaving it out makes a trainer you have to go and find.

### `battle`

| Field | Default | What it does |
| --- | --- | --- |
| `level` | `1` | Level of the Pokémon that have **no** `Level:` line |
| `format` | `singles` | `singles`, `doubles`, `triples` - aliases `solo`, `duo`, `trio`, suffix `_50` |
| `difficulty` | `5` | [How well the AI plays](DIFFICULTY.md), from 0 (at random) to 5 (playing to win) |
| `healParty` | `true` | Heals the trainer's team before and after every battle |
| `music` | the mod's track | Sound ID played during the battle, `null` for silence |
| `gimmicks` | `[]` | [Battle gimmicks](GIMMICKS.md) the trainer uses: `["mega"]` for Mega Evolution |

The `_50` suffix (`singles_50`, `doubles_50`, `triples_50`) puts **both teams** at level 50 for
the battle. `level` therefore has no visible effect on a `_50` trainer.

The battle is fought on copies. Both teams enter it **automatically healed** - full HP, statuses
cleared, PP restored -, a fainted Pokémon included. And nothing comes back out: whatever the
outcome, win, loss or interrupted battle, the player's team gets back the HP, statuses and PP it
had before. It still earns its experience and EVs.

So the healing only lasts as long as the battle: a Pokémon fainted before a `_50` is still
fainted after it. No healing item is needed to start a battle, and none is saved either.

`level` is not `difficulty`: the first is the Pokémon's level - and only a fallback, for
Showdown entries with no `Level:` line -, the second is how well the AI plays. A level 100
trainer at `difficulty: 0` is still easy.

`difficulty` also decides what the mod corrects in Cobblemon's AI: nothing below `3`, the
impossible mistakes at `3`, entry hazards at `4`, and reading the battle in full at `5`.

**➜ Exactly what each level does is in [DIFFICULTY.md](DIFFICULTY.md)**, together with the
`/cobblemontrainers debugai` command, which shows you in battle what the mod corrected and why.

`healParty: false` carries damage and PP from one battle to the next - handy for a boss you
wear down over several attempts. Its fainted Pokémon move to the back of the team, so that it
opens the next battle with one still standing.

**➜ `gimmicks` is covered in [GIMMICKS.md](GIMMICKS.md)**: what has to be installed, the stone
to hand the Pokémon, and the fallback item for when the mod providing it is not there.

A `doubles` battle needs at least 2 Pokémon **on each side**, a `triples` at least 3. If either
team is too short, Cobblemon refuses the battle and says so in the chat. On the player's side
those first 2 or 3 also have to be able to fight: the mod refuses rather than let the battle
lock up.

### `messages`

| Field | What it does |
| --- | --- |
| `start` | At the start of the battle |
| `win` | If the player wins |
| `lose` | If the player loses |

### `progress`

| Field | Default | Values | What it does |
| --- | --- | --- | --- |
| `rematch` | `unlimited` | `unlimited`, `never` | Whether they can be challenged again once beaten |
| `listed` | `true` | boolean | Shows up in the Battle Phone and `/cobblemontrainers list` |

## Categories

**A trainer's folder is their category.** Nothing to declare: putting `erika.json` in
`champions/` is what makes her a champion. Categories group the Battle Phone list and
`/cobblemontrainers list`, and they are what [locks](#locking-a-trainer) and
[advancements](#advancements) can point at.

A `category.json` **inside the folder** gives it a name and a place, both optional:

```json
// cobblemontrainers/champions/category.json
{ "name": "category.my_pack.champions", "order": 1 }
```

| Field | Default | What it does |
| --- | --- | --- |
| `name` | the folder name | Display name, [translatable](#translating-your-text) like a trainer's |
| `order` | after the others | Place in the pack's page, smallest at the top |

- A category with no file shows up under its folder name and is sorted after the ones carrying
  an `order`, alphabetically.
- Trainers sitting at the root form one last group, under a heading supplied by the mod
  ("Trainers"). A pack that uses no category at all shows no heading: the list is exactly what
  it used to be.
- A subfolder of a subfolder is a category in its own right (`champions/kanto`), with its own
  `category.json`.

## Locking a trainer

The `requires` block closes a trainer off until a player has done what it asks. They then turn
the challenge down politely, listing what is missing - and by default they do not even show up
in the Battle Phone.

```json
"requires": {
  "defeated": ["champions/jasmine", "champions/brock"],
  "victories": { "count": 8, "category": "champions" },
  "items": [{ "item": "my_pack:boulder_badge", "count": 1 }],
  "party": [{ "pokemon": "staraptor", "count": 1 }],
  "advancement": "my_pack:league_access",
  "hidden": false,
  "message": "trainer.my_pack.champion.locked"
}
```

| Field | What it does |
| --- | --- |
| `defeated` | Trainers that must have been beaten. Without a namespace, the ID is read inside the pack of the trainer requiring it - path included (`champions/jasmine`) |
| `victories` | A number of trainers beaten: `count`, narrowed by `pack` and/or `category`. Leaving `count` out means **all of that group** |
| `items` | Items the player has to be carrying, full ID. **Never consumed** |
| `party` | Pokémon the player has to have with them: `pokemon` is written the way `/pokespawn` takes it, `count` says how many. **Never taken** |
| `advancement` | An advancement that must have been earned, vanilla or from a pack |
| `hidden` | `true` by default: the trainer is absent from the Battle Phone while locked. `false` keeps them there, locked and with their conditions shown |
| `message` | What the trainer answers. By default, a line from the mod - the list of what is missing is appended either way |

- **Every declared condition has to be met.** They add up, they are never alternatives.
- `victories` only counts `listed` trainers, and **never the trainer requiring it**: a champion
  can therefore ask for "beat every champion".
- An item, species, trainer or advancement ID that cannot be found counts as unmet, with a
  warning in the log: a typo closes a trainer off, it never opens them up.
- `party` reads the **party** only, never the PC, and **only what is written is checked**:
  `staraptor` accepts any level, gender and form, fainted included. The rest of the syntax
  follows: `staraptor shiny=true` asks for a shiny, `rotom appliance=wash` for one form.
- The refusal is returned **before** the battle is built: no healed team, no music.

## Advancements

Beating a trainer fires the `cobblemon-trainers:trainer_defeated` criterion. Your advancements
are then ordinary advancements - title, icon, tree, toast and rewards are Minecraft's.

```json
// data/my_pack/advancement/boulder_badge.json
{
  "display": {
    "icon": { "id": "cobblemon:poke_ball" },
    "title": { "translate": "advancement.my_pack.boulder_badge.title" },
    "description": { "translate": "advancement.my_pack.boulder_badge.description" },
    "frame": "task"
  },
  "criteria": {
    "beaten": {
      "trigger": "cobblemon-trainers:trainer_defeated",
      "conditions": { "trainer": "my_pack:champions/brock" }
    }
  }
}
```

| Condition | What it matches |
| --- | --- |
| `trainer` | The trainer beaten |
| `category` | Their category |
| `pack` | Their namespace |
| `count` | How many **different** trainers matching the filters above the player has beaten |

All optional and cumulative; with none of them, the criterion is met on the first trainer
beaten. `trainer` and `category` accept a full ID (`my_pack:champions/brock`) or a bare path
(`champions/brock`), which then matches in every namespace.

"Beat all 8 champions" therefore fits in one criterion:

```json
"conditions": { "pack": "my_pack", "category": "champions", "count": 8 }
```

The count is read from the saved progress, the very same one `/cobblemontrainers list` shows:
it survives a restart and is not wiped by `/reload`.

## Rematches and rewards

`progress.rematch` decides whether the trainer can be challenged again, `rewards` decides what
you get, and each reward decides for itself how often it drops.

A rematchable trainer is therefore a **way to farm**, and your pack decides which one: berries
on every win, an ore, a resource you get no other way. The mod ships none of those loops, only
what it takes to write them.

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

**What is remembered is the trainer's ID**, not the NPC standing in the world. Beating one copy
beats them all: putting ten of them on a map does not give ten battles, and killing the one you
beat to spawn them again does not reset anything.

- **Only a win counts.** A loss, a flee, a `/stopbattle` or a trainer vanishing mid-battle
  record nothing.
- **It is per player.** A trainer beaten by one player is still there for the others.
- **The memory lives in the world**, not in the datapack: `/reload` does not clear it. Renaming
  or moving a file changes its ID, so it starts over.

| Field | Default | What it does |
| --- | --- | --- |
| `item` | - | Full ID, **namespace required** |
| `count` | `1` | How many, clamped to 1-6400 |
| `hidden` | `false` | Keep it off the Battle Phone |
| `firstWinOnly` | `false` | Only drop on this player's first win |

Items go to the inventory, whatever does not fit drops at the player's feet, and every item
received is announced in the chat. An item that cannot be found is skipped with a warning, the
others are handed over all the same.

**The Battle Phone shows the rewards on a trainer's page**, even before they have been beaten:
unlike their team, a reward is the reason to try. `hidden` takes a line off that display without
changing anything about what is handed over - the player finds out by winning. Marking every
line `hidden` is how you get a trainer whose rewards are a complete surprise.

```json
"rewards": [
  { "item": "cobblemon:rare_candy", "count": 10 },
  { "item": "cobblemon:master_ball", "hidden": true }
]
```

### Farmable or not

`firstWinOnly` decides, **reward by reward**, whether it drops again on later wins. By default
it does: a reward can be farmed for as long as the trainer accepts a rematch.

That is what lets one battle give a trophy once and consumables every time:

```json
"progress": { "rematch": "unlimited" },
"rewards": [
  { "item": "cobblemon:link_cable", "firstWinOnly": true },
  { "item": "cobblemon:exp_candy_l", "count": 5 }
]
```

The Link Cable drops once, the candies on every win. The Battle Phone page says so: a
`firstWinOnly` reward carries a marker, an outline while it is still to be won and filled once
it has been, and its icon is greyed out from then on. The tooltip spells it out.

`firstWinOnly` does nothing on a `"rematch": "never"` trainer: there is never a second win to
tell apart.

## Progress tracking

Two ways into the same data.

`/cobblemontrainers list [<player>]` lists trainers by category and says which ones the player
has beaten:

```
Trainers of Steve - 1 / 3 defeated
Champions - 1 / 2
✔ my_pack:champions/jasmine - Jasmine (no rematch)
✘ my_pack:champions/champion - The Champion (locked, 1 requirement(s) left)
Trainers - 0 / 1
✘ my_pack:rival - Rival
```

The **Battle Phone** item (`cobblemon-trainers:battle_phone`) shows an ordinary player the same
thing, in a screen: one tab per datapack, a heading per category, each trainer's skin and their
state. It also shows **a trainer's team once they have been beaten**, models and all - before
that the six slots stay empty, the server flatly refusing to send the team.

Three differences between the two:

- The Battle Phone **hides** trainers locked with `hidden`; `/cobblemontrainers list` always
  shows them, along with how many conditions are left. That is the operator's view.
- `"listed": false` takes the trainer out of both. It is a display setting, not a memory one:
  wins are still recorded, so `rematch` and `firstWinOnly` keep working.
- The demo trainers shipped with the mod appear in both, in their own tab - your namespace
  makes yours.

## The team format

**One entry in the `team` array = one Pokémon**, its lines separated by `\n`: the block Showdown
exports, pasted as is.

```json
"team": [
  "Gyarados (M) @ Leftovers\nAbility: Intimidate\nLevel: 64\n- Waterfall",
  "Vaporeon (F)\nLevel: 62\n- Surf"
]
```

Lines that are recognised, on top of the first one:

| Line | Example |
| --- | --- |
| First line | `Nickname (Species) (M) @ Item` - nickname, gender and item optional |
| Form | `Aspects: rlm, poison` |
| Fallback item | `Fallback Item: Life Orb` |
| Ability | `Ability: Static` |
| Level | `Level: 88` |
| Shiny | `Shiny: Yes` |
| Gender | `Gender: M` |
| EVs / IVs | `EVs: 252 SpA / 4 SpD / 252 Spe` |
| Nature | `Timid Nature` |
| Move | `- Thunderbolt` |

Any other line is ignored silently: a Showdown export pastes in without cleaning up.

Three details that catch people out:

- **Names are written the way Showdown writes them**, punctuation included: `U-turn`,
  `Will-O-Wisp`, `Farfetch'd`, `Flabébé`, `Mr. Mime` are converted to Cobblemon identifiers.
  A move that does not exist is skipped with a warning, and the Pokémon shows up with the rest.
  The one exception is the form suffix (`Raichu-Alola`): see [`Aspects:`](#the-aspects-line).
- **A held item with no namespace is looked up in `cobblemon:` first**: `Light Ball` becomes
  `cobblemon:light_ball`, `Heavy-Duty Boots` becomes `cobblemon:heavy_duty_boots`. Failing that,
  the same name is looked up across every loaded mod, and is only accepted when exactly one
  matches - which is what makes `@ Charizardite X` work without naming the mod providing it.
  Ambiguous or not found, the item is skipped with a warning and the Pokémon shows up
  empty-handed, unless it declares a [fallback item](#the-fallback-item-line). Write the ID out
  in full (`minecraft:stick`) to settle any doubt.
- **Stat abbreviations are translated by the mod**: `HP`, `Atk`, `Def`, `SpA`, `SpD`, `Spe`, and
  the long names. An abbreviation outside that list is ignored silently.

## The `Aspects:` line

A form is not a separate species in Cobblemon: it is the same species carrying different
**aspects**. An Alolan Raichu is a `raichu` with the `alolan` aspect. The `Aspects:` line lists
them, separated by commas or spaces:

```json
"team": ["Haxorus @ Life Orb\nAspects: rlm, poison\nAbility: Venomedge\nLevel: 65\n- Poison Jab"]
```

| Feature type | Aspect | What to write |
| --- | --- | --- |
| Flag (`"type": "flag"`) | `alolan`, `rlm` | `Aspects: alolan` |
| Choice (`"type": "choice"`) | `wash-appliance` | `Aspects: appliance=wash` |

A choice feature is not declared by its aspect but by its `feature=value` pair: Rotom-Wash's
`wash-appliance` aspect comes from the `appliance` feature set to `wash`. The feature's name is
the name of the `data/<ns>/cobblemon/species_features/<name>.json` file.

- **Where to find them**: in the `aspects` field of the form, in the pack that adds it
  (`data/<ns>/cobblemon/species/…` or `species_additions/…`).
- **How to check**: the line uses exactly the same syntax as `/pokespawn`, so
  `/pokespawn haxorus rlm=true poison=true` in game tells you straight away.
- **An unknown aspect is ignored** with a warning: the trainer gets the Pokémon in its base
  form. Typically, the pack defining the form is not loaded.
- **The form needs nothing else**: stats, types, abilities and model all follow.

## The `Fallback Item:` line

What the Pokémon holds when the item on its first line does not exist - typically because the
mod providing it is not installed.

```json
"team": ["Charizard @ Charizardite X
Fallback Item: Life Orb
Level: 80
- Flare Blitz"]
```

- **The first item that exists is the one held**, the rest are ignored. Several
  `Fallback Item:` lines are tried in the order they are written.
- **The rule covers any item**, not just Mega Stones: the mod only looks at what the game has
  registered.
- **When nothing resolves**, the Pokémon shows up empty-handed, exactly as without the line.

## Skins

```json
"skin": { "type": "player_username", "value": "Notch" }
"skin": { "type": "player_uuid",     "value": "069a79f4-44e9-4726-a5be-fca90e38aaf5" }
"skin": { "type": "texture",         "value": "my_pack:textures/trainers/red.png", "model": "slim" }
```

The first two download the skin from the Mojang API when the trainer spawns: they need network
access and an account that exists. Should it fail, whatever the type, the trainer keeps the
default skin and the reason is in the log.

`texture` takes the full path under `assets/`, namespace first and `.png` included:
`my_pack:textures/trainers/red.png` points at `assets/my_pack/textures/trainers/red.png`. The
file is an **ordinary player skin**, a 64×64 PNG with transparency, rendered on the Steve rig or
on Alex's with `"model": "slim"`.

That route has two particularities:

- **The image is read by the server**, which sends it to the clients along with the trainer: a
  player who does not have your pack still sees the right skin.
- **So it has to be in `mods/`.** A pack sitting in `resourcepacks/` or `datapacks/` is out of
  the server's reach; the trainer stays on the default skin, and the log explains why.

If you have no image of your own, the mod ships one to try it with:
`cobblemon-trainers:textures/trainers/example.png`.

## Battle music

The music is sent to the players in the battle, loops, and stops when the battle ends, however
it ends: win, loss, flee, `/stopbattle`, or a trainer vanishing. **Nothing else plays alongside
it**: whatever was playing in the *Music* category is stopped just before, and the game keeps
its own music to itself until the battle is over.

| `battle.music` | Result |
| --- | --- |
| absent | the mod's track |
| `null` or `""` | no music |
| a sound ID | that track |

The sound is played by the client, so **`data/` is not enough**: the `.ogg` and its `sounds.json`
entry live under `assets/`.

```
my_pack/
├── pack.mcmeta
└── assets/my_pack/
    ├── sounds.json
    └── sounds/battle_music/champion.ogg
```

```json
// sounds.json
{
  "battle_music.champion": {
    "subtitle": "my_pack.subtitles.champion",
    "sounds": [{ "name": "my_pack:battle_music/champion", "stream": true }]
  }
}
```

The trainer references the **key** in `sounds.json`, never the file path:
`"music": "my_pack:battle_music.champion"`.

- **`"stream": true` is essential** on a long track, otherwise Minecraft loads the whole file
  into memory at once.
- **The track loops**: it starts over the moment it ends, with no gap, until the battle is
  over. So pick one whose end runs into its beginning.
- **The track is played on the player**, with no falloff: a mono file is not positioned in the
  world. Stereo is still the better choice for music.

## Translating your text

`name`, the three `messages` and a category's `name` are sent as translatable text. Two ways to
use that, your choice:

- **Plain text** - `"name": "Red"` shows up as is, in every language.
- **A translation key** - `"name": "trainer.my_pack.red.name"`, defined under `assets/`.

```json
// assets/my_pack/lang/en_us.json
{
  "trainer.my_pack.red.name": "Red",
  "category.my_pack.champions": "Champions"
}
```

Every player then reads the text in their own language, **the trainer's floating nameplate
included**. On a server, `resource-pack` in `server.properties` hands the pack out
automatically.

This is the only translation route: keys are resolved by the client, so a lang file put in the
datapack would do nothing. If no translation exists, Minecraft shows the key as is - a good way
to spot a key you forgot.

## Testing your pack

```
/reload
/cobblemontrainers spawn my_pack:champions/erika
/cobblemontrainers list
/cobblemontrainers defeat my_pack:champions/erika
```

**➜ Every command is detailed in [COMMANDS.md](COMMANDS.md)**, arguments included.

`/reload` reloads the trainers without a restart. The autocompletion on `spawn` offers the
trainers that actually loaded, under their full ID: if yours is not there, it was not read - the
reason is in the log. A bare file name is enough for the command
(`/cobblemontrainers spawn erika`), the mod finds the folder.

On load, the mod writes one summary line:

```
[cobblemon-trainers] Loaded 7 trainer(s) in 2 categor(y/ies): my_pack:champions/erika, …
```

`/cobblemontrainers defeat <id|all> [<players>] [reset]` records a win without a battle: enough
to check a `requires`, an advancement or a Battle Phone page without playing the whole league -
`all` ticks off every loaded trainer at once. It hands out no rewards, and `reset` forgets the
win so you can test the lock again. An advancement already earned, on the other hand, only comes
off with `/advancement revoke`.

A resource pack does not reload with `/reload`: that is <kbd>F3</kbd>+<kbd>T</kbd>.

To put a trainer somewhere for good rather than spawning them by hand, the mod provides a
**trainer spawner block** (`cobblemon-trainers:trainer_spawner`), which holds an ID and puts the
trainer back whenever they are missing. Nothing to declare in the pack: it is configured in
game, by right-clicking it. See [the README](../../README.md#le-bloc-de-dresseur).

## Common mistakes

| Symptom | Likely cause |
| --- | --- |
| The trainer is not in the autocompletion | Wrong folder: it has to be `data/<ns>/cobblemontrainers/`, `<ns>` being your namespace |
| `Loaded 0 trainer(s)` from `datapacks/` | `pack.mcmeta` missing or the wrong `pack_format` - Minecraft skips the whole pack |
| A pack in `mods/` does nothing | `pack.mcmeta` is not at the **root** of the archive, or you zipped the folder instead of its contents |
| Trainers load, music and translations do not | The archive is in `datapacks/`, which is read **only** as data. Put it in `mods/` |
| A `category.json` does nothing | It is at the root of `cobblemontrainers/`: it describes the folder it sits in, so it needs one |
| Two trainers with the same name override each other | They were in the same folder: the ID includes the path, not just the name |
| Right-clicking answers "does not want to battle you yet" | An unmet `requires`. `/cobblemontrainers list <player>` says how many conditions are left |
| A `requires` trainer is missing from the Battle Phone | That is the default (`hidden: true`). Set `"hidden": false` |
| Right-clicking answers "already beaten" | `"rematch": "never"` and that player has won. Spawning the trainer again changes nothing |
| The advancement never fires | Folder `data/<ns>/advancement/` (singular since 1.21), or a `trainer`/`category` matching no loaded ID. `/cobblemontrainers defeat <id>` checks it in one command |
| No reward on a win | No `rewards`, `"firstWinOnly": true` on a win that is not the first, or an item that cannot be found |
| The trainer is missing from `/cobblemontrainers list` | `"listed": false`, or not loaded at all - `/cobblemontrainers spawn` will tell you |
| The doubles battle is refused | Fewer than 2 Pokémon on one of the two sides, yours included |
| An EV/IV stat looks ignored | A stat name outside the recognised list |
| No music | The track is missing from the resource pack, or the ID does not match the `sounds.json` key |
| The skin stays Steve | The username does not exist, or the Mojang API is unreachable - check the log |
| A `texture` skin stays on the default | A path matching no file (`.png` included), or a pack outside `mods/` |

## A complete example

The repository ships a pack covering every option, trainer by trainer:
[`examples/cobblemonrlm/`](../../examples/cobblemonrlm). Zip its contents, rename to `.jar`, drop
it in `mods/`: that is all.

Easier still: it comes **already zipped** as an asset on every
[release](https://github.com/matheo-1712/cobblemon-trainers/releases), under the name
`exemple_trainer_datapack.zip`. It goes as is into `mods/`, `datapacks/` or `resourcepacks/`.

Among other things it shows a full league: three champions in the `champions` category, one of
them locked behind the first and an item, a last one hidden until the other two have fallen, and
two advancements hooked onto the wins.
