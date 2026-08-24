# Faire venir un dresseur

Un dresseur peut être **appelé** par un joueur depuis le Battle Phone. Le joueur ouvre la fiche
du dresseur, appuie sur **Appeler**, et le dresseur arrive à quelques dizaines de blocs.

Le mod ne fait jamais apparaître un dresseur tout seul. Il n'y a que trois façons d'en voir un :

| Voie | Qui déclenche | Pour quoi |
| --- | --- | --- |
| Le bloc de dresseur | Un opérateur qui pose le bloc | Un dresseur qui vit à un endroit fixe |
| `/cobblemontrainers spawn` | Un opérateur | Tester, dépanner |
| Le Battle Phone | N'importe quel joueur | Un dresseur qui vient quand on l'appelle |

Cette page ne traite que de la troisième. Pour le reste du format, voir
[DATAPACK.md](DATAPACK.md).

*This page is also available [in English](en/SPAWNING.md).*

## Sommaire

- [Le bloc `location`](#le-bloc-location) · [Les conditions](#les-conditions) ·
  [Les textes](#les-textes)
- [Ce que voit le joueur](#ce-que-voit-le-joueur) · [Ce que fait le mod](#ce-que-fait-le-mod)
- [Les valeurs fixées par le mod](#les-valeurs-fixées-par-le-mod)
- [Tester](#tester) · [Erreurs fréquentes](#erreurs-fréquentes)

## Le bloc `location`

**Nommer un lieu dans le bloc `location`, c'est rendre le dresseur appelable.** Il n'y a pas
d'autre interrupteur. Un dresseur qui ne nomme aucun lieu n'a pas de bouton : c'est ainsi qu'on
écrit un champion qu'il faut aller trouver dans son arène.

Le bloc sert donc à deux choses distinctes, et on peut n'en vouloir qu'une :

| Ce qu'il y a dans le bloc | Lieu affiché | Bouton Appeler |
| --- | --- | --- |
| Rien, ou pas de bloc du tout | non | non |
| Seulement un `label` | oui | **non** |
| Au moins une condition | oui | oui |

Un `label` seul est donc parfaitement valide : le champion dit où le trouver, et n'y va pas pour
autant. C'est le cas normal d'un dresseur posé par un bloc de dresseur quelque part dans le
monde.

```json
{
  "name": "Ace du Solo",
  "location": {
    "biome": "#minecraft:is_badlands",
    "time": "night"
  }
}
```

Le plus court possible fonctionne : un seul champ suffit.

### Les conditions

Toutes facultatives, toutes cumulatives : elles s'additionnent, elles ne sont jamais des
alternatives. Elles sont vérifiées **une seule fois**, au moment où le joueur appuie sur le
bouton.

| Champ | Exemple | Ce qui est testé |
| --- | --- | --- |
| `dimension` | `minecraft:the_nether` | La dimension du joueur |
| `biome` | `minecraft:desert` | Le biome sous le joueur |
| `biome` avec `#` | `#minecraft:is_desert` | N'importe quel biome du tag |
| `structure` | `minecraft:village_desert` | Le joueur est **sur** une pièce générée de la structure |
| `structure` avec `#` | `#minecraft:village` | N'importe quelle structure du tag |
| `area` | `{ "from": [100, -200], "to": [400, 100] }` | Le joueur est dans la boîte, en `x` et `z` |
| `minY` | `0` | Altitude minimale, incluse |
| `maxY` | `62` | Altitude maximale, incluse |
| `time` | `day`, `night` | L'heure du monde |
| `weather` | `clear`, `rain`, `thunder` | Le temps **à la position du joueur** |

`area` ne porte que sur `x` et `z` ; l'altitude est `minY` et `maxY`, pour qu'une même idée ne
soit pas écrite à deux endroits. Les deux coins de `area` peuvent être donnés dans n'importe
quel ordre.

`weather` est lu à la position, pas sur le monde : il ne pleut pas dans un désert, et un joueur
qui s'y tient n'est pas sous la pluie quoi qu'annonce le ciel ailleurs.

Un ID qui n'existe pas est une condition que **rien ne remplit**, volontairement : compter une
faute de frappe comme remplie ouvrirait le dresseur au monde entier.

### Les textes

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `label` | le mod formule | Ce qu'affiche le Battle Phone à la place de la description automatique |
| `arrival` | le mod formule | Ce que dit le dresseur en arrivant, avec ses coordonnées |
| `busy` | le mod formule | Ce qu'il dit quand un autre exemplaire de lui est déjà dans le coin |

`label` est le seul des trois qui serve encore à un dresseur non appelable. Les deux autres ne
sont dits qu'au moment d'un appel : les écrire sans nommer de lieu est signalé dans le log.

```json
"location": {
  "label": "dans l'arène de Céladopole"
}
```

Ça, c'est un champion. Le joueur lit où il est, et va le trouver à pied.

Les trois sont des clés de traduction, comme `name` et les `messages` : mets une clé et
traduis-la dans ton resource pack, ou mets du texte brut et il s'affiche tel quel. Voir
[Traduire les textes](DATAPACK.md#traduire-les-textes).

`arrival` reçoit **trois arguments** : `x`, `y`, `z`. Écris-les avec `%s %s %s`.

```json
"location": {
  "biome": "#minecraft:is_badlands",
  "time": "night",
  "label": "dans les mesas, la nuit",
  "arrival": "Je suis en %s %s %s. Ne me fais pas attendre.",
  "busy": "Je suis déjà dans le coin, ouvre les yeux."
}
```

**Quand écrire un `label`.** Le mod nomme ce qu'il peut : une dimension et un biome ont une
traduction dans le jeu, un ID de structure et une boîte de coordonnées n'en ont pas et n'en
auront jamais. Un dresseur repéré par une structure ou une `area` mérite donc un `label`.

## Ce que voit le joueur

Sur la fiche du dresseur, sous son équipe :

- **Une ligne de lieu**, `Se trouve : dans les mesas, la nuit`. Elle est visible avant d'avoir
  battu le dresseur - contrairement à son équipe, qui est une récompense.
- **Un bouton Appeler**, à droite. Il n'existe que pour un dresseur appelable.

Le bouton est grisé pour un dresseur déjà battu qui refuse la revanche
(`"progress": { "rematch": "never" }`). Le survoler dit pourquoi.

Le bouton n'est **pas** grisé quand le joueur est au mauvais endroit : le client ne sait pas où
le joueur se trouve par rapport à la condition, il sait seulement comment elle se lit. C'est le
serveur qui répond, dans le chat, en listant ce qui manque :

```
Ace du Solo n'est pas ici. Cherche-le :
- biomes #minecraft:is_badlands
- la nuit
```

Appuyer sur le bouton ferme le Battle Phone : la réponse est un message de chat, et il faut
pouvoir le lire.

## Ce que fait le mod

À l'appel, dans cet ordre :

1. Le dresseur existe, est listé, et n'est pas masqué pour ce joueur.
2. Il déclare un bloc `location`.
3. Le joueur remplit son `requires` - voir [les conditions de
   combat](DATAPACK.md#conditions-pour-combattre). **Un dresseur verrouillé n'est jamais
   appelable.**
4. Il n'a pas déjà été battu par un dresseur qui refuse la revanche.
5. Il ne boude pas ce joueur (voir plus bas).
6. Le joueur est au bon endroit.
7. Aucun autre exemplaire du même dresseur n'est dans les parages.

Puis :

- Le dresseur qu'avait déjà appelé ce joueur est renvoyé. **Un seul dresseur appelé par
  joueur.**
- Le nouveau apparaît entre 10 et 20 blocs, tourné vers le joueur.
- Le joueur reçoit un message avec les coordonnées. **Lui seul.**

Ensuite :

| Événement | Ce qui se passe |
| --- | --- |
| Le combat se termine | Le dresseur repart quelques secondes plus tard, gagné ou perdu |
| Le joueur s'éloigne, change de dimension ou se déconnecte | Le dresseur repart |
| Le joueur appelle quelqu'un d'autre | Le premier repart |
| Le dresseur meurt | L'appel est perdu, et il boude ce joueur pendant 5 minutes |
| Le dresseur est en plein combat | **On ne le retire jamais**, quoi que fasse le joueur - et un nouvel appel est refusé tant qu'il dure |

Le combat se lance au **clic droit sur le dresseur**, comme partout ailleurs dans le mod.
Appeler quelqu'un, ce n'est pas le défier.

En multijoueur, **n'importe quel joueur peut défier un dresseur appelé par un autre**, à
condition de remplir lui-même son `requires`. La progression reste individuelle.

**Rien n'est sauvegardé.** Un appel dure quelques minutes ; le redémarrage du serveur les
oublie tous et efface les dresseurs restés derrière.

## Les valeurs fixées par le mod

Ces nombres ne se règlent pas dans un datapack. Ils sont les mêmes pour tous les dresseurs, pour
que le joueur apprenne la règle une fois.

| Réglage | Valeur |
| --- | --- |
| Distance d'arrivée | 10 à 20 blocs |
| Si rien ne convient dans cet anneau | On cherche **plus près**, jusqu'à 3 blocs |
| Si rien ne convient près non plus | On cherche plus loin, jusqu'à 64 blocs |
| Recherche en hauteur | 8 blocs au-dessus et en dessous du joueur |
| Refus si un autre exemplaire est à moins de | 100 blocs |
| Le dresseur repart si le joueur dépasse | 128 blocs |
| Départ après le combat | 5 secondes |
| Bouderie après une mort | 5 minutes |
| Dresseurs appelés en même temps | 1 par joueur |

**Pourquoi le dresseur arrive loin.** Un dresseur qui apparaît sous le nez du joueur n'est pas
une rencontre. La position est cherchée autour de l'altitude du joueur, pas à la surface : un
joueur dans une grotte aurait sinon son dresseur sur le toit, à trente blocs et un mur de là.

**Pourquoi il arrive parfois près.** Quand l'anneau des 10 à 20 blocs ne contient nulle part où
se tenir - une clairière cernée d'eau, une corniche -, le mod cherche **plus près** avant de
chercher plus loin. Arriver à cinq blocs vaut mieux qu'arriver de l'autre côté de la crête. Il
ne descend jamais en dessous de 3 blocs : un dresseur qui se matérialise dans le joueur serait
pire que tout.

**Le dresseur n'apparaît jamais dans l'eau.** Il lui faut un sol solide et sec, la place d'un
corps, et aucun liquide - ni aux pieds, ni à la tête, ni dans le bloc sur lequel il se tient.
Ce dernier point compte : une dalle immergée, une marche sous la surface ou un bloc de glace
dans un lac sont assez solides pour qu'on s'y tienne et mettraient le dresseur les pieds dans
l'eau.

**Pourquoi il n'arrive quand même pas toujours.** Le mod ne charge aucun chunk pour répondre à
un bouton. Au milieu d'un océan, il n'y a de toute façon rien de sec entre 3 et 64 blocs, et
l'appel est refusé - le joueur regagne la terre ferme et réessaie.

## Tester

```bash
/reload
```

Puis, en jeu :

- `/cobblemontrainers list` dit quels dresseurs sont chargés.
- Ouvre le Battle Phone : un dresseur appelable a une ligne de lieu et un bouton.
- `/cobblemontrainers spawn <id>` reste le moyen de tester un dresseur **sans** passer par son
  lieu.
- Une valeur non comprise dans `location` est signalée dans le log du serveur au chargement -
  cherche les lignes `(cobblemon-trainers)`.

Pour tester un lieu qu'on n'a pas sous la main, `/locate biome`, `/locate structure` et
`/time set night` font gagner du temps.

## Erreurs fréquentes

| Symptôme | Cause |
| --- | --- |
| Aucun bouton sur la fiche | Le dresseur n'a pas de bloc `location` |
| Aucun bouton, mais le lieu s'affiche | Le bloc n'a qu'un `label` : c'est voulu, ajoute une condition pour le rendre appelable |
| Aucun bouton, et `arrival` est ignoré | Le bloc ne nomme aucun lieu ; le log le dit au chargement |
| Aucun bouton, et le dresseur est verrouillé | Un `requires` non rempli ; le bouton revient quand il l'est |
| Le dresseur n'apparaît nulle part dans la liste | `"progress": { "listed": false }`, ou un `requires` avec `hidden` |
| « n'est pas ici » alors qu'on y est | Un ID de biome ou de structure mal écrit ; le mod le compte comme non rempli |
| Le lieu affiché est un ID illisible | Une structure ou une `area` : ajoute un `label` |
| Le bouton est grisé | Dresseur déjà battu avec `"rematch": "never"` |
| « Je suis déjà quelque part par ici » | Un exemplaire du même dresseur est à moins de 100 blocs, posé par un bloc ou appelé |
| Le dresseur disparaît sans raison | Le joueur s'est éloigné de plus de 128 blocs, ou a changé de dimension |
