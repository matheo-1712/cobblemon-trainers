# Battle gimmicks

A trainer can Mega Evolve mid-battle. That is the `battle.gimmicks` field, and it needs another
mod - the one that puts Mega Stones in the game.

For setting the field on a trainer, see [DATAPACK.md](DATAPACK.md#field-reference).

*Cette page existe aussi [en français](../GIMMICKS.md).*

## In one minute

```json
{
  "name": "Peter",
  "battle": {
    "level": 80,
    "gimmicks": ["mega"]
  },
  "team": [
    "Charizard @ Charizardite X\nFallback Item: Life Orb\nLevel: 80\n- Flare Blitz\n- Dragon Claw"
  ]
}
```

Both halves are needed: the **stone** on the Pokémon, and the **word** in `gimmicks`. Handing
over the stone without writing `["mega"]` makes a trainer who never uses it.

## What has to be installed

| Requirement | Why |
| --- | --- |
| [Cobblemon: Mega Showdown](https://modrinth.com/mod/mega-showdown) on the server | It is what provides the Mega Stones and the mega form |
| The same mod on the client | Otherwise the player never sees the transformation |

Cobblemon Trainers **does not depend** on Mega Showdown: the mod loads, runs and plays without
it. A trainer declaring `["mega"]` simply battles as usual, never mega evolving.

## Accepted values

| Value | Effect |
| --- | --- |
| `"mega"` | The trainer mega evolves as soon as the battle allows it |

`terastal`, `zmove`, `dynamax` and `ultra` exist in Cobblemon but are **not supported yet**:
writing one is reported in the log at load time and does nothing. Any other word is reported as
a typo.

## When the trainer mega evolves

At the first opportunity, meaning the first turn its active Pokémon is allowed to. That is what
trainers do in the games, and it cannot be the wrong call: mega evolving costs no turn.

- **Once per battle**, the same as for a player. In a double battle, whichever of the two
  Pokémon gets the chance first takes it.
- **Never on a switch**: the mega evolution goes out with the turn's move.
- **Difficulty changes nothing.** A trainer at `difficulty: 0` still mega evolves: the pack
  handed over the stone and wrote the word, and that is not a question of how well the AI plays.

## The stone

It is handed over like any other held item, on the Pokémon's first line:

```
Charizard @ Charizardite X
```

The name is written the way Showdown writes it. The mod looks it up in Cobblemon first, then
across every loaded mod - so it never needs to know the stone comes from Mega Showdown. Writing
the full ID (`@ mega_showdown:charizardite_x`) works too and settles any ambiguity.

**The Pokémon has to be able to hold that stone**: a Charizardite X on a Snorlax mega evolves
nothing, and that is the battle simulator's ruling, not the mod's.

## The fallback item

Without Mega Showdown the stone does not exist and the Pokémon shows up empty-handed - which
changes its battle more than the missing mega evolution does. The `Fallback Item:` line answers
that:

```
Charizard @ Charizardite X
Fallback Item: Life Orb
```

The first item that exists is the one held. The details are in
[DATAPACK.md](DATAPACK.md#the-fallback-item-line) - the rule covers every item, not just stones.

## The player's side

In Cobblemon, a player can only mega evolve with a **Key Stone** among their key items. A
trainer has no such requirement: they mega evolve even against a player who has none.

That is Cobblemon's rule and the mod leaves it alone, but it is worth planning for in a pack: a
trainer who mega evolves is harder than they look to a player early in the game. Putting the Key
Stone before the trainer, or locking the trainer behind
[`requires.items`](DATAPACK.md#locking-a-trainer), are two ways of making sure.

## Checking that it works

1. `/cobblemontrainers spawn <id>` to put the trainer down.
2. Battle them. On the first turn, the Pokémon should transform.
3. Nothing happens? In order: is Mega Showdown installed on both sides, does the stone match the
   species, and does the log say `Ignoring held item` when the pack loads?

`/cobblemontrainers debugai` adds a chat line the moment the trainer mega evolves, which tells
"it never does it" apart from "it does it but nothing shows".
