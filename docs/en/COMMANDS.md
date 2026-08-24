# The commands

Everything the mod does from the chat fits in one command, `/cobblemontrainers`, and a verb.

```
/cobblemontrainers spawn <id> [<x> <y> <z>]
/cobblemontrainers list [<player>]
/cobblemontrainers defeat <id|all> [<players>] [reset]
/cobblemontrainers debugai
```

*Cette page existe aussi [en français](../COMMANDS.md).*

**Permission level 2 (operator) is checked once, on the root**, and so covers all four verbs.
An ordinary player sees none of them in the autocompletion: what is meant for them is the
Battle Phone, which reads the same progress without granting any power at all.

| Verb | What it does |
| --- | --- |
| [`spawn`](#spawn) | Spawns a trainer |
| [`list`](#list) | Reads a player's progress |
| [`defeat`](#defeat) | Records a win without a battle |
| [`debugai`](#debugai) | Shows in chat what the mod corrects in the AI |

## A trainer's ID

`spawn` and `defeat` take the same argument. It accepts both forms:

| Written | Read as |
| --- | --- |
| `my_pack:champions/erika` | that exact trainer |
| `champions/erika` | the first loaded trainer with that path |
| `erika` | the same, the folder found on its own |

The autocompletion always offers the **full ID**: the namespace *is* the pack a trainer comes
from, so it is the only thing telling apart two packs shipping the same file name. The search
matches both halves - `jac` finds `my_pack:jasmine`.

A trainer missing from the autocompletion was not loaded: the reason is in the server log, and
[DATAPACK.md](DATAPACK.md#common-mistakes) lists the causes.

## `spawn`

```
/cobblemontrainers spawn <id>
/cobblemontrainers spawn <id> <x> <y> <z>
```

Spawns the trainer, at the caller's position if no coordinates are given. The coordinates take
vanilla syntax, `~` and `^` included.

- **The trainer does not come back** when killed: this is a testing and troubleshooting tool.
  For a trainer holding a post there is the trainer spawner block; for a trainer the player
  fetches themselves there is [calling one over](SPAWNING.md).
- **The trainer's location is not checked.** A trainer who only answers at midnight in the
  badlands still spawns in broad daylight: that is what lets you test their team without going
  to find their biome.
- A position outside the world is refused, as it is for `/summon`.

## `list`

```
/cobblemontrainers list
/cobblemontrainers list <player>
```

Lists trainers by category and ticks the ones the player has beaten. With no argument, it is
your own progress.

```
Trainers of Steve - 1 / 3 defeated
Champions - 1 / 2
✔ my_pack:champions/jasmine - Jasmine (no rematch)
✘ my_pack:champions/champion - The Champion (locked, 1 requirement(s) left)
Trainers - 0 / 1
✘ my_pack:rival - Rival
```

- **This is the operator's view**: a locked trainer always shows up here, with how many
  conditions are left, where the Battle Phone hides them by default. The conditions are
  evaluated against the **targeted player**, not against you.
- Only `"listed": true` trainers appear, in the exact order the Battle Phone shows them.
- The command returns how many trainers were beaten, readable with
  `execute store result score …` - enough to hang a scoreboard off a league.

## `defeat`

```
/cobblemontrainers defeat <id> [<players>] [reset]
/cobblemontrainers defeat all [<players>] [reset]
```

Records a win **without a battle**. The trainer counts as beaten, advancements are evaluated,
trainers locked behind them open up, and their Battle Phone page reveals their team - exactly
what a real win would have done.

| Form | Effect |
| --- | --- |
| `defeat <id>` | You have beaten that trainer |
| `defeat <id> <players>` | They have. Vanilla selectors work (`@a`, `@p[…]`) |
| `defeat all` | Every loaded trainer at once |
| `… reset` | The opposite: the win is forgotten |

- **No reward is handed out**, and the end-of-battle message is not sent: a testing tool has to
  be runnable a hundred times without burying the player in items.
- **`all` takes every loaded trainer**, not only the `listed` ones.
- **The trigger fires even when the win was already recorded.** That is what catches up an
  advancement added after the fact, without replaying the battle.
- **`reset` does not take back an advancement already earned.** Minecraft only does that with
  `/advancement revoke`.
- The command returns how many (trainer, player) records were touched.

## `debugai`

```
/cobblemontrainers debugai
```

A switch. While it is on, every decision the mod turns down in a trainer's AI shows up in your
chat during the battle, with its reason and the numbers behind it - move refused, switch
refused, heal set aside. Run the command again to turn it off.

It is the only way to tell "the trainer did not want to switch" apart from "the trainer wanted
to switch and the mod stopped them". See [DIFFICULTY.md](DIFFICULTY.md) for what each level
corrects.

- **The setting is per player and lives in memory**: it is forgotten on disconnect.
- It shows the decisions of **any trainer in a battle you are in**, so watching someone else's
  fight is enough to read its AI.

## Common errors

| Message | Cause |
| --- | --- |
| `Unknown trainer` | The ID matches no loaded trainer - check the `data/<ns>/cobblemontrainers/` folder |
| `No trainer loaded` | No pack was read at all; the log says why |
| `Every loaded trainer is hidden from listings` | They are all `"listed": false` |
| `Invalid position` | Coordinates outside the world's bounds |
| `Could not spawn the trainer` | The server log has the detail - typically a team no Pokémon survives parsing |
| The command is not in the autocompletion | Permission below 2, or the mod is missing from the server |
