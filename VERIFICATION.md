# Verification — Blueprint Forge Survival 0.3.0

**Not compiled against Minecraft and not run in game.** The environment this version was edited in has no network
access, so Gradle could not download Minecraft, Yarn mappings, Fabric Loom or Fabric API.

Checked:
- Every `.java` file parses (JDK 21 compiler front end). Run without Minecraft on the classpath, every remaining
  error is an unresolved Minecraft/Fabric/LWJGL/Gson symbol; none refer to this mod's own classes or methods.
- No non-ASCII characters remain in the sources.
- Pure-logic classes (`Geometry`, `PackedPalette`, `WalkPath`, `MaterialSummary`) are unchanged, so the existing
  18 unit tests still describe them. They were not re-run here.

Not checked (please do these first):
1. `./gradlew build` compiles. Any error will be a Minecraft API name or signature; send it back to be fixed.
2. Open every menu in a test world: G, Load Schematics (select, double-click), Placements -> Configure,
   Material List, Verifier, Configuration Menu tabs. Confirm text is sharp, not dimmed or blurred.
3. Load `examples/test-floor.litematic`, put nine stone in the hotbar and build on a flat creative or test world
   at each speed preset.
4. Test stairs, slabs, doors and a facing-dependent block, then flying/airborne refusal.
5. Only then try a server, and only one that allows building automation.
