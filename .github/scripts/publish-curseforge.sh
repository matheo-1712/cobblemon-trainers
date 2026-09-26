#!/usr/bin/env bash
set -euo pipefail

: "${VERSION:?VERSION is required}"
: "${RELEASE_TYPE:?RELEASE_TYPE is required}"
loader="${LOADER:-fabric}"
case "$loader" in
  fabric|neoforge) ;;
  *) echo "::error::Unknown loader: $loader"; exit 1 ;;
esac
case "${DRY_RUN:-false}" in
  true) ;;
  false) : "${CURSEFORGE_TOKEN:?CURSEFORGE_TOKEN is required}" ;;
  *) echo '::error::DRY_RUN must be true or false'; exit 1 ;;
esac

project=$(sed -n 's/^curseforge_id=//p' gradle.properties | tr -d '\r')
game_version=$(sed -n 's/^minecraft_version=//p' gradle.properties | tr -d '\r')
[[ "$project" =~ ^[0-9]+$ ]] || { echo '::error::curseforge_id must be numeric'; exit 1; }
[[ -n "$game_version" ]] || { echo '::error::minecraft_version is missing'; exit 1; }

if [[ "$loader" == fabric ]]; then
  default_jar="fabric/build/libs/cobblemon-trainers-${VERSION}.jar"
else
  default_jar="neoforge/build/libs/cobblemon-trainers-neoforge-${VERSION}.jar"
fi
jar="${RELEASE_FILE:-$default_jar}"
[[ -f "$jar" ]] || { echo "::error::$jar was not built"; exit 1; }
python3 .github/scripts/verify-release.py "$jar" "$VERSION" "$loader"

case "$RELEASE_TYPE" in
  stable) curse_type=release ;;
  beta|alpha) curse_type=$RELEASE_TYPE ;;
  *) echo "::error::Unknown release type: $RELEASE_TYPE"; exit 1 ;;
esac

# The previous plugin upload failed on a numeric environment tag. Send only the Minecraft
# and Fabric names through the official API to avoid that rejected tag.
if [[ "$loader" == fabric ]]; then
  loader_name=Fabric
  relation_names=(fabric-api fabric-language-kotlin)
else
  loader_name=NeoForge
  relation_names=(kotlin-for-forge)
fi
relations=$(printf '%s\n' "${relation_names[@]}" | jq -R '{slug: ., type: "requiredDependency"}' | jq -s .)
metadata=$(jq -n \
  --arg changelog "${CHANGELOG:-}" \
  --arg version "$VERSION" \
  --arg game_version "$game_version" \
  --arg release_type "$curse_type" \
  --arg loader_name "$loader_name" \
  --argjson relations "$relations" \
  '{changelog: $changelog, changelogType: "markdown",
    displayName: ("Cobblemon Trainers " + $version + " (" + $loader_name + ")"),
    gameVersionNames: [$game_version, $loader_name], releaseType: $release_type,
    relations: {projects: ([{slug: "cobblemon", type: "requiredDependency"}] + $relations)}}')

if [[ "${DRY_RUN:-false}" == true ]]; then
  mkdir -p build/mod-publish
  printf '%s\n' "$metadata" > "build/mod-publish/curseforge-${loader}.json"
  echo "Dry run: $jar; metadata saved to build/mod-publish/curseforge-${loader}.json"
  exit 0
fi

response=$(mktemp)
trap 'rm -f "$response"' EXIT
code=$(curl --silent --show-error --output "$response" --write-out '%{http_code}' \
  -H "X-Api-Token: $CURSEFORGE_TOKEN" \
  --form-string "metadata=$metadata" \
  --form "file=@$jar" \
  "https://minecraft.curseforge.com/api/projects/$project/upload-file")

if [[ "$code" != 200 && "$code" != 201 ]] || ! jq -e '.id | numbers' "$response" >/dev/null; then
  echo "::error::CurseForge upload failed (HTTP $code)"
  cat "$response"
  exit 1
fi

echo "Uploaded CurseForge file $(jq -r '.id' "$response")"
