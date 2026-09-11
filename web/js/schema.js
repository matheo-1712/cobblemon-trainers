/*
 * The fields of every file the mod reads, in one place.
 *
 * This is the single source the whole editor is built from: the forms, the labels in both
 * languages, the defaults that decide what gets written out, and the links back to the docs.
 * Adding a field to the mod means adding one entry here - nowhere else.
 *
 * A field:
 *   k       key in the JSON
 *   t       str | text | num | bool | sel | strnull | strlist | objlist | group
 *           | tags | area | offset | color | texture
 *   def     default value; a value equal to it is left out of the output
 *   l / h   label and hint, both { fr, en }
 *   doc     page and anchor in the documentation, appended to the docs base url
 */

const SCHEMA = (() => {
  const t = (fr, en) => ({ fr, en });

  const SKIN = {
    k: 'skin', t: 'group', doc: 'DATAPACK.md#skins',
    l: t('Skin', 'Skin'),
    h: t('Sans bloc skin, le dresseur est un Steve.', 'With no skin block the trainer is a Steve.'),
    fields: [
      { k: 'type', t: 'sel', def: '', l: t('Type', 'Type'),
        options: [
          ['', t('Aucun (Steve)', 'None (Steve)')],
          ['player_username', t('Pseudo de joueur', 'Player username')],
          ['player_uuid', t('UUID de joueur', 'Player UUID')],
          ['texture', t('Image du pack', 'Pack texture')]
        ] },
      { k: 'value', t: 'str', def: '', l: t('Valeur', 'Value'),
        h: t('Un pseudo, un UUID, ou mon_pack:textures/trainers/red.png',
             'A username, a UUID, or my_pack:textures/trainers/red.png') },
      { k: 'model', t: 'sel', def: 'default', l: t('Gabarit', 'Model'),
        h: t('Seulement pour type: texture - un profil Mojang le dit lui-même.',
             'For type: texture only - a Mojang profile says so itself.'),
        options: [['default', t('Steve', 'Steve')], ['slim', t('Alex (slim)', 'Alex (slim)')]] }
    ]
  };

  const BATTLE = {
    k: 'battle', t: 'group', doc: 'DATAPACK.md#battle',
    l: t('Combat', 'Battle'),
    fields: [
      { k: 'level', t: 'num', def: 1, min: 1, max: 100, l: t('Niveau', 'Level'),
        h: t('Niveau des Pokémon qui n’ont pas de ligne Level:.',
             'Level of the Pokémon with no Level: line of their own.') },
      { k: 'format', t: 'sel', def: 'singles', l: t('Format', 'Format'),
        options: [
          ['singles', t('Simple (1v1)', 'Singles (1v1)')],
          ['doubles', t('Double (2v2)', 'Doubles (2v2)')],
          ['triples', t('Triple (3v3)', 'Triples (3v3)')],
          ['singles_50', t('Simple, niveau 50', 'Singles, level 50')],
          ['doubles_50', t('Double, niveau 50', 'Doubles, level 50')],
          ['triples_50', t('Triple, niveau 50', 'Triples, level 50')]
        ] },
      { k: 'difficulty', t: 'sel', def: 5, doc: 'DIFFICULTE.md', l: t('Difficulté', 'Difficulty'),
        h: t('L’intelligence de l’IA, pas la puissance de l’équipe.',
             'How well the AI plays, not how strong the team is.'),
        options: [
          [0, t('0 - au hasard', '0 - random')],
          [1, t('1 - débutant', '1 - beginner')],
          [2, t('2 - occasionnel', '2 - casual')],
          [3, t('3 - correct', '3 - decent')],
          [4, t('4 - solide', '4 - solid')],
          [5, t('5 - sérieux', '5 - serious')]
        ] },
      { k: 'healParty', t: 'bool', def: true, l: t('Soigner son équipe', 'Heal its party'),
        h: t('false fait persister dégâts et PP d’un combat à l’autre.',
             'false carries damage and PP from one battle to the next.') },
      { k: 'music', t: 'strnull', def: '', l: t('Musique', 'Music'),
        h: t('Clé de sounds.json, pas un chemin de fichier. Vide = la piste du mod.',
             'A sounds.json key, not a file path. Empty = the mod’s own track.') },
      { k: 'gimmicks', t: 'tags', def: [], doc: 'GIMMICKS.md', l: t('Gimmicks', 'Gimmicks'),
        options: [
          ['mega', t('Méga-évolution', 'Mega evolution')],
          ['zmove', t('Capacité Z', 'Z-Move')],
          ['max', t('Dynamax', 'Dynamax')],
          ['terastal', t('Téracristal', 'Terastal')]
        ],
        h: t('Le Pokémon doit porter la gemme ou le cristal, dans son entrée d’équipe.',
             'The Pokémon must carry the stone or crystal, in its team entry.') },
      { k: 'intro', t: 'intro', def: '', doc: 'INTROS.md', l: t('Écran de versus', 'Versus screen'),
        h: t('L’id d’une intro : bw, kagumi... ou mon_pack:arene.',
             'An intro id: bw, kagumi... or my_pack:arena.') }
    ]
  };

  const MESSAGES = {
    k: 'messages', t: 'group', doc: 'DATAPACK.md#messages',
    l: t('Répliques', 'Messages'),
    h: t('Tout passe par la boîte de dialogue, rien par le chat. Chacune est facultative.',
         'All of it goes through the dialogue box, none through chat. Each one is optional.'),
    fields: [
      { k: 'greeting', t: 'text', def: '', l: t('Accueil', 'Greeting'),
        h: t('Au clic droit, au-dessus des boutons.', 'On right click, above the buttons.') },
      { k: 'start', t: 'text', def: '', l: t('Début du combat', 'Battle start'),
        h: t('Après avoir accepté.', 'After accepting.') },
      { k: 'decline', t: 'text', def: '', l: t('Refus', 'Decline'),
        h: t('Après le bouton Annuler - jamais après Échap.', 'After the Cancel button - never after Escape.') },
      { k: 'win', t: 'text', def: '', l: t('Le joueur gagne', 'The player wins') },
      { k: 'lose', t: 'text', def: '', l: t('Le joueur perd', 'The player loses') }
    ]
  };

  const PROGRESS = {
    k: 'progress', t: 'group', doc: 'DATAPACK.md#progress',
    l: t('Progression', 'Progress'),
    fields: [
      { k: 'rematch', t: 'sel', def: 'unlimited', l: t('Revanche', 'Rematch'),
        options: [
          ['unlimited', t('Autant de fois qu’on veut', 'As many times as you like')],
          ['never', t('Une seule fois', 'Once and never again')]
        ] },
      { k: 'listed', t: 'bool', def: true, l: t('Visible dans le Battle Phone', 'Listed in the Battle Phone') }
    ]
  };

  const REWARDS = {
    k: 'rewards', t: 'objlist', def: [], doc: 'DATAPACK.md#revanches-et-récompenses',
    l: t('Récompenses', 'Rewards'),
    h: t('Remises au vainqueur, et affichées sur sa fiche avant même le combat.',
         'Handed to the winner, and shown on their card before the battle.'),
    fields: [
      { k: 'item', t: 'str', def: '', l: t('Objet', 'Item'),
        h: t('ID complet, namespace obligatoire.', 'Full id, namespace required.') },
      { k: 'count', t: 'num', def: 1, min: 1, max: 6400, l: t('Quantité', 'Count') },
      { k: 'hidden', t: 'bool', def: false, l: t('Secrète', 'Hidden'),
        h: t('Pas annoncée dans le Battle Phone.', 'Not announced in the Battle Phone.') },
      { k: 'firstWinOnly', t: 'bool', def: false, l: t('Première victoire seulement', 'First win only') }
    ]
  };

  const REQUIRES = {
    k: 'requires', t: 'group', doc: 'DATAPACK.md#conditions-pour-combattre',
    l: t('Conditions pour le combattre', 'Conditions to fight'),
    h: t('Toutes s’additionnent : ce ne sont jamais des alternatives.',
         'They all add up: never alternatives.'),
    fields: [
      { k: 'defeated', t: 'strlist', def: [], l: t('Dresseurs à battre', 'Trainers to defeat'),
        h: t('Sans namespace, l’ID est lu dans le pack du dresseur qui l’exige.',
             'With no namespace, the id is read in the pack of the trainer asking for it.') },
      { k: 'victories', t: 'group', l: t('Nombre de victoires', 'Number of victories'),
        fields: [
          { k: 'count', t: 'num', def: null, min: 0, l: t('Combien', 'How many'),
            h: t('Vide = tous ceux du groupe.', 'Empty = every trainer of the group.') },
          { k: 'pack', t: 'str', def: '', l: t('Limité au pack', 'Limited to pack') },
          { k: 'category', t: 'str', def: '', l: t('Limité à la catégorie', 'Limited to category') }
        ] },
      { k: 'items', t: 'objlist', def: [], l: t('Objets à avoir sur soi', 'Items to carry'),
        h: t('Jamais consommés.', 'Never consumed.'),
        fields: [
          { k: 'item', t: 'str', def: '', l: t('Objet', 'Item') },
          { k: 'count', t: 'num', def: 1, min: 1, l: t('Quantité', 'Count') }
        ] },
      { k: 'party', t: 'objlist', def: [], l: t('Pokémon dans l’équipe', 'Pokémon in the party'),
        h: t('S’écrit comme pour /pokespawn : staraptor shiny=true.',
             'Written as for /pokespawn: staraptor shiny=true.'),
        fields: [
          { k: 'pokemon', t: 'str', def: '', l: t('Pokémon', 'Pokémon') },
          { k: 'count', t: 'num', def: 1, min: 1, l: t('Combien', 'How many') }
        ] },
      { k: 'advancement', t: 'str', def: '', l: t('Advancement obtenu', 'Advancement earned') },
      { k: 'hidden', t: 'bool', def: true, l: t('Caché tant qu’il est verrouillé', 'Hidden while locked') },
      { k: 'message', t: 'text', def: '', l: t('Ce qu’il répond', 'What he answers') }
    ]
  };

  const LOCATION = {
    k: 'location', t: 'group', doc: 'SPAWNING.md',
    l: t('Lieu et appel', 'Location and call'),
    h: t('Au moins une condition rend le dresseur appelable depuis le Battle Phone. Un label seul se contente de dire où il est.',
         'One condition makes the trainer callable from the Battle Phone. A label alone only says where he is.'),
    fields: [
      { k: 'dimension', t: 'str', def: '', l: t('Dimension', 'Dimension'),
        h: t('minecraft:the_nether', 'minecraft:the_nether') },
      { k: 'biome', t: 'str', def: '', l: t('Biome', 'Biome'),
        h: t('minecraft:desert, ou #minecraft:is_desert pour un tag.', 'minecraft:desert, or #minecraft:is_desert for a tag.') },
      { k: 'structure', t: 'str', def: '', l: t('Structure', 'Structure'),
        h: t('minecraft:village_desert, ou #minecraft:village.', 'minecraft:village_desert, or #minecraft:village.') },
      { k: 'area', t: 'area', l: t('Zone (x, z)', 'Area (x, z)') },
      { k: 'minY', t: 'num', def: null, l: t('Y minimum', 'Minimum Y') },
      { k: 'maxY', t: 'num', def: null, l: t('Y maximum', 'Maximum Y') },
      { k: 'time', t: 'sel', def: '', l: t('Moment', 'Time'),
        options: [['', t('N’importe quand', 'Any time')], ['day', t('Jour', 'Day')], ['night', t('Nuit', 'Night')]] },
      { k: 'weather', t: 'sel', def: '', l: t('Temps', 'Weather'),
        options: [['', t('N’importe lequel', 'Any')], ['clear', t('Dégagé', 'Clear')], ['rain', t('Pluie', 'Rain')], ['thunder', t('Orage', 'Thunder')]] },
      { k: 'label', t: 'text', def: '', l: t('Lieu affiché', 'Displayed place'),
        h: t('Remplace la description automatique du Battle Phone.', 'Replaces the Battle Phone’s own wording.') },
      { k: 'arrival', t: 'text', def: '', l: t('À l’arrivée', 'On arrival'),
        h: t('Reçoit trois arguments : x, y, z. Le nom du dresseur est déjà mis autour.',
             'Gets three arguments: x, y, z. The trainer’s name is already wrapped around it.') },
      { k: 'busy', t: 'text', def: '', l: t('Déjà occupé', 'Already busy') }
    ]
  };

  const TRINKET_SLOTS = [
    ['face', t('Visage', 'Face'), t('Lunettes de Max, tiare de Lisia', 'Maxie’s glasses, Lisia’s tiara')],
    ['chest', t('Torse / cou', 'Chest / neck'), t('Charme de Diantha, ancre d’Arthur', 'Diantha’s charm, Archie’s anchor')],
    ['wrist', t('Poignet', 'Wrist'), t('Bracelet méga, gant de Korrina', 'Mega bracelet, Korrina’s glove')],
    ['forearm', t('Avant-bras', 'Forearm'), t('Z-Ring', 'Z-Ring')],
    ['hand', t('Main forte', 'Main hand'), t('Dynamax Band, Omni Ring', 'Dynamax Band, Omni Ring')],
    ['belt', t('Ceinture', 'Belt'), t('Orbe Tera', 'Tera Orb')],
    ['ankle', t('Cheville', 'Ankle'), t('Chevillère de Zinnia', 'Zinnia’s anklet')]
  ];

  const COSMETICS = {
    k: 'cosmetics', t: 'group', doc: 'COSMETIQUES.md',
    l: t('Tenue', 'Outfit'),
    h: t('De l’apparence et rien d’autre : ni points d’armure, ni drop, ni effet en combat.',
         'Looks and nothing else: no armour points, no drops, no effect in battle.'),
    fields: [
      { k: 'head', t: 'str', def: '', l: t('Tête', 'Head') },
      { k: 'chest', t: 'str', def: '', l: t('Torse', 'Chest') },
      { k: 'legs', t: 'str', def: '', l: t('Jambes', 'Legs') },
      { k: 'feet', t: 'str', def: '', l: t('Pieds', 'Feet') },
      { k: 'mainHand', t: 'str', def: '', l: t('Main forte', 'Main hand') },
      { k: 'offHand', t: 'str', def: '', l: t('Autre main', 'Off hand') },
      { k: 'trinkets', t: 'group', l: t('Accessoires', 'Trinkets'),
        h: t('Un endroit du corps, pas un usage : n’importe quel objet peut y aller.',
             'A place on the body, not a use: any item can go there.'),
        fields: TRINKET_SLOTS.map(([k, l, h]) => ({ k, t: 'str', def: '', l, h })) }
    ]
  };

  const TRAINER = [
    { k: 'name', t: 'str', def: 'Trainer', doc: 'DATAPACK.md#tous-les-champs', l: t('Nom', 'Name'),
      h: t('Affiche au-dessus du dresseur. Une cle de traduction marche aussi.',
           'Shown above the trainer. A translation key works too.') },
    SKIN, BATTLE, MESSAGES, PROGRESS, REWARDS, REQUIRES, LOCATION, COSMETICS
  ];

  const CATEGORY = [
    { k: 'name', t: 'str', def: '', doc: 'DATAPACK.md#catégories', l: t('Nom affiché', 'Displayed name'),
      h: t('Par défaut, le nom du dossier.', 'The folder name by default.') },
    { k: 'order', t: 'num', def: null, l: t('Ordre', 'Order'),
      h: t('Le plus petit en haut. Sans valeur, la catégorie passe après les autres.',
           'Smallest first. With no value, the category comes after the others.') }
  ];

  /* ---- Intros ---------------------------------------------------------- */

  const INTRO_FILE = [
    { k: 'duration', t: 'num', def: 100, min: 20, max: 200, doc: 'INTROS.md#le-fichier',
      l: t('Durée (ticks)', 'Duration (ticks)'), h: t('20 ticks = 1 seconde.', '20 ticks = 1 second.') },
    { k: 'fadeIn', t: 'num', def: 4, min: 0, l: t('Fondu d’entrée', 'Fade in') },
    { k: 'fadeOut', t: 'num', def: 8, min: 0, l: t('Fondu de sortie', 'Fade out') }
  ];

  const LAYER_COMMON = [
    { k: 'anchor', t: 'sel', def: 'center', l: t('Ancre', 'Anchor'),
      options: [
        ['top-left', t('Haut gauche', 'Top left')], ['top', t('Haut', 'Top')], ['top-right', t('Haut droite', 'Top right')],
        ['left', t('Gauche', 'Left')], ['center', t('Centre', 'Center')], ['right', t('Droite', 'Right')],
        ['bottom-left', t('Bas gauche', 'Bottom left')], ['bottom', t('Bas', 'Bottom')], ['bottom-right', t('Bas droite', 'Bottom right')]
      ] },
    { k: 'offset', t: 'offset', l: t('Décalage', 'Offset'),
      h: t('En pixels de 640 x 360.', 'In pixels of 640 x 360.') },
    { k: 'at', t: 'num', def: 0, min: 0, l: t('Entre au tick', 'Enters at tick') },
    { k: 'for', t: 'num', def: 12, min: 1, l: t('Pendant', 'Over') },
    { k: 'from', t: 'sel', def: 'fade', l: t('Entrée', 'Entrance'),
      options: [
        ['fade', t('Fondu', 'Fade')], ['left', t('Depuis la gauche', 'From the left')], ['right', t('Depuis la droite', 'From the right')],
        ['top', t('Depuis le haut', 'From the top')], ['bottom', t('Depuis le bas', 'From the bottom')],
        ['pop', t('Pop', 'Pop')], ['none', t('Sans façon', 'Plain')]
      ] },
    { k: 'ease', t: 'sel', def: 'out', l: t('Courbe', 'Easing'),
      options: [['out', t('Ralentit en arrivant', 'Slows on arrival')], ['in', t('Accélère', 'Speeds up')], ['linear', t('Linéaire', 'Linear')]] },
    { k: 'alpha', t: 'num', def: 1, min: 0, max: 1, step: 0.01, l: t('Opacité', 'Opacity') },
    { k: 'sound', t: 'str', def: '', l: t('Son', 'Sound'),
      h: t('Clé de sounds.json, joué une fois au tick d’entrée.', 'A sounds.json key, played once on the entrance tick.') },
    { k: 'volume', t: 'num', def: 1, min: 0, step: 0.1, l: t('Volume', 'Volume') },
    { k: 'pitch', t: 'num', def: 1, min: 0.5, max: 2, step: 0.1, l: t('Hauteur', 'Pitch') }
  ];

  const WHO = { k: 'who', t: 'sel', def: 'trainer', l: t('Qui', 'Who'),
    options: [['trainer', t('Le dresseur', 'The trainer')], ['player', t('Le joueur', 'The player')]] };

  const LAYERS = {
    fill: { l: t('Aplat', 'Fill'), fields: [
      { k: 'color', t: 'color', def: '#000000', l: t('Couleur', 'Colour') },
      { k: 'width', t: 'num', def: null, l: t('Largeur', 'Width'), h: t('Vide = tout l’écran.', 'Empty = the whole screen.') },
      { k: 'height', t: 'num', def: null, l: t('Hauteur', 'Height') },
      { k: 'slant', t: 'num', def: 0, step: 0.05, l: t('Penchant', 'Slant'), h: t('0 est un rectangle.', '0 is a rectangle.') }
    ] },
    figure: { l: t('Figure', 'Figure'), fields: [
      WHO,
      { k: 'height', t: 'num', def: 96, l: t('Hauteur', 'Height') },
      { k: 'yaw', t: 'num', def: 0, l: t('Rotation', 'Yaw'), h: t('0 regarde le joueur.', '0 looks at the player.') },
      { k: 'tilt', t: 'num', def: 0, l: t('Inclinaison', 'Tilt') }
    ] },
    text: { l: t('Texte', 'Text'), fields: [
      { k: 'value', t: 'str', def: '', l: t('Texte', 'Text'),
        h: t('Marques : %name%, %category%, %level%, %team%, %player%.', 'Marks: %name%, %category%, %level%, %team%, %player%.') },
      { k: 'size', t: 'num', def: 1, min: 0.1, step: 0.1, l: t('Taille', 'Size') },
      { k: 'color', t: 'color', def: '#FFFFFF', l: t('Couleur', 'Colour') },
      { k: 'shadow', t: 'bool', def: true, l: t('Ombre portée', 'Drop shadow') }
    ] },
    image: { l: t('Image', 'Image'), fields: [
      { k: 'texture', t: 'texture', def: '', l: t('Texture', 'Texture'),
        h: t('mon_pack:textures/gui/intro/logo.png - lue par le client, donc sous assets/.',
             'my_pack:textures/gui/intro/logo.png - read by the client, so under assets/.') },
      { k: 'width', t: 'num', def: null, l: t('Largeur', 'Width') },
      { k: 'height', t: 'num', def: null, l: t('Hauteur', 'Height') },
      { k: 'color', t: 'color', def: '#FFFFFF', l: t('Teinte', 'Tint'),
        h: t('Les textures livrées sont blanches, donc teintables.', 'The shipped textures are white, so they take a tint.') }
    ] },
    vs: { l: t('VS', 'VS'), fields: [
      { k: 'size', t: 'num', def: 1, min: 0.1, step: 0.5, l: t('Taille', 'Size'),
        h: t('4 est la taille de l’intro du mod.', '4 is the size in the mod’s own intro.') },
      { k: 'color', t: 'color', def: '#FFF0C0', l: t('Couleur', 'Colour') }
    ] },
    team_balls: { l: t('Poké Balls', 'Poké Balls'), fields: [
      WHO,
      { k: 'size', t: 'num', def: 1, min: 0.1, step: 0.1, l: t('Taille', 'Size') },
      { k: 'gap', t: 'num', def: 3, l: t('Écart', 'Gap') },
      { k: 'slots', t: 'num', def: 6, min: 1, max: 6, l: t('Emplacements', 'Slots') },
      { k: 'empty', t: 'bool', def: true, l: t('Montrer les vides', 'Show the empty ones') }
    ] },
    pokemon: { l: t('Pokémon', 'Pokémon'), fields: [
      { k: 'slot', t: 'num', def: 1, min: 1, max: 6, l: t('Rang dans l’équipe', 'Party slot') },
      { k: 'height', t: 'num', def: 64, l: t('Hauteur', 'Height') },
      { k: 'yaw', t: 'num', def: 0, l: t('Rotation', 'Yaw') }
    ] }
  };

  return { TRAINER, CATEGORY, INTRO_FILE, LAYER_COMMON, LAYERS, TRINKET_SLOTS };
})();
