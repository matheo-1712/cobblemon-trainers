# Habiller un dresseur

Un dresseur peut porter une armure, tenir une Poké Ball et avoir un bracelet méga au poignet.
C'est le bloc `cosmetics`.

Pour poser le bloc dans un dresseur, voir [DATAPACK.md](DATAPACK.md#tous-les-champs).

*This page is also available [in English](en/COSMETICS.md).*

## En une minute

```json
{
  "name": "Randonneuse",
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

Six emplacements d'équipement, sept endroits du corps, un ID d'objet complet dans chacun, tout
est facultatif. **Rien de tout ça ne change un combat** : pas de points d'armure, pas d'arme,
pas de drop à la mort. C'est de l'apparence, et rien d'autre.

## Les six emplacements d'équipement

| Champ | Où ça se porte |
| --- | --- |
| `head` | Sur la tête |
| `chest` | Sur le torse |
| `legs` | Sur les jambes |
| `feet` | Aux pieds |
| `mainHand` | Dans la main forte |
| `offHand` | Dans l'autre main |

Ce sont les six emplacements d'équipement d'un mob Minecraft, et c'est voulu : le jeu les
envoie déjà à tous les clients qui voient le dresseur. Un dresseur habillé ici est habillé
pour tout le monde, y compris pour un joueur qui arrive dix minutes plus tard, et il le reste
après un redémarrage.

N'importe quel objet passe, pas seulement une armure. Un objet qui n'est pas une armure dans
un emplacement d'armure ne s'affiche simplement pas - Minecraft ne sait le dessiner que sur un
corps, et il n'en a pas le modèle.

Un ID que rien ne fournit est **signalé une fois dans le log au chargement**, et l'emplacement
reste vide. Un pack qui habille son champion avec un objet d'un mod absent se charge quand
même.

## Le bloc `trinkets` : ce qui se porte

Un bracelet méga, une paire de lunettes, un pendentif, une chevillère ne se tiennent pas et ne
sont pas une armure : ils se portent quelque part sur le corps. C'est le bloc `trinkets`, **un
champ par endroit**, et aucun ne partage sa place avec un autre - un dresseur peut donc porter
les sept à la fois.

| Champ | Où c'est dessiné | Exemples |
| --- | --- | --- |
| `face` | Sur la tête | Lunettes de Max, tiare de Lisia |
| `chest` | Au cou / sur le torse | Charme de Diantha, ancre d'Arthur, pendentif |
| `wrist` | Au poignet du bras libre | Bracelet méga, gant de Korrina |
| `forearm` | Sur le même bras, un peu plus haut | Z-Ring |
| `hand` | Sur le dos de la main forte | Dynamax Band, Omni Ring |
| `belt` | À la ceinture | Orbe Tera |
| `ankle` | À la cheville droite | Chevillère de Zinnia |

**Les noms disent l'endroit, pas l'usage, et c'est volontaire.** Un objet clé n'est pas
toujours un bracelet : chez Mega Showdown, les lunettes de Max, la tiare de Lisia, le charme de
Diantha, l'ancre d'Arthur et la chevillère de Zinnia occupent **le même emplacement méga** que
le bracelet, mais se portent à cinq endroits différents. Rien dans les données du jeu ne le dit
- ce mod choisit l'endroit dans son code, objet par objet - donc aucun tag ni registre ne peut
être interrogé. C'est le pack qui sait, donc c'est le pack qui écrit.

Conséquence : **n'importe quel objet va n'importe où**. Rien n'oblige à mettre un vrai objet
clé, et un pack peut accrocher son propre bijou à la ceinture de son champion.

> **Ça ne donne aucun gimmick.** Un dresseur méga-évolue parce que son
> [`battle.gimmicks`](GIMMICKS.md) le dit, jamais parce qu'il porte le bracelet - il n'a
> d'ailleurs jamais eu besoin de l'objet pour ça. Le bracelet est là pour que ça se voie.

Les objets clés cités viennent de Mega Showdown. Sans ce mod, les IDs ne résolvent rien :
c'est signalé une fois dans le log et le dresseur ne porte rien là.

Deux endroits - `chest` et `belt` - sont posés en coordonnées de corps plutôt que sur un os,
comme chez Mega Showdown : ils ne suivent donc pas une animation qui fait pivoter le torse. Les
cinq autres suivent leur membre.

## Ce que ça ne fait pas

- **Ça ne donne aucun gimmick.** Voir plus haut.
- **Ça ne protège pas.** Un dresseur ne se bat pas au corps à corps ; l'armure n'a pas de
  points d'armure à donner.
- **Ça ne se ramasse pas.** La chance de drop de chaque emplacement est mise à zéro : un
  opérateur qui tue un dresseur pour le déplacer ne distribue pas son chapeau.
- **Ça ne suit pas un `/reload`.** Un dresseur déjà posé garde la tenue qu'il avait en
  arrivant, exactement comme son nom et son skin : ce qu'un `/reload` change, c'est la
  définition, pas les entités déjà faites avec. Casse et repose le bloc de dresseur, ou
  rappelle-le depuis le Battle Phone.

## Si rien ne s'affiche

- **Le mod doit être sur le client aussi.** C'est lui qui dessine tout ça ; Cobblemon ne
  dessine ni armure ni objet tenu sur ses PNJ.
- **Le dresseur doit venir de ce mod.** Un PNJ Cobblemon habillé autrement n'est pas repris.
- **Le dresseur doit être sur le modèle de joueur**, celui de tous les dresseurs du mod. Un
  PNJ sur un modèle à lui, aux os nommés autrement, n'est pas habillé - plutôt que de poser
  l'armure à des coordonnées devinées.
- **Un trinket ajouté après coup demande de refaire apparaître le dresseur**, comme le reste
  de la tenue : voir la règle du `/reload` ci-dessus.
- **Relis le log au chargement.** Un ID d'objet qui ne résout rien y est nommé, avec le
  dresseur et l'emplacement.
