# cobblemonrlm - pack d'exemple

Pack d'exemple pour Cobblemon Trainers. Chaque dresseur illustre une combinaison différente
des options disponibles. Sers-t'en comme point de départ : copie le dossier, renomme
`cobblemonrlm` en ton namespace partout (dossiers **et** clés de traduction), et édite.

## L'installer

Ce pack est à la fois un **datapack** (les dresseurs, sous `data/`) et un **resource pack**
(les traductions et la musique, sous `assets/`). Zippe le contenu de ce dossier - les fichiers
à la racine de l'archive, pas le dossier lui-même - renomme en `.jar`, et pose-le dans
`mods/`, à côté de `cobblemon-trainers` :

```bash
cd examples/cobblemonrlm && zip -r ../../cobblemonrlm.jar .
```

C'est tout. Pas de `fabric.mod.json`, pas de resource pack à cocher : le mod lit les deux
moitiés. En jeu, `/reload` puis `/cobblemontrainers spawn cobblemonrlm:<id>` - le nom de
fichier seul suffit, le mod retrouve le dossier. `/cobblemontrainers list` montre ensuite
lesquels tu as vaincus, et `/cobblemontrainers defeat all` les coche tous pour voir la ligue
débloquée sans la jouer.

Les autres emplacements marchent aussi, si tu préfères :

| Où poser l'archive | Ce que ça charge |
| --- | --- |
| `mods/` | tout, actif partout sans rien cocher |
| `saves/<monde>/datapacks/` | les dresseurs seuls |
| `resourcepacks/`, à activer dans les options | les traductions et la musique |

Les deux dernières lignes vont ensemble : même résultat que la première, en deux fichiers au
lieu d'un. Le dossier `datapacks/` d'un monde ne lit **que** `data/` - un `assets/` posé là
est ignoré, quel que soit le format.

## Les dresseurs

Le dossier d'un dresseur est sa **catégorie**, et fait donc partie de son ID. Le pack en a
trois - `debutants`, `champions`, `kanto` -, chacune avec son `category.json` qui lui donne un
nom traduit et une place dans la liste ; les autres dresseurs restent à la racine et forment
le groupe « Dresseurs ».

| ID | Ce qu'il démontre |
| --- | --- |
| `cobblemonrlm:solo_ace` | Combat `singles`, `difficulty` 5, soin activé. Équipe avec surnom, genre entre parenthèses, `Shiny`, EV **et** IV, nature, et un objet tenu déjà namespacé (`cobblemon:leftovers`) |
| `cobblemonrlm:duo_tacticien` | Combat en double via l'alias `duo`, `difficulty` 4 |
| `cobblemonrlm:trio_marathon` | Combat en triple via l'alias `trio`, `healParty: false` - les dégâts persistent d'un combat à l'autre. Six Pokémon |
| `cobblemonrlm:debutants/debutant` | `difficulty` 0 (IA la plus faible), bas niveau, aucun message, aucun skin défini |
| `cobblemonrlm:skin_par_uuid` | `skin.type` en `player_uuid` au lieu du pseudo |
| `cobblemonrlm:skin_texture` | `skin.type` en `texture` : une image livrée par le pack, `assets/cobblemonrlm/textures/trainers/aventurier.png`. Aucun accès réseau, aucun compte Mojang |
| `cobblemonrlm:champions/champion_unique` | `"rematch": "never"` et une liste de `rewards` - un combat unique par joueur. Et un `requires` **visible** (`"hidden": false`) : il faut avoir battu Jacinthe et porter un diamant, sans quoi il décline en listant ce qui manque |
| `cobblemonrlm:champions/maitre_cache` | Un `requires` **caché** : absent du Battle Phone tant que les deux autres champions n'ont pas été battus. `victories` sans `count` veut dire « tous ceux de la catégorie », lui-même excepté |
| `cobblemonrlm:debutants/recompense_unique` | Rejouable autant qu'on veut, et **deux récompenses de régimes différents** : le Câble Liaison en `firstWinOnly` ne tombe qu'une fois, les bonbons Exp tombent à chaque victoire |
| `cobblemonrlm:formes` | Lignes `Aspects:` : un Raichu d'Alola et un Smogogo de Galar (caractéristiques à drapeau, `alolan` / `galarian`), un Motisma-Lavage (caractéristique à choix, `appliance=wash`) |
| `cobblemonrlm:polyglotte` | Textes en clés de traduction, fournies par `assets/cobblemonrlm/lang/` - passe ton jeu en anglais puis en français pour voir la différence, nom flottant compris |
| `cobblemonrlm:champions/jacinthe` | Le cas complet : équipe de six, textes traduits, et `battle.music` pointant sur une piste du pack plutôt que sur celle du mod |
| `cobblemonrlm:kanto/minimaliste` | JSON réduit au strict minimum : tous les autres champs prennent leur valeur par défaut. Son dossier `kanto/` fait partie de son ID **et** de son affichage |

## Les advancements

`data/cobblemonrlm/advancement/` branche deux advancements sur le critère
`cobblemon-trainers:trainer_defeated` :

| Advancement | Condition |
| --- | --- |
| `cobblemonrlm:badge_jacinthe` | `"trainer": "cobblemonrlm:champions/jacinthe"` - un dresseur nommé |
| `cobblemonrlm:ligue` | `"pack"` + `"category": "champions"` + `"count": 3` - les trois champions, en un seul critère |

Ils apparaissent dans l'écran des progrès du jeu, avec toast et annonce dans le chat, sans que
le mod ait à s'en occuper : ce sont des advancements ordinaires.

## À savoir

Les combats en double et en triple exigent assez de Pokémon **des deux côtés** : 2 minimum
pour un double, 3 pour un triple. Si ton équipe est trop courte, Cobblemon refuse le combat
et affiche la raison dans le chat.

Les noms et messages sont en texte brut pour la plupart des dresseurs. `polyglotte`,
`jacinthe` et les noms de catégories montrent l'autre approche : leurs textes sont des clés
de traduction définies dans `assets/cobblemonrlm/lang/`. C'est la seule façon de traduire un
dresseur - les clés sont résolues par le client, donc il faut lui livrer la partie resource
pack.

## La musique de `jacinthe`

Même logique que les traductions, et pour la même raison : le son est joué par le client,
donc il vit sous `assets/`.

```
assets/cobblemonrlm/
├── sounds.json                                 ← déclare battle_music.jacinthe
└── sounds/battle_music/jacinthe_battle.ogg
```

Le chemin du `.ogg` doit correspondre exactement au `name` de `sounds.json` :
`cobblemonrlm:battle_music/jacinthe_battle` se résout en
`assets/cobblemonrlm/sounds/battle_music/jacinthe_battle.ogg`. Un fichier absent ou mal placé
ne produit aucune erreur visible - le combat se déroule simplement en silence, le reste du
dresseur fonctionnant normalement.

Le dresseur ne connaît que la clé déclarée dans `sounds.json` :
`"music": "cobblemonrlm:battle_music.jacinthe"`, dans son bloc `battle`. Les autres dresseurs
du pack n'en définissent pas et prennent donc la piste par défaut du mod, ce qui permet de
comparer les deux à la suite.

Détail à connaître : au début du combat, tout ce qui jouait dans la catégorie *Musique* est
coupé, thème de combat compris s'il en restait un. Rien ne se superpose.

## Le skin de `skin_texture`

```
assets/cobblemonrlm/textures/trainers/aventurier.png
```

Un skin de joueur classique, en 64×64 : le fichier que tu téléverserais sur ton compte
Minecraft. Le dresseur le désigne par son emplacement complet sous `assets/` :

```json
"skin": { "type": "texture", "value": "cobblemonrlm:textures/trainers/aventurier.png" }
```

Contrairement aux traductions et à la musique, cette image est lue par le **serveur**, qui
l'envoie ensuite avec le dresseur : un joueur qui n'a pas le pack voit quand même le bon
skin. En contrepartie, elle doit être posée là où le serveur regarde, c'est-à-dire dans
`mods/` - le pack dans `resourcepacks/` seul ne suffit pas.

Ajoute `"model": "slim"` si ton image est dessinée pour le gabarit Alex (bras de 3 pixels) ;
sans ce champ, c'est le gabarit Steve qui est utilisé.
