/*
 * The form engine: schema in, DOM out, and a plain object underneath.
 *
 * Nothing here knows what a trainer is. It knows the field types schema.js uses, and it edits
 * the very object that gets written to disk - so what the JSON tab shows is not a rendering of
 * the form, it is the form's own document.
 *
 * One rule decides what ends up in the file: **a value equal to its default is left out**. The
 * mod fills every default in itself, so writing them back would be noise in a pack author's
 * file and a second place for the default to live. `null` is the exception - it is a value a
 * pack means (a silent battle), not an absence.
 */

const Form = (() => {
  const el = (tag, cls, text) => {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined) node.textContent = text;
    return node;
  };

  const DOCS = 'https://github.com/matheo-1712/cobblemon-trainers/blob/master/docs/';

  /** The documentation of a field, in the reader's language: docs/ is French, docs/en/ English. */
  const docHref = (doc) => {
    if (!doc) return null;
    if (I18N.lang === 'fr') return DOCS + doc;
    const [page, anchor] = doc.split('#');
    const english = {
      'DATAPACK.md': 'DATAPACK.md', 'DIFFICULTE.md': 'DIFFICULTY.md', 'GIMMICKS.md': 'GIMMICKS.md',
      'INTROS.md': 'INTROS.md', 'SPAWNING.md': 'SPAWNING.md', 'COSMETIQUES.md': 'COSMETICS.md'
    }[page] || page;
    // Anchors are the headings of each page, so they are translated with it: the page alone is
    // the honest link rather than a fragment that lands nowhere.
    return DOCS + 'en/' + english + (anchor && english === page ? '#' + anchor : '');
  };

  /** Empty is empty whatever the shape; `null` is not empty, it is a choice. */
  const isEmpty = (value) => value === undefined || value === ''
    || (Array.isArray(value) && value.length === 0)
    || (value && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length === 0);

  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

  /** Drops what the mod would have defaulted anyway, all the way down. */
  const prune = (obj) => {
    Object.keys(obj).forEach((key) => {
      const value = obj[key];
      if (value && typeof value === 'object' && !Array.isArray(value)) {
        prune(value);
      }
      if (isEmpty(value)) delete obj[key];
    });
    return obj;
  };

  const setValue = (doc, field, value) => {
    if (value === undefined || (field.def !== undefined && same(value, field.def) && value !== null)) {
      delete doc[field.k];
    } else {
      doc[field.k] = value;
    }
  };

  const getValue = (doc, field) => (doc[field.k] !== undefined ? doc[field.k] : field.def);

  /* ---- one row --------------------------------------------------------- */

  const row = (field, control, ctx) => {
    const wrap = el('div', 'field');
    const head = el('div', 'field-head');
    const label = el('label', 'field-label', I18N.of(field.l));
    head.appendChild(label);

    const href = docHref(field.doc);
    if (href) {
      const link = el('a', 'field-doc', '?');
      link.href = href;
      link.target = '_blank';
      link.rel = 'noreferrer';
      link.title = I18N.t('doc.open');
      head.appendChild(link);
    }

    wrap.appendChild(head);
    wrap.appendChild(control);

    if (field.h) wrap.appendChild(el('p', 'field-hint', I18N.of(field.h)));
    if (ctx && ctx.note) wrap.appendChild(ctx.note);
    return wrap;
  };

  /* ---- the controls ---------------------------------------------------- */

  /** The bare text control, without whatever the field type puts around it. */
  const textInput = (field, doc, changed, ctx) => {
    const input = el('input', 'input');
    input.type = 'text';
    input.value = getValue(doc, field) ?? '';
    if (field.placeholder) input.placeholder = field.placeholder;
    if (ctx && ctx.list) input.setAttribute('list', ctx.list);
    input.addEventListener('input', () => { setValue(doc, field, input.value); changed(); });
    return input;
  };

  /**
   * A menu of the resources the pack actually holds, tied to the text field beside it.
   *
   * A datalist was already there and was not enough: it is a suggestion one has to know to ask
   * for, so a music the author uploaded five minutes ago still had to be typed out by hand,
   * character for character, into a field where a typo is silent.
   *
   * The two controls edit one value and stay in step - the menu is not a button that inserts,
   * it shows what the field says. A value it has no row for (another pack's id, a name half
   * typed) selects "typed by hand" rather than being quietly rewritten into something else.
   */
  const refMenu = (ctx, input, onPick) => {
    const select = el('select', 'input input-menu');
    const known = new Set();

    const option = (value, label) => {
      const node = el('option', null, label);
      node.value = value;
      return node;
    };

    select.appendChild(option('', I18N.t('ref.empty')));
    (ctx.menu() || []).forEach((group) => {
      const items = (group.items || []).filter(Boolean);
      if (!items.length) return;
      const box = el('optgroup');
      box.label = group.label;
      items.forEach((value) => {
        if (known.has(value)) return;
        known.add(value);
        box.appendChild(option(value, value));
      });
      select.appendChild(box);
    });
    // A menu with nothing in it is worse than none: it takes half the row to offer "empty" and
    // "typed by hand", which is the field itself. This is also how a field whose meaning
    // changes with a neighbour - a skin value is a username or a texture - stops offering
    // textures to someone naming an account.
    if (!known.size) return null;
    const typed = option('__typed__', I18N.t('ref.typed'));
    select.appendChild(typed);

    const sync = () => {
      const value = input.value;
      const listed = value === '' || known.has(value);
      typed.hidden = listed;
      select.value = listed ? value : '__typed__';
    };

    select.addEventListener('change', () => {
      if (select.value === '__typed__') { sync(); input.focus(); return; }
      input.value = select.value;
      onPick(select.value);
      sync();
    });
    input.addEventListener('input', sync);
    sync();
    return select;
  };

  const controls = {
    str(field, doc, changed, ctx) {
      const input = textInput(field, doc, changed, ctx);
      const menu = ctx && ctx.menu
        && refMenu(ctx, input, (value) => { setValue(doc, field, value); changed(); });
      if (!menu) return input;
      const wrap = el('div', 'row row-pick');
      wrap.appendChild(menu);
      wrap.appendChild(input);
      return wrap;
    },

    /*
     * A texture is a name, and next to it the file that name reaches.
     *
     * The button is handed in by the caller (`ctx.pick`): this file does not know what an
     * asset is, and must not - it renders fields. What it does know is that typing an id by
     * hand and putting the image in the pack are one question, so they get one row.
     */
    texture(field, doc, changed, ctx) {
      const wrap = el('div', 'row row-pick');
      const input = textInput(field, doc, changed, ctx);
      const menu = ctx && ctx.menu
        && refMenu(ctx, input, (value) => { setValue(doc, field, value); changed(); });
      if (menu) wrap.appendChild(menu);
      wrap.appendChild(input);
      if (ctx && ctx.pick) {
        const button = el('button', 'btn btn-small', I18N.t('assets.pick'));
        button.type = 'button';
        button.title = I18N.t('assets.pick.hint');
        button.addEventListener('click', () => ctx.pick((reference) => {
          setValue(doc, field, reference);
          input.value = reference;
          changed();
        }));
        wrap.appendChild(button);
      }
      return wrap;
    },

    text(field, doc, changed) {
      const input = el('textarea', 'input');
      input.rows = 2;
      input.value = getValue(doc, field) ?? '';
      input.addEventListener('input', () => { setValue(doc, field, input.value); changed(); });
      return input;
    },

    num(field, doc, changed) {
      const input = el('input', 'input input-num');
      input.type = 'number';
      if (field.min !== undefined) input.min = field.min;
      if (field.max !== undefined) input.max = field.max;
      if (field.step !== undefined) input.step = field.step;
      const current = getValue(doc, field);
      input.value = current === null || current === undefined ? '' : current;
      input.addEventListener('input', () => {
        setValue(doc, field, input.value === '' ? undefined : Number(input.value));
        changed();
      });
      return input;
    },

    bool(field, doc, changed) {
      const wrap = el('label', 'switch');
      const input = el('input');
      input.type = 'checkbox';
      input.checked = Boolean(getValue(doc, field));
      input.addEventListener('change', () => { setValue(doc, field, input.checked); changed(); });
      wrap.appendChild(input);
      wrap.appendChild(el('span', 'switch-mark'));
      wrap.appendChild(el('span', 'switch-text', I18N.t(input.checked ? 'yes' : 'no')));
      input.addEventListener('change', () => {
        wrap.lastChild.textContent = I18N.t(input.checked ? 'yes' : 'no');
      });
      return wrap;
    },

    sel(field, doc, changed) {
      const select = el('select', 'input');
      field.options.forEach(([value, label]) => {
        const option = el('option', null, I18N.of(label));
        option.value = String(value);
        select.appendChild(option);
      });
      select.value = String(getValue(doc, field) ?? '');
      select.addEventListener('change', () => {
        const raw = select.value;
        const typed = field.options.find(([v]) => String(v) === raw)[0];
        setValue(doc, field, typed);
        changed();
      });
      return select;
    },

    strnull(field, doc, changed, ctx) {
      const wrap = el('div', 'row row-pick');
      const input = el('input', 'input');
      input.type = 'text';
      if (ctx && ctx.list) input.setAttribute('list', ctx.list);
      const current = getValue(doc, field);
      input.value = current === null ? '' : (current ?? '');
      input.disabled = current === null;

      const silence = el('label', 'switch switch-inline');
      const box = el('input');
      box.type = 'checkbox';
      box.checked = current === null;
      silence.appendChild(box);
      silence.appendChild(el('span', 'switch-mark'));
      silence.appendChild(el('span', 'switch-text', I18N.t('null.silence')));

      input.addEventListener('input', () => { setValue(doc, field, input.value); changed(); });
      box.addEventListener('change', () => {
        input.disabled = box.checked;
        if (box.checked) doc[field.k] = null;
        else setValue(doc, field, input.value);
        changed();
      });

      const menu = ctx && ctx.menu
        && refMenu(ctx, input, (value) => { setValue(doc, field, value); changed(); });
      if (menu) {
        // Silence is not a track, so the menu has nothing to say while the switch is on.
        const follow = () => { menu.disabled = box.checked; };
        box.addEventListener('change', follow);
        follow();
        wrap.appendChild(menu);
      }
      wrap.appendChild(input);
      wrap.appendChild(silence);
      return wrap;
    },

    color(field, doc, changed) {
      const wrap = el('div', 'row');
      const swatch = el('input', 'input-color');
      swatch.type = 'color';
      const input = el('input', 'input input-hex');
      input.type = 'text';
      const current = String(getValue(doc, field) ?? field.def ?? '#FFFFFF');
      swatch.value = current.startsWith('#') ? current : '#' + current;
      input.value = current;

      const push = (value) => { setValue(doc, field, value); changed(); };
      swatch.addEventListener('input', () => { input.value = swatch.value.toUpperCase(); push(input.value); });
      input.addEventListener('input', () => {
        if (/^#[0-9a-fA-F]{6}$/.test(input.value)) swatch.value = input.value;
        push(input.value);
      });
      wrap.appendChild(swatch);
      wrap.appendChild(input);
      return wrap;
    },

    tags(field, doc, changed) {
      const wrap = el('div', 'tags');
      const current = new Set(getValue(doc, field) || []);
      field.options.forEach(([value, label]) => {
        const tag = el('label', 'tag');
        const box = el('input');
        box.type = 'checkbox';
        box.checked = current.has(value);
        box.addEventListener('change', () => {
          if (box.checked) current.add(value); else current.delete(value);
          setValue(doc, field, field.options.map(([v]) => v).filter((v) => current.has(v)));
          changed();
        });
        tag.appendChild(box);
        tag.appendChild(el('span', null, I18N.of(label)));
        wrap.appendChild(tag);
      });
      return wrap;
    },

    offset(field, doc, changed) {
      const wrap = el('div', 'row');
      const current = doc[field.k] || [0, 0];
      ['x', 'y'].forEach((axis, index) => {
        const cell = el('label', 'mini');
        cell.appendChild(el('span', 'mini-label', axis));
        const input = el('input', 'input input-num');
        input.type = 'number';
        input.value = current[index] ?? 0;
        input.addEventListener('input', () => {
          const next = [Number(wrap.querySelectorAll('input')[0].value) || 0,
                        Number(wrap.querySelectorAll('input')[1].value) || 0];
          if (next[0] === 0 && next[1] === 0) delete doc[field.k];
          else doc[field.k] = next;
          changed();
        });
        cell.appendChild(input);
        wrap.appendChild(cell);
      });
      return wrap;
    },

    area(field, doc, changed) {
      const wrap = el('div', 'area');
      const current = doc[field.k] || {};
      const corners = [['from', current.from || []], ['to', current.to || []]];
      const inputs = [];

      corners.forEach(([name, values]) => {
        const line = el('div', 'row');
        line.appendChild(el('span', 'mini-label', name));
        [0, 1].forEach((index) => {
          const input = el('input', 'input input-num');
          input.type = 'number';
          input.placeholder = index === 0 ? 'x' : 'z';
          input.value = values[index] ?? '';
          inputs.push(input);
          line.appendChild(input);
        });
        wrap.appendChild(line);
      });

      const push = () => {
        const numbers = inputs.map((input) => (input.value === '' ? null : Number(input.value)));
        if (numbers.some((value) => value === null)) delete doc[field.k];
        else doc[field.k] = { from: [numbers[0], numbers[1]], to: [numbers[2], numbers[3]] };
        changed();
      };
      inputs.forEach((input) => input.addEventListener('input', push));
      return wrap;
    },

    strlist(field, doc, changed) {
      const wrap = el('div', 'list');
      const values = doc[field.k] || [];

      const redraw = () => {
        wrap.innerHTML = '';
        values.forEach((value, index) => {
          const line = el('div', 'row');
          const input = el('input', 'input');
          input.type = 'text';
          input.value = value;
          input.addEventListener('input', () => { values[index] = input.value; push(); });
          const remove = el('button', 'btn btn-ghost btn-mini', '×');
          remove.type = 'button';
          remove.title = I18N.t('list.remove');
          remove.addEventListener('click', () => { values.splice(index, 1); redraw(); push(); });
          line.appendChild(input);
          line.appendChild(remove);
          wrap.appendChild(line);
        });
        const add = el('button', 'btn btn-ghost btn-small', '+ ' + I18N.t('list.add'));
        add.type = 'button';
        add.addEventListener('click', () => { values.push(''); redraw(); push(); });
        wrap.appendChild(add);
      };

      const push = () => {
        const kept = values.filter((value) => value !== '');
        if (kept.length === 0) delete doc[field.k]; else doc[field.k] = values;
        changed();
      };

      redraw();
      return wrap;
    },

    objlist(field, doc, changed) {
      const wrap = el('div', 'list');
      const values = doc[field.k] || [];

      const redraw = () => {
        wrap.innerHTML = '';
        values.forEach((entry, index) => {
          const card = el('div', 'card card-inline');
          const head = el('div', 'card-head');
          head.appendChild(el('span', 'card-title', '#' + (index + 1)));
          const remove = el('button', 'btn btn-ghost btn-mini', '×');
          remove.type = 'button';
          remove.addEventListener('click', () => { values.splice(index, 1); redraw(); push(); });
          head.appendChild(remove);
          card.appendChild(head);
          card.appendChild(render(field.fields, entry, () => { push(); }));
          wrap.appendChild(card);
        });
        const add = el('button', 'btn btn-ghost btn-small', '+ ' + I18N.t('list.add'));
        add.type = 'button';
        add.addEventListener('click', () => { values.push({}); redraw(); push(); });
        wrap.appendChild(add);
      };

      const push = () => {
        if (values.length === 0) delete doc[field.k]; else doc[field.k] = values;
        changed();
      };

      redraw();
      return wrap;
    }
  };

  /* ---- groups and the whole form --------------------------------------- */

  const group = (field, doc, changed, ctx) => {
    if (!doc[field.k] || typeof doc[field.k] !== 'object') doc[field.k] = {};
    const inner = doc[field.k];

    const box = el('details', 'group');
    box.open = Object.keys(inner).length > 0 || Boolean(field.open);
    const summary = el('summary', 'group-head');
    summary.appendChild(el('span', 'group-title', I18N.of(field.l)));

    const href = docHref(field.doc);
    if (href) {
      const link = el('a', 'field-doc', '?');
      link.href = href;
      link.target = '_blank';
      link.rel = 'noreferrer';
      link.addEventListener('click', (event) => event.stopPropagation());
      summary.appendChild(link);
    }

    box.appendChild(summary);
    if (field.h) box.appendChild(el('p', 'field-hint group-hint', I18N.of(field.h)));
    box.appendChild(render(field.fields, inner, changed, ctx && ctx[field.k]));
    return box;
  };

  /** Builds the controls of a list of fields against one object. */
  const render = (fields, doc, changed, ctx) => {
    const wrap = el('div', 'fields');
    fields.forEach((field) => {
      if (field.t === 'group') {
        wrap.appendChild(group(field, doc, changed, ctx));
        return;
      }
      const build = controls[field.t] || controls.str;
      wrap.appendChild(row(field, build(field, doc, changed, ctx && ctx[field.k]), ctx && ctx[field.k]));
    });
    return wrap;
  };

  return { render, prune, isEmpty, el, docHref };
})();
