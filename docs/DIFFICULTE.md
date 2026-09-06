# La difficulté des dresseurs

`battle.difficulty` va de `0` à `5` et vaut `5` par défaut. Ce document dit exactement ce que
fait chaque valeur.

Deux choses se superposent : l'IA de Cobblemon, qui décide, et une couche du mod, qui refuse
certaines de ses décisions. La difficulté pilote les deux.

Pour poser le champ dans un dresseur, voir [DATAPACK.md](DATAPACK.md#battle).

*This page is also available [in English](en/DIFFICULTY.md).*

## Récapitulatif

| Niveau | L'IA joue au hasard | Elle change de Pokémon quand elle le veut | Le mod corrige |
| --- | --- | --- | --- |
| `0` | tous les tours | jamais | rien |
| `1` | 4 tours sur 5 | jamais | rien |
| `2` | 3 tours sur 5 | jamais | rien |
| `3` | 2 tours sur 5 | 1 fois sur 5 | les erreurs impossibles, écrans compris |
| `4` | 1 tour sur 5 | 3 fois sur 5 | idem, plus les pièges d'entrée et les écrans |
| `5` | jamais | toujours | tout, y compris la lecture du combat |

Un dresseur `0` n'est pas « un peu moins bon » qu'un `5` : il joue littéralement une capacité
au hasard à chaque tour. L'écart utile pour un joueur se situe entre `3` et `5`.

## Ce que fait Cobblemon

Deux tirages internes dépendent du niveau, et rien d'autre.

**Le tirage de compétence.** À chaque tour, l'IA tire un dé. S'il échoue, elle joue une capacité
**au hasard** parmi celles qui sont utilisables et n'y réfléchit pas davantage. S'il réussit,
toute sa logique se déroule : matchup, statuts, boosts, météo, protections, coup le plus fort.
La chance de réussite est `niveau × 20 %`, et le niveau `5` réussit toujours.

**Le tirage de changement.** Quand sa logique conclut qu'il faut changer de Pokémon, un second
dé décide si elle obéit : jamais en dessous de `3`, 20 % à `3`, 60 % à `4`, toujours à `5`.

C'est ce second tirage qui explique un comportement contre-intuitif : **les boucles de
changement n'apparaissent qu'au niveau 5**. La logique de changement de Cobblemon n'a aucune
mémoire du tour précédent, donc elle peut proposer de fuir chaque tour ; aux niveaux 3 et 4,
c'est le dé qui casse la boucle par hasard. Au niveau 5 il n'y a plus de dé. C'est la première
chose que la couche du mod corrige.

## Niveaux 0, 1 et 2 — Cobblemon seul

Le mod ne corrige **rien**. Le dresseur joue exactement comme n'importe quel PNJ Cobblemon,
erreurs comprises : il peut attaquer une cible immunisée, lancer un statut qui ne prend pas,
gaspiller son meilleur coup. Il ne change jamais de Pokémon de son plein gré.

C'est voulu. Un dresseur de début de partie doit être battable par un joueur qui découvre le
jeu, et une IA qui ne se trompe jamais ne l'est pas.

## Niveau 3 — les erreurs impossibles

La couche du mod s'allume et refuse ce qui ne peut pas fonctionner.

### Plus d'attaque sur une cible immunisée

Une capacité offensive dont l'efficacité vaut ×0 contre toutes les cibles n'est plus jouée : le
dresseur joue à la place sa meilleure capacité restante. Sol sur un Vol, Normal sur un Spectre,
Poison sur un Acier, Électrik sur un Sol, Psy sur un Ténèbres, Dragon sur un Fée.

Seule la table des types est lue à ce niveau. Un talent qui immunise (Lévitation, Absorb-Volt)
n'est **pas** vu — ça vient au niveau 5.

Cette règle rattrape aussi le tirage au hasard : une capacité tirée au sort qui se trouve être
immunisée est refusée comme les autres.

### Plus d'écran déjà posé

Protection, Mur Lumière et Voile Aurore ne sont plus rejoués tant que l'écran tient : Cobblemon
les range parmi ses capacités d'installation et les joue au hasard, sans jamais regarder ce qui
est en place, ce qui donnait un tour perdu et un « Mais cela échoue ! » dans le chat. Le
dresseur attaque à la place.

Trois cas sont refusés :

| Cas | Pourquoi |
| --- | --- |
| L'écran est déjà debout | Les écrans ne se cumulent pas |
| Voile Aurore est debout | Il vaut déjà Protection et Mur Lumière |
| Voile Aurore sans neige ni grêle | La capacité échoue |

Un écran posé compte comme en place pendant huit tours, la durée que lui donne la Lumargile.

### Un changement doit servir à quelque chose

Un changement volontaire n'est accepté que si le Pokémon entrant améliore réellement le rapport
de force — d'au moins une demi-marche d'efficacité de type. Fuir vers un Pokémon aussi mal loti
n'est plus possible, ce qui supprime la boucle.

Quatre situations échappent à cette mesure, parce que le dresseur ne cherche alors pas un
meilleur matchup :

| Situation | Pourquoi le changement passe |
| --- | --- |
| PV sous 30 % | Il essaie de sauver un Pokémon, pas de gagner l'échange |
| Une stat tombée à -3 ou pire | Sortir remet les baisses à zéro |
| Toutes ses capacités résistées (×0.5 ou moins) | Il est muré : rester ne mène nulle part |
| Plus aucune capacité ne fait quoi que ce soit | Impasse totale |

Dans le dernier cas, le dresseur part **même si Cobblemon ne l'avait pas proposé** — mais
seulement vers un remplaçant strictement meilleur, faute de quoi la boucle reviendrait.

## Niveau 4 — le plan de jeu

Tout ce que fait le niveau 3, plus deux règles. Ce sont les deux seules de tout le système qui
**ajoutent** une décision : partout ailleurs, la couche se contente de refuser celles de
Cobblemon.

### Les pièges d'entrée

Si le **premier** Pokémon envoyé connaît un piège d'entrée, il le pose au premier tour. Ordre de
préférence quand il en connaît plusieurs :

1. Piège de Roc — touche tout ce qui entre et ignore Vol et Lévitation
2. Picots
3. Pics Toxik
4. Toile Gluante

Un seul piège est posé par dresseur, donc les deux premiers Pokémon d'un combat double n'en
posent pas deux fois le même. Aucun autre Pokémon de l'équipe n'en posera plus tard.

### Les écrans, avec une Lumargile

Un Pokémon qui tient une **Lumargile** et connaît Protection, Mur Lumière ou Voile Aurore pose
son écran plutôt que d'attaquer. L'objet est ce qui déclenche la règle : sans écran il ne sert
à rien, donc un pack qui l'a donné a déjà pris la décision.

| Il pose | Quand |
| --- | --- |
| Voile Aurore | Il neige ou il grêle - l'écran vaut alors pour les deux camps de dégâts à la fois |
| Protection | Le coup le plus dur qui l'attend est physique |
| Mur Lumière | Le coup le plus dur qui l'attend est spécial |

- **Jamais deux fois le même** : un écran déjà posé compte comme en place pendant huit tours,
  la durée que lui donne la Lumargile. Le dresseur passe donc à autre chose dès le tour
  suivant.
- **Après Voile Aurore, plus aucun écran** : il vaut Protection et Mur Lumière à lui seul, les
  poser ensuite ne ferait que dépenser des tours.
- **Voile Aurore hors neige n'est jamais joué** : la capacité échoue, Showdown la propose
  quand même.
- **Quatre situations rendent la main** : un KO est disponible ce tour-ci, le Pokémon tombe ce
  tour-ci, Cobblemon voulait se soigner, ou il jouait déjà un écran de lui-même.

Sans Lumargile, rien ne change : Cobblemon joue ses écrans comme n'importe quelle capacité
d'installation, au hasard et sans savoir si l'un est déjà en place.

## Niveau 5 — la lecture du combat

Tout ce que font les niveaux 3 et 4, plus l'ensemble ci-dessous.

### Les immunités de talent et d'objet

L'efficacité prend en compte ce qui annule un type au-delà de la table :

| Talent ou objet | Type annulé |
| --- | --- |
| Lévitation, Absorbe-Terre, Ballon | Sol |
| Absorb-Volt, Motorisé, Paratonnerre | Électrik |
| Absorb-Eau, Peau Sèche, Lavabo | Eau |
| Torche, Cuisson Idéale | Feu |
| Herbivore | Plante |
| Garde Mystik | tout ce qui n'est pas super efficace |

Un Mewtwo n'enverra plus Séisme sur un Lévitation.

### Les statuts qui ne peuvent pas prendre

Toxik sur un Poison ou un Acier, Cage Éclair sur un Électrik, Feu Follet sur un Feu, ou
n'importe quel statut sur une cible qui en porte déjà un : la capacité est refusée et le
dresseur joue autre chose.

### Le coup le plus fort par défaut

Quand Cobblemon choisit une capacité offensive **sans raison déclarée** — c'est-à-dire pas un
statut, pas un boost, pas un piège, pas un soin, pas une protection — et qu'une autre fait plus
de dégâts, c'est celle-là qui est jouée.

Cette règle neutralise le tirage au hasard résiduel de Cobblemon sans écraser son jeu réfléchi :
une capacité à laquelle il tient est reconnue comme telle et jamais échangée contre des dégâts.

### Le soin au bon moment

Cobblemon soigne dès que les PV passent sous la moitié, en jouant la première capacité de soin
qu'il trouve, sans rien regarder d'autre. Trois questions sont posées avant de le laisser faire :

- **Un KO est-il en main ?** Si une capacité met l'adversaire KO ce tour-ci, elle passe avant.
- **Le soin rachète-t-il le tour ?** Si l'adversaire rend au moins autant que ce qui est
  restauré, le dresseur finit le tour où il l'a commencé : il attaque à la place.
- **Le soin sera-t-il absorbé ?** S'il manque moins de 60 % de ce que la capacité restaure, le
  reste déborde : le dresseur attaque et gardera le soin pour plus tard.

**Limite connue :** la couche peut refuser un soin, jamais en ajouter un. Un dresseur ne se
soignera donc jamais au-dessus de la moitié de ses PV, même quand ce serait le bon jeu.

### Ce qui encaisse un coup décisif

Le dresseur ne croit plus à un KO que la cible survivrait :

- **Baraka** et la **Ceinture Force** à PV pleins, qui laissent 1 PV.
- Une **Frimousse** ou une **Tête de Gel** intacte, qui annule le coup entier.

Et dans le cas de la Frimousse ou de la Tête de Gel, il ne gaspille pas son meilleur coup dedans :
il la casse avec sa capacité **la plus faible** et garde la grosse pour le tour suivant.

L'état de la garde est lu de deux façons — la forme du Pokémon (Mimiqui démasqué, Bekaglaon
sans glace) et le souvenir de l'avoir déjà frappé. La première des deux qui dit « cassée »
l'emporte.

### Mettre KO en premier

Si une capacité met un adversaire KO **et** que le dresseur frappe avant lui — vitesse et
priorité comparées —, elle est jouée quoi qu'il ait prévu d'autre.

Une égalité de vitesse est comptée comme perdue. En jeu c'est un pile ou face, et une IA qui
suppose gagner tous ses pile ou face joue imprudemment.

La priorité prêtée à l'adversaire est celle de sa capacité **la plus dangereuse** contre la
cible, pas la plus prioritaire de son arsenal. Sinon un joueur portant une simple Vive-Attaque
suffirait à convaincre le dresseur qu'il ne passe jamais en premier.

Distorsion et Vent Arrière ne sont pas pris en compte : l'ordre peut donc être mal jugé sous
Distorsion.

### Le dernier tour va à ce qui part encore

Quand le dresseur tombe ce tour-ci et qu'il frappe en second, la capacité prévue ne se
résoudra jamais. Seule une capacité **prioritaire** partira encore : c'est celle-là qu'il joue,
la plus forte parmi celles qui passent avant.

Un Mimiqui boosté à la Danse-Lames et sur le point de tomber joue donc Ombre Portée plutôt que
Câlinerie — le premier part, le second arrive après le KO.

S'il n'a aucune capacité prioritaire, plus rien ne se résout de toute façon. Il attaque quand
même plutôt que de se préparer : l'estimation peut se tromper d'un jet de dégâts ou d'un raté,
et un boost posé pour un tour qui n'arrive pas est perdu à coup sûr.

### Pas deux Abri d'affilée

Abri, Détection et compagnie ne sont pas rejoués deux tours de suite : la seconde tentative a
une chance sur trois d'aboutir et offre un tour gratuit quand elle échoue.

Cette règle porte sur les protections **du dresseur** uniquement. Compter celles du joueur
demanderait un état auquel la couche n'a pas accès.

## Ce qui n'est jamais corrigé, à aucun niveau

- **Le remplaçant envoyé après un KO.** C'est un choix entièrement laissé à Cobblemon.
- **Une capacité imposée** : Encore, Colère, un objet qui verrouille le choix. Il n'y a rien à
  décider.
- **Les gimmicks de combat** (méga-évolution, téracristallisation). Ils ne dépendent pas de
  `difficulty` du tout : c'est le pack qui les déclare, pas l'IA qui a une bonne idée. Voir
  [GIMMICKS.md](GIMMICKS.md). Le téracristal se lit quand même mieux à `difficulty: 5` - l'un de
  ses deux déclencheurs porte sur le coup déjà choisi, et un dresseur qui choisit mal a rarement
  le bon coup en main.
- **Le niveau, les statistiques et l'équipe du dresseur.** `difficulty` est la qualité du jeu,
  pas la puissance de l'équipe. Un dresseur niveau 100 en `difficulty: 0` reste facile, un
  dresseur niveau 20 en `difficulty: 5` reste faible.

Enfin, la couche ne juge que ce que Cobblemon lui propose. Sauf pour les deux règles du
niveau 4 - le piège d'entrée et l'écran sous Lumargile -, elle ne peut pas faire jouer une
action à laquelle Cobblemon n'avait pas pensé.

## Vérifier en jeu

```
/cobblemontrainers debugai
```

Tant que l'interrupteur est actif, chaque décision corrigée s'affiche dans votre chat pendant le
combat, avec sa raison et les chiffres qui l'ont motivée. Relancez la commande pour l'éteindre.

C'est le seul moyen de faire la différence entre « le dresseur n'a pas voulu changer » et « le
dresseur a voulu changer et le mod l'en a empêché ». Réservé aux opérateurs, comme le reste de
la commande.

## Quel niveau choisir

| Pour | Niveau |
| --- | --- |
| Un premier dresseur, un tutoriel | `0` à `1` |
| Un dresseur de route | `2` à `3` |
| Un rival, un chef d'arène | `4` |
| Un champion, un boss de fin | `5` |

À `5`, un dresseur joue mieux que la plupart des joueurs occasionnels. Compensez par l'équipe
plutôt que par le niveau si le combat doit rester abordable : une équipe plus courte ou moins
bien construite reste lisible pour le joueur, alors qu'une IA volontairement stupide passe pour
un bug.
