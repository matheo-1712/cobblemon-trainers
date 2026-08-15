# cobblemonrlm — pack d'exemple

Pack d'exemple pour Cobblemon Trainers. Chaque dresseur illustre une combinaison différente
des options disponibles. Sers-t'en comme point de départ : copie le dossier, renomme
`cobblemonrlm` en ton namespace partout (dossiers **et** clés de traduction), et édite.

## L'installer

Ce pack est à la fois un **datapack** (les dresseurs, sous `data/`) et un **resource pack**
(les traductions et la musique, sous `assets/`). Il porte donc `pack.mcmeta` *et*
`fabric.mod.json` : la même archive marche aux trois emplacements, à toi de choisir.

Zippe le contenu de ce dossier — les fichiers à la racine de l'archive, pas le dossier
lui-même :

```bash
cd examples/cobblemonrlm && zip -r ../../cobblemonrlm-1.0.0.jar .
```

| Où poser l'archive | Ce que ça charge |
| --- | --- |
| `mods/` (en `.jar`) | tout, actif partout sans rien cocher |
| `saves/<monde>/datapacks/` | les dresseurs seuls |
| `resourcepacks/`, à activer dans les options | les traductions et la musique |

Les deux dernières lignes vont ensemble : c'est le même résultat que la première, en deux
fichiers au lieu d'un. Le `.jar` n'est pas obligatoire hors de `mods/` — un `.zip` fait
exactement le même travail dans `datapacks/` et `resourcepacks/`.

Ensuite, en jeu : `/reload`, puis `/spawntrainer cobblemonrlm:<id>`.

Attention : dans `mods/`, c'est `fabric.mod.json` qui compte, et **sans lui Fabric ignore le
jar sans le moindre message.** Dans `datapacks/` et `resourcepacks/`, c'est `pack.mcmeta`.

## Les dresseurs

| ID | Ce qu'il démontre |
| --- | --- |
| `cobblemonrlm:solo_ace` | Combat `singles`, `skill` 5, soin activé. Équipe écrite une ligne par entrée, avec surnom, genre entre parenthèses, `Shiny`, EV **et** IV, nature, et un objet tenu déjà namespacé (`cobblemon:leftovers`) |
| `cobblemonrlm:duo_tacticien` | Combat en double via l'alias `duo`, `skill` 4. Équipe écrite un Pokémon par entrée, lignes séparées par `\n` |
| `cobblemonrlm:trio_marathon` | Combat en triple via l'alias `trio`, `autoHealParty: false` — les dégâts persistent d'un combat à l'autre. Six Pokémon |
| `cobblemonrlm:debutant` | `skill` 0 (IA la plus faible), bas niveau, aucun message, aucun skin défini |
| `cobblemonrlm:skin_par_uuid` | `skin.type` en `player_uuid` au lieu du pseudo |
| `cobblemonrlm:pnj_pacifiste` | `canBattle: false` — le clic droit ne déclenche rien |
| `cobblemonrlm:polyglotte` | Textes en clés de traduction, fournies par `assets/cobblemonrlm/lang/` — passe ton jeu en anglais puis en français pour voir la différence, nom flottant compris |
| `cobblemonrlm:jacinthe` | Le cas complet : équipe de six, textes traduits, et `battleMusic` pointant sur une piste du pack plutôt que sur celle du mod |
| `cobblemonrlm:minimaliste` | JSON réduit au strict minimum : tous les autres champs prennent leur valeur par défaut. Rangé dans `cobblemontrainers/trainers/kanto/`, ce qui **ne change pas** son ID — seul le nom de fichier compte |

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
└── sounds/battle_music/jacinthe_battle.ogg     ← à fournir toi-même
```

**Le `.ogg` n'est pas livré ici** — pas de piste dont la licence permettrait de la
redistribuer dans ce dépôt. Dépose la tienne à ce chemin exact, en Ogg Vorbis. Sans elle,
`jacinthe` se bat en silence : le reste du dresseur fonctionne normalement.

Le dresseur ne connaît que la clé déclarée dans `sounds.json` :
`"battleMusic": "cobblemonrlm:battle_music.jacinthe"`. Les autres dresseurs du pack ne
définissent pas `battleMusic` et prennent donc la piste par défaut du mod, ce qui permet de
comparer les deux à la suite.

Détail à connaître : au début du combat, tout ce qui jouait dans la catégorie *Musique* est
coupé, thème de combat compris s'il en restait un. Rien ne se superpose.
