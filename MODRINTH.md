<p align="center">
  <img src="https://raw.githubusercontent.com/matheo-1712/cobblemon-trainers/refs/heads/master/assets/logo.png" alt="Cobblemon Trainers">
</p>

<p align="center">
  <strong>Add real Pokémon trainers to your Cobblemon world - each one a single JSON file.</strong>
</p>


---

## What you get

- **Real trainer battles.** Right-click a trainer, get a proper Cobblemon battle in
  singles, doubles or triples - with battle music playing over everything else.
- **Trainers that stay where you put them.** An invisible spawner block holds one trainer in
  place: kill it and it comes back, walk it away and it returns. Configure it by right-clicking,
  and it keeps its settings inside a structure.
- **Showdown teams, pasted in.** Copy an export straight from Pokémon Showdown: items,
  abilities, natures, EVs/IVs, moves, shininess, nicknames. Unknown lines are ignored, so
  it just works.
- **Any skin you like.** Borrow a Minecraft account's skin by username or UUID, or ship
  your own PNG in the pack. Custom textures are sent by the server, so every player sees
  them.
- **Rewards and one-time bosses.** Hand out items on victory, once or every time. Make a
  champion a single encounter per player with `"rematch": "never"`.
- **Progress tracking, in game.** The Battle Phone item gives every player their own board:
  one tab per pack, a heading per category, each trainer's skin - and their team, once beaten.
- **Leagues, not just trainers.** Sort trainers into categories with folders, lock one behind
  another - a badge, an advancement, eight victories - and hang ordinary Minecraft advancements
  off any win.
- **Adjustable AI.** Skill from 0 (plays randomly) to 5 (plays to win), independent from
  Pokémon level.
- **Translatable text.** Trainer names and dialogue accept translation keys, so every
  player reads them in their own language - floating nameplate included.

---

## Commands

```
/cobblemontrainers spawn <id>
/cobblemontrainers spawn <id> <x> <y> <z>
/cobblemontrainers list [<player>]
```

Both require permission level 2 (operator). Autocomplete lists every loaded trainer under its
full `<pack>:<trainer>` ID, and searches both halves - typing `jac` finds `my_pack:jacinthe`.

---

## The Battle Phone

The **Battle Phone**
(`cobblemon-trainers:battle_phone`) is
the same board for everyone else: right-click it and every listed trainer of the world is
there, one tab per datapack, a heading per category, each with its skin and its state - beaten,
beaten for good, still standing, or locked with the list of what's missing.

Click a line and the trainer fills the right-hand page: level, team size, and six slots that
stay shut until you win, then show their Pokémon's models, forms and shinies included. The item
stores nothing - it reads the server - so losing it loses nothing. Find it in the
**Cobblemon Trainers** creative tab, or:

---

## How to make my own trainer pack

You can see all details in (e)[]

## Known limitations

- Forms are declared through their aspects (`Aspects: alolan`), not the Showdown name
  suffix (`Raichu-Alola`) - a suffix can't be told apart from species whose name contains a
  hyphen (`Ho-Oh`, `Porygon-Z`).
- `player_username` and `player_uuid` skins are fetched from the Mojang API, so they need
  network access and an existing account. On failure the trainer keeps the default skin and
  the reason goes to the logs. The `texture` type needs nothing but an image in your pack.
- Battle music doesn't loop: a battle longer than the track ends in silence. Looping is a
  decision of the client's sound engine, which this mod doesn't touch.

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
- **Example pack:** [downloadable](https://github.com/matheo-1712/cobblemon-trainers/releases) next to the jar on every release, on assets - one folder that works as
  a datapack and a resource pack at once, covering every option.

Found a bug, or a trainer that won't load? Open an issue on GitHub with your JSON and the
server log - the mod names the file it choked on.
