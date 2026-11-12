# Écrire une ligue

Une **ligue** est un tournoi : le joueur s'inscrit auprès d'un **hôte**, puis les dresseurs
arrivent l'un après l'autre, **sans aucun soin entre deux combats**. C'est le Conseil 4 : on
entre, on enchaîne, et l'équipe qui affronte le dernier est celle que les premiers ont laissée.

*This page is also available [in English](en/LEAGUES.md).*

## Le fichier

Une ligue vit dans `data/<namespace>/cobblemontrainers/leagues/<chemin>.json`, à côté de
`trainers/` et `intro/`. Son ID est `<namespace>:<chemin>`, `leagues/` non compté.

```json
// data/mon_pack/cobblemontrainers/leagues/conseil.json → mon_pack:conseil
{
  "name": "Le Conseil",
  "host": {
    "name": "Arbitre du Conseil",
    "skin": { "type": "player_username", "value": "Notch" },
    "messages": { "greeting": "Trois dresseurs à la suite. Tu entres ?" }
  },
  "trainers": ["conseil/portier", "conseil/stratege", "mon_pack:conseil/maitre"],
  "rewards": [
    { "item": "cobblemon:master_ball", "firstWinOnly": true }
  ]
}
```

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `name` | l'ID | Nom affiché ; du texte brut ou une clé de traduction, comme le `name` d'un dresseur |
| `host` | - | Le personnage auprès duquel on s'inscrit, voir ci-dessous |
| `trainers` | - | Les dresseurs, **dans l'ordre**. Un chemin sans namespace est pris dans celui de la ligue |
| `rewards` | - | Ce que vaut la ligue terminée, en plus des récompenses de chaque dresseur. Mêmes entrées que [celles d'un dresseur](DATAPACK.md#revanches-et-récompenses) ; `firstWinOnly` y compte sur la ligue |

Les dresseurs nommés sont des dresseurs ordinaires de `trainers/`. **La ligue les fait
apparaître elle-même**, à leur tour, et les retire ensuite : il n'y a rien à poser pour eux. Ils
arrivent à côté du joueur, ou sur [l'arène](#larène) si la ligue en a une. Un exemplaire du même
dresseur posé ailleurs dans le monde reste un dresseur normal.

## L'hôte

L'hôte n'est **pas un dresseur** : il n'a ni équipe ni combat, et n'existe que dans le fichier
de la ligue.

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `host.name` | le nom de la ligue | Son nom |
| `host.skin` | Steve | Le même bloc que le [`skin` d'un dresseur](DATAPACK.md#skins) |
| `host.messages.greeting` | une phrase du mod | Dit au clic droit, au-dessus de *Participer / Annuler* |
| `host.messages.decline` | - | Dit à qui choisit *Annuler* |
| `host.messages.between` | - | Dit entre deux combats, au-dessus de l'avancement |
| `host.messages.win` | une phrase du mod | Dit une fois le dernier dresseur battu |
| `host.messages.lose` | une phrase du mod | Dit, dans le chat, au joueur dont le tournoi vient de s'arrêter |

On le pose avec `/cobblemontrainers league <id> [<x> <y> <z>]` (opérateur). Il reste là jusqu'à
ce qu'on le retire, redémarrages compris.

## L'arène

Par défaut, chaque dresseur apparaît à quelques blocs du joueur. Une **arène** fixe l'endroit :
tous les dresseurs de la ligue y apparaissent, tournés vers le joueur.

```
/cobblemontrainers league <id> arena set [<x> <y> <z>]
/cobblemontrainers league <id> arena clear
```

| Forme | Effet |
| --- | --- |
| `arena set` | L'arène est là où vous vous tenez, dans votre dimension |
| `arena set <x> <y> <z>` | À ces coordonnées, `~` et `^` compris |
| `arena clear` | Plus d'arène : les dresseurs reviennent à côté du joueur |

- **L'arène appartient au monde, pas au pack** : elle est enregistrée dans la sauvegarde, donc
  elle survit aux redémarrages et aux `/reload`, et un même pack a une arène par monde.
- **Une arène par ligue.** La redéfinir remplace l'ancienne.
- **Le point est pris tel quel**, sans chercher de sol libre autour : c'est à vous de le choisir
  dégagé.
- **Posez-la près de l'hôte.** Un joueur à plus de la distance de fuite de Cobblemon
  (`defaultFleeDistance`), ou dans une autre dimension, ne pourrait pas tenir le combat : pour
  lui le dresseur apparaît à ses côtés, comme sans arène.

## Le déroulé

1. **Clic droit sur l'hôte**, puis *Participer*. L'hôte refuse un joueur sans Pokémon en état de
   se battre, déjà dans un tournoi, ou qui arrive pendant le tournoi d'un autre.
2. **Le premier dresseur apparaît** - à quelques blocs, ou sur l'arène - et son combat s'ouvre tout seul - avec son
   [écran de versus](INTROS.md) s'il en a un. Ses `requires` et sa règle de revanche ne sont pas
   consultés : ici c'est la ligue qui décide.
3. **Combat gagné** : le dresseur dit son mot de la fin, disparaît, et **la boîte de l'hôte
   s'ouvre** - où on en est, qui vient ensuite, et deux boutons, *Continuer* ou *Abandonner*.
   Fermer la boîte avec Échap continue.
4. **Le suivant apparaît**, et ainsi de suite.
5. **Le dernier battu, la ligue est terminée** : récompenses, critère d'advancement, et l'hôte
   félicite le joueur.

**Tout combat qui ne finit pas par une victoire arrête le tournoi** : défaite, fuite, joueur trop
loin, `/stopbattle`, déconnexion. Il faut retourner voir l'hôte et repartir du premier.

Chaque dresseur battu compte comme une victoire normale : ses récompenses sont remises, sa ligne
du Battle Phone est cochée, ses advancements se déclenchent.

**Une ligue n'accueille qu'un joueur à la fois** : tant qu'un tournoi est en cours, l'hôte refuse
les autres en nommant celui qui joue, quel que soit l'exemplaire de l'hôte auquel on s'adresse.
La place se libère dès que ce tournoi se termine ou s'arrête. Deux ligues différentes se jouent
en même temps sans se gêner.

## Pas de soin

À la fin de chaque combat gagné, l'équipe est notée : **PV, statut et PP** de chaque Pokémon. À
l'ouverture du suivant, elle est **ramenée** à cet état.

- C'est un plafond, pas une restauration : un Pokémon qui a *perdu* des PV entre-temps les garde
  perdus.
- Ce qu'un joueur arrive à soigner entre deux combats lui est donc repris, et un message le lui
  dit. Les objets dépensés pour ça sont dépensés.
- Un Pokémon K.O. reste K.O.
- **Aucun Pokémon ne peut rejoindre l'équipe** en cours de tournoi : ça l'arrête. En retirer un
  est permis.

## Advancement

Terminer une ligue déclenche `cobblemon-trainers:league_completed`.

| Condition | Rôle |
| --- | --- |
| `league` | La ligue terminée : un ID complet (`mon_pack:conseil`) ou un chemin seul |
| `pack` | Son namespace |

Les deux sont facultatives. Battre un par un tous les dresseurs d'une ligue ne la termine pas :
c'est le dernier combat d'un tournoi qui compte.

## À savoir

- **Une ligue qui nomme un dresseur introuvable est ignorée en entier** : il lui manquerait un
  maillon.
- **Les formats `_50` ne reportent rien.** Ils se jouent sur des copies soignées de l'équipe :
  ce combat-là ne laisse aucune trace sur le suivant. Le chargement le signale.
- **Sans arène, il faut de la place autour du joueur** : le dresseur suivant cherche un sol libre
  à quelques blocs, au même étage. S'il n'en trouve pas, le tournoi s'arrête.
- Un `/reload` ou un redémarrage en plein tournoi le fait perdre ; le dresseur resté dans le
  monde est retiré au chargement.
- Le Battle Phone ne montre pas encore les ligues.
