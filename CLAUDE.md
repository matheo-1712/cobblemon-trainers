# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projet

Mod Fabric pour Minecraft 1.21.1 qui ajoute des dresseurs Pokémon configurables à
Cobblemon 1.7.3. Code principal en Kotlin (`matheo1712.cobbletrainers`), les mixins en Java.
Tout tourne côté serveur logique, à une exception près : `client.MinecraftMixin`, qui branche
la lecture des `assets/` d'un pack posé dans `mods/` (voir « Livrer un pack »). Aucun
entrypoint client, aucun rendu.

Le code, les commentaires et les logs sont en **anglais**. Tout texte affiché au joueur
passe par `assets/cobblemon-trainers/lang/` — jamais de littéral en dur.

La doc utilisateur est en français : `README.md` (installation, commandes) et
`docs/DATAPACK.md` (guide complet de création de datapack). Ce qui touche au format des
dresseurs — nouveau champ, nouvelle règle de parsing — se répercute dans `docs/DATAPACK.md`,
qui est la référence ; le README n'en garde qu'un résumé.

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
messages, musique et récompenses de combat.

- **`CobblemonTrainers`** — `ModInitializer`. Enregistre `/spawntrainer` et `/listtrainers`,
  branche `TrainerReloadListener` sur le gestionnaire de ressources `SERVER_DATA` (ce qui
  couvre à la fois le chargement initial et `/reload`), et enregistre les listeners de combat
  dans un `try/catch` car ils dépendent de la présence de Cobblemon.
- **`TrainerRegistry`** — map en mémoire `ResourceLocation -> TrainerDefinition`, alimentée
  uniquement par les datapacks (`data/<namespace>/cobblemontrainers/<nom>.json`, y compris
  ceux du mod). Le dossier est à un seul niveau sous le namespace, comme les `species/` et
  `npcs/` de Cobblemon, mais nommé d'après le mod plutôt que `trainers/` : un `trainers/`
  générique entrerait en collision avec un autre mod qui lirait le même dossier. Il n'y a
  volontairement pas de couche de config sur disque. Comme dans les
  registres de Cobblemon, seul le nom de fichier compte : les sous-dossiers ne font pas
  partie de l'ID. Une erreur de parsing est loguée et le dresseur ignoré, sans faire
  échouer les autres.
- **`TrainerDefinition` / `TrainerSkin`** — data classes Gson. Tous les champs ont une
  valeur par défaut, donc un JSON partiel reste valide. Attention à ne pas confondre
  `level` (niveau des Pokémon) et `skill` (difficulté de l'IA, 0-5, passée à
  `StrongBattleAI`).
- **`ShowdownTeamParser`** — convertit du format Showdown en `PokemonProperties` en
  reconstruisant une chaîne de propriétés Cobblemon (`"pikachu level=88 ability=static …"`)
  passée à `PokemonProperties.parse`.
- **`TrainerSpawner`** — construit et fait apparaître le `NPCEntity`.
- **`TrainerBattleEventHandler`** — s'abonne à `CobblemonEvents.BATTLE_STARTED_POST` pour
  le message de début et la musique, et à `BATTLE_VICTORY` pour le message de fin, les
  récompenses et l'enregistrement de la victoire. Il surveille aussi la mort et la
  suppression des entités, pour arrêter le combat d'un dresseur qui quitte le monde (voir
  plus bas).
- **`TrainerProgress`** — `SavedData` du monde : qui a battu quel dresseur. Voir « Revanches
  et récompenses ».
- **`TrainerRewards`** — remet les objets au vainqueur.
- **`ListTrainersCommand`** — `/listtrainers [joueur]`, la lecture de `TrainerProgress`
  côté joueur.
- **`TrainerBattleMusic`** — envoie `ClientboundSoundPacket` / `ClientboundStopSoundPacket`
  aux joueurs du combat.

### Musique de combat

Le son voyage dans un `Holder.direct(SoundEvent.createVariableRangeEvent(id))` : rien n'est
enregistré dans `BuiltInRegistries.SOUND_EVENT`, donc un datapack peut nommer n'importe
quelle piste sans que le mod la connaisse. C'est le client qui résout le nom, il faut donc
un **resource pack** pour une piste maison — le jar du mod en est un, ce qui fait marcher
`TrainerBattleMusic.DEFAULT_TRACK` sans rien configurer.

Rien n'est mémorisé côté serveur : `stop` se contente de couper la piste que nomme la
définition. Conséquence assumée : une piste modifiée par `/reload` en plein combat continue
jusqu'à la fin du combat.

Points à ne pas redécouvrir :

- **`sounds.json` n'a pas de champ `category`.** La catégorie est choisie à l'envoi, ici
  `SoundSource.MUSIC` (curseur *Musique* du joueur).
- **L'exclusivité passe par un `ClientboundStopSoundPacket(null, MUSIC)`** envoyé juste avant
  la piste — un nom nul veut dire « toute la catégorie », d'où l'ordre strict des deux
  paquets. Ça règle aussi la suite : le `MusicManager` du client voit sa piste disparaître et
  retire un délai avant la prochaine, une bonne dizaine de minutes pour la musique de
  surface. C'est le plus près de l'exclusif qu'un serveur puisse faire — lancer la musique
  d'ambiance est une décision du client.
- **`"stream": true` est obligatoire** sur un morceau long, sinon Minecraft charge tout le
  fichier en mémoire.
- **Un `.ogg` stéréo est joué sans atténuation**, position ignorée — exactement ce qu'on veut
  pour une musique. La position envoyée (celle du joueur) n'est qu'un repli pour une piste
  mono.
- **Pas de boucle.** `isLooping` est une décision de `SoundInstance`, côté client, et le mod
  ne touche pas au moteur audio : un combat plus long que la piste finit en silence.

### Revanches et récompenses

`canRebattle` et `rewardOnce` ont besoin de savoir qui a déjà battu quoi — un état de monde,
pas de datapack. Il vit donc dans un `SavedData` (`TrainerProgress`, fichier
`cobblemon_trainers_progress.dat` de l'overworld) et non dans `TrainerRegistry`, que `/reload`
vide entièrement.

Points à ne pas redécouvrir :

- **La clé est l'ID du dresseur, pas l'UUID de l'entité.** Battre un exemplaire de
  `mon_pack:champion` les bat tous, et tuer le PNJ ne remet rien à zéro. D'où
  `TrainerRegistry.idFromAspects`, qui rend l'ID sans exiger qu'une définition le porte
  encore : un dresseur retiré des datapacks garde son identité, et son entrée survit à un
  pack désactivé le temps d'une session.
- **Rien n'est oublié quand une entité disparaît**, contrairement à l'arrêt des combats. La
  taille est bornée par le nombre de dresseurs × joueurs.
- **`tracked` masque, il n'empêche pas d'enregistrer.** Un dresseur masqué garde son entrée,
  sans quoi `canRebattle` et `rewardOnce` cesseraient de marcher pour lui. Tout ce qui
  présente la progression à un joueur passe par `TrainerRegistry.tracked()` plutôt que
  `all()` — c'est le point d'entrée que réutilisera l'item de suivi prévu. Les dresseurs de
  démonstration du mod portent `"tracked": false` : ils sont chargés dans tous les mondes.
- **`SavedData.Factory` n'accepte pas un `DataFixTypes` nul** : `DimensionDataStorage`
  le déréférence dès que le fichier existe. `DataFixTypes.LEVEL` fait l'affaire — le
  DataFixerUpper rend l'entrée telle quelle quand la version lue vaut la version courante,
  donc c'est un passe-plat.
- **Le refus de revanche est dans `TrainerBattleInteraction`, avant `BattleBuilder`**, pour
  que rien ne démarre : ni équipe soignée, ni musique.
- **Les vainqueurs viennent de l'événement, pas de `battle.players`** : dans un combat à
  plusieurs joueurs, seul le camp gagnant est récompensé. `PlayerBattleActor.entity` est nul
  pour un joueur déconnecté ; on ne lui donne rien et on n'enregistre rien, donc le dresseur
  l'attend toujours.
- **`Inventory.placeItemBackInInventory` fait tout le travail** de remise : il découpe en
  piles et jette au sol ce qui ne rentre pas. Mais il **vide l'`ItemStack` qu'on lui passe**,
  en le découpant jusqu'à zéro : lire son nom ou sa quantité après coup donne « Air » et 0.
  Le message de récompense est donc construit avant l'appel.

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

**Une seule voie de traduction, côté client.** Un datapack qui veut traduire ses dresseurs
livre un resource pack à côté (`assets/<namespace>/lang/<code>.json`). Une résolution côté
serveur, lisant les lang depuis le datapack, a existé puis été retirée volontairement : elle
faisait deux chemins concurrents pour le même résultat, et ne savait de toute façon pas
traduire le nom flottant par joueur.

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
  suffixées `_ev` / `_iv`. Les abréviations Showdown (`atk`, `spa`, …) ne veulent rien dire
  pour Cobblemon et seraient ignorées silencieusement, d'où la table `STAT_NAMES` de
  `ShowdownTeamParser` qui les traduit avant de construire la chaîne de propriétés.
- **`held_item` passe par le `ItemParser` vanilla**, qui préfixe `minecraft:` par défaut.
  Un objet Cobblemon sans namespace lève une `CommandSyntaxException` pendant la
  construction de l'équipe.
- **Le parseur de propriétés découpe sur les espaces.** Les valeurs qui peuvent en
  contenir (surnom, objet tenu) sont assignées directement sur l'objet `PokemonProperties`
  après `parse`, jamais via la chaîne.
- **`PokemonProperties.copy()` perd le surnom**, donc `SimplePartyProvider` aussi. `copy()`
  est un aller-retour `saveToJSON` → `loadFromJSON`, et ce couple ne connaît pas `nickname`
  (contrairement à `saveToNBT`/`loadFromNBT` et à `asString`) : le champ repart nul, sans un
  mot. `provide()` commence par ce `copy()`, d'où un surnom Showdown correctement parsé mais
  jamais visible en combat. `TrainerSpawner.applyTeam` remplit donc lui-même le
  `NPCPartyStore` — le reste de `provide()` n'est que le niveau par défaut et la création du
  store.
- **Il n'existe pas de classe `cobblemon:humanoid`.** Cobblemon 1.7.3 ne livre que
  `ai_test`, `kitchen_sink`, `sacchi` et `standard`.
- **`resourceIdentifier` pilote le rendu.** Laissé vide, `NPCClasses.reload` le remplace
  par l'ID de la classe — donc `cobblemon-trainers:trainer`, pour lequel aucun asset
  n'existe, et le skin de joueur n'apparaît jamais. Les variations qui associent
  `model-default` / `model-slim` aux modèles Steve/Alex avec `"texture": "variable"` sont
  déclarées sous le nom `cobblemon:standard`
  (`assets/cobblemon/bedrock/npcs/variations/standard/50_standard_player.json`) : notre
  classe doit donc pointer explicitement `"resourceIdentifier": "cobblemon:standard"`.

### Les NPCClass du mod

`data/cobblemon-trainers/npcs/` contient les classes NPC du mod, un détail
d'implémentation : les datapacks n'en déclarent jamais et `TrainerDefinition` n'a plus de
champ `npcClass`. Ces fichiers restent hors de `cobblemontrainers/` contrairement aux dresseurs :
le dossier est imposé par le `resourcePath` de `NPCClasses`, le registre de Cobblemon. Leur interaction est `cobblemon-trainers:battle`, implémentée par
`TrainerBattleInteraction` : un clic droit lance le combat et renvoie au joueur les erreurs
de `BattleBuilder`. Le MoLang `q.npc.start_battle` faisait la même chose mais avalait les
erreurs, d'où un clic droit totalement muet quand le joueur n'avait pas de Pokémon.

Une seule instance d'interaction est partagée par tous les dresseurs, donc le format de
combat n'y est pas figé : `interact()` retrouve la définition via
`TrainerRegistry.findByAspects(npc.aspects)` et lit son `battleFormat`. Le `skill` est lui
posé par entité (`NPCEntity.skill`, qui prime sur celui de la classe).

`autoHealParty` est le seul réglage qui ne peut pas être posé par entité : Cobblemon le lit
sur la classe, à deux endroits (`NPCBattleActor` avant le combat, `PokemonBattle` après).
Le mod livre donc **deux classes**, `trainer.json` et `trainer_no_heal.json`, identiques à ce
booléen près, et `TrainerSpawner` choisit selon le champ `autoHealParty` du dresseur. Si tu
modifies l'une, modifie l'autre.

Reste propre à ces JSON : `resourceIdentifier`.

### Liaison NPC → dresseur

L'ID du dresseur est stocké dans les **aspects** du NPC sous la forme
`trainer_id:<namespace>:<nom>` (`CobblemonTrainers.TRAINER_ASPECT_PREFIX`). Les aspects
appliqués sont sérialisés en NBT, donc le lien survit à un redémarrage.

### Skins

`applySkin` résout la texture sur un thread daemon, puis repasse par `server.execute` pour
écrire dans l'entité. Il faut poser les aspects `model-default` / `model-slim` en même temps
que `NPC_PLAYER_TEXTURE` : ce sont eux qui déterminent le rig utilisé au rendu. Un échec est
silencieux, le NPC garde son skin par défaut.

`skin.type` accepte `player_username`, `player_uuid` (profil Mojang, téléchargement réseau)
et `texture` (une image de pack, lue par `TrainerTextures`).

Points à ne pas redécouvrir sur `texture` :

- **`NPC_PLAYER_TEXTURE` transporte les octets du PNG**, pas un chemin : le client n'a rien à
  résoudre, donc un pack posé sur le serveur seul habille quand même les dresseurs de tout le
  monde. C'est la raison d'être de ce type de skin, et ce qui le rend indépendant du réseau
  Mojang.
- **L'image doit être lisible côté serveur**, ce qui exclut les gestionnaires de ressources :
  celui du serveur ne connaît que `data/`, celui du client n'existe pas sur un serveur dédié.
  `TrainerTextures` lit donc le classpath (assets du mod et de tout ce que Fabric a chargé),
  puis les packs de `mods/` via `ModsFolderPackSource.readResource`. Un pack rangé dans
  `resourcepacks/` ou `datapacks/` est hors de portée, et c'est irréductible.
- **Le gabarit vient du JSON** (`skin.model`, `default` ou `slim`) : contrairement à un profil
  Mojang, une image seule ne dit pas sur quel rig la dessiner.
- Le mod livre `assets/cobblemon-trainers/textures/trainers/example.png` pour que la voie soit
  testable sans fabriquer un skin.

### Fin de combat

Cobblemon n'expose **aucun** événement « combat terminé » : `BATTLE_VICTORY` et
`BATTLE_FLED` ne couvrent que deux sorties sur quatre, et `/stopbattle` comme un abandon
n'en déclenchent aucune. Le seul point de passage commun est
`PokemonBattle.end()` → `BattleRegistry.closeBattle` → `battle.onEndHandlers`. C'est donc
là qu'est coupée la musique, via un handler ajouté à `BATTLE_STARTED_POST`. Ne pas revenir
à un abonnement par type de fin : c'est ce qui laissait la musique tourner après un
`/stopbattle`.

Un dresseur qui meurt ou disparaît pendant un combat laisserait le joueur enfermé face à un
acteur sans entité. `ServerLivingEntityEvents.AFTER_DEATH` et
`ServerEntityEvents.ENTITY_UNLOAD` (filtré sur `removalReason.shouldDestroy()`, pour qu'un
déchargement de chunk ne compte pas) appellent `battle.stop()` sur les `npc.battleIds`. Les
deux se déclenchent pour un même dresseur tué, c'est voulu : le combat déjà fermé n'est plus
dans le registre, donc l'opération est idempotente. Le filtre `TrainerRegistry.findByAspects`
évite de toucher aux NPC qui ne viennent pas du mod.

### Livrer un pack

Trois voies, toutes prises en charge et **laissées au choix de l'auteur du pack** — ne pas en
privilégier une dans la doc, sauf à dire que `mods/` est la seule qui charge les deux moitiés.

| Voie | Emplacement | Formats | Charge | Fichier requis |
| --- | --- | --- | --- | --- |
| Dossier des mods | `mods/` | dossier, `.zip`, `.jar` | `data/` et `assets/` | `pack.mcmeta` |
| Datapack | `<monde>/datapacks/` | dossier, `.zip`, `.jar` | `data/` | `pack.mcmeta` |
| Resource pack | `resourcepacks/` | dossier, `.zip`, `.jar` | `assets/` | `pack.mcmeta` |

`ModsFolderPackSource` est ce qui rend la première ligne vraie **sans `fabric.mod.json`** :
une `RepositorySource` qui balaie le dossier des mods et expose ce qu'elle y trouve sous les
deux `PackType`. Sans elle, Fabric ne regarde que les jars portant un `fabric.mod.json` et
saute les autres en silence (`ModDiscoverer$ModScanTask.computeJarFile` renvoie `null`, rien
n'est logué) — c'était la cause d'un pack « qui ne se charge pas » sans le moindre message.

Elle est branchée par deux mixins, parce qu'il n'existe aucune API pour ajouter une source
après construction :

- `PackRepositoryMixin` (commun) — `@Inject` en fin de `<init>`, réécrit le champ `sources`.
  Un `ServerPacksSource` dans la liste identifie un dépôt de données ; couvrir le constructeur
  plutôt que ses appelants attrape d'un coup le serveur dédié, le serveur intégré et l'écran
  de création de monde.
- `client.MinecraftMixin` (client) — `@ModifyArg` sur le tableau varargs du constructeur de
  `Minecraft`. Déclaré dans le tableau `client` du mixins.json, pas dans `mixins` : il cible
  une classe client et ferait échouer le chargement sur un serveur dédié. Le dépôt client est
  identifié par construction, ce qui évite de nommer `ClientPackSource` dans un mixin commun.

Points à ne pas redécouvrir :

- **`PackSelectionConfig.required` doit être `true` côté ressources, `false` côté données.**
  `Minecraft.reloadResourcePacks` passe par `PackRepository.reload()`, dont `rebuildSelected`
  ne réinsère que les packs *required* : un pack seulement « ajoutable automatiquement » y
  serait découvert puis retiré de la sélection au premier F3+T. Côté données au contraire,
  `MinecraftServer.configurePackRepository` lit `PackSource.shouldAddAutomatically`, ce qui
  active le pack tout en laissant `/datapack disable` utilisable.
- **Les archives portant un `fabric.mod.json` sont ignorées** par cette source : Fabric les
  charge lui-même et les expose déjà sous les deux types (`ModResourcePackCreator` de
  `fabric-resource-loader-v0`). Les ramasser ici les enregistrerait deux fois.
- **Un pack sans contenu pour le type demandé est écarté** (`getNamespaces(packType)` vide),
  pour qu'un pack de données seules n'apparaisse pas dans l'écran des resource packs.
- Le dossier des mods vient de `-Dfabric.modsFolder` sinon de `gameDir/mods`, et le
  `DirectoryValidator` de `allowed_symlinks.txt`, comme vanilla.

**Le format d'archive n'élargit pas ce qu'un *emplacement* charge.** Le dossier `datapacks/`
d'un monde est un `FolderRepositorySource` en `PackType.SERVER_DATA` uniquement : un `assets/`
posé là n'est jamais lu, `.jar` ou pas. `PackDetectorMixin` fait accepter le `.jar` comme
conteneur, il ne change pas le `PackType` de la source.

Ne pas essayer de brancher le dossier d'un monde sur le gestionnaire de ressources client :
ça a été écrit une fois puis retiré. C'était faisable (une `RepositorySource` alimentée par
`ServerLifecycleEvents`), mais ça ne marche qu'en solo — sur un serveur le client n'a pas le
fichier — donc un pack se comporterait différemment en solo et en multi. `mods/` n'a pas ce
défaut : chaque côté lit son propre dossier.

`pack_format` diffère par type en 1.21.1 : 48 côté données, 34 côté ressources. Une archive
qui sert des deux côtés déclare `supported_formats` en intervalle, sinon l'écran des resource
packs l'affiche comme incompatible. C'est ce que fait `examples/cobblemonrlm/pack.mcmeta`.

### Mixins

`ExampleMixin` est le stub du template Fabric, sans effet.

`PackDetectorMixin` est le seul mixin réel. Il fait accepter les archives `.jar` à
`PackDetector.detectPackResources`, unique endroit où Minecraft filtre sur `.zip` — tout le
reste de la chaîne marche déjà, `FilePackResources` ouvrant le fichier avec `ZipFile`, qui se
moque de l'extension. `PackDetector` étant partagé par toutes les sources sur dossier, ça
vaut pour `datapacks/` comme pour `resourcepacks/`, ce qui est le but : un auteur qui a
construit un `.jar` pour `mods/` peut le déposer tel quel aux deux autres emplacements sans
réempaqueter en `.zip`.

Loom remappe les mixins statiquement (pas de refmap dans le jar) : après un changement,
vérifier dans `build/libs/*.jar` que la cible est bien passée en intermediary
(`detectPackResources` → `method_52441`, `PackDetector` → `class_8621`).

### Formes et aspects

Une forme Cobblemon n'est pas une espèce : c'est la même espèce portant d'autres **aspects**,
et `Pokemon.updateForm()` choisit la forme via `Species.getForm(aspects)`. La ligne
`Aspects:` du format d'équipe les déclare, et `ShowdownTeamParser` les réinjecte dans la
chaîne de propriétés (`rlm=true`, ou `appliance=wash` tel quel pour une caractéristique à
choix) — la même syntaxe que `/pokespawn`, donc testable en jeu.

Points à ne pas redécouvrir :

- **Passer par les propriétés est la seule voie qui tient.** `PokemonProperties.form` est
  écrasé : `commonApply` pose la forme puis appelle `updateAspects()`, qui la recalcule
  depuis les aspects. Et `PokemonProperties.aspects` n'est jamais appliqué, il ne sert qu'à
  `matches`. Seules les caractéristiques d'espèce produisent des aspects durables.
- **L'aspect d'une forme n'est pas toujours le nom de sa propriété.** Rotom-Lavage a
  l'aspect `wash-appliance`, produit par la caractéristique à choix `appliance` réglée sur
  `wash` : impossible de dériver la propriété depuis l'aspect sans lire l'`aspectFormat` du
  fournisseur. D'où le passe-plat pour les valeurs contenant `=`.
- **Un aspect inconnu est validé ici, pas par Cobblemon.** `PokemonProperties.parse` jette
  sans un mot une clé qu'aucune caractéristique ne déclare. La liste vient de
  `CustomPokemonProperty.properties`, que `SpeciesFeatures.reload` alimente avec chaque
  fournisseur qui est un `CustomPokemonPropertyType` — donc peuplée par les datapacks, et
  seulement consultable une fois le serveur chargé (l'équipe est construite à l'apparition
  du dresseur, c'est bon).

## Publication

`.github/workflows/release.yml` se déclenche à la **publication d'une release GitHub** :
il construit, publie sur Modrinth via `./gradlew publishMods`
(`me.modmuss50.mod-publish-plugin`), puis attache les assets à la release.

**Modrinth est la seule plateforme.** CurseForge a été tenté puis retiré : son API d'upload
répondait invariablement `Invalid game version ID: 11779 belongs to an invalid dependency`.
Le plugin envoie quatre catégories d'IDs (version Minecraft, modloader, environnement
client/server, version Java), toutes résolues depuis la liste de CurseForge elle-même — l'ID
existe donc, c'est son type que le projet refuse. Retirer `javaVersions` n'a rien changé, ce
qui laisse le tag d'environnement, que le plugin impose (`client` ou `server`, au moins un).
Ne pas réessayer sans avoir d'abord identifié 11779 via `GET /api/game/versions` avec un jeton
CurseForge.

Pour couper une release : publier une release GitHub dont le tag est `v<version>`. Rien à
bumper avant. Le corps de la release devient le changelog partout, et la case *pre-release*
choisit entre `stable` et `beta` (`alpha` si le tag contient `alpha`).

**Le tag fait foi pour la version.** Le workflow le passe en `-Pversion`, ce qui alimente
`project.version`, donc le nom du jar, le `${version}` que `processResources` injecte dans
`fabric.mod.json`, et le numéro de version publié. Le `version=` de
`gradle.properties` n'est plus qu'un défaut pour les builds locaux : il n'a pas besoin de
suivre, et rien ne le vérifie. Le seul garde-fou est que le tag doit ressembler à un numéro
(`1.2.3`, avec ou sans `v`), sinon le jar s'appellerait d'après une étiquette du genre
`latest`.

Points à ne pas redécouvrir :

- **Le workflow exige `MODRINTH_TOKEN` avant de construire.** Sans jeton, `publishMods`
  bascule en `dryRun` et le workflow finirait vert sans rien publier.
- **Un numéro de version déjà publié sur Modrinth est refusé (409).** Une release qui échoue
  après l'upload Modrinth ne se rejoue pas sur le même tag : reprendre sur le suivant, ou
  supprimer la version depuis le tableau de bord.
- **Les dépendances Modrinth sont épinglées à la version exacte du build**, lue depuis
  `gradle.properties` (`cobblemon_version`, `fabric_api_version`, `fabric_kotlin_version`) :
  changer une version de build déplace la contrainte publiée avec elle. Modrinth accepte un ID
  de version ou un numéro, et refuse la publication si le motif ne correspond pas à exactement
  une version — une faute de frappe échoue bruyamment.
- **La release GitHub existe déjà** quand le workflow tourne. Le publisher `github` du plugin
  ne sait que *créer* une release, jamais alimenter une release existante (sauf via l'option
  `parent`, réservée aux sous-projets) : les assets passent donc par `gh release upload`.
- **`file` pointe sur `remapJar`, pas `jar`.** Le second garde les mappings nommés et planterait
  hors environnement de développement. `RemapJarTask` dérive de `org.gradle.jvm.tasks.Jar`, pas
  de `org.gradle.api.tasks.bundling.Jar` — `tasks.named<Jar>(…)` échoue avec l'import par défaut
  du Kotlin DSL.
- **Dans le bloc `publishMods`, `version` est la propriété de l'extension**, pas celle du projet :
  l'interpoler donne sa description Gradle. D'où le `modVersion` capturé au-dessus.
- Sans jeton, `publishMods` bascule en `dryRun` et écrit dans `build/mod-publish/` — c'est ce qui
  rend `./gradlew publishMods` sûr en local.

### Le pack d'exemple dans la release

`exampleDatapack` zippe `examples/cobblemonrlm` en `exemple_trainer_datapack.zip`, publié à côté
du jar. Il reste **hors du jar** : tout ce qui est sous `data/` dans le jar est un datapack que le
jeu charge pour tout le monde, donc l'embarquer ferait apparaître les dresseurs `cobblemonrlm:`
dans des mondes qui n'ont rien demandé.

Le zip **exclut `fabric.mod.json`**. Ce fichier n'existe que pour permettre de construire le
dossier en `.jar` que Fabric charge, et il nuit à un `.zip` : Fabric ignore les archives qui ne
sont pas des `.jar`, tandis que `ModsFolderPackSource` saute tout ce qui porte des métadonnées de
mod — le pack ne se chargerait donc de nulle part. Sans lui, le même zip marche dans `mods/`,
`datapacks/` et `resourcepacks/`.

## Limites connues

`ShowdownTeamParser` ne traduit pas le suffixe de forme des exports Showdown
(`Raichu-Alola`, `Rotom-Wash`) : rien ne le distingue d'une espèce dont le nom contient un
tiret (`Ho-Oh`, `Porygon-Z`, `Jangmo-o`) sans interroger le registre des espèces, et le
suffixe ne donne de toute façon pas la propriété d'une caractéristique à choix. Les formes
passent par `Aspects:`.
