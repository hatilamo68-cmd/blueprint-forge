# Changelog

## 0.3.0

Speed
- Placement interval is now 4..200 ticks (default 6); 0.2.1 spent roughly 30+ ticks per block.
- Placement confirmation runs in the background (up to 6 in flight) instead of blocking the next click.
- Next target is chosen and aimed while the interval runs; least-turn target chosen from a small pool.
- Eased view turning; the scan no longer skips two of every three ticks.
- Hotbar slot is selected one tick before the click.

Bug fixes
- GUI: screens called `renderBackground` themselves and then `super.render`, which draws the background again in 1.21.
  That darkens/blurs everything drawn before it (the Materials table) and can fail with a double-blur error.
  All screens now draw panels from an overridden `renderBackground` and never call it twice.
- Stairs and rails never matched (their `shape` depends on neighbours), so the builder either waited forever or
  paused with "a placed block changed". Derived properties are now ignored.
- Blocks with states an item cannot set (waterlogged, powered, open, lit, age, rotation, repeater delay...) left the
  builder waiting forever. They now count as placed.
- Positions with no placeable item (water, fire) prevented "Blueprint complete"; they are now skipped and reported.
- Walking-mode target selection used a per-scan local, so a route was only planned on lucky ticks.
- Every button in the old menu closed the screen, including the walking/refill toggles.
- `/bf materials` from chat could be closed straight away by the chat screen; screens now open on the next tick.
- Source files are now compiled as UTF-8 (the old Materials screen contained a non-ASCII dash that breaks on
  Windows default encodings).
- Size limits raised: 400,000 non-air blocks (was 65,536), 16,777,216 volume (was 1,048,576). Block counting is
  sliced across ticks, and the material list no longer rebuilds the whole placement every refresh.
- Builder options (speed, walking, refill, preview, HUD) are saved between sessions.

GUI
- New Litematica-style screens: Main Menu, Load Schematics, Schematic Placements, Configure Placement,
  Material List, Schematic Verifier, Configuration Menu. Several schematics can be loaded at once.
- Commands: `/bf menu`, `/bf speed`, `/bf delay` now starts at 4.
