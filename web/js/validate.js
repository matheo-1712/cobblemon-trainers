/*
 * The traps the mod sets, said out loud before the pack is zipped.
 *
 * Gson drops a key it does not know without a word, and a datapack that loads is not a datapack
 * that works: most of what goes wrong in a trainer file goes wrong silently. Everything checked
 * here is a rule that exists in the mod, and each message says what the game will do rather
 * than that something is "invalid".
 *
 * Three levels: error is a file the mod will refuse or a field it will drop, warn is something
 * that loads and then does nothing, info is a consequence worth knowing.
 */

const Validate = (() => {
  const ID = /^[a-z0-9_.-]+$/;
  const PATH = /^[a-z0-9_.\-/]+$/;
  const NAMESPACED = /^[a-z0-9_.-]+:[a-z0-9_.\-/]+$/;
  const SHIPPED_INTROS = ['bw', 'rerebleue', 'kagumi', 'griff501', 'octavien29', 'theazertor',
                          'aeliothys', 'ultra_rerebleue'];
  const LAYER_TYPES = ['figure', 'text', 'image', 'fill', 'vs', 'team_balls', 'pokemon'];

  const say = (found, level, fr, en) => found.push({ level, fr, en });

  /** An id a pack wrote, either namespaced or resolved inside its own namespace. */
  const resolve = (id, namespace) => (id.includes(':') ? id : namespace + ':' + id);

  /* ---- a trainer ------------------------------------------------------- */

  const trainer = (doc, found, pack) => {
    const skin = doc.skin || {};
    if (skin.type && !skin.value) {
      say(found, 'error',
        'Le skin déclare un type sans valeur : le dresseur gardera le skin par défaut.',
        'The skin declares a type with no value: the trainer will keep the default skin.');
    }
    if (skin.type === 'texture' && skin.value) {
      if (!NAMESPACED.test(skin.value) || !skin.value.endsWith('.png')) {
        say(found, 'error',
          'Un skin texture s’écrit mon_pack:textures/trainers/red.png, namespace et .png compris.',
          'A texture skin is written my_pack:textures/trainers/red.png, namespace and .png included.');
      } else if (pack && pack.references && skin.value.startsWith(pack.namespace + ':')
                 && !pack.references('skin').includes(skin.value)) {
        say(found, 'warn',
          'Aucune image de ce nom dans le pack : ajoute le .png dans Ressources, ou le dresseur restera en Steve.',
          'No image of that name in the pack: add the .png under Assets, or the trainer stays a Steve.');
      }
      say(found, 'info',
        'Skin en image : l’image est lue par le serveur, donc le pack doit être posé dans mods/.',
        'Image skin: the image is read by the server, so the pack has to sit in mods/.');
    }
    if (skin.model && skin.type && skin.type !== 'texture') {
      say(found, 'info',
        'Le gabarit n’est lu que pour un skin texture : un profil Mojang le dit lui-même.',
        'The model is only read for a texture skin: a Mojang profile says so itself.');
    }

    const battle = doc.battle || {};
    (battle.gimmicks || []).forEach((gimmick) => {
      if (gimmick === 'dynamax') {
        say(found, 'error',
          'Le dynamax s’écrit "max" : "dynamax" n’est pas reconnu et le gimmick ne sortira jamais.',
          'Dynamax is written "max": "dynamax" is not recognised and the gimmick will never come out.');
      } else if (gimmick === 'ultra') {
        say(found, 'warn',
          '"ultra" est reconnu mais non supporté : l’ultra-explosion n’est pas jouée.',
          '"ultra" is recognised but unsupported: ultra burst is never played.');
      } else if (!['mega', 'zmove', 'max', 'terastal'].includes(gimmick)) {
        say(found, 'error',
          'Gimmick inconnu : ' + gimmick + '. Les mots sont mega, zmove, max, terastal.',
          'Unknown gimmick: ' + gimmick + '. The words are mega, zmove, max, terastal.');
      }
    });
    if ((battle.gimmicks || []).length && (battle.difficulty === 0 || battle.difficulty === 1)) {
      say(found, 'info',
        'À cette difficulté le dresseur joue presque au hasard : il aura rarement en main le coup qui vaut un gimmick.',
        'At this difficulty the trainer plays close to random: he will rarely hold the move a gimmick is worth spending on.');
    }
    if (String(battle.format || '').endsWith('_50') && battle.level) {
      say(found, 'info',
        'Le format met les deux équipes au niveau 50 : battle.level n’a plus d’effet visible.',
        'The format puts both teams at level 50: battle.level has no visible effect left.');
    }
    if (battle.music) {
      if (!NAMESPACED.test(battle.music)) {
        say(found, 'error',
          'La musique est une clé de sounds.json, namespace compris : mon_pack:battle_music.champion.',
          'The music is a sounds.json key, namespace included: my_pack:battle_music.champion.');
      } else if (pack && battle.music.startsWith(pack.namespace + ':')
                 && !(pack.references ? pack.references('music').concat(pack.references('sound')) : []).includes(battle.music)) {
        say(found, 'warn',
          'Aucune musique de ce nom dans le pack : ajoute le .ogg dans Ressources, ou le combat se fera en silence.',
          'No music of that name in the pack: add the .ogg under Assets, or the battle will run silent.');
      } else if (!battle.music.startsWith('cobblemon-trainers:')) {
        say(found, 'info',
          'Piste maison : le pack doit être posé dans mods/, la seule voie qui charge aussi assets/.',
          'Custom track: the pack has to sit in mods/, the one way that loads assets/ too.');
      }
    }
    if (battle.intro) {
      const bare = !battle.intro.includes(':');
      if (bare && !SHIPPED_INTROS.includes(battle.intro)) {
        say(found, 'warn',
          'Aucune intro du mod ne s’appelle ' + battle.intro + ' : sans namespace, l’id est cherché chez le mod. Le combat s’ouvrira sans écran.',
          'No intro of the mod is called ' + battle.intro + ': with no namespace the id is looked up in the mod. The battle will open with no screen.');
      }
      if (!bare && pack && battle.intro.startsWith(pack.namespace + ':')) {
        const path = battle.intro.slice(pack.namespace.length + 1);
        if (!pack.files.some((file) => file.kind === 'intro' && file.path === path)) {
          say(found, 'warn',
            'Ce pack ne contient pas l’intro ' + path + ' : le combat s’ouvrira sans écran.',
            'This pack has no intro named ' + path + ': the battle will open with no screen.');
        }
      }
    }

    (doc.rewards || []).forEach((reward, index) => {
      if (!reward.item || !NAMESPACED.test(reward.item)) {
        say(found, 'error',
          'Récompense #' + (index + 1) + ' : il faut un ID complet, namespace obligatoire.',
          'Reward #' + (index + 1) + ': a full id is required, namespace included.');
      }
    });
    if ((doc.rewards || []).length && (doc.rewards || []).every((reward) => reward.hidden)) {
      say(found, 'info',
        'Toutes les récompenses sont secrètes : la fiche du Battle Phone n’en montrera aucune.',
        'Every reward is hidden: the Battle Phone card will show none of them.');
    }

    const requires = doc.requires || {};
    (requires.items || []).forEach((entry) => {
      if (!entry.item || !NAMESPACED.test(entry.item)) {
        say(found, 'error',
          'Condition items : ID complet obligatoire. Un ID introuvable ferme le dresseur au lieu de l’ouvrir.',
          'items condition: a full id is required. An id that resolves to nothing closes the trainer rather than opening it.');
      }
    });
    if (requires.advancement && !NAMESPACED.test(requires.advancement)) {
      say(found, 'error',
        'Condition advancement : ID complet obligatoire.',
        'advancement condition: a full id is required.');
    }
    (requires.defeated || []).forEach((id) => {
      if (!pack) return;
      const full = resolve(id, pack.namespace);
      if (full.startsWith(pack.namespace + ':')
          && !pack.files.some((file) => file.kind === 'trainer' && pack.namespace + ':' + file.path === full)) {
        say(found, 'warn',
          'Condition defeated : ce pack ne contient aucun dresseur ' + full + '. Une faute de frappe ferme le dresseur pour tout le monde.',
          'defeated condition: this pack has no trainer ' + full + '. A typo closes the trainer for everyone.');
      }
    });

    const location = doc.location || {};
    const conditions = ['dimension', 'biome', 'structure', 'area', 'minY', 'maxY', 'time', 'weather']
      .filter((key) => location[key] !== undefined && location[key] !== '');
    if ((location.arrival || location.busy) && conditions.length === 0) {
      say(found, 'warn',
        'arrival et busy ne servent qu’à un appel, et sans condition de lieu le bouton Appeler n’apparaît pas.',
        'arrival and busy only serve a call, and with no location condition the Call button never shows.');
    }
    if (location.label && conditions.length === 0) {
      say(found, 'info',
        'Un label seul affiche le lieu sans rendre le dresseur appelable : c’est le cas d’un champion qui ne quitte pas son arène.',
        'A label alone shows the place without making the trainer callable: that is a champion who does not leave their gym.');
    }

    const cosmetics = doc.cosmetics || {};
    const worn = { ...cosmetics, ...(cosmetics.trinkets || {}) };
    delete worn.trinkets;
    Object.entries(worn).forEach(([slot, item]) => {
      if (item && !NAMESPACED.test(item)) {
        say(found, 'error',
          'Tenue (' + slot + ') : ID complet obligatoire, sinon l’emplacement reste vide.',
          'Outfit (' + slot + '): a full id is required, otherwise the slot stays empty.');
      }
    });

    const team = doc.team || [];
    if (team.length === 0) {
      say(found, 'warn',
        'Aucun Pokémon : Cobblemon refusera le combat.',
        'No Pokémon: Cobblemon will refuse the battle.');
    }
    const places = { doubles: 2, doubles_50: 2, triples: 3, triples_50: 3 }[battle.format] || 1;
    if (team.length && team.length < places) {
      say(found, 'error',
        'Ce format demande au moins ' + places + ' Pokémon de chaque côté.',
        'This format needs at least ' + places + ' Pokémon on each side.');
    }
    team.forEach((entry, index) => {
      const lines = String(entry).split('\n').map((line) => line.trim()).filter(Boolean);
      if (lines.length === 0) {
        say(found, 'error',
          'Pokémon #' + (index + 1) + ' est vide.',
          'Pokémon #' + (index + 1) + ' is empty.');
        return;
      }
      if (lines.filter((line) => line.startsWith('-')).length > 4) {
        say(found, 'warn',
          'Pokémon #' + (index + 1) + ' : plus de quatre capacités, les suivantes sont ignorées.',
          'Pokémon #' + (index + 1) + ': more than four moves, the extra ones are ignored.');
      }
      const species = lines[0].split('@')[0].trim();
      if (!species) {
        say(found, 'error',
          'Pokémon #' + (index + 1) + ' : la première ligne doit nommer une espèce.',
          'Pokémon #' + (index + 1) + ': the first line has to name a species.');
      }
      if (/-(Alola|Galar|Hisui|Paldea|Wash|Heat|Frost|Fan|Mow|Therian|Origin)\b/i.test(species)) {
        say(found, 'warn',
          'Pokémon #' + (index + 1) + ' : le suffixe de forme d’un export Showdown n’est pas lu. Les formes passent par une ligne Aspects:.',
          'Pokémon #' + (index + 1) + ': the form suffix of a Showdown export is not read. Forms go through an Aspects: line.');
      }
      if (/^Tera Type:/m.test(entry) === false && (battle.gimmicks || []).includes('terastal')) {
        say(found, 'info',
          'Pokémon #' + (index + 1) + ' sans ligne Tera Type: téracristallisera dans son type primaire.',
          'Pokémon #' + (index + 1) + ' has no Tera Type: line, so it will terastallize into its primary type.');
      }
    });
  };

  /* ---- an intro -------------------------------------------------------- */

  const intro = (doc, found, pack) => {
    const owned = (kind, id) => !pack || !pack.references
      || !id.startsWith(pack.namespace + ':') || pack.references(kind).includes(id);

    const duration = doc.duration ?? 100;
    if (duration < 20 || duration > 200) {
      say(found, 'error',
        'La durée doit tenir entre 20 et 200 ticks.',
        'The duration has to sit between 20 and 200 ticks.');
    }
    const layers = doc.layers || [];
    if (layers.length === 0) {
      say(found, 'warn',
        'Aucun calque : l’écran s’ouvrirait sur du vide.',
        'No layer: the screen would open on nothing.');
    }
    let last = 0;
    layers.forEach((layer, index) => {
      const name = 'Calque #' + (index + 1);
      const label = 'Layer #' + (index + 1);
      if (!LAYER_TYPES.includes(layer.type)) {
        say(found, 'error',
          name + ' : type inconnu (' + layer.type + '). Il sera retiré au chargement.',
          label + ': unknown type (' + layer.type + '). It will be dropped at load.');
        return;
      }
      if (layer.type === 'image' && !layer.texture) {
        say(found, 'error',
          name + ' : une image sans texture est retirée au chargement.',
          label + ': an image with no texture is dropped at load.');
      }
      if (layer.type === 'image' && layer.texture && !NAMESPACED.test(layer.texture)) {
        say(found, 'error',
          name + ' : la texture s’écrit mon_pack:textures/gui/intro/logo.png.',
          label + ': the texture is written my_pack:textures/gui/intro/logo.png.');
      }
      if (layer.type === 'image' && layer.texture && NAMESPACED.test(layer.texture)
          && !owned('intro_texture', layer.texture)) {
        say(found, 'warn',
          name + ' : aucune image de ce nom dans le pack, le calque ne dessinera rien.',
          label + ': no image of that name in the pack, the layer will draw nothing.');
      }
      if (layer.type === 'pokemon') {
        say(found, 'info',
          name + ' montre un Pokémon de l’équipe avant le combat. Aucune intro du mod ne le fait.',
          label + ' shows a team Pokémon before the battle. None of the mod’s own intros does.');
      }
      const end = (layer.at ?? 0) + (layer.for ?? 12);
      last = Math.max(last, end);
      if (end > duration) {
        say(found, 'warn',
          name + ' entre encore quand l’écran se referme (' + end + ' > ' + duration + ').',
          label + ' is still entering when the screen closes (' + end + ' > ' + duration + ').');
      }
      if (layer.sound && !NAMESPACED.test(layer.sound)) {
        say(found, 'error',
          name + ' : le son est une clé de sounds.json, namespace compris.',
          label + ': the sound is a sounds.json key, namespace included.');
      } else if (layer.sound && !owned('sound', layer.sound) && !owned('music', layer.sound)) {
        say(found, 'warn',
          name + ' : aucun son de ce nom dans le pack, le calque entrera en silence.',
          label + ': no sound of that name in the pack, the layer will come in silent.');
      }
    });
    if (layers.length && duration - last < 10) {
      say(found, 'info',
        'La dernière entrée se termine juste avant la fin : le joueur ne peut passer l’écran qu’à ce moment-là.',
        'The last entrance lands right at the end: the player cannot skip the screen before that.');
    }
  };

  /* ---- the rest -------------------------------------------------------- */

  const advancement = (doc, found) => {
    const criteria = Object.values(doc.criteria || {});
    if (criteria.length === 0) {
      say(found, 'error', 'Aucun critère : l’advancement s’obtiendrait tout seul.',
          'No criteria: the advancement would be granted on its own.');
    }
    criteria.forEach((criterion) => {
      const conditions = criterion.conditions || {};
      if (conditions.trainer && !NAMESPACED.test(conditions.trainer)) {
        say(found, 'error',
          'Le dresseur d’un critère s’écrit avec son namespace : mon_pack:champions/pierre.',
          'A criterion’s trainer is written with its namespace: my_pack:champions/brock.');
      }
    });
  };

  const file = (entry, pack) => {
    const found = [];
    if (!PATH.test(entry.path)) {
      say(found, 'error',
        'Le chemin ne peut contenir que minuscules, chiffres, _ - . et /.',
        'The path may only hold lowercase letters, digits, _ - . and /.');
    }
    if (entry.kind === 'trainer' && entry.path.split('/').pop() === 'category') {
      say(found, 'error',
        'category est le seul nom de fichier réservé : il décrit un dossier, jamais un dresseur.',
        'category is the one reserved file name: it describes a folder, never a trainer.');
    }
    if (entry.kind === 'trainer') trainer(entry.doc, found, pack);
    if (entry.kind === 'intro') intro(entry.doc, found, pack);
    if (entry.kind === 'advancement') advancement(entry.doc, found);
    return found;
  };

  const pack = (state) => {
    const found = [];
    if (!ID.test(state.namespace)) {
      say(found, 'error',
        'Le namespace ne peut contenir que minuscules, chiffres, _ - et .',
        'The namespace may only hold lowercase letters, digits, _ - and .');
    }
    const seen = new Set();
    state.files.forEach((entry) => {
      const key = entry.kind + '/' + entry.path;
      if (seen.has(key)) {
        say(found, 'error',
          'Deux fichiers portent le même chemin : ' + entry.path + '. Le second écrase le premier.',
          'Two files share the same path: ' + entry.path + '. The second one overwrites the first.');
      }
      seen.add(key);
    });

    const assets = state.assets || [];
    const languages = Object.keys(state.lang || {});
    const translated = languages.some((code) => Object.keys(state.lang[code] || {}).length > 0);

    if ((assets.length || translated) && state.archive !== 'jar') {
      say(found, 'info',
        'Ce pack porte des assets (musique, skin, traduction) : seul mods/ charge les deux moitiés en un fichier.',
        'This pack carries assets (music, skin, translation): only mods/ loads both halves from one file.');
    }
    if (assets.length && state.archive === 'jar') {
      say(found, 'info',
        'Archive .jar : Fabric la charge comme un mod, donc elle va dans mods/ et nulle part ailleurs.',
        'A .jar archive: Fabric loads it as a mod, so it goes in mods/ and nowhere else.');
    }

    if (state.usedKeys) {
      const missing = state.usedKeys().filter((key) =>
        languages.every((code) => !(state.lang[code] || {})[key]));
      if (missing.length) {
        say(found, 'warn',
          missing.length + ' clé(s) de traduction sans texte : Minecraft affichera la clé telle quelle.',
          missing.length + ' translation key(s) with no text: Minecraft will show the key as it is.');
      }
      languages.forEach((code) => {
        const done = state.usedKeys().filter((key) => (state.lang[code] || {})[key]).length;
        const total = state.usedKeys().length;
        if (total && done && done < total) {
          say(found, 'info',
            code + ' : ' + done + ' clé(s) sur ' + total + '. Une clé sans texte retombe sur la clé elle-même, pas sur une autre langue.',
            code + ': ' + done + ' key(s) out of ' + total + '. A key with no text falls back to the key itself, not to another language.');
        }
      });
    }

    const total = assets.reduce((sum, asset) => sum + (asset.size || 0), 0);
    if (total > 40 * 1024 * 1024) {
      say(found, 'warn',
        'Le pack pèse ' + Math.round(total / 1048576) + ' Mo : un serveur qui le distribue en resource-pack fera attendre ses joueurs.',
        'The pack weighs ' + Math.round(total / 1048576) + ' MB: a server handing it out as a resource pack will keep its players waiting.');
    }
    return found;
  };

  return { file, pack, SHIPPED_INTROS };
})();
