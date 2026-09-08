# Battle gimmicks

A trainer can Mega Evolve, fire a Z-Move, Dynamax or Terastallize mid-battle. That is the
`battle.gimmicks` field.

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

Both halves are needed: the **preparation** on the Pokémon - a stone, a Z-Crystal, a Tera type -
and the **word** in `gimmicks`. Handing over the stone without writing `["mega"]` makes a trainer
who never uses it. Dynamax is the only one that asks nothing of the Pokémon.

## Accepted values

| Value | Effect | Needs another mod |
| --- | --- | --- |
| `"mega"` | The trainer mega evolves as soon as the battle allows it | Yes, see below |
| `"zmove"` | The trainer fires its Z-Move on the turn it decides something | Yes, see below |
| `"max"` | The trainer Dynamaxes on the turn it decides something | Yes, see below |
| `"terastal"` | The trainer Terastallizes on the turn it decides something | No |

> Dynamax is written **`max`**, not `dynamax`: these four words are Cobblemon's own ids, taken
> as they are so that there is only one vocabulary to learn.

`ultra` (Ultra Burst) exists in Cobblemon but is **not supported yet**: writing it is reported in
the log at load time and does nothing. Any other word is reported as a typo.

A trainer may declare all of them. On a turn the battle offers several, only one goes out - an
answer carries a single gimmick - and the order never changes:

1. **Mega Evolution**, because it is not spent on anything: it costs no turn and cannot be the
   wrong call.
2. **The Z-Move**, then **Dynamax**, then **Terastallization**. All three only go out on the turn
   they decide something, so when two would answer the same question the cheapest one goes: a
   Z-Move is one hit and then nothing, a Dynamax runs for three turns, and a Terastallization is
   what the Pokémon *is* for the rest of the battle.

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

# The Z-Move

## What has to be installed

The same mod as for Mega Evolution: [Cobblemon: Mega
Showdown](https://modrinth.com/mod/mega-showdown), on both sides. It is what provides the
Z-Crystals and what teaches the simulator to see them. Without it, a trainer declaring
`["zmove"]` simply battles as usual and never fires one.

## The Z-Crystal

It is a held item, so it goes on the Pokémon's first line:

```
Pikachu @ Electrium Z
Level: 80
- Thunderbolt
- Volt Tackle
```

- A **type** crystal (`Electrium Z`, `Firium Z`, …) upgrades any move of that type.
- A **specific** crystal (`Aloraichium Z`, `Decidium Z`, …) only upgrades one particular move on
  one particular Pokémon, which has to know that move.

The item lookup rule and the `Fallback Item:` line are exactly the stone's, above.

## When the trainer fires its Z-Move

**Not at the first opportunity.** A side only gets one, and a Z-Move spent on a target that was
going down anyway is a Z-Move wasted. There is therefore a single trigger, the same as the
Terastallization's first one:

| It fires its Z-Move when… | In other words |
| --- | --- |
| The move it was about to play turns lethal on the Z-Move's power | It takes a knockout it did not have |

There is **no** second, defensive trigger, and that is not an omission: a Z-Move is one hit and
then nothing. The Z-status moves - Z-Parting Shot heals, Z-Celebrate boosts - would replace the
move the trainer had chosen with something else, which is the one thing a gimmick must not do
here. **On a status move the trainer therefore keeps its Z-Move in hand.**

- **Once per battle.** In a double battle, whichever of the two Pokémon it decides something for
  first takes it.
- **Never on a switch**: the Z-Move goes out with the turn's move.
- **A move with variable power** (Seismic Toss, Gyro Ball, Return…) keeps the Z in hand: its Z
  power cannot be worked out, and the mod would rather spend nothing than guess.
- **Difficulty never forbids it**, but it shows: the trigger looks at *the move already chosen*.
  A trainer at `difficulty: 0` picks at random, so it will rarely be holding the move that tips
  over. See [DIFFICULTY.md](DIFFICULTY.md).

---

# Dynamax

## What has to be installed

[Cobblemon: Mega Showdown](https://modrinth.com/mod/mega-showdown) again, on both sides: Dynamax
does not exist on a bare install.

**Nothing to prepare on the Pokémon.** Unlike the other three, Dynamax asks for no held item and
no team line: the word in `gimmicks` is enough. A player needs a Dynamax Band, but a trainer has
no such constraint - see [below](#the-player-though).

## When the trainer Dynamaxes

Like Terastallization, it waits for one of two moments - Dynamax does both things at once, it
lifts every move into a Max Move and it doubles the health bar:

| It Dynamaxes when… | In other words |
| --- | --- |
| The move it was about to play turns lethal on the Max Move's power | It takes a knockout it did not have |
| The incoming hit that would knock it out stops being lethal once its bar is doubled | It survives a turn it was losing |

- **Once per battle**, and it runs for three turns. The mod does not read those three turns: what
  they are worth depends on what the player does next, and either trigger already justifies the
  use on its own.
- **Never on a switch**: the Dynamax goes out with the turn's move.
- **Never on a status move.** Dynamax turns *every* status move into Max Guard, so Dynamaxing on
  a recovery or a setup move would replace the trainer's decision instead of attaching to it. It
  waits for a turn it is attacking.
- **Difficulty never forbids it**, with the same nuance as the other two: only the first trigger
  depends on the move already chosen, the second one plays at every difficulty.

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

**Without the line**, the Pokémon takes **its own primary type**: a Rhydon is Tera Ground, a
Gengar is Tera Ghost. Terastallizing is then a pure same-type boost with no defensive change -
useful, predictable, and never a surprise to the pack.

That default is the mod's. Left alone, Cobblemon rolls a Tera type at **random** (its
`teraTypeRate` setting), so the same trainer would Terastallize into a different type on every
spawn. The primary type is read **after** the form: an Alolan Vulpix is Tera Ice, not Tera
Fire.

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

In Cobblemon a player only gets a gimmick with the matching key item: a Key Stone for the mega, a
Z-Ring for the Z-Move, a Dynamax Band for Dynamax, a Tera Orb for the Terastallization. A trainer
has none of those constraints: it uses its own even against a player who has nothing.

That is Cobblemon's rule and the mod does not touch it, but it is worth preparing for on the pack
side: a trainer that Terastallizes is harder than it looks for a player early on. Putting the
item behind the trainer, or locking the trainer with
[`requires.items`](DATAPACK.md#locking-a-trainer), are two ways to make sure.

## Checking that it works

1. `/cobblemontrainers spawn <id>` to place the trainer.
2. Battle it.
   - Mega: the Pokémon should transform on the first turn.
   - Z-Move, Dynamax, Terastal: you have to give it a reason. Bring its active Pokémon to the
     edge of a knockout, or put in front of it a target it only just fails to take out.
3. Nothing happens?
   - For mega and the Z-Move: is Mega Showdown installed on both sides, does the item match the
     species and the move, and does the log say `Ignoring held item` when the pack loads?
   - For Dynamax: is Mega Showdown installed on both sides?
   - For terastal: does the log say `Ignoring unknown Tera type`? If not, the moment most likely
     never came.

`/cobblemontrainers debugai` adds a chat line the moment a trainer uses a gimmick, with the
reason for the three that wait for theirs. That is what tells "it never does it" apart from "it
is waiting for its moment".

The example pack ships three trainers built for this: `cobblemonrlm:terastal`,
`cobblemonrlm:zmove` (four Z-Crystals, each with its own `Fallback Item:`) and
`cobblemonrlm:dynamax` (nothing to hand the Pokémon, just the word). The last two only fire
their gimmick with Mega Showdown installed; without it they simply battle as usual.
