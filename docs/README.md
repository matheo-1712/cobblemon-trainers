# Documentation

Le wiki du mod. Le [README](../README.md) présente Cobblemon Trainers, son installation et
ses commandes ; les pages ci-dessous sont la **référence** - c'est là que vit le détail.

*This documentation is also available [in English](en/README.md).*

## Les pages

| Page | Ce qu'on y trouve |
| --- | --- |
| [COMMANDS.md](COMMANDS.md) | Les quatre verbes de `/cobblemontrainers`, leurs arguments et ce qu'ils renvoient |
| [DATAPACK.md](DATAPACK.md) | Créer un pack de dresseurs : arborescence, tous les champs, catégories, conditions, advancements, équipes Showdown, skins, musique, récompenses, traductions |
| [SPAWNING.md](SPAWNING.md) | Faire venir un dresseur : le bloc `location` et l'appel depuis le Battle Phone |
| [DIFFICULTE.md](DIFFICULTE.md) | Ce que fait exactement `battle.difficulty`, de `0` à `5` |

Un pack d'exemple couvrant chaque option vit dans
[`examples/cobblemonrlm/`](../examples/cobblemonrlm) : un seul dossier qui fait à la fois
datapack (`data/`) et resource pack (`assets/`). Chaque
[release](https://github.com/matheo-1712/cobblemon-trainers/releases) l'attache déjà zippé,
sous le nom `exemple_trainer_datapack.zip`.

**Huit dresseurs sont livrés avec le mod**, dans le namespace `cobblemon-trainers` : des
dresseurs iconiques niveau 80, appelables chacun depuis un biome, et un dernier niveau 100
verrouillé derrière l'un d'eux, qui ne répond que dans l'End. Ils apparaissent dans le Battle
Phone sous leur propre onglet, à côté de ceux de tes packs.

## Par question

| Je veux… | Aller à |
| --- | --- |
| Connaître les commandes | [Les commandes](COMMANDS.md) |
| Écrire mon premier dresseur | [Le premier dresseur](DATAPACK.md#le-premier-dresseur) |
| Savoir où poser mon pack | [Où poser le pack](DATAPACK.md#où-poser-le-pack) |
| La liste de tous les champs JSON | [Tous les champs](DATAPACK.md#tous-les-champs) |
| Coller une équipe Showdown | [Le format d'équipe](DATAPACK.md#le-format-déquipe) |
| Une forme régionale, un fakemon | [La ligne `Aspects:`](DATAPACK.md#la-ligne-aspects) |
| Donner un skin à un dresseur | [Skins](DATAPACK.md#skins) |
| Ranger mes dresseurs en ligue | [Catégories](DATAPACK.md#catégories) |
| Verrouiller un dresseur derrière un autre | [Conditions pour combattre](DATAPACK.md#conditions-pour-combattre) |
| Donner des objets à la victoire | [Revanches et récompenses](DATAPACK.md#revanches-et-récompenses) |
| Déclencher un advancement | [Advancements](DATAPACK.md#advancements) |
| Rendre un dresseur appelable | [Le bloc `location`](SPAWNING.md#le-bloc-location) |
| Choisir un niveau d'IA | [Quel niveau choisir](DIFFICULTE.md#quel-niveau-choisir) |
| Comprendre une correction de l'IA en jeu | [Vérifier en jeu](DIFFICULTE.md#vérifier-en-jeu) |
| Traduire mes dresseurs | [Traduire les textes](DATAPACK.md#traduire-les-textes) |
| Comprendre pourquoi mon pack ne charge pas | [Erreurs fréquentes](DATAPACK.md#erreurs-fréquentes) |

## Une règle, une page

Chaque sujet n'est décrit qu'à un seul endroit, et les autres pages y renvoient :

- les **commandes** sont dans `COMMANDS.md` ;
- le **format des dresseurs** est dans `DATAPACK.md` ;
- tout ce qui touche à **l'appel d'un dresseur** est dans `SPAWNING.md` ;
- tout ce que fait **l'IA** est dans `DIFFICULTE.md`.

Une règle écrite à deux endroits est une règle qui finit fausse à l'un des deux. Ajouter un
champ, c'est ajouter une ligne au tableau de `DATAPACK.md`, pas une section.
