# cobblemonrlm — pack d'exemple

Pack d'exemple pour Cobblemon Trainers. Chaque dresseur illustre une combinaison différente
des options disponibles. Sers-t'en comme point de départ : copie le dossier, renomme
`cobblemonrlm` en ton namespace partout (dossiers **et** clés de traduction), et édite.

## L'installer

Ce pack est à la fois un **datapack** (les dresseurs, sous `data/`) et un **resource pack**
(les traductions et la musique, sous `assets/`). Zippe le contenu de ce dossier — les fichiers
à la racine de l'archive, pas le dossier lui-même — renomme en `.jar`, et pose-le dans
`mods/`, à côté de `cobblemon-trainers` :

```bash
cd examples/cobblemonrlm && zip -r ../../cobblemonrlm.jar .
```

C'est tout. Pas de `fabric.mod.json`, pas de resource pack à cocher : le mod lit les deux
moitiés. En jeu, `/reload` puis `/spawntrainer cobblemonrlm:<id>`. `/listtrainers` montre
ensuite lesquels tu as vaincus.

Les autres emplacements marchent aussi, si tu préfères :

| Où poser l'archive | Ce que ça charge |
| --- | --- |
| `mods/` | tout, actif partout sans rien cocher |
| `saves/<monde>/datapacks/` | les dresseurs seuls |
| `resourcepacks/`, à activer dans les options | les traductions et la musique |

Les deux dernières lignes vont ensemble : même résultat que la première, en deux fichiers au
lieu d'un. Le dossier `datapacks/` d'un monde ne lit **que** `data/` — un `assets/` posé là
est ignoré, quel que soit le format.

## Les dresseurs

| ID | Ce qu'il démontre |
| --- | --- |
| `cobblemonrlm:solo_ace` | Combat `singles`, `skill` 5, soin activé. Équipe écrite une ligne par entrée, avec surnom, genre entre parenthèses, `Shiny`, EV **et** IV, nature, et un objet tenu déjà namespacé (`cobblemon:leftovers`) |
| `cobblemonrlm:duo_tacticien` | Combat en double via l'alias `duo`, `skill` 4. Équipe écrite un Pokémon par entrée, lignes séparées par `\n` |
| `cobblemonrlm:trio_marathon` | Combat en triple via l'alias `trio`, `autoHealParty: false` — les dégâts persistent d'un combat à l'autre. Six Pokémon |
| `cobblemonrlm:debutant` | `skill` 0 (IA la plus faible), bas niveau, aucun message, aucun skin défini |
| `cobblemonrlm:skin_par_uuid` | `skin.type` en `player_uuid` au lieu du pseudo |
| `cobblemonrlm:skin_texture` | `skin.type` en `texture` : une image livrée par le pack, `assets/cobblemonrlm/textures/trainers/aventurier.png`. Aucun accès réseau, aucun compte Mojang |
| `cobblemonrlm:pnj_pacifiste` | `canBattle: false` — le clic droit ne déclenche rien. Aussi `tracked: false`, donc absent de `/listtrainers` : il n'y a rien à battre chez lui |
| `cobblemonrlm:champion_unique` | `canRebattle: false` et une liste de `rewards` — un combat unique par joueur, avec un butin remis à la victoire. Une fois battu, il refuse la revanche et le dit dans le chat, y compris si tu le réinvoques |
| `cobblemonrlm:recompense_unique` | L'autre moitié du couple : rejouable autant qu'on veut, mais `rewardOnce: true` — la récompense ne tombe qu'à la première victoire |
| `cobblemonrlm:formes` | Lignes `Aspects:` : un Raichu d'Alola et un Smogogo de Galar (caractéristiques à drapeau, `alolan` / `galarian`), un Motisma-Lavage (caractéristique à choix, `appliance=wash`) |
| `cobblemonrlm:polyglotte` | Textes en clés de traduction, fournies par `assets/cobblemonrlm/lang/` — passe ton jeu en anglais puis en français pour voir la différence, nom flottant compris |
| `cobblemonrlm:jacinthe` | Le cas complet : équipe de six, textes traduits, et `battleMusic` pointant sur une piste du pack plutôt que sur celle du mod |
| `cobblemonrlm:minimaliste` | JSON réduit au strict minimum : tous les autres champs prennent leur valeur par défaut. Rangé dans `cobblemontrainers/kanto/`, ce qui **ne change pas** son ID — seul le nom de fichier compte |

## À savoir

Les combats en double et en triple exigent assez de Pokémon **des deux côtés** : 2 minimum
pour un double, 3 pour un triple. Si ton équipe est trop courte, Cobblemon refuse le combat
et affiche la raison dans le chat.

Les noms et messages sont en texte brut pour la plupart des dresseurs. `polyglotte` et
`jacinthe` montrent l'autre approche : leurs textes sont des clés de traduction définies
dans `assets/cobblemonrlm/lang/`. C'est la seule façon de traduire un dresseur — les clés
sont résolues par le client, donc il faut lui livrer la partie resource pack.

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
ne produit aucune erreur visible — le combat se déroule simplement en silence, le reste du
dresseur fonctionnant normalement.

Le dresseur ne connaît que la clé déclarée dans `sounds.json` :
`"battleMusic": "cobblemonrlm:battle_music.jacinthe"`. Les autres dresseurs du pack ne
définissent pas `battleMusic` et prennent donc la piste par défaut du mod, ce qui permet de
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
`mods/` — le pack dans `resourcepacks/` seul ne suffit pas.

Ajoute `"model": "slim"` si ton image est dessinée pour le gabarit Alex (bras de 3 pixels) ;
sans ce champ, c'est le gabarit Steve qui est utilisé.
