<p align="center">
  <img src="https://raw.githubusercontent.com/matheo-1712/cobblemon-trainers/refs/heads/master/assets/logo.png" alt="Cobblemon Trainers">
</p>

<p align="center">
  <strong>Add real Pokémon trainers to your Cobblemon world - each one a single JSON file.</strong>
</p>

<p align="center">
  <sub><strong>Compatible with</strong> · Minecraft 1.21.1 · Fabric · Cobblemon 1.7.3 · Java 21 · needed on client <em>and</em> server</sub>
</p>

## What makes it different

**No trainer ever spawns on its own.** That is the starting decision, and there is no setting
to undo it: a world does not end up littered with trainers standing around in biomes nobody
crosses. A trainer is only ever there because somebody wanted them there - an admin, or the
player themselves.

**On the admin side**, two ways to populate a world:

- `/cobblemontrainers spawn` puts a trainer down here and now, for a test or an event;
- the **trainer spawner block** gives one a post. It is invisible, remembers which trainer to
  summon, and puts them back whenever they are missing - killed, wandered off, or gone with an
  unloaded chunk. That is what keeps a champion in their gym.

And because a configured block **travels inside a structure** with its settings, dropping one
into a saved building is enough for every generated copy to come with *its own* trainer already
in place. A village of trainers is built once.

**On the player side, meeting a trainer works nothing like that**: it all goes through the
**Battle Phone**, an item, not a command.

It is **crafted**: four iron ingots in the corners, four copper ingots on the sides, and a
**blue apricorn** in the middle.

<p align="center"><img src="https://raw.githubusercontent.com/matheo-1712/cobblemon-trainers/refs/heads/master/assets/battle_phone_craft.png" alt="Battle Phone recipe"></p>

Open the Battle Phone, pick a trainer from the list, call them. But a trainer only comes
**where they said they would be**: in the badlands at night, in a thunderstorm, on a structure,
in the End. Their page shows that place, the **Call** button sits right next to it, and if you
are not there the refusal names exactly what is missing. Finding a trainer means reading their
page and travelling to the right spot - and the world stays empty until you do.

---

## What you get

- **Real trainer battles** - right-click, singles, doubles or triples, with battle music.
- **Trainers who come when called** - from the Battle Phone, wherever they said they would be.
- **Trainers that stay put** - an invisible block holds one in place for good.
- **Showdown teams, pasted in** - copy an export, paste it, done.
- **Any skin you like** - a Minecraft account's, or your own PNG.
- **Rewards** - items on victory, once or every time.
- **Progress tracking in game** - the Battle Phone, one board per player.
- **Leagues, not just trainers** - categories, locks, advancements.
- **Adjustable AI** - several difficulty levels, up to you per trainer.
- **Regional forms and fakemon** - any Cobblemon form, including ones other packs add.
- **Translatable text** - names and dialogue in every player's own language.
- **Eight trainers in the box** - ready to fight, in their own tab.

---

## In detail

**Real trainer battles.** Right-click a trainer and you get a proper Cobblemon battle, in
singles, doubles or triples, optionally with both teams brought to level 50. Battle music plays
over everything else and stops however the battle ends. The battle opens on the Pokémon you
have selected, not on your first slot.

**Trainers who come when called.** Give a trainer a place - a biome, a structure, an area, a
time, a weather - and any player can call them over from the Battle Phone once they are
standing in it. Give them none and they wait wherever an admin put them.

**Trainers that stay where you put them.** An invisible spawner block holds one trainer in
place: kill it and it comes back, walk it away and it returns. Configure it by right-clicking,
and it keeps its settings inside a structure - so every generated copy of a building arrives
with its own trainer.

**Showdown teams, pasted in.** Copy an export straight from Pokémon Showdown: items,
abilities, natures, EVs/IVs, moves, shininess, nicknames. Unknown lines are ignored, so it just
works. Regional forms and fakemon go through an `Aspects:` line, the same syntax as
`/pokespawn`.

**Any skin you like.** Borrow a Minecraft account's skin by username or UUID, or ship your own
PNG in the pack. Custom textures are sent by the server, so every player sees them even without
your pack.

**Rewards, and one-time bosses.** Hand out items on victory - every time, or only on the first
win, reward by reward. A reward can be kept secret until it is won. Make a champion a single
encounter per player with `"rematch": "never"`.

That is what rewards are for: a rematchable trainer is a **way to farm**, and the datapack
decides which one. A trainer paying out berries on every win is a plantation you play instead
of wait for; another can pay out an ore, a resource you get no other way, or a step in a
progression. The mod ships none of those loops - it ships the hook, and packs will do the
rest.

**Progress tracking, in game.** The Battle Phone item gives every player their own board: one
tab per pack, a heading per category, each trainer's skin, what they still owe you, and whether
you have beaten them. Beat one and their full team opens up, models, forms and shinies
included. Operators get the same from `/cobblemontrainers list`. Progress is saved with the
world and survives `/reload` and restarts.

**Leagues, not just trainers.** Sort trainers into categories with folders, lock one behind
another - a badge, an advancement, eight victories - and hang ordinary Minecraft advancements
off any win.

**Adjustable AI.** Each trainer sets its own difficulty, from a first opponent to a champion
who plays to win. It is independent from the Pokémon's level, so a low-level trainer can still
be a real fight.

**Translatable text.** Trainer names and dialogue accept translation keys, so every player
reads them in their own language - floating nameplate included.

**Eight trainers in the box.** The mod ships its own, in their own tab: iconic trainers at
level 80, each answering a call from one biome, and a level 100 one locked behind them that
only answers in the End.

---

## Commands

```
/cobblemontrainers spawn <id> [<x> <y> <z>]
/cobblemontrainers list [<player>]
/cobblemontrainers defeat <id|all> [<players>] [reset]
/cobblemontrainers debugai
```

Every verb is detailed in
[the commands page](https://github.com/matheo-1712/cobblemon-trainers/blob/master/docs/en/COMMANDS.md).
They all require permission level 2 (operator). Autocomplete lists every loaded trainer under
its full `<pack>:<trainer>` ID, and searches both halves - typing `jac` finds
`my_pack:jacinthe`.

---

## The Battle Phone

`/cobblemontrainers list` is for operators. The **Battle Phone**
(`cobblemon-trainers:battle_phone`) is the same board for everyone else: right-click it and
every listed trainer of the world is there, one tab per datapack, a heading per category,
each with its skin and its state - beaten, beaten for good, still standing, or locked with the
list of what's missing.

Click a line and the trainer fills the right-hand page: level, team size, and six slots that
stay shut until you win, then show their Pokémon's models, forms and shinies included. The item
stores nothing - it reads the server - so losing it loses nothing. Find it in the
**Cobblemon Trainers** creative tab, or:

---

## How to make my own trainer pack

You can see all details in [DATAPACK DOCS](https://github.com/matheo-1712/cobblemon-trainers/blob/master/docs/en/DATAPACK.md)

---

## Mega Evolution

Trainers Mega Evolve when you let them. Hand a Pokémon its Mega Stone, add `"gimmicks": ["mega"]`
to the trainer's `battle` block, and they transform on the first turn - once per battle, whatever
their AI difficulty.

It needs [Cobblemon: Mega Showdown](https://modrinth.com/mod/mega-showdown), which stays
**optional**: without it the mod loads and plays exactly as before, and a team can name a
`Fallback Item:` so its Pokémon holds something sensible instead of the stone it cannot have.

Full guide: [GIMMICKS DOCS](https://github.com/matheo-1712/cobblemon-trainers/blob/master/docs/en/GIMMICKS.md)

## Known limitations

- Forms are declared through their aspects (`Aspects: alolan`), not the Showdown name
  suffix (`Raichu-Alola`) - a suffix can't be told apart from species whose name contains a
  hyphen (`Ho-Oh`, `Porygon-Z`).
- `player_username` and `player_uuid` skins are fetched from the Mojang API, so they need
  network access and an existing account. On failure the trainer keeps the default skin and
  the reason goes to the logs. The `texture` type needs nothing but an image in your pack.
- Battle music only plays for a player who has the mod: their client is what runs it, which is
  also what makes it loop and what keeps the game's own music waiting until the battle is over.

---

## Credits

Developed by **Mathéo** ([matheo-1712](https://github.com/matheo-1712)).

## Usage terms

- **Modpacks: yes.** You are free to include this mod in any modpack, public or private,
  free or otherwise. No permission needed, no message required.
- **Servers: yes.** Run it on any server you like.
- **Datapacks: yes.** Trainer packs you build with this mod are yours - distribute them
  however you want.
- **Reuploading: no.** Do not republish the mod on Modrinth, CurseForge, or any other
  distribution site, mirror, or ad-wrapped download page. Link to
  [this page](https://modrinth.com/mod/cobblemon-trainers-rerebleue) instead - that way everyone gets
  the real file, the right version, and the changelog with it.

## Links

- **Source & issues:** [github.com/matheo-1712/cobblemon-trainers](https://github.com/matheo-1712/cobblemon-trainers)
- **Full documentation:** [the wiki](https://github.com/matheo-1712/cobblemon-trainers/blob/master/docs/en/README.md) -
  every field, the `location` block, and what each AI difficulty does
- **Example pack:** [downloadable](https://github.com/matheo-1712/cobblemon-trainers/releases) next to the jar on every release, on assets - one folder that works as
  a datapack and a resource pack at once, covering every option.

## Trainer packs to play

Nothing here yet - this is where packs built with the mod will be listed, so you have trainers
to fight without writing any. Made one you want listed? Open an issue on GitHub with the link.

---

Found a bug, or a trainer that won't load? Open an issue on GitHub with your JSON and the
server log - the mod names the file it choked on.
