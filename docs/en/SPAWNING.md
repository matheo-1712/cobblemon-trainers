# Calling a trainer over

A trainer can be **called** by a player from the Battle Phone. The player opens the trainer's
page, presses **Call**, and the trainer turns up a few dozen blocks away.

The mod never spawns a trainer on its own. There are only three ways to see one:

| Route | Who triggers it | What for |
| --- | --- | --- |
| The trainer spawner block | An operator placing the block | A trainer who lives in a fixed spot |
| `/cobblemontrainers spawn` | An operator | Testing, troubleshooting |
| The Battle Phone | Any player | A trainer who comes when called |

This page only covers the third one. For the rest of the format, see
[DATAPACK.md](DATAPACK.md).

*Cette page existe aussi [en français](../SPAWNING.md).*

## Contents

- [The `location` block](#the-location-block) · [The conditions](#the-conditions) ·
  [The text](#the-text)
- [What the player sees](#what-the-player-sees) · [What the mod does](#what-the-mod-does)
- [The values the mod fixes](#the-values-the-mod-fixes)
- [Testing](#testing) · [Common mistakes](#common-mistakes)

## The `location` block

**Naming a place in the `location` block is what makes a trainer callable.** There is no other
switch. A trainer naming no place has no button: that is how you write a champion you have to go
and find in their gym.

So the block does two distinct things, and you may well want only one of them:

| What is in the block | Place shown | Call button |
| --- | --- | --- |
| Nothing, or no block at all | no | no |
| A `label` only | yes | **no** |
| At least one condition | yes | yes |

A `label` on its own is therefore perfectly valid: the champion says where to find them, and
does not go anywhere. That is the normal case for a trainer held somewhere in the world by a
spawner block.

```json
{
  "name": "Singles Ace",
  "location": {
    "biome": "#minecraft:is_badlands",
    "time": "night"
  }
}
```

The shortest possible thing works: one field is enough.

### The conditions

All optional, all cumulative: they add up, they are never alternatives. They are checked
**once**, the moment the player presses the button.

| Field | Example | What is tested |
| --- | --- | --- |
| `dimension` | `minecraft:the_nether` | The player's dimension |
| `biome` | `minecraft:desert` | The biome under the player |
| `biome` with `#` | `#minecraft:is_desert` | Any biome in the tag |
| `structure` | `minecraft:village_desert` | The player is **standing on** a generated piece of the structure |
| `structure` with `#` | `#minecraft:village` | Any structure in the tag |
| `area` | `{ "from": [100, -200], "to": [400, 100] }` | The player is inside the box, in `x` and `z` |
| `minY` | `0` | Minimum height, inclusive |
| `maxY` | `62` | Maximum height, inclusive |
| `time` | `day`, `night` | The world's time |
| `weather` | `clear`, `rain`, `thunder` | The weather **at the player's position** |

`area` only covers `x` and `z`; height is `minY` and `maxY`, so that one idea is never written
in two places. The two corners of `area` can be given in any order.

`weather` is read at the position, not on the world: it does not rain in a desert, and a player
standing in one is not in the rain whatever the sky says elsewhere.

An ID that does not exist is a condition **nothing meets**, deliberately: counting a typo as met
would open the trainer up to everybody.

### The text

| Field | Default | What it does |
| --- | --- | --- |
| `label` | the mod words it | What the Battle Phone shows instead of the automatic description |
| `arrival` | the mod words it | What the trainer says on arriving, with their coordinates |
| `busy` | the mod words it | What they say when another copy of them is already nearby |

`label` is the only one of the three that still means something to a trainer who cannot be
called. The other two are only ever said during a call: writing them without naming a place is
reported in the log.

```json
"location": {
  "label": "in the Celadon City gym"
}
```

That is a champion. The player reads where they are, and walks there.

All three are translation keys, like `name` and the `messages`: put a key in and translate it in
your resource pack, or put plain text in and it shows up as is. See
[Translating your text](DATAPACK.md#translating-your-text).

`arrival` receives **three arguments**: `x`, `y`, `z`. Write them as `%s %s %s`.

```json
"location": {
  "biome": "#minecraft:is_badlands",
  "time": "night",
  "label": "in the badlands, at night",
  "arrival": "I am at %s %s %s. Do not keep me waiting.",
  "busy": "I am already around here, open your eyes."
}
```

**When to write a `label`.** The mod names what it can: a dimension and a biome have a
translation in the game, a structure ID and a box of coordinates do not and never will. A
trainer pinned down by a structure or an `area` therefore deserves a `label`.

## What the player sees

On the trainer's page, under their team:

- **A location line**, `Found: in the badlands, at night`. It is visible before the trainer has
  been beaten - unlike their team, which is a reward.
- **A Call button**, on the right. It only exists for a callable trainer.

The button is greyed out for a trainer already beaten who refuses a rematch
(`"progress": { "rematch": "never" }`). Hovering it says why.

The button is **not** greyed out when the player is in the wrong place: the client does not know
where the player stands relative to the condition, it only knows how the condition reads. The
server is what answers, in the chat, listing what is missing:

```
Singles Ace is not here. Look for them:
- biomes #minecraft:is_badlands
- at night
```

Pressing the button closes the Battle Phone: the answer is a chat message, and it has to be
readable.

## What the mod does

On a call, in this order:

1. The trainer exists, is listed, and is not hidden from this player.
2. They declare a `location` block.
3. The player meets their `requires` - see [locking a
   trainer](DATAPACK.md#locking-a-trainer). **A locked trainer can never be called.**
4. They have not already been beaten by a trainer refusing a rematch.
5. They are not sulking at this player (see below).
6. The player is in the right place.
7. No other copy of the same trainer is around.

Then:

- The trainer this player had already called is sent away. **One called trainer per player.**
- The new one turns up between 10 and 20 blocks away, facing the player.
- The player gets a message with the coordinates. **Only them.**

After that:

| Event | What happens |
| --- | --- |
| The battle ends | The trainer leaves a few seconds later, won or lost |
| The player walks off, changes dimension or disconnects | The trainer leaves |
| The player calls somebody else | The first one leaves |
| The trainer dies | The call is lost, and they sulk at that player for 5 minutes |
| The trainer is mid-battle | **They are never removed**, whatever the player does - and a new call is refused for as long as it lasts |

The battle starts on a **right-click on the trainer**, as everywhere else in the mod. Calling
somebody over is not challenging them.

In multiplayer, **any player can challenge a trainer called by somebody else**, provided they
meet the `requires` themselves. Progress stays individual.

**Nothing is saved.** A call lasts a few minutes; restarting the server forgets them all and
clears out the trainers left behind.

## The values the mod fixes

These numbers cannot be set in a datapack. They are the same for every trainer, so that a player
learns the rule once.

| Setting | Value |
| --- | --- |
| Arrival distance | 10 to 20 blocks |
| If nothing in that ring works | It looks **closer**, down to 3 blocks |
| If nothing close works either | It looks further, up to 64 blocks |
| Vertical search | 8 blocks above and below the player |
| Refused if another copy is closer than | 100 blocks |
| The trainer leaves if the player goes past | 128 blocks |
| Leaving after a battle | 5 seconds |
| Sulking after a death | 5 minutes |
| Trainers called at once | 1 per player |

**Why the trainer arrives far away.** A trainer materialising under the player's nose is not an
encounter. The position is searched around the player's height, not at the surface: a player in
a cave would otherwise get their trainer on the roof, thirty blocks and a wall away.

**Why they sometimes arrive close.** When the 10-to-20-block ring has nowhere to stand - a
clearing ringed by water, a ledge -, the mod looks **closer** before it looks further. Arriving
five blocks away beats arriving on the other side of the ridge. It never goes below 3 blocks: a
trainer materialising inside the player would be worse than anything.

**The trainer never arrives in water.** They need solid dry ground, room for a body, and no
liquid - not at their feet, not at their head, and not in the block they stand on. That last
point matters: a submerged slab, a step below the surface or a block of ice in a lake are all
solid enough to stand on and would put the trainer ankle-deep in water.

**Why they still do not always turn up.** The mod loads no chunk to answer a button press. In
the middle of an ocean there is nothing dry between 3 and 64 blocks anyway, and the call is
refused - the player gets back to dry land and tries again.

## Testing

```bash
/reload
```

Then, in game:

- `/cobblemontrainers list` says which trainers are loaded.
- Open the Battle Phone: a callable trainer has a location line and a button.
- `/cobblemontrainers spawn <id>` is still the way to test a trainer **without** going through
  their location.
- A value in `location` that is not understood is reported in the server log on load - look for
  the `(cobblemon-trainers)` lines.

To test a place you are not standing in, `/locate biome`, `/locate structure` and
`/time set night` save time.

## Common mistakes

| Symptom | Cause |
| --- | --- |
| No button on the page | The trainer has no `location` block |
| No button, but the place shows up | The block only has a `label`: that is intended, add a condition to make them callable |
| No button, and `arrival` is ignored | The block names no place; the log says so on load |
| No button, and the trainer is locked | An unmet `requires`; the button comes back once it is met |
| The trainer is nowhere in the list | `"progress": { "listed": false }`, or a `requires` with `hidden` |
| "is not here" while standing right there | A misspelled biome or structure ID; the mod counts it as unmet |
| The place shown is an unreadable ID | A structure or an `area`: add a `label` |
| The button is greyed out | Trainer already beaten with `"rematch": "never"` |
| "I am already somewhere around here" | A copy of the same trainer is within 100 blocks, placed by a block or called |
| The trainer vanishes for no reason | The player went more than 128 blocks away, or changed dimension |
