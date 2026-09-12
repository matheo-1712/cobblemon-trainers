/*
 * The chrome of the editor, in both languages.
 *
 * Field labels are not here: they live next to the field they describe, in schema.js, so that
 * adding a field means touching one place rather than three. This file is everything else -
 * buttons, panels, warnings.
 *
 * French is the reference, as it is for docs/. English is the translation.
 */

const I18N = (() => {
  const STRINGS = {
    'app.title': ['Éditeur de dresseurs', 'Trainer editor'],
    'app.subtitle': ['Cobblemon Trainers', 'Cobblemon Trainers'],
    'app.docs': ['Documentation', 'Documentation'],
    'app.source': ['Le mod', 'The mod'],
    'app.offline': ['Tout se passe dans ton navigateur : rien n’est envoyé nulle part.',
                    'Everything happens in your browser: nothing is sent anywhere.'],

    'pack.title': ['Le pack', 'The pack'],
    'pack.namespace': ['Namespace', 'Namespace'],
    'pack.namespace.hint': ['Le dossier sous data/ et le début de chaque ID. Minuscules, chiffres, _ - . uniquement.',
                            'The folder under data/ and the start of every id. Lowercase, digits, _ - . only.'],
    'pack.description': ['Description du pack', 'Pack description'],
    'pack.files': ['Fichiers', 'Files'],
    'pack.empty': ['Aucun fichier. Commence par un dresseur, ou charge un modèle.',
                   'No file yet. Start with a trainer, or load a template.'],
    'pack.add.trainer': ['Dresseur', 'Trainer'],
    'pack.add.intro': ['Intro', 'Intro'],
    'pack.add.category': ['Catégorie', 'Category'],
    'pack.add.advancement': ['Advancement', 'Advancement'],
    'pack.add': ['Ajouter', 'Add'],
    'pack.import': ['Importer', 'Import'],
    'pack.import.hint': ['Un .json, ou un pack entier en .zip', 'A .json file, or a whole pack as .zip'],
    'pack.export': ['Télécharger le pack (.zip)', 'Download the pack (.zip)'],
    'pack.export.one': ['Ce fichier seul (.json)', 'This file alone (.json)'],
    'pack.reset': ['Tout effacer', 'Clear everything'],
    'pack.reset.confirm': ['Effacer le pack en cours ? Rien n’est récupérable.',
                           'Clear the current pack? Nothing is recoverable.'],
    'pack.saved': ['Enregistré dans ce navigateur', 'Saved in this browser'],
    'pack.templates': ['Modèles', 'Templates'],
    'pack.template.pick': ['Partir d’un modèle…', 'Start from a template…'],

    'file.path': ['Chemin', 'Path'],
    'file.path.hint': ['Le dossier est la catégorie, le nom du fichier fait l’ID.',
                       'The folder is the category, the file name makes the id.'],
    'file.id': ['ID', 'Id'],
    'file.rename': ['Renommer', 'Rename'],
    'file.delete': ['Supprimer', 'Delete'],
    'file.duplicate': ['Dupliquer', 'Duplicate'],
    'file.delete.confirm': ['Supprimer ce fichier ?', 'Delete this file?'],

    'kind.trainer': ['Dresseur', 'Trainer'],
    'kind.intro': ['Intro', 'Intro'],
    'kind.category': ['Catégorie', 'Category'],
    'kind.advancement': ['Advancement', 'Advancement'],

    'tab.form': ['Formulaire', 'Form'],
    'tab.team': ['Équipe', 'Team'],
    'tab.layers': ['Calques', 'Layers'],
    'tab.json': ['JSON', 'JSON'],
    'tab.preview': ['Aperçu', 'Preview'],

    'team.title': ['Équipe', 'Team'],
    'team.hint': ['Un Pokémon par entrée, au format Showdown. Un export entier collé ici est découpé tout seul.',
                  'One Pokémon per entry, in Showdown format. A whole export pasted here is split on its own.'],
    'team.add': ['Ajouter un Pokémon', 'Add a Pokémon'],
    'team.paste': ['Coller un export entier', 'Paste a whole export'],
    'team.empty': ['Aucun Pokémon. Un dresseur sans équipe ne peut pas se battre.',
                   'No Pokémon. A trainer with no team cannot battle.'],
    'team.extra': ['Lignes maison : Aspects:, Fallback Item:, Alpha: Yes, Tera Type:',
                   'Lines of our own: Aspects:, Fallback Item:, Alpha: Yes, Tera Type:'],
    'team.count': ['%s Pokémon', '%s Pokémon'],

    'layers.add': ['Ajouter un calque', 'Add a layer'],
    'layers.empty': ['Aucun calque : l’écran serait noir.', 'No layer: the screen would be black.'],
    'layers.order': ['Dessinés dans cet ordre, le premier derrière.', 'Drawn in this order, the first one behind.'],
    'layers.up': ['Monter', 'Move up'],
    'layers.down': ['Descendre', 'Move down'],
    'layers.hide': ['Masquer dans l’aperçu', 'Hide in the preview'],
    'layers.duplicate': ['Dupliquer', 'Duplicate'],
    'layers.remove': ['Retirer', 'Remove'],
    'layers.part.place': ['Où', 'Where'],
    'layers.part.time': ['Quand', 'When'],
    'layers.part.sound': ['Son', 'Sound'],

    'stage.layout': ['Mise en page', 'Layout'],
    'stage.play': ['Animation', 'Animation'],
    'stage.hint': ['Clique un calque pour le prendre, glisse-le pour le placer, tire un coin pour le redimensionner.',
                   'Click a layer to take it, drag it to place it, pull a corner to resize it.'],
    'stage.hint.play': ['L’animation telle que le mod la dessine. Repasse en Mise en page pour déplacer un calque.',
                        'The animation as the mod draws it. Switch back to Layout to move a layer.'],
    'stage.keys': ['Flèches : 1 px · Maj : 10 px ou tout droit · Alt : sans aimant · Suppr : retirer · Ctrl+D : dupliquer',
                   'Arrows: 1 px · Shift: 10 px, or straight · Alt: no snapping · Delete: remove · Ctrl+D: duplicate'],
    'stage.snap': ['Aimanter', 'Snap'],
    'stage.grid': ['Repères', 'Guides'],
    'stage.anchor': ['Ancre', 'Anchor'],
    'stage.anchor.hint': ['Le point d’où part le décalage. En changer ne déplace pas le calque : c’est ce à quoi il se tient.',
                          'The point the offset starts from. Changing it does not move the layer: it is what the layer holds on to.'],

    'timeline.title': ['Chronologie', 'Timeline'],
    'timeline.hint': ['Clique la règle pour voir un tick.', 'Click the ruler to see a tick.'],
    'timeline.bar': ['Glisser : le tick d’entrée · le bord droit : la durée de l’entrée',
                     'Drag: the entrance tick · its right edge: how long the entrance takes'],

    'preview.play': ['Lire', 'Play'],
    'preview.pause': ['Pause', 'Pause'],
    'preview.restart': ['Reprendre du début', 'From the start'],
    'preview.tick': ['Tick', 'Tick'],
    'preview.skin': ['Skin de l’aperçu', 'Preview skin'],
    'preview.skin.hint': ['Un pseudo Minecraft, pour voir la figure du dresseur. Rien n’en sort dans le JSON.',
                          'A Minecraft username, to see the trainer figure. None of it reaches the JSON.'],
    'preview.player': ['Le joueur', 'The player'],
    'preview.trainer': ['Le dresseur', 'The trainer'],
    'preview.note': ['Les figures et les Pokémon sont dessinés à plat : en jeu, ce sont des modèles.',
                     'Figures and Pokémon are drawn flat here: in game they are models.'],
    'preview.texture.missing': ['Texture absente de l’aperçu', 'Texture missing from the preview'],
    'preview.drop': ['Dépose un PNG pour le voir ici', 'Drop a PNG to see it here'],

    'json.copy': ['Copier', 'Copy'],
    'json.copied': ['Copié', 'Copied'],
    'json.paste': ['Remplacer par du JSON collé', 'Replace with pasted JSON'],
    'json.invalid': ['JSON invalide : %s', 'Invalid JSON: %s'],

    'check.ok': ['Rien à signaler.', 'Nothing to report.'],
    'check.title': ['Vérifications', 'Checks'],
    'check.errors': ['%s erreur(s)', '%s error(s)'],
    'check.warnings': ['%s avertissement(s)', '%s warning(s)'],

    'adv.criteria': ['Ce qu’il faut faire', 'What it takes'],
    'adv.mode.trainer': ['Battre un dresseur précis', 'Defeat one trainer'],
    'adv.mode.count': ['Battre un nombre de dresseurs', 'Defeat a number of trainers'],
    'adv.parent': ['Advancement parent', 'Parent advancement'],
    'adv.frame': ['Cadre', 'Frame'],
    'adv.frame.task': ['Normal', 'Task'],
    'adv.frame.goal': ['Objectif', 'Goal'],
    'adv.frame.challenge': ['Défi', 'Challenge'],
    'adv.icon': ['Icône', 'Icon'],
    'adv.title': ['Titre', 'Title'],
    'adv.description': ['Description', 'Description'],
    'adv.toast': ['Notification', 'Toast'],
    'adv.announce': ['Annonce dans le chat', 'Announce in chat'],
    'adv.hidden': ['Caché dans l’arbre', 'Hidden in the tree'],
    'adv.trainer': ['ID du dresseur', 'Trainer id'],
    'adv.count': ['Combien', 'How many'],
    'adv.category': ['Limité à la catégorie', 'Limited to category'],
    'adv.pack': ['Limité au pack', 'Limited to pack'],

    'pack.archive': ['Format de l’archive', 'Archive format'],
    'pack.archive.zip': ['.zip — mods/, datapacks/ ou resourcepacks/', '.zip — mods/, datapacks/ or resourcepacks/'],
    'pack.archive.jar': ['.jar — mods/ seul, dépendance déclarée', '.jar — mods/ only, dependency declared'],
    'pack.archive.hint': [
      'Le .zip se charge des trois endroits, mais seul mods/ lit aussi les assets. Le .jar porte un fabric.mod.json, donc le jeu refuse de démarrer sans le mod - et il ne va que dans mods/.',
      'A .zip loads from all three places, but only mods/ reads the assets too. A .jar carries a fabric.mod.json, so the game refuses to start without the mod - and it only goes in mods/.'],

    'assets.title': ['Ressources', 'Assets'],
    'assets.hint': [
      'Les musiques, les skins et les images d’intro voyagent dans le même fichier que les dresseurs. Le sounds.json est écrit tout seul, à partir de ce qui est ici.',
      'Music, skins and intro images travel in the same file as the trainers. The sounds.json is written on its own, from what sits here.'],
    'assets.add': ['Ajouter un fichier', 'Add a file'],
    'assets.copy': ['Copier la référence', 'Copy the reference'],
    'assets.listen': ['Écouter', 'Listen'],
    'assets.subtitle': ['Sous-titre (facultatif)', 'Subtitle (optional)'],
    'assets.delete.confirm': ['Retirer ce fichier du pack ?', 'Remove this file from the pack?'],
    'assets.wrong': ['Ce type de ressource attend un fichier .%s', 'This kind of asset expects a .%s file'],
    'assets.drop.skin': ['Dépose un PNG : il entre dans le pack', 'Drop a PNG: it goes into the pack'],
    'assets.drop.texture': ['Dépose un PNG pour ce calque', 'Drop a PNG for this layer'],
    'assets.sounds.hint': [
      'Écrit à la publication. La clé est ce qu’un dresseur nomme ; stream vaut true pour une musique, qui dure et boucle.',
      'Written at export time. The key is what a trainer names; stream is true for music, which runs long and loops.'],
    'assets.size': ['%s au total', '%s in total'],

    'lang.title': ['Traductions', 'Translations'],
    'lang.hint': [
      'Une clé de traduction est résolue par le client : chaque joueur voit le texte dans sa langue, nom flottant compris. Sans texte, Minecraft affiche la clé telle quelle.',
      'A translation key is resolved by the client: every player sees the text in their own language, floating name included. With no text, Minecraft shows the key as it is.'],
    'lang.key': ['Clé', 'Key'],
    'lang.add': ['Une langue', 'A language'],
    'lang.add.prompt': ['Code de langue (fr_fr, en_us, es_es…)', 'Language code (fr_fr, en_us, es_es…)'],
    'lang.empty': [
      'Aucune clé pour l’instant. Écris du texte dans un dresseur, puis « Clés de traduction ».',
      'No key yet. Write text in a trainer, then use "Translation keys".'],
    'lang.keyify': ['Clés de traduction', 'Translation keys'],
    'lang.keyify.hint': [
      'Remplace les textes de ce dresseur par des clés, et range les phrases dans la première langue.',
      'Replaces this trainer’s texts with keys, and files the sentences under the first language.'],

    'list.add': ['Ajouter', 'Add'],
    'list.remove': ['Retirer', 'Remove'],
    'list.empty': ['Vide', 'Empty'],
    'null.silence': ['Silence (null)', 'Silence (null)'],
    'doc.open': ['Lire la doc', 'Read the docs'],
    'yes': ['Oui', 'Yes'],
    'no': ['Non', 'No']
  };

  let lang = 'fr';
  const listeners = [];

  const index = () => (lang === 'fr' ? 0 : 1);

  return {
    get lang() { return lang; },
    set(next) {
      if (next === lang) return;
      lang = next;
      try { localStorage.setItem('ct-lang', next); } catch (e) { /* private window */ }
      listeners.forEach((fn) => fn(lang));
    },
    restore() {
      try {
        const saved = localStorage.getItem('ct-lang');
        if (saved === 'fr' || saved === 'en') lang = saved;
        else if ((navigator.language || '').slice(0, 2) !== 'fr') lang = 'en';
      } catch (e) { /* private window */ }
    },
    onChange(fn) { listeners.push(fn); },
    /** A chrome string, with %s filled in by the arguments given. */
    t(key, ...args) {
      const entry = STRINGS[key];
      if (!entry) return key;
      return args.reduce((text, arg) => text.replace('%s', arg), entry[index()]);
    },
    /** A { fr, en } pair from the schema. */
    of(pair) {
      if (!pair) return '';
      return lang === 'fr' ? pair.fr : pair.en;
    }
  };
})();
