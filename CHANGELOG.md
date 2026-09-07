# Changelog

All notable changes to HoloPlace are documented here.
Format based on [Keep a Changelog](https://keepachangelog.com/); versioning is [SemVer](https://semver.org/).

## [Unreleased]

Work in progress toward the first public release (`0.1.0`). Everything below is built and
verified in-game on Minecraft 26.1.2 unless noted.

### Added
- Load a `.litematic` and see it as a **textured translucent ghost** in the world — blocks,
  biome tint (grass / leaves / water), face shading, fluids (water / lava sources, waterlogged),
  real block-entity models (chests, signs, beds…), and entities (item frames, armour stands,
  paintings, mobs) — all fading together with one opacity slider.
- **Drag-to-position**: the ghost snaps to the block face under the crosshair; mouse wheel sets
  reach distance, `Shift`+wheel sets vertical offset. No coordinate typing, no nested menus.
- One controls screen (`K`): opacity, rotate / mirror, show / hide, exact X/Y/Z, and two sliders
  to view the schematic one floor at a time.
- Rotate (`R` / `Shift`+`R`) and mirror (`M`) about the footprint centre.
- **See-through / x-ray** toggle (`X`) — draw the ghost over walls.
- **Build-assist** (`H`) — hide blocks already placed correctly, show a progress %, outline
  wrongly-placed blocks in red and blocks that don't belong at all in orange. Looking at either
  shows a tooltip with the game's own item icon for the correct block. Optional "hide wrong too".
- **Material list** (`/holoplace materials`) — blocks needed and still missing.
- **Schematic capture** — select a two-corner area (`B`), then `/holoplace capture save <name>`
  or the `K`-screen row writes blocks + block entities + entities to a new `.litematic`.
- Per-world placement persistence — where you leave a schematic is restored when you rejoin.
- OS drag-and-drop import (drop a `.litematic` on the window).
- Commands: `show` / `hide` / `clear` / `reset` / `move` / `nudge` / `layers` / `materials` /
  `capture` / `help`, with tab-completion.
- Full English and Spanish localization (HUD, tooltips, commands, `K` screen).
- First-join welcome message and `/holoplace help` with the full command + key list.

### Security / robustness
- Bounded schematic loading: 256 MiB NBT accounter, per-axis (30 000) / per-region-volume
  (64 M cells) / region-count (4096) caps, and a `LitematicaBitArray` overflow guard — a corrupt
  or hostile `.litematic` fails with a clear error instead of freezing or OOM-ing the client.

### Notes
- Standalone: reads the open `.litematic` format directly, no dependency on Litematica. MIT.
- Not affiliated with Litematica or Mojang.
