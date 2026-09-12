# L'éditeur web

La page publiée sur <https://matheo-1712.github.io/cobblemon-trainers/> : on y écrit un
dresseur, une intro, une catégorie et un advancement, on y dépose les musiques, les skins et
les images, on y traduit les textes, et on télécharge le pack entier prêt à poser dans `mods/`.

Tout se passe dans le navigateur - aucun serveur, aucun compte, rien qui parte ailleurs. Le
pack en cours est gardé dans le `localStorage` (clé `ct-pack`) et les fichiers déposés dans
IndexedDB, et deux services extérieurs sont appelés : `crafthead.net` pour l'image d'un skin de
joueur (l'API Mojang refuse le navigateur) et `cdnjs` pour JSZip.

**Importer une archive remplace le pack en cours**, après confirmation : un `.zip` ou un `.jar`
est un pack entier, et deux packs fondus l'un dans l'autre partagent un namespace - leurs
fichiers répondraient alors à des ID qu'aucun des deux auteurs n'a écrits. Un `.json` seul
continue d'entrer dans le pack ouvert : celui-là est un fichier, pas un pack.

**Rien n'est créé en arrivant** : un pack neuf est vide, et les quatre boutons de l'écran
d'accueil ou le menu des modèles décident de ce qu'il contient. **Un rafraîchissement ne perd
rien**, ni le pack ni l'endroit où on en était : le fichier ouvert, l'onglet, le calque
sélectionné, la vue de l'aperçu et les réglages de la scène sont gardés à côté, sous `ct-view`.
Deux clés plutôt qu'une parce qu'une vue n'appartient pas à un pack et ne doit jamais voyager
dedans. Le bouton **Tout effacer** vide les deux.

## Ce que l'archive contient

| Chemin | D'où il vient |
| --- | --- |
| `pack.mcmeta` | Écrit, avec l'intervalle de `pack_format` des deux côtés |
| `data/<ns>/cobblemontrainers/…` | Les dresseurs, les intros, les `category.json` |
| `data/<ns>/advancement/…` | Les advancements |
| `assets/<ns>/sounds/…` · `textures/…` | Les fichiers déposés dans **Ressources** - un asset ne se charge que depuis `resourcepacks/` ou `mods/` |
| `assets/<ns>/sounds.json` | **Écrit** à partir des sons du pack - `stream` vrai pour une musique |
| `assets/<ns>/lang/<code>.json` | L'onglet **Traductions** |
| `fabric.mod.json` | Seulement en `.jar`, pour déclarer la dépendance au mod |

**La catégorie d'un dresseur se choisit dans un menu**, en haut de sa fiche, à gauche de son
chemin. Dans le mod le dossier **est** la catégorie, donc le menu et le champ de chemin sont
deux vues d'une même chaîne : choisir range le dresseur, taper `ligue/peter` place le menu sur
`ligue`. Il propose les dossiers que le pack utilise déjà - ceux qu'un `category.json` décrit,
avec leur nom affiché, et ceux où un dresseur se trouve déjà. On n'y invente pas une catégorie :
un dossier vide n'en est pas une tant que rien n'y est rangé, donc une nouvelle se crée en la
tapant dans le chemin.

**Un champ qui nomme une ressource en propose un menu** : la musique qu'on vient de déposer, le
skin du pack, une intro livrée avec le mod. Le menu et le champ éditent la même valeur et
restent d'accord - un ID venu d'ailleurs se tape toujours à la main, et le menu dit alors
« saisi à la main » plutôt que de le réécrire. Un champ qui n'a rien à proposer n'a pas de
menu, et celui d'un skin ne propose les images du pack que si son type est bien `texture`.

Une ressource ne se nomme jamais à la main : son emplacement dans l'archive et la référence
qu'un dresseur écrit (`mon_pack:battle_music.finale`) sortent du même endroit, donc ils ne
peuvent pas se contredire. C'est pour ça que le bouton **Importer…**, posé à côté du champ
qui nomme une image, fait les deux d'un seul geste : le fichier entre dans le pack et le champ
est rempli de l'identifiant qui l'atteint. Un glisser-déposer sur la même zone fait de même. Le bouton **Clés de traduction** fait le reste : il remplace les
phrases d'un dresseur par des clés et range les phrases dans la première langue.

## Y travailler

```bash
bash web/sync-assets.sh          # remplit web/assets/ depuis le mod
python -m http.server 8765 -d web
```

`sync-assets.sh` **copie depuis le dépôt** le logo et l'icône du mod, les textures d'intro, les
huit intros livrées et les dresseurs d'exemple, et il **écrit `js/shipped.js`** à partir du
`sounds.json` du mod : c'est de ses clés que sortent les musiques proposées, donc une piste
ajoutée au mod est offerte par l'éditeur sans que personne édite une liste. Ce fichier-là est
commité, contrairement à `assets/` - un `<script>` se charge même depuis le disque, là où un
`fetch` serait refusé, et la liste des pistes est trop centrale pour manquer dans le montage
qu'un auteur essaie en premier. Rien de tout ça n'est commité : c'est le workflow `pages.yml` qui rejoue le
script à la publication, donc une intro qui change dans le mod change dans l'éditeur, et
l'éditeur ne peut pas décrire une version du mod qui n'existe plus.

Ouvrir `index.html` directement marche aussi, sauf le menu des modèles : c'est la seule chose
que l'éditeur lise par un `fetch`, que le navigateur refuse sur `file://`.

## Les fichiers

| Fichier | Rôle |
| --- | --- |
| `js/schema.js` | **Tous les champs de tous les formats**, labels FR et EN compris. Ajouter un champ au mod, c'est ajouter une entrée ici et nulle part ailleurs |
| `js/form.js` | Le moteur de formulaire : un schéma entre, du DOM sort, et l'objet édité **est** le fichier |
| `js/validate.js` | Les pièges du mod dits à voix haute - un champ que Gson ignore en silence, un `dynamax` qui s'écrit `max` |
| `js/preview.js` | `BattleIntroScreen.kt` transcrit : mêmes courbes, mêmes entrées, même 640 × 360 |
| `js/stage.js` | L'aperçu d'intro rendu manipulable : la sélection, le glisser-déposer, les poignées, l'aimantation et la chronologie |
| `js/shipped.js` | **Généré** par `sync-assets.sh` depuis le `sounds.json` du mod : les pistes qu'un dresseur peut demander. Ne pas l'éditer à la main |
| `js/assets.js` | Ce qui vit sous `assets/` : les quatre types de fichiers, leur place dans l'archive, la référence qu'ils portent, et le `sounds.json` qui en sort. Les octets vont dans IndexedDB - une piste de trois mégaoctets ne tient pas dans le `localStorage` |
| `js/pack.js` | Le pack, son arborescence, les deux moitiés de l'archive et la relecture d'un pack existant |
| `js/app.js` | La mise en page et le câblage |
| `js/i18n.js` | Le reste de l'interface, en deux langues |

## Écrire une intro

L'aperçu d'une intro se manipule : on clique un calque pour le prendre, on le glisse pour le
placer, on tire un coin pour le redimensionner. Les flèches déplacent d'un pixel, `Maj` de dix,
`Suppr` retire le calque et `Ctrl+D` le duplique. Un calque déplacé écrit son `offset` ; un
calque redimensionné écrit ce que son type a - la `width` et la `height` d'un aplat ou d'une
image, la `height` d'une figure, la `size` d'un texte.

Deux vues, et le partage est toute l'idée :

| Vue | Ce qu'elle montre | Ce qu'on y fait |
| --- | --- | --- |
| **Mise en page** | Chaque calque là où il se pose, à pleine force, quel que soit le tick | On place, on redimensionne, on aimante |
| **Animation** | La vraie image, celle que le mod dessine à ce tick | On regarde |

On ne déplace pas un calque dans l'animation, et ce n'est pas un oubli : une entrée interpole
la position d'un calque, donc un pixel de souris n'y serait pas un pixel de décalage - il
serait un pixel multiplié par l'avancement de l'entrée.

**L'ancre se choisit sur un pavé de neuf**, et en changer ne déplace pas le calque : le
décalage est re-calculé sur le nouveau coin. Le trait pointillé tracé de l'ancre au calque dit
d'où part ce décalage. C'est ce à quoi le calque se tient quand la fenêtre change de forme, pas
un moyen de l'envoyer à l'autre bout de l'écran.

**Rien de tout ça ne fait descendre la page.** Les cartes de calque défilent dans leur propre
panneau, et l'aperçu comme la chronologie restent à côté : choisir un calque, en ouvrir la
carte ou en glisser un ne sort jamais le canvas de l'écran.

**La chronologie** donne une ligne par calque, de son tick d'entrée à la fin de son entrée :
glisser la barre change le `at`, son bord droit le `for`, et cliquer la règle amène l'aperçu à
ce tick - ce qui bascule en Animation, puisque demander à voir le tick 40 est demander à voir
l'animation.

## Ce qu'un aperçu ne peut pas montrer

Une `figure` et un calque `pokemon` sont des **modèles 3D** en jeu. Le navigateur n'en a aucun :
la figure est dessinée à plat depuis le skin, exactement comme le mod le fait lui-même quand
l'entité manque, et le Pokémon est une case marquée. Le placement, la couleur, le temps et
l'entrée, eux, sont les vrais.
