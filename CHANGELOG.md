# Changelog

All notable changes to HoloPlace are documented here.
Format based on [Keep a Changelog](https://keepachangelog.com/); versioning is [SemVer](https://semver.org/).

## [0.1.0] — 2026-09-08

First public release. Built for Minecraft 26.1.2 (Fabric, client-side). Full smoke test passed
in-game on a production instance.

### Added
- Load a `.litematic` and see it as a **textured translucent ghost** in the world — blocks,
  biome tint (grass / leaves / water), face shading, fluids (water / lava sources, waterlogged),
  real block-entity models (chests, signs, beds…), and entities (item frames, armour stands,
  paintings, mobs) — all fading together with one opacity slider.
- **Drag-to-position**: the ghost snaps to the block face under the crosshair; mouse wheel sets
  reach distance, `Shift`+wheel sets vertical offset. No coordinate typing, no nested menus.
- One screen (`K`) with two tabs — **Build** (load, position, rotate / mirror, opacity, see-through,
  build-assist, layer sliders) and **Create** (select an area, save it as a `.litematic`) — so the
  mod's two jobs are visible from the first open. Non-obvious controls carry tooltips; hovering a
  schematic in the list shows its size / block count / regions.
- Rotate (`R` / `Shift`+`R`) and mirror (`M`) about the footprint centre.
- **See-through / x-ray** toggle (`X`) — draw the ghost over walls (composited the same way as the
  normal ghost, so nothing draws over it).
- **Build-assist** (`H`) — hide blocks already placed correctly, show a progress %, outline
  wrongly-placed blocks in red and terrain clipping into the build in orange. A wrongly-placed
  cell drops its ghost model so the marker (and the "should be X" crosshair tooltip) reads clearly.
  If the schematic is mostly inside terrain, it says so and shows the outline to clear, rather than
  a wall of markers.
- **Material list** (`/holoplace materials`) — blocks needed and still missing.
- **Schematic capture** — select a two-corner area (`B`), then `/holoplace capture save <name>`
  or the `K`-screen row writes blocks + block entities + entities to a new `.litematic`.
- Status panels anchor to a screen corner of your choice ("HUD" button on the `K` screen cycles
  the four corners). The build panel and the capture panel share the corner and stack.
- Per-world placement persistence — where you leave a schematic is restored when you rejoin.
- OS drag-and-drop import (drop a `.litematic` on the window).
- Commands: `show` / `hide` / `clear` / `reset` / `move` / `nudge` / `layers` / `materials` /
  `capture` / `help`, with tab-completion.
- Full English and Spanish localization (HUD, tooltips, commands, `K` screen).
- First-join welcome message and `/holoplace help` with the full command + key list.

### Performance
- The ghost mesh is tesselated on a background thread. Loading or rotating a large schematic shows
  a footprint outline and a "preparing" note instead of freezing the game for a moment.
- The ghost's geometry is uploaded to the GPU once and redrawn each frame, instead of rebuilding
  every vertex on the CPU every frame. Large, detailed schematics no longer cost frame rate just by
  being visible.

### Security / robustness
- Bounded schematic loading: 256 MiB NBT accounter, per-axis (30 000) / per-region-volume
  (64 M cells) / region-count (4096) caps, and a `LitematicaBitArray` overflow guard — a corrupt
  or hostile `.litematic` fails with a clear error instead of freezing or OOM-ing the client.

### Notes
- Standalone: reads the open `.litematic` format directly, no dependency on Litematica. MIT.
- Not affiliated with Litematica or Mojang.
