# Writing a league

A **league** is a tournament: the player signs up with a **host**, then the trainers come one
after the other, **with no healing between two battles**. It is the Elite Four: you walk in, you
keep going, and the team that faces the last one is the team the first ones left you.

*Cette page existe aussi [en français](../LIGUES.md).*

## The file

A league lives in `data/<namespace>/cobblemontrainers/leagues/<path>.json`, next to `trainers/`
and `intro/`. Its ID is `<namespace>:<path>`, `leagues/` not counted.

```json
// data/my_pack/cobblemontrainers/leagues/council.json → my_pack:council
{
  "name": "The Council",
  "host": {
    "name": "Council Referee",
    "skin": { "type": "player_username", "value": "Notch" },
    "messages": { "greeting": "Three trainers in a row. Are you in?" }
  },
  "trainers": ["council/gatekeeper", "council/strategist", "my_pack:council/master"],
  "rewards": [
    { "item": "cobblemon:master_ball", "firstWinOnly": true }
  ]
}
```

| Field | Default | Role |
| --- | --- | --- |
| `name` | the ID | Displayed name; plain text or a translation key, like a trainer's `name` |
| `host` | - | The character a player signs up with, see below |
| `trainers` | - | The trainers, **in order**. A path without a namespace is taken from the league's own |
| `rewards` | - | What the finished league is worth, on top of each trainer's rewards. Same entries as [a trainer's](DATAPACK.md#rematches-and-rewards); `firstWinOnly` is counted on the league |

The trainers named are ordinary trainers from `trainers/`. **The league spawns them itself**,
when their turn comes, and takes them away afterwards: there is nothing to place for them. They
arrive next to the player, or at [the arena](#the-arena) if the league has one. A copy of the
same trainer standing elsewhere in the world stays a normal trainer.

## The host

The host is **not a trainer**: it has no team and no battle, and exists only in the league's
file.

| Field | Default | Role |
| --- | --- | --- |
| `host.name` | the league's name | Its name |
| `host.skin` | Steve | The same block as a [trainer's `skin`](DATAPACK.md#skins) |
| `host.messages.greeting` | a line of the mod's | Said on right-click, above *Enter / Cancel* |
| `host.messages.decline` | - | Said to whoever picks *Cancel* |
| `host.messages.between` | - | Said between two battles, above the progress line |
| `host.messages.win` | a line of the mod's | Said once the last trainer is beaten |
| `host.messages.lose` | a line of the mod's | Said, in chat, to a player whose tournament has just ended |

Place it with `/cobblemontrainers league <id> [<x> <y> <z>]` (operator). It stays until it is
removed, restarts included.

## The arena

By default, each trainer appears a few blocks from the player. An **arena** fixes the place:
every trainer of the league appears there, turned towards the player.

```
/cobblemontrainers league <id> arena set [<x> <y> <z>]
/cobblemontrainers league <id> arena clear
```

| Form | Effect |
| --- | --- |
| `arena set` | The arena is where you stand, in your dimension |
| `arena set <x> <y> <z>` | At those coordinates, `~` and `^` included |
| `arena clear` | No arena any more: the trainers come next to the player again |

- **The arena belongs to the world, not to the pack**: it is written to the save, so it survives
  restarts and `/reload`, and one pack has one arena per world.
- **One arena per league.** Setting it again replaces the old one.
- **The point is taken as written**, with no search for free ground around it: picking a clear
  one is up to you.
- **Put it near the host.** A player further than Cobblemon's flee distance
  (`defaultFleeDistance`), or in another dimension, could not hold the battle: for them the
  trainer appears at their side, as with no arena.

## How it plays

1. **Right-click the host**, then *Enter*. The host turns down a player with no Pokémon able to
   battle, one already in a tournament, or one who comes during somebody else's.
2. **The first trainer appears** - a few blocks away, or at the arena - and their battle opens
   by itself, with their
   [versus screen](INTROS.md) if they have one. Their `requires` and rematch rule are not
   consulted: here the league decides.
3. **Battle won**: the trainer says their closing word, disappears, and **the host's box opens**
   - where you stand, who comes next, and two buttons, *Continue* or *Forfeit*. Closing the box
   with Escape continues.
4. **The next one appears**, and so on.
5. **Once the last one is beaten, the league is finished**: rewards, the advancement criterion,
   and the host congratulates the player.

**Any battle that does not end in a win stops the tournament**: a loss, fleeing, walking too
far, `/stopbattle`, disconnecting. You go back to the host and start from the first trainer.

Each trainer beaten counts as a normal victory: their rewards are handed over, their Battle
Phone line is ticked, their advancements fire.

**A league takes one player at a time**: while a tournament is running, the host turns the
others down and names whoever is playing, whichever copy of the host they talk to. The place
frees up as soon as that tournament finishes or stops. Two different leagues run at the same
time without getting in each other's way.

## No healing

At the end of each battle won, the team is noted: **HP, status and PP** of every Pokémon. When
the next one opens, it is **brought back down** to that.

- It is a ceiling, not a restore: a Pokémon that *lost* HP in between keeps it lost.
- Whatever a player manages to heal between two battles is therefore taken back, and a message
  says so. Items spent on it are spent.
- A fainted Pokémon stays fainted.
- **No Pokémon may join the team** during a tournament: that stops it. Taking one out is
  allowed.

## Advancement

Finishing a league fires `cobblemon-trainers:league_completed`.

| Condition | Role |
| --- | --- |
| `league` | The league finished: a full ID (`my_pack:council`) or a bare path |
| `pack` | Its namespace |

Both are optional. Beating every trainer of a league one by one does not finish it: it is the
last battle of a tournament that counts.

## Good to know

- **A league naming a trainer that cannot be found is ignored whole**: it would be missing a
  link.
- **`_50` formats carry nothing over.** They are played on healed copies of the team: that
  battle leaves no trace on the next. Loading says so.
- **With no arena, there has to be room around the player**: the next trainer looks for free ground a few
  blocks away, on the same floor. If there is none, the tournament stops.
- A `/reload` or a restart in the middle of a tournament loses it; the trainer left in the world
  is removed on load.
- The Battle Phone does not show leagues yet.
