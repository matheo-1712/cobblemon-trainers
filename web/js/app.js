/*
 * The editor itself: the sidebar, the four editors, and what ties them to the pack.
 *
 * Everything shown here is built from schema.js and checked by validate.js; this file only
 * decides layout and wiring. A change anywhere writes straight into the document the pack
 * holds, then repaints - so the JSON tab is never a copy of the form, it is the file.
 */

const App = (() => {
  const el = Form.el;
  const T = (key, ...args) => I18N.t(key, ...args);

  let tab = { trainer: 'form', intro: 'layers', category: 'form', advancement: 'form' };
  /*
   * What the intro editor holds between renders: where the playhead is, which view is up,
   * which layer is selected, and the two aids. It outlives every render, so a drag that ends
   * in a rebuild comes back to the same selection under the same pointer.
   */
  let preview = {
    tick: 0, playing: false, raf: null, mode: 'layout',
    hidden: new Set(), selected: null, snap: true, grid: true,
    skin: '', player: 'RereBleue', slim: false
  };
  let templates = null;

  const $ = (id) => document.getElementById(id);

  /** The pack as the checks want to read it: its state, plus the two questions they ask of it. */
  const packView = () => ({ ...Pack.state, references: Pack.references, usedKeys: Pack.usedKeys });

  /* ---- the sidebar ------------------------------------------------------ */

  const kindLabel = (kind) => T('kind.' + kind);

  /** The two pages that are not a file: everything under assets/, and the lang files. */
  const renderViews = () => {
    const box = $('views');
    box.innerHTML = '';
    const keys = Pack.usedKeys().length;
    const missing = Pack.usedKeys().filter((key) =>
      Pack.languages().every((code) => !(Pack.state.lang[code] || {})[key])).length;

    [['__assets__', T('assets.title'), Pack.state.assets.length, 0],
     ['__lang__', T('lang.title'), keys, missing]].forEach(([key, label, count, warn]) => {
      const line = el('button', 'file' + (Pack.state.selected === key ? ' file-on' : ''));
      line.type = 'button';
      line.appendChild(el('span', 'file-name', label));
      if (warn) line.appendChild(el('span', 'dot dot-warn', String(warn)));
      else if (count) line.appendChild(el('span', 'dot dot-quiet', String(count)));
      line.addEventListener('click', () => Pack.select(key));
      box.appendChild(line);
    });
  };

  const renderSidebar = () => {
    renderViews();
    const box = $('files');
    box.innerHTML = '';

    const files = Pack.sorted();
    if (files.length === 0) {
      box.appendChild(el('p', 'muted', T('pack.empty')));
      return;
    }

    let lastKind = null;
    files.forEach((entry) => {
      if (entry.kind !== lastKind) {
        lastKind = entry.kind;
        box.appendChild(el('div', 'files-head', kindLabel(entry.kind)));
      }
      const line = el('button', 'file' + (entry.key === Pack.state.selected ? ' file-on' : ''));
      line.type = 'button';
      const name = entry.path.split('/');
      const leaf = name.pop();
      if (name.length) line.appendChild(el('span', 'file-folder', name.join('/') + '/'));
      line.appendChild(el('span', 'file-name', entry.kind === 'category' ? leaf + '/' : leaf));
      const problems = Validate.file(entry, packView());
      const errors = problems.filter((p) => p.level === 'error').length;
      if (errors) line.appendChild(el('span', 'dot dot-error', String(errors)));
      line.addEventListener('click', () => { Pack.select(entry.key); });
      box.appendChild(line);
    });
  };

  /* ---- the header of a file --------------------------------------------- */

  const renderHeader = (entry) => {
    const head = el('div', 'editor-head');

    const left = el('div', 'editor-id');
    left.appendChild(el('span', 'badge badge-' + entry.kind, kindLabel(entry.kind)));

    const path = el('input', 'input input-path');
    path.type = 'text';
    path.value = entry.path;
    path.spellcheck = false;
    path.addEventListener('input', () => {
      entry.path = path.value.trim().toLowerCase();
      Pack.changed();
    });
    left.appendChild(path);

    const suffix = entry.kind === 'category' ? '/category.json' : '.json';
    left.appendChild(el('span', 'muted mono', suffix));
    head.appendChild(left);

    const right = el('div', 'editor-actions');
    right.appendChild(el('span', 'muted mono editor-ref', Pack.idOf(entry)));

    if (entry.kind === 'trainer') {
      const keys = el('button', 'btn btn-ghost btn-small', T('lang.keyify'));
      keys.type = 'button';
      keys.title = T('lang.keyify.hint');
      keys.addEventListener('click', () => keyify(entry));
      right.appendChild(keys);
    }

    const one = el('button', 'btn btn-ghost btn-small', T('pack.export.one'));
    one.type = 'button';
    one.addEventListener('click', () => Pack.downloadOne(entry));
    right.appendChild(one);

    const copy = el('button', 'btn btn-ghost btn-small', T('file.duplicate'));
    copy.type = 'button';
    copy.addEventListener('click', () => Pack.duplicate(entry.key));
    right.appendChild(copy);

    const kill = el('button', 'btn btn-ghost btn-small btn-danger', T('file.delete'));
    kill.type = 'button';
    kill.addEventListener('click', () => {
      if (window.confirm(T('file.delete.confirm'))) Pack.remove(entry.key);
    });
    right.appendChild(kill);

    head.appendChild(right);
    return head;
  };

  const renderTabs = (entry, names) => {
    const bar = el('div', 'tabs');
    names.forEach((name) => {
      const button = el('button', 'tab' + (tab[entry.kind] === name ? ' tab-on' : ''), T('tab.' + name));
      button.type = 'button';
      button.addEventListener('click', () => { tab[entry.kind] = name; render(); });
      bar.appendChild(button);
    });
    return bar;
  };

  /* ---- JSON tab ---------------------------------------------------------- */

  const renderJson = (entry) => {
    const wrap = el('div', 'json');
    const area = el('textarea', 'input mono json-area');
    area.value = Pack.json(entry.doc);
    area.spellcheck = false;

    const bar = el('div', 'row row-end');
    const status = el('span', 'muted');
    const copy = el('button', 'btn btn-small', T('json.copy'));
    copy.type = 'button';
    copy.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(area.value);
        status.textContent = T('json.copied');
      } catch (e) {
        area.select();
      }
    });
    const apply = el('button', 'btn btn-small btn-primary', T('json.paste'));
    apply.type = 'button';
    apply.addEventListener('click', () => {
      try {
        entry.doc = JSON.parse(area.value);
        Pack.changed();
      } catch (error) {
        status.textContent = T('json.invalid', error.message);
        status.className = 'error-text';
      }
    });
    bar.appendChild(status);
    bar.appendChild(copy);
    bar.appendChild(apply);

    wrap.appendChild(area);
    wrap.appendChild(bar);
    return wrap;
  };

  /* ---- the trainer ------------------------------------------------------- */

  const skinCanvas = (entry) => {
    const skin = entry.doc.skin || {};
    const card = el('div', 'skin-card');
    const canvas = document.createElement('canvas');
    canvas.width = 16 * 5;
    canvas.height = 32 * 5;
    canvas.className = 'skin-canvas';
    card.appendChild(canvas);

    const paint = () => {
      const ctx = canvas.getContext('2d');
      ctx.imageSmoothingEnabled = false;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      let image = null;
      if (skin.type === 'texture' && skin.value) image = Preview.texture(skin.value);
      else if (skin.value) image = Preview.skin(skin.value);
      if (image && image !== 'missing') {
        Preview.drawFlatSkin(ctx, image, skin.model === 'slim', canvas.width / 2, 0, 5);
      } else {
        ctx.fillStyle = '#141824';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
      }
    };
    paint();

    const note = el('p', 'muted small');
    if (skin.type === 'texture') {
      // Dropping a skin puts it *in the pack*: the file is what the archive will carry, and the
      // field is filled from it, so the image and the id it is reached by cannot disagree.
      note.textContent = T('assets.drop.skin');
      card.addEventListener('dragover', (event) => { event.preventDefault(); card.classList.add('drop'); });
      card.addEventListener('dragleave', () => card.classList.remove('drop'));
      card.addEventListener('drop', async (event) => {
        event.preventDefault();
        event.stopPropagation();
        card.classList.remove('drop');
        const file = event.dataTransfer.files[0];
        if (!file) return;
        const asset = await take(file, 'skin');
        if (!asset) return;
        entry.doc.skin = { ...skin, type: 'texture', value: Assets.reference(asset, Pack.state.namespace) };
        Pack.changed();
      });
    } else {
      note.textContent = 'crafthead.net';
    }
    card.appendChild(note);
    return card;
  };

  const renderTeam = (entry) => {
    const wrap = el('div', 'team');
    const team = entry.doc.team || [];

    wrap.appendChild(el('p', 'field-hint', T('team.hint')));
    wrap.appendChild(el('p', 'field-hint', T('team.extra')));

    team.forEach((member, index) => {
      const card = el('div', 'card');
      const head = el('div', 'card-head');
      const species = String(member).split('\n')[0].split('@')[0].trim();
      head.appendChild(el('span', 'card-title', '#' + (index + 1) + (species ? ' · ' + species : '')));

      const actions = el('div', 'row');
      if (index > 0) {
        const up = el('button', 'btn btn-ghost btn-mini', '↑');
        up.type = 'button';
        up.addEventListener('click', () => {
          [team[index - 1], team[index]] = [team[index], team[index - 1]];
          Pack.changed();
        });
        actions.appendChild(up);
      }
      const kill = el('button', 'btn btn-ghost btn-mini', '×');
      kill.type = 'button';
      kill.addEventListener('click', () => {
        team.splice(index, 1);
        if (team.length === 0) delete entry.doc.team;
        Pack.changed();
      });
      actions.appendChild(kill);
      head.appendChild(actions);
      card.appendChild(head);

      const area = el('textarea', 'input mono team-area');
      area.value = member;
      area.rows = Math.max(6, String(member).split('\n').length + 1);
      area.spellcheck = false;
      area.addEventListener('input', () => {
        team[index] = area.value;
        entry.doc.team = team;
        Pack.save();
        renderChecks(entry);
      });
      area.addEventListener('blur', () => Pack.changed());
      card.appendChild(area);
      wrap.appendChild(card);
    });

    if (team.length === 0) wrap.appendChild(el('p', 'muted', T('team.empty')));

    const bar = el('div', 'row');
    const add = el('button', 'btn btn-small', '+ ' + T('team.add'));
    add.type = 'button';
    add.addEventListener('click', () => {
      entry.doc.team = [...team, ''];
      Pack.changed();
    });
    bar.appendChild(add);

    const paste = el('button', 'btn btn-small btn-ghost', T('team.paste'));
    paste.type = 'button';
    paste.addEventListener('click', () => {
      const text = window.prompt(T('team.paste'));
      if (!text) return;
      const split = text.split(/\n\s*\n/).map((one) => one.trim()).filter(Boolean);
      entry.doc.team = [...team, ...split];
      Pack.changed();
    });
    bar.appendChild(paste);
    wrap.appendChild(bar);
    return wrap;
  };

  const renderTrainer = (entry) => {
    const wrap = el('div', 'editor-body');
    wrap.appendChild(renderTabs(entry, ['form', 'team', 'json']));

    if (tab.trainer === 'json') {
      wrap.appendChild(renderJson(entry));
      return wrap;
    }
    if (tab.trainer === 'team') {
      wrap.appendChild(renderTeam(entry));
      return wrap;
    }

    const columns = el('div', 'columns');
    const left = el('div', 'column');
    const ctx = {
      skin: { value: { list: 'skin-ids' } },
      battle: { intro: { list: 'intro-ids' }, music: { list: 'music-ids' } }
    };
    left.appendChild(Form.render(SCHEMA.TRAINER, entry.doc, () => {
      Form.prune(entry.doc);
      Pack.save();
      renderSidebar();
      renderChecks(entry);
      right.replaceChildren(skinCanvas(entry), teamSummary(entry));
    }, ctx));

    const right = el('aside', 'column column-side');
    right.appendChild(skinCanvas(entry));
    right.appendChild(teamSummary(entry));

    columns.appendChild(left);
    columns.appendChild(right);
    wrap.appendChild(columns);
    return wrap;
  };

  const teamSummary = (entry) => {
    const team = entry.doc.team || [];
    const card = el('div', 'side-card');
    card.appendChild(el('h4', null, T('team.title')));
    card.appendChild(el('p', 'muted small', T('team.count', team.length)));
    team.slice(0, 6).forEach((member) => {
      const species = String(member).split('\n')[0].split('@')[0].trim() || '—';
      card.appendChild(el('div', 'chip', species));
    });
    const go = el('button', 'btn btn-ghost btn-small', T('tab.team'));
    go.type = 'button';
    go.addEventListener('click', () => { tab.trainer = 'team'; render(); });
    card.appendChild(go);
    return card;
  };

  /* ---- the intro ---------------------------------------------------------- */

  const about = () => {
    const trainer = Pack.state.files.find((file) => file.kind === 'trainer');
    const skin = (trainer && trainer.doc.skin) || {};
    return {
      name: trainer ? trainer.doc.name || 'Trainer' : 'Trainer',
      category: trainer && trainer.path.includes('/') ? trainer.path.split('/')[0] : '',
      level: trainer && trainer.doc.battle ? trainer.doc.battle.level ?? 1 : 1,
      team: trainer && trainer.doc.team ? trainer.doc.team.length : 6,
      player: preview.player,
      playerSlim: false,
      trainerSkin: preview.skin || skin.value || '',
      trainerSlim: skin.model === 'slim'
    };
  };

  /*
   * What the stage, the timeline and the layer cards have to say to each other lives here
   * rather than being passed down: all three are rebuilt by the same render, and all three
   * have to answer the same selection. They are cleared at the top of renderIntro, so a stale
   * canvas from a file that is no longer open can never be painted into.
   */
  let stage = null;
  let clock = null;       // the timeline, once it exists
  let cards = [];         // one <details> per layer, so selecting can open one without a render

  const layerName = (layer, index) => {
    const known = SCHEMA.LAYERS[layer.type];
    const title = known ? I18N.of(known.l) : layer.type;
    const said = layer.type === 'text' ? layer.value
      : layer.type === 'image' ? String(layer.texture || '').split('/').pop()
      : layer.type === 'figure' || layer.type === 'team_balls'
        ? T('preview.' + (layer.who === 'player' ? 'player' : 'trainer')) : '';
    return (index + 1) + ' · ' + title + (said ? ' · ' + said : '');
  };

  /**
   * Brings a card into view inside the layers panel, and moves nothing else.
   *
   * `scrollIntoView` scrolls every ancestor that can scroll, the document included: choosing a
   * layer on the canvas would send the page down to its card and take the canvas off screen -
   * which is exactly the moment one is looking at it.
   */
  const reveal = (card) => {
    const panel = card && card.parentElement;
    if (!panel) return;
    const box = card.getBoundingClientRect();
    const view = panel.getBoundingClientRect();
    if (box.top < view.top) panel.scrollTop += box.top - view.top;
    else if (box.bottom > view.bottom) {
      panel.scrollTop += Math.min(box.top - view.top, box.bottom - view.bottom);
    }
  };

  /** Lights the card of the selected layer and opens it, without rebuilding the editor. */
  const paintSelection = (open, bring) => {
    cards.forEach((card, index) => {
      const on = index === preview.selected;
      card.classList.toggle('layer-on', on);
      if (on && open) {
        card.open = true;
        if (bring) reveal(card);
      }
    });
  };

  /**
   * Only a selection that actually changed opens a card: a click on the summary of the card
   * that is already chosen is a click to fold it away, and forcing it back open would leave
   * one card in the list that cannot be closed.
   */
  const select = (index, reveal) => {
    const moved = preview.selected !== index;
    preview.selected = index;
    paintSelection(moved, moved && reveal);
    if (stage) stage.paintOverlay();
    if (clock) clock.update();
  };

  /** A drag writes into the very object the file is made of; only the repaint is ours. */
  const touched = () => {
    Pack.save();
    if (clock) clock.update();
  };

  const renderStage = (entry) => {
    const card = el('div', 'preview');
    transport = {};

    // Switching view never rebuilds the editor, it only repaints: a scrub of the timeline
    // ruler switches to the animation mid-drag, and a rebuild there would tear the node the
    // pointer is captured on out from under it.
    const seg = [];
    const modes = el('div', 'seg');
    [['layout', 'stage.layout'], ['play', 'stage.play']].forEach(([mode, key]) => {
      const button = el('button', 'seg-btn', T(key));
      button.type = 'button';
      button.addEventListener('click', () => {
        if (preview.mode === mode) return;
        preview.mode = mode;
        if (mode === 'layout') stop();
        paintMode();
        if (stage) stage.paint();
      });
      seg.push([mode, button]);
      modes.appendChild(button);
    });

    const toggles = el('div', 'row row-end stage-toggles');
    [['snap', 'stage.snap'], ['grid', 'stage.grid']].forEach(([key, label]) => {
      const tag = el('label', 'check');
      const box = el('input');
      box.type = 'checkbox';
      box.checked = Boolean(preview[key]);
      box.addEventListener('change', () => {
        preview[key] = box.checked;
        if (stage) stage.paintOverlay();
      });
      tag.appendChild(box);
      tag.appendChild(el('span', null, T(label)));
      toggles.appendChild(tag);
    });

    const bar = el('div', 'stage-bar');
    bar.appendChild(modes);
    bar.appendChild(toggles);
    card.appendChild(bar);

    const holder = el('div', 'stage');
    const canvas = document.createElement('canvas');
    canvas.width = Preview.WIDTH;
    canvas.height = Preview.HEIGHT;
    canvas.className = 'preview-canvas';
    holder.appendChild(canvas);

    // The overlay takes the pointer and the keyboard, so it is focusable: arrows nudge the
    // selected layer, and a focus ring says the canvas is listening.
    const overlay = document.createElement('canvas');
    overlay.className = 'stage-overlay';
    overlay.tabIndex = 0;
    holder.appendChild(overlay);
    card.appendChild(holder);

    stage = Stage.create({
      canvas,
      overlay,
      state: preview,
      scene: () => entry.doc,
      layers: () => entry.doc.layers,
      about,
      onSelect: (index) => select(index, true),
      onEdit: touched,
      onCommit: () => Pack.changed(),
      onRemove: (index) => removeLayer(entry, index),
      onDuplicate: (index) => duplicateLayer(entry, index),
      onPaint: () => { if (clock) clock.update(); }
    });

    card.appendChild(renderTransport(entry, canvas));

    const said = el('p', 'muted small stage-hint');
    const keys = el('p', 'muted small', T('stage.keys'));
    card.appendChild(said);
    card.appendChild(keys);

    card.appendChild(renderStageSkins(entry));
    card.appendChild(el('p', 'muted small', T('preview.note')));

    Object.assign(transport, { seg, toggles, holder, said, keys });
    paintMode();
    Preview.onRepaint = () => { if (stage) stage.paint(); };
    stage.paint();
    if (preview.playing && preview.mode === 'play') loop(entry);
    return card;
  };

  /** Everything on the stage panel that says which of the two views is up. */
  const paintMode = () => {
    if (!transport || !transport.seg) return;
    const playing = preview.mode === 'play';
    transport.seg.forEach(([mode, button]) =>
      button.classList.toggle('seg-on', preview.mode === mode));
    transport.toggles.hidden = playing;
    transport.keys.hidden = playing;
    transport.holder.classList.toggle('stage-play', playing);
    transport.said.textContent = T(playing ? 'stage.hint.play' : 'stage.hint');
  };

  /** Play, pause, restart and the scrub - all four only mean anything in the animation. */
  const renderTransport = (entry, canvas) => {
    const bar = el('div', 'preview-bar');
    const duration = () => Math.max(entry.doc.duration ?? 100, 1);

    const play = el('button', 'btn btn-small btn-primary',
                    T(preview.playing ? 'preview.pause' : 'preview.play'));
    play.type = 'button';
    play.addEventListener('click', () => {
      preview.playing = !preview.playing;
      if (preview.playing) {
        preview.mode = 'play';
        if (preview.tick >= duration() - 0.01) preview.tick = 0;
        paintMode();
        loop(entry);
      } else {
        stop();
        if (stage) stage.paint();
      }
      paintTransport();
    });
    bar.appendChild(play);

    const again = el('button', 'btn btn-small', T('preview.restart'));
    again.type = 'button';
    again.addEventListener('click', () => {
      preview.tick = 0;
      preview.playing = true;
      preview.mode = 'play';
      paintMode();
      loop(entry);
      paintTransport();
    });
    bar.appendChild(again);

    const slider = el('input', 'slider');
    slider.type = 'range';
    slider.min = 0;
    slider.max = duration();
    slider.step = 0.5;
    slider.value = preview.tick;
    slider.addEventListener('input', () => {
      scrubTo(Number(slider.value));
    });
    bar.appendChild(slider);

    const count = el('span', 'mono muted stage-clock', String(Math.round(preview.tick)));
    bar.appendChild(count);

    // The transport is redrawn by the animation loop rather than by a render: sixty renders a
    // second would rebuild the layer cards under the author's pointer.
    Object.assign(transport, { play, slider, count, canvas });
    return bar;
  };

  const renderStageSkins = (entry) => {
    const skins = el('div', 'row preview-skins');
    [['preview.trainer', 'skin'], ['preview.player', 'player']].forEach(([label, key]) => {
      const cell = el('label', 'mini');
      cell.appendChild(el('span', 'mini-label', T(label)));
      const input = el('input', 'input');
      input.type = 'text';
      input.placeholder = 'RereBleue';
      input.value = preview[key] || '';
      input.addEventListener('change', () => {
        preview[key] = input.value.trim();
        if (stage) stage.paint();
      });
      cell.appendChild(input);
      skins.appendChild(cell);
    });
    const hint = el('div', 'preview-skins-hint');
    hint.appendChild(el('p', 'muted small', T('preview.skin.hint')));
    const wrap = el('div', 'stage-skins');
    wrap.appendChild(skins);
    wrap.appendChild(hint);
    return wrap;
  };

  /* -- the animation -- */

  let transport = null;

  const stop = () => {
    preview.playing = false;
    if (preview.raf) cancelAnimationFrame(preview.raf);
    preview.raf = null;
  };

  /** Scrubbing is a question about the animation, so it answers in the animation. */
  const scrubTo = (tick) => {
    stop();
    preview.tick = tick;
    preview.mode = 'play';
    paintMode();
    if (stage) stage.paint();
    paintTransport();
  };

  const paintTransport = () => {
    if (!transport) return;
    transport.slider.value = preview.tick;
    transport.count.textContent = preview.tick.toFixed(0);
    transport.play.textContent = T(preview.playing ? 'preview.pause' : 'preview.play');
  };

  const loop = (entry) => {
    if (preview.raf) cancelAnimationFrame(preview.raf);
    let last = performance.now();
    const step = (now) => {
      const duration = Math.max(entry.doc.duration ?? 100, 1);
      preview.tick += (now - last) / 50;
      last = now;
      if (preview.tick >= duration) {
        preview.tick = duration;
        preview.playing = false;
      }
      if (stage) stage.paint();
      paintTransport();
      if (preview.playing) preview.raf = requestAnimationFrame(step);
    };
    preview.raf = requestAnimationFrame(step);
  };

  /* -- the layers -- */

  const removeLayer = (entry, index) => {
    entry.doc.layers.splice(index, 1);
    preview.hidden.clear();
    preview.selected = entry.doc.layers.length ? Math.min(index, entry.doc.layers.length - 1) : null;
    Pack.changed();
  };

  const duplicateLayer = (entry, index) => {
    const copy = JSON.parse(JSON.stringify(entry.doc.layers[index]));
    entry.doc.layers.splice(index + 1, 0, copy);
    preview.hidden.clear();
    preview.selected = index + 1;
    Pack.changed();
  };

  /** Moves a layer in the drawing order, and the selection with it. */
  const moveLayer = (entry, index, to) => {
    const layers = entry.doc.layers;
    if (to < 0 || to >= layers.length) return;
    [layers[to], layers[index]] = [layers[index], layers[to]];
    const held = preview.hidden.has(index);
    const there = preview.hidden.has(to);
    preview.hidden.delete(index);
    preview.hidden.delete(to);
    if (held) preview.hidden.add(to);
    if (there) preview.hidden.add(index);
    if (preview.selected === index) preview.selected = to;
    else if (preview.selected === to) preview.selected = index;
    Pack.changed();
  };

  /**
   * The nine anchors as a pad rather than a menu.
   *
   * Picking one re-bases the offset, so the layer does not move: what changes is the corner it
   * holds on to. A menu could not say that, and a pad next to the tether drawn on the canvas
   * says it without a sentence.
   */
  const renderAnchorPad = (entry, layer, index) => {
    const wrap = el('div', 'field');
    const head = el('span', 'field-label', T('stage.anchor'));
    wrap.appendChild(head);
    const pad = el('div', 'pad');
    ['top-left', 'top', 'top-right', 'left', 'center', 'right',
     'bottom-left', 'bottom', 'bottom-right'].forEach((anchor) => {
      const dot = el('button', 'pad-dot' + ((layer.anchor ?? 'center') === anchor ? ' pad-on' : ''));
      dot.type = 'button';
      dot.title = anchor;
      dot.addEventListener('click', () => {
        if (!stage) return;
        stage.reanchor(index, anchor);
        Pack.changed();
      });
      pad.appendChild(dot);
    });
    wrap.appendChild(pad);
    wrap.appendChild(el('p', 'field-hint', T('stage.anchor.hint')));
    return wrap;
  };

  const renderLayerCard = (entry, layer, index) => {
    const layers = entry.doc.layers;
    const box = el('details', 'card layer' + (index === preview.selected ? ' layer-on' : ''));
    box.open = index === preview.selected;

    const head = el('summary', 'card-head');
    head.appendChild(el('span', 'card-title', layerName(layer, index)));
    head.appendChild(el('span', 'layer-at mono muted', '@' + (layer.at ?? 0)));

    const actions = el('div', 'row');
    const act = (glyph, key, run, cls) => {
      const button = el('button', 'btn btn-ghost btn-mini' + (cls || ''), glyph);
      button.type = 'button';
      button.title = T(key);
      button.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        run();
      });
      actions.appendChild(button);
    };

    act(preview.hidden.has(index) ? '◌' : '◉', 'layers.hide', () => {
      if (preview.hidden.has(index)) preview.hidden.delete(index);
      else preview.hidden.add(index);
      render();
    });
    act('⧉', 'layers.duplicate', () => duplicateLayer(entry, index));
    act('↑', 'layers.up', () => moveLayer(entry, index, index - 1));
    act('↓', 'layers.down', () => moveLayer(entry, index, index + 1));
    act('×', 'layers.remove', () => removeLayer(entry, index), ' btn-danger');
    head.appendChild(actions);
    head.addEventListener('click', () => select(index, false));
    box.appendChild(head);

    const changed = () => {
      Pack.save();
      renderChecks(entry);
      if (stage) stage.paint();
      if (clock) clock.update();
    };

    const body = el('div', 'layer-body');
    const own = SCHEMA.LAYERS[layer.type];
    if (own) {
      body.appendChild(el('h5', 'layer-part', I18N.of(own.l)));
      body.appendChild(Form.render(own.fields, layer, changed, { texture: { list: 'texture-ids' } }));
    }

    body.appendChild(el('h5', 'layer-part', T('layers.part.place')));
    body.appendChild(renderAnchorPad(entry, layer, index));
    body.appendChild(Form.render(SCHEMA.LAYER_PLACE, layer, changed));

    body.appendChild(el('h5', 'layer-part', T('layers.part.time')));
    body.appendChild(Form.render(SCHEMA.LAYER_TIME, layer, changed));

    body.appendChild(el('h5', 'layer-part', T('layers.part.sound')));
    body.appendChild(Form.render(SCHEMA.LAYER_SOUND, layer, changed, { sound: { list: 'sound-ids' } }));

    if (layer.type === 'image') {
      const drop = el('div', 'drop-zone', T('assets.drop.texture'));
      drop.addEventListener('dragover', (event) => { event.preventDefault(); drop.classList.add('drop'); });
      drop.addEventListener('dragleave', () => drop.classList.remove('drop'));
      drop.addEventListener('drop', async (event) => {
        event.preventDefault();
        event.stopPropagation();
        drop.classList.remove('drop');
        const asset = await take(event.dataTransfer.files[0], 'intro_texture');
        if (asset) {
          layer.texture = Assets.reference(asset, Pack.state.namespace);
          Pack.changed();
        }
      });
      body.appendChild(drop);
    }

    box.appendChild(body);
    return box;
  };

  const renderLayers = (entry) => {
    const wrap = el('div', 'layers');
    if (!Array.isArray(entry.doc.layers)) entry.doc.layers = [];
    const layers = entry.doc.layers;

    wrap.appendChild(Form.render(SCHEMA.INTRO_FILE, entry.doc, () => {
      Pack.save();
      render();
    }));

    // A new layer arrives where the playhead is and where the eye is: writing it at tick 0
    // meant every added layer had to be dragged out of the first frame before it could be
    // seen at all.
    const bar = el('div', 'row row-wrap add-row');
    bar.appendChild(el('span', 'field-label', T('layers.add')));
    Object.entries(SCHEMA.LAYERS).forEach(([type, definition]) => {
      const add = el('button', 'btn btn-small btn-ghost', '+ ' + I18N.of(definition.l));
      add.type = 'button';
      add.addEventListener('click', () => {
        const at = Math.round(preview.mode === 'play' ? preview.tick : 0);
        layers.push(at ? { type, at } : { type });
        preview.selected = layers.length - 1;
        Pack.changed();
      });
      bar.appendChild(add);
    });
    wrap.appendChild(bar);

    wrap.appendChild(el('p', 'field-hint', T('layers.order')));
    if (layers.length === 0) wrap.appendChild(el('p', 'muted', T('layers.empty')));

    // The cards scroll in a panel of their own rather than down the page. Opening one, or
    // bringing the chosen one into view, then cannot take the stage off screen, and the page
    // keeps the height it had - twenty layers do not push the timeline out of reach.
    const list = el('div', 'layer-list');
    cards = layers.map((layer, index) => {
      const card = renderLayerCard(entry, layer, index);
      list.appendChild(card);
      return card;
    });
    wrap.appendChild(list);
    // Nothing is laid out until the caller has put all this in the document.
    if (cards[preview.selected]) setTimeout(() => reveal(cards[preview.selected]), 0);
    return wrap;
  };

  const renderIntro = (entry) => {
    if (preview.selected !== null && preview.selected !== undefined
        && !(entry.doc.layers || [])[preview.selected]) preview.selected = null;

    const wrap = el('div', 'editor-body');
    wrap.appendChild(renderTabs(entry, ['layers', 'json']));

    if (tab.intro === 'json') {
      stop();
      wrap.appendChild(renderJson(entry));
      return wrap;
    }

    const columns = el('div', 'columns columns-intro');
    const left = el('div', 'column');
    const right = el('aside', 'column column-preview');

    // The stage is built first: the layer cards ask it to re-anchor, and the timeline paints
    // the playhead it moves.
    right.appendChild(renderStage(entry));
    clock = Stage.timeline({
      state: preview,
      scene: () => entry.doc,
      layers: () => entry.doc.layers,
      label: layerName,
      title: T('timeline.title'),
      hint: T('timeline.hint'),
      barHint: T('timeline.bar'),
      onSelect: (index) => select(index, true),
      onScrub: () => scrubTo(preview.tick),
      onEdit: () => { Pack.save(); if (stage) stage.paint(); },
      onCommit: () => Pack.changed()
    });
    right.appendChild(clock.node);

    left.appendChild(renderLayers(entry));
    columns.appendChild(left);
    columns.appendChild(right);
    wrap.appendChild(columns);
    return wrap;
  };

  /* ---- category and advancement -------------------------------------------- */

  const renderCategory = (entry) => {
    const wrap = el('div', 'editor-body');
    wrap.appendChild(renderTabs(entry, ['form', 'json']));
    if (tab.category === 'json') {
      wrap.appendChild(renderJson(entry));
      return wrap;
    }
    wrap.appendChild(Form.render(SCHEMA.CATEGORY, entry.doc, () => {
      Form.prune(entry.doc);
      Pack.save();
      renderChecks(entry);
    }));
    return wrap;
  };

  /**
   * An advancement is Minecraft's own format, so the form edits a flat view of it and rebuilds
   * the file on every change: display on one side, our trigger's four conditions on the other.
   */
  const renderAdvancement = (entry) => {
    const wrap = el('div', 'editor-body');
    wrap.appendChild(renderTabs(entry, ['form', 'json']));
    if (tab.advancement === 'json') {
      wrap.appendChild(renderJson(entry));
      return wrap;
    }

    const doc = entry.doc;
    doc.display = doc.display || {};
    doc.criteria = doc.criteria || {};
    const name = Object.keys(doc.criteria)[0] || 'defeated';
    doc.criteria[name] = doc.criteria[name] || { trigger: 'cobblemon-trainers:trainer_defeated', conditions: {} };
    const criterion = doc.criteria[name];
    criterion.trigger = 'cobblemon-trainers:trainer_defeated';
    criterion.conditions = criterion.conditions || {};
    doc.requirements = [[name]];

    const model = {
      title: typeof doc.display.title === 'string' ? doc.display.title : (doc.display.title || {}).translate || '',
      description: typeof doc.display.description === 'string' ? doc.display.description : (doc.display.description || {}).translate || '',
      icon: (doc.display.icon || {}).id || '',
      frame: doc.display.frame || 'task',
      toast: doc.display.show_toast !== false,
      announce: doc.display.announce_to_chat !== false,
      hidden: Boolean(doc.display.hidden),
      parent: doc.parent || '',
      trainer: criterion.conditions.trainer || '',
      category: criterion.conditions.category || '',
      pack: criterion.conditions.pack || '',
      count: criterion.conditions.count
    };

    const apply = () => {
      doc.display = {
        icon: { id: model.icon || 'cobblemon:poke_ball' },
        title: model.title,
        description: model.description,
        frame: model.frame
      };
      if (model.toast === false) doc.display.show_toast = false;
      if (model.announce === false) doc.display.announce_to_chat = false;
      if (model.hidden) doc.display.hidden = true;
      if (model.parent) doc.parent = model.parent; else delete doc.parent;

      const conditions = {};
      ['trainer', 'category', 'pack'].forEach((key) => { if (model[key]) conditions[key] = model[key]; });
      if (model.count !== undefined && model.count !== null && model.count !== '') conditions.count = Number(model.count);
      criterion.conditions = conditions;
      Pack.save();
      renderSidebar();
      renderChecks(entry);
    };

    const t = (fr, en) => ({ fr, en });
    const fields = [
      { k: 'title', t: 'str', def: '', l: t('Titre', 'Title') },
      { k: 'description', t: 'text', def: '', l: t('Description', 'Description') },
      { k: 'icon', t: 'str', def: '', l: t('Icône', 'Icon'), h: t('ID complet : cobblemon:poke_ball', 'Full id: cobblemon:poke_ball') },
      { k: 'frame', t: 'sel', def: 'task', l: t('Cadre', 'Frame'),
        options: [['task', t('Normal', 'Task')], ['goal', t('Objectif', 'Goal')], ['challenge', t('Défi', 'Challenge')]] },
      { k: 'parent', t: 'str', def: '', l: t('Advancement parent', 'Parent advancement') },
      { k: 'toast', t: 'bool', def: true, l: t('Notification', 'Toast') },
      { k: 'announce', t: 'bool', def: true, l: t('Annonce dans le chat', 'Announce in chat') },
      { k: 'hidden', t: 'bool', def: false, l: t('Caché dans l’arbre', 'Hidden in the tree') },
      { k: 'trainer', t: 'str', def: '', l: t('Dresseur battu', 'Trainer defeated'),
        h: t('ID complet, ou chemin nu pour tous les namespaces.', 'A full id, or a bare path for every namespace.') },
      { k: 'category', t: 'str', def: '', l: t('Catégorie', 'Category') },
      { k: 'pack', t: 'str', def: '', l: t('Pack', 'Pack') },
      { k: 'count', t: 'num', def: null, min: 1, l: t('Combien de dresseurs différents', 'How many different trainers'),
        h: t('Les quatre conditions se cumulent. Sans aucune, le premier dresseur battu valide.',
             'The four conditions add up. With none, the first trainer defeated grants it.') }
    ];

    wrap.appendChild(Form.render(fields, model, apply));
    return wrap;
  };

  /* ---- assets: the other half of a pack ------------------------------------ */

  const size = (bytes) => (bytes > 1048576
    ? (bytes / 1048576).toFixed(1) + ' Mo'
    : Math.max(1, Math.round(bytes / 1024)) + ' ko');

  /**
   * Hands a PNG of the pack to the preview, so an intro image and a texture skin are drawn
   * from the very bytes the archive will carry rather than from a placeholder.
   */
  const feedPreview = async (asset) => {
    if (asset.kind !== 'skin' && asset.kind !== 'intro_texture') return;
    const blob = await Assets.get(asset.id);
    if (!blob) return;
    const reader = new FileReader();
    reader.onload = () => {
      Preview.give(Assets.reference(asset, Pack.state.namespace), reader.result);
      if (Preview.onRepaint) Preview.onRepaint();
    };
    reader.readAsDataURL(blob);
  };

  const feedAll = () => Pack.state.assets.forEach(feedPreview);

  const take = async (file, kind) => {
    try {
      const asset = await Pack.addAsset(file, kind);
      await feedPreview(asset);
      return asset;
    } catch (error) {
      window.alert(T('assets.wrong', error.message));
      return null;
    }
  };

  const renderAssets = () => {
    const wrap = el('div', 'editor-body');
    // A thumbnail asks for an image that may still be loading; when it lands, draw this again.
    Preview.onRepaint = () => { if (Pack.state.selected === '__assets__') render(); };
    wrap.appendChild(el('p', 'field-hint', T('assets.hint')));

    Object.entries(Assets.KINDS).forEach(([kind, definition]) => {
      const block = el('section', 'assets-block');
      const head = el('div', 'card-head');
      head.appendChild(el('h3', null, I18N.of(definition.l)));

      const pick = el('button', 'btn btn-small', '+ ' + T('assets.add'));
      pick.type = 'button';
      pick.addEventListener('click', () => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.' + definition.ext;
        input.multiple = true;
        input.addEventListener('change', async () => {
          for (const file of input.files) await take(file, kind);
        });
        input.click();
      });
      head.appendChild(pick);
      block.appendChild(head);
      block.appendChild(el('p', 'field-hint', I18N.of(definition.h)));

      const held = Pack.state.assets.filter((asset) => asset.kind === kind);
      held.forEach((asset) => {
        const line = el('div', 'asset');

        const name = el('input', 'input input-path');
        name.type = 'text';
        name.value = asset.name;
        name.addEventListener('change', () => Pack.renameAsset(asset.id, name.value));
        line.appendChild(name);

        const reference = Assets.reference(asset, Pack.state.namespace);
        const ref = el('button', 'mono asset-ref', reference);
        ref.type = 'button';
        ref.title = T('assets.copy');
        ref.addEventListener('click', () => navigator.clipboard.writeText(reference).catch(() => {}));
        line.appendChild(ref);

        line.appendChild(el('span', 'muted small', size(asset.size)));

        if (kind === 'music' || kind === 'sound') {
          const listen = el('button', 'btn btn-ghost btn-mini', '▶');
          listen.type = 'button';
          listen.title = T('assets.listen');
          listen.addEventListener('click', async () => {
            const blob = await Assets.get(asset.id);
            if (!blob) return;
            const audio = new Audio(URL.createObjectURL(blob));
            audio.volume = 0.5;
            audio.play();
            listen.disabled = true;
            audio.addEventListener('ended', () => { listen.disabled = false; });
            listen.addEventListener('dblclick', () => { audio.pause(); listen.disabled = false; });
          });
          line.appendChild(listen);

          const subtitle = el('input', 'input');
          subtitle.type = 'text';
          subtitle.placeholder = T('assets.subtitle');
          subtitle.value = asset.subtitle || '';
          subtitle.addEventListener('change', () => {
            asset.subtitle = subtitle.value.trim();
            if (asset.subtitle) {
              Pack.setTranslation('fr_fr', Pack.state.namespace + '.subtitles.' + asset.name, asset.subtitle);
            }
            Pack.changed();
          });
          line.appendChild(subtitle);
        } else {
          const thumb = document.createElement('canvas');
          thumb.className = 'asset-thumb';
          thumb.width = 48;
          thumb.height = 48;
          const image = Preview.texture(reference);
          if (image && image !== 'missing') {
            const ctx = thumb.getContext('2d');
            ctx.imageSmoothingEnabled = false;
            const scale = Math.min(48 / image.width, 48 / image.height);
            ctx.drawImage(image, 0, 0, image.width * scale, image.height * scale);
          }
          line.appendChild(thumb);
        }

        const kill = el('button', 'btn btn-ghost btn-mini btn-danger', '×');
        kill.type = 'button';
        kill.addEventListener('click', () => {
          if (window.confirm(T('assets.delete.confirm'))) Pack.removeAsset(asset.id);
        });
        line.appendChild(kill);
        block.appendChild(line);
      });

      if (held.length === 0) block.appendChild(el('p', 'muted small', T('list.empty')));
      wrap.appendChild(block);
    });

    const sounds = Assets.soundsJson(Pack.state.assets, Pack.state.namespace);
    if (sounds) {
      const box = el('details', 'group');
      const summary = el('summary', 'group-head');
      summary.appendChild(el('span', 'group-title', 'sounds.json'));
      box.appendChild(summary);
      box.appendChild(el('p', 'field-hint', T('assets.sounds.hint')));
      const area = el('textarea', 'input mono json-area');
      area.value = Pack.json(sounds);
      area.readOnly = true;
      area.style.height = '220px';
      box.appendChild(area);
      wrap.appendChild(box);
    }
    return wrap;
  };

  /* ---- translations -------------------------------------------------------- */

  const renderTranslations = () => {
    const wrap = el('div', 'editor-body');
    wrap.appendChild(el('p', 'field-hint', T('lang.hint')));

    const keys = Pack.usedKeys();
    const extra = new Set();
    Pack.languages().forEach((code) => Object.keys(Pack.state.lang[code] || {})
      .forEach((key) => { if (!keys.includes(key)) extra.add(key); }));
    const all = [...keys, ...extra];

    const bar = el('div', 'row row-wrap');
    Pack.languages().forEach((code) => {
      const chip = el('span', 'chip');
      chip.appendChild(el('span', null, code));
      if (Pack.languages().length > 1) {
        const kill = el('button', 'btn btn-ghost btn-mini', '×');
        kill.type = 'button';
        kill.addEventListener('click', () => Pack.removeLanguage(code));
        chip.appendChild(kill);
      }
      bar.appendChild(chip);
    });
    const add = el('button', 'btn btn-small btn-ghost', '+ ' + T('lang.add'));
    add.type = 'button';
    add.addEventListener('click', () => {
      const code = (window.prompt(T('lang.add.prompt'), 'es_es') || '').trim().toLowerCase();
      if (/^[a-z]{2}_[a-z]{2}$/.test(code)) Pack.addLanguage(code);
    });
    bar.appendChild(add);
    wrap.appendChild(bar);

    if (all.length === 0) {
      wrap.appendChild(el('p', 'muted', T('lang.empty')));
      return wrap;
    }

    const table = el('div', 'lang-table');
    const head = el('div', 'lang-row lang-head');
    head.appendChild(el('span', null, T('lang.key')));
    Pack.languages().forEach((code) => head.appendChild(el('span', null, code)));
    table.appendChild(head);

    all.forEach((key) => {
      const line = el('div', 'lang-row');
      const name = el('span', 'mono small lang-key', key);
      if (!keys.includes(key)) name.classList.add('muted');
      line.appendChild(name);

      Pack.languages().forEach((code) => {
        const input = el('input', 'input');
        input.type = 'text';
        input.value = (Pack.state.lang[code] || {})[key] || '';
        input.addEventListener('input', () => Pack.setTranslation(code, key, input.value));
        input.addEventListener('blur', () => renderChecks(Pack.current()));
        line.appendChild(input);
      });
      table.appendChild(line);
    });
    wrap.appendChild(table);
    return wrap;
  };

  /**
   * Turns the sentences a trainer holds into translation keys, and moves the sentences into
   * the first language of the pack.
   *
   * This is the one thing the editor writes for you that a pack author would otherwise write
   * twice: the key in the trainer, the sentence in the lang file. The keys follow the shape of
   * the example pack - trainer.<ns>.<path>.<field> - so a pack read afterwards looks like one
   * written by hand.
   */
  const keyify = (entry) => {
    const base = 'trainer.' + Pack.state.namespace + '.' + entry.path.replace(/\//g, '.');
    const code = Pack.languages()[0] || 'fr_fr';
    const move = (holder, field, suffix) => {
      const value = holder[field];
      if (!value || Pack.looksLikeKey(value)) return;
      const key = base + '.' + suffix;
      Pack.setTranslation(code, key, value);
      holder[field] = key;
    };

    move(entry.doc, 'name', 'name');
    Object.keys(entry.doc.messages || {}).forEach((field) => move(entry.doc.messages, field, field));
    if (entry.doc.requires) move(entry.doc.requires, 'message', 'locked');
    if (entry.doc.location) {
      ['label', 'arrival', 'busy'].forEach((field) => move(entry.doc.location, field, field));
    }
    Pack.changed();
  };

  /* ---- checks -------------------------------------------------------------- */

  const renderChecks = (entry) => {
    const box = $('checks');
    box.innerHTML = '';
    const found = [
      ...Validate.pack(Pack.state),
      ...(entry ? Validate.file(entry, packView()) : [])
    ];

    const head = el('div', 'checks-head');
    head.appendChild(el('h3', null, T('check.title')));
    const errors = found.filter((one) => one.level === 'error').length;
    const warnings = found.filter((one) => one.level === 'warn').length;
    if (errors) head.appendChild(el('span', 'dot dot-error', String(errors)));
    if (warnings) head.appendChild(el('span', 'dot dot-warn', String(warnings)));
    box.appendChild(head);

    if (found.length === 0) {
      box.appendChild(el('p', 'muted', T('check.ok')));
      return;
    }
    const order = { error: 0, warn: 1, info: 2 };
    found.sort((a, b) => order[a.level] - order[b.level]).forEach((one) => {
      const line = el('div', 'check check-' + one.level);
      line.appendChild(el('span', 'check-mark', one.level === 'error' ? '!' : one.level === 'warn' ? '?' : 'i'));
      line.appendChild(el('span', null, I18N.lang === 'fr' ? one.fr : one.en));
      box.appendChild(line);
    });
  };

  /* ---- the whole thing ------------------------------------------------------ */

  const render = () => {
    if (preview.raf) cancelAnimationFrame(preview.raf);
    // Everything the intro editor holds points at DOM that is about to be thrown away. An
    // image finishing its download asks the stage to repaint itself, and it must not find one
    // belonging to a file nobody has open any more.
    stage = null;
    clock = null;
    cards = [];
    transport = null;
    renderSidebar();

    const main = $('editor');
    main.innerHTML = '';

    if (Pack.state.selected === '__assets__' || Pack.state.selected === '__lang__') {
      const assets = Pack.state.selected === '__assets__';
      const head = el('div', 'editor-head');
      head.appendChild(el('span', 'badge', assets ? T('assets.title') : T('lang.title')));
      main.appendChild(head);
      main.appendChild(assets ? renderAssets() : renderTranslations());
      renderChecks(null);
      return;
    }

    const entry = Pack.current();

    if (!entry) {
      const empty = el('div', 'empty');
      empty.appendChild(el('h2', null, T('pack.empty')));
      const row = el('div', 'row row-wrap');
      ['trainer', 'intro', 'category', 'advancement'].forEach((kind) => {
        const add = el('button', 'btn btn-primary', '+ ' + kindLabel(kind));
        add.type = 'button';
        add.addEventListener('click', () => Pack.add(kind));
        row.appendChild(add);
      });
      empty.appendChild(row);
      main.appendChild(empty);
      renderChecks(null);
      return;
    }

    main.appendChild(renderHeader(entry));
    main.appendChild({
      trainer: renderTrainer,
      intro: renderIntro,
      category: renderCategory,
      advancement: renderAdvancement
    }[entry.kind](entry));
    renderChecks(entry);
  };

  /* ---- chrome --------------------------------------------------------------- */

  const paintChrome = () => {
    document.querySelectorAll('[data-i18n]').forEach((node) => {
      node.textContent = T(node.dataset.i18n);
    });
    document.querySelectorAll('[data-i18n-title]').forEach((node) => {
      node.title = T(node.dataset.i18nTitle);
    });
    document.documentElement.lang = I18N.lang;
    $('lang-fr').classList.toggle('on', I18N.lang === 'fr');
    $('lang-en').classList.toggle('on', I18N.lang === 'en');
  };

  const SHIPPED_TEXTURES = ['rays', 'burst', 'slash', 'banner', 'petal', 'shuriken', 'grid',
    'moon', 'stars', 'scene_manor', 'scene_manor_lights', 'ultra_rift', 'ultra_shard',
    'crest_rerebleue', 'crest_kagumi', 'crest_griff501', 'crest_octavien29', 'crest_theazertor',
    'crest_aeliothys'];

  /** What every field that names something may be offered: the mod's, then the pack's own. */
  const fillLists = () => {
    const fill = (id, values) => {
      const list = $(id);
      list.innerHTML = '';
      values.forEach((value) => {
        const option = document.createElement('option');
        option.value = value;
        list.appendChild(option);
      });
    };

    fill('intro-ids', [...Validate.SHIPPED_INTROS,
      ...Pack.state.files.filter((file) => file.kind === 'intro')
        .map((file) => Pack.state.namespace + ':' + file.path)]);
    fill('music-ids', [...Pack.references('music'), 'cobblemon-trainers:battle_music.b2w2_tournament']);
    fill('sound-ids', [...Pack.references('sound'), ...Pack.references('music')]);
    fill('skin-ids', Pack.references('skin'));
    fill('texture-ids', [...Pack.references('intro_texture'),
      ...SHIPPED_TEXTURES.map((name) => `cobblemon-trainers:textures/gui/intro/${name}.png`)]);
  };

  const loadTemplates = async () => {
    try {
      const response = await fetch('assets/manifest.json');
      if (!response.ok) return;
      templates = await response.json();
      const select = $('templates');
      select.hidden = false;
      (templates.intros || []).forEach((item) => {
        const option = document.createElement('option');
        option.value = 'intro:' + item.file;
        option.textContent = 'Intro · ' + item.id;
        select.appendChild(option);
      });
      (templates.trainers || []).forEach((item) => {
        const option = document.createElement('option');
        option.value = 'trainer:' + item.file;
        option.textContent = 'Dresseur · ' + item.id;
        select.appendChild(option);
      });
      select.addEventListener('change', async () => {
        if (!select.value) return;
        const [kind, file] = select.value.split(':');
        const doc = await (await fetch(file)).json();
        const name = file.split('/').pop().replace('.json', '');
        Pack.add(kind, name, doc);
        select.value = '';
      });
    } catch (e) {
      /* opened straight from the filesystem: templates are the one thing that needs a server */
    }
  };

  const wire = () => {
    $('namespace').addEventListener('input', (event) => {
      Pack.state.namespace = event.target.value.trim().toLowerCase();
      Pack.changed();
    });
    $('description').addEventListener('input', (event) => {
      Pack.state.description = event.target.value;
      Pack.save();
    });

    ['trainer', 'intro', 'category', 'advancement'].forEach((kind) => {
      $('add-' + kind).addEventListener('click', () => Pack.add(kind));
    });

    $('archive').addEventListener('change', (event) => {
      Pack.state.archive = event.target.value;
      Pack.changed();
    });

    $('export').addEventListener('click', () => Pack.zip());
    $('reset').addEventListener('click', () => {
      if (window.confirm(T('pack.reset.confirm'))) Pack.clear();
    });

    const file = $('import-file');
    $('import').addEventListener('click', () => file.click());
    file.addEventListener('change', async () => {
      for (const one of file.files) {
        if (one.name.endsWith('.zip') || one.name.endsWith('.jar')) await Pack.readZip(one);
        else Pack.readJson(await one.text(), one.name);
      }
      file.value = '';
    });

    document.body.addEventListener('dragover', (event) => event.preventDefault());
    document.body.addEventListener('drop', async (event) => {
      if (!event.dataTransfer.files.length) return;
      const one = event.dataTransfer.files[0];
      const name = one.name.toLowerCase();
      if (name.endsWith('.zip') || name.endsWith('.jar')) {
        event.preventDefault();
        await Pack.readZip(one);
      } else if (name.endsWith('.json')) {
        event.preventDefault();
        Pack.readJson(await one.text(), one.name);
      } else if (name.endsWith('.ogg') || name.endsWith('.png')) {
        // A file dropped on nothing in particular is judged by what is open: a png while an
        // intro is being written is a layer's image, anywhere else it is a skin.
        event.preventDefault();
        const open = Pack.current();
        const kind = name.endsWith('.ogg')
          ? (open && open.kind === 'intro' ? 'sound' : 'music')
          : (open && open.kind === 'intro' ? 'intro_texture' : 'skin');
        const asset = await take(one, kind);
        if (asset) Pack.select('__assets__');
      }
    });

    $('lang-fr').addEventListener('click', () => I18N.set('fr'));
    $('lang-en').addEventListener('click', () => I18N.set('en'));
  };

  const start = () => {
    I18N.restore();
    const had = Pack.restore();
    wire();
    loadTemplates();

    I18N.onChange(() => { paintChrome(); render(); });
    let namespace = Pack.state.namespace;
    Pack.onChange(() => {
      $('namespace').value = Pack.state.namespace;
      $('description').value = Pack.state.description;
      $('archive').value = Pack.state.archive;
      // Every reference holds the namespace, so renaming the pack renames its images too.
      if (Pack.state.namespace !== namespace) {
        namespace = Pack.state.namespace;
        feedAll();
      }
      fillLists();
      render();
    });

    $('namespace').value = Pack.state.namespace;
    $('description').value = Pack.state.description;
    $('archive').value = Pack.state.archive;
    // The bytes outlive the page: a skin dropped yesterday has to draw itself again today.
    feedAll();
    if (!had) Pack.add('trainer', 'champions/erika');
    paintChrome();
    fillLists();
    render();
  };

  return { start };
})();

document.addEventListener('DOMContentLoaded', App.start);
