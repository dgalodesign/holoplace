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

M3 verified in-game 2026-09-03 — "funcionó genial". Grab mode + scroll mixin work at runtime.
Initial commit `e5be7df` on `main`.

### Known gaps (M3)
- No click-to-lock / Esc-cancel (by design for MVP — `G` toggles).
- Placement persists in `GhostState` only for the session; config keeps prefs, not the anchor.

## M4 — rotation, mirror, opacity, HUD 🚧 (written, compiles, **needs in-game check**)

- `PlacementTransform` (record, in `schematic/`) — mirror-then-rotate mapping between authored
  bounding-box space and transformed footprint space, `forward` + `inverse`, footprint XZ swap on
  quarter turns, plus `applyToState` (`state.mirror(m).rotate(r)`). **5 unit tests** (forward∘inverse
  identity over every mirror×rotation, bijection onto footprint, known corners).
- `GhostState` carries `Rotation` + `Mirror`; `GhostRenderer` and `SchematicBlockView` both go
  through the transform (renderer forward, view inverse for culling). `PlacementController` centres
  the footprint using the transformed dimensions.
- Keys: `R` rotate CW / `Shift+R` CCW, `M` cycle mirror. `Alt+wheel` (ghost visible) = opacity ±5%.
  `/holoplace reset`. All persisted to `config/holoplace/config.json`.
- `GhostHud` — always-visible one-panel readout (name, pos, footprint size, rotation, mirror,
  opacity, key hints) via `HudElementRegistry.attachElementAfter(HOTBAR, …)` + `GuiGraphicsExtractor`.
- 15 unit tests green.

M4 verified in-game 2026-09-03 — "todo funciona perfecto". Rotation, mirror, opacity and the HUD
panel all work (HUD renders via the new 26.1 extractor model).

## M5 — OS drag-and-drop, flat picker, tab-completion 🚧 (written, compiles, **needs in-game check**)

- `MouseHandlerMixin.onDrop` — a `.litematic` dropped onto the game window is copied into
  `config/holoplace/schematics/` and shown (into grab mode). MC's own `onDrop` still runs.
- `SchematicImport` — the one "load + show + grab" path shared by the command, picker and file-drop.
- `SchematicPickerScreen` (`K`) — flat `LinearLayout` of one button per `.litematic` (capped at 14,
  overflow hint), one click loads. "Open folder" when empty. No folders, no nested menus.
- `/holoplace show|info` now tab-complete schematic names.

M5 verified in-game 2026-09-03 — "funciono perfecto". OS drag-and-drop, picker screen, and
tab-completion all work. **MVP functionally complete.** Commit `cb78f18` on `main`.

## M6 — mesh cache 🚧 (written, compiles, **needs in-game check**)

The renderer no longer tesselates every block every frame. `GhostMesh.build(schematic, transform)`
does the model tesselation + face culling once and stores a flat `List<Quad>` of
`(footprint-local x/y/z, BakedQuad)`. `GhostRenderer` rebuilds it only when the schematic /
rotation / mirror changes; each frame just replays the list with a per-frame translate
(`anchor - camera`) and colour. So **dragging the anchor and changing opacity are free** — no
rebuild. Rebuild time is logged at debug level.

### Known gaps / risks (M6)
- Unverified visually — must render identical to before, just faster. Watch for a hitch on load /
  rotate (the one rebuild) on a large schematic.
- Still `List<Quad>` objects (~32 B/quad) + per-frame `putBlockBakedQuad` per quad — much cheaper
  than tesselation, but a GPU-buffer upload would remove the per-frame vertex writes entirely (M7+).

## M7 — see-through / x-ray 🚧 (written, compiles, **needs in-game check — highest risk so far**)

- `GhostPipelines` — a custom `RenderPipeline` (registered via `RenderPipelines.register` during
  client init) = the translucent-block pipeline with `DepthStencilState(CompareOp.ALWAYS_PASS, false)`
  (depth test always passes, no depth write), wrapped in a `RenderType`. `GhostRenderer` uses it when
  `GhostState.seeThrough()` — the ghost then paints over the player's own blocks.
- Toggle: `X` key, `/holoplace seethrough`, persisted; shown as `x-ray` in the HUD.

### Risks (M7)
- The custom pipeline may not compile/load at runtime (registration timing vs the shader manager
  pre-compile, or the `core/block` shader not liking the setup). If it fails it could render nothing
  or crash on first use of x-ray mode — normal mode is unaffected.
- Translucent sorting across the whole schematic with depth off may show back faces through front
  faces. Acceptable for an x-ray overlay; revisit if it looks bad.

M7 verified in-game 2026-09-03 — "funcionó perfecto". The custom x-ray pipeline compiles and works
at runtime. Commits `0d7aba3` (M6) and `2ac665e` (M7) on `main`.

## M8 — build-assist: hide already-placed blocks 🚧 (written, compiles, **needs in-game check**)

- `GhostMesh` restructured to be block-addressable: per source block it keeps the footprint-local
  position, the transformed `BlockState`, and the slice of `quads[]` it produced (`quadStart[]`).
  Tesselation still happens once.
- `GhostRenderer`, when `GhostState.hideMatched()`, walks the baked blocks and skips any whose
  `level.getBlockState(anchor + local)` is `==` the ghost's state — no re-tesselation, just a
  world lookup + identity compare per block per frame.
- HUD shows `build <placed>/<total> (NN%)` progress while on.
- Toggle: `H` key, `/holoplace buildassist`, persisted.

### Known gaps / risks (M8)
- Exact-state compare: blocks with properties the player can't reproduce (leaves `distance`,
  redstone `power`, …) never match and stay visible. A "block-only" compare mode is a later option.
- Per-frame `getBlockState` per baked block — fine to ~20k blocks, may want throttling for huge ones.

## Controls now: G grab · R/⇧R rotate · M mirror · X x-ray · H build-assist · Alt+wheel opacity ·
wheel/⇧wheel reach/height · K picker · drop a .litematic on the window

## Next — M9: biome tint (grass/leaves/water) · fluids · block entities · GPU-buffer upload ·
material list (`/holoplace materials`)
