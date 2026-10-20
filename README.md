# Blueprint Forge Survival — 0.3.0

A **client-only Fabric mod for Minecraft Java 1.21.1** that imports `.litematic` blueprints, shows placement
outlines, and places blocks from your inventory using ordinary right-clicks. It needs no OP, cheats or server-side
mod. Everything it does goes through the server's normal reach, inventory and placement checks.

The planner is local deterministic automation, **not a cloud AI service**: no API key, model or subscription.

## Install

1. Minecraft **Java 1.21.1**, **Fabric Loader 0.16.14+**, **Fabric API for 1.21.1**, Java 21.
2. Build the jar (see *Build*) and put `blueprint-forge-0.3.0.jar` in your **client's** `mods` folder.
   Remove older Blueprint Forge jars first.
3. Put `.litematic` files in `schematics/` or `config/blueprint-forge/blueprints/` (sub-folders work in the browser).

## Using it (menus modelled on Litematica)

Press **G** for the main menu (rebindable). **B** toggles the outline preview, **P** starts/pauses the builder.

| Screen | What it does |
| --- | --- |
| **Load Schematics** | Folder browser with a filter box and a details panel (name, author, dates, total blocks, enclosing size, regions). Click to select, double-click or *Load Schematic* to load. |
| **Schematic Placements** | Every loaded schematic with its origin/rotation. Select, Configure or Remove each one. |
| **Configure placement** | Origin X/Y/Z with -/+ buttons (Shift = 10 blocks), *Move To My Position*, rotation, layer stepper, remove. |
| **Material List** | Item / Total / Missing / Available columns, search, sort, hide-complete, scope (whole schematic or selected layer). |
| **Schematic Verifier** | Correct / Missing / Wrong block / Wrong state counts per block type. |
| **Configuration Menu** | Tabs: Generic (rendering, HUD), Builder (speed, delay, walking, refill), Hotkeys. Saved between sessions. |

This is a re-creation of the look and flow, not Litematica's code or assets: there is no area-selection tool,
schematic saving, or preview thumbnail. It does not need Litematica or MaLiLib.

## Speed

The builder places one block every **N ticks** (20 ticks = 1 second). **N is never below 4**, which is the item-use
cooldown vanilla applies while a player holds right-click, so the mod never clicks faster than a person can.

| Preset | Ticks | Blocks per second |
| --- | --- | --- |
| Careful | 12 | ~1.7 |
| **Normal (default)** | 6 | ~3.3 |
| Fast | 4 | 5 |

`/bf speed careful|normal|fast` or `/bf delay <4..200>`, or the Builder tab of the Configuration Menu.

What makes it faster than 0.2.1 (which needed roughly 30+ ticks per block):

- Placements are confirmed in the background instead of the builder waiting for each one before the next click.
- The next block is picked, and the view turned toward it, while the interval timer is still running.
- Among the nearby placeable blocks it picks the one needing the least head turning, instead of strict file order.
- Turning is eased (large steps for big turns, snaps in the last degree or two).
- The scan loop no longer idles two out of three ticks.

## Server rules and bans

**There is no ban-proof mode, and 0.3.0 does not claim one.** The mod does not bypass anti-cheat, protection or server
rules. What it does is behave like ordinary play: real crosshair raycast, normal reach (capped at 4.5), visible
supporting face, the vanilla click rate floor, hotbar selection one tick before the click, no extra movement or
look packets, no flying, no teleport, no velocity or ground-state changes. Automated building is still against the
rules on many servers. Faster settings make automation easier to notice. Only use it where the server allows it.

## What the builder does

- Scans for unfinished positions and checks the required item is in your hotbar (or inventory with refill on).
- Checks reach, a visible support face, predicted block state and placement conditions before clicking.
- Turns the real player view toward the support face, checks the actual raycast, and right-clicks.
- Pauses on: manual WASD/Space/Shift/mouse input, an open screen, lost window focus, dimension change, low
  health/food, flying/gliding, leaving solid ground, water/lava, vehicles, large unexpected movement, a rejected or
  non-persisting placement, or a recently placed block changing back.
- Walking (`/bf walk`) and inventory refill (`/bf refill`) are **off by default**. Walking uses level-ground routes
  only (no jumping, no stairs/ledges).
- Does not mine, craft, fetch from containers, eat, fight, scaffold, bridge gaps or fly. Supply materials and access.

## Block states

Completion compares the block and the properties that item placement can control (facing, half, axis, slab type...).
These are **ignored** because neighbours derive them: connections (north/south/east/west/up/down), stair/rail `shape`,
door `hinge`, leaf `distance`/`persistent`, `snowy`, chest `type`. These are ignored because an ordinary item
placement cannot choose them: `waterlogged`, `powered`, `open`, `lit`, `power`, repeater `delay`, `age`, `rotation`,
`note`/`instrument`, snow `layers`, candle/pickle/egg counts, and similar. A block that differs only in those counts
as done. Stackable blocks (snow layers, candles, sea pickles) are therefore placed once, not stacked.

Facing-dependent blocks (stairs, furnaces, pistons...) take their facing from where you stand relative to the support
face. Without walking you may need to stand on the right side. Positions with no placeable block item (water, fire,
moving pistons) are skipped and reported when the build finishes.

## Limits

- Import: gzip NBT `.litematic` versions 4-7, multiple regions, signed sizes, packed palettes; Blueprint Forge
  `.json` format 1. Up to 16 MiB compressed, 256 regions, **400,000 non-air blocks**, 16,777,216 bounding volume.
  Sponge `.schem` and `.nbt` are not supported. No data-version migration: unknown blocks/properties are rejected.
- Block entities (chest contents, sign text), entities and scheduled ticks are ignored. Air does not remove blocks.
- Double slabs are not built (the second half needs a second click on an occupied block).
- Imports and placements are not kept across disconnects; reload after reconnecting. Builder options are kept.
- Outline preview shows at most 4096 unfinished positions within 96 blocks. Counting runs in slices so big
  schematics do not stall the game.
- Material counts are block positions; doors, beds and other multi-part blocks use fewer items.

## Commands (all local, no OP)

| Command | Action |
| --- | --- |
| `/bf menu` | Open the main menu |
| `/bf import <file>` / `/bf load` | Import `.litematic` or `.json` |
| `/bf list` | List files and show the import folder |
| `/bf demo` | Built-in cabin preview |
| `/bf here` / `/bf move <x> <y> <z>` | Set the origin (minimum corner) |
| `/bf rotate <0\|90\|180\|270>` | Rotation, including block orientations |
| `/bf layer <-1\|n>` | All layers, or one local Y layer (floor is 0) |
| `/bf build` / `/bf pause` / `/bf stop` | Start, toggle, stop |
| `/bf speed <careful\|normal\|fast>` | Speed preset |
| `/bf delay <4..200>` | Ticks between placements |
| `/bf walk` / `/bf refill` | Toggle walking / inventory refill |
| `/bf materials` / `/bf status` / `/bf clear` | Material list, status, unload everything |

## Build

JDK 21 and the included wrapper: `./gradlew build` (Windows: `gradlew.bat build`). Install
`build/libs/blueprint-forge-0.3.0.jar`, not the sources jar. `.github/workflows/build.yml` builds it on GitHub if you
push this folder to a repository. See `VERIFICATION.md` for what has and has not been checked.

MIT license. Independent project; no Litematica code is bundled. Minecraft is a trademark of Mojang/Microsoft.
