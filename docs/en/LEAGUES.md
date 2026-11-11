# Writing a league

A **league** is a run of trainers beaten in order, **where nothing healed between two battles
counts**. It is the Elite Four: you walk in, you keep going, and the team that faces the last
one is the team the first ones left you.

*Cette page existe aussi [en français](../LIGUES.md).*

## The file

A league lives in `data/<namespace>/cobblemontrainers/leagues/<path>.json`, next to `trainers/`
and `intro/`. Its ID is `<namespace>:<path>`, `leagues/` not counted.

```json
// data/my_pack/cobblemontrainers/leagues/council.json → my_pack:council
{
  "name": "The Council",
  "trainers": ["council/gatekeeper", "council/strategist", "my_pack:council/master"],
  "heal": 0,
  "rewards": [
    { "item": "cobblemon:master_ball", "firstWinOnly": true }
  ]
}
```

| Field | Default | Role |
| --- | --- | --- |
| `name` | the ID | Displayed name; plain text or a translation key, like a trainer's `name` |
| `trainers` | - | The trainers, **in order**. A path without a namespace is taken from the league's own |
| `heal` | `0` | Share of its max HP given back to each Pokémon still standing between two battles, from `0` to `1` |
| `rewards` | - | What the finished league is worth, on top of each trainer's rewards. Same entries as [a trainer's](DATAPACK.md#rematches-and-rewards); `firstWinOnly` is counted on the league |

The trainers themselves are ordinary trainers: the league does not spawn them. Place them
wherever you like, for instance with one [trainer block](SPAWNING.md) per room.

## How it plays

- **The first trainer battles anyone.** Challenging them starts a run - or starts it again.
- **The others only battle the player whose run has reached them.** Everyone else is turned down
  in the dialogue box, which names the next opponent.
- **Winning moves the run on**, and the chat says where it stands.
- **Any battle that does not end in a win ends the run**: a loss, fleeing, walking too far,
  `/stopbattle`. You start again from the first trainer.
- **Beating the last one finishes the league**: rewards, the advancement criterion, and the
  league is recorded as finished for that player.

A run belongs to one player, and a player has one at a time. It lives in memory: a server
restart loses it. Disconnecting does not.

## What is kept between two battles

At the end of each battle won, the team is noted: **HP, status and PP** of every Pokémon. When
the next one opens, it is **brought back down** to that.

- It is a ceiling, not a restore: a Pokémon that *lost* HP in between keeps it lost.
- A player may therefore go and heal between two rooms - it buys them nothing, and a message
  says so. Items spent on it are spent.
- A fainted Pokémon stays fainted, whatever `heal` says.
- **No Pokémon may join the team** between two battles: the next trainer refuses for as long as
  it is there. Taking one out is allowed.
- `heal: 1` switches all of this off: the league is only an order, and the team is free.

## Advancement

Finishing a league fires `cobblemon-trainers:league_completed`.

| Condition | Role |
| --- | --- |
| `league` | The league finished: a full ID (`my_pack:council`) or a bare path |
| `pack` | Its namespace |

Both are optional. Beating every trainer of a league one by one does not finish it: it is the
last battle of a run that counts.

## Good to know

- **A trainer belongs to one league only.** The second one naming it is ignored, with an error
  in the log.
- **A league naming a trainer that cannot be found is ignored whole**: it would be missing a
  link.
- **Avoid `progress.rematch: "never"` on a member**: a player who loses further down could
  never start over. Loading says so.
- **`_50` formats carry nothing over.** They are played on healed copies of the team: that
  battle leaves no trace on the next. Loading says so too.
- A member's `requires` still applies, before the league's own rule.
- The Battle Phone does not show leagues yet.
