# Progress

## Toolchain reality (verified 2026-09-02)

The target moved with Minecraft's new version scheme. Confirmed against the live Fabric meta /
Modrinth / decompiled 26.1.2 sources:

| Thing | Value |
|---|---|
| Minecraft | **26.1.2** (data version 4790, the **first unobfuscated** release — no Yarn, no mappings step) |
| Java | **25** (Minecraft's bundled `java-runtime-epsilon` is a full JDK 25 and builds fine) |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.155.2+26.1.2 |
| Loom | 1.17.20 · Gradle 9.5.1 |
| Renamed APIs | `WorldRenderEvents` → `net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents`; `ResourceLocation` → `Identifier`; `fabric-key-binding-api-v1` → `fabric-key-mapping-api-v1`; key categories are now `KeyMapping.Category` objects; terrain `RenderType` → `ChunkSectionLayer` |
| Good surprise | `fabric-renderer-api-v1`, `fabric-renderer-indigo`, `fabric-model-loading-api-v1` **are** shipping for 26.1.2 |

Build: `JAVA_HOME` → the epsilon JDK, then `./gradlew build`. Sources for API spelunking:
`./gradlew genSources` (jars land under `.gradle/loom-cache/minecraftMaven/**/*-sources.jar`).

## M0 — scaffolding ✅

- Gradle + Loom project, split `main` / `client` source sets, client-only mod.
- `HoloPlaceClient` boots, creates `config/holoplace/schematics/`, logs readiness.
- Key mappings registered under a "HoloPlace" category: `K` (picker), `G` (grab mode) — both
  currently show a "not implemented" action-bar note and log.
- `build` + `test` green.

## M1 — `.litematic` parser ✅

- `LitematicaBitArray` — contiguous bit-packing (entries straddle `long` boundaries, unlike vanilla
  `BitStorage`). `bitsFor(paletteSize) = max(2, 32 - nlz(paletteSize - 1))`.
- `LitematicaSchematicReader.read(Path)` → `Schematic` (regions, palette as `BlockState[]`,
  bit-packed indices, block entities, entities, missing-block set). Supports schematic `Version` 4–7.
  Palette entries resolved with vanilla `NbtUtils.readBlockState(BuiltInRegistries.BLOCK, …)`.
  Region `Size` normalised (may be negative) to a positive-dims box with a min corner.
- `SchematicRegion.getBlockState(x,y,z)` in normalised local coords; `countNonAir()`, volumes.
- Tests: 10 passing (`LitematicaBitArrayTest`, `LitematicaSchematicReaderTest` — the latter runs
  `Bootstrap.bootStrap()` and builds `.litematic` NBT by hand: palette/dims, negative-size
  normalisation, missing blocks, version rejection, multi-region).
- `/holoplace list` and `/holoplace info <file>` print a summary (size, region count, block counts,
  data version, format version, unknown block ids).

## M2 — static textured ghost overlay 🚧 (written, compiles, **needs in-game visual check**)

- `SchematicBlockView implements net.minecraft.client.renderer.block.BlockAndTintGetter` — read-only
  view over a placed `Schematic` in camera/world space. Full-bright, no biome tint yet.
- `GhostRenderer` — on `LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN`, immediate mode: for every
  non-air block, `BlockStateModelSet.get(state).collectParts(...)`, cull faces against neighbours
  (`isSolidRender`), emit quads via `VertexConsumer.putBlockBakedQuad` at camera-relative coords
  into `RenderTypes.translucentMovingBlock()`, then `bufferSource().endBatch(...)`. Alpha comes from
  a shared `QuadInstance` colour (`GhostState.opacityAlpha()`), default 0.55.
- `GhostState` singleton — current schematic, anchor, opacity, visibility.
- Commands: `/holoplace show <file>` (anchors at the player's block pos — M3 makes it draggable),
  `/holoplace hide`. `G` now toggles ghost visibility.
- Full `build` + `test` green.

M2 verified in-game 2026-09-02 — "se ve perfecto". `RenderTypes.translucentMovingBlock()` +
`putBlockBakedQuad` at camera-relative coords in `AFTER_TRANSLUCENT_TERRAIN` works.

### Known gaps (M2)
- No 26.1 datafixers — schematics from older MC (e.g. `Circulo`, data version 4440) resolve palette
  by raw id; renamed blocks silently become air.
- No AO, no biome tint, no fluids, no block entities. No per-section VBO cache (rebuilds every frame).
- No "see through walls" (needs a custom depth-test-off `RenderPipeline`).

## M3 — drag-to-position 🚧 (written, compiles, **needs in-game check**)

- `PlacementController` singleton — grab mode. `tick()` (pumped from the key handler each client
  tick) raycasts from the eye (`level.clip`, reach-limited) and snaps the schematic's **footprint
  centre** to the block face under the crosshair; falls back to a floating point at `reach` when
  nothing is hit. Anchor Y = target + `verticalOffset`.
- First mixin: `MouseHandlerMixin` (`onScroll` HEAD, cancellable) — in grab mode the wheel changes
  `reach` (Shift: `verticalOffset`) instead of the hotbar slot. Mixin infra now set up
  (`holoplace.client.mixins.json`, wired in `fabric.mod.json`).
- `HoloPlaceConfig` — `config/holoplace/config.json` via Gson (opacity, reach, verticalOffset,
  seeThrough, lastSchematic). Prefs load on init, save on grab-stop.
- Flow: `/holoplace show <file>` loads it **and drops into grab mode**; look to position; `G` locks;
  `G` again re-grabs; `/holoplace hide` clears.

### Known gaps (M3)
- Mixin unverified at runtime (build-time AP validation passed). A broken mixin crashes on launch
  with a clear message.
- No click-to-lock / Esc-cancel (by design for MVP — `G` toggles).
- Placement persists in `GhostState` only for the session; config keeps prefs + last name, not the
  world-specific anchor.

## Next — M4: HUD controls (opacity slider, rotate ±90°, mirror) + "see through" pipeline
