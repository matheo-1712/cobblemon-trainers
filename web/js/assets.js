/*
 * The other half of a pack: what lives under assets/.
 *
 * A trainer file is text and fits in localStorage; a battle theme is three megabytes of ogg and
 * does not. The bytes live in IndexedDB, and the pack keeps only what it needs to name them -
 * kind, name, size. Nothing here is uploaded anywhere: the archive is built in the browser.
 *
 * Four kinds, and each one decides two things at once: where the file goes in the archive, and
 * what a trainer writes to reach it. The two are never typed by hand, which is the only way
 * they cannot disagree - the same reason TrainerPlace keeps a condition and its wording
 * together.
 *
 *   music          assets/<ns>/sounds/battle_music/<name>.ogg   ->  <ns>:battle_music.<name>
 *   sound          assets/<ns>/sounds/intro/<name>.ogg          ->  <ns>:intro.<name>
 *   skin           assets/<ns>/textures/trainers/<name>.png     ->  <ns>:textures/trainers/<name>.png
 *   intro_texture  assets/<ns>/textures/gui/intro/<name>.png    ->  <ns>:textures/gui/intro/<name>.png
 */

const Assets = (() => {
  const DB_NAME = 'cobblemon-trainers-editor';
  const STORE = 'assets';

  const KINDS = {
    music: {
      ext: 'ogg', accept: 'audio/ogg', folder: 'sounds/battle_music',
      l: { fr: 'Musique de combat', en: 'Battle music' },
      h: { fr: 'Un .ogg. Il est diffusé en flux et bouclé : une piste longue ne coûte rien.',
           en: 'One .ogg. It is streamed and looped: a long track costs nothing.' }
    },
    sound: {
      ext: 'ogg', accept: 'audio/ogg', folder: 'sounds/intro',
      l: { fr: 'Son d’intro', en: 'Intro sound' },
      h: { fr: 'Un impact, un éclat de verre : joué une fois, au tick d’un calque.',
           en: 'An impact, a shatter: played once, on a layer’s tick.' }
    },
    skin: {
      ext: 'png', accept: 'image/png', folder: 'textures/trainers',
      l: { fr: 'Skin de dresseur', en: 'Trainer skin' },
      h: { fr: 'Un skin de joueur ordinaire, 64 × 64. Lu par le serveur, donc le pack va dans mods/.',
           en: 'An ordinary player skin, 64 x 64. Read by the server, so the pack goes in mods/.' }
    },
    intro_texture: {
      ext: 'png', accept: 'image/png', folder: 'textures/gui/intro',
      l: { fr: 'Texture d’intro', en: 'Intro texture' },
      h: { fr: 'Une image d’un calque. Blanche, elle prend la teinte du calque.',
           en: 'A layer’s image. White, it takes the layer’s tint.' }
    }
  };

  /* ---- the store --------------------------------------------------------- */

  let db = null;

  const open = () => new Promise((resolve, reject) => {
    if (db) return resolve(db);
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(STORE)) request.result.createObjectStore(STORE);
    };
    request.onsuccess = () => { db = request.result; resolve(db); };
    request.onerror = () => reject(request.error);
  });

  const run = async (mode, work) => {
    const base = await open();
    return new Promise((resolve, reject) => {
      const transaction = base.transaction(STORE, mode);
      const request = work(transaction.objectStore(STORE));
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  };

  const put = (id, blob) => run('readwrite', (store) => store.put(blob, id));
  const get = (id) => run('readonly', (store) => store.get(id));
  const drop = (id) => run('readwrite', (store) => store.delete(id));
  const keys = () => run('readonly', (store) => store.getAllKeys());

  /* ---- names and paths --------------------------------------------------- */

  /** What a file name is allowed to be, once we have made it one. */
  const slug = (name) => name
    .replace(/\.[^.]+$/, '')
    .normalize('NFD').replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9_.-]+/g, '_')
    .replace(/^_+|_+$/g, '') || 'sans_nom';

  const kindOfFile = (file) => {
    const name = file.name.toLowerCase();
    if (name.endsWith('.ogg')) return 'music';
    if (name.endsWith('.png')) return 'skin';
    return null;
  };

  const path = (asset, namespace) =>
    `assets/${namespace}/${KINDS[asset.kind].folder}/${asset.name}.${KINDS[asset.kind].ext}`;

  /** The sounds.json entry a sound answers to, without its namespace. */
  const soundKey = (asset) =>
    (asset.kind === 'music' ? 'battle_music.' : 'intro.') + asset.name;

  /** What a pack writes in a field to reach this file. */
  const reference = (asset, namespace) => {
    if (asset.kind === 'music' || asset.kind === 'sound') return `${namespace}:${soundKey(asset)}`;
    return `${namespace}:${KINDS[asset.kind].folder}/${asset.name}.${KINDS[asset.kind].ext}`;
  };

  /** The path inside the sound archive, which drops `sounds/` and the extension. */
  const soundPath = (asset, namespace) =>
    `${namespace}:${KINDS[asset.kind].folder.replace(/^sounds\//, '')}/${asset.name}`;

  /**
   * The sounds.json of a pack, built from the sounds it holds.
   *
   * `stream` is true for a battle theme and false for a stinger, and that is not a detail: a
   * track that plays for minutes would otherwise be held in memory whole.
   */
  const soundsJson = (assets, namespace) => {
    const sounds = assets.filter((asset) => asset.kind === 'music' || asset.kind === 'sound');
    if (sounds.length === 0) return null;

    const out = {};
    sounds.forEach((asset) => {
      const entry = { sounds: [{ name: soundPath(asset, namespace), stream: asset.kind === 'music' }] };
      if (asset.subtitle) entry.subtitle = `${namespace}.subtitles.${asset.name}`;
      out[soundKey(asset)] = entry;
    });
    return out;
  };

  return {
    KINDS, slug, kindOfFile, path, reference, soundKey, soundsJson,
    put, get, drop, keys
  };
})();
