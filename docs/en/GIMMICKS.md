# Battle gimmicks

A trainer can Mega Evolve or Terastallize mid-battle. That is the `battle.gimmicks` field.

For setting the field on a trainer, see [DATAPACK.md](DATAPACK.md#field-reference).

*Cette page existe aussi [en français](../GIMMICKS.md).*

## In one minute

```json
{
  "name": "Peter",
  "battle": {
    "level": 80,
    "gimmicks": ["mega", "terastal"]
  },
  "team": [
    "Charizard @ Charizardite X\nFallback Item: Life Orb\nLevel: 80\n- Flare Blitz\n- Dragon Claw",
    "Garganacl @ Leftovers\nTera Type: Fairy\nLevel: 80\n- Salt Cure\n- Recover"
  ]
}
```

Both halves are needed: the **preparation** on the Pokémon - a stone, a Tera type - and the
**word** in `gimmicks`. Handing over the stone without writing `["mega"]` makes a trainer who
never uses it.

## Accepted values

| Value | Effect | Needs another mod |
| --- | --- | --- |
| `"mega"` | The trainer mega evolves as soon as the battle allows it | Yes, see below |
| `"terastal"` | The trainer Terastallizes on the turn it decides something | No |

`zmove`, `dynamax` and `ultra` exist in Cobblemon but are **not supported yet**: writing one is
reported in the log at load time and does nothing. Any other word is reported as a typo.

A trainer may declare both. On the turn the battle offers them at once, the mega evolution goes
out: it is tied to the Pokémon holding the stone, whereas a Terastallization belongs to the side
and loses nothing by waiting.

---

# Mega Evolution

## What has to be installed

| Requirement | Why |
| --- | --- |
| [Cobblemon: Mega Showdown](https://modrinth.com/mod/mega-showdown) on the server | It is what provides the Mega Stones and the mega form |
| The same mod on the client | Otherwise the player never sees the transformation |

Cobblemon Trainers **does not depend** on Mega Showdown: the mod loads, runs and plays without
it. A trainer declaring `["mega"]` simply battles as usual, never mega evolving.

## When the trainer mega evolves

At the first opportunity, meaning the first turn its active Pokémon is allowed to. That is what
trainers do in the games, and it cannot be the wrong call: mega evolving costs no turn.

- **Once per battle**, the same as for a player. In a double battle, whichever of the two
  Pokémon gets the chance first takes it.
- **Never on a switch**: the mega evolution goes out with the turn's move.
- **Difficulty changes nothing.** A trainer at `difficulty: 0` still mega evolves: the pack
  handed over the stone and wrote the word, and that is not a question of how well the AI plays.

## The stone

It is given like any other held item, on the Pokémon's first line:

```
Charizard @ Charizardite X
```

The name is written as it is on Showdown. The mod looks for it in Cobblemon first, then across
every loaded mod - so it never has to know the stone comes from Mega Showdown. Writing the full
ID (`@ mega_showdown:charizardite_x`) works too and removes any ambiguity.

**The Pokémon has to be able to hold that stone**: a Charizardite X on a Snorlax mega evolves
nothing, and that is the battle simulator's call, not the mod's.

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

---

# Terastallization

Nothing to install: Cobblemon ships the Tera types, the Tera Orb and the animation. A trainer
declaring `["terastal"]` works on a bare install.

## The Tera type

It is declared with the `Tera Type:` line, the one from Showdown exports:

```
Garganacl @ Leftovers
Ability: Purifying Salt
Tera Type: Fairy
- Salt Cure
- Recover
```

All eighteen types are accepted, plus `Stellar`. An unknown name is reported in the log and
ignored.

**Without the line**, the Pokémon keeps whatever Tera type Cobblemon gives it by default. The
trainer still Terastallizes, but into a type the pack never chose - so write the line as soon as
you write `terastal`.

## When the trainer Terastallizes

**Not at the first opportunity.** A side only gets one, and spending it on turn one because it
was offered is losing it. The trainer waits for one of these two moments:

| It Terastallizes when… | In other words |
| --- | --- |
| The move it was about to play turns lethal on the Tera bonus | It takes a knockout it did not have |
| The incoming hit that would knock it out stops being lethal against its Tera type | It survives a turn it was losing |

Outside those two, it holds on to it. **A trainer can therefore finish a battle without ever
Terastallizing** - that means the moment never came, not that the word is misspelt.

- **Once per battle.** In a double battle, whichever of the two Pokémon it decides something for
  first takes it.
- **Never on a switch**: the Terastallization goes out with the turn's move.
- **Difficulty never forbids it**, but it shows all the same: the first of the two triggers looks
  at *the move already chosen*. A trainer at `difficulty: 0` picks at random, so it will rarely
  be holding the move that turns lethal. A `difficulty: 5` trainer already chooses well, and
  therefore Terastallizes more often. See [DIFFICULTY.md](DIFFICULTY.md).

### The Stellar case

`Tera Type: Stellar` grants no type: it changes no resistance. Only the first trigger - the
secured knockout - applies, with the Stellar bonus (×2 on a move that was already same-type, ×1.2
on anything else). A Stellar Pokémon may never Terastallize if none of its moves tips over.

---

## The player, though

In Cobblemon a player cannot mega evolve without a **Key Stone** among their key items, nor
Terastallize without a **Tera Orb**. A trainer has neither constraint: it uses its gimmicks even
against a player who has neither.

That is Cobblemon's rule and the mod does not touch it, but it is worth preparing for on the pack
side: a trainer that Terastallizes is harder than it looks for a player early on. Putting the
item behind the trainer, or locking the trainer with
[`requires.items`](DATAPACK.md#locking-a-trainer), are two ways to make sure.

## Checking that it works

1. `/cobblemontrainers spawn <id>` to place the trainer.
2. Battle it.
   - Mega: the Pokémon should transform on the first turn.
   - Terastal: you have to give it a reason. Bring its active Pokémon to the edge of a knockout,
     or put in front of it a target it only just fails to take out.
3. Nothing happens?
   - For mega: is Mega Showdown installed on both sides, does the stone match the species, and
     does the log say `Ignoring held item` when the pack loads?
   - For terastal: does the log say `Ignoring unknown Tera type`? If not, the moment most likely
     never came.

`/cobblemontrainers debugai` adds a chat line the moment a trainer uses a gimmick, with the
reason for a Terastallization. That is what tells "it never does it" apart from "it is waiting
for its moment".

The example pack ships `cobblemonrlm:terastal`, a trainer built for it.
