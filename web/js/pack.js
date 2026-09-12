/*
 * The pack: the files being written, where they go in the archive, and how they come back.
 *
 * A pack is one namespace and a list of files. What decides a trainer's id is its path - the
 * folder it sits in is its category, exactly as in the mod - so the path is the one thing the
 * editor never generates for you.
 *
 * The layout written out is the one of docs/DATAPACK.md, both halves of it:
 *   pack.mcmeta
 *   data/<ns>/cobblemontrainers/trainers/<path>.json
 *   data/<ns>/cobblemontrainers/trainers/<folder>/category.json
 *   data/<ns>/cobblemontrainers/intro/<path>.json
 *   data/<ns>/advancement/<path>.json
 *   assets/<ns>/sounds.json          - written from the sounds the pack holds
 *   assets/<ns>/sounds/...           - the .ogg themselves
 *   assets/<ns>/textures/...         - skins and intro images
 *   assets/<ns>/lang/<code>.json     - every translation key the pack uses
 *
 * An archive carrying assets/ only loads whole from `mods/`, which is why pack.mcmeta always
 * declares the two pack formats as a range: a pack served from both sides is otherwise shown
 * as incompatible in the resource pack screen.
 */

const Pack = (() => {
  const KEY = 'ct-pack';
  const ROOT = 'cobblemontrainers';
  const DATA_FORMAT = 48;
  const ASSET_FORMAT = 34;

  const state = {
    namespace: 'mon_pack',
    description: 'Mes dresseurs',
    files: [],
    /** What lives under assets/: metadata only, the bytes being in IndexedDB. */
    assets: [],
    /** Translation keys, by language code: { fr_fr: { key: text } }. */
    lang: { fr_fr: {}, en_us: {} },
    /** zip loads from the three places; jar carries a fabric.mod.json and is for mods/. */
    archive: 'zip',
    selected: null,
    nextId: 1
  };

  const listeners = [];
  const changed = () => {
    save();
    listeners.forEach((fn) => fn(state));
  };

  /* ---- where a file lives ---------------------------------------------- */

  const pathOf = (entry) => {
    switch (entry.kind) {
      case 'trainer': return `data/${state.namespace}/${ROOT}/trainers/${entry.path}.json`;
      case 'category': return `data/${state.namespace}/${ROOT}/trainers/${entry.path}/category.json`;
      case 'intro': return `data/${state.namespace}/${ROOT}/intro/${entry.path}.json`;
      case 'advancement': return `data/${state.namespace}/advancement/${entry.path}.json`;
      default: return entry.path;
    }
  };

  /** The id a pack and the mod would agree on, `trainers/` not counted. */
  const idOf = (entry) => {
    if (entry.kind === 'category') return `${state.namespace}:${entry.path}`;
    return `${state.namespace}:${entry.path}`;
  };

  /* ---- starting points -------------------------------------------------- */

  const blank = {
    trainer: () => ({
      name: 'Nouveau dresseur',
      skin: { type: 'player_username', value: 'RereBleue' },
      battle: { level: 25 },
      messages: { greeting: '' },
      team: ['Pikachu\nAbility: Static\nLevel: 25\n- Thunderbolt\n- Quick Attack']
    }),
    intro: () => ({
      duration: 100,
      fadeIn: 4,
      fadeOut: 8,
      layers: [
        { type: 'fill', color: '#05060D', alpha: 0.88, from: 'fade', for: 4 },
        { type: 'fill', color: '#2F6FBF', alpha: 0.55, slant: 0.35, width: 128, offset: [-128, 0], from: 'left', for: 18 },
        { type: 'fill', color: '#BF3A3A', alpha: 0.55, slant: 0.35, width: 128, offset: [128, 0], from: 'right', for: 18 },
        { type: 'figure', who: 'player', height: 150, yaw: 22, offset: [-128, 0], from: 'left', for: 18 },
        { type: 'figure', who: 'trainer', height: 150, yaw: -22, offset: [128, 0], from: 'right', for: 18 },
        { type: 'text', value: '%player%', offset: [-128, -95], at: 18, for: 6, from: 'fade' },
        { type: 'text', value: '%name%', offset: [128, -95], at: 18, for: 6, from: 'fade' },
        { type: 'vs', size: 4, at: 18, for: 6, from: 'pop' }
      ]
    }),
    category: () => ({ name: '', order: 1 }),
    advancement: () => ({
      display: {
        icon: { id: 'cobblemon:poke_ball' },
        title: 'Mon premier badge',
        description: 'Battre le champion.',
        frame: 'task'
      },
      criteria: {
        defeated: {
          trigger: 'cobblemon-trainers:trainer_defeated',
          conditions: { trainer: `${state.namespace}:champions/erika` }
        }
      },
      requirements: [['defeated']]
    })
  };

  /* ---- the list --------------------------------------------------------- */

  const add = (kind, path, doc) => {
    const entry = {
      key: `f${state.nextId++}`,
      kind,
      path: path || suggest(kind),
      doc: doc || blank[kind]()
    };
    state.files.push(entry);
    state.selected = entry.key;
    changed();
    return entry;
  };

  const suggest = (kind) => {
    const base = { trainer: 'nouveau_dresseur', intro: 'nouvelle_intro', category: 'champions', advancement: 'nouvel_advancement' }[kind];
    let path = base;
    let n = 2;
    while (state.files.some((file) => file.kind === kind && file.path === path)) {
      path = `${base}_${n++}`;
    }
    return path;
  };

  const remove = (key) => {
    const index = state.files.findIndex((file) => file.key === key);
    if (index < 0) return;
    state.files.splice(index, 1);
    if (state.selected === key) {
      state.selected = state.files.length ? state.files[Math.min(index, state.files.length - 1)].key : null;
    }
    changed();
  };

  const duplicate = (key) => {
    const entry = state.files.find((file) => file.key === key);
    if (!entry) return;
    add(entry.kind, suggest(entry.kind), JSON.parse(JSON.stringify(entry.doc)));
  };

  const select = (key) => {
    state.selected = key;
    changed();
  };

  const current = () => state.files.find((file) => file.key === state.selected) || null;

  /** The order the Battle Phone would show: category folders first, then the root. */
  const sorted = () => [...state.files].sort((a, b) => {
    const kinds = ['trainer', 'category', 'intro', 'advancement'];
    if (a.kind !== b.kind) return kinds.indexOf(a.kind) - kinds.indexOf(b.kind);
    const depth = (file) => (file.path.includes('/') ? 0 : 1);
    if (depth(a) !== depth(b)) return depth(a) - depth(b);
    return a.path.localeCompare(b.path);
  });

  /* ---- saving ----------------------------------------------------------- */

  const save = () => {
    try {
      localStorage.setItem(KEY, JSON.stringify({
        namespace: state.namespace,
        description: state.description,
        files: state.files,
        assets: state.assets,
        lang: state.lang,
        archive: state.archive,
        selected: state.selected,
        nextId: state.nextId
      }));
    } catch (e) { /* a private window, or a pack larger than the quota */ }
  };

  const restore = () => {
    try {
      const saved = JSON.parse(localStorage.getItem(KEY) || 'null');
      if (!saved || !Array.isArray(saved.files)) return false;
      Object.assign(state, saved);
      // A pack saved before assets existed carries neither, and neither may be undefined.
      state.assets = state.assets || [];
      state.lang = state.lang || { fr_fr: {}, en_us: {} };
      state.archive = state.archive || 'zip';
      return state.files.length > 0 || state.assets.length > 0;
    } catch (e) {
      return false;
    }
  };

  const clear = async () => {
    for (const asset of state.assets) {
      try { await Assets.drop(asset.id); } catch (e) { /* gone already */ }
    }
    state.files = [];
    state.assets = [];
    state.lang = { fr_fr: {}, en_us: {} };
    state.selected = null;
    state.nextId = 1;
    changed();
  };

  /* ---- what lives under assets/ ------------------------------------------ */

  /** A name no other asset of the same kind already answers to. */
  const freeName = (kind, wanted) => {
    let name = Assets.slug(wanted);
    let n = 2;
    while (state.assets.some((asset) => asset.kind === kind && asset.name === name)) {
      name = `${Assets.slug(wanted)}_${n++}`;
    }
    return name;
  };

  /**
   * Puts a file in the pack. The bytes go to IndexedDB; what comes back is the reference a
   * trainer writes to reach it, so the caller can fill the field in straight away.
   */
  const addAsset = async (file, kind) => {
    const definition = Assets.KINDS[kind];
    if (!file.name.toLowerCase().endsWith('.' + definition.ext)) {
      throw new Error(definition.ext.toUpperCase());
    }
    const asset = {
      id: 'a' + Date.now().toString(36) + Math.random().toString(36).slice(2, 7),
      kind,
      name: freeName(kind, file.name),
      size: file.size,
      subtitle: ''
    };
    await Assets.put(asset.id, file);
    state.assets.push(asset);
    changed();
    return asset;
  };

  const removeAsset = async (id) => {
    const index = state.assets.findIndex((asset) => asset.id === id);
    if (index < 0) return;
    try { await Assets.drop(id); } catch (e) { /* already gone from the store */ }
    state.assets.splice(index, 1);
    changed();
  };

  const renameAsset = (id, wanted) => {
    const asset = state.assets.find((one) => one.id === id);
    if (!asset) return;
    asset.name = freeName(asset.kind, wanted);
    changed();
  };

  /** Every reference a field may name, so the editor can offer them rather than ask. */
  const references = (kind) => state.assets
    .filter((asset) => asset.kind === kind)
    .map((asset) => Assets.reference(asset, state.namespace));

  /* ---- translations -------------------------------------------------------- */

  const KEY_LIKE = /^[a-z0-9_][a-z0-9_.-]*\.[a-z0-9_.-]+$/;

  /** A string a pack wrote that is a translation key rather than a sentence. */
  const looksLikeKey = (value) => typeof value === 'string'
    && !value.includes(' ') && value.length < 120 && KEY_LIKE.test(value);

  /** Every key the pack's own files name, in the order they are met. */
  const usedKeys = () => {
    const found = [];
    const take = (value) => { if (looksLikeKey(value) && !found.includes(value)) found.push(value); };

    state.files.forEach((entry) => {
      if (entry.kind === 'trainer') {
        take(entry.doc.name);
        Object.values(entry.doc.messages || {}).forEach(take);
        take((entry.doc.requires || {}).message);
        ['label', 'arrival', 'busy'].forEach((key) => take((entry.doc.location || {})[key]));
      } else if (entry.kind === 'category') {
        take(entry.doc.name);
      } else if (entry.kind === 'intro') {
        (entry.doc.layers || []).forEach((layer) => {
          if (layer.type === 'text' && !String(layer.value || '').includes('%')) take(layer.value);
        });
      }
    });
    state.assets.forEach((asset) => {
      if (asset.subtitle) take(`${state.namespace}.subtitles.${asset.name}`);
    });
    return found;
  };

  const languages = () => Object.keys(state.lang);

  const setTranslation = (code, key, value) => {
    state.lang[code] = state.lang[code] || {};
    if (value) state.lang[code][key] = value; else delete state.lang[code][key];
    save();
  };

  const addLanguage = (code) => {
    if (!code || state.lang[code]) return;
    state.lang[code] = {};
    changed();
  };

  const removeLanguage = (code) => {
    delete state.lang[code];
    changed();
  };

  /* ---- in and out ------------------------------------------------------- */

  const json = (doc) => JSON.stringify(doc, null, 2) + '\n';

  const mcmeta = () => json({
    pack: {
      pack_format: DATA_FORMAT,
      supported_formats: { min_inclusive: ASSET_FORMAT, max_inclusive: DATA_FORMAT },
      description: state.description || state.namespace
    }
  });

  const download = (name, blob) => {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  const downloadOne = (entry) => {
    const name = pathOf(entry).split('/').pop();
    download(name, new Blob([json(entry.doc)], { type: 'application/json' }));
  };

  /**
   * A mod identity, so Fabric loads the pack itself and can refuse to start when the mod is
   * missing rather than leave a pack loaded for nothing.
   *
   * It only belongs in a `.jar`: Fabric skips an archive that is not one, while the mod's own
   * ModsFolderPackSource skips anything carrying mod metadata - a `.zip` with this file inside
   * would load from nowhere at all.
   */
  const fabricMod = () => json({
    schemaVersion: 1,
    id: state.namespace,
    version: '1.0.0',
    name: state.description || state.namespace,
    environment: '*',
    depends: { 'cobblemon-trainers': '*' }
  });

  const readme = () => {
    const assets = state.assets.length > 0 || languages().some((code) => Object.keys(state.lang[code]).length);
    return `Pack ${state.namespace} - Cobblemon Trainers\n\n`
      + (state.archive === 'jar'
        ? 'A poser dans mods/. Le fabric.mod.json declare la dependance au mod,\n'
          + 'donc le jeu le dira clairement si Cobblemon Trainers manque.\n'
        : assets
          ? 'A poser dans mods/ : c\'est la seule voie qui charge data/ ET assets/\n'
            + '(les musiques, les skins et les traductions) en un seul fichier.\n'
            + 'Dans <monde>/datapacks/, seuls les dresseurs se chargeraient.\n'
          : 'A poser dans mods/ ou dans <monde>/datapacks/.\n')
      + '\nDocumentation : https://github.com/matheo-1712/cobblemon-trainers/blob/master/docs/DATAPACK.md\n';
  };

  /** The pack as an archive: the data half, the assets half, and what ties them together. */
  const build = async () => {
    const archive = new JSZip();
    archive.file('pack.mcmeta', mcmeta());
    archive.file('README.txt', readme());
    if (state.archive === 'jar') archive.file('fabric.mod.json', fabricMod());

    state.files.forEach((entry) => archive.file(pathOf(entry), json(entry.doc)));

    for (const asset of state.assets) {
      const blob = await Assets.get(asset.id);
      if (blob) archive.file(Assets.path(asset, state.namespace), blob);
    }

    const sounds = Assets.soundsJson(state.assets, state.namespace);
    if (sounds) archive.file(`assets/${state.namespace}/sounds.json`, json(sounds));

    languages().forEach((code) => {
      const entries = state.lang[code] || {};
      if (Object.keys(entries).length === 0) return;
      archive.file(`assets/${state.namespace}/lang/${code}.json`, json(entries));
    });

    return archive;
  };

  const zip = async () => {
    const archive = await build();
    const blob = await archive.generateAsync({ type: 'blob' });
    download(`${state.namespace}.${state.archive}`, blob);
  };

  /** What a lone JSON file is, judged by what it carries. */
  const kindOf = (doc) => {
    if (Array.isArray(doc.layers) || doc.duration !== undefined) return 'intro';
    if (doc.criteria || doc.display) return 'advancement';
    if (doc.order !== undefined && !doc.battle && !doc.team) return 'category';
    return 'trainer';
  };

  const readJson = (text, name) => {
    const doc = JSON.parse(text);
    const kind = kindOf(doc);
    const path = name.replace(/\.json$/i, '').replace(/[^a-z0-9_.\-/]/gi, '_').toLowerCase();
    return add(kind, path, doc);
  };

  /** A pack archive, read back into the list - anything outside our folders is ignored. */
  const readZip = async (file) => {
    const archive = await JSZip.loadAsync(file);
    const entries = Object.values(archive.files).filter((item) => !item.dir);
    let found = 0;

    const meta = entries.find((item) => item.name.endsWith('pack.mcmeta'));
    if (meta) {
      try {
        const parsed = JSON.parse(await meta.async('string'));
        if (parsed.pack && parsed.pack.description) state.description = String(parsed.pack.description);
      } catch (e) { /* a description is not worth failing an import over */ }
    }

    // The format describes the archive being read, so it is decided here rather than left
    // over from whatever was loaded before - an import replaces, it does not inherit.
    state.archive = entries.some((item) => item.name.endsWith('fabric.mod.json')) ? 'jar' : 'zip';

    // The assets half first, so a trainer read afterwards already has its music to point at.
    for (const item of entries) {
      const lang = item.name.match(/assets\/([^/]+)\/lang\/([a-z_]+)\.json$/);
      if (lang) {
        try {
          state.namespace = lang[1];
          const parsed = JSON.parse(await item.async('string'));
          state.lang[lang[2]] = { ...(state.lang[lang[2]] || {}), ...parsed };
          found += 1;
        } catch (e) { /* a lang file we cannot read is one the game could not read either */ }
        continue;
      }

      const kind = { 'sounds/battle_music': 'music', 'sounds/intro': 'sound',
                     'textures/trainers': 'skin', 'textures/gui/intro': 'intro_texture' };
      const asset = item.name.match(/assets\/([^/]+)\/(.+)\/([^/]+)\.(ogg|png)$/);
      if (asset && kind[asset[2]]) {
        state.namespace = asset[1];
        const blob = await item.async('blob');
        await addAsset(new File([blob], asset[3] + '.' + asset[4]), kind[asset[2]]);
        found += 1;
      }
    }

    // The subtitle of a sound is written in sounds.json and said in the lang files, so it only
    // comes back once both have been read - and it is given back its text rather than its key.
    const soundsFile = entries.find((item) => item.name.endsWith(`assets/${state.namespace}/sounds.json`));
    if (soundsFile) {
      try {
        const sounds = JSON.parse(await soundsFile.async('string'));
        Object.entries(sounds).forEach(([key, entry]) => {
          if (!entry || !entry.subtitle) return;
          const asset = state.assets.find((one) => Assets.soundKey(one) === key);
          if (!asset) return;
          const said = languages().map((code) => (state.lang[code] || {})[entry.subtitle]).find(Boolean);
          asset.subtitle = said || entry.subtitle;
        });
      } catch (e) { /* a sounds.json we cannot read is one the game could not read either */ }
    }

    for (const item of entries) {
      const trainer = item.name.match(new RegExp(`data/([^/]+)/${ROOT}/trainers/(.+)\\.json$`));
      const intro = item.name.match(new RegExp(`data/([^/]+)/${ROOT}/intro/(.+)\\.json$`));
      const advancement = item.name.match(/data\/([^/]+)\/advancements?\/(.+)\.json$/);
      const match = trainer || intro || advancement;
      if (!match) continue;

      state.namespace = match[1];
      let doc;
      try {
        doc = JSON.parse(await item.async('string'));
      } catch (e) {
        continue;
      }
      found += 1;

      if (trainer && match[2].endsWith('/category')) {
        add('category', match[2].replace(/\/category$/, ''), doc);
      } else if (trainer) {
        add('trainer', match[2], doc);
      } else if (intro) {
        add('intro', match[2], doc);
      } else {
        add('advancement', match[2], doc);
      }
    }
    return found;
  };

  return {
    state, add, remove, duplicate, select, current, sorted, clear, blank,
    pathOf, idOf, json, zip, build, downloadOne, readJson, readZip, restore, save,
    addAsset, removeAsset, renameAsset, references,
    usedKeys, looksLikeKey, languages, setTranslation, addLanguage, removeLanguage,
    onChange(fn) { listeners.push(fn); },
    changed
  };
})();
