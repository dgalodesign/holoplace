# HoloPlace

Modern UX for building from schematics in Minecraft (Fabric, client-side).

Load a `.litematic` file, see it as a **textured ghost overlay** in the world, and
position it **directly on screen with drag-to-position** — no nested menus, no
manual coordinate entry. Basic controls (opacity, rotation, mirror) stay visible
on a single panel.

Standalone: reads the open, documented `.litematic` format directly. No dependency
on Litematica.

## Status

Early development. See [`docs/plan.md`](docs/plan.md) for the full plan and
[`docs/progress.md`](docs/progress.md) for what's done.

| Milestone | Scope | Status |
|---|---|---|
| M0 | Project scaffolding, mod boots | ✅ |
| M1 | `.litematic` parser → in-memory model + unit tests | ✅ |
| M2 | Static textured ghost overlay render | ✅ |
| M3 | Drag-to-position (raycast anchor, wheel distance/height) | ✅ |
| M4 | Rotate / mirror / opacity + always-visible HUD panel | ✅ |
| M5 | Flat picker screen + OS drag-and-drop import + tab-complete | ✅ |
| M6 | Mesh cache — no per-frame re-tesselation | 🚧 written, needs in-game check |
| M7 | See-through / x-ray toggle (draw over walls) | ✅ |
| M8 | Build-assist — hide blocks already placed | ✅ |
| M9 | Material list (`/holoplace materials`) | ✅ |
| M10 | Per-world placement persistence (restored on rejoin) | ✅ |
| M11 | Biome tint (grass / leaves / water colour) | ✅ |
| M12 | Fluids (water / lava sources, waterlogged blocks) | 🚧 written, needs in-game check |

## Controls

| Input | Action |
|---|---|
| Drop a `.litematic` on the window | Import it and enter grab mode |
| `K` | Open the schematic picker |
| `/holoplace show <file>` | Load a `.litematic` and enter grab mode (tab-completes) |
| `/holoplace hide` · `/holoplace show` | Stop / resume drawing (placement is kept and still restores on rejoin) |
| `/holoplace clear` · `/holoplace reset` | Forget the placement · reset rotation & mirror |
| `/holoplace materials` | List blocks needed (and still missing) for the placed schematic |
| Look around (grab mode) | Position the ghost; snaps to the block face under the crosshair |
| Wheel · `Shift`+wheel (grab mode) | Reach distance · vertical offset |
| `G` | Toggle grab mode / lock |
| `R` · `Shift`+`R` | Rotate 90° CW · CCW |
| `M` | Cycle mirror (none → front-back → left-right) |
| `X` | Toggle see-through (draw the ghost over walls) |
| `H` | Toggle build-assist (hide blocks you've already placed) |
| `Alt`+wheel | Opacity ±5% |

Schematics are read from `config/holoplace/schematics/` and `<gamedir>/schematics/`. Where you
leave a placed schematic is remembered per world and restored when you rejoin.

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

MIT — see [LICENSE](LICENSE).
