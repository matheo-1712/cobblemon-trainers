# Les gimmicks de combat

Un dresseur peut méga-évoluer en plein combat. C'est le champ `battle.gimmicks`, et ça demande
un autre mod - celui qui met les méga-gemmes dans le jeu.

Pour poser le champ dans un dresseur, voir [DATAPACK.md](DATAPACK.md#battle).

*This page is also available [in English](en/GIMMICKS.md).*

## En une minute

```json
{
  "name": "Peter",
  "battle": {
    "level": 80,
    "gimmicks": ["mega"]
  },
  "team": [
    "Charizard @ Charizardite X\nFallback Item: Life Orb\nLevel: 80\n- Flare Blitz\n- Dragon Claw"
  ]
}
```

Il faut les deux : la **gemme** sur le Pokémon, et le **mot** dans `gimmicks`. Donner la gemme
sans écrire `["mega"]` fait un dresseur qui ne s'en sert jamais.

## Ce qu'il faut installer

| Il faut | Pourquoi |
| --- | --- |
| [Cobblemon: Mega Showdown](https://modrinth.com/mod/mega-showdown) sur le serveur | C'est lui qui fournit les méga-gemmes et la forme méga |
| Le même mod côté client | Sinon le joueur ne voit pas la transformation |

Cobblemon Trainers **ne dépend pas** de Mega Showdown : le mod se charge, se lance et se joue
sans lui. Un dresseur qui déclare `["mega"]` combat alors normalement, sans jamais
méga-évoluer.

## Les valeurs acceptées

| Valeur | Effet |
| --- | --- |
| `"mega"` | Le dresseur méga-évolue dès que le combat le lui permet |

`terastal`, `zmove`, `dynamax` et `ultra` existent chez Cobblemon mais **ne sont pas encore
supportés** par le mod : les écrire est signalé dans le log au chargement, et ne fait rien.
Tout autre mot est signalé comme une faute de frappe.

## Quand le dresseur méga-évolue

À la première occasion, c'est-à-dire au premier tour où son Pokémon actif peut le faire. C'est
ce que font les dresseurs des jeux, et ça ne peut pas être un mauvais choix : la
méga-évolution ne coûte pas de tour.

- **Une seule fois par combat**, comme pour un joueur. En double, c'est le premier des deux
  Pokémon à en avoir l'occasion qui la prend.
- **Jamais sur un changement de Pokémon** : la méga part avec l'attaque du tour.
- **La difficulté n'y change rien.** Un dresseur en `difficulty: 0` méga-évolue quand même : le
  pack a donné la gemme et écrit le mot, ce n'est pas une question d'intelligence de l'IA.

## La gemme

Elle se donne comme n'importe quel objet tenu, sur la première ligne du Pokémon :

```
Charizard @ Charizardite X
```

Le nom s'écrit comme sur Showdown. Le mod le cherche d'abord chez Cobblemon, puis chez tous les
mods chargés - il n'a donc pas besoin de savoir que la gemme vient de Mega Showdown. Écrire
l'ID complet (`@ mega_showdown:charizardite_x`) marche aussi et lève toute ambiguïté.

**Le Pokémon doit pouvoir porter cette gemme** : une Dracaufite X sur un Ronflex ne
méga-évolue rien, c'est le simulateur de combat qui en décide, pas le mod.

## L'objet de repli

Sans Mega Showdown, la gemme n'existe pas et le Pokémon apparaît les mains vides - ce qui
change son combat plus que l'absence de méga-évolution. La ligne `Fallback Item:` répond à ça :

```
Charizard @ Charizardite X
Fallback Item: Life Orb
```

Le premier objet qui existe est porté. Le détail est dans
[DATAPACK.md](DATAPACK.md#la-ligne-fallback-item) - la règle vaut pour tous les objets, pas
seulement les gemmes.

## Le joueur, lui

Chez Cobblemon, un joueur ne peut méga-évoluer qu'avec une **Key Stone** dans ses objets clés.
Un dresseur n'a pas cette contrainte : il méga-évolue même face à un joueur qui n'en a pas.

C'est la règle de Cobblemon et le mod n'y touche pas, mais ça se prépare côté pack : un
dresseur qui méga-évolue est plus dur qu'il n'en a l'air pour un joueur en début de partie.
Mettre la Key Stone derrière le dresseur, ou verrouiller le dresseur avec
[`requires.items`](DATAPACK.md#conditions-pour-combattre), sont deux façons de s'en assurer.

## Vérifier que ça marche

1. `/cobblemontrainers spawn <id>` pour poser le dresseur.
2. Le combattre. Au premier tour, le Pokémon doit se transformer.
3. Rien ne se passe ? Dans l'ordre : Mega Showdown est-il installé des deux côtés, la gemme
   correspond-elle à l'espèce, et le log dit-il `Ignoring held item` au chargement du pack ?

`/cobblemontrainers debugai` ajoute une ligne dans le chat au moment où le dresseur
méga-évolue, ce qui distingue « il ne le fait pas » de « il le fait mais on ne le voit pas ».
