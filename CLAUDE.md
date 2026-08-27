# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projet

Mod Fabric pour Minecraft 1.21.1 qui ajoute des dresseurs Pokémon configurables à
Cobblemon 1.7.3. Code principal en Kotlin (`matheo1712.cobbletrainers`), les mixins en Java.
L'essentiel du travail se fait côté serveur logique - les dresseurs viennent de datapacks et
combattent là-bas - mais **le mod a un côté client, requis, et c'est un endroit légitime pour
ce qui appartient au client** : un écran, un dessin, une instance sonore, un réglage que seul
le client connaît. Ne pas contorsionner le serveur pour éviter d'y toucher : c'est comme ça
qu'on a fini par envoyer une piste qui ne pouvait pas boucler et que le `MusicManager` ne
voyait pas (voir « Musique de combat »). La question à poser est « qui décide ? », pas « peut-on
rester côté serveur ? ».

Ce qui y vit aujourd'hui : `client.MinecraftMixin`, qui branche la lecture des `assets/` d'un
pack posé dans `mods/` (voir « Livrer un pack ») ; `client.ClientLevelMixin`, qui rend le bloc
de dresseur visible quand on tient son item ; l'entrypoint `CobblemonTrainersClient`, qui
ouvre les deux écrans du mod et reçoit ce qui les alimente ; ces deux écrans eux-mêmes, sous
`client.gui` (voir « Le bloc de dresseur » et « Le Battle Phone ») ;
`client.ClientPokemonSelection`, qui annonce au serveur le Pokémon sélectionné parce que
Cobblemon ne le lui dit jamais (voir « Le Pokémon qui ouvre le combat ») ; et le couple
`client.ClientBattleMusic` / `client.MusicManagerMixin`, qui joue le thème de combat en boucle
et garde la musique du monde silencieuse pendant ce temps (voir « Musique de combat »).
Aucun renderer d'entité n'est enregistré : le Battle Phone dessine les skins à plat depuis
l'image, et pour les Pokémon d'une équipe il appelle le `drawProfilePokemon` de Cobblemon -
seul endroit du mod qui touche à du rendu 3D.

Le code, les commentaires et les logs sont en **anglais**. Tout texte affiché au joueur
passe par `assets/cobblemon-trainers/lang/` - jamais de littéral en dur.

La doc utilisateur est en français : `README.md` (installation, commandes),
`docs/DATAPACK.md` (guide complet de création de datapack), `docs/DIFFICULTE.md` (ce que fait
chaque niveau de `battle.difficulty`) et `docs/SPAWNING.md` (le bloc `location` et l'appel
depuis le Battle Phone). Ce qui touche au format des dresseurs - nouveau champ,
nouvelle règle de parsing - se répercute dans `docs/DATAPACK.md`, qui est la référence ; le
README n'en garde qu'un résumé. **Toute règle de l'IA va dans `docs/DIFFICULTE.md`**, **tout
ce qui touche aux gimmicks de combat dans `docs/GIMMICKS.md`**, et **tout
ce qui touche à l'appel d'un dresseur dans `docs/SPAWNING.md`**, jamais
dans `docs/DATAPACK.md`, qui n'en garde qu'un renvoi : une règle décrite à deux endroits est une
règle qui finit fausse à l'un des deux. `docs/DATAPACK.md` est tenu **aussi
concis que possible** : une idée par phrase, un tableau plutôt qu'un paragraphe, et rien qui
soit déjà dit ailleurs dans le fichier. Ajouter un champ, c'est ajouter une ligne de tableau,
pas une section.

`docs/en/` est la **traduction anglaise** de ces quatre pages plus leur index
(`DATAPACK.md`, `SPAWNING.md`, `DIFFICULTY.md` et `GIMMICKS.md` - aux noms anglais -,
`README.md`), destinée aux
joueurs qui arrivent par Modrinth. Le français reste la version de référence : **une règle se
change d'abord dans `docs/`, puis dans `docs/en/` dans le même commit**. Deux pages qui
divergent sont pires qu'une page absente, la seconde ayant l'air à jour. `MODRINTH.md`, à la
racine, est la présentation publiée sur la boutique et n'a pas d'équivalent français - c'est
une page de vente, pas une page du wiki.

## Commandes

```bash
./gradlew build          # compile + remap + produit build/libs/*.jar
./gradlew runClient      # client de dev (tâche fournie par Fabric Loom)
./gradlew runServer      # serveur de dev
./gradlew genSources     # décompile Minecraft/Cobblemon pour la navigation IDE
./gradlew clean build
```

Sur Windows, utiliser `.\gradlew.bat`.

Il n'existe pas de source set `src/test` - `build` ne lance donc aucun test.
Toute vérification passe par `runClient`/`runServer`, dont les mondes vivent dans `run/`
(gitignoré). **Ne pas mettre de jar Cobblemon dans `run/mods/`** : il est déjà fourni par
`modImplementation`, et le doublon fait planter le client au démarrage. Mega Showdown et ses
dépendances, elles, y sont bien - c'est `copyDevMods` qui les y dépose avant chaque `runClient`,
et c'est voulu (voir « Les gimmicks de combat »).

Le projet cible **Java 21**, imposé par un toolchain Gradle dans `build.gradle.kts`.
Cobblemon déclare `depends: java [21]`, une version exacte : sans le toolchain, `runClient`
hérite du JDK de Gradle et le loader refuse de démarrer. Le CI utilise le même JDK 21.

## Versions

Toutes les versions sont dans `gradle.properties`, jamais en dur dans `build.gradle.kts`.
Cobblemon et Architectury sont tirés du **Maven Modrinth** et référencés par **ID de
version Modrinth** (`cobblemon_version=kF7CvxTo`), pas par numéro sémantique : pour
changer de version, il faut récupérer le nouvel ID sur Modrinth.

Mega Showdown, `accessories` et `owo` sont là aussi, dans la configuration `devMods` : rien ne
compile contre eux, ils ne servent qu'au jeu de dev (voir « Les gimmicks de combat »). Ils ne
sont pas non plus déclarés en dépendance Modrinth à la publication - le mod tourne sans.

Un ID Modrinth ne dit rien de la version de jeu ni du loader qu'il cible - une erreur ici
passe la résolution Gradle et casse le build plus loin. Symptôme rencontré : Loom échoue en
phase de configuration avec `Cannot remap access widener from namespace 'official'` parce
que l'ID pointait vers un build pour une autre version de Minecraft. Pour vérifier un ID :

```bash
curl -s "https://api.modrinth.com/v2/version/<ID>" | python -c "import sys,json;v=json.load(sys.stdin);print(v['version_number'],v['loaders'],v['game_versions'])"
```

## Architecture

Flux complet : JSON de datapack → `TrainerDefinition` → `NPCEntity` Cobblemon →
messages, musique et récompenses de combat.

- **`CobblemonTrainers`** - `ModInitializer`. Enregistre `/cobblemontrainers` et ses verbes,
  branche `TrainerReloadListener` sur le gestionnaire de ressources `SERVER_DATA` (ce qui
  couvre à la fois le chargement initial et `/reload`), et enregistre les listeners de combat
  dans un `try/catch` car ils dépendent de la présence de Cobblemon.
- **`TrainerRegistry`** - deux maps en mémoire, `ResourceLocation -> TrainerDefinition` et
  `-> TrainerCategory`, alimentées uniquement par les datapacks
  (`data/<namespace>/cobblemontrainers/<chemin>.json`, y compris ceux du mod). Le dossier est
  à un seul niveau sous le namespace, comme les `species/` et `npcs/` de Cobblemon, mais nommé
  d'après le mod plutôt que `trainers/` : un `trainers/` générique entrerait en collision avec
  un autre mod qui lirait le même dossier. Il n'y a volontairement pas de couche de config sur
  disque. **Contrairement aux registres de Cobblemon, le chemin complet fait l'ID** :
  `champions/erika.json` donne `<ns>:champions/erika`, et ce dossier est la catégorie du
  dresseur (voir « Catégories »). Une erreur de parsing est loguée et le fichier ignoré, sans
  faire échouer les autres.
- **`TrainerDefinition` / `TrainerSkin` / `TrainerRequirements`** - data classes Gson,
  regroupées en blocs (`battle`, `messages`, `progress`, `requires`). Tous les champs ont une
  valeur par défaut, donc un JSON partiel reste valide - et c'est ce qui fait que Gson passe
  par le constructeur sans argument que Kotlin génère, sans quoi les blocs absents arriveraient
  nuls. Attention à ne pas confondre `battle.level` (niveau des Pokémon) et `battle.difficulty`
  (difficulté de l'IA, 0-5, passée à `StrongBattleAI`).
- **`TrainerCategory`** - le `category.json` d'un dossier : nom affiché et ordre, les deux
  facultatifs.
- **`TrainerLock`** - évalue le `requires` d'un dresseur pour un joueur et rend la liste de ce
  qui manque. Voir « Conditions de combat ».
- **`TrainerPlace`** - évalue le `location` d'un dresseur et sait le formuler. Voir « Appeler un
  dresseur ».
- **`TrainerCalls`** - l'appel depuis le Battle Phone : les vérifications, la place où poser le
  dresseur, et sa fin de vie. Même section.
- **`TrainerGaze`** - donne à chaque dresseur chargé un `LookAtPlayerGoal`, pour qu'il tourne la
  tête vers un joueur qui s'approche. Voir « Le regard des dresseurs ».
- **`battle.TrainerLead`** - qui ouvre le combat, des deux côtés, et la mémoire par joueur de
  ce que son client a annoncé. Voir « Le Pokémon qui ouvre le combat ».
- **`dialogue.TrainerDialogue`** - tout ce qu'un dresseur dit, monté en `Dialogue` Cobblemon.
  Voir « Les dialogues ».
- **`advancement.TrainerDefeatedTrigger`** - le critère `cobblemon-trainers:trainer_defeated`.
- **`ShowdownTeamParser`** - convertit du format Showdown en `PokemonProperties` en
  reconstruisant une chaîne de propriétés Cobblemon (`"pikachu level=88 ability=static …"`)
  passée à `PokemonProperties.parse`. Une entrée du tableau `team` est un Pokémon ; elle est
  quand même redécoupée sur les lignes vides, ce qui rend un export Showdown entier collé dans
  une seule entrée lisible sans code en plus.
- **`TrainerSpawner`** - construit et fait apparaître le `NPCEntity`.
- **`TrainerBattleEventHandler`** - s'abonne à `CobblemonEvents.BATTLE_STARTED_POST` pour la
  musique, et à `BATTLE_VICTORY` pour le mot de la fin, les
  récompenses et l'enregistrement de la victoire. Il surveille aussi la mort et la
  suppression des entités, pour arrêter le combat d'un dresseur qui quitte le monde (voir
  plus bas).
- **`battle.TrainerBattleRange`** - la distance au-delà de laquelle un combat de dresseur
  s'arrête, celle d'un combat contre un Pokémon sauvage. Même section.
- **`battle.ai.TrainerBattleAI`** - la couche qui refuse les décisions intenables du
  `StrongBattleAI` de Cobblemon, épaulée par `BattleTypeChart` (efficacité des types, talents
  compris), `BattleDamage` (dégâts en points de vie), `BattleGuards` (ce qui encaisse un coup
  qu'on croyait décisif), `BattleSpeed` (qui frappe en premier), `BattleScreens` (les écrans et
  ce qui les rend utiles) et `TrainerAiDebug` (l'interrupteur de débogage). Voir « L'IA de
  combat ».
- **`battle.ai.TrainerGimmicks`** - le vocabulaire de `battle.gimmicks` et ce que le combat
  offre ce tour-ci. Voir « Les gimmicks de combat ».
- **`TrainerProgress`** - `SavedData` du monde : qui a battu quel dresseur. Voir « Revanches
  et récompenses ».
- **`TrainerRewards`** - remet les objets au vainqueur.
- **`ListTrainersCommand`** - `/cobblemontrainers list [joueur]`, la lecture de `TrainerProgress`
  côté joueur, groupée par catégorie.
- **`TrainerCommands`** - la racine `/cobblemontrainers` et le seul `requires(hasPermission(2))`
  du mod. Les trois verbes ne s'enregistrent plus eux-mêmes : chacun expose un `node()` que la
  racine assemble. Un verbe qu'on voudrait ouvrir à tout le monde devrait donc sortir de cette
  racine plutôt que d'y poser son propre contrôle.
- **`DefeatTrainerCommand`** - `/cobblemontrainers defeat <id|all> [joueurs] [reset]`,
  l'écriture : une victoire inscrite sans combat, pour tester une progression sans la jouer. Elle enregistre et
  déclenche `TrainerDefeatedTrigger`, rien de plus - ni récompenses, ni message de fin de
  combat, parce qu'un outil de test doit pouvoir tourner cent fois. Le trigger part même quand
  la victoire était déjà là : c'est ce qui rattrape un advancement ajouté après coup.
- **`DebugAiCommand`** - `/cobblemontrainers debugai`, l'interrupteur qui montre en chat ce que
  la couche d'IA a refusé et pourquoi. Voir « L'IA de combat ». `all`
  prend tous les dresseurs chargés, pas seulement les `listed` : c'est un outil de test, pas
  une vue de joueur. Le mot-clé est un littéral Brigadier, donc il éclipse un dresseur qui
  s'appellerait `all` - les littéraux sont essayés avant les arguments.
- **`TrainerBattleMusic`** - nomme la piste de combat aux joueurs du combat ; c'est leur client
  qui la joue et la fait boucler.
- **`block.TrainerBlocks` / `TrainerSpawnerBlock` / `TrainerSpawnerBlockEntity` /
  `TrainerSpawnerItem`** - le bloc qui maintient un dresseur en place. Voir « Le bloc de
  dresseur ».
- **`network.TrainerSpawnerNetworking`** - les deux `CustomPacketPayload` de l'écran de ce
  bloc, et la validation côté serveur de ce qui revient.
- **`item.TrainerItems` / `BattlePhoneItem`** - l'objet qui ouvre le suivi de progression.
  Voir « Le Battle Phone ».
- **`network.BattlePhoneNetworking`** - les `CustomPacketPayload` de cet écran : le
  listing, la demande de skin et la réponse, celles de l'équipe, et l'appel d'un dresseur.
- **`network.BattleLeadNetworking`** - le Pokémon sélectionné, du client vers le serveur.
- **`network.BattleMusicNetworking`** - l'autre paquet qui n'appartient pas à un écran : le
  thème de combat à jouer, ou son arrêt. Voir « Musique de combat ».
- **`trainers.TrainerSkins`** - la résolution d'un `TrainerSkin` en image, hors thread serveur
  et avec cache. Voir « Skins ».
- **`client.CobblemonTrainersClient`** - l'unique entrypoint client.
- **`client.ClientPokemonSelection`** - la sélection de l'overlay, poussée vers le serveur.
- **`client.ClientBattleMusic`** - le thème de combat, joué et tenu côté client, que lit aussi
  `client.MusicManagerMixin`.
- **`client.gui.TrainerSpawnerScreen` / `client.gui.BattlePhoneScreen`** - les deux écrans.
- **`client.gui.TrainerSkinRenderer` / `client.cache.TrainerSkinCache`** - le dessin d'un skin
  à plat, et les textures que le Battle Phone a reçues.
- **`client.cache.TrainerTeamCache`** - les équipes que le Battle Phone a reçues, prêtes à
  dessiner.

### Musique de combat

Le serveur ne fait que **nommer** la piste : rien ne passe par
`BuiltInRegistries.SOUND_EVENT`, donc un datapack peut nommer n'importe quelle piste sans que
le mod la connaisse. C'est le client qui résout le nom, et qui la joue, il faut donc un
**resource pack** pour une piste maison - le jar du mod en est un, ce qui fait marcher
`TrainerBattleMusic.DEFAULT_TRACK` sans rien configurer.

Rien n'est mémorisé côté serveur : `stop` dit au client de lâcher le thème qu'il tient.
Conséquence assumée : une piste modifiée par `/reload` en plein combat continue jusqu'à la fin
du combat.

Points à ne pas redécouvrir :

- **Le `ClientboundSoundPacket` a été abandonné, et pour deux raisons qui sont toutes deux des
  décisions du client.** Il ne sait pas boucler - `isLooping` appartient à l'instance que le
  client construit, et celle d'un paquet de son joue une fois - et il n'apprend rien au
  `MusicManager`, qui continue son compte à rebours vers la prochaine musique d'ambiance. Ce
  compteur n'était jamais repoussé par notre coupure de catégorie : `MusicManager.tick` ne fait
  que le rabaisser (`Math.min`), et la branche qui le remet à dix ou vingt minutes ne tourne que
  si une piste à lui jouait. Un combat qui démarrait quelques secondes avant l'échéance recevait
  donc la musique de surface par-dessus - le bug #33.
- **La boucle est gratuite et sans blanc.** `SoundEngine.play` passe `isLooping()` à
  `SoundBufferLibrary.getStream(path, looping)`, qui rend un `LoopingAudioStream` : le `.ogg`
  est rouvert à la fin du flux, dans le pipeline audio. Rien à ticker côté mod. Pour un son
  **non** streamé c'est `Channel.setLooping` qui s'en charge - les deux cas sont couverts, mais
  nos pistes sont toutes en `"stream": true`.
- **Le constructeur complet de `SimpleSoundInstance` est public**, et prend un
  `ResourceLocation` plutôt qu'un `SoundEvent` : c'est ce qui garde les pistes inconnues du
  registre jouables. C'est celui dont vanilla se sert dans `forMusic`, avec `relative` et
  `Attenuation.NONE` - l'instance est posée sur l'auditeur, donc la position du joueur ne compte
  plus (un `.ogg` mono n'est plus spatialisé).
- **Le silence du monde est tenu par `client.MusicManagerMixin`**, qui annule `tick` tant que
  `ClientBattleMusic` tient un thème. Annuler le tick **gèle** le compteur au lieu de le laisser
  s'écouler dans le vide : le monde reprend où il en était. C'est exactement ce que fait
  Cobblemon pour sa propre musique de combat, et les deux mixins cohabitent - il suffit que l'un
  des deux tienne le tick. Comme la piste boucle, le garde tient tout le combat.
- **Le garde répond sur la référence, pas sur le moteur audio.** `ClientBattleMusic.isPlaying`
  regarde si un thème est chargé, pas `SoundManager.isActive` : un son streamé n'atteint son
  canal qu'un instant après `play`, et la question posée est « un combat est-il en cours ».
- **Le nettoyage est fait côté client, dans l'ordre**, par `ClientBattleMusic.play` :
  `musicManager.stopPlaying()` d'abord - pour que le manager lâche aussi sa référence - puis
  `soundManager.stop(null, MUSIC)` pour le reste. Le faire depuis le serveur avec un
  `ClientboundStopSoundPacket` emporterait notre propre piste selon l'ordre d'arrivée.
- **`stopPlaying()` est rappelé à l'arrêt** pour son effet de bord : il ajoute 100 ticks au
  compte à rebours, de quoi ne pas voir le monde attaquer un morceau à la seconde où le combat
  finit.
- **Un client sans le mod n'entend rien** (`ServerPlayNetworking.canSend`), et c'est la réponse
  supportée : le mod est requis des deux côtés, comme pour les deux écrans.
- **Ne pas passer par le `BattleMusicPacket` de Cobblemon.** Il existe, avec tout ce qu'il faut
  autour - `BattleMusicController`, une instance qui boucle et se fond, la mise en pause des
  catégories *Ambient*, *Music* et *Records* - mais **Cobblemon ne l'envoie nulle part** : il
  n'est référencé que par sa propre table d'enregistrement réseau. Branché dessus, le mod n'a
  plus eu de musique du tout. Du code mort qui compile n'est pas du code qui marche.
- **`sounds.json` n'a pas de champ `category`.** La catégorie est choisie à la construction de
  l'instance, ici `SoundSource.MUSIC` (curseur *Musique* du joueur).
- **`VOLUME` est volontairement bien en dessous de 1.** Une piste de combat tourne plusieurs
  minutes par dessus tout le reste ; à 1.0 elle couvre le combat qu'elle accompagne. Le curseur
  *Musique* du joueur s'applique par dessus. `PITCH`, lui, reste à 1.0 : c'est la vitesse de
  lecture, borné à `[0.5, 2.0]` par `SoundEngine.calculatePitch`, et il transposerait la piste
  entière - il n'a rien à voir avec le niveau sonore. Les deux voyagent dans le paquet plutôt
  que d'être écrits côté client : le réglage reste à côté de la piste par défaut.
- **`"stream": true` est obligatoire** sur un morceau long, sinon Minecraft charge tout le
  fichier en mémoire - et une piste qui boucle le reste tout le combat.

### Revanches et récompenses

`progress.rematch` et le `firstWinOnly` des récompenses ont besoin de savoir qui a déjà battu
quoi - un état
de monde, pas de datapack. Il vit donc dans un `SavedData` (`TrainerProgress`, fichier
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
- **La fréquence d'une récompense est portée par la récompense, pas par le dresseur.**
  `TrainerReward.firstWinOnly` a remplacé un `progress.rewards` qui valait pour toute la liste
  d'un coup et ne savait donc pas dire « un trophée une fois, du consommable à chaque fois ».
  Ce champ a été retiré sans transition, le mod étant encore en bêta ; un pack qui l'écrirait
  encore le verrait ignoré en silence, comme n'importe quelle clé inconnue de Gson. Ne pas le
  rebrancher : un régime décidé à deux endroits finirait par se contredire.
- **`TrainerRewards.isDue` est la règle partagée** par `grant` et `preview`. C'est ce qui fait
  qu'une fiche ne promet pas indéfiniment un trophée déjà réclamé - d'où le `firstWin` que le
  listing calcule depuis `TrainerProgress`.
- **Une récompense réclamée est marquée, pas retirée.** `preview` la renvoie avec `due` à faux
  plutôt que de la laisser tomber : la retirer était honnête sur ce que paie le prochain combat
  et ne disait rien au joueur, la ligne disparaissant de la fiche à l'instant où elle était
  gagnée. Le `once` qui l'accompagne vient du pack et ne bouge jamais - c'est ce qu'un joueur
  veut savoir *avant* le combat, et les deux répondent donc à deux questions différentes.
- **`progress.listed` masque, il n'empêche pas d'enregistrer.** Un dresseur masqué garde son
  entrée, sans quoi `rematch` et `rewards` cesseraient de marcher pour lui. Tout ce qui
  présente la progression à un joueur passe par `TrainerRegistry.listed()` plutôt que `all()`.
  Cette liste est aussi triée dans l'ordre d'affichage - pack, ordre de catégorie, ID - et
  mise en cache au reload : le Battle Phone la parcourt pour chaque ligne envoyée, et une
  condition `victories` la reparcourt pour chaque dresseur évalué.
- **`SavedData.Factory` n'accepte pas un `DataFixTypes` nul** : `DimensionDataStorage`
  le déréférence dès que le fichier existe. `DataFixTypes.LEVEL` fait l'affaire - le
  DataFixerUpper rend l'entrée telle quelle quand la version lue vaut la version courante,
  donc c'est un passe-plat.
- **Le refus de revanche est dans `TrainerBattleInteraction.refusal`, avant `BattleBuilder`**,
  pour que rien ne démarre : ni équipe soignée, ni musique. Le refus pour condition non remplie
  est au même endroit et pour la même raison, et la boîte de dialogue le montre à la place du
  `greeting` - voir « Les dialogues ».
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

Le `name` d'un dresseur et ses quatre `messages` sont eux aussi passés à
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
- **`NPCBattleActor` n'hérite pas de `TrainerBattleActor`** - les deux dérivent
  séparément de `AIBattleActor`. Filtrer sur `TrainerBattleActor` ne matche jamais un
  NPC. `NPCBattleActor` expose directement `.npc`.
- **`canChallenge` et `healAfterwards` ne sont plus lus.** `healAfterwards` est
  `@Deprecated` ; `canChallenge` n'apparaît que dans du code commenté. Le combat est
  déclenché par l'`interaction` du NPC, et le soin par `autoHealParty` sur la classe.
  `NPCEntity.interaction` est surchargeable par entité, pas `autoHealParty`.
- **`adjustLevel` doit être accompagné de `cloneParties`.** `BattleBuilder.pvp1v1` et `pvp2v2`
  forcent le clonage dès que le format ajuste les niveaux ; `pvn`, le nôtre, transmet
  `cloneParties` tel quel. Sans clonage, le niveau 50 et le soin sont appliqués aux vrais
  Pokémon du joueur, puis `PokemonStore.set` réattache leurs `storeCoordinates` à un
  `PlayerPartyStore` jetable que Cobblemon abandonne aussitôt - l'équipe continue de les
  lister, eux se croient ailleurs, et le premier dépôt au PC en fait deux. C'est le bug de
  duplication de l'issue #22. `TrainerBattleInteraction` pose donc `cloneParties` lui-même.
  L'expérience et les EV ne changent pas : Cobblemon les attribue sur
  `BattlePokemon.originalPokemon`, qui reste le vrai Pokémon.
- **`pvn` ne regarde pas la santé de l'équipe du joueur.** Il filtre bien sur les PV du côté du
  dresseur, mais du côté joueur il ne compte que la taille de l'équipe : un joueur K.O. entrait
  donc en combat avec une équipe où personne ne peut être envoyé (issue #25).
  `TrainerBattleInteraction` refuse avant `pvn`, **sans exception** - un format qui ajuste les
  niveaux soigne pourtant les copies avec lesquelles il combat, mais laisser passer une équipe
  K.O. là revenait à offrir une équipe pleine à qui n'en a plus. L'exemption a été écrite puis
  retirée, ne pas la remettre. Seule l'équipe vide est laissée à Cobblemon, qui la refuse aussi
  et dont l'erreur le dit mieux.
- **Un nom Cobblemon ne garde que `[a-z0-9]`.** Espèces, capacités, talents et natures sont
  nommés d'après leur nom affiché débarrassé de tout le reste, accents compris : `uturn`,
  `willowisp`, `kingsshield`, `hooh`, `porygonz`, `mrmime`, `farfetchd`, `flabebe`. D'où
  `normalizeName`, qui décompose en NFD, retire les marques et supprime tout ce qui n'est ni
  lettre ni chiffre. Retirer les seuls espaces laissait `u-turn`, que rien ne résout et que
  Cobblemon écarte sans un mot - le parseur vérifie donc aussi chaque capacité avec
  `Moves.getByName`. Les aspects sont l'exception (`normalizeAspect`, qui se contente de
  passer en minuscules) : ce sont déjà des identifiants, et `appliance=wash` perdrait sa
  paire.
- **Clés de stats.** `PokemonProperties` dérive ses clés des constantes de l'enum
  `Stats` : `hp`, `attack`, `defence`, `special_attack`, `special_defence`, `speed`,
  suffixées `_ev` / `_iv`. Les abréviations Showdown (`atk`, `spa`, …) ne veulent rien dire
  pour Cobblemon et seraient ignorées silencieusement, d'où la table `STAT_NAMES` de
  `ShowdownTeamParser` qui les traduit avant de construire la chaîne de propriétés.
- **`held_item` passe par le `ItemParser` vanilla**, qui préfixe `minecraft:` par défaut.
  Un objet Cobblemon sans namespace lève une `CommandSyntaxException` pendant la
  construction de l'équipe. Un nom d'objet Showdown est écrit pour un humain
  (`Heavy-Duty Boots`, `King's Rock`, `Exp. Share`, `Poké Ball`) là où un ID Cobblemon ne
  contient que `[a-z0-9_]` : `normalizeItem` ne se contente donc pas de traduire les
  espaces, il retire les accents, élide `'` et `.`, et rend tout le reste en `_`. La même
  `CommandSyntaxException` remontait jusqu'au tick qui demandait l'apparition, d'où deux
  filets : le parseur vérifie l'ID dans `BuiltInRegistries.ITEM` et retire l'objet inconnu
  en le loguant, et `TrainerSpawner.applyTeam` entoure `create()` - un Pokémon fautif coûte
  sa place dans l'équipe, pas le dresseur.
- **Le parseur de propriétés découpe sur les espaces.** Les valeurs qui peuvent en
  contenir (surnom, objet tenu) sont assignées directement sur l'objet `PokemonProperties`
  après `parse`, jamais via la chaîne.
- **`PokemonProperties.copy()` perd le surnom**, donc `SimplePartyProvider` aussi. `copy()`
  est un aller-retour `saveToJSON` → `loadFromJSON`, et ce couple ne connaît pas `nickname`
  (contrairement à `saveToNBT`/`loadFromNBT` et à `asString`) : le champ repart nul, sans un
  mot. `provide()` commence par ce `copy()`, d'où un surnom Showdown correctement parsé mais
  jamais visible en combat. `TrainerSpawner.applyTeam` remplit donc lui-même le
  `NPCPartyStore` - le reste de `provide()` n'est que le niveau par défaut et la création du
  store.
- **Il n'existe pas de classe `cobblemon:humanoid`.** Cobblemon 1.7.3 ne livre que
  `ai_test`, `kitchen_sink`, `sacchi` et `standard`.
- **`resourceIdentifier` pilote le rendu.** Laissé vide, `NPCClasses.reload` le remplace
  par l'ID de la classe - donc `cobblemon-trainers:trainer`, pour lequel aucun asset
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
`TrainerBattleInteraction` : un clic droit ouvre la boîte de dialogue du dresseur (voir « Les
dialogues »), et le combat qui s'ensuit renvoie au joueur les erreurs de `BattleBuilder`. Le
MoLang `q.npc.start_battle` faisait la même chose mais avalait les erreurs, d'où un clic droit
totalement muet quand le joueur n'avait pas de Pokémon.

Une seule instance d'interaction est partagée par tous les dresseurs, donc le format de
combat n'y est pas figé : `interact()` retrouve la définition via
`TrainerRegistry.findByAspects(npc.aspects)` et lit son `battle.format`. Le `skill` est lui
posé par entité (`NPCEntity.skill`, qui prime sur celui de la classe).

`autoHealParty` est le seul réglage qui ne peut pas être posé par entité : Cobblemon le lit
sur la classe, à deux endroits (`NPCBattleActor` avant le combat, `PokemonBattle` après).
Le mod livre donc **deux classes**, `trainer.json` et `trainer_no_heal.json`, identiques à ce
booléen près, et `TrainerSpawner` choisit selon le `battle.healParty` du dresseur. Si tu
modifies l'une, modifie l'autre.

Reste propre à ces JSON : `resourceIdentifier`.

### Liaison NPC → dresseur

L'ID du dresseur est stocké dans les **aspects** du NPC sous la forme
`trainer_id:<namespace>:<nom>` (`CobblemonTrainers.TRAINER_ASPECT_PREFIX`). Les aspects
appliqués sont sérialisés en NBT, donc le lien survit à un redémarrage.

Un dresseur posé par un bloc en porte un second, `trainer_spawner:<BlockPos.asLong()>`
(`SPAWNER_ASPECT_PREFIX`), pour la même raison : c'est ce qui permet à un bloc de reconnaître
ses propres restes. `TrainerSpawner.spawn` prend un `extraAspects` pour ça.

### Le bloc de dresseur

`cobblemon-trainers:trainer_spawner` retient un ID de dresseur et remet ce dresseur en place
chaque fois qu'il manque à l'appel. C'est la seule partie du mod qui a du code client.

**La visibilité façon barrière n'est pas du rendu.** Aucune classe client de vanilla ne
mentionne `BarrierBlock` : `getRenderShape()` renvoie `INVISIBLE` et le modèle de bloc n'a
qu'une texture `particle`. Ce qu'on voit est une **particule** `BLOCK_MARKER`, semée par
`ClientLevel.doAnimateTick` sur les positions au hasard dont le bloc est celui que renvoie
`ClientLevel.getMarkerParticleTarget()` - laquelle lit `MARKER_PARTICLE_ITEMS`, un
`Set.of(Items.BARRIER, Items.LIGHT)` privé et immuable. La seule entrée est donc cette
méthode, d'où `client.ClientLevelMixin` : un `@Inject(HEAD, cancellable)` qui répond notre
bloc quand la main principale tient son item, et laisse la réponse de vanilla intacte sinon.
Le modèle du bloc doit exister et ne déclarer qu'une `particle`, sinon la particule dessine la
texture manquante.

Points à ne pas redécouvrir :

- **Le bloc étend `Block` + `EntityBlock`, pas `BaseEntityBlock`.** `BaseEntityBlock` donnerait
  `INVISIBLE` gratuitement, mais il ré-abstrait `codec()`, dont les fabriques
  (`simpleCodec`, `propertiesCodec`) sont `protected static` sur `BlockBehaviour` et donc hors
  de portée d'une sous-classe Kotlin. `Block.codec()` est concret : le problème disparaît.
- **Pas de collision, contrairement à la barrière.** Le dresseur apparaît *dans* le bloc ; un
  bloc solide le repousserait. Le reste des propriétés copie la barrière : incassable hors
  créatif, `noLootTable`, immunisé aux pistons.
- **Une seule ligne du build fait du bruit** : `BlockEntityType.Builder.build(null)` avertit en
  Kotlin, le paramètre Java n'étant pas annoté. `FabricBlockEntityTypeBuilder`, qui évitait ça,
  est déprécié en faveur de ce même builder. L'avertissement est le moindre mal.
- **L'écran n'est pas un `MenuType`** : il n'y a pas d'inventaire. Deux `CustomPacketPayload`
  suffisent, l'un qui porte les réglages du bloc et la liste des dresseurs chargés, l'autre qui
  ramène ce que le joueur a tapé. Rien n'est mémorisé entre les deux : le bloc est retrouvé
  depuis la position du paquet de retour, et tout est revalidé là - permission, distance, ID.
  Un client peut renvoyer autre chose que ce qu'on lui a proposé.
- **La liste des dresseurs vient du serveur.** Les dresseurs sont des données de datapack : le
  client n'a pas de `TrainerRegistry` peuplé, même en solo il ne faut pas s'y fier.
- **Le retour se fait par téléport, la réapparition par spawn.** Un dresseur vivant qui s'est
  éloigné est ramené (il garde son identité et son UUID) ; un dresseur mort ou disparu est
  recréé après le délai. Un dresseur en plein combat (`battleIds` non vide) est laissé
  tranquille.
- **Le retour rend la vie de l'entité, pas celle de l'équipe.** `npc.health = npc.maxHealth`,
  parce qu'un dresseur recréé serait arrivé intact. L'équipe, elle, n'est pas touchée : le
  report des dégâts d'un combat au suivant est ce que règle `battle.healParty`, et un
  `PartyStore.heal()` ici l'écraserait en douce. Ça a été écrit puis retiré, ne pas le
  remettre.
- **`respawnAt` ne vide pas `spawnedTrainer`.** Un chunk qui vient de se charger peut ne pas
  encore avoir ses entités : le tick suivant retrouve le dresseur par UUID et annule la
  réapparition programmée. Vider l'UUID tout de suite ferait un doublon à chaque rechargement.
  `respawnAt` n'est pas sauvegardé - c'est un temps de jeu absolu, sans valeur dans un autre
  monde - et le transitoire `seenAlive` distingue le dresseur qu'on a vu mourir (délai complet)
  de celui qui n'est pas encore là (`RELOAD_GRACE_TICKS`).
- **Une structure emporte la configuration toute seule.** `StructureTemplate.fillFromWorld`
  appelle `saveWithId` et `placeInWorld` rappelle `loadWithComponents` : le NBT du block entity
  fait le voyage sans qu'on ait rien à écrire. Le piège est l'inverse - l'état d'exécution part
  avec, et une copie hérite de l'UUID du dresseur de l'original. D'où la règle : `spawnedTrainer`
  n'est cru que si l'entité porte le `spawnerAspect()` de *cette* position, sinon la copie
  adopterait le dresseur du bloc source et le téléporterait chez elle.
- **L'orientation est une propriété de blockstate, pas un champ du block entity.** C'est la
  seule raison d'être de `FACING` : `StructureTemplate` fait passer les états par `rotate` et
  `mirror` du bloc, alors qu'un float rangé dans le NBT du block entity serait recopié tel
  quel - une structure posée d'un quart de tour aurait ses dresseurs regardant de travers.
  Le prix est de descendre d'un angle libre à quatre directions, ce qui ne se voit pas.
  Le block entity lit `blockState.getValue(FACING).toYRot()` : `LevelChunk.setBlockState`
  appelle `blockEntity.setBlockState` quand seul l'état change, donc la valeur suit.
- **Le balayage par aspect est le filet de sécurité** : avant chaque spawn et à la casse du
  bloc, tout NPC portant `trainer_spawner:<pos>` dans la zone est supprimé. Il attrape le
  dresseur parti dans un chunk déchargé pendant que le bloc, lui, continuait de tourner.
- **Rien du bloc n'existe pour un non-opérateur**, et ça passe par quatre verrous distincts,
  tous accrochés à `Player.canUseGameMasterBlocks()` (créatif *et* niveau 2) : le bloc
  implémente `GameMasterBlock`, ce qui fait refuser la casse par `ServerPlayerGameMode`
  ; l'item est un `GameMasterBlockItem`, dont `getPlacementState` renvoie null ; `getShape`
  renvoie une forme vide quand le `EntityCollisionContext` porte un joueur sans droits, donc le
  rayon du curseur traverse le bloc au lieu d'afficher une boîte de sélection dans le vide ; et
  le mixin des particules refuse de répondre. L'écran, lui, revalide côté serveur.
  Ne pas retirer l'un en croyant les autres redondants : ils couvrent chacun un chemin
  différent (casser, poser, viser, voir).
- **L'onglet créatif du mod, lui, n'est pas verrouillé, et c'est délibéré.** On peut faire
  disparaître un onglet en ne remplissant son `displayItems` que si
  `parameters.hasPermissions()` : un `CATEGORY` vide n'est pas affiché
  (`CreativeModeTab.shouldDisplay()`). Ça a été essayé et retiré. Ce drapeau est le seul signal
  de permission qu'un onglet reçoit, et l'écran créatif le calcule comme
  `canUseGameMasterBlocks() && option operatorItemsTab` - or cette option vaut **false** par
  défaut, donc l'onglet restait invisible pour l'opérateur à qui il est destiné. Ne pas le
  remettre sans avoir résolu ça.

### Le Battle Phone

`cobblemon-trainers:battle_phone` ouvre le suivi de progression : les dresseurs `listed`, un
onglet par datapack, un intertitre par catégorie, et le skin de chacun. C'est le pendant
joueur de `/cobblemontrainers list`, qui reste opérateur - l'objet ne donne aucun pouvoir, il lit.

**Le craft du Battle Phone** est `data/cobblemon-trainers/recipe/battle_phone.json` : quatre
lingots de fer aux coins, quatre de cuivre sur les côtés, une noigrume bleue au centre. Trois
choses à savoir avant d'y toucher :

- **Le dossier est `recipe/`, au singulier**, comme `advancement/` depuis 1.21.
- **Les ingrédients sont des items, pas des tags.** `c:ingots/iron` marcherait - Cobblemon
  l'emploie - mais un tag vide échoue *en silence* : la recette disparaît sans un mot dans le
  log. Deux lingots vanilla ne valent pas ce risque.
- **La noigrume vient de Cobblemon** (`cobblemon:blue_apricorn`), qui est une dépendance dure :
  la recette ne peut donc pas se retrouver sans son ingrédient.

Points à ne pas redécouvrir :

- **L'onglet, c'est le namespace.** Un « datapack » n'existe pas à l'exécution : ce que le mod
  connaît d'un dresseur, c'est l'ID que lui a donné le pack qui l'a fourni. Le regroupement se
  fait donc sur le namespace, côté client, sur une liste que le serveur envoie déjà dans
  l'ordre d'affichage. L'onglet « tous les datapacks » n'apparaît qu'à partir de deux
  namespaces, sinon il doublerait le seul autre.
- **Le client ne regroupe jamais, il découpe.** Les intertitres de catégorie sont les runs de
  lignes voisines qui portent le même `category` : c'est le serveur qui a trié, et refaire un
  `groupBy` ici pourrait contredire son ordre. Un pack dont tous les dresseurs sont à la racine
  n'a aucun intertitre - un titre unique serait une ligne dépensée pour rien.
- **Le listing vient du serveur**, pour les mêmes raisons que l'écran du bloc : le client n'a
  ni `TrainerRegistry` ni `TrainerProgress`. Pas de `MenuType` non plus, il n'y a pas
  d'inventaire.
- **Les skins ne sont pas dans le listing.** Chacun pèse quelques kilo-octets et un monde peut
  compter cent dresseurs : ils sont demandés un par un, quand une ligne en a besoin, et servis
  depuis le cache de `TrainerSkins`. `TrainerSkinCache.get` a donc le droit de répondre null,
  ce qui veut dire « demandé, pas encore arrivé ».
- **Le serveur répond toujours, même sans image.** Une absence de réponse laisserait le client
  attendre indéfiniment, et il redemanderait à chaque frame. Un skin irrésolu est donc une
  entrée de cache sans texture.
- **L'ID demandé est revalidé** contre `TrainerRegistry`, son `listed` et son verrou : la
  demande vient d'un client, donc rien ne garantit qu'il renvoie un des dresseurs qu'on lui a
  proposés - et un dresseur caché n'a jamais été proposé.
- **Les skins sont dessinés à plat**, face par face depuis l'image (`TrainerSkinRenderer`), et
  non par le renderer d'entité : un dresseur jamais rencontré n'a pas d'entité à afficher.
  C'est aussi ce qui garde le mod sans renderer.
- **`GuiGraphics.blit` laisse l'état de blending à son appelant**, et dessiner du texte
  termine son propre batch - ce qui *désactive* le blending au passage. Un `enableBlend()` en
  début de `render()` ne suffit donc pas : c'est chaque blit qui l'active, d'où le helper
  `blit` de l'écran. Sans ça, le cadre effacerait ce qu'il recouvre.
- **`ShowdownTeamParser.countPokemon` existe pour cet écran.** Compter l'équipe avec `parse`
  ferait passer chaque espèce, capacité et objet de chaque dresseur par les registres de
  Cobblemon à chaque ouverture.
- **Les textures sont les nôtres**, sous
  `assets/cobblemon-trainers/textures/gui/battle_phone/`, volontairement neutres : un cadre,
  une case, deux flèches, un marqueur. Elles ne dépendent pas des assets de Cobblemon - une
  version antérieure les empruntait au Pokédex, ne pas y revenir. Le trou transparent de
  `frame.png` doit correspondre aux constantes `INNER_*` de l'écran : c'est lui qui délimite la
  zone dessinée.
- **Les textures sont libérées à la déconnexion** (`ClientPlayConnectionEvents.DISCONNECT`) :
  un autre serveur peut avoir d'autres dresseurs sous les mêmes ID.
- **L'équipe n'est envoyée qu'après la victoire.** Le serveur revérifie `TrainerProgress`
  avant de répondre : la fiche est une récompense, pas un outil pour repérer le prochain
  combat. Un dresseur pas encore battu reçoit une réponse *vide*, pas une absence de réponse,
  pour la même raison que les skins.
- **L'équipe passe par `create()`**, comme à l'apparition d'un dresseur, parce que c'est la
  seule façon de connaître les **aspects** finaux d'un Pokémon : le parseur pose une forme
  dans la chaîne de propriétés, et seul `create()` la transforme en aspects - ceux qui
  choisissent le modèle affiché (forme régionale, chromatique). D'où
  `ShowdownTeamParser.countPokemon` pour le listing, qui lui n'a besoin que du nombre.
- **Les modèles sont rendus par `drawProfilePokemon`** (`com.cobblemon.mod.common.client.gui`),
  avec un `FloatingState` par case, jeté quand la sélection change : un état appartient au
  modèle pour lequel il a été posé. `applyProfileTransform` est laissé à sa valeur par défaut,
  c'est lui qui met un Wailord et un Joltik à la même échelle utile.
- **La taille tient en deux facteurs, pas un.** Le `scale` de `drawProfilePokemon` est un
  `matrixStack.scale` brut : seul, il rend un Pokémon haut de quelques pixels. Cobblemon met un
  `scale(2.5f, 2.5f, 1f)` sur la pose *avant* l'appel et passe `4.5f` - c'est ce couple qui est
  repris ici, y compris son écrasement en profondeur, le `2.5` étant le seul des deux à porter
  sur z. Ne pas les fusionner en un seul nombre.
- **Le modèle descend depuis le point de translation**, il ne se tient pas dessus : viser le
  haut de la case, pas le bas. Un modèle occupe à peu près la hauteur d'une case de la grille.
- **L'ID du dresseur n'est plus affiché** dans la fiche, volontairement : c'est le namespace
  qui compte pour le joueur, et il est déjà dans le sélecteur et dans les en-têtes de la
  liste. Ne pas le remettre.
- **Les lignes de la liste n'ont pas toutes la même hauteur** - un en-tête de datapack est
  plus court qu'un dresseur -, donc l'affichage, le clic et le calcul du défilement parcourent
  les lignes au lieu de diviser par une hauteur. Les en-têtes n'existent que dans l'onglet
  « tous les datapacks » ; ailleurs le sélecteur nomme déjà le pack.
- **Le bouton d'appel est dessiné en rectangles**, pas en texture, comme tout ce qui n'est pas
  l'une des six images. Il est logé au bout de la bande du statut : c'est la seule zone libre
  qui reste en haut, l'équipe prenant tout le dessus et le cadre commençant quatre pixels sous
  la ligne de statut. Voir « Appeler un dresseur ».
- **Les récompenses tiennent dans un rail à côté du dresseur**, une case par objet. C'est ce qui
  a fait décaler la figure vers la gauche : l'écran du haut n'a pas de bande libre en dessous -
  il s'arrête quatre pixels sous le statut -, donc la place a été prise sur la largeur de la
  colonne de gauche, pas sur la hauteur. La plaque est celle du lieu et du bouton d'appel, accent
  compris : un objet posé seul à côté du dresseur se lisait comme quelque chose qui avait glissé
  de l'équipe.
- **Le rail ne grandit jamais avec la liste.** Il est haut de ce qu'il a de récompenses - une
  seule fait une case, pas une colonne vide avec quelque chose en haut - et plafonné à quatre,
  ce que la bande entre l'équipe et le statut peut tenir. Au-delà, la dernière case compte le
  reste et le nomme au survol, donc quarante récompenses dessinent exactement le même rail que
  quatre. La liste de ce survol est bornée à son tour : une infobulle plus haute que la fenêtre
  ne répond pas mieux que pas d'infobulle.
- **L'infobulle de l'écran est une liste de lignes**, pas une ligne, depuis ce rail. Les autres
  survols - équipe, bouton d'appel - n'en donnent qu'une et la rendent telle quelle.
- **Le compte est celui de vanilla** (`renderItemDecorations`), dans le coin de l'icône, qui
  n'écrit rien pour un objet seul - c'est déjà ce qu'un joueur lit comme « un ». Un « 1x » posé
  à côté de l'icône a existé : il coûtait une colonne de large pour ne rien dire.
- **Elles arrivent en `ItemStack`, pas en ID.** Le serveur les résout avec
  `TrainerRewards.preview`, exactement la résolution qui les remet au vainqueur : une fiche ne
  peut donc pas annoncer une récompense qui ne serait jamais donnée, et le client n'a ni ID à
  résoudre ni compte à borner. `ItemStack.OPTIONAL_STREAM_CODEC` fait le transport.
- **Elles sont visibles avant la victoire**, contrairement à l'équipe, et c'est délibéré : une
  équipe est une récompense pour avoir gagné, une récompense est la raison d'essayer. Un pack qui
  veut garder la surprise pose `hidden` sur la ligne concernée - c'est le seul endroit où
  `TrainerRewards.preview` et `grant` divergent volontairement, et le filtre est côté serveur :
  ce qui n'est pas envoyé ne se lit pas sur le réseau.
- **Le drapeau est par récompense, pas par dresseur.** Cacher tout un lot se fait en marquant
  chaque ligne. Un interrupteur au niveau du dresseur en plus de celui-ci ferait deux réglages
  pour un même résultat, donc deux façons de se contredire.
- **Une récompense unique porte le marqueur de la ligne de statut** : le contour tant qu'elle
  est due, la bille pleine une fois réclamée, et l'objet réclamé passe sous un voile de la
  couleur de la plaque. Ce sont les deux mêmes formes que « battu / pas encore » sur le même
  écran, donc le rail répond à « est-ce que je l'aurai encore » sans légende, et l'infobulle le
  dit en mots pour qui lit la marque comme une décoration.
- **La colonne du marqueur n'existe que si une case dessinée la demande.** Le rail passe de 26 à
  36 pixels et reste centré dans les deux cas. Une colonne de cases vides coûterait aux icônes
  leur place dans la bande.
- **Le rail est centré sur ce qui se voit, pas sur les repères du code.** `REWARD_CENTER_X` tombe
  entre le bord du skin et le premier slot de l'équipe. Centrer sur `TEAM_X` collait le rail au
  dresseur : une case d'équipe porte dix-huit pixels d'air avant son slot, donc son bord gauche
  n'est nulle part sur l'écran. `TEAM_SLOT_INSET` et `TEAM_SLOTS_WIDTH` existent pour ça, et la
  plaque du lieu s'aligne dessus elle aussi.
- **`TEAM_X` reste une constante, `TEAM_TOP` est calculé.** Le partage horizontal a été essayé -
  répartir à parts égales tout ce qui reste à droite de la figure - puis retiré : ça déplaçait
  l'équipe entre un dresseur qui donne quelque chose et un qui ne donne rien, et la colonne était
  déjà bien là où elle était. En hauteur en revanche, un `UPPER_Y + 46` en dur laissait un pixel
  sous la dernière rangée et treize au-dessus de la première : `TEAM_TOP` se lit maintenant sur
  ses deux voisins, la ligne du niveau et la plaque du lieu, ce qui garde les deux bouts égaux.
- **Le capot de `frame.png` avait 20 pixels de bordure à gauche et 13 à droite.** L'écran du haut
  n'était donc pas centré dans son cadre, ce qui se lit tout de suite comme un fond raté du côté
  étroit. Sept colonnes de corps ont été retirées à gauche - le biseau extérieur passe de 31 à 38
  et les deux bordures font 13. Le trou, lui, n'a pas bougé : aucune constante de l'écran ne
  change, et la base en dessous non plus, qui déborde le capot de ce côté de toute façon.
- **Le voile passe par `RenderType.guiOverlay()`**, celui dont vanilla éclaire ses propres
  cases : un `fill` ordinaire se dessine *sous* le modèle d'un objet, donc il ne griserait rien
  du tout.
- **Les objets se dessinent après les aplats et avant les modèles.** `GuiGraphics.renderItem`
  vide le lot en cours et gère lui-même la profondeur, donc il ne doit pas couper une série de
  `fill` ou de blits - même raison que pour les modèles, un cran plus tôt.
- **`const val REWARD_TOP = TEAM_TOP` doit être déclaré après `TEAM_TOP`.** Une constante Kotlin
  ne regarde pas en avant dans le même objet, et l'erreur (« Variable must be initialized »)
  ne dit pas que c'est une question d'ordre.

### Appeler un dresseur

Un joueur fait venir un dresseur depuis sa fiche du Battle Phone. Le mod ne fait **jamais**
apparaître un dresseur de lui-même : il y a le bloc, la commande, et cet appel.

Points à ne pas redécouvrir :

- **C'est le lieu nommé qui est l'interrupteur, pas la présence du bloc.** Il n'existe pas de
  champ `callable` à côté : nommer un lieu, c'est déclarer qu'on vient quand on est appelé, et
  deux réglages disant la même chose finiraient par se contredire. `TrainerLocation.isEmpty` est
  ce qui tranche, et il ne regarde que les conditions.
- **Un `label` seul est valide, et affiché.** Dire où l'on est et venir quand on appelle sont
  deux choses différentes : un champion pointe son arène sans s'en éloigner. D'où l'ordre dans
  `TrainerPlace.describe`, qui lit le `label` **avant** de tester `isEmpty` - inverser les deux
  ferait disparaître le lieu de tous les dresseurs non appelables. `arrival` et `busy`, eux, ne
  servent qu'à un appel : les écrire sans nommer de lieu est signalé au chargement.
- **Une condition et son libellé sont déclarés ensemble**, dans le `Check` de
  `TrainerPlace.checks`. C'est la seule garantie que le Battle Phone n'annonce pas un lieu autre
  que celui qui est testé. Ne pas séparer la description de l'évaluation.
- **Le client ne grise jamais sur le lieu.** Il sait comment une condition se lit, pas où le
  joueur se trouve par rapport à elle : le bouton reste actif et c'est le serveur qui refuse, en
  listant ce qui manque. Le grisé est réservé à ce que le client sait vraiment - un dresseur
  battu qui refuse la revanche.
- **La position est cherchée autour du Y du joueur, pas à la surface.** Un `getHeightmapPos`
  poserait le dresseur sur le toit d'un joueur en grotte, à trente blocs et un mur de là.
  Chercher dans la colonne autour de l'appelant répond à la grotte et à la plaine avec une seule
  règle. Et aucun chunk n'est chargé pour répondre à un clic de bouton - d'où `hasChunk`, en
  coordonnées de chunk, les deux `hasChunkAt` étant dépréciés en 1.21.1.
- **L'ordre des passes de `findSpot` est un ordre de préférence**, pas une optimisation :
  l'anneau voulu, puis **plus près**, puis plus loin. Rapprocher avant d'éloigner est ce qui
  répond à une clairière cernée d'eau ; `CLOSEST_DISTANCE` est ce qui empêche le dresseur
  d'arriver dans le joueur.
- **Trois tests de fluide, pas deux.** Les pieds et la tête sont les évidents ; le troisième est
  le bloc sur lequel on se tient. Une dalle immergée, une marche sous la surface et un bloc de
  glace dans un lac passent tous `isFaceSturdy` et mettraient le dresseur dans l'eau.
- **Rien n'est sauvegardé, et c'est l'aspect qui rattrape.** Un appel vit en mémoire, l'entité
  vit sur disque : un redémarrage les met en désaccord. L'aspect `trainer_call:<uuid>`
  (`CALL_ASPECT_PREFIX`) est ce qui permet à `discardOrphan` de reconnaître un dresseur appelé
  dont plus aucun appel ne se souvient.
- **Le garde `spawning` n'est pas de la superstition.** `ENTITY_LOAD` part depuis
  `addFreshEntity`, donc pendant `TrainerCalls.spawnFor`, avant que l'appel soit enregistré :
  sans lui, `discardOrphan` supprimerait le dresseur qu'on est en train de poser.
- **`isRemoved` couvre le déchargement de chunk autant que le retrait.** Le tick lâche
  l'enregistrement dans les deux cas ; ce qui reste dans le chunk est ramassé par
  `discardOrphan` au rechargement. C'est voulu : le joueur était loin de toute façon.
- **Un dresseur en combat n'est jamais retiré**, quoi que fasse l'appelant. Retirer un acteur en
  plein combat est exactement ce que `TrainerBattleEventHandler` doit rattraper ensuite, et il
  n'y a aucune raison de le provoquer.
- **Le doublon ne regarde que les entités chargées**, volontairement. La règle porte sur deux
  exemplaires de la même personne visibles à la fois, pas sur un décompte mondial - qu'un
  exemplaire dorme dans un chunk déchargé à mille blocs ne regarde personne.
- **Le nom du dresseur ne voyage pas dans `arrival`.** Le message est enveloppé par
  `chat.trainer_message`, qui le nomme déjà, donc un pack écrit du dialogue pur et reçoit
  exactement trois arguments : `x`, `y`, `z`. Passer aussi le nom obligerait chaque pack à
  commencer par `%s`. C'est le seul texte de dresseur qui reste dans le chat : il répond à un
  bouton du Battle Phone, à distance, là où aucune boîte de dialogue ne peut s'ouvrir sur un
  dresseur que le joueur ne voit pas encore.
- **La mort passe par `AFTER_DEATH`**, le même événement que l'arrêt des combats, pour deux
  raisons différentes. Les deux abonnements coexistent sans se connaître.

### Le Pokémon qui ouvre le combat

`BattleBuilder.pvn` prend un `leadingPokemon` que `PartyStore.toBattleTeam` remonte en tête de
l'équipe. Personne ne le remplissait, donc un combat partait toujours sur le premier slot.
`TrainerLead` répond à sa place, en trois temps : le slot sélectionné dans l'overlay, sinon le
Pokémon sorti, sinon le premier de l'équipe qui tienne debout.

**Une règle tient les deux côtés du combat : un Pokémon K.O. n'ouvre jamais un combat.** Rien
dans Cobblemon ne la fait respecter - `toBattleTeam` sert l'équipe dans l'ordre des slots sans
jamais lire une barre de vie -, et Showdown répond à un combat qu'il ne peut pas ouvrir par
`Can't switch: You can't switch to a fainted Pokémon`, une erreur que personne ne reçoit : le
camp qui l'a envoyée ne peut plus jouer du tout. C'est le soft lock de l'issue #32, et il
frappait le joueur et le dresseur pour la même raison.

Points à ne pas redécouvrir :

- **La sélection de l'overlay ne quitte jamais le client.** Elle vit dans
  `ClientStorageManager.selectedSlot`, que seuls les keybinds écrivent ; le serveur ne la reçoit
  dans aucun paquet de Cobblemon. C'est toute la raison d'être de `ClientPokemonSelection` et de
  son paquet, et donc de la cinquième responsabilité côté client.
- **Le Pokémon sorti est la même sélection, vue du serveur.** Sortir un Pokémon, c'est envoyer
  `SendOutPokemonPacket(selectedSlot)` : ce qui marche à côté du joueur *est* ce qu'il avait
  choisi. C'est ce repli qui fait que le cas courant marche déjà sans notre client.
- **Le test est `state is SentOutState`, pas `entity != null`.** `Pokemon.entity` répond aussi
  pour un `ShoulderedState`, et un Pokémon sur l'épaule n'est pas un Pokémon sorti.
- **Ce que le client annonce n'est jamais cru.** L'UUID est retrouvé dans la vraie équipe au
  moment du combat : un Pokémon qui n'est pas au joueur ne trouve rien et le repli joue. Le
  paquet est un indice, pas une instruction.
- **Un Pokémon K.O. n'est jamais choisi**, et le repli continue. Ce n'est pas la même règle que
  le refus de combat : celui-ci porte sur l'équipe entière et ne souffre aucune exception, alors
  qu'ici un format qui ajuste les niveaux soigne l'équipe, donc une sélection K.O. y est honorée
  au lieu d'être sautée. Le refus garantit qu'il reste toujours quelqu'un debout à faire entrer.
- **Rendre `null` ne veut plus dire « garde le défaut de Cobblemon ».** Ce défaut *est* le
  premier slot quel que soit son état : c'était lui le soft lock. Le dernier repli nomme donc le
  premier Pokémon en état de combattre - ce qui redonne le slot un quand l'équipe est en ordre,
  et le corrige sinon. `null` ne reste que pour l'équipe sans personne à envoyer, déjà refusée
  par `TrainerBattleInteraction.partyRefusal`, ou vide, ce que Cobblemon dit mieux que nous.
- **Le dresseur n'a pas de `leadingPokemon`**, donc `TrainerLead.orderTeam` range son équipe
  elle-même : les K.O. passent derrière. Un dresseur en `battle.healParty: false` garde ses
  dégâts d'un combat à l'autre, donc celui qui a perdu son premier Pokémon rouvrait le combat
  suivant avec lui. Réécrire ce store est permis là où ça ne l'est pas côté joueur : il est à
  nous, monté à l'apparition, et aucun joueur ne l'a arrangé. Un dresseur qui soigne est laissé
  tranquille - `toBattleTeam` soigne son équipe juste après, et le réordonner changerait en
  douce le lead choisi par le pack.
- **Le cas doubles/triples est refusé, pas corrigé.** `leadingPokemon` ne remplit que la
  première place, et l'équipe d'un joueur lui appartient : un format qui envoie deux ou trois
  Pokémon d'un coup est le seul cas que le mod voit venir sans pouvoir le réparer.
  `partyRefusal` regarde les places d'ouverture que `TrainerLead.teamOrder` reconstitue - la
  même liste que `toBattleTeam` - et dit quoi soigner ou remonter. Un refus lisible vaut mieux
  qu'un combat bloqué.
- **La sélection est de la session, pas du joueur.** Elle est oubliée à la déconnexion des deux
  côtés, plutôt que de survivre à une équipe réorganisée entre-temps.
- **Le client compare avant de parler.** Il n'existe aucun événement sur ce champ, d'où le tick ;
  ce qui est borné, c'est ce qui part sur le réseau, pas ce qui est lu.

### Le regard des dresseurs

Un dresseur ne se déplace jamais. Sans rien de plus il fixerait la direction dans laquelle il a
été posé, et l'aborder reviendrait à aborder une statue. `TrainerGaze` lui donne un
`LookAtPlayerGoal` : il tourne la tête vers un joueur à moins de huit blocs.

Points à ne pas redécouvrir :

- **C'est un goal vanilla, pas un tick à nous.** Cobblemon fait tourner ses NPC sur le système
  de Brain, mais `NPCEntity` ne surcharge que `customServerAiStep` : le `serverAiStep` de
  vanilla continue de ticker le `goalSelector` et le `LookControl` en dessous. La portée, les
  coups d'œil ailleurs et l'enchaînement tête puis corps sont donc gratuits, et ça ne coûte rien
  par tick quand aucun joueur n'est à portée.
- **Le crochet est le chargement de l'entité, pas l'apparition.** Les goals ne sont pas
  sauvegardés : accroché à `TrainerSpawner`, le regard aurait disparu au premier redémarrage
  pour les dresseurs tenus par un bloc. `ServerEntityEvents.ENTITY_LOAD` couvre l'apparition et
  la relecture d'un chunk avec une seule règle.
- **`probability` vaut 1, pas les 0.02 de vanilla.** Ce nombre est ce qui fait qu'un villageois
  vous jette un regard de temps en temps ; un dresseur qui attend un défi regarde qui s'avance,
  à chaque fois.
- **`Mob.goalSelector` est `protected`**, d'où `MobAccessor` - le seul rôle de ce mixin. Et côté
  Kotlin, l'appel doit passer par `as Any as MobAccessor` : le compilateur ne voit pas
  l'interface sur `NPCEntity`, puisque le mixin ne la pose qu'à l'exécution, et refuse le cast
  direct. Ce n'est pas une maladresse à nettoyer.

### Catégories

Le dossier d'un dresseur **est** sa catégorie : `champions/erika.json` porte l'ID
`<ns>:champions/erika` et la catégorie `<ns>:champions`. Rien n'est déclaré, donc rien ne peut
diverger entre le rangement et l'affichage.

Points à ne pas redécouvrir :

- **Un seul nom de fichier est réservé, `category.json`**, et il décrit le dossier où il se
  trouve. Un dossier `categories/` à la racine a été essayé puis retiré : il apparaissait dans
  l'arborescence au milieu des vraies catégories, et décrivait de loin ce qu'il ne contenait
  pas. Un `category.json` posé à la racine du dossier des dresseurs ne décrit rien et est
  ignoré avec un avertissement.
- **`TrainerCategory.order` vaut `Int.MAX_VALUE` par défaut**, ce qui range les catégories
  sans fichier après les autres sans que le tri ait un cas particulier ; leur égalité est
  départagée par le nom.
- **Les dresseurs de la racine passent en dernier**, sous un titre du mod
  (`category.uncategorized`). C'est le seul groupe dont le nom ne vient pas d'un pack.
- **Le tri vit dans le registre, pas dans les écrans.** `TrainerRegistry.listed()` rend déjà
  l'ordre final ; le Battle Phone et `/cobblemontrainers list` ne font que le découper.

### Conditions de combat

`TrainerRequirements` (le bloc `requires`) ferme un dresseur, et `TrainerLock` est le seul
endroit qui l'évalue - pour l'interaction, pour le listing du Battle Phone, pour la commande.

Points à ne pas redécouvrir :

- **`unmet()` rend une liste de `Component`, pas un booléen.** La même liste sert de message de
  refus dans le chat et d'indice dans l'écran, et comme un `Component` traduisible est résolu
  côté client, elle traverse le réseau sans que le Battle Phone sache ce qu'est une condition.
  D'où `ComponentSerialization.STREAM_CODEC` dans le paquet du listing.
- **Un ID introuvable compte comme non rempli.** Objet, dresseur ou advancement : le compter
  comme rempli ouvrirait le dresseur à tout le monde sur une faute de frappe.
- **Rien n'est jamais consommé.** Un `items` est une clé que le joueur garde, sans quoi une
  revanche demanderait de refarmer l'objet. Ça a été posé comme règle, pas comme défaut.
- **`party` se répond avec `PokemonProperties.matches`**, pas avec une comparaison d'espèce.
  C'est le seul usage prévu du champ `aspects` (voir « Formes et aspects »), et c'est ce qui
  fait qu'un pack peut demander un chromatique ou une forme précise sans que le mod ait un
  champ pour chacun. La chaîne est mise en minuscules avant `parse` : tous les identifiants de
  Cobblemon le sont, donc `Staraptor` et `staraptor` demandent la même chose.
- **Une exigence que Cobblemon lit comme vide est refusée.** Son parseur laisse tomber sans un
  mot les clés qu'il ne connaît pas, donc une faute de frappe donnerait des propriétés qui
  matchent n'importe quel Pokémon - c'est-à-dire un dresseur ouvert à tout le monde, l'exact
  inverse de ce qu'une condition doit faire en échouant. D'où le test sur `asString`.
- **La condition et son libellé sont construits ensemble** (`PartyQuery`), pour la même raison
  que le `Check` de `TrainerPlace` : le nom affiché ne peut pas désigner autre chose que ce
  qui est testé. Le nom traduit de l'espèce n'est pris que pour une espèce nue ; dès qu'il y a
  une propriété en plus, c'est la chaîne du pack qui est montrée, faute de savoir la nommer.
- **Seule l'équipe est lue, jamais le PC, et un K.O. compte.** La question est qui voyage avec
  le joueur, pas qui peut se battre - et une condition qu'un joueur ne peut pas vérifier d'un
  coup d'œil serait une condition qu'il subit.
- **`victories` exclut le dresseur qui l'exige** de son propre pool, pour qu'un champion puisse
  demander « battre tous les champions ». Le pool ne contient que des dresseurs `listed`.
- **Le masquage est décidé côté serveur.** Un dresseur `hidden` et verrouillé n'est pas envoyé
  du tout, et sa demande de skin est refusée : le listing est la seule chose qui trahirait son
  existence.
- **`/cobblemontrainers list` ne masque rien**, volontairement : c'est la vue de l'opérateur,
  et elle évalue les conditions contre le joueur ciblé, pas contre l'appelant.

### Advancements

`TrainerDefeatedTrigger` enregistre `cobblemon-trainers:trainer_defeated` dans
`BuiltInRegistries.TRIGGER_TYPES`, depuis `onInitialize` - avant la lecture des datapacks, sans
quoi un advancement qui l'utilise échoue au parsing.

Le mod ne livre aucun advancement **de jeu** : ils vivraient dans tous les mondes et,
contrairement à un dresseur, un advancement n'a pas d'interrupteur `listed`. Les exemples sont
dans `examples/cobblemonrlm/data/cobblemonrlm/advancement/` (dossier au **singulier** depuis
1.21).

La seule exception est `advancement/recipes/battle_phone.json`, qui n'est pas un objectif mais
le déblocage de recette du Battle Phone : sans lui la recette existe mais n'apparaît jamais
dans le livre de recettes. Il est invisible dans l'arbre et ne fait ni toast ni annonce, comme
tous ceux de vanilla. Voir « Le craft du Battle Phone ».

Points à ne pas redécouvrir :

- **`CriteriaTriggers.register` est privé** en 1.21.1 : passer par
  `Registry.register(BuiltInRegistries.TRIGGER_TYPES, …)` est la seule voie pour un ID qui ne
  soit pas dans le namespace `minecraft`.
- **`SimpleInstance.player()` est une méthode Java**, pas une propriété : une data class Kotlin
  générerait `getPlayer()` et n'implémenterait rien. D'où le champ privé et l'override.
- **Le trigger part après `TrainerProgress.recordVictory`**, jamais avant : la condition
  `count` se compte sur cet enregistrement, victoire courante comprise.
- **`count` se compte sur la progression, pas sur un compteur à part.** C'est ce qui survit à un
  redémarrage, et c'est ce que le joueur appellerait son score.

### Skins

`TrainerSkins` est le seul endroit qui transforme un `TrainerSkin` en image, pour ses deux
appelants : `TrainerSpawner`, qui habille un NPC, et le Battle Phone, qui dessine un dresseur
dans un écran. Tout ce qui bloque - recherche de profil, téléchargement, lecture de pack -
tourne sur son pool de threads daemon ; le rappel arrive donc *hors* du thread serveur, et
c'est à l'appelant de repasser par `server.execute` avant de toucher au monde.

Le résultat est mis en cache, échecs compris, et la clé est la déclaration de skin, pas le
dresseur : deux dresseurs qui portent le même skin partagent une entrée. Sans ce cache, une
ligne du Battle Phone coûterait un aller-retour Mojang. `/reload` le vide (dans
`CobblemonTrainers.TrainerReloadListener`) : c'est le moment où un pack peut avoir changé
l'image derrière un nom déjà résolu.

`applySkin` pose les aspects `model-default` / `model-slim` en même temps que
`NPC_PLAYER_TEXTURE` : ce sont eux qui déterminent le rig utilisé au rendu. Un échec est
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

### L'IA de combat

`battle.ai.TrainerBattleAI` enveloppe le `StrongBattleAI` de Cobblemon et refuse celles de ses
décisions qui ne peuvent pas être bonnes. Le raisonnement reste le sien : la couche dit non, et
propose autre chose. Trois défauts sont couverts, tous constatés en jeu à skill 5.

- **Un coup sur une cible immunisée.** `findAndUseMostDamagingMove` prend le `maxByOrNull` de ses
  estimations **sans plancher** : quand tout vaut 0, la première entrée de la map gagne. Et
  `choose()` se termine sur un `availableMoves.randomOrNull()` dès qu'aucune branche n'a décidé,
  immunités comprises.
- **Les immunités de talent et d'objet ne sont jamais lues.** La table existe pourtant dans
  Cobblemon (`AIUtility.typeImmuneAbilities`) ; `moveDamageMultiplier` ne la consulte pas. Un Sol
  sur un Lévitation passe donc pour neutre.
- **Les boucles de changement.** `checkSwitchOutSkill()` obéit à `shouldSwitchOut` avec une
  probabilité de `0, 0, 0, 0.2, 0.6, 1.0` pour les skills 0 à 5 - donc **toujours** à 5 - et
  `shouldSwitchOut` n'a aucune mémoire du tour précédent. Un mauvais matchup fuit vers un autre
  mauvais matchup, chaque tour, pendant que le joueur tape gratuitement. Les skills bas ne sont
  pas plus malins : c'est le dé qui casse la boucle.
- **Le soin sur une règle plate.** Cobblemon joue le **premier** coup de soin de la liste dès que
  les PV passent sous la moitié, sans jamais demander ce que l'adversaire rend au tour suivant,
  ni si un KO était en main, ni combien le coup restaure vraiment.
- **Ce qui encaisse un coup décisif n'est jamais vu.** Frimousse et Ice Face annulent le premier
  coup entier, Baraka et la Ceinture Force laissent 1 PV. L'IA dépense son meilleur coup, la
  cible survit, et le tour suivant est offert.
- **La riposte n'entre dans aucun calcul.** Rien ne compare ce qu'on rend à ce qu'on prend, ni ne
  regarde qui frappe en premier : un dresseur sur le point de tomber pose tranquillement un boost
  qui ne se résoudra jamais.
- **Les écrans sont joués comme n'importe quelle capacité d'installation.** Ils sont dans les
  `setupMoves` de Cobblemon, donc tirés au sort, sans regarder ce qui est déjà posé, ce que
  l'adversaire frappe, ni si une Lumargile est en main - un dresseur bâti autour d'eux n'y venait
  quasiment jamais.

Points à ne pas redécouvrir :

- **La difficulté dose la correction, pas l'information.** Un wrapper ne peut pas rendre l'IA de
  Cobblemon *moins* informée : son `ActiveTracker` garde une référence sur les vrais objets
  `Pokemon` du joueur, et rien ne permet de s'interposer. `CorrectionLevel` traduit donc
  `battle.difficulty` en qualité de décision - rien en dessous de 3, immunités de type et
  discipline de changement à 3-4, tout le reste à 5. Qu'un dresseur de route se trompe encore est
  voulu, pas un oubli.
- **Les tables sont celles de Cobblemon.** `AIUtility` expose publiquement son chart de types,
  `typeImmuneAbilities`, `canAffectWithStatus` et ses listes d'intentions (statuts, boosts,
  pièges, soins, météo, protections). Les réutiliser plutôt que d'en écrire fait qu'une mise à
  jour de Cobblemon suit toute seule. `BattleTypeChart` n'y ajoute que trois correctifs :
  `immunity` est retiré (Vaccin bloque l'empoisonnement, pas les dégâts Poison, et l'honorer
  ferait refuser une attaque parfaitement valable), `lightningrod` est ajouté (Cobblemon écrit
  `lightingrod`, qui ne correspond à aucun talent), `dryskin` et `wellbakedbody` manquaient.
- **Un coup « intentionnel » n'est jamais échangé contre des dégâts.** Sans ça la correction
  aplatirait le jeu de Cobblemon en « tape toujours le plus fort » et ferait disparaître boosts,
  statuts, pièges et soins. C'est le seul rôle de `purposefulMoves`.
- **Le changement exige un gain de matchup réel** (`MATCHUP_GAIN_REQUIRED`, une demi-marche
  d'efficacité). C'est la seule règle anti-boucle : un cooldown ou un quota de changements par
  combat traitaient le symptôme, celle-ci traite la cause - fuir vers quelqu'un d'aussi mal loti
  n'est plus une option. Un demi-point, et pas un point entier, parce qu'un pivot défensif - un
  remplaçant qui résiste sans taper plus fort - ne gagne que ça et doit passer.
- **Trois des quatre raisons de changer de Cobblemon n'ont rien à voir avec les types**, et le
  test de gain les notait donc toutes à zéro : `shouldSwitchOut` part aussi sous 30 % de PV, à
  une stat tombée à -3 ou pire, et sur `truant` / `slowstart`. Un dresseur qui ne switchait plus
  du tout est le symptôme de ce test appliqué à tout. `nonTypeReason` rend ces cas à Cobblemon
  sans les juger, plus celui du dresseur muré (tout résisté), et seul le changement réellement
  motivé par le type passe par le seuil. Ne pas y remettre `slowstart` et `truant` sans le
  demander : c'est un choix, pas un oubli.
- **Le piège d'entrée et l'écran sont les deux seules règles qui *ajoutent* une décision**, tout
  le reste refuse. Elles s'allument à la difficulté 4 et non sur un `CorrectionLevel` entier,
  parce que c'est là qu'elles ont été demandées - d'où le `difficulty` brut gardé à côté du
  niveau. Pour le piège, aucun test « est-il déjà posé » n'est nécessaire : c'est le premier
  tour, rien n'a bougé. Et `openingPlayed` est porté par l'acteur, pas par le Pokémon, sinon les
  deux leads d'un combat double poseraient deux fois le même piège.
- **C'est la Lumargile qui autorise l'écran, pas l'IA.** Un objet qui ne fait rien tant qu'aucun
  écran n'est posé est une intention de pack, au même titre qu'une méga-gemme : la couche
  l'exécute au lieu de la juger. Sans lui, Cobblemon garde la main - il range les écrans dans
  ses `setupMoves`, joués au hasard et sans savoir si l'un est déjà en place.
- **Ce qui est déjà debout se lit à deux endroits, comme une Frimousse.** Les `BattleContext`
  d'abord - les écrans dans le `contextManager` du **côté** (`Type.SCREEN`), la météo dans celui
  du **combat** (`Type.WEATHER`) -, et notre propre `screensPlayed` ensuite, qui note le tour où
  un écran a été joué. Le second n'est pas une ceinture : en jeu, le contexte de camp n'est pas
  arrivé, et une règle qui ne lisait que lui reposait le Voile Aurore **à chaque tour**. Une
  mémoire est toujours là, donc le pire cas est un écran reposé huit tours trop tard.
  `ContextManager.get` peut rendre null, d'où le `orEmpty()`, et `BattleActor.getSide()` est une
  méthode Java : Kotlin ne l'expose pas en propriété.
- **Voile Aurore ferme la question.** Il vaut les deux autres écrans, donc tant qu'il est debout
  la règle rend la main - sans quoi le dresseur enchaînait trois tours d'installation pour un
  seul effet utile.
- **La mémoire n'est jamais effacée quand un écran saute** (Casse-Brique, Anti-Brume). Un écran
  reposé huit tours plus tard est une erreur bien plus petite qu'un écran reposé tous les tours,
  et c'est exactement ce dont on revient.
- **Les ids de contexte sont déjà normalisés à la Showdown** (`Effect.Companion` passe en
  minuscules et retire le reste), donc `auroraveil` et `lightscreen` tombent exactement sur les
  ids de capacité. Le `normalise` de `BattleScreens` est une ceinture, pas une traduction.
- **Voile Aurore hors neige n'est pas refusé par Showdown**, il échoue. D'où la lecture de la
  météo avant de le proposer, avec `hail` gardé à côté de `snow` : c'est le même écran, sous le
  nom que Showdown envoyait avant la génération 9.
- **Le soin est jugé, jamais ajouté.** `correctHeal` refuse le soin quand un coup met KO ce
  tour-ci, quand l'adversaire rend au moins autant que ce qui est restauré (le tour ne rachète
  rien), et quand la barre n'a pas assez descendu pour l'absorber (`MIN_HEAL_USED`). Un wrapper
  ne voit que ce que Cobblemon propose : il ne peut pas faire soigner un dresseur qui n'en avait
  pas l'intention, donc le seuil effectif ne peut que monter à l'intérieur des 50 % de Cobblemon,
  jamais descendre.
- **Les règles tactiques s'arrêtent à `FULL`, et c'est délibéré.** `tactics()` - soin jugé, garde
  cassée au moins cher, KO qui part en premier, boost abandonné quand on tombe ce tour-ci, Abri
  non répété - ne tourne qu'à la difficulté 5. Un dresseur de route qui ne se trompe jamais sur
  un KO et ne gaspille jamais un tour n'est plus un dresseur de route.
- **Une garde se casse avec le coup le moins cher, pas le plus fort.** C'est l'exact inverse de la
  règle « joue le plus fort » du même niveau, et l'ordre dans `tactics()` est ce qui fait gagner
  la bonne : la garde est traitée avant. `BattleGuards.guardIntact` croise deux sources - l'aspect
  de forme (`busted-form`, `noice_face`), exact mais qui suppose que Cobblemon répercute le
  changement de forme de Showdown, et notre propre mémoire `struck` des Pokémon déjà frappés,
  toujours disponible et au pire en retard d'un tour. La première des deux qui dit « cassée »
  l'emporte.
- **On ne peut compter que nos propres Abri.** Cobblemon tient un `protectCount` de l'adversaire
  dans son tracker privé ; nous ne voyons que nos propres décisions. La règle porte donc sur le
  dresseur - pas deux Abri d'affilée - et pas sur ce que fait le joueur.
- **Une égalité de vitesse est perdue, par convention.** `BattleSpeed.movesFirst` tranche en
  faveur de l'adversaire : en jeu c'est un pile ou face, et une IA qui suppose gagner tous ses
  pile ou face joue imprudemment, alors que l'inverse la rend seulement prudente. Distorsion,
  Vent Arrière et les objets de vitesse ne sont pas lus - se tromper d'ordre coûte un coup mal
  choisi, pas un combat cassé.
- **La priorité prêtée à l'adversaire est celle de sa capacité la plus dangereuse**, que
  `BattleDamage.strongestAgainst` rend avec ses dégâts. Prendre le maximum de son arsenal a été
  écrit puis retiré : une Vive-Attaque dans l'équipe du joueur suffisait à ce que le dresseur ne
  se croie jamais premier, et toutes les règles qui dépendent de l'ordre s'éteignaient.
- **Un dernier tour va à la capacité qui part encore.** La règle ne se limite pas aux boosts :
  face à un KO et en second, *toute* capacité non prioritaire est remplacée par la meilleure
  prioritaire. C'est le cas du Mimiqui sous Danse-Lames qui jouait Câlinerie au lieu d'Ombre
  Portée - la première version ne corrigeait que les coups non offensifs et le laissait passer.
- **`BattleDamage` existe parce qu'un classement ne suffit plus.** « Ce coup met-il KO » et « ce
  soin rachète-t-il le tour » se répondent en points de vie. Le `calculateDamage` de Cobblemon
  ferait l'affaire mais prend les `TrackerPokemon` de son `activeTracker` privé, sans getter :
  injoignable. La formule est donc réimplémentée, jet moyen et non maximal, pour qu'un KO annoncé
  soit un KO réel.
- **L'impasse est le seul cas où l'on change sans exiger de gain**, et le remplaçant doit quand
  même être *strictement* meilleur. Un remplaçant à égalité serait renvoyé au tour suivant, et la
  boucle reviendrait de notre fait.
- **Cobblemon demande sa décision plus d'une fois par tour.** `RequestInstruction` et
  `TurnInstruction` envoient chacune un `BattleMakeChoicePacket`, et `AIBattleActor.sendUpdate`
  en fait un `onChoiceRequested` à chaque fois ; Cobblemon écrase sa propre réponse, donc ça ne
  se voyait pas avant que la couche ne garde une mémoire entre les tours. `DecisionKey` rend la
  réponse déjà donnée tant qu'elle reste valide. Sans ça, la seconde passe lisait un `struck`
  que la première venait de remplir : une Frimousse intacte y paraissait cassée.
- **Rien ne doit remonter de `choose()`.** Un combat attend cette réponse : une exception y
  laisserait le joueur enfermé dans un combat où personne ne peut jouer. D'où le `try/catch`, qui
  rend la décision de Cobblemon telle quelle.
- **Chaque substitution est loguée en `debug` avec sa raison**, et envoyée en chat aux joueurs
  qui ont activé `/cobblemontrainers debugai`. Sans ça un seuil est intuable : un changement
  refusé et un changement que personne n'a proposé se ressemblent exactement depuis l'autre côté
  du combat. `TrainerAiDebug.idle()` évite de formater une ligne que personne ne lira.

### Les gimmicks de combat

Un dresseur qui déclare `battle.gimmicks: ["mega"]` méga-évolue en combat. `TrainerGimmicks`
tient le vocabulaire, `TrainerBattleAI.withGimmick` la décision. Toute la doc joueur est dans
`docs/GIMMICKS.md`, jamais dans `docs/DATAPACK.md`, qui n'en garde qu'une ligne de tableau et un
renvoi.

**Cobblemon 1.7.3 fait déjà tout.** `MoveActionResponse` porte un `gimmickID` à côté de son coup
et de sa cible, et le `ShowdownMoveset` que reçoit `choose()` dit ce que le simulateur offre ce
tour-ci (`canMegaEvo`). Répondre `mega` à côté du coup est tout ce qu'il y a à faire.

**Aucune ligne du mod ne nomme Mega Showdown**, et rien ne compile contre lui. Ce qu'il
apporte : les gemmes, le patch du simulateur qui les lui fait voir, et la forme à l'écran - via
le `MegaEvolutionEvent` de Cobblemon, qui ne regarde pas à qui appartient le Pokémon.

**Le jeu de dev le charge depuis `run/mods/`**, où `copyDevMods` copie la configuration
`devMods` (Mega Showdown, `accessories` dont il dépend en dur, `owo` dont accessories dépend).
Un `modRuntimeOnly` a été essayé et retiré : un jar de mod embarque ses bibliothèques en JiJ -
owo y range endec et jankson - et Loom ne les met pas sur le classpath de dev, donc le jeu
plantait sur un `NoClassDefFoundError: io/wispforest/endec/util/MapCarrier`. Fabric, lui, les
déballe comme chez un joueur et remappe les mods au passage.

Points à ne pas redécouvrir :

- **Rien ne filtre le côté PNJ.** `ShowdownActionRequest.sanitize` ne coupe un gimmick que pour
  un acteur dont l'UUID est celui d'un **joueur** du combat sans la key item qui va avec
  (`cobblemon:key_stone` pour la méga). Un `NPCBattleActor` n'y correspond jamais. Conséquence
  assumée : un dresseur méga-évolue face à un joueur qui n'a pas de Key Stone, et c'est au pack
  de compenser.
- **Le gimmick est posé hors du garde `CorrectionLevel`.** `choose()` rendait la décision de
  Cobblemon telle quelle en dessous de la difficulté 3 ; un dresseur facile n'aurait donc jamais
  méga-évolué. C'est le pack qui a donné la gemme et écrit le mot, pas l'IA qui a eu une bonne
  idée. D'où la découpe en `decide()` (les corrections) puis `withGimmick()` (le gimmick).
- **La mémoire est la requête, pas un booléen.** Un camp ne méga-évolue qu'une fois, donc le
  second actif d'un double doit être refusé - mais Cobblemon demande sa décision **deux fois par
  tour** (voir « L'IA de combat »), et répondre la seconde fois sans le `gimmickID` écraserait la
  première réponse et annulerait la méga. Comparer la `DecisionKey` répond aux deux d'un coup :
  même requête, même réponse ; requête différente, refus.
- **`isValid` ne vérifie pas la disponibilité du gimmick.** Répondre `mega` quand le simulateur
  ne l'offre pas est une erreur Showdown en plein combat, pas un refus poli. `canMegaEvo` est
  donc le seul garde qui compte, et il est lu à chaque décision.
- **On copie la réponse, on ne la mute pas.** `gimmickID` est un `var`, mais l'objet vient du
  délégué.
- **Le mot du pack est l'id de Cobblemon** (`mega`, `terastal`, `zmove`, `dynamax`, `ultra`).
  Les quatre non supportés sont reconnus au chargement et signalés comme tels, pour qu'un pack
  qui les écrit ne les confonde pas avec une faute de frappe.

### Les objets tenus d'une équipe

`ShowdownTeamParser` cherche un objet sans namespace chez `cobblemon:` d'abord, puis par chemin
dans **tous** les namespaces enregistrés, et n'accepte que s'il n'y en a qu'un. C'est ce qui fait
qu'un `@ Charizardite X` collé depuis Showdown trouve la gemme de Mega Showdown sans que le
parseur connaisse ce mod. Ambigu, c'est refusé plutôt que deviné.

La ligne `Fallback Item:` est à nous, écrite dans la forme de celles de Showdown comme
`Aspects:`. Le premier objet de la chaîne qui existe est porté ; plusieurs lignes sont essayées
dans l'ordre écrit. La règle ne connaît pas les gemmes : c'est la réponse générale à un objet
qu'un mod absent ne fournit pas, et elle évite qu'un Pokémon prévu avec un objet se batte les
mains vides. Toutes les raisons de refus sont loguées dans `resolveItem`, une par cas.

### Les dialogues

Un clic droit sur un dresseur n'ouvre plus le combat : il ouvre la boîte de dialogue de
Cobblemon, celle que leur NPC `standard` utilise via `cobblemon:npc-example`. Le dresseur
salue, le joueur choisit Combattre ou Annuler, et le dresseur dit son mot une fois le combat
fini. Les cinq `messages` d'un dresseur (`greeting`, `start`, `decline`, `win`, `lose`)
passent tous par là ; plus rien de tout ça n'est envoyé au chat.

Le `Dialogue` est **monté en Kotlin, par joueur et par interaction**, pas lu depuis un
`data/<ns>/dialogues/`. Deux raisons, et aucune des deux ne se contourne en MoLang : les
répliques vivent dans le fichier du dresseur, à côté de son équipe, et les boutons dépendent
d'un état serveur - revanche, `requires`, équipe K.O. - qu'une expression de dialogue ne sait
pas interroger. Le mod ne livre donc **aucun asset de dialogue**.

Le partage est le même qu'avant : `TrainerBattleInteraction` garde les règles (`refusal`,
`startBattle`), `TrainerDialogue` ne fait que les mettre en scène. La boîte n'affiche jamais
autre chose que ce que `refusal` a déjà décidé.

Points à ne pas redécouvrir :

- **Rien n'a besoin d'être enregistré côté client.** `DialogueOpenedPacket` sérialise la *page*
  regardée - texte, options, portrait -, pas une référence vers un dialogue du registre. Une
  boîte montée sur le serveur arrive donc sur un client Cobblemon nu exactement comme les leurs.
- **`DialogueManager.startDialogue(player, npc, dialogue)`** est la bonne des trois surcharges :
  c'est elle qui pose `q.npc` dans le runtime et le `npc` sur l'`ActiveDialogue` - ce dernier
  étant ce que lit `isTalking`.
- **Le portrait est tiré de l'entité** (`ReferenceDialogueFaceProvider(npc.id, …)`), donc un
  dresseur supprimé pendant que la boîte est ouverte laisse un cadre vide. C'est toute la raison
  du `!TrainerDialogue.isTalking(...)` dans le tick de `TrainerCalls` : un dresseur appelé ne
  rentre pas tant que son appelant lit. Le garde ne fait que reporter - fermer la boîte, ou se
  déconnecter, le lève au passage suivant.
- **`PokemonBattle.end()` tourne avant que `BATTLE_VICTORY` soit émis.** Les `onEndHandlers`
  s'exécutent donc avant que la boîte d'adieu existe : inutile d'essayer de les enchaîner, c'est
  le garde ci-dessus qui répond, pas un `CompletableFuture` accroché à la fin de combat.
- **Les listes de Cobblemon sont des `MutableList`.** `DialoguePage.lines` et
  `DialogueOptionSetInput.options` refusent un `listOf` : `mapTo(mutableListOf<DialogueText>())`
  et `mutableListOf`. L'erreur du compilateur ne dit pas que c'est une question de variance.
- **Un message absent ne fait pas une page vide, il retire une étape.** Sans `start`, le
  bouton Combattre lance le combat lui-même ; sans `decline`, Annuler ferme la boîte. Une page
  n'existe que si un pack a quelque chose à y mettre.
- **Le mot de la fin est dit à chaque joueur, pas diffusé.** Une boîte appartient à qui la
  regarde, donc `win` va aux gagnants et `lose` aux autres, là où l'ancien message de chat
  disait la même chose à tout le monde.
- **La boîte d'adieu attend une seconde.** Sans ça elle s'ouvre par-dessus l'interface de combat
  qui affiche encore le résultat. Ce délai tient dans les cinq secondes que `TrainerCalls`
  laisse à un dresseur appelé avant de le renvoyer.
- **Un joueur déjà en combat ne reçoit pas de boîte** : `interact` va droit à `startBattle`, dont
  l'erreur Cobblemon dit mieux pourquoi rien ne se passe qu'un dialogue par-dessus le combat.
- **Échapper depuis la page `accept` lance quand même le combat.** Le défi est accepté à ce
  moment-là ; l'échappatoire, c'est le bouton Annuler de la page d'avant.
- **`decline` répond au bouton Annuler, pas à Échap.** Un pack qui écrit un mot pour qui
  refuse s'adresse à un joueur qui a répondu, pas à un joueur qui veut sortir de la boîte -
  retenir ce dernier une page de plus est exactement ce qu'Échap ne doit pas faire. La page
  `greeting` garde donc l'action d'échappement par défaut, qui ferme.
- **Les libellés des boutons sont au mod, jamais au pack** (`dialogue.option.*`). Un pack qui
  renommerait « Combattre » ferait un bouton dont personne ne peut deviner l'effet.

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

Un joueur qui s'éloigne, lui, n'était rattrapé par personne : `PokemonBattle.tick` n'appelle
son `checkFlee` que `if (isPvW)`, donc **un combat contre un NPC n'est jamais mesuré** et le
joueur restait enfermé dedans à l'autre bout du monde - le bug #36. `TrainerBattleRange`
transcrit ce `checkFlee` pour nos combats : la distance est le `defaultFleeDistance` de la
config Cobblemon, celle-là même que `BattleBuilder.pve` donne à un Pokémon sauvage, et elle
se mesure du dresseur au joueur le plus proche.

Points à ne pas redécouvrir :

- **Le tick est celui du serveur, pas celui du combat**, et il est conditionné aux
  `dispatches` comme celui de Cobblemon : couper un combat au milieu de sa file d'actions,
  c'est poser un `>forcetie` sur un combat qui parlait encore.
- **Un joueur dans une autre dimension compte comme parti**, faute d'être à une distance
  quelconque du dresseur. C'est le comportement de `checkFlee`, dont le filtre de niveau
  élimine simplement l'entité.
- **La position du dresseur est lue même une fois l'entité retirée**, et c'est justement le
  cas que ça répare : un chunk déchargé ne passe pas le `removalReason.shouldDestroy()`
  ci-dessus, donc un dresseur déchargé n'arrêtait pas son propre combat. Sa dernière
  position connue est la bonne : la question est de savoir si le joueur est loin de là où le
  dresseur se tenait.
- **Les joueurs sont relus sur les acteurs, pas sur `battle.players`.** Cette liste est figée
  à la construction du combat, donc elle garde le `ServerPlayer` qu'une reconnexion a
  remplacé ; `PlayerBattleActor.entity` refait la recherche par UUID à chaque appel.
- **Les combats sont arrêtés hors de l'itération** : `stop()` déclenche les `onEndHandlers`,
  dont l'un revient effacer l'entrée de la map qu'on est en train de parcourir.

### Livrer un pack

Trois voies, toutes prises en charge et **laissées au choix de l'auteur du pack** - ne pas en
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
n'est logué) - c'était la cause d'un pack « qui ne se charge pas » sans le moindre message.

Elle est branchée par deux mixins, parce qu'il n'existe aucune API pour ajouter une source
après construction :

- `PackRepositoryMixin` (commun) - `@Inject` en fin de `<init>`, réécrit le champ `sources`.
  Un `ServerPacksSource` dans la liste identifie un dépôt de données ; couvrir le constructeur
  plutôt que ses appelants attrape d'un coup le serveur dédié, le serveur intégré et l'écran
  de création de monde.
- `client.MinecraftMixin` (client) - `@ModifyArg` sur le tableau varargs du constructeur de
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
`ServerLifecycleEvents`), mais ça ne marche qu'en solo - sur un serveur le client n'a pas le
fichier - donc un pack se comporterait différemment en solo et en multi. `mods/` n'a pas ce
défaut : chaque côté lit son propre dossier.

`pack_format` diffère par type en 1.21.1 : 48 côté données, 34 côté ressources. Une archive
qui sert des deux côtés déclare `supported_formats` en intervalle, sinon l'écran des resource
packs l'affiche comme incompatible. C'est ce que fait `examples/cobblemonrlm/pack.mcmeta`.

### Mixins

`ExampleMixin` est le stub du template Fabric, sans effet.

`client.ClientLevelMixin` rend le bloc de dresseur visible quand on tient son item ; il est
décrit sous « Le bloc de dresseur ». `client.MusicManagerMixin` retient la musique de fond du
jeu pendant un combat ; il est décrit sous « Musique de combat », n'a aucun `@Shadow` et tient
en un `@Inject(HEAD, cancellable)`. Comme `client.MinecraftMixin` les deux sont déclarés dans
le tableau `client` du mixins.json : une classe client dans `mixins` ferait échouer le
chargement sur un serveur dédié.

`AIBattleActorMixin` remplace l'IA de combat des dresseurs du mod par `TrainerBattleAI` (voir
« L'IA de combat »). Le constructeur de `NPCBattleActor` prend pourtant l'IA en paramètre -
`StrongBattleAI(skill)` n'en est que la valeur par défaut - mais `BattleBuilder.pvn` ne l'expose
pas, et bâtir le combat nous-mêmes voudrait dire réécrire toutes les vérifications et tous les
messages d'erreur que `pvn` renvoie au joueur. Le remplacement se fait à la première demande de
décision plutôt que dans un constructeur : le champ vit sur `AIBattleActor` alors que l'identité
du dresseur vit sur le `NPCBattleActor` en dessous, et à `AIBattleActor.<init>` la sous-classe
n'a pas encore posé son `npc`. Le filtre est l'aspect `trainer_id:`, donc un pack tiers qui
utilise le mod comme API en profite sans rien déclarer. Comme il vise une classe Cobblemon et non
une classe Minecraft, Loom ne remappe rien : la cible reste `onChoiceRequested` dans le jar.

`MobAccessor` ouvre `Mob.goalSelector`, `protected` et donc hors de portée de qui n'est pas un
mob. Son unique client est `TrainerGaze` (voir « Le regard des dresseurs »). Vérifié dans le jar
construit : la cible est bien passée en intermediary (`Mob` → `class_1308`, `goalSelector` →
`field_6201`).

`PackDetectorMixin` fait accepter les archives `.jar` à
`PackDetector.detectPackResources`, unique endroit où Minecraft filtre sur `.zip` - tout le
reste de la chaîne marche déjà, `FilePackResources` ouvrant le fichier avec `ZipFile`, qui se
moque de l'extension. `PackDetector` étant partagé par toutes les sources sur dossier, ça
vaut pour `datapacks/` comme pour `resourcepacks/`, ce qui est le but : un auteur qui a
construit un `.jar` pour `mods/` peut le déposer tel quel aux deux autres emplacements sans
réempaqueter en `.zip`.

Loom remappe les mixins statiquement (pas de refmap dans le jar) : après un changement,
vérifier dans `build/libs/*.jar` que la cible est bien passée en intermediary
(`detectPackResources` → `method_52441`, `PackDetector` → `class_8621` ;
`getMarkerParticleTarget` → `method_35752` ; `MusicManager` → `class_1142`, `tick` →
`method_18669`).

### Formes et aspects

Une forme Cobblemon n'est pas une espèce : c'est la même espèce portant d'autres **aspects**,
et `Pokemon.updateForm()` choisit la forme via `Species.getForm(aspects)`. La ligne
`Aspects:` du format d'équipe les déclare, et `ShowdownTeamParser` les réinjecte dans la
chaîne de propriétés (`rlm=true`, ou `appliance=wash` tel quel pour une caractéristique à
choix) - la même syntaxe que `/pokespawn`, donc testable en jeu.

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
  fournisseur qui est un `CustomPokemonPropertyType` - donc peuplée par les datapacks, et
  seulement consultable une fois le serveur chargé (l'équipe est construite à l'apparition
  du dresseur, c'est bon).

## Publication

`.github/workflows/release.yml` se déclenche à la **publication d'une release GitHub** :
il construit, publie sur Modrinth via `./gradlew publishMods`
(`me.modmuss50.mod-publish-plugin`), puis attache les assets à la release.

**Modrinth est la seule plateforme.** CurseForge a été tenté puis retiré : son API d'upload
répondait invariablement `Invalid game version ID: 11779 belongs to an invalid dependency`.
Le plugin envoie quatre catégories d'IDs (version Minecraft, modloader, environnement
client/server, version Java), toutes résolues depuis la liste de CurseForge elle-même - l'ID
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

- **La description Modrinth est `MODRINTH.md`**, poussée par le workflow à chaque release :
  la page ne peut donc pas décrire un build antérieur, et elle se relit en pull request comme
  le reste. `mod-publish-plugin` ne sait que créer des *versions* - le projet lui-même se
  modifie par `PATCH /v2/project/<id>`, avec le jeton en `Authorization` **sans** `Bearer`, et
  une réussite répond `204` sans corps. L'étape est **la dernière** du workflow : une
  description refusée ne doit pas coûter à la release ses assets, et le run rouge dit quoi
  rejouer. Un lien relatif dans ce fichier serait mort sur Modrinth - tout y est en URL
  absolue.
- **La version publiée déclare son environnement**, `environment = CLIENT_AND_SERVER` dans le
  bloc `modrinth`. Modrinth exige une métadonnée d'environnement exacte (règle 5.1 des Content
  Rules) et rejette le projet en modération sans elle ; depuis mod-publish-plugin 2.1.0 ça se
  règle dans le build, plus dans les réglages de version du site. Le mod est requis des deux
  côtés : les garde-fous `ServerPlayNetworking.canSend` des deux écrans sont un message poli
  pour un client qui ne l'a pas, pas une configuration supportée.
- **Le slug Modrinth est `cobblemon-trainers-rerebleue`.** `cobblemon-trainers` appartient déjà
  à un autre projet, et la modération refuse un slug qui ne correspond pas au nom du projet.
  Le `modrinth_id` de `gradle.properties` est l'ID du projet, immuable : il ne suit pas le slug.
  Le seul endroit du dépôt qui nomme le slug est le lien de `MODRINTH.md`.
- **Le workflow exige `MODRINTH_TOKEN` avant de construire.** Sans jeton, `publishMods`
  bascule en `dryRun` et le workflow finirait vert sans rien publier.
- **Un numéro de version déjà publié sur Modrinth est refusé (409).** Une release qui échoue
  après l'upload Modrinth ne se rejoue pas sur le même tag : reprendre sur le suivant, ou
  supprimer la version depuis le tableau de bord.
- **Les dépendances Modrinth sont épinglées à la version exacte du build**, lue depuis
  `gradle.properties` (`cobblemon_version`, `fabric_api_version`, `fabric_kotlin_version`) :
  changer une version de build déplace la contrainte publiée avec elle. Modrinth accepte un ID
  de version ou un numéro, et refuse la publication si le motif ne correspond pas à exactement
  une version - une faute de frappe échoue bruyamment.
- **La release GitHub existe déjà** quand le workflow tourne. Le publisher `github` du plugin
  ne sait que *créer* une release, jamais alimenter une release existante (sauf via l'option
  `parent`, réservée aux sous-projets) : les assets passent donc par `gh release upload`.
- **`file` pointe sur `remapJar`, pas `jar`.** Le second garde les mappings nommés et planterait
  hors environnement de développement. `RemapJarTask` dérive de `org.gradle.jvm.tasks.Jar`, pas
  de `org.gradle.api.tasks.bundling.Jar` - `tasks.named<Jar>(…)` échoue avec l'import par défaut
  du Kotlin DSL.
- **Dans le bloc `publishMods`, `version` est la propriété de l'extension**, pas celle du projet :
  l'interpoler donne sa description Gradle. D'où le `modVersion` capturé au-dessus.
- Sans jeton, `publishMods` bascule en `dryRun` et écrit dans `build/mod-publish/` - c'est ce qui
  rend `./gradlew publishMods` sûr en local.

### Le pack d'exemple dans la release

`exampleDatapack` zippe `examples/cobblemonrlm` en `exemple_trainer_datapack.zip`, publié à côté
du jar. Il reste **hors du jar** : tout ce qui est sous `data/` dans le jar est un datapack que le
jeu charge pour tout le monde, donc l'embarquer ferait apparaître les dresseurs `cobblemonrlm:`
dans des mondes qui n'ont rien demandé.

Le zip **exclut `fabric.mod.json`**. Ce fichier n'existe que pour permettre de construire le
dossier en `.jar` que Fabric charge, et il nuit à un `.zip` : Fabric ignore les archives qui ne
sont pas des `.jar`, tandis que `ModsFolderPackSource` saute tout ce qui porte des métadonnées de
mod - le pack ne se chargerait donc de nulle part. Sans lui, le même zip marche dans `mods/`,
`datapacks/` et `resourcepacks/`.

## Limites connues

`ShowdownTeamParser` ne traduit pas le suffixe de forme des exports Showdown
(`Raichu-Alola`, `Rotom-Wash`) : rien ne le distingue d'une espèce dont le nom contient un
tiret (`Ho-Oh`, `Porygon-Z`, `Jangmo-o`) sans interroger le registre des espèces, et le
suffixe ne donne de toute façon pas la propriété d'une caractéristique à choix. Les formes
passent par `Aspects:`.
