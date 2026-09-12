/*
 * The stage: the intro preview made into something you can point at.
 *
 * `preview.js` answers "what does this scene look like at tick 40". This file answers the two
 * questions an author actually has in front of that picture - "which layer is that" and "put
 * it there" - and writes the answer into the very object the file is made of, so a drag and a
 * typed number are the same edit.
 *
 * Two views, and the split is the whole idea:
 *
 *   layout     every layer where it comes to rest, at full strength, whatever the tick. This
 *              is the arrangement, and the only view that can be dragged in: an entrance
 *              interpolates a layer's position, so a pixel of mouse there would not be a pixel
 *              of offset - it would be a pixel times however far into the entrance we are.
 *   animation  the real frame, the one the mod draws. Read only, on purpose.
 *
 * Nothing is drawn on the scene canvas itself: handles, guides and the anchor tether live on a
 * second canvas laid over it, at twice the resolution, so the scene stays the pixellated
 * 640 x 360 the mod scales from while the chrome around it stays sharp.
 */

const Stage = (() => {
  const W = 640;
  const H = 360;
  const SNAP = 4;          // how close to a line before it takes, in scene pixels
  const GRAB = 6;          // how close to a handle counts as grabbing it
  const HANDLE = 7;        // the drawn square

  /**
   * What a corner handle changes, per layer type.
   *
   * `free` has a width and a height of its own; `height` has only the one and keeps its
   * proportions; `scale` has neither, only a multiplier. A type absent from here cannot be
   * resized on the stage at all - there would be nothing to write.
   */
  const SIZING = {
    fill: { free: true },
    image: { free: true },
    figure: { height: true },
    pokemon: { height: true },
    text: { scale: 'size' },
    vs: { scale: 'size' },
    team_balls: { scale: 'size' }
  };

  const HANDLES = [
    { x: -1, y: -1, cursor: 'nwse-resize' }, { x: 0, y: -1, cursor: 'ns-resize' },
    { x: 1, y: -1, cursor: 'nesw-resize' }, { x: 1, y: 0, cursor: 'ew-resize' },
    { x: 1, y: 1, cursor: 'nwse-resize' }, { x: 0, y: 1, cursor: 'ns-resize' },
    { x: -1, y: 1, cursor: 'nesw-resize' }, { x: -1, y: 0, cursor: 'ew-resize' }
  ];

  /** The handles a type offers: something proportional is only grabbed by a corner. */
  const handlesFor = (type) => {
    const kind = SIZING[type];
    if (!kind) return [];
    return kind.free ? HANDLES : HANDLES.filter((one) => one.x && one.y);
  };

  const clamp = (value, low, high) => Math.min(Math.max(value, low), high);

  /* ---- the scene canvas ------------------------------------------------- */

  /**
   * @param opts canvas and overlay, the two stacked canvases; scene() the intro document,
   *   layers() its layers, about() what a mark resolves against, state the shared preview
   *   state (tick, mode, hidden, selected, snap, grid), and the four ways out: onSelect(index),
   *   onEdit() during a drag, onCommit() once it ends, onRemove / onDuplicate for the keyboard.
   */
  const create = (opts) => {
    const { canvas, overlay, state } = opts;
    overlay.width = W * 2;
    overlay.height = H * 2;

    let boxes = [];       // what the last paint drew, by layer index
    let hover = null;     // the index under the pointer
    let drag = null;
    let guides = [];      // the lines a snap is holding on to, drawn while dragging

    const layers = () => opts.layers() || [];
    const layout = () => state.mode !== 'play';

    /** A layer's box on the stage: at rest in layout, where it was drawn in animation. */
    const boxOf = (index) => {
      const layer = layers()[index];
      if (!layer) return null;
      return layout() ? Preview.restBox(canvas, layer, opts.about()) : boxes[index] || null;
    };

    /* -- painting -- */

    const paintOverlay = () => {
      const ctx = overlay.getContext('2d');
      ctx.setTransform(2, 0, 0, 2, 0, 0);
      ctx.clearRect(0, 0, W, H);
      ctx.lineWidth = 1;

      if (state.grid && layout()) {
        ctx.save();
        ctx.strokeStyle = 'rgba(120, 150, 220, 0.16)';
        ctx.beginPath();
        [W / 3, W / 2, (W * 2) / 3].forEach((x) => { ctx.moveTo(x, 0); ctx.lineTo(x, H); });
        [H / 3, H / 2, (H * 2) / 3].forEach((y) => { ctx.moveTo(0, y); ctx.lineTo(W, y); });
        ctx.stroke();
        ctx.restore();
      }

      if (hover !== null && hover !== state.selected) {
        const box = boxOf(hover);
        if (box) {
          ctx.strokeStyle = 'rgba(140, 170, 235, 0.7)';
          ctx.setLineDash([3, 3]);
          ctx.strokeRect(Math.round(box.left) + 0.5, Math.round(box.top) + 0.5,
                         Math.max(Math.round(box.w) - 1, 1), Math.max(Math.round(box.h) - 1, 1));
          ctx.setLineDash([]);
        }
      }

      guides.forEach((guide) => {
        ctx.strokeStyle = 'rgba(255, 180, 80, 0.85)';
        ctx.beginPath();
        if (guide.axis === 'x') { ctx.moveTo(guide.at + 0.5, 0); ctx.lineTo(guide.at + 0.5, H); }
        else { ctx.moveTo(0, guide.at + 0.5); ctx.lineTo(W, guide.at + 0.5); }
        ctx.stroke();
      });

      const chosen = state.selected;
      const layer = chosen === null || chosen === undefined ? null : layers()[chosen];
      const box = layer ? boxOf(chosen) : null;
      if (!box) return;

      // The tether says what an offset is measured from: without it the anchor menu below is a
      // word with no picture, and dragging a layer teaches nothing about where it holds on.
      const ax = Preview.anchorX(layer.anchor ?? 'center');
      const ay = Preview.anchorY(layer.anchor ?? 'center');
      if (layout()) {
        ctx.save();
        ctx.strokeStyle = 'rgba(120, 150, 220, 0.55)';
        ctx.setLineDash([2, 3]);
        ctx.beginPath();
        ctx.moveTo(ax, ay);
        ctx.lineTo(box.x, box.y);
        ctx.stroke();
        ctx.restore();
        ctx.fillStyle = 'rgba(120, 150, 220, 0.9)';
        ctx.fillRect(clamp(ax, 2, W - 2) - 2, clamp(ay, 2, H - 2) - 2, 4, 4);
      }

      ctx.strokeStyle = '#4c8bf5';
      ctx.strokeRect(Math.round(box.left) + 0.5, Math.round(box.top) + 0.5,
                     Math.max(Math.round(box.w) - 1, 1), Math.max(Math.round(box.h) - 1, 1));

      if (!layout()) return;
      handlesFor(layer.type).forEach((one) => {
        const hx = Math.round(box.x + (one.x * box.w) / 2);
        const hy = Math.round(box.y + (one.y * box.h) / 2);
        ctx.fillStyle = '#0b0e17';
        ctx.fillRect(hx - HANDLE / 2, hy - HANDLE / 2, HANDLE, HANDLE);
        ctx.strokeRect(hx - HANDLE / 2 + 0.5, hy - HANDLE / 2 + 0.5, HANDLE - 1, HANDLE - 1);
      });
    };

    const paint = () => {
      boxes = Preview.frame(canvas, opts.scene(), state.tick, opts.about(), state.hidden,
                            { layout: layout() });
      paintOverlay();
      if (opts.onPaint) opts.onPaint();
    };

    /* -- pointing -- */

    const at = (event) => {
      const rect = overlay.getBoundingClientRect();
      return [((event.clientX - rect.left) / rect.width) * W,
              ((event.clientY - rect.top) / rect.height) * H];
    };

    const handleAt = (px, py) => {
      const chosen = state.selected;
      if (chosen === null || chosen === undefined) return null;
      const layer = layers()[chosen];
      const box = layer ? boxOf(chosen) : null;
      if (!box) return null;
      return handlesFor(layer.type).find((one) =>
        Math.abs(px - (box.x + (one.x * box.w) / 2)) <= GRAB
        && Math.abs(py - (box.y + (one.y * box.h) / 2)) <= GRAB) || null;
    };

    /** The topmost layer under the pointer - topmost, because that is the one on top. */
    const layerAt = (px, py) => {
      const list = layers();
      for (let index = list.length - 1; index >= 0; index -= 1) {
        if (state.hidden.has(index)) continue;
        const box = boxOf(index);
        if (!box) continue;
        // A one-pixel rule and a small text are things a pack writes; they still have to be
        // grabbable, so anything thin is given a little room around it.
        const slack = Math.min(box.w, box.h) < 10 ? 4 : 0;
        if (px >= box.left - slack && px <= box.left + box.w + slack
            && py >= box.top - slack && py <= box.top + box.h + slack) return index;
      }
      return null;
    };

    /* -- snapping -- */

    /** Every line worth landing on: the screen's own, then what the other layers offer. */
    const lines = (skip) => {
      const x = [0, W / 2, W];
      const y = [0, H / 2, H];
      layers().forEach((layer, index) => {
        if (index === skip || state.hidden.has(index)) return;
        const box = Preview.restBox(canvas, layer, opts.about());
        x.push(box.left, box.x, box.left + box.w);
        y.push(box.top, box.y, box.top + box.h);
      });
      return { x, y };
    };

    /** Pulls one axis of a moving box onto the nearest line, and says which line took it. */
    const magnet = (center, half, candidates) => {
      let best = null;
      [-half, 0, half].forEach((edge) => {
        candidates.forEach((line) => {
          const delta = line - (center + edge);
          if (Math.abs(delta) <= SNAP && (!best || Math.abs(delta) < Math.abs(best.delta))) {
            best = { delta, at: line };
          }
        });
      });
      return best;
    };

    /* -- editing -- */

    const setOffset = (layer, x, y) => {
      const dx = Math.round(x - Preview.anchorX(layer.anchor ?? 'center'));
      const dy = Math.round(y - Preview.anchorY(layer.anchor ?? 'center'));
      if (dx === 0 && dy === 0) delete layer.offset;
      else layer.offset = [dx, dy];
    };

    const move = (layer, px, py, event) => {
      let x = drag.box.x + (px - drag.from[0]);
      let y = drag.box.y + (py - drag.from[1]);

      if (event.shiftKey) {
        if (Math.abs(px - drag.from[0]) > Math.abs(py - drag.from[1])) y = drag.box.y;
        else x = drag.box.x;
      }

      guides = [];
      if (state.snap && !event.altKey) {
        const near = lines(drag.index);
        const onX = magnet(x, drag.box.w / 2, near.x);
        const onY = magnet(y, drag.box.h / 2, near.y);
        if (onX) { x += onX.delta; guides.push({ axis: 'x', at: onX.at }); }
        if (onY) { y += onY.delta; guides.push({ axis: 'y', at: onY.at }); }
      }
      setOffset(layer, x, y);
    };

    const resize = (layer, px, py, event) => {
      const one = drag.handle;
      const base = drag.box;
      const kind = SIZING[layer.type];

      const spanW = one.x === 1 ? px - base.left
        : one.x === -1 ? base.left + base.w - px : base.w;
      const spanH = one.y === 1 ? py - base.top
        : one.y === -1 ? base.top + base.h - py : base.h;
      let fw = Math.max(0.02, spanW / Math.max(base.w, 1));
      let fh = Math.max(0.02, spanH / Math.max(base.h, 1));

      // Only a fill and an image have two sizes to give; everything else grows as one, and
      // Shift asks a fill to do the same.
      if (!kind.free || event.shiftKey) {
        const both = one.y ? fh : fw;
        fw = both;
        fh = both;
      }

      if (kind.free) {
        // Only the axis the handle actually moves is written. Setting both would pin a fill's
        // height to 360 for having pulled its side - the same shape, but no longer the "empty
        // means the whole screen" a pack wrote.
        const both = event.shiftKey;
        if (one.x || both) layer.width = Math.max(1, Math.round(drag.width * fw));
        if (one.y || both) layer.height = Math.max(1, Math.round(drag.height * fh));
      } else if (kind.height) {
        layer.height = Math.max(1, Math.round(drag.height * fh));
      } else {
        layer[kind.scale] = Math.max(0.1, Math.round(drag.size * fh * 10) / 10);
      }

      // The field is not the box - a figure rounds to whole skin pixels, a size grows text by
      // however much the font grows - so the grabbed corner is held by re-measuring rather
      // than by trusting the number we just wrote.
      const now = Preview.restBox(canvas, layer, opts.about());
      const x = one.x === 0 ? base.x
        : one.x === 1 ? base.left + now.w / 2 : base.left + base.w - now.w / 2;
      const y = one.y === 0 ? base.y
        : one.y === 1 ? base.top + now.h / 2 : base.top + base.h - now.h / 2;
      setOffset(layer, x, y);
    };

    /* -- events -- */

    const onDown = (event) => {
      if (event.button !== 0) return;
      // Focusing scrolls the element into view unless told not to, and the canvas is the
      // one thing that must not move when it is clicked.
      overlay.focus({ preventScroll: true });
      const [px, py] = at(event);

      if (!layout()) {
        const found = layerAt(px, py);
        if (found !== null) opts.onSelect(found);
        return;
      }

      const handle = handleAt(px, py);
      const index = handle ? state.selected : layerAt(px, py);
      if (index === null || index === undefined) {
        if (state.selected !== null && state.selected !== undefined) opts.onSelect(null);
        return;
      }
      if (index !== state.selected) opts.onSelect(index);

      const layer = layers()[index];
      const box = Preview.restBox(canvas, layer, opts.about());
      drag = {
        index, handle, box, from: [px, py],
        width: layer.width ?? box.w,
        height: layer.height ?? box.h,
        size: layer.size ?? 1,
        moved: false
      };
      overlay.setPointerCapture(event.pointerId);
      event.preventDefault();
    };

    const onMove = (event) => {
      const [px, py] = at(event);

      if (!drag) {
        const handle = layout() ? handleAt(px, py) : null;
        const found = layout() ? layerAt(px, py) : null;
        overlay.style.cursor = handle ? handle.cursor : (found !== null ? 'move' : 'default');
        if (found !== hover) { hover = found; paintOverlay(); }
        return;
      }

      const layer = layers()[drag.index];
      if (!layer) return;
      drag.moved = true;
      if (drag.handle) resize(layer, px, py, event);
      else move(layer, px, py, event);
      opts.onEdit();
      paint();
    };

    const onUp = () => {
      if (!drag) return;
      const moved = drag.moved;
      drag = null;
      guides = [];
      // A drag that never moved is a click that has already selected: rebuilding the editor
      // for it would cost the layer card its scroll position for nothing.
      if (moved) opts.onCommit();
      else paintOverlay();
    };

    const onKey = (event) => {
      const chosen = state.selected;
      if (chosen === null || chosen === undefined) return;
      const layer = layers()[chosen];
      if (!layer) return;

      if (event.key === 'Escape') { opts.onSelect(null); return; }
      if (event.key === 'Delete' || event.key === 'Backspace') {
        event.preventDefault();
        opts.onRemove(chosen);
        return;
      }
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'd') {
        event.preventDefault();
        opts.onDuplicate(chosen);
        return;
      }

      const step = event.shiftKey ? 10 : 1;
      const nudge = { ArrowLeft: [-step, 0], ArrowRight: [step, 0],
                      ArrowUp: [0, -step], ArrowDown: [0, step] }[event.key];
      if (!nudge || !layout()) return;
      event.preventDefault();
      const offset = layer.offset || [0, 0];
      setOffset(layer,
        Preview.anchorX(layer.anchor ?? 'center') + offset[0] + nudge[0],
        Preview.anchorY(layer.anchor ?? 'center') + offset[1] + nudge[1]);
      opts.onEdit();
      paint();
    };

    overlay.addEventListener('pointerdown', onDown);
    overlay.addEventListener('pointermove', onMove);
    overlay.addEventListener('pointerup', onUp);
    overlay.addEventListener('pointercancel', onUp);
    overlay.addEventListener('pointerleave', () => {
      if (!drag && hover !== null) { hover = null; paintOverlay(); }
    });
    overlay.addEventListener('keydown', onKey);

    /**
     * Moves a layer's anchor without moving the layer: the offset is re-based onto the new
     * corner, so choosing one answers "what does this hold on to when the window changes
     * shape" instead of throwing the layer across the screen.
     */
    const reanchor = (index, anchor) => {
      const layer = layers()[index];
      if (!layer) return;
      const box = Preview.restBox(canvas, layer, opts.about());
      if (anchor === 'center') delete layer.anchor; else layer.anchor = anchor;
      setOffset(layer, box.x, box.y);
    };

    return { paint, paintOverlay, reanchor, boxOf };
  };

  /* ---- the timeline ----------------------------------------------------- */

  /**
   * One row per layer, a bar from its entrance tick to the end of its entrance.
   *
   * Drag the bar to say when a layer arrives, its right edge to say how long it takes. The
   * ruler above scrubs, and that is the one gesture that also switches the canvas to the
   * animation: asking to see tick 40 is asking to see the animation.
   */
  const timeline = (opts) => {
    const el = Form.el;
    const state = opts.state;
    const wrap = el('div', 'timeline');

    const head = el('div', 'timeline-head');
    head.appendChild(el('h4', null, opts.title));
    head.appendChild(el('span', 'muted small', opts.hint));
    wrap.appendChild(head);

    const body = el('div', 'timeline-body');
    wrap.appendChild(body);

    const duration = () => Math.max(opts.scene().duration ?? 100, 1);
    const rows = [];

    const tickAt = (event, track) => {
      const rect = track.getBoundingClientRect();
      return clamp(((event.clientX - rect.left) / rect.width) * duration(), 0, duration());
    };

    const ruler = el('div', 'timeline-row timeline-ruler');
    ruler.appendChild(el('span', 'timeline-name muted small', '0 – ' + duration()));
    const rulerTrack = el('div', 'timeline-track');
    const marks = Math.min(10, duration());
    for (let mark = 0; mark <= marks; mark += 1) {
      const notch = el('span', 'timeline-mark');
      notch.style.left = ((mark / marks) * 100) + '%';
      rulerTrack.appendChild(notch);
    }
    const scrub = (event) => {
      state.tick = Math.round(tickAt(event, rulerTrack) * 2) / 2;
      opts.onScrub();
    };
    rulerTrack.addEventListener('pointerdown', (event) => {
      rulerTrack.setPointerCapture(event.pointerId);
      scrub(event);
    });
    rulerTrack.addEventListener('pointermove', (event) => {
      if (rulerTrack.hasPointerCapture(event.pointerId)) scrub(event);
    });
    ruler.appendChild(rulerTrack);
    body.appendChild(ruler);

    // The playhead crosses every row, so which layers are up at this tick is read off the
    // timeline rather than counted. It rides a strip laid over the track column alone, which
    // is the only way a percentage of the ruler is also a percentage of every bar below it.
    const sweep = el('div', 'timeline-sweep');
    const playhead = el('div', 'timeline-playhead');
    sweep.appendChild(playhead);
    body.appendChild(sweep);

    (opts.layers() || []).forEach((layer, index) => {
      const row = el('div', 'timeline-row');
      const name = el('button', 'timeline-name');
      name.type = 'button';
      name.textContent = opts.label(layer, index);
      name.title = name.textContent;
      name.addEventListener('click', () => opts.onSelect(index));
      row.appendChild(name);

      const track = el('div', 'timeline-track');
      const bar = el('div', 'timeline-bar');
      bar.title = opts.barHint;
      const grip = el('span', 'timeline-grip');
      bar.appendChild(grip);
      track.appendChild(bar);
      row.appendChild(track);

      let hold = null;
      const start = (mode) => (event) => {
        hold = { mode, tick: tickAt(event, track), at: layer.at ?? 0, over: layer.for ?? 12 };
        event.stopPropagation();
        event.preventDefault();
        opts.onSelect(index);
      };
      const holdMove = (event) => {
        if (!hold) return;
        const delta = tickAt(event, track) - hold.tick;
        if (hold.mode === 'move') {
          const next = Math.round(clamp(hold.at + delta, 0, duration()));
          if (next === 0) delete layer.at; else layer.at = next;
        } else {
          const next = Math.max(1, Math.round(hold.over + delta));
          if (next === 12) delete layer.for; else layer.for = next;
        }
        opts.onEdit();
        update();
      };
      const stop = () => { if (hold) { hold = null; opts.onCommit(); } };

      bar.addEventListener('pointerdown', (event) => {
        start('move')(event);
        bar.setPointerCapture(event.pointerId);
      });
      grip.addEventListener('pointerdown', (event) => {
        start('stretch')(event);
        grip.setPointerCapture(event.pointerId);
      });
      bar.addEventListener('pointermove', holdMove);
      grip.addEventListener('pointermove', holdMove);
      bar.addEventListener('pointerup', stop);
      grip.addEventListener('pointerup', stop);
      bar.addEventListener('pointercancel', stop);
      grip.addEventListener('pointercancel', stop);

      rows.push({ row, bar, index });
      body.appendChild(row);
    });

    const update = () => {
      const total = duration();
      playhead.style.left = ((clamp(state.tick, 0, total) / total) * 100) + '%';
      rows.forEach(({ row, bar, index }) => {
        const layer = (opts.layers() || [])[index];
        if (!layer) return;
        const at = clamp(layer.at ?? 0, 0, total);
        const over = Math.max(layer.for ?? 12, 1);
        bar.style.left = ((at / total) * 100) + '%';
        bar.style.width = Math.min((over / total) * 100, 100 - (at / total) * 100) + '%';
        row.classList.toggle('timeline-on', index === state.selected);
        row.classList.toggle('timeline-off', state.hidden.has(index));
      });
    };

    update();
    return { node: wrap, update };
  };

  return { create, timeline, SIZING, handlesFor };
})();
