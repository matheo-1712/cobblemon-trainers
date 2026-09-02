# Trainer difficulty

`battle.difficulty` runs from `0` to `5` and defaults to `5`. This page says exactly what each
value does.

Two things sit on top of each other: Cobblemon's AI, which decides, and a layer from the mod,
which turns some of those decisions down. Difficulty drives both.

To set the field on a trainer, see [DATAPACK.md](DATAPACK.md#battle).

*Cette page existe aussi [en français](../DIFFICULTE.md).*

## At a glance

| Level | The AI plays at random | It switches Pokémon when it wants to | The mod corrects |
| --- | --- | --- | --- |
| `0` | every turn | never | nothing |
| `1` | 4 turns out of 5 | never | nothing |
| `2` | 3 turns out of 5 | never | nothing |
| `3` | 2 turns out of 5 | 1 time in 5 | the impossible mistakes, screens included |
| `4` | 1 turn out of 5 | 3 times in 5 | the same, plus entry hazards and screens |
| `5` | never | always | everything, reading the battle included |

A `0` trainer is not "a bit worse" than a `5`: they literally play a random move every turn. The
range that matters to a player sits between `3` and `5`.

## What Cobblemon does

Two internal rolls depend on the level, and nothing else.

**The skill roll.** Every turn, the AI rolls a die. On a failure it plays a **random** move among
the usable ones and thinks no further. On a success, its whole logic runs: matchup, statuses,
boosts, weather, protection, strongest move. The chance of success is `level × 20%`, and level
`5` always succeeds.

**The switch roll.** When its logic concludes that it should switch Pokémon, a second die decides
whether it obeys: never below `3`, 20% at `3`, 60% at `4`, always at `5`.

That second roll explains a counter-intuitive behaviour: **switching loops only show up at level
5**. Cobblemon's switching logic has no memory of the previous turn, so it can suggest fleeing
every single turn; at levels 3 and 4 the die breaks the loop by chance. At level 5 there is no
die left. It is the first thing the mod's layer corrects.

## Levels 0, 1 and 2 — Cobblemon alone

The mod corrects **nothing**. The trainer plays exactly like any Cobblemon NPC, mistakes
included: they can attack an immune target, throw a status that cannot land, waste their best
move. They never switch Pokémon of their own accord.

That is deliberate. An early-game trainer has to be beatable by a player discovering the game,
and an AI that never makes a mistake is not.

## Level 3 — the impossible mistakes

The mod's layer switches on and turns down what cannot work.

### No more attacking an immune target

An offensive move whose effectiveness is ×0 against every target is no longer played: the trainer
plays their best remaining move instead. Ground on a Flying, Normal on a Ghost, Poison on a
Steel, Electric on a Ground, Psychic on a Dark, Dragon on a Fairy.

Only the type chart is read at this level. An ability granting immunity (Levitate, Volt Absorb)
is **not** seen — that comes at level 5.

This rule also catches the random roll: a move picked at random that happens to be immune is
turned down like any other.

### No re-laying a screen that is up

Reflect, Light Screen and Aurora Veil are no longer replayed while the screen holds: Cobblemon
files them among its setup moves and plays them on a coin flip, without ever looking at what is
already standing - which cost a turn and put a "But it failed!" in the chat. The trainer attacks
instead.

Three cases are turned down:

| Case | Why |
| --- | --- |
| The screen is already standing | Screens do not stack |
| Aurora Veil is standing | It is already Reflect and Light Screen |
| Aurora Veil with no snow or hail | The move fails |

A screen that has been set counts as standing for eight turns, the length a Light Clay gives it.

### A switch has to be worth something

A voluntary switch is only accepted if the Pokémon coming in genuinely improves the matchup — by
at least half a step of type effectiveness. Fleeing towards a Pokémon just as badly off is no
longer possible, which removes the loop.

Four situations are exempt, because the trainer is not looking for a better matchup then:

| Situation | Why the switch goes through |
| --- | --- |
| HP under 30% | They are trying to save a Pokémon, not win the exchange |
| A stat down to -3 or worse | Switching out clears the drops |
| Every move resisted (×0.5 or less) | They are walled: staying leads nowhere |
| No move does anything at all | A complete dead end |

In that last case the trainer leaves **even if Cobblemon had not suggested it** — but only
towards a strictly better replacement, otherwise the loop would come back.

## Level 4 — the game plan

Everything level 3 does, plus two rules. They are the only two in the whole system that **add**
a decision: everywhere else the layer merely turns Cobblemon's own down.

### Entry hazards

If the **first** Pokémon sent out knows an entry hazard, they set it on turn one. Order of
preference when they know several:

1. Stealth Rock — hits everything coming in and ignores Flying and Levitate
2. Spikes
3. Toxic Spikes
4. Sticky Web

One hazard is set per trainer, so the two leads of a double battle do not set the same one twice.
No other Pokémon on the team will set one later.

### Screens, with a Light Clay

A Pokémon holding a **Light Clay** that knows Reflect, Light Screen or Aurora Veil puts its
screen up instead of attacking. The item is what triggers the rule: it does nothing at all
without a screen, so a pack that handed one over has already made the decision.

| It sets | When |
| --- | --- |
| Aurora Veil | Snow or hail is on the field - the screen then covers both kinds of damage at once |
| Reflect | The hardest hit coming is physical |
| Light Screen | The hardest hit coming is special |

- **Never the same one twice**: a screen that has been set counts as standing for eight turns,
  the length a Light Clay gives it. The trainer moves on to something else the very next turn.
- **Nothing follows Aurora Veil**: it is Reflect and Light Screen in one, so setting either
  afterwards would only spend turns.
- **Aurora Veil is never played outside snow**: the move fails, and Showdown offers it anyway.
- **Four situations hand the turn back**: a knockout is available this turn, the Pokémon is
  going down this turn, Cobblemon wanted to heal, or it was already playing a screen of its own.

Without a Light Clay nothing changes: Cobblemon plays its screens like any other setup move, on
a coin flip and with no idea whether one is already up.

## Level 5 — reading the battle

Everything levels 3 and 4 do, plus all of the below.

### Ability and item immunities

Effectiveness now accounts for what cancels a type beyond the chart:

| Ability or item | Type cancelled |
| --- | --- |
| Levitate, Earth Eater, Air Balloon | Ground |
| Volt Absorb, Motor Drive, Lightning Rod | Electric |
| Water Absorb, Dry Skin, Storm Drain | Water |
| Flash Fire, Well-Baked Body | Fire |
| Sap Sipper | Grass |
| Wonder Guard | anything not super effective |

A Mewtwo will no longer throw Earthquake at a Levitate.

### Statuses that cannot land

Toxic on a Poison or a Steel, Thunder Wave on an Electric, Will-O-Wisp on a Fire, or any status
on a target already carrying one: the move is turned down and the trainer plays something else.

### The strongest hit by default

When Cobblemon picks an offensive move **for no declared reason** — meaning not a status, not a
boost, not a hazard, not a heal, not a protection — and another one deals more damage, that one
is played instead.

This rule neutralises Cobblemon's leftover randomness without flattening its deliberate play: a
move it cares about is recognised as such and never traded for damage.

### Healing at the right time

Cobblemon heals as soon as HP drops below half, playing the first healing move it finds, without
looking at anything else. Three questions are asked before letting it:

- **Is a KO in hand?** If a move knocks the opponent out this turn, it comes first.
- **Does the heal buy the turn back?** If the opponent deals at least as much as is restored, the
  trainer ends the turn where they started it: they attack instead.
- **Will the heal be absorbed?** If less than 60% of what the move restores is missing, the rest
  overflows: the trainer attacks and keeps the heal for later.

**Known limit:** the layer can turn a heal down, never add one. So a trainer will never heal
above half their HP, even when that would be the right play.

### What survives a hit you thought was decisive

The trainer no longer believes in a KO the target would survive:

- **Sturdy** and a **Focus Sash** at full HP, which leave 1 HP.
- An intact **Disguise** or **Ice Face**, which cancels the whole hit.

And in the Disguise or Ice Face case, they do not waste their best move on it: they break it with
their **weakest** move and keep the big one for the turn after.

The state of the guard is read two ways — the Pokémon's form (a busted Mimikyu, an Eiscue without
its ice) and the memory of having already hit it. Whichever of the two says "broken" first wins.

### Knocking out first

If a move knocks an opponent out **and** the trainer strikes before them — speed and priority
compared —, it is played whatever else they had planned.

A speed tie counts as lost. In game it is a coin flip, and an AI assuming it wins every coin flip
plays recklessly.

The priority credited to the opponent is that of their **most dangerous** move against the
target, not the highest-priority one in their arsenal. Otherwise a player carrying a plain Quick
Attack would be enough to convince the trainer they never move first.

Trick Room and Tailwind are not taken into account: the order can therefore be misjudged under
Trick Room.

### A last turn goes to what still resolves

When the trainer is going down this turn and strikes second, the move they planned will never
resolve. Only a **priority** move still goes off: that is the one they play, the strongest among
those that move first.

A Mimikyu boosted by Swords Dance and about to fall therefore plays Shadow Sneak rather than Play
Rough — the first goes off, the second arrives after the KO.

If they have no priority move, nothing resolves anyway. They still attack rather than set up: the
estimate can be off by a damage roll or a miss, and a boost set up for a turn that never comes is
lost for certain.

### No two Protects in a row

Protect, Detect and the like are not replayed two turns running: the second attempt has a one in
three chance of working and gives away a free turn when it fails.

This rule covers the **trainer's** protections only. Counting the player's would need state the
layer has no access to.

## What is never corrected, at any level

- **The replacement sent out after a KO.** That choice is left entirely to Cobblemon.
- **A forced move**: Encore, Outrage, an item locking the choice. There is nothing to decide.
- **Battle gimmicks** (Mega Evolution, Terastallization). They do not depend on `difficulty`
  at all: the pack declares them, the AI does not have a bright idea. See
  [GIMMICKS.md](GIMMICKS.md). A Terastallization still reads better at `difficulty: 5` - one of
  its two triggers looks at the move already chosen, and a trainer that chooses badly is rarely
  holding the right one.
- **The trainer's level, stats and team.** `difficulty` is how well they play, not how strong
  the team is. A level 100 trainer at `difficulty: 0` is still easy, a level 20 trainer at
  `difficulty: 5` is still weak.

Finally, the layer only judges what Cobblemon hands it. Apart from the two level 4 rules - the
entry hazard and the screen under a Light Clay - it cannot make a trainer play an action
Cobblemon had not thought of.

## Checking in game

```
/cobblemontrainers debugai
```

While the switch is on, every corrected decision shows up in your chat during the battle, with
its reason and the numbers behind it. Run the command again to turn it off.

It is the only way to tell "the trainer did not want to switch" apart from "the trainer wanted to
switch and the mod stopped them". Operators only, like the rest of the command.

## Which level to pick

| For | Level |
| --- | --- |
| A first trainer, a tutorial | `0` to `1` |
| A route trainer | `2` to `3` |
| A rival, a gym leader | `4` |
| A champion, a final boss | `5` |

At `5`, a trainer plays better than most casual players. Compensate with the team rather than
with the level if the battle is meant to stay approachable: a shorter or less well built team
still reads clearly to the player, whereas a deliberately stupid AI reads as a bug.
