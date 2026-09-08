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
- Shading is per-quad directional darkening, not true per-vertex AO.

M16 + M17 verified in-game 2026-09-04, with these follow-up fixes:
- `d321e05` — fluids rendered far above the ghost (`FluidRenderer` emits section-local coords);
  wrapped the output in `OffsetVertexConsumer`. Also litematica strips the BE `id` from its NBT →
  build via `EntityBlock.newBlockEntity` + `loadWithComponents`. Layer control → two sliders +
  count. Key hints on the screen checkboxes.
- `0bd0876` — block entities were all culled: `tryExtractRenderState` distance-checks
  `be.getBlockPos()`, which is near the origin for a ghost BE. Extract the render state directly.
- `1814140` / `6ca6b6b` — block-entity models ignored the opacity slider (their render types don't
  blend). `GhostSubmitCollector` wraps the submit collector, multiplies the ghost alpha into every
  colour, and — for non-blending render types — reflectively reads the type's texture and swaps in
  `RenderTypes.entityTranslucent`. Verified: chests/signs now fade with the slider.

## Status: MVP complete, all milestones verified.

Open item: GPU vertex-buffer upload (removes the per-frame `putBlockBakedQuad` cost; current cap is
4M quads). Higher risk on the new 26.1 GPU API; deferred to [`docs/backlog.md`](backlog.md).

## M18 — post-MVP UI polish 🚧 (written, compiles, **needs in-game check**)

*(user request: polish the UI, add Spanish, add a look-at tooltip for wrong blocks, and a marker for
blocks that don't belong to the schematic at all)*

- **Full Spanish translation** — every player-facing string (HUD, tooltip, `/holoplace` command
  feedback, action-bar messages, the drag-and-drop/import messages, the welcome message, and the
  `K` screen) now goes through `Component.translatable(...)` with parallel `en_us.json` / `es_es.json`
  entries, instead of the keybind-only translation that shipped with the MVP. Colour codes (`§`) live
  inside the translated values, matching the existing HUD convention.
- **"Extra block" marker** — a new orange wire cube (`GhostRenderer.scanExtraBlocks` /
  `renderExtraBlocks`) flags a world cell that's non-air where the schematic says air, i.e. a block
  that doesn't belong to the build at all. Distinct from the existing red "wrong block" cube (world
  block present but doesn't match what the schematic wants there). Scan is a bounding-box walk over
  the schematic's footprint, skipped above a 2M-cell volume guard (logged once) to avoid a hitch on a
  huge or very sparse schematic. `GhostRenderer.renderMarkerSet` was factored out of the old
  `renderWrongBlocks` so both marker kinds share one draw path.
- **Look-at tooltip** (`GhostTooltipHud`, new HUD element) — while build-assist is on and the
  crosshair (`Minecraft.hitResult`, no extra raycast) is on a block inside the schematic's footprint
  that doesn't match the plan, shows a small icon + label near the crosshair: the game's own item
  icon (`GuiGraphicsExtractor.fakeItem`) for the correct block plus "Should be: X", or a barrier icon
  plus "This block doesn't belong here" for an extra block.

### Known gaps (M18)
- The extra-block scan re-walks the whole footprint on every `placementScan` tick (same 250 ms
  throttle as the rest of build-assist) rather than being incremental.
- Tooltip only reacts to the vanilla crosshair hit — no reach-independent lookup, so it respects
  normal block-interaction distance.

M18 verified in-game 2026-09-04 — "funciona perfecto". Follow-up user request: reduce the render
cost of wrongly-placed blocks, without changing the default build-assist behaviour.

- **"Hide wrong too"** — new checkbox (`GhostState.hideWrongToo`, persisted), off by default so
  existing build-assist behaviour is unchanged. When on *and* build-assist is on, a wrongly-placed
  cell (world has a non-matching, non-air block) also skips the full ghost model — same as an
  already-correct cell — leaving only the red wire-cube marker to say "fix this". Applies uniformly
  to regular blocks, fluids, and block-entity markers/models via a shared `isBuiltOrHiddenWrong`
  check, reusing the existing `wrongBlock[]` scan (no extra world scan needed).

**Bug fix** (user-reported: a wrong block in a chest/sign/etc.'s spot wasn't flagged): block entities
produce no model quads of their own, so they were never part of `wrongBlock[]` — only regular,
quad-producing blocks were scanned for wrongness. Added a parallel `wrongBE[]` scan
(`scanWrongBlockEntities`, same throttled pass as the rest of build-assist) and reused the existing
`renderMarkerSet` helper to draw the same red wire cube over a block entity's cell when the world
holds the wrong block there.

**Bug fix** (same root cause, user-reported: a wrong block where *water* should go wasn't flagged):
a pure water/lava source has no block model either, so it lived only in the mesh's fluid arrays,
outside `wrongBlock[]`. Added `wrongFluid[]` (`scanWrongFluids`) — flags a fluid cell whose world
block is a different, non-air, non-same-fluid block. Waterlogged blocks are skipped (their host
block already covers them via `wrongBlock[]`), and the same fluid at a different level (a source vs.
its own edge flow) is not flagged.

## M19 — capture: area selection tool 🚧 (written, compiles, 23 tests green, **needs in-game check**)

*(first slice of schematic creation — full plan in [`docs/plan-capture.md`](plan-capture.md))*

- `SelectionState` (in `src/main` so it's unit-testable) — two corners, derives the inclusive
  min/max box, size and volume regardless of click order. `SelectionStateTest` — 5 cases.
- `CaptureController` (client singleton) — `B` keybind / `/holoplace capture` toggles "selection
  mode"; while on, left-click sets corner 1 and right-click sets corner 2 via Fabric's
  `AttackBlockCallback` / `UseBlockCallback` (consumed only while selecting, so normal play is
  untouched — no new mixin, per the audit's "prefer events over mixins" note). `/holoplace capture
  pos1|pos2` sets a corner from the crosshair; `/holoplace capture clear` resets.
  - The callbacks return `InteractionResult.FAIL`, not `SUCCESS`, to consume a click — `SUCCESS`
    still lets Fabric run the prediction and send the action packet, so the server would break /
    place the block anyway (instantly in creative). `FAIL` cancels outright. (First in-game test
    caught this: blocks still broke/placed while selecting.)
- `SelectionRenderer` — cyan wire box for the full selection, yellow unit cube for a lone first
  corner. Reuses `ShapeRenderer` + `RenderTypes.lines()` like the ghost markers.
- `CaptureHud` — top-right panel (opposite corner from `GhostHud`): both corners, size X×Y×Z, cell
  count, and a red "selection very large" line past ~5M cells.
- New lang keys (`holoplace.capture.*`, `holoplace.hud.capture_*`, `key.holoplace.capture_select`) in
  both `en_us` and `es_es`.

### Known gaps (M19)
- The selection box has no upper-bound enforcement, only a HUD warning — the hard save cap is in M20.

M19 follow-up polish (user-requested): corner 1 (left-click) draws a green cube, corner 2 (right-click)
an orange one — shown once set so which is which stays readable, HUD lines tinted to match — and the
selection box gained translucent shaded faces (`RenderTypes.debugQuads`, 6 low-alpha quads) on top of
the wire edges.

## M20 — capture: `.litematic` writer + full capture 🚧 (written, compiles, 26 tests green, **needs in-game check**)

- `LitematicaSchematicWriter` (in `src/main`, so the round-trip is testable) — the inverse of
  `LitematicaSchematicReader`: single-region, format `Version` 6, palette with air forced to index 0,
  bit-packing through the (until now unused) `LitematicaBitArray.set`. `write(Path, …)` gzips it;
  `toNbt(…)` is the seam the test uses. `Region` record carries the dense `BlockState[]` grid plus
  block entities / entities already in litematica's layout.
- `CaptureWriter` (client) — walks the selected region of the client world into a `Region`: block
  states straight from `level.getBlockState`, block entities via `BlockEntity.saveCustomOnly` +
  region-relative `x`/`y`/`z` ints (litematica's format — no vanilla `id`).
- `CaptureController.save(name)` + `/holoplace capture save [name]` — no name → `capture-<timestamp>`;
  writes into the same `config/holoplace/schematics/` folder the picker reads, so a capture shows up
  there immediately. Name is sanitised; a save over `MAX_SAVE_VOLUME` (8M cells) is refused.
- `LitematicaSchematicWriterTest` — 3 round-trips: a grid with a stateful block (stairs facing/half)
  read back cell-by-cell through the real reader; an all-air selection; a block-entity's NBT kept by
  local position.

### Known gaps (M20)
- No live progress/feedback for a big capture — it's synchronous on the calling thread (fine under
  the 8M cap, but a large capture will hitch briefly).

M20 verified in-game 2026-09.

## M21 — capture: "changes only" mode ❌ built, tested, then removed

Tried and worked: a passive `LevelBlockChangeMixin` on `Level.setBlock` fed a `ChangeLog`, filtered
to changes within 1.5s of a player block interaction (to drop worldgen post-processing / grass /
fluid ambient churn), and `save changes` exported only those cells. The user paused it, then asked to
remove it entirely (2026-09). Deleted: `ChangeLog`, `ChangeTracker`, `LevelBlockChangeMixin`,
`ChangeLogTest`, `HoloPlaceConfig.captureChangesOnly`, the `/holoplace capture mode` command, the
K-screen mode button, the HUD mode lines, and the related lang keys. `CaptureWriter.capture` is back
to always-full. If ever revisited: Minecraft stores no per-block "who placed this", so after-the-fact
detection isn't possible — you must track changes live or diff against a baseline snapshot.

## M22 (partial) — capture in the K screen, entities, mod icon 🚧 (written, compiles, **needs in-game check**)

- **Entity capture** (`CaptureWriter.captureEntities`) — capture now also picks up entities whose
  position is inside the box: `entity.save(TagValueOutput…)` + `id`, with `Pos` rewritten
  region-relative (same convention as the block-entity `x`/`y`/`z`). Verified in-game: an item frame
  and an armour stand write into the file with their client-side data. Client-side data only — item
  frames, armour stands, paintings and a mob's *visible* state come through; full mob NBT (AI,
  attributes, inventory) isn't synced to the client so it won't be in the file. Players are skipped.
- **Capture row in the `K` screen** — `[Área]` (toggles selection mode, closes the screen), a name
  field, and `[Guardar]`.
- **Mod icon** — `assets/holoplace/icon.png` + the `"icon"` field in `fabric.mod.json`.
  Final art (2026-09-08): holographic pixel hammer, user-made. 128 in the jar, 512 in
  `docs/assets/icon-512.png` for the store listing.

A scroll/scrollbar on the `K` screen (it's getting tall).

## M23 — render schematic entities in the ghost ✅ (item frame + armour stand verified in-game 2026-09)

*(user request after seeing a captured item frame / armour stand not show up)*

- `GhostMesh` now deserialises the schematic's `Entities` per (schematic, transform):
  `EntityType.create(TagValueInput…, mc.level, LOAD)`, then `entity.mirror(…)` + `entity.rotate(…)`
  for the ghost's rotation/mirror (hanging entities update their `direction` too), and
  `PlacementTransform.forwardExact` (a fractional-position variant of `forward` — reflects about the
  box edge `size - p`, not block parity `size - 1 - p`) for the position. Stored as a `GhostEntity`
  record (entity + footprint-local x/y/z).
- `GhostRenderer.submitEntities` — a second `COLLECT_SUBMITS` handler: per entity, `setPos(anchor +
  local)` + `setOldPosAndRot`, then `dispatcher.extractEntity` / `dispatcher.submit(...)` through the
  same `GhostSubmitCollector` the block-entity path uses, so entities fade with the opacity slider.
  Honours the layer clip.
- Toggle: "Entities" checkbox on the `K` screen (`GhostState.showEntities`, persisted, default on).

**Follow-up fixes** (first in-game test: item frame didn't draw, armour stand spun):
- Armour stand spin — a never-ticked `LivingEntity` has `yBodyRotO`/`yHeadRotO` at 0 while
  `yBodyRot`/`yHeadRot` hold the target, so the renderer's `rotLerp` swept between them as
  `partialTick` cycled. Fix: `submitEntities` now extracts with `partialTick = 1.0f` (every lerp
  resolves to the current value) and syncs the `*O` fields each frame; `makeEntity` syncs them once.
- Item frame invisible — `BlockAttachedEntity.readAdditionalSaveData` rejects the captured `block_pos`
  (absolute world coords, >16 blocks from the region-relative `Pos`), so the frame loaded with a
  default direction/position. Fix: a small `HangingEntityInvoker` mixin (`@Invoker` for the
  `protected setDirection`) — `makeEntity` sets the attach block near the transformed position, then
  `setDirection(transformedFacing)` recalcs the bounding box. `Rotation.rotate(Direction)` /
  `Mirror.mirror(Direction)` give the transformed facing.
- Ghost entities render full-bright (`s.lightCoords = FULL_BRIGHT`), like the ghost blocks.

**Second round** (item frame still invisible, then wrong rotation / no fade — `aae74c5`):
- The frame border bakes into a **fabric-renderer-api `Mesh`** with an empty vanilla `parts` list,
  submitted through FRAPI's *extended* `submitBlockModel(…, Mesh, …)`. `GhostSubmitCollector` only
  overrode the vanilla 7-arg method, so FRAPI's default forwarding **dropped the mesh** → nothing
  drawn. Fix: override the extended overload and forward the mesh to the real collector (stored as an
  `ExtendedBlockModelSubmit`, drawn by `BlockFeatureRenderer`).
- Opacity for the mesh: `GhostSubmitCollector.fade` rebuilds it via `Renderer.get().mutableMesh()`
  with `multiplyColor(opacity<<24 | 0xFFFFFF)` per quad, swaps the opaque render type for a blending
  one (`blendable`) and flags it translucent so it draws in the translucent feature pass.
- Frame faced the wrong way after a schematic rotation — `makeEntity` transformed a hanging entity's
  facing twice (`HangingEntity.mirror()/rotate()` already call `setDirection`, then the code
  re-applied `mirror`+`rotate`). Now the hanging-entity branch runs *before* those generic calls and
  computes the facing once from the just-loaded `Facing`. Verified in-game 2026-09.

**Third round** (opacity slider skipped paintings, framed items, layered parts — `56f4de6`):
- Paintings submit their picture via `submitCustomGeometry` — passed straight through. Now swaps the
  render type (`blendable`) and wraps the buffer in a new `TintingVertexConsumer`.
- Framed items submit raw `BakedQuad`s (white vertices, no tint index); `ItemFeatureRenderer` draws
  them opaque regardless of `tintLayers`. Rebuild each quad against a blending render type + tint
  slot 0 and pass a faded colour there; also override FRAPI's extended `submitItem(…, MeshView, …)`
  (was dropping the mesh).
- Villager clothes/hats, armour trims, … render via `RenderLayer.renderColoredCutoutModel` →
  `collector.order(n).submitModel(…)`, and `order(n)` returned the *unwrapped* real collector. Tinting
  split into `GhostOrderedSubmitCollector`; `order(n)` now returns a wrapped ordered collector.
- Verified in-game: painting, item frame + water bucket, villager (body + robe + hat) all fade.

### Known gaps (M23)
- Entities aren't ticked — armour stands / item frames / paintings are fine (static), but a mob shows
  in a default idle pose with no animation.
- Client-side entity data only (from capture) — a captured mob has no AI/inventory NBT, so it renders
  bare.
- Entity `Pos` from third-party (real Litematica) files is assumed region-relative; if some file
  stores it differently the entity will be offset (fixable once seen).

## M24 — UI/GUI pass 🚧 (written, compiles, **needs in-game check**)

Toward the first launch (`0.1.0`), with the "0.2.0" scope pulled in. Norte: ~6 controles visibles,
el resto agrupado/oculto/explicado. Propuesta + mockup aprobados: `docs/ui-redesign-m24.md`,
`docs/assets/k-screen-mockup.html`.

- **`HoloPlaceScreen` reescrito** — manual y-cursor layout en secciones: cabecera de estado
  (nombre · tamaño · rot · espejo · Ocultar/Mostrar), `§ DISPLAY` (opacidad + fila
  `Rotar [↺] 90° [↻]  Espejo [LR▸]  Reset` que reusa `PlacementController` + see-through/shading),
  `§ BUILD ASSIST` (hide-placed + línea de progreso + `Ignorar orientación` / `Ocultar errores`
  **indentados y deshabilitados** cuando el padre está off + capas), `§ SCHEMATICS`
  (`ObjectSelectionList` con scroll, la cargada marcada, `●`), `§ CREATE`, y `▸ Avanzado`
  plegable (coords + modelos BE + entidades). Scroll con rueda si el contenido no cabe
  (`scrollY` estático, `rebuildWidgets()` on wheel).
- **Tooltip en cada control no obvio** — `holoplace.tip.*` (ES + EN), 11 claves.
- **Hover sobre una fila de la lista → metadatos** (`SchematicMeta`, nuevo en `src/main`): lee solo
  la cabecera NBT en un hilo daemon, cachea por `Path`, muestra tamaño / nº de bloques / regiones /
  data version en un tooltip. Nunca desempaqueta bloques.
- **HUD colapsado** (`GhostHud`) — bloqueado: solo `❖ nombre` (+ progreso si build-assist). Agarrando:
  las 6 líneas completas.
- `build` + `test` verdes. Espaciado subido tras el primer screenshot (filas ~22px).
- **Repaso de opciones (2026-09-08)** — cada toggle evaluado contra "¿su default sirve al 90%?":
  - **"Sombreado de caras"** → quitado, siempre ON (nadie lo apaga; apoya el pitch "parece real").
  - **"Ocultar también mis errores"** → quitado, siempre ON (el modelo fantasma sobre un bloque
    mal colocado es clutter; el contorno rojo + el tooltip "debería ser X" ya informan).
  - **"Ignorar la orientación del bloque"** (`match_block_only`) → movido a "Avanzado" (flujo real
    —pasada rápida vs acabado— pero de nicho).
  - `HoloPlaceClient` fuerza `setShade(true)` / `setHideWrongToo(true)`; los campos de config
    siguen (persistencia), solo desaparece el checkbox. 4 claves de lang muertas eliminadas
    (parity 148/148).
  - `VISUALIZACIÓN` queda: opacidad · rotar/espejo/reset · ver a través.
    `ASISTENTE` queda: ocultar-colocados + progreso + capas. `AVANZADO`: mover-coords ·
    ignorar-orientación · modelos BE · entidades.

**Ajustes tras los screenshots del usuario:**
- Fondo del menú in-world muy claro sobre terreno brillante → oscurecido (fill full-screen + banda
  tras el panel en `extractRenderState`).
- Botones `↺`/`↻` sin glifo en la fuente de MC → `-90°` / `+90°`.
- Espaciado subido (filas ~22px, cabeceras 8+16).
- **See-through no cubría los marcadores del asistente** — los cubos de alambre de error/sobra
  usaban `RenderTypes.lines()` (con test de profundidad) → ocultos tras los bloques ya puestos.
  Nuevo `GhostPipelines.SEE_THROUGH_LINES` (snippet LINES + depth `ALWAYS_PASS`);
  `linesForGhost(seeThrough)` en los 3 pases de marcadores de `GhostRenderer`.
- **Dos pestañas Construir / Crear** (`805b226`) — las dos tareas del mod, visibles desde el
  primer momento. Construir = header + display/asistente/schematics/avanzado. Crear = flujo de
  captura con espacio (intro, seleccionar área, esquinas + tamaño, limpiar, nombre + guardar).
- **"Mover a coords" quitado** de Avanzado (redundante con arrastrar y `/holoplace move`).
- Hint de drag-and-drop bajo la lista ("suelta un .litematic en la ventana").
- **Punto 3 (opciones)** — fusionados los toggles "Modelos de entidades de bloque" + "Entidades"
  en uno solo: **"Detalles (cofres, carteles, marcos, cuadros)"** (Avanzado).
- **Texto desbordado** — `label()` recorta al ancho del panel con "…"; las 4 frases largas
  (prompt vacío, hint de drop, intro + hint de la pestaña Crear) usan `MultiLineTextWidget`.
- **Celda mal colocada = sin fantasma** — cuando el asistente (`H`) está activo, una celda con un
  bloque equivocado ya NO dibuja su modelo fantasma encima; solo queda el marcador rojo + el
  tooltip "Debería ser: X" en la cruceta. Superponer fantasma y bloque real no aportaba nada.
  `hideWrongToo` deja de ser opción (config / `GhostState.setHideWrongToo` fuera); es el
  comportamiento fijo del asistente.
- **Sin tope de marcadores** — se quitó `MARKER_CAP` (150). En esquemas grandes los marcadores
  volvían a desaparecer del todo. Ahora se dibuja un cubo de alambre por celda marcada siempre,
  como antes de M24. El `EXTRA_SCAN_VOLUME_LIMIT` (2M) y `MAX_QUADS` ya acotan el coste.
- **Caja envolvente descartada** — la caja que salía con >150 errores parecía delimitar el
  schematic y confundía; eliminada junto con su toggle (`GhostState.errorBox` / config /
  `holoplace.ui.error_box`). Grosor/opacidad de marcadores: `MarkerOpacitySlider` propio en
  Avanzado (default 0.85), líneas 2.5px.

### M24 — CERRADO (2026-09-08)
- Verificado in-game por el usuario: las dos pestañas, layout/scroll, tooltips, la lista + hover de
  metadatos, el HUD colapsado, sub-opciones en gris, see-through + asistente, y el asistente sobre
  esquemas grandes con los cambios de marcadores.
- Limpieza final: 11 claves de lang muertas del diseño viejo eliminadas
  (`tip.layers`, `ui.capture_select`, `ui.go`, `ui.layer_of/layer_one/layer_range`, `ui.move`,
  `ui.page`, `ui.reset_transform`, `ui.schematics`, `ui.sect.create`). Parity 147/147.
- `./gradlew build` verde (18 tests), `holoplace-0.1.0.jar` generado.
- Thumbnail al hover → aplazado a 0.2.0 (2026-09-08). En 26.1 no hay estado PIP para "structure";
  el 3D vivo pediría RenderTarget + pipeline propios en la capa de render, y 0.1.0 sale en 26.1.2
  para no arriesgar esa capa antes del lanzamiento. Se hace junto al port a 26.2.

## M26 — horneado de malla off-thread (2026-09-08, falta check in-game)

El tirón al cargar / rotar un schematic grande venía de `GhostMesh.build` corriendo entero en el
hilo de render.

- **`GhostMesh.build` partido en dos**: `bakeGeometry(schematic, transform)` → `GhostMesh.Geometry`
  (el paseo pesado: teselado de modelos, culling de caras, fluidos, NBT crudo de BE/entidades — sin
  crear ningún objeto vivo, seguro fuera del hilo de render); `Geometry.assemble(registries)` →
  `GhostMesh` (construye los `BlockEntity` / entidades desde el NBT, esto sí en el hilo de render,
  pero es barato — decenas de objetos, no millones de quads).
- **`GhostMeshBaker`** — ejecutor de un solo hilo daemon (`holoplace-mesh-baker`, prioridad −2).
  `poll(schematic, transform)` (render thread, 1×/frame): devuelve la malla lista, o `null` mientras
  hornea. Cuando el `CompletableFuture` termina, llama a `assemble` en el hilo de render y cachea.
  Rehornea al cambiar schematic o transform. `invalidate()` tira todo (cambio de mundo).
- **Mientras hornea**: `GhostRenderer` dibuja el **contorno del footprint** (caja de alambre cian,
  sobre paredes) para que puedas seguir posicionando, y el HUD muestra "preparando el modelo…"
  (`matchedBlocks == MESH_BAKING = -3`, clave `holoplace.hud.baking`).
- `submitBlockEntities` / `submitEntities` ahora salen si `!m.matches(schematic, transform)` — así
  una malla vieja no renderiza sus BE en la rotación anterior durante el rehorneado.
- Modelos horneados = inmutables tras la carga de recursos → teselar fuera del hilo es seguro
  (es lo que hace el propio Sodium para las secciones de chunk).
- Posible micro-parpadeo al rotar un schematic **pequeño** (1-2 frames de contorno antes de que el
  worker entregue) — verificar en el juego si molesta; si sí, añadir una gracia "mantener malla
  vieja N ms".

## Reader hardening + licensing note (pre-publish, 2026-09)

*(from the publish-readiness audit — the [High] finding was: no size cap before allocating memory.)*

- `LitematicaSchematicReader` now bounds a load: `NbtIo.readCompressed` with a 256 MiB `NbtAccounter`
  (was `unlimitedHeap()`), a per-axis cap (30 000), a per-region volume cap (64 M cells), and a region
  count cap (4096). An oversized/corrupt/hostile `.litematic` throws a clear `IOException` (caught and
  shown to the player) instead of freezing or OOM-ing the client.
- `LitematicaBitArray` rejects a `size` that would overflow the `(int)` word-count cast or demand a
  multi-GB `long[]`. 2 new tests each side (32 total).
- `docs/licensing.md` — Litematica + MaLiLib are LGPL-3.0; that binds their code, not the idea /
  features / file format. An independent MIT implementation that copies no code or assets is fine
  (SAS v. WPL: software functionality and file formats aren't copyrightable). Code comments that
  named Litematica internals were reworded to describe the format instead.
