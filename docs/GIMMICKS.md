# Les gimmicks de combat

Un dresseur peut méga-évoluer ou téracristalliser en plein combat. C'est le champ
`battle.gimmicks`.

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

Il faut les deux : la **préparation** sur le Pokémon - une gemme, un type Tera -, et le **mot**
dans `gimmicks`. Donner la gemme sans écrire `["mega"]` fait un dresseur qui ne s'en sert jamais.

## Les valeurs acceptées

| Valeur | Effet | Demande un autre mod |
| --- | --- | --- |
| `"mega"` | Le dresseur méga-évolue dès que le combat le lui permet | Oui, voir plus bas |
| `"terastal"` | Le dresseur téracristallise au tour où ça décide quelque chose | Non |

`zmove`, `dynamax` et `ultra` existent chez Cobblemon mais **ne sont pas encore supportés** par
le mod : les écrire est signalé dans le log au chargement, et ne fait rien. Tout autre mot est
signalé comme une faute de frappe.

Un dresseur peut déclarer les deux. Le tour où le combat les offre en même temps, c'est la
méga-évolution qui part - elle est liée au Pokémon qui porte la gemme, alors que le téracristal
appartient au camp et attend sans rien perdre.

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

Chez Cobblemon, un joueur ne peut méga-évoluer qu'avec une **Key Stone** dans ses objets clés,
ni téracristalliser sans **Orbe Tera**. Un dresseur n'a pas ces contraintes : il se sert de ses
gimmicks même face à un joueur qui n'a ni l'une ni l'autre.

C'est la règle de Cobblemon et le mod n'y touche pas, mais ça se prépare côté pack : un
dresseur qui téracristallise est plus dur qu'il n'en a l'air pour un joueur en début de partie.
Mettre l'objet derrière le dresseur, ou verrouiller le dresseur avec
[`requires.items`](DATAPACK.md#conditions-pour-combattre), sont deux façons de s'en assurer.

## Vérifier que ça marche

1. `/cobblemontrainers spawn <id>` pour poser le dresseur.
2. Le combattre.
   - Méga : le Pokémon doit se transformer au premier tour.
   - Téracristal : il faut lui donner une raison. Amenez son Pokémon actif au bord du KO, ou
     mettez-lui en face une cible qu'il ne tue que d'un cheveu.
3. Rien ne se passe ?
   - Pour la méga : Mega Showdown est-il installé des deux côtés, la gemme correspond-elle à
     l'espèce, et le log dit-il `Ignoring held item` au chargement du pack ?
   - Pour le téracristal : le log dit-il `Ignoring unknown Tera type` ? Sinon, c'est
     probablement que l'occasion ne s'est pas présentée.

`/cobblemontrainers debugai` ajoute une ligne dans le chat au moment où le dresseur se sert d'un
gimmick, avec la raison pour le téracristal. C'est ce qui distingue « il ne le fait pas » de
« il attend son moment ».

Le pack d'exemple contient `cobblemonrlm:terastal`, un dresseur bâti pour ça.
