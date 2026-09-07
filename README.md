# HoloPlace

Modern UX for building from schematics in Minecraft (Fabric, client-side).

Load a `.litematic` file, see it as a **textured ghost overlay** in the world, and
position it **directly on screen with drag-to-position** — no nested menus, no
manual coordinate entry. Basic controls (opacity, rotation, mirror) stay visible
on a single panel.

Standalone: reads the open, documented `.litematic` format directly. No dependency
on Litematica.

## Status

MVP complete and verified in-game (Minecraft 26.1.2). See [`docs/plan.md`](docs/plan.md)
for the plan and [`docs/progress.md`](docs/progress.md) for the milestone history.

| Milestone | Scope | Status |
|---|---|---|
| M0 | Project scaffolding, mod boots | ✅ |
| M1 | `.litematic` parser → in-memory model + unit tests | ✅ |
| M2 | Static textured ghost overlay render | ✅ |
| M3 | Drag-to-position (raycast anchor, wheel distance/height) | ✅ |
| M4 | Rotate / mirror / opacity + always-visible HUD panel | ✅ |
| M5 | Flat picker screen + OS drag-and-drop import + tab-complete | ✅ |
| M6 | Mesh cache — no per-frame re-tesselation | ✅ |
| M7 | See-through / x-ray toggle (draw over walls) | ✅ |
| M8 | Build-assist — hide blocks already placed + progress % | ✅ |
| M9 | Material list (`/holoplace materials`) — total & still missing | ✅ |
| M10 | Per-world placement persistence; hide ≠ clear | ✅ |
| M11 | Biome tint (grass / leaves / water colour) | ✅ |
| M12 | Fluids (water / lava sources, waterlogged blocks) | ✅ |
| M13 | Block-entity wire markers (fallback) | ✅ |
| M14 | One controls screen (opacity slider + toggles + list) · block-only match | ✅ |
| M15 | Perf hardening (throttled build-assist scan, tint freeze on drag, size guard) | ✅ |
| M16 | Real block-entity models (chests, signs, beds…), opacity-aware | ✅ |
| M17 | help/welcome · rotate-around-centre · move/nudge · layer sliders · wrong-block red · face shading · paged list | ✅ |
| M18 | Spanish translation · extra-block marker (orange) · look-at tooltip with item icon | ✅ |

Not done: GPU vertex-buffer upload (a perf win for very large schematics — the current
per-frame vertex submit is capped at 4M quads). See [`docs/backlog.md`](docs/backlog.md).

## Controls

| Input | Action |
|---|---|
| Drop a `.litematic` on the window | Import it and enter grab mode |
| `K` | Open the HoloPlace screen (list + all display controls) |
| `/holoplace show <file>` | Load a `.litematic` and enter grab mode (tab-completes) |
| `/holoplace hide` · `/holoplace show` | Stop / resume drawing (placement is kept and still restores on rejoin) |
| `/holoplace clear` · `/holoplace reset` | Forget the placement · reset rotation & mirror |
| `/holoplace move <x y z>` · `/holoplace nudge <dir> [n]` | Place at exact coords · shift by n blocks |
| `/holoplace layers <min> [max]` · `/holoplace layers off` | Show only a Y-slice of the ghost |
| `/holoplace materials` · `/holoplace help` | Blocks needed / still missing · full command + key list |
| Look around (grab mode) | Position the ghost; snaps to the block face under the crosshair |
| Wheel · `Shift`+wheel (grab mode) | Reach distance · vertical offset |
| `G` | Toggle grab mode / lock |
| `R` · `Shift`+`R` | Rotate 90° CW · CCW |
| `M` | Cycle mirror (none → front-back → left-right) |
| `X` | Toggle see-through (draw the ghost over walls) |
| `H` | Toggle build-assist (hide blocks you've already placed) |
| `Alt`+wheel | Opacity ±5% |

The `K` screen has the opacity slider, every toggle (with its key), X/Y/Z fields, and
two "layer" sliders to view the schematic one floor at a time. With build-assist on,
blocks you've placed wrong show a red outline, and blocks that don't belong to the
build at all (world has a block, schematic wants air there) show an orange outline.
Looking at either while build-assist is on shows a tooltip near the crosshair with the
game's own item icon for the correct block. "Hide wrong too" (off by default) also hides
the full ghost model for wrongly-placed blocks, leaving just the red outline — less to render.

Schematics are read from `config/holoplace/schematics/` and `<gamedir>/schematics/`. Where you
leave a placed schematic is remembered per world and restored when you rejoin.

Fully localized in English and Spanish (`en_us` / `es_es`) — HUD, tooltip, commands, and the
`K` screen all follow Minecraft's language setting.

## Target

- Minecraft **26.1.2** — the first unobfuscated release (Mojang names, no Yarn/mappings step)
- Java **25**
- Fabric Loader 0.19.5 · Fabric API 0.155.2+26.1.2 · Loom 1.17.20 · Gradle 9.5.1

## Building

Requires a JDK 25. Minecraft's bundled `java-runtime-epsilon` (Microsoft Store /
official launcher) is a full JDK 25 and works.

```bash
./gradlew build
```

```bash
./gradlew runClient
```

## License

MIT — see [LICENSE](LICENSE). Standalone implementation of the open `.litematic` format; not
affiliated with Litematica (LGPL-3.0) or Mojang. See [`docs/licensing.md`](docs/licensing.md).
