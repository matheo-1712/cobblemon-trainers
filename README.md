# Cobblemon Trainers

Ajoute des dresseurs Pokémon configurables à Cobblemon, avec des équipes au format Showdown.

En jeu : `/spawntrainer <id>` (opérateur), ou `/spawntrainer <id> <x> <y> <z>`.
Un clic droit sur le dresseur lance le combat.

## Ajouter des dresseurs

Deux emplacements possibles, rechargés par `/reload` :

- **Datapack** — `data/<namespace>/trainers/<nom>.json`. L'ID du dresseur est
  `<namespace>:<nom>`.
- **Config** — `config/cobblemon-trainers/trainers/<nom>.json`. Chargé sous le namespace
  `cobblemon-trainers`, et écrase donc un dresseur du même nom fourni par le mod.

```json
{
  "name": "Red",
  "level": 88,
  "skin": { "type": "player_username", "value": "Red" },
  "canBattle": true,
  "battleStartMessage": "...",
  "battleEndWinMessage": "C'est terminé !",
  "battleEndLoseMessage": "...",
  "team": [
    "Pikachu (M) @ Light Ball",
    "Ability: Static",
    "Level: 88",
    "EVs: 252 SpA / 4 SpD / 252 Spe",
    "Timid Nature",
    "- Thunderbolt",
    "- Iron Tail",
    "",
    "Snorlax (M) @ Leftovers",
    "Ability: Thick Fat",
    "Level: 88",
    "- Body Slam",
    "- Earthquake"
  ]
}
```

`team` accepte aussi un Pokémon complet par entrée, ses lignes séparées par `\n`.

| Champ | Défaut | Rôle |
| --- | --- | --- |
| `name` | `Trainer` | Nom affiché au-dessus du dresseur |
| `level` | `1` | Niveau du dresseur, et niveau par défaut des Pokémon sans `Level:` |
| `skin.type` | `player_username` | `player_username` ou `player_uuid` |
| `skin.value` | `Steve` | Pseudo ou UUID dont le skin est repris |
| `canBattle` | `true` | À `false`, l'interaction est désactivée |
| `npcClass` | `cobblemon-trainers:trainer` | NPCClass Cobblemon utilisé |

Le soin de l'équipe après un combat n'est pas réglable par dresseur dans Cobblemon 1.7.3 :
il dépend de `autoHealParty` sur le NPCClass. Pour le changer, copie
`data/cobblemon-trainers/npcs/trainer.json` dans ton datapack sous ton propre namespace et
référence-le via `npcClass`.

## Développement

```bash
./gradlew build
```

Voir aussi la [documentation Fabric](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up)
pour la configuration de l'IDE.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
