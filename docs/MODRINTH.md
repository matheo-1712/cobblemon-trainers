# Cobblemon Trainers

**Add real Pokémon trainers to your Cobblemon world — each one a single JSON file.**

Drop the mod in, and you can populate your world with custom trainers: a full Showdown
team, a Minecraft skin, custom dialogue, battle music, rewards, and a difficulty setting.
No code, no recompiling, no in-game editor. Just a datapack and `/reload`.

Works in singleplayer and on servers. Everything runs server-side, so **players don't need
the mod to see your trainers' skins**.

---

## What you get

- **Real trainer battles.** Right-click a trainer, get a proper Cobblemon battle in
  singles, doubles or triples — with battle music playing over everything else.
- **Showdown teams, pasted in.** Copy an export straight from Pokémon Showdown: items,
  abilities, natures, EVs/IVs, moves, shininess, nicknames. Unknown lines are ignored, so
  it just works.
- **Any skin you like.** Borrow a Minecraft account's skin by username or UUID, or ship
  your own PNG in the pack. Custom textures are sent by the server, so every player sees
  them.
- **Rewards and one-time bosses.** Hand out items on victory, once or every time. Make a
  champion unbeatable twice with `canRebattle: false`.
- **Progress tracking.** `/listtrainers` shows who's left to beat, for you or any player.
  Progress is saved with the world and survives `/reload` and restarts.
- **Adjustable AI.** Skill from 0 (plays randomly) to 5 (plays to win), independent from
  Pokémon level.
- **Regional forms, megas, fakemon.** An `Aspects:` line handles any Cobblemon form,
  including ones added by other packs.
- **Translatable text.** Trainer names and dialogue accept translation keys, so every
  player reads them in their own language — floating nameplate included.

---

## Requirements

| | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Mod loader | Fabric (Loader ≥ 0.17.2) |
| Java | 21 — exactly, Cobblemon refuses anything else |
| Cobblemon | ≥ 1.7.3 |
| Fabric API | required |
| Fabric Language Kotlin | required |

## Installation

Put `cobblemon-trainers-<version>.jar` in your `mods/` folder, next to Cobblemon, Fabric API
and Fabric Language Kotlin. That's it — the mod ships two demo trainers you can spawn right
away.

### Built-in trainers

Two ready-to-use trainers ship with the mod, so you can test everything before writing a
single JSON file:

| ID | Name | Level | Format | Team |
| --- | --- | --- | --- | --- |
| `cobblemon-trainers:rerebleue` | **RereBleue** | 80 | Singles | Hydreigon (nicknamed Ram), Mimikyu, Lucario, Archeops |
| `cobblemon-trainers:theazertor` | **TheAzertor** | 70 | Doubles | Metagross, Togekiss, Tyranitar, Rotom |

```
/spawntrainer cobblemon-trainers:rerebleue
/spawntrainer cobblemon-trainers:theazertor
```

Both play at maximum AI skill (`skill: 5`) with fully built competitive teams — they are meant
as a real fight, and as a reference for your own files.

## Commands

```
/spawntrainer <id>
/spawntrainer <id> <x> <y> <z>
/listtrainers [<player>]
```

Both require permission level 2 (operator). Autocomplete lists every loaded trainer under its
full `<pack>:<trainer>` ID, and searches both halves — typing `jac` finds `my_pack:jacinthe`.

---

## Making a trainer

Create `data/<your_namespace>/cobblemontrainers/red.json` in a datapack:

```json
{
  "name": "Red",
  "level": 88,
  "skin": { "type": "player_username", "value": "Red" },
  "battleStartMessage": "Let's settle this.",
  "battleEndWinMessage": "...It's over.",
  "skill": 5,
  "canRebattle": false,
  "rewards": [
    { "item": "cobblemon:master_ball", "count": 1 }
  ],
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

Then, in game:

```
/reload
/spawntrainer my_pack:red
```

**Every field is optional** — a file containing just `{}` produces a valid (if boring)
trainer. A broken file is logged and skipped; the others still load.

### Field reference

| Field | Default | What it does |
| --- | --- | --- |
| `name` | `Trainer` | Name shown above the trainer |
| `level` | `1` | Level for Pokémon with no `Level:` line |
| `team` | `[]` | The team, in Showdown format |
| `skin.type` | `player_username` | `player_username`, `player_uuid` or `texture` |
| `skin.value` | `Steve` | Username, UUID, or path to a PNG in your pack |
| `skin.model` | `default` | `default` (Steve) or `slim` (Alex) — `texture` only |
| `battleFormat` | `singles` | `singles`, `doubles`, `triples` |
| `skill` | `5` | Battle AI difficulty, 0–5 |
| `autoHealParty` | `true` | Heal the trainer's team before and after each battle |
| `canBattle` | `true` | `false` makes the NPC non-hostile |
| `canRebattle` | `true` | `false` = one battle per player, ever |
| `tracked` | `true` | `false` hides the trainer from `/listtrainers` |
| `rewards` | `[]` | Items given on victory |
| `rewardOnce` | `false` | `true` = rewards only on the first win |
| `battleStartMessage` | — | Sent when the battle begins |
| `battleEndWinMessage` | — | Sent when the player wins |
| `battleEndLoseMessage` | — | Sent when the player loses |
| `battleMusic` | mod's track | Sound ID played during the battle, `null` for silence |

---

## Shipping your pack

Three options, pick whichever suits your pack:

| Location | Accepted formats | Loads |
| --- | --- | --- |
| `mods/` | folder, `.zip`, `.jar` | `data/` **and** `assets/` |
| `saves/<world>/datapacks/` | folder, `.zip`, `.jar` | `data/` |
| `resourcepacks/` | folder, `.zip`, `.jar` | `assets/` |

Two things this mod adds on top of vanilla: `.jar` archives are accepted everywhere (the
game only knows folders and `.zip`), and **a pack dropped in `mods/` loads with nothing but
its `pack.mcmeta`** — no `fabric.mod.json`, no code. It's the only route that loads trainers,
translations, music and custom skin textures in a single file the player just drops in.

---

## Known limitations

- Forms are declared through their aspects (`Aspects: alolan`), not the Showdown name
  suffix (`Raichu-Alola`) — a suffix can't be told apart from species whose name contains a
  hyphen (`Ho-Oh`, `Porygon-Z`).
- `player_username` and `player_uuid` skins are fetched from the Mojang API, so they need
  network access and an existing account. On failure the trainer keeps the default skin and
  the reason goes to the logs. The `texture` type needs nothing but an image in your pack.
- Battle music doesn't loop: a battle longer than the track ends in silence. Looping is a
  client-side decision, and this mod has no client code.

---

## Credits

Developed by **Mathéo** ([matheo-1712](https://github.com/matheo-1712)).

## Usage terms

- **Modpacks: yes.** You are free to include this mod in any modpack, public or private,
  free or otherwise. No permission needed, no message required.
- **Servers: yes.** Run it on any server you like.
- **Datapacks: yes.** Trainer packs you build with this mod are yours — distribute them
  however you want.
- **Reuploading: no.** Do not republish the mod on Modrinth, CurseForge, or any other
  distribution site, mirror, or ad-wrapped download page. Link to
  [this page](https://modrinth.com/mod/cobblemon-trainers) instead — that way everyone gets
  the real file, the right version, and the changelog with it.

## Links

- **Source & issues:** [github.com/matheo-1712/cobblemon-trainers](https://github.com/matheo-1712/cobblemon-trainers)
- **Example pack:** [downloadable](https://github.com/matheo-1712/cobblemon-trainers/releases) next to the jar on every release, on assets — one folder that works as
  a datapack and a resource pack at once, covering every option.

Found a bug, or a trainer that won't load? Open an issue on GitHub with your JSON and the
server log — the mod names the file it choked on.
