# Dressing a trainer

A trainer can wear armour, hold a Poké Ball and have a Mega Bracelet on their wrist. That is
the `cosmetics` block.

To put the block in a trainer, see [DATAPACK.md](DATAPACK.md#field-reference).

*Cette page existe aussi [en français](../COSMETIQUES.md).*

## In a minute

```json
{
  "name": "Hiker",
  "cosmetics": {
    "head": "minecraft:leather_helmet",
    "chest": "minecraft:leather_chestplate",
    "legs": "minecraft:leather_leggings",
    "feet": "minecraft:leather_boots",
    "mainHand": "cobblemon:poke_ball",
    "trinkets": {
      "wrist": "mega_showdown:mega_bracelet",
      "face": "mega_showdown:maxie_glasses",
      "belt": "mega_showdown:tera_orb"
    }
  }
}
```

Six equipment slots, seven places on the body, one full item ID in each, all of them optional.
**None of it changes a battle**: no armour points, no weapon, nothing dropped on death. It is
appearance, and nothing else.

## The six equipment slots

| Field | Where it is worn |
| --- | --- |
| `head` | On the head |
| `chest` | On the body |
| `legs` | On the legs |
| `feet` | On the feet |
| `mainHand` | In the main hand |
| `offHand` | In the other hand |

These are the six equipment slots of a Minecraft mob, on purpose: the game already sends them
to every client that can see the trainer. A trainer dressed here is dressed for everyone,
including a player who turns up ten minutes later, and stays dressed across a restart.

Any item works, not only armour. An item that is not armour in an armour slot simply does not
show: Minecraft only knows how to draw armour on a body, and it has no model for anything
else.

An ID nothing provides is **logged once at load**, and the slot is left empty. A pack that
dresses its champion in an item from a mod the server does not have still loads.

## The `trinkets` block: what is worn

A Mega Bracelet, a pair of glasses, a pendant, an anklet are neither held nor armour: they are
worn somewhere on the body. That is the `trinkets` block, **one field per place**, and no two
of them share a spot - so a trainer may wear all seven at once.

| Field | Where it is drawn | Examples |
| --- | --- | --- |
| `face` | On the head | Maxie's Glasses, Lisia's Tiara |
| `chest` | Round the neck / on the chest | Diantha's Charm, Archie's Anchor, a pendant |
| `wrist` | On the wrist of the free arm | Mega Bracelet, Korrina's Glove |
| `forearm` | On the same arm, a little higher | Z-Ring |
| `hand` | Across the back of the main hand | Dynamax Band, Omni Ring |
| `belt` | Hooked on the belt | Tera Orb |
| `ankle` | On the right ankle | Zinnia's Anklet |

**The names say the place, not the purpose, and that is deliberate.** A key item is not always
a wrist band: in Mega Showdown, Maxie's Glasses, Lisia's Tiara, Diantha's Charm, Archie's Anchor
and Zinnia's Anklet all sit in **the same mega slot** as the bracelet, yet are worn in five
different places. Nothing in the game's data says which is which - that mod picks the spot in
its own code, item by item - so no tag and no registry can be asked. The pack is the one who
knows, so the pack is the one who writes it.

Which means **any item goes anywhere**. Nothing requires a real key item, and a pack is free to
hang a trinket of its own on its champion's belt.

> **It grants no gimmick.** A trainer Mega Evolves because its
> [`battle.gimmicks`](GIMMICKS.md) says so, never because it wears the bracelet - it never
> needed the item for that anyway. The bracelet is there so that it shows.

The key items named above come from Mega Showdown. Without that mod the IDs resolve to nothing:
it is logged once and the trainer wears nothing there.

Two of the places - `chest` and `belt` - are placed in plain body coordinates rather than off a
bone, as that mod places them too: they do not follow an animation that turns the torso. The
other five follow their limb.

## What it does not do

- **It grants no gimmick.** See above.
- **It does not protect.** A trainer never fights hand to hand; the armour has no armour points
  to give.
- **It cannot be picked up.** The drop chance of every slot is set to zero: an operator killing
  a trainer to move it does not hand out its hat.
- **It does not follow a `/reload`.** A trainer already standing keeps the outfit it arrived
  in, exactly like its name and its skin: what a `/reload` changes is the definition, not the
  entities already made from it. Break and replace the trainer spawner, or call the trainer
  again from the Battle Phone.

## If nothing shows up

- **The mod has to be on the client too.** It is what draws all of this; Cobblemon draws
  neither armour nor a held item on its NPCs.
- **The trainer has to come from this mod.** A Cobblemon NPC dressed by other means is left
  alone.
- **The trainer has to be on the player model**, which every trainer of this mod is. An NPC on
  a model of its own, with differently named bones, is not dressed - rather than have its
  armour hung on guessed coordinates.
- **A trinket added after the fact needs the trainer spawned again**, like the rest of the
  outfit: see the `/reload` rule above.
- **Read the load log.** An item ID that resolves to nothing is named there, with the trainer
  and the slot.
