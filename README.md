# World Milestones

Fabric mod for Minecraft 26.2 that provides JSON-defined categories, quests, sections, server-owned progress, rewards, commands, and a client quest browser.

## Configuration

On the first server start, defaults are copied to `config/worldmilestones/`:

- `config.json` controls join synchronization and legacy weekly mission rotation.
- `categories.json` is an array containing all category definitions. Set `id`, `name` (the title), `icon` (a Minecraft item ID), `description`, `color`, and `order` for each category. A category icon can use `icon_texture` with a base64 skin texture value to render a custom player head. A timed category can also set `rotation_interval` (for example `7d`) and `active_quest_count`.
- `quests/<category-id>/*.json` contains one quest definition per file, grouped by category.

Edit or add JSON files and run `/wm reload` to apply them without restarting. Unknown reward types are ignored and reported only when granting; built-in reward types are `experience`, `item`, and `command`. Commands in reward definitions run as the server and may use `%player%`.

For a new category, add an object to the array in `categories.json`. For a new milestone or mission, add a JSON file under `quests/<category-id>/`; its `category` must match a category `id`. Each quest can have `sections`, each with its own `id`, `name`, `icon`, description, and required progress. Use unique IDs and Minecraft item IDs such as `minecraft:diamond` for icons. `progress_scope` is `GLOBAL` (one completion shared by the whole server), `PERSONAL` (each player progresses independently), or `TEAM`.

Timed categories randomly activate up to `active_quest_count` quests from all quests in that category, then rotate them after `rotation_interval`. Supported interval suffixes are `s`, `m`, `h`, `d`, and `w`. The next rotation countdown is shown in the category view. Automatic-stat quests capture each online player's statistic baseline when activated; players who are offline use their first join during that rotation as their baseline. The legacy `weekly` quest flag and `weekly_count` config remain supported for categories without `rotation_interval`.

For automatically tracked progress, add entries to `automatic`. Supported `type` values are `mob_killed` (entity `target`), `item_crafted` (item `target`), `item_smelted` (item `target`), `item_used` (item `target`), `block_placed` (block item `target`), `block_mined` (block `target`), `animals_bred`, `target_hit`, `mob_kills`, `armor_worn`, and `server_join`. `required_progress` is the counter threshold. Non-rotating quests use Minecraft's lifetime player statistics. Rotating quests count from their activation baseline. Minecraft exposes crafted and smelted item counts through the same statistic, so `item_smelted` can also include crafted items of the same type. Block placement uses the block item's use statistic.

For legacy weekly rotation, use a category without `rotation_interval`, set `"weekly": true` and `"progress_scope": "PERSONAL"`. Those missions are selected from all such quest files using `weekly_count` in `config.json`; everyone sees the same selected missions. Rotation follows ISO weeks in UTC, and each new week clears progress for the previous active missions. Timed category settings take precedence over the legacy `weekly` flag.

Example quest file:

```json
{
	"id": "quests:weekly_miner",
	"name": "Weekly Miner",
	"description": "Mine stone for the community.",
	"category": "semanal",
	"icon": "minecraft:iron_pickaxe",
	"type": "quest",
	"progress_scope": "PERSONAL",
	"required_progress": 32,
	"automatic": [ { "type": "block_mined", "target": "minecraft:stone" } ],
	"sections": [
		{ "id": "stone", "name": "Mine stone", "icon": "minecraft:stone", "required": true, "required_progress": 32 }
	],
	"requirements": [],
	"rewards": [ { "type": "experience", "amount": 50 } ]
}
```

Requirements have a public registration point at `RequirementTypeRegistry.register(type, factory)`. Automatic progress is configured with the `automatic` field above; sections can also be completed by administrators.

## Commands

Commands are available with either the `/wm` or `/worldmilestones` prefix. For example, `/worldmilestones list` does the same as `/wm list`.

- `/wm` or `/wm list` lists the visible categories and quests available to you.
- `/wm open` opens the client quest browser.
- `/wm info <quest>` shows a quest's description, requirements, and your progress.
- `/wm reload` reloads the category and quest JSON files.
- `/wm rotation reduce <category> <duration>` reduces the time until a timed category rotates. Durations use `s`, `m`, `h`, `d`, or `w`, such as `/wm rotation reduce semanal 1d`.
- `/wm complete <quest>` completes a quest and grants its rewards, including rewards for required sections.
- `/wm progress <quest> <amount>` sets a quest's progress. If the required amount is reached, the quest is completed and its rewards are granted.
- `/wm section <quest> <section>` completes a section, grants its rewards once, and checks whether the quest is complete using its `ALL`, `ANY`, or `X_OF_Y` completion mode.
- `/wm reset <quest>` clears the quest's progress and completion state.
- `/wm admin add <player>` designates a milestones-admin (OP only).
- `/wm admin remove <player>` removes the designation (OP only).
- `/wm admin list` lists designated milestones-admins (OP only).

`/wm`, `list`, `open`, and `info` are available to players. `reload`, `complete`, `progress`, `section`, and `reset` are available to OPs and designated milestones-admins. Only OPs can assign or remove milestones-admins. Quest and section arguments are IDs from the JSON configuration; `progress` and `section` expect single-word IDs.

Progress is saved in the active world at `data/worldmilestones-progress.json`, independently of quest definitions. Personal progress is keyed by player UUID; global progress is shared by the world; team progress uses the player's scoreboard team, falling back to a private key when the player has no team. Global reward definitions are snapshotted into this world data when unlocked, and each player's delivery is recorded by UUID. Players who are offline at unlock receive their pending global rewards the next time they join. Previously unlocked global milestones are also migrated from the loaded quest configuration when players next join.

## Client

The inventory keeps its normal E key and includes a World Milestones button at the top left. The configurable `Open World Milestones` keybind defaults to O. Quest data and progress are sent by the server; the client only displays the received snapshot.

Translations are in `assets/worldmilestones/lang/en_us.json` and `es_es.json`.