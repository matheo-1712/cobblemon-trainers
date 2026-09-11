# L'éditeur web

La page publiée sur <https://matheo-1712.github.io/cobblemon-trainers/> : on y écrit un
dresseur, une intro, une catégorie et un advancement, on y dépose les musiques, les skins et
les images, on y traduit les textes, et on télécharge le pack entier prêt à poser dans `mods/`.

Tout se passe dans le navigateur - aucun serveur, aucun compte, rien qui parte ailleurs. Le
pack en cours est gardé dans le `localStorage` et les fichiers déposés dans IndexedDB, et deux
services extérieurs sont appelés : `crafthead.net` pour l'image d'un skin de joueur (l'API
Mojang refuse le navigateur) et `cdnjs` pour JSZip.

## Ce que l'archive contient

| Chemin | D'où il vient |
| --- | --- |
| `pack.mcmeta` | Écrit, avec l'intervalle de `pack_format` des deux côtés |
| `data/<ns>/cobblemontrainers/…` | Les dresseurs, les intros, les `category.json` |
| `data/<ns>/advancement/…` | Les advancements |
| `assets/<ns>/sounds/…` · `textures/…` | Les fichiers déposés dans **Ressources** |
| `assets/<ns>/sounds.json` | **Écrit** à partir des sons du pack - `stream` vrai pour une musique |
| `assets/<ns>/lang/<code>.json` | L'onglet **Traductions** |
| `fabric.mod.json` | Seulement en `.jar`, pour déclarer la dépendance au mod |

Une ressource ne se nomme jamais à la main : son emplacement dans l'archive et la référence
qu'un dresseur écrit (`mon_pack:battle_music.finale`) sortent du même endroit, donc ils ne
peuvent pas se contredire. Le bouton **Clés de traduction** fait le reste : il remplace les
phrases d'un dresseur par des clés et range les phrases dans la première langue.

## Y travailler

```bash
bash web/sync-assets.sh          # remplit web/assets/ depuis le mod
python -m http.server 8765 -d web
```

`sync-assets.sh` **copie depuis le dépôt** le logo et l'icône du mod, les textures d'intro, les
huit intros livrées et les dresseurs d'exemple. Rien de tout ça n'est commité : c'est le workflow `pages.yml` qui rejoue le
script à la publication, donc une intro qui change dans le mod change dans l'éditeur, et
l'éditeur ne peut pas décrire une version du mod qui n'existe plus.

Ouvrir `index.html` directement marche aussi, sauf le menu des modèles : un `fetch` sur
`file://` est refusé par le navigateur.

## Les fichiers

| Fichier | Rôle |
| --- | --- |
| `js/schema.js` | **Tous les champs de tous les formats**, labels FR et EN compris. Ajouter un champ au mod, c'est ajouter une entrée ici et nulle part ailleurs |
| `js/form.js` | Le moteur de formulaire : un schéma entre, du DOM sort, et l'objet édité **est** le fichier |
| `js/validate.js` | Les pièges du mod dits à voix haute - un champ que Gson ignore en silence, un `dynamax` qui s'écrit `max` |
| `js/preview.js` | `BattleIntroScreen.kt` transcrit : mêmes courbes, mêmes entrées, même 640 × 360 |
| `js/assets.js` | Ce qui vit sous `assets/` : les quatre types de fichiers, leur place dans l'archive, la référence qu'ils portent, et le `sounds.json` qui en sort. Les octets vont dans IndexedDB - une piste de trois mégaoctets ne tient pas dans le `localStorage` |
| `js/pack.js` | Le pack, son arborescence, les deux moitiés de l'archive et la relecture d'un pack existant |
| `js/app.js` | La mise en page et le câblage |
| `js/i18n.js` | Le reste de l'interface, en deux langues |

## Ce qu'un aperçu ne peut pas montrer

Une `figure` et un calque `pokemon` sont des **modèles 3D** en jeu. Le navigateur n'en a aucun :
la figure est dessinée à plat depuis le skin, exactement comme le mod le fait lui-même quand
l'entité manque, et le Pokémon est une case marquée. Le placement, la couleur, le temps et
l'entrée, eux, sont les vrais.
