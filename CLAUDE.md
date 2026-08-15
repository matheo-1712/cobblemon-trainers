# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projet

Mod Fabric pour Minecraft 1.21.1 qui ajoute des dresseurs Pokémon configurables à
Cobblemon 1.7.3. Code principal en Kotlin (`matheo1712.cobbletrainers`), un stub Java
pour les mixins. Aucun code client-only : tout tourne côté serveur logique.

Le code, les commentaires et les logs sont en **anglais**. Tout texte affiché au joueur
passe par `assets/cobblemon-trainers/lang/` — jamais de littéral en dur.

## Commandes

```bash
./gradlew build          # compile + remap + produit build/libs/*.jar
./gradlew runClient      # client de dev (tâche fournie par Fabric Loom)
./gradlew runServer      # serveur de dev
./gradlew genSources     # décompile Minecraft/Cobblemon pour la navigation IDE
./gradlew clean build
```

Sur Windows, utiliser `.\gradlew.bat`.

Il n'existe pas de source set `src/test` — `build` ne lance donc aucun test.
Toute vérification passe par `runClient`/`runServer`, dont les mondes vivent dans `run/`
(gitignoré). **Ne pas mettre de jar Cobblemon dans `run/mods/`** : il est déjà fourni par
`modImplementation`, et le doublon fait planter le client au démarrage.

Le projet cible **Java 21**, imposé par un toolchain Gradle dans `build.gradle.kts`.
Cobblemon déclare `depends: java [21]`, une version exacte : sans le toolchain, `runClient`
hérite du JDK de Gradle et le loader refuse de démarrer. Le CI utilise le même JDK 21.

## Versions

Toutes les versions sont dans `gradle.properties`, jamais en dur dans `build.gradle.kts`.
Cobblemon et Architectury sont tirés du **Maven Modrinth** et référencés par **ID de
version Modrinth** (`cobblemon_version=kF7CvxTo`), pas par numéro sémantique : pour
changer de version, il faut récupérer le nouvel ID sur Modrinth.

Un ID Modrinth ne dit rien de la version de jeu ni du loader qu'il cible — une erreur ici
passe la résolution Gradle et casse le build plus loin. Symptôme rencontré : Loom échoue en
phase de configuration avec `Cannot remap access widener from namespace 'official'` parce
que l'ID pointait vers un build pour une autre version de Minecraft. Pour vérifier un ID :

```bash
curl -s "https://api.modrinth.com/v2/version/<ID>" | python -c "import sys,json;v=json.load(sys.stdin);print(v['version_number'],v['loaders'],v['game_versions'])"
```

## Architecture

Flux complet : JSON de datapack → `TrainerDefinition` → `NPCEntity` Cobblemon →
messages de combat.

- **`CobblemonTrainers`** — `ModInitializer`. Enregistre `/spawntrainer`, branche
  `TrainerReloadListener` sur le gestionnaire de ressources `SERVER_DATA` (ce qui couvre
  à la fois le chargement initial et `/reload`), et enregistre les listeners de combat
  dans un `try/catch` car ils dépendent de la présence de Cobblemon.
- **`TrainerRegistry`** — map en mémoire `ResourceLocation -> TrainerDefinition`, alimentée
  uniquement par les datapacks (`data/<namespace>/trainers/<nom>.json`, y compris ceux du
  mod). Il n'y a volontairement pas de couche de config sur disque. Comme dans les
  registres de Cobblemon, seul le nom de fichier compte : les sous-dossiers ne font pas
  partie de l'ID. Une erreur de parsing est loguée et le dresseur ignoré, sans faire
  échouer les autres.
- **`TrainerDefinition` / `TrainerSkin`** — data classes Gson. Tous les champs ont une
  valeur par défaut, donc un JSON partiel reste valide.
- **`ShowdownTeamParser`** — convertit du format Showdown en `PokemonProperties` en
  reconstruisant une chaîne de propriétés Cobblemon (`"pikachu level=88 ability=static …"`)
  passée à `PokemonProperties.parse`.
- **`TrainerSpawner`** — construit et fait apparaître le `NPCEntity`.
- **`TrainerBattleEventHandler`** — s'abonne à `CobblemonEvents.BATTLE_STARTED_POST` et
  `BATTLE_VICTORY` et envoie les messages configurés.

### Textes et traductions

Un seul logger, `CobblemonTrainers.LOGGER` (nommé d'après le mod id, donc les lignes
sortent en `(cobblemon-trainers)` dans le log Minecraft), avec des messages en anglais et
des paramètres SLF4J `{}` plutôt que de l'interpolation.

Côté joueur, `CobblemonTrainers.lang(key, vararg args)` construit un `Component.translatable`
préfixé par le mod id. Les clés vivent dans `assets/cobblemon-trainers/lang/en_us.json`
(référence) et `fr_fr.json`.

Les champs `name` et `battleEndWinMessage` & co. d'un dresseur sont eux aussi passés à
`Component.translatable`. C'est volontaire et sans risque : Minecraft affiche la clé telle
quelle quand aucune traduction n'existe, donc du texte brut dans le JSON continue de
s'afficher normalement, tandis qu'un pack peut fournir une vraie clé. `rerebleue.json` est
l'exemple traduit, les `example_*.json` restent en texte brut.

### Contraintes de l'API NPC de Cobblemon 1.7.3

Ces points ne se devinent pas depuis notre code seul et ont chacun causé un bug :

- **`NPCClass` est un singleton partagé.** `NPCClasses` est un `JsonDataRegistry` :
  `getByIdentifier` renvoie l'instance enregistrée. Écrire `npc.npc.party = …` remplace
  l'équipe pour **tous** les NPC de cette classe. L'équipe doit être posée sur
  `npc.party` (le `NPCPartyStore` de l'entité), et **après** `initialize()`, qui
  réassigne `party` depuis la classe.
- **`NPCBattleActor` n'hérite pas de `TrainerBattleActor`** — les deux dérivent
  séparément de `AIBattleActor`. Filtrer sur `TrainerBattleActor` ne matche jamais un
  NPC. `NPCBattleActor` expose directement `.npc`.
- **`canChallenge` et `healAfterwards` ne sont plus lus.** `healAfterwards` est
  `@Deprecated` ; `canChallenge` n'apparaît que dans du code commenté. Le combat est
  déclenché par l'`interaction` du NPC, et le soin par `autoHealParty` sur la classe.
  `NPCEntity.interaction` est surchargeable par entité, pas `autoHealParty`.
- **Clés de stats.** `PokemonProperties` dérive ses clés des constantes de l'enum
  `Stats` : `hp`, `attack`, `defence`, `special_attack`, `special_defence`, `speed`,
  suffixées `_ev` / `_iv`. Les abréviations Showdown (`atk`, `spa`, …) sont ignorées
  silencieusement.
- **`held_item` passe par le `ItemParser` vanilla**, qui préfixe `minecraft:` par défaut.
  Un objet Cobblemon sans namespace lève une `CommandSyntaxException` pendant la
  construction de l'équipe.
- **Le parseur de propriétés découpe sur les espaces.** Les valeurs qui peuvent en
  contenir (surnom, objet tenu) sont assignées directement sur l'objet `PokemonProperties`
  après `parse`, jamais via la chaîne.
- **Il n'existe pas de classe `cobblemon:humanoid`.** Cobblemon 1.7.3 ne livre que
  `ai_test`, `kitchen_sink`, `sacchi` et `standard`.
- **`resourceIdentifier` pilote le rendu.** Laissé vide, `NPCClasses.reload` le remplace
  par l'ID de la classe — donc `cobblemon-trainers:trainer`, pour lequel aucun asset
  n'existe, et le skin de joueur n'apparaît jamais. Les variations qui associent
  `model-default` / `model-slim` aux modèles Steve/Alex avec `"texture": "variable"` sont
  déclarées sous le nom `cobblemon:standard`
  (`assets/cobblemon/bedrock/npcs/variations/standard/50_standard_player.json`) : notre
  classe doit donc pointer explicitement `"resourceIdentifier": "cobblemon:standard"`.

### Le NPCClass du mod

`data/cobblemon-trainers/npcs/trainer.json` définit la classe utilisée par défaut. Son
interaction est `cobblemon-trainers:battle`, implémentée par [TrainerBattleInteraction] :
un clic droit lance un combat en simple et renvoie au joueur les erreurs de `BattleBuilder`.
Le MoLang `q.npc.start_battle` faisait la même chose mais avalait les erreurs, d'où un clic
droit totalement muet quand le joueur n'avait pas de Pokémon.

C'est dans ce JSON que se règlent le soin après combat (`autoHealParty`), le niveau d'IA
(`skill`) et le `resourceIdentifier`. Un dresseur peut viser une autre classe via son champ
`npcClass`.

### Liaison NPC → dresseur

L'ID du dresseur est stocké dans les **aspects** du NPC sous la forme
`trainer_id:<namespace>:<nom>` (`CobblemonTrainers.TRAINER_ASPECT_PREFIX`). Les aspects
appliqués sont sérialisés en NBT, donc le lien survit à un redémarrage.

### Skins

`applySkin` résout le profil et télécharge la texture Mojang sur un thread daemon, puis
repasse par `server.execute` pour écrire dans l'entité. Il faut poser les aspects
`model-default` / `model-slim` en même temps que `NPC_PLAYER_TEXTURE` : ce sont eux qui
déterminent le rig utilisé au rendu. Un échec est silencieux, le NPC garde son skin par
défaut. `skin.type` n'accepte que `player_username` ou `player_uuid`.

### Mixins

`ExampleMixin` est le stub du template Fabric, sans effet. Le mod ne repose sur aucun
mixin réel ; tout passe par les API publiques Fabric et Cobblemon.

## Limites connues

`ShowdownTeamParser` ne traduit pas les formes régionales notées à la Showdown
(`Raichu-Alola`) vers les aspects Cobblemon.
