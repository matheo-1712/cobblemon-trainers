# Écrire une ligue

Une **ligue** est une suite de dresseurs à battre dans l'ordre, **sans que rien de ce qui est
soigné entre deux combats ne compte**. C'est le Conseil 4 : on entre, on enchaîne, et l'équipe
qui affronte le dernier est celle que les premiers ont laissée.

*This page is also available [in English](en/LEAGUES.md).*

## Le fichier

Une ligue vit dans `data/<namespace>/cobblemontrainers/leagues/<chemin>.json`, à côté de
`trainers/` et `intro/`. Son ID est `<namespace>:<chemin>`, `leagues/` non compté.

```json
// data/mon_pack/cobblemontrainers/leagues/conseil.json → mon_pack:conseil
{
  "name": "Le Conseil",
  "trainers": ["conseil/portier", "conseil/stratege", "mon_pack:conseil/maitre"],
  "heal": 0,
  "rewards": [
    { "item": "cobblemon:master_ball", "firstWinOnly": true }
  ]
}
```

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `name` | l'ID | Nom affiché ; du texte brut ou une clé de traduction, comme le `name` d'un dresseur |
| `trainers` | - | Les dresseurs, **dans l'ordre**. Un chemin sans namespace est pris dans celui de la ligue |
| `heal` | `0` | Part des PV max rendue à chaque Pokémon encore debout entre deux combats, de `0` à `1` |
| `rewards` | - | Ce que vaut la ligue terminée, en plus des récompenses de chaque dresseur. Mêmes entrées que [celles d'un dresseur](DATAPACK.md#revanches-et-récompenses) ; `firstWinOnly` y compte sur la ligue |

Les dresseurs eux-mêmes sont des dresseurs ordinaires : la ligue ne les fait pas apparaître.
Pose-les où tu veux, par exemple avec un [bloc de dresseur](SPAWNING.md) par salle.

## Le déroulé

- **Le premier dresseur combat tout le monde.** Le défier commence une série - ou la
  recommence.
- **Les suivants ne combattent que le joueur dont la série est arrivée jusqu'à eux.** Les
  autres sont refusés dans la boîte de dialogue, qui nomme le prochain adversaire.
- **Gagner fait avancer la série**, et le chat rappelle où elle en est.
- **Tout combat qui ne finit pas par une victoire met fin à la série** : défaite, fuite, joueur
  trop loin, `/stopbattle`. On repart du premier.
- **Battre le dernier termine la ligue** : récompenses, critère d'advancement, et la ligue est
  inscrite comme terminée pour ce joueur.

Une série est par joueur, et un joueur n'en mène qu'une à la fois. Elle vit en mémoire : un
redémarrage du serveur la fait perdre. Se déconnecter, non.

## Ce qui est gardé entre deux combats

À la fin de chaque combat gagné, l'équipe est notée : **PV, statut et PP** de chaque Pokémon. À
l'ouverture du suivant, elle est **ramenée** à cet état.

- C'est un plafond, pas une restauration : un Pokémon qui a *perdu* des PV entre-temps les garde
  perdus.
- Un joueur peut donc aller se soigner entre deux salles - ça ne lui achète rien, et un message
  le lui dit. Les objets dépensés pour ça sont dépensés.
- Un Pokémon K.O. reste K.O., quel que soit `heal`.
- **Aucun Pokémon ne peut rejoindre l'équipe** entre deux combats : le dresseur suivant refuse
  tant qu'il est là. En retirer un est permis.
- `heal: 1` coupe tout ça : la ligue n'est plus qu'un ordre de passage, et l'équipe est libre.

## Advancement

Terminer une ligue déclenche `cobblemon-trainers:league_completed`.

| Condition | Rôle |
| --- | --- |
| `league` | La ligue terminée : un ID complet (`mon_pack:conseil`) ou un chemin seul |
| `pack` | Son namespace |

Les deux sont facultatives. Battre un par un tous les dresseurs d'une ligue ne la termine pas :
c'est le dernier combat d'une série qui compte.

## À savoir

- **Un dresseur n'appartient qu'à une ligue.** La seconde qui le nomme est ignorée, avec une
  erreur dans le log.
- **Une ligue qui nomme un dresseur introuvable est ignorée en entier** : il lui manquerait un
  maillon.
- **Évite `progress.rematch: "never"` sur un membre** : le joueur qui perd plus loin ne pourrait
  plus jamais recommencer. Le chargement le signale.
- **Les formats `_50` ne reportent rien.** Ils se jouent sur des copies soignées de l'équipe :
  ce combat-là ne laisse aucune trace sur la suivante. Le chargement le signale aussi.
- Le `requires` d'un membre s'applique toujours, avant la règle de la ligue.
- Le Battle Phone ne montre pas encore les ligues.
