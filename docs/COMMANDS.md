# Les commandes

Tout ce que le mod fait depuis le chat tient dans une seule commande, `/cobblemontrainers`, et
un verbe.

```
/cobblemontrainers spawn <id> [<x> <y> <z>]
/cobblemontrainers list [<joueur>]
/cobblemontrainers defeat <id|all> [<joueurs>] [reset]
/cobblemontrainers debugai
```

*This page is also available [in English](en/COMMANDS.md).*

**Le niveau de permission 2 (opérateur) est vérifié une seule fois, sur la racine**, et couvre
donc les quatre verbes. Un joueur ordinaire ne voit aucun d'eux dans l'autocomplétion : ce qui
lui est destiné, c'est le [Battle Phone](../README.md#le-battle-phone), qui lit la même
progression sans donner le moindre pouvoir.

| Verbe | Ce qu'il fait |
| --- | --- |
| [`spawn`](#spawn) | Fait apparaître un dresseur |
| [`list`](#list) | Lit la progression d'un joueur |
| [`defeat`](#defeat) | Écrit une victoire sans combat |
| [`debugai`](#debugai) | Montre en chat ce que le mod corrige chez l'IA |

## L'ID d'un dresseur

`spawn` et `defeat` prennent le même argument. Il accepte les deux formes :

| Écrit | Lu comme |
| --- | --- |
| `mon_pack:champions/erika` | ce dresseur-là |
| `champions/erika` | le premier dresseur chargé qui porte ce chemin |
| `erika` | idem, le dossier retrouvé tout seul |

L'autocomplétion, elle, propose toujours l'**ID complet** : le namespace *est* le pack d'où
vient le dresseur, donc c'est la seule chose qui distingue deux packs livrant le même nom de
fichier. La recherche porte sur les deux moitiés - `jac` retrouve `mon_pack:jacinthe`.

Un dresseur absent de l'autocomplétion n'a pas été chargé : la raison est dans le log du
serveur, et [DATAPACK.md](DATAPACK.md#erreurs-fréquentes) en liste les causes.

## `spawn`

```
/cobblemontrainers spawn <id>
/cobblemontrainers spawn <id> <x> <y> <z>
```

Fait apparaître le dresseur, à la position de l'appelant si aucune coordonnée n'est donnée.
Les coordonnées acceptent la syntaxe vanilla, `~` et `^` compris.

- **Le dresseur ne revient pas** si on le tue : c'est un outil de test et de dépannage. Pour un
  dresseur qui tient un poste, il y a le [bloc de dresseur](../README.md#le-bloc-de-dresseur) ;
  pour un dresseur que le joueur fait venir lui-même, il y a
  [l'appel depuis le Battle Phone](SPAWNING.md).
- **Le lieu du dresseur n'est pas vérifié.** Un dresseur qui ne répond qu'à minuit dans les
  mesas apparaît quand même en plein jour : c'est ce qui permet de tester son équipe sans aller
  chercher son biome.
- Une position hors du monde est refusée, comme pour `/summon`.

## `list`

```
/cobblemontrainers list
/cobblemontrainers list <joueur>
```

Liste les dresseurs par catégorie et coche ceux que le joueur a battus. Sans argument, c'est
votre propre progression.

```
Dresseurs de Steve - 1 / 3 vaincus
Champions - 1 / 2
✔ mon_pack:champions/jacinthe - Jacinthe (plus de revanche)
✘ mon_pack:champions/maitre - Le Maître (verrouillé, 1 condition(s) restante(s))
Dresseurs - 0 / 1
✘ mon_pack:rival - Rival
```

- **C'est la vue de l'opérateur** : un dresseur verrouillé y figure toujours, avec le nombre de
  conditions qui lui restent, là où le Battle Phone le cache par défaut. Les conditions sont
  évaluées contre le **joueur ciblé**, pas contre vous.
- Seuls les dresseurs `"listed": true` apparaissent, dans l'ordre exact du Battle Phone.
- La commande rend le nombre de dresseurs vaincus, lisible avec
  `execute store result score …` - de quoi brancher un système de score sur une ligue.

## `defeat`

```
/cobblemontrainers defeat <id> [<joueurs>] [reset]
/cobblemontrainers defeat all [<joueurs>] [reset]
```

Inscrit une victoire **sans combat**. Le dresseur compte comme vaincu, les advancements sont
évalués, les dresseurs verrouillés derrière lui s'ouvrent, et sa fiche du Battle Phone révèle
son équipe - exactement ce qu'aurait fait une vraie victoire.

| Forme | Effet |
| --- | --- |
| `defeat <id>` | Vous avez battu ce dresseur |
| `defeat <id> <joueurs>` | Eux l'ont battu. Sélecteurs vanilla acceptés (`@a`, `@p[…]`) |
| `defeat all` | Tous les dresseurs chargés d'un coup |
| `… reset` | L'inverse : la victoire est oubliée |

- **Aucune récompense n'est remise**, et le message de fin de combat n'est pas envoyé : un
  outil de test doit pouvoir tourner cent fois sans enterrer le joueur sous les objets.
- **`all` prend tous les dresseurs chargés**, pas seulement les `listed`.
- **Le trigger part même si la victoire était déjà inscrite.** C'est ce qui rattrape un
  advancement ajouté après coup, sans avoir à rejouer le combat.
- **`reset` ne retire pas un advancement déjà obtenu.** Minecraft ne le fait qu'avec
  `/advancement revoke`.
- La commande rend le nombre de couples (dresseur, joueur) touchés.

## `debugai`

```
/cobblemontrainers debugai
```

Un interrupteur. Tant qu'il est actif, chaque décision que le mod refuse à l'IA d'un dresseur
s'affiche dans votre chat pendant le combat, avec sa raison et les chiffres qui l'ont motivée -
coup refusé, changement refusé, soin écarté. Relancez la commande pour l'éteindre.

C'est le seul moyen de distinguer « le dresseur n'a pas voulu changer » de « le dresseur a
voulu changer et le mod l'en a empêché ». Voir [DIFFICULTE.md](DIFFICULTE.md) pour ce que
chaque niveau corrige.

- **Le réglage est par joueur et vit en mémoire** : il est oublié à la déconnexion.
- Il montre les décisions de **n'importe quel dresseur d'un combat où vous êtes**, donc
  regarder le combat d'un autre suffit à en lire l'IA.

## Erreurs fréquentes

| Message | Cause |
| --- | --- |
| `Dresseur introuvable` | L'ID ne correspond à aucun dresseur chargé - vérifie le dossier `data/<ns>/cobblemontrainers/` |
| `Aucun dresseur chargé` | Aucun pack n'a été lu du tout ; le log dit pourquoi |
| `Tous les dresseurs chargés sont masqués des listes` | Ils sont tous en `"listed": false` |
| `Position invalide` | Coordonnées hors des limites du monde |
| `Le dresseur n'a pas pu être invoqué` | Le log du serveur donne le détail - typiquement une équipe qu'aucun Pokémon ne survit à parser |
| La commande n'existe pas dans l'autocomplétion | Permission inférieure à 2, ou mod absent du serveur |
