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

M8 verified in-game 2026-09-03 — "funciona perfecto". Commit `69c0c86` on `main`.

## M9 — material list 🚧 (written, compiles, **needs in-game check**)

- `MaterialList` (in `schematic/`, pure + 3 unit tests) — `totals(schematic)` aggregates blocks by
  `state.asItem()` (air / no-item blocks dropped), sorted descending; `remaining(schematic, placed)`
  subtracts an already-satisfied map.
- `/holoplace materials` — prints the totals, and when a schematic is placed in a world, walks it
  through the transform + anchor to count blocks already matching and shows `<left>/<total>` per item
  plus an overall placed count. Top 30 rows.
- Test fixtures extracted to `SchematicFixtures` (shared by reader + material tests).

M9 verified in-game 2026-09-03 — "funciona perfecto". Commit `239b0aa`.

## M10 — per-world placement persistence 🚧 (written, compiles, **needs in-game check**)
*(user request: keep the schematic where it was left after relaunching)*

- `WorldPlacements` — `config/holoplace/placements.json` maps a world key → {schematic, x/y/z,
  rotation, mirror}. Key is `sp/<save-folder>` (singleplayer, via
  `getSingleplayerServer().getWorldPath(ROOT)`) or `mp/<server-ip>`.
- Saved on: grab-lock, rotate, mirror, reset, show/hide, and on disconnect (unless mid-drag).
- Restored on `ClientPlayConnectionEvents.JOIN` — reads the schematic file, sets anchor + rotation +
  mirror, and its visible/hidden state. Skips silently if the file is gone.
- **Hide ≠ clear** (user point): `/holoplace hide` stops drawing but keeps the placement (persisted
  as hidden, `/holoplace show` with no arg brings it back, still restores on rejoin). `/holoplace
  clear` forgets it entirely. `DISCONNECT` saves then clears session state.

### Known gaps (M10)
- SP key is the save-folder name — renaming a save loses its placement.
- No hide/show keybind yet (commands only).

M10 verified in-game 2026-09-03 (hide/clear split too — "revisado y perfecto"). Commits `71dbdf7`,
`a8a9a9d`.

## M11 — biome tint 🚧 (written, compiles, **needs in-game check**)

- `GhostMesh.Quad` gains a `tinted` flag (from `quad.materialInfo().isTinted()`, computed at build).
- `GhostRenderer` resolves per-block tint (index 0) via `BlockColors.getTintSources(state)` →
  `BlockTintSource.colorInWorld(state, mc.level, worldPos)` — against the **real** world, so
  grass/leaves/water/redstone take the local biome colour. Cached per (mesh, anchor); recomputed
  only when the anchor moves. Untinted quads still render white.

### Known gaps (M11)
- Only tint index 0 is cached; blocks with a second tint layer use index 0's colour (rare).
- Tint recompute on every drag tick — fine at typical sizes, could hitch on a very large schematic.

M11 verified in-game 2026-09-04 — "esta perfecto". Commit `4e27e91`.

## M12 — fluids 🚧 (written, compiles, **needs in-game check**)

- `GhostMesh` also collects fluid blocks (`state.getFluidState()` non-empty — plain water/lava and
  waterlogged blocks): footprint-local position + transformed state.
- `GhostRenderer.renderFluids` — per fluid block, `FluidRenderer.tesselate(view, worldPos, output,
  state, fluidState)` writing into the same ghost buffer (`FluidRenderer.Output.getBuilder` takes a
  `VertexConsumer` directly). Uses a render-anchored `SchematicBlockView` for neighbour/height
  checks. Respects build-assist.
- `SchematicBlockView.getBlockTint` now delegates to the real `ClientLevel` (the render-time view is
  world-anchored) so water takes the biome colour.

### Known gaps (M12)
- Fluid opacity isn't scaled by the ghost opacity slider.
- Per-frame fluid tesselation (not cached like block quads) — fine unless a schematic is mostly water.

M12 verified in-game 2026-09-04 — "listo, revisado". Commit `d451f63`.

## M13 — block-entity markers 🚧 (written, compiles, **needs in-game check**)

Block entities that render (almost) no model — chests, signs, beds, banners, skulls, conduits —
were invisible in the ghost. `GhostMesh` now also collects "BE block, produced 0 model quads", and
`GhostRenderer.renderBlockEntityMarkers` draws a translucent **wire cube** at each such cell
(`ShapeRenderer.renderShape` + `RenderTypes.lines()`), honouring build-assist.

This is a **placeholder** — it shows *where* a chest/sign goes, not the actual chest model. Real
block-entity rendering (construct `BlockEntity.loadStatic` from the schematic's `TileEntities` NBT,
run the BER via `BlockEntityRenderDispatcher.tryExtractRenderState` + `submit` in `COLLECT_SUBMITS`)
is a bigger, separate task — deferred unless wanted.

### Known gaps (M13)
- Markers use depth-tested lines, so in x-ray mode they're still occluded by walls.

M13 built (not separately verified — user said "continua"). Commit `6f28bbc`.

## M14 — one controls screen + block-only match 🚧 (written, compiles, **needs in-game check**)

- `HoloPlaceScreen` (`K`) replaces the picker: one flat screen with an opacity slider
  (`AbstractSliderButton`, 5–100 %), checkboxes for see-through / hide-placed / match-block-only,
  Hide-Show and Reset buttons, and the schematic list (capped at 8). Every change persists.
- `GhostState.matches(world, ghost)` — exact state `==` or (block-only mode) `world.is(block)`.
  Used by the renderer (blocks, fluids, BE markers) and `/holoplace materials`. Fixes leaves /
  redstone / stairs-shape never counting as placed.
- Old `SchematicPickerScreen` removed.

### Known gaps (M14)
- Screen has no scroll — schematic list still capped (8 here).

M14 verified in-game 2026-09-04 — "funciona todo".

## M15 — perf hardening 🚧 (written, compiles, **needs in-game check**)

Not the GPU-buffer rewrite — lower-risk throttling on the paths that scaled with block count:

- **Build-assist world scan** was `getBlockState` per baked block *every frame*. Now cached as a
  `boolean[] needsPlacing`, rescanned at most every 250 ms (or immediately on mesh / anchor /
  match-mode change). `placedCount` comes from the same pass.
- **Biome tint** is frozen while grab mode is active (dragging) — recomputed once on drop.
- **Guard**: a schematic over 4M quads is not rendered; the HUD says "too large" instead of freezing.

### Known gaps (M15)
- The per-quad `putBlockBakedQuad` in the frame buffer is still O(quads) every frame — a real
  GPU-vertex-buffer upload would remove it (deferred; higher risk on the new 26.1 GPU API).

M15 verified in-game 2026-09-04. Commit `dea7c7c`.

## M16 — real block-entity models 🚧 (written, compiles, **needs in-game check — high risk**)

- `SchematicRegion.blockEntityNbt(x,y,z)` maps the region's `TileEntities` list by local pos.
- `GhostMesh.build` constructs a `BlockEntity` per BE block via `BlockEntity.loadStatic(footprintPos,
  transformedState, nbt, registryAccess)` + `setLevel(mc.level)`; cached in the mesh.
- `GhostRenderer.collectBlockEntities` runs on `LevelRenderEvents.COLLECT_SUBMITS`:
  `dispatcher.tryExtractRenderState(be, partial, null)` → set `blockPos` to the world cell →
  `poseStack.translate(cell - camera)` → `dispatcher.submit(state, ps, submitNodeCollector,
  cameraRenderState)`. Honours build-assist. Falls back to the wire cube when a BE has no state.
- Toggle: "Block entity models" checkbox (default on), persisted. Off → wire cubes.

### Known gaps / risks (M16)
- **High runtime risk** — submit-model integration, whether `COLLECT_SUBMITS` is flushed with vanilla
  BEs, the `blockPos`-reassign + pose-translate combo. If it renders wrong/crashes, the checkbox
  turns it off.
- BEs render **opaque** (submit model gives no alpha) — a chest won't fade with the opacity slider.
- Chest openness/brightness `combine()` queries `mc.level` at the ghost cell, not the schematic;
  facing + single/double come from the block state so geometry is right, but a real chest sitting
  where the ghost is could confuse it.
- Sign text, banner patterns etc. come straight from the schematic NBT.

M16 built (not separately verified — user said continue). Commit `c8fcafa`.

## M17 — UX batch 🚧 (written, compiles, **needs in-game check**)

User picked all evaluated improvements except inventory-aware materials:

- **D — help & welcome**: `/holoplace help` lists keys + commands; a one-time chat hint on first
  join (`config.seenIntro`).
- **E — rotate around centre**: rotating a *locked* ghost now keeps the footprint centre fixed
  instead of pivoting on the min corner (`PlacementController.keepFootprintCentre`).
- **A — manual placement**: `/holoplace move <x y z>`, `/holoplace nudge <dir> [n]`, and X/Y/Z +
  "Move" fields on the screen.
- **B — layer clip**: `GhostState.layerVisible(localY)` gates blocks/fluids/BEs/markers to a
  footprint-local Y range. `/holoplace layers <min> [max] | off` and a screen row. Persisted.
- **C — wrong-block highlight**: the build-assist scan also flags cells where the world holds a
  different non-air block; those get a red wire cube.
- **F — face shading**: classic per-face darkening (`CardinalLighting.byFace`) on ghost quads, on by
  default, "Shading" checkbox. Much less flat.
- **G — paged schematic list**: 10 per page with `< page n/m >` instead of a hard cap of 8.

### Known gaps (M17)
- Layers are footprint-local Y (0 = bottom); no keybind/scroll to sweep the layer window yet.
- Shading is per-quad, not true per-vertex AO.

## Next — verify M16 + M17 in-game; GPU buffer still open
