# World Milestones

Fabric mod for Minecraft 26.2 that provides JSON-defined categories, quests, sections, server-owned progress, rewards, commands, and a client quest browser.

## Configuration

On the first server start, defaults are copied to `config/worldmilestones/`:

- `config.json` controls whether the latest quest snapshot is sent on player join.
- `categories/*.json` defines visible categories, ordering, icons, colors, and permission levels.
- `quests/*.json` defines quest metadata, progress scope, sections, requirements, and rewards.

Edit or add JSON files and run `/wm reload` to apply them without restarting. Unknown reward types are ignored and reported only when granting; built-in reward types are `experience`, `item`, and `command`. Commands in reward definitions run as the server and may use `%player%`.

Requirements have a public registration point at `RequirementTypeRegistry.register(type, factory)`. Automatic requirement evaluation is intentionally not part of this initial version; progress and section completion are currently controlled by operator commands.

## Commands

- `/wm` or `/wm list` lists visible categories and quests.
- `/wm open` opens the client quest browser.
- `/wm info <quest>` shows quest details and the caller's progress.
- `/wm reload` reloads JSON configuration.
- `/wm complete <quest>` completes a quest and grants its quest and required-section rewards.
- `/wm progress <quest> <amount>` sets progress and completes/rewards it when the threshold is reached.
- `/wm section <quest> <section>` completes a section, grants its rewards once, and evaluates `ALL`, `ANY`, or `X_OF_Y` completion mode.
- `/wm reset <quest>` clears progress and completion state.

Configuration, completion, progress, and reset commands require operator permission level 2. Open/list/info are available to players. The same commands are available under `/worldmilestones`.

Progress is saved in the active world at `data/worldmilestones-progress.json`, independently of quest definitions. Personal progress is keyed by player UUID; global progress is shared by the world; team progress uses the player's scoreboard team, falling back to a private key when the player has no team.

## Client

The inventory keeps its normal E key and includes a World Milestones button at the top left. The configurable `Open World Milestones` keybind defaults to O. Quest data and progress are sent by the server; the client only displays the received snapshot.

Translations are in `assets/worldmilestones/lang/en_us.json` and `es_es.json`.