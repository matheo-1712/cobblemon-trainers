# Les gimmicks de combat

Un dresseur peut méga-évoluer, sortir un Z-Move, dynamaxer ou téracristalliser en plein combat.
C'est le champ `battle.gimmicks`.

Pour poser le champ dans un dresseur, voir [DATAPACK.md](DATAPACK.md#battle).

*This page is also available [in English](en/GIMMICKS.md).*

## En une minute

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

Il faut les deux : la **préparation** sur le Pokémon - une gemme, un cristal Z, un type Tera -,
et le **mot** dans `gimmicks`. Donner la gemme sans écrire `["mega"]` fait un dresseur qui ne
s'en sert jamais. Le dynamax est le seul qui ne demande rien au Pokémon.

## Les valeurs acceptées

| Valeur | Effet | Demande un autre mod |
| --- | --- | --- |
| `"mega"` | Le dresseur méga-évolue dès que le combat le lui permet | Oui, voir plus bas |
| `"zmove"` | Le dresseur sort son Z-Move au tour où ça décide quelque chose | Oui, voir plus bas |
| `"max"` | Le dresseur dynamaxe au tour où ça décide quelque chose | Oui, voir plus bas |
| `"terastal"` | Le dresseur téracristallise au tour où ça décide quelque chose | Non |

> Le dynamax s'écrit **`max`**, pas `dynamax` : ces quatre mots sont les identifiants de
> Cobblemon, repris tels quels pour qu'il n'y ait qu'un vocabulaire à connaître.

`ultra` (l'Ultra-Explosion) existe chez Cobblemon mais **n'est pas encore supporté** : l'écrire
est signalé dans le log au chargement, et ne fait rien. Tout autre mot est signalé comme une
faute de frappe.

Un dresseur peut les déclarer tous. Le tour où le combat en offre plusieurs, un seul part - une
réponse ne porte qu'un gimmick - et l'ordre est toujours le même :

1. **La méga-évolution**, parce qu'elle ne se dépense pas : elle ne coûte pas de tour et ne peut
   pas être un mauvais choix.
2. **Le Z-Move**, puis **le dynamax**, puis **le téracristal**. Les trois ne partent qu'au tour
   où ils décident quelque chose, donc quand deux répondent à la même question, c'est le moins
   cher qui part : un Z-Move est un coup puis plus rien, un dynamax dure trois tours, un
   téracristal est ce que le Pokémon *est* pour le reste du combat.

---

# La méga-évolution

## Ce qu'il faut installer

| Il faut | Pourquoi |
| --- | --- |
| [Cobblemon: Mega Showdown](https://modrinth.com/mod/mega-showdown) sur le serveur | C'est lui qui fournit les méga-gemmes et la forme méga |
| Le même mod côté client | Sinon le joueur ne voit pas la transformation |

Cobblemon Trainers **ne dépend pas** de Mega Showdown : le mod se charge, se lance et se joue
sans lui. Un dresseur qui déclare `["mega"]` combat alors normalement, sans jamais
méga-évoluer.

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

---

# Le Z-Move

## Ce qu'il faut installer

Le même mod que pour la méga-évolution : [Cobblemon: Mega
Showdown](https://modrinth.com/mod/mega-showdown), des deux côtés. C'est lui qui fournit les
cristaux Z et qui apprend au simulateur à les voir. Sans lui, un dresseur qui déclare
`["zmove"]` combat normalement, sans jamais en sortir un.

## Le cristal Z

C'est un objet tenu, donc il se donne sur la première ligne du Pokémon :

```
Pikachu @ Electrium Z
Level: 80
- Thunderbolt
- Volt Tackle
```

- Un cristal **de type** (`Electrium Z`, `Firium Z`, …) transforme n'importe quelle capacité de
  ce type.
- Un cristal **spécifique** (`Aloraichium Z`, `Decidium Z`, …) ne transforme qu'une capacité
  précise d'un Pokémon précis, et il faut que le Pokémon la connaisse.

La règle de recherche d'objet et la ligne `Fallback Item:` sont exactement celles de la gemme,
ci-dessus.

## Quand le dresseur sort son Z-Move

**Pas à la première occasion.** Un camp n'y a droit qu'une fois, et un Z-Move dépensé sur une
cible qui tombait de toute façon est un Z-Move perdu. Il n'y a donc qu'un déclencheur, le même
que le premier du téracristal :

| Il sort son Z-Move si… | Autrement dit |
| --- | --- |
| Le coup qu'il allait jouer devient létal grâce à la puissance du Z-Move | Il prend un KO qu'il n'avait pas |

Il n'y a **pas** de second déclencheur défensif, et ce n'est pas un oubli : un Z-Move est un
coup, puis plus rien. Les Z de statut - Z-Démoralisation qui soigne, Z-Danse Fleurie qui booste -
remplaceraient la capacité que le dresseur avait choisie par autre chose, ce qu'un gimmick ne
doit pas faire ici. **Sur une capacité de statut, le dresseur garde donc son Z-Move en main.**

- **Une seule fois par combat.** En double, le premier des deux Pokémon pour qui ça décide
  quelque chose le prend.
- **Jamais sur un changement de Pokémon** : le Z-Move part avec l'attaque du tour.
- **Une capacité à puissance variable** (Frappe Atlas, Gyroballe, Rapport…) garde le Z en main :
  sa puissance en Z ne se calcule pas, et le mod préfère ne rien dépenser plutôt que deviner.
- **La difficulté ne l'interdit jamais**, mais elle se voit : le déclencheur regarde *le coup
  déjà choisi*. Un dresseur en `difficulty: 0` joue au hasard, il aura donc rarement en main le
  coup qui bascule. Voir [DIFFICULTE.md](DIFFICULTE.md).

---

# Le dynamax

## Ce qu'il faut installer

Encore [Cobblemon: Mega Showdown](https://modrinth.com/mod/mega-showdown), des deux côtés : le
dynamax n'existe pas dans une installation nue.

**Rien à préparer sur le Pokémon.** Contrairement aux trois autres, le dynamax ne demande ni
objet tenu ni ligne d'équipe : le mot dans `gimmicks` suffit. Côté joueur il faut un Bracelet
Dynamax, mais un dresseur n'a pas cette contrainte - voir [plus bas](#le-joueur-lui).

## Quand le dresseur dynamaxe

Comme le téracristal, il attend l'un de deux moments - le dynamax fait les deux choses à la
fois, il gonfle les capacités en Capsules Max et il double la barre de vie :

| Il dynamaxe si… | Autrement dit |
| --- | --- |
| Le coup qu'il allait jouer devient létal grâce à la puissance de la Capsule Max | Il prend un KO qu'il n'avait pas |
| Le coup adverse qui allait le mettre KO cesse de l'être une fois sa barre doublée | Il survit à un tour qu'il perdait |

- **Une seule fois par combat**, et il dure trois tours. Le mod ne regarde pas ces trois tours :
  ce qu'ils valent dépend de ce que le joueur fait ensuite, et chacun des deux déclencheurs vaut
  déjà la dépense à lui seul.
- **Jamais sur un changement de Pokémon** : le dynamax part avec l'attaque du tour.
- **Jamais sur une capacité de statut.** Le dynamax les transforme *toutes* en Draco-Barrière,
  donc dynamaxer sur un soin ou une danse remplacerait la décision du dresseur au lieu de s'y
  accrocher. Il attend un tour où il attaque.
- **La difficulté ne l'interdit jamais**, avec la même nuance que pour les deux autres :
  seul le premier déclencheur dépend du coup déjà choisi, le second joue à toutes les
  difficultés.

---

# Le téracristal

Rien à installer : Cobblemon fournit les types Tera, l'Orbe Tera et l'animation. Un dresseur qui
déclare `["terastal"]` marche sur une installation nue.

## Le type Tera

Il se déclare avec la ligne `Tera Type:`, celle des exports Showdown :

```
Garganacl @ Leftovers
Ability: Purifying Salt
Tera Type: Fairy
- Salt Cure
- Recover
```

Les dix-huit types sont acceptés, plus `Stellar`. Un nom inconnu est signalé dans le log et
ignoré.

**Sans cette ligne**, le Pokémon prend **son propre type primaire** : un Rhinoféros est Tera
Sol, un Ectoplasma Tera Spectre. Le téracristal devient alors une pure amélioration de son STAB,
sans changement défensif - utile, prévisible, et jamais une surprise pour le pack.

C'est le mod qui pose ce défaut. Livré à lui-même, Cobblemon tire un type Tera **au hasard**
(son réglage `teraTypeRate`), donc le même dresseur téracristallisait dans un type différent à
chaque apparition. Le type primaire est calculé **après** la forme : un Goupix d'Alola est donc
Tera Glace, pas Tera Feu.

## Quand le dresseur téracristallise

**Pas à la première occasion.** Un camp n'y a droit qu'une fois, et la dépenser au tour 1 parce
qu'elle était offerte, c'est la perdre. Le dresseur attend donc l'un de ces deux moments :

| Il téracristallise si… | Autrement dit |
| --- | --- |
| Le coup qu'il allait jouer devient létal grâce au bonus Tera | Il prend un KO qu'il n'avait pas |
| Le coup adverse qui allait le mettre KO cesse d'être létal contre son type Tera | Il survit à un tour qu'il perdait |

En dehors de ces deux cas, il garde son téracristal. **Un dresseur peut donc finir un combat
sans jamais téracristalliser** - ça veut dire que l'occasion ne s'est pas présentée, pas que le
mot est mal écrit.

- **Une seule fois par combat.** En double, le premier des deux Pokémon pour qui ça décide
  quelque chose la prend.
- **Jamais sur un changement de Pokémon** : le téracristal part avec l'attaque du tour.
- **La difficulté ne l'interdit jamais**, mais elle se voit quand même : le premier des deux
  déclencheurs regarde *le coup déjà choisi*. Un dresseur en `difficulty: 0` joue au hasard, il
  aura donc rarement en main le coup qui devient létal. Un `difficulty: 5` choisit déjà bien, et
  téracristallise donc plus souvent. Voir [DIFFICULTE.md](DIFFICULTE.md).

### Le cas Stellar

`Tera Type: Stellar` ne donne aucun type : il ne change aucune résistance. Seul le premier
déclencheur - le KO sécurisé - s'applique donc, avec le bonus Stellar (×2 sur un coup déjà STAB,
×1,2 sur le reste). Un Pokémon en Stellar peut ne jamais téracristalliser si aucun de ses coups
ne bascule.

---

## Le joueur, lui

Chez Cobblemon, un joueur ne se sert d'un gimmick que s'il a l'objet clé qui va avec : une Key
Stone pour la méga, un Z-Ring pour le Z-Move, un Bracelet Dynamax pour le dynamax, une Orbe Tera
pour le téracristal. Un dresseur n'a aucune de ces contraintes : il se sert des siens même face
à un joueur qui n'a rien.

C'est la règle de Cobblemon et le mod n'y touche pas, mais ça se prépare côté pack : un
dresseur qui téracristallise est plus dur qu'il n'en a l'air pour un joueur en début de partie.
Mettre l'objet derrière le dresseur, ou verrouiller le dresseur avec
[`requires.items`](DATAPACK.md#conditions-pour-combattre), sont deux façons de s'en assurer.

## Vérifier que ça marche

1. `/cobblemontrainers spawn <id>` pour poser le dresseur.
2. Le combattre.
   - Méga : le Pokémon doit se transformer au premier tour.
   - Z-Move, dynamax, téracristal : il faut lui donner une raison. Amenez son Pokémon actif au
     bord du KO, ou mettez-lui en face une cible qu'il ne tue que d'un cheveu.
3. Rien ne se passe ?
   - Pour la méga et le Z-Move : Mega Showdown est-il installé des deux côtés, l'objet
     correspond-il à l'espèce et à la capacité, et le log dit-il `Ignoring held item` au
     chargement du pack ?
   - Pour le dynamax : Mega Showdown est-il installé des deux côtés ?
   - Pour le téracristal : le log dit-il `Ignoring unknown Tera type` ? Sinon, c'est
     probablement que l'occasion ne s'est pas présentée.

`/cobblemontrainers debugai` ajoute une ligne dans le chat au moment où le dresseur se sert d'un
gimmick, avec la raison pour les trois qui attendent leur moment. C'est ce qui distingue « il ne
le fait pas » de « il attend son moment ».

Le pack d'exemple contient trois dresseurs bâtis pour ça : `cobblemonrlm:terastal`,
`cobblemonrlm:zmove` (quatre cristaux Z, chacun avec son `Fallback Item:`) et
`cobblemonrlm:dynamax` (rien à donner aux Pokémon, juste le mot). Les deux derniers ne sortent
leur gimmick qu'avec Mega Showdown installé ; sans lui ils combattent normalement.
