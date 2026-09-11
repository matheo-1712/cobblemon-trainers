#!/usr/bin/env bash
# Fills web/assets/ from the mod itself.
#
# The editor needs three things the repository already holds: the intro textures a pack may
# name, the eight intros shipped with the mod, and the example trainers. None of them is copied
# into the repository - they are taken from their one source at publish time, so an intro that
# changes in the mod changes in the editor too, and nothing can drift.
#
# Run it before opening web/index.html locally; the Pages workflow runs the same script.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/.." && pwd)"
assets="$here/assets"

textures="$root/src/main/resources/assets/cobblemon-trainers/textures/gui/intro"
intros="$root/src/main/resources/data/cobblemon-trainers/cobblemontrainers/intro"
examples="$root/examples/cobblemonrlm/data/cobblemonrlm/cobblemontrainers/trainers"

rm -rf "$assets/intro" "$assets/intros" "$assets/examples"
mkdir -p "$assets/intro" "$assets/intros" "$assets/examples"

cp "$textures"/*.png "$assets/intro/"
cp "$intros"/*.json "$assets/intros/"
find "$examples" -name '*.json' ! -name 'category.json' -exec cp {} "$assets/examples/" \;

# The manifest is what the templates dropdown reads: a listing, since a static host has none.
{
  echo '{'
  echo '  "intros": ['
  first=1
  for file in "$assets/intros"/*.json; do
    name="$(basename "$file" .json)"
    [ $first -eq 1 ] || echo ','
    first=0
    printf '    { "id": "%s", "file": "assets/intros/%s.json" }' "$name" "$name"
  done
  echo ''
  echo '  ],'
  echo '  "trainers": ['
  first=1
  for file in "$assets/examples"/*.json; do
    name="$(basename "$file" .json)"
    [ $first -eq 1 ] || echo ','
    first=0
    printf '    { "id": "%s", "file": "assets/examples/%s.json" }' "$name" "$name"
  done
  echo ''
  echo '  ]'
  echo '}'
} > "$assets/manifest.json"

echo "web/assets: $(ls "$assets/intro" | wc -l) textures, $(ls "$assets/intros" | wc -l) intros, $(ls "$assets/examples" | wc -l) trainers"
