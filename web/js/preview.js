/*
 * The versus screen, replayed in a canvas.
 *
 * This is BattleIntroScreen.kt transcribed: the same easing, the same entrances, the same
 * common fade-out, the same 640 x 360 the mod scales from - so the canvas is drawn at exactly
 * that size and blown up by CSS, which makes uiScale 1 and the anchors land where the game
 * puts them.
 *
 * What cannot be transcribed is anything that needs the game: a figure is the player's or the
 * trainer's real model in game, a `pokemon` layer is a Cobblemon model. Both are drawn flat
 * here - the figure exactly the way the mod itself falls back when the entity is missing, the
 * Pokemon as a marked placeholder. Everything a pack actually writes - placement, colour,
 * timing, entrance - is the real thing.
 */

const Preview = (() => {
  const WIDTH = 640;
  const HEIGHT = 360;
  const BALL_SIZE = 10;
  const FIGURE_W = 16;
  const FIGURE_H = 32;
  const BACK_C1 = 1.70158;
  const BACK_C3 = BACK_C1 + 1;
  const SHIPPED = 'cobblemon-trainers:textures/gui/intro/';

  /* ---- images ---------------------------------------------------------- */

  const images = new Map();   // id -> HTMLImageElement | 'missing'
  const dropped = new Map();  // id -> data url a user gave us

  const load = (id, src) => {
    const image = new Image();
    image.crossOrigin = 'anonymous';
    image.onload = () => { images.set(id, image); if (Preview.onRepaint) Preview.onRepaint(); };
    image.onerror = () => { images.set(id, 'missing'); if (Preview.onRepaint) Preview.onRepaint(); };
    image.src = src;
    images.set(id, null);
    return null;
  };

  /** The image behind a texture id, or null while it loads, or 'missing'. */
  const texture = (id) => {
    if (!id) return 'missing';
    if (dropped.has(id)) {
      const held = images.get('drop:' + id);
      if (held !== undefined) return held;
      return load('drop:' + id, dropped.get(id));
    }
    if (images.has(id)) return images.get(id);
    if (id.startsWith(SHIPPED)) return load(id, 'assets/intro/' + id.slice(SHIPPED.length));
    images.set(id, 'missing');
    return 'missing';
  };

  const give = (id, dataUrl) => {
    dropped.set(id, dataUrl);
    images.delete('drop:' + id);
  };

  /* ---- skins ----------------------------------------------------------- */

  const skins = new Map();

  /** A player skin, by username or uuid, through a service that answers CORS. */
  const skin = (name) => {
    const key = (name || '').trim().toLowerCase();
    if (!key) return null;
    if (skins.has(key)) return skins.get(key);
    const image = new Image();
    image.crossOrigin = 'anonymous';
    image.onload = () => { skins.set(key, image); if (Preview.onRepaint) Preview.onRepaint(); };
    image.onerror = () => { skins.set(key, 'missing'); if (Preview.onRepaint) Preview.onRepaint(); };
    image.src = 'https://crafthead.net/skin/' + encodeURIComponent(key);
    skins.set(key, null);
    return null;
  };

  /**
   * A skin drawn flat, face by face, the way TrainerSkinRenderer does it in game.
   *
   * @param scale screen pixels per skin pixel.
   */
  const figure = (ctx, image, slim, centerX, top, scale) => {
    const armWidth = slim ? 3 : 4;
    const legacy = image.height < 64;
    const left = centerX - ((8 + armWidth * 2) * scale) / 2;

    const part = (dx, dy, w, h, u, v) => ctx.drawImage(
      image, u, v, w, h,
      Math.round(left + dx * scale), Math.round(top + dy * scale),
      Math.round(w * scale), Math.round(h * scale)
    );

    part(armWidth, 0, 8, 8, 8, 8);
    part(armWidth, 8, 8, 12, 20, 20);
    part(0, 8, armWidth, 12, 44, 20);
    part(armWidth + 8, 8, armWidth, 12, legacy ? 44 : 36, legacy ? 20 : 52);
    part(armWidth, 20, 4, 12, 4, 20);
    part(armWidth + 4, 20, 4, 12, legacy ? 4 : 20, legacy ? 20 : 52);

    part(armWidth, 0, 8, 8, 40, 8);
    if (!legacy) {
      part(armWidth, 8, 8, 12, 20, 36);
      part(0, 8, armWidth, 12, 44, 36);
      part(armWidth + 8, 8, armWidth, 12, 52, 52);
      part(armWidth, 20, 4, 12, 4, 36);
      part(armWidth + 4, 20, 4, 12, 4, 52);
    }
  };

  /* ---- the maths of the screen ----------------------------------------- */

  const eased = (progress) => {
    const remaining = 1 - Math.min(Math.max(progress, 0), 1);
    return 1 - remaining * remaining * remaining;
  };

  const ease = (name, value) => {
    if (name === 'linear') return value;
    if (name === 'in') return value * value * value;
    return eased(value);
  };

  const popped = (value) => {
    const back = value - 1;
    return 1 + BACK_C3 * back * back * back + BACK_C1 * back * back;
  };

  const lerp = (from, to, amount) => from + (to - from) * amount;

  const anchorX = (anchor) => (String(anchor).endsWith('left') ? 0
    : String(anchor).endsWith('right') ? WIDTH : WIDTH / 2);

  const anchorY = (anchor) => (String(anchor).startsWith('top') ? 0
    : String(anchor).startsWith('bottom') ? HEIGHT : HEIGHT / 2);

  const textWidth = (ctx, value, size) => {
    ctx.save();
    ctx.font = font(size);
    const measured = ctx.measureText(value).width;
    ctx.restore();
    return measured;
  };

  const font = (size) => Math.round(9 * size) + 'px "Jersey 10", "Minecraftia", monospace';

  const span = (ctx, layer) => {
    switch (layer.type) {
      case 'figure': return layer.height ?? 96;
      case 'pokemon': return layer.height ?? 64;
      case 'image': return (layer.width ?? 96) / 2;
      case 'fill': return layer.width !== undefined && layer.width !== null ? layer.width / 2 : WIDTH;
      case 'team_balls': return ((layer.slots ?? 6) * (BALL_SIZE + (layer.gap ?? 3))) / 2;
      case 'text': return textWidth(ctx, String(layer.value ?? ''), layer.size ?? 1) / 2 + 8;
      default: return textWidth(ctx, 'VS', layer.size ?? 1) / 2 + 8;
    }
  };

  /**
   * What a layer covers at rest, in the 640 x 360 the screen is written on.
   *
   * `span` above answers a different question - how far off screen an entrance starts - and
   * the two must not be merged: that one is a half-width with slack in it, this one is the
   * real box the stage lets an author grab.
   */
  const extent = (ctx, layer, about) => {
    switch (layer.type) {
      case 'fill': {
        // A slant leans every row of the band sideways, up to half its height times the slant
        // on each side: the nominal width alone would leave the top and bottom of a band like
        // the mod's own ungrabbable.
        const w = layer.width ?? WIDTH;
        const h = layer.height ?? HEIGHT;
        return [w + h * Math.abs(layer.slant ?? 0), h];
      }
      case 'figure': {
        const pixels = Math.max(1, Math.round((layer.height ?? 96) / FIGURE_H));
        return [FIGURE_W * pixels, FIGURE_H * pixels];
      }
      case 'text': {
        const size = layer.size ?? 1;
        const value = resolve(layer.value, about || {});
        return [Math.max(textWidth(ctx, value, size), 6), Math.max(10 * size, 6)];
      }
      case 'vs': {
        const size = layer.size ?? 1;
        return [Math.max(textWidth(ctx, 'VS', size), 6), Math.max(10 * size, 6)];
      }
      case 'image': {
        const image = texture(layer.texture);
        const known = image && image !== 'missing' ? image : null;
        return [layer.width ?? (known ? known.width : 64), layer.height ?? (known ? known.height : 64)];
      }
      case 'team_balls': {
        const slots = Math.min(Math.max(layer.slots ?? 6, 1), 6);
        const size = BALL_SIZE * (layer.size ?? 1);
        const gap = layer.gap ?? 3;
        return [slots * (size + gap) - gap, size];
      }
      case 'pokemon': {
        const tall = layer.height ?? 64;
        return [tall, tall];
      }
      default: return [16, 16];
    }
  };

  /** Where a layer comes to rest: its anchor plus its offset. */
  const rest = (layer) => [
    anchorX(layer.anchor ?? 'center') + (layer.offset ? layer.offset[0] : 0),
    anchorY(layer.anchor ?? 'center') + (layer.offset ? layer.offset[1] : 0)
  ];

  /** The same, as a box - what the stage drags, resizes and snaps to. */
  const restBox = (canvas, layer, about) => {
    const [x, y] = rest(layer);
    const [w, h] = extent(canvas.getContext('2d'), layer, about);
    return { x, y, w, h, left: x - w / 2, top: y - h / 2 };
  };

  /** The five marks a `text` layer may carry, filled in the way the screen fills them. */
  const resolve = (value, about) => String(value ?? '')
    .replace(/%name%/g, about.name || 'Trainer')
    .replace(/%category%/g, about.category || '')
    .replace(/%level%/g, about.level ?? 1)
    .replace(/%team%/g, about.team ?? 0)
    .replace(/%player%/g, about.player || 'RereBleue');

  const rgb = (color, fallback) => {
    const text = String(color ?? '').trim().replace('#', '');
    return /^[0-9a-fA-F]{6}$/.test(text) ? '#' + text : fallback;
  };

  /* ---- drawing --------------------------------------------------------- */

  const tinted = (image, color) => {
    const buffer = document.createElement('canvas');
    buffer.width = image.width;
    buffer.height = image.height;
    const ctx = buffer.getContext('2d');
    ctx.drawImage(image, 0, 0);
    ctx.globalCompositeOperation = 'multiply';
    ctx.fillStyle = color;
    ctx.fillRect(0, 0, buffer.width, buffer.height);
    ctx.globalCompositeOperation = 'destination-in';
    ctx.drawImage(image, 0, 0);
    return buffer;
  };

  const ball = (ctx, x, y, size) => {
    const r = size / 2;
    ctx.save();
    ctx.translate(x + r, y + r);
    ctx.beginPath();
    ctx.arc(0, 0, r, Math.PI, 0);
    ctx.fillStyle = '#D8453C';
    ctx.fill();
    ctx.beginPath();
    ctx.arc(0, 0, r, 0, Math.PI);
    ctx.fillStyle = '#F1F1F1';
    ctx.fill();
    ctx.beginPath();
    ctx.arc(0, 0, r, 0, Math.PI * 2);
    ctx.lineWidth = Math.max(1, size / 8);
    ctx.strokeStyle = '#1B1B1F';
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(-r, 0);
    ctx.lineTo(r, 0);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(0, 0, Math.max(1.5, r / 3), 0, Math.PI * 2);
    ctx.fillStyle = '#F1F1F1';
    ctx.fill();
    ctx.stroke();
    ctx.restore();
  };

  const silhouette = (ctx, x, y, height, alpha, marked) => {
    const pixels = Math.max(1, Math.round(height / FIGURE_H));
    const w = FIGURE_W * pixels;
    const h = FIGURE_H * pixels;
    ctx.save();
    ctx.globalAlpha = alpha * 0.8;
    ctx.fillStyle = '#101018';
    ctx.fillRect(Math.round(x - w / 2), Math.round(y - h / 2), w, h);
    if (marked) {
      ctx.globalAlpha = alpha;
      ctx.fillStyle = '#6C7BA8';
      ctx.font = font(1.6);
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(marked, x, y);
    }
    ctx.restore();
  };

  const draw = {
    fill(ctx, layer, x, y, scale, alpha) {
      const w = (layer.width ?? WIDTH) * scale;
      const h = (layer.height ?? HEIGHT) * scale;
      ctx.save();
      ctx.globalAlpha = alpha;
      ctx.fillStyle = rgb(layer.color, '#000000');
      const top = Math.round(y - h / 2);
      const bottom = Math.round(y + h / 2);
      const half = w / 2;
      const slant = layer.slant ?? 0;
      if (!slant) {
        ctx.fillRect(Math.round(x - half), top, Math.round(w), bottom - top);
      } else {
        for (let row = top; row < bottom; row += 2) {
          const rowHeight = Math.min(2, bottom - row);
          const lean = (y - row) * slant;
          ctx.fillRect(Math.round(x + lean - half), row, Math.round(w), rowHeight);
        }
      }
      ctx.restore();
    },

    figure(ctx, layer, x, y, scale, alpha, about) {
      const tall = (layer.height ?? 96) * scale;
      const who = layer.who === 'player' ? about.player : about.trainerSkin;
      // A trainer wearing a skin the pack ships names it the way a texture is named; anything
      // else is an account, and goes through the skin service.
      const image = String(who || '').includes(':') ? texture(who) : skin(who);
      ctx.save();
      ctx.globalAlpha = alpha;
      if (image && image !== 'missing') {
        const pixels = Math.max(1, Math.round(tall / FIGURE_H));
        figure(ctx, image, layer.who === 'player' ? about.playerSlim : about.trainerSlim,
               x, y - (FIGURE_H * pixels) / 2, pixels);
      } else {
        silhouette(ctx, x, y, tall, alpha, null);
      }
      ctx.restore();
    },

    text(ctx, layer, x, y, scale, alpha, about) {
      const value = resolve(layer.value, about);
      if (!value) return;
      ctx.save();
      ctx.globalAlpha = alpha;
      ctx.font = font((layer.size ?? 1) * scale);
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      if (layer.shadow !== false) {
        ctx.fillStyle = 'rgba(0,0,0,0.55)';
        ctx.fillText(value, x + 1.5 * scale, y + 1.5 * scale);
      }
      ctx.fillStyle = rgb(layer.color, '#FFFFFF');
      ctx.fillText(value, x, y);
      ctx.restore();
    },

    vs(ctx, layer, x, y, scale, alpha) {
      const size = (layer.size ?? 1) * scale;
      ctx.save();
      ctx.globalAlpha = alpha;
      ctx.font = font(size);
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillStyle = '#000000';
      for (let dx = -1; dx <= 1; dx += 1) {
        for (let dy = -1; dy <= 1; dy += 1) {
          if (dx || dy) ctx.fillText('VS', x + dx, y + dy);
        }
      }
      ctx.fillStyle = rgb(layer.color, '#FFF0C0');
      ctx.fillText('VS', x, y);
      ctx.restore();
    },

    image(ctx, layer, x, y, scale, alpha) {
      const image = texture(layer.texture);
      if (!image || image === 'missing') {
        if (image === 'missing') {
          const w = (layer.width ?? 64) * scale;
          const h = (layer.height ?? 64) * scale;
          ctx.save();
          ctx.globalAlpha = alpha * 0.5;
          ctx.strokeStyle = rgb(layer.color, '#FFFFFF');
          ctx.setLineDash([4, 4]);
          ctx.strokeRect(Math.round(x - w / 2), Math.round(y - h / 2), Math.round(w), Math.round(h));
          ctx.restore();
        }
        return;
      }
      const w = (layer.width ?? image.width) * scale;
      const h = (layer.height ?? image.height) * scale;
      const tint = rgb(layer.color, '#FFFFFF');
      const source = tint.toUpperCase() === '#FFFFFF' ? image : tinted(image, tint);
      ctx.save();
      ctx.globalAlpha = alpha;
      ctx.drawImage(source, Math.round(x - w / 2), Math.round(y - h / 2), Math.round(w), Math.round(h));
      ctx.restore();
    },

    team_balls(ctx, layer, x, y, scale, alpha, about) {
      const slots = Math.min(Math.max(layer.slots ?? 6, 1), 6);
      const held = layer.who === 'player' ? 6 : (about.team ?? 0);
      const filled = Math.min(Math.max(held, 0), slots);
      const size = BALL_SIZE * (layer.size ?? 1) * scale;
      const gap = (layer.gap ?? 3) * scale;
      const step = size + gap;
      const left = x - (slots * step - gap) / 2;
      const top = y - size / 2;

      for (let slot = 0; slot < slots; slot += 1) {
        if (slot >= filled && layer.empty === false) continue;
        const ballX = left + slot * step;
        ctx.save();
        ctx.globalAlpha = alpha;
        ball(ctx, ballX, top, size);
        if (slot >= filled) {
          ctx.globalAlpha = alpha * 0.6;
          ctx.fillStyle = '#000000';
          ctx.fillRect(ballX, top, size, size);
        }
        ctx.restore();
      }
    },

    pokemon(ctx, layer, x, y, scale, alpha) {
      const tall = (layer.height ?? 64) * scale;
      ctx.save();
      ctx.globalAlpha = alpha * 0.85;
      ctx.fillStyle = '#161B2B';
      ctx.strokeStyle = '#3C4A72';
      ctx.setLineDash([3, 3]);
      ctx.fillRect(Math.round(x - tall / 2), Math.round(y - tall / 2), Math.round(tall), Math.round(tall));
      ctx.strokeRect(Math.round(x - tall / 2), Math.round(y - tall / 2), Math.round(tall), Math.round(tall));
      ctx.setLineDash([]);
      ctx.globalAlpha = alpha;
      ball(ctx, x - tall / 5, y - tall / 5, tall / 2.5);
      ctx.fillStyle = '#8A97BE';
      ctx.font = font(0.9);
      ctx.textAlign = 'center';
      ctx.fillText('#' + (layer.slot ?? 1), x, y + tall / 2 - 4);
      ctx.restore();
    }
  };

  /**
   * One frame of a scene.
   *
   * @param about name, category, level, team size, the two skins - what the server sends along
   *   with the scene, and what the marks of a `text` layer are filled from.
   * @param options `layout: true` draws every layer where it comes to rest, at full strength
   *   and whatever the tick - the arrangement an author is placing rather than one moment of
   *   the animation. That is the editor's own view; the mod never draws it.
   * @return the box each layer was drawn in, by index, for a stage to hit-test against.
   */
  const frame = (canvas, scene, elapsed, about, hidden, options) => {
    const layout = Boolean(options && options.layout);
    const ctx = canvas.getContext('2d');
    ctx.imageSmoothingEnabled = false;
    ctx.clearRect(0, 0, WIDTH, HEIGHT);

    const ticks = Math.max(scene.duration ?? 100, 1);
    const fadeIn = Math.max(scene.fadeIn ?? 4, 1);
    const fadeOut = Math.max(scene.fadeOut ?? 8, 1);
    const leaving = layout ? 1 : 1 - eased((elapsed - (ticks - fadeOut)) / fadeOut);
    const screenAlpha = layout ? 1 : eased(elapsed / fadeIn) * leaving;
    const boxes = [];
    if (screenAlpha <= 0) return boxes;

    (scene.layers || []).forEach((layer, index) => {
      if (hidden && hidden.has(index)) return;
      if (!layout && elapsed < (layer.at ?? 0)) return;
      if (!draw[layer.type]) return;

      const since = elapsed - (layer.at ?? 0);
      const over = Math.max(layer.for ?? 12, 1);
      const entrance = layout ? 1 : ease(layer.ease ?? 'out', Math.min(Math.max(since / over, 0), 1));

      const restX = anchorX(layer.anchor ?? 'center') + (layer.offset ? layer.offset[0] : 0);
      const restY = anchorY(layer.anchor ?? 'center') + (layer.offset ? layer.offset[1] : 0);
      const reach = span(ctx, layer);

      let x = restX;
      let y = restY;
      let alpha = screenAlpha * (layer.alpha ?? 1);
      let scale = 1;

      switch (layout ? 'none' : (layer.from ?? 'fade')) {
        case 'left': x = lerp(-reach, restX, entrance); break;
        case 'right': x = lerp(WIDTH + reach, restX, entrance); break;
        case 'top': y = lerp(-reach, restY, entrance); break;
        case 'bottom': y = lerp(HEIGHT + reach, restY, entrance); break;
        case 'pop':
          alpha *= Math.min(entrance * 2, 1);
          scale = popped(entrance);
          break;
        case 'none': break;
        default: alpha *= entrance;
      }

      if (alpha <= 0) return;

      // A model blends nothing in game, so a figure leaves by going rather than by fading.
      if (!layout && (layer.type === 'figure' || layer.type === 'pokemon')) {
        switch (layer.from) {
          case 'left': x = lerp(-reach, x, leaving); break;
          case 'right': x = lerp(WIDTH + reach, x, leaving); break;
          case 'top': y = lerp(-reach, y, leaving); break;
          case 'bottom': y = lerp(HEIGHT + reach, y, leaving); break;
          default: scale *= leaving;
        }
        if (scale <= 0) return;
      }

      const [w, h] = extent(ctx, layer, about);
      boxes[index] = { index, x, y, w: w * scale, h: h * scale };
      draw[layer.type](ctx, layer, x, y, scale, Math.min(alpha, 1), about);
    });

    return boxes;
  };

  return { frame, give, texture, skin, extent, rest, restBox, anchorX, anchorY,
           WIDTH, HEIGHT, onRepaint: null, drawFlatSkin: figure };
})();
