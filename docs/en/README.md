# Documentation

The mod's wiki, in English. [MODRINTH.md](../../MODRINTH.md) introduces Cobblemon Trainers and
covers installing it; the pages below are the **reference** - this is where the detail lives.

*Cette documentation existe aussi [en français](../README.md), avec le
[README](../../README.md) du dépôt.*

## The pages

| Page | What is in it |
| --- | --- |
| [COMMANDS.md](COMMANDS.md) | The four verbs of `/cobblemontrainers`, their arguments and what they return |
| [DATAPACK.md](DATAPACK.md) | Making a trainer pack: layout, every field, categories, locks, advancements, Showdown teams, skins, music, rewards, translations |
| [SPAWNING.md](SPAWNING.md) | Getting a trainer to turn up: the `location` block and calling one from the Battle Phone |
| [DIFFICULTY.md](DIFFICULTY.md) | Exactly what `battle.difficulty` does, from `0` to `5` |
| [GIMMICKS.md](GIMMICKS.md) | Making a trainer Mega Evolve: `battle.gimmicks`, the stone and the fallback item |

An example pack covering every option lives in
[`examples/cobblemonrlm/`](../../examples/cobblemonrlm): one folder that works as a datapack
(`data/`) and as a resource pack (`assets/`) at once. Every
[release](https://github.com/matheo-1712/cobblemon-trainers/releases) attaches it ready-zipped,
under the name `exemple_trainer_datapack.zip`.

**Eight trainers ship with the mod**, in the `cobblemon-trainers` namespace: iconic trainers
at level 80, each callable from one biome, and a level 100 one locked behind them that only
answers in the End. They show up in the Battle Phone in their own tab, next to the
ones from your packs.

## By question

| I want to… | Go to |
| --- | --- |
| Learn the commands | [The commands](COMMANDS.md) |
| Write my first trainer | [Your first trainer](DATAPACK.md#your-first-trainer) |
| Know where to put my pack | [Where to put the pack](DATAPACK.md#where-to-put-the-pack) |
| See every JSON field | [Field reference](DATAPACK.md#field-reference) |
| Paste in a Showdown team | [The team format](DATAPACK.md#the-team-format) |
| Use a regional form or a fakemon | [The `Aspects:` line](DATAPACK.md#the-aspects-line) |
| Give a trainer a skin | [Skins](DATAPACK.md#skins) |
| Sort my trainers into a league | [Categories](DATAPACK.md#categories) |
| Lock a trainer behind another | [Locking a trainer](DATAPACK.md#locking-a-trainer) |
| Hand out items on a win | [Rematches and rewards](DATAPACK.md#rematches-and-rewards) |
| Fire an advancement | [Advancements](DATAPACK.md#advancements) |
| Make a trainer callable | [The `location` block](SPAWNING.md#the-location-block) |
| Pick an AI level | [Which level to pick](DIFFICULTY.md#which-level-to-pick) |
| Make a trainer Mega Evolve | [Battle gimmicks](GIMMICKS.md) |
| Understand an AI correction in game | [Checking in game](DIFFICULTY.md#checking-in-game) |
| Translate my trainers | [Translating your text](DATAPACK.md#translating-your-text) |
| Work out why my pack will not load | [Common mistakes](DATAPACK.md#common-mistakes) |

## One rule, one page

Each subject is described in exactly one place, and the other pages link to it:

- the **commands** are in `COMMANDS.md`;
- the **trainer format** is in `DATAPACK.md`;
- anything about **calling a trainer over** is in `SPAWNING.md`;
- anything the **AI** does is in `DIFFICULTY.md`;
- anything about **battle gimmicks** is in `GIMMICKS.md`.

A rule written in two places is a rule that ends up wrong in one of them. Adding a field means
adding a row to the `DATAPACK.md` table, not a section.
