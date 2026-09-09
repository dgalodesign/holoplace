# HoloPlace

[![build](https://github.com/dgalodesign/holoplace/actions/workflows/build.yml/badge.svg)](https://github.com/dgalodesign/holoplace/actions/workflows/build.yml)

Modern UX for building from schematics in Minecraft (Fabric, client-side).

Load a `.litematic` file, see it as a **textured ghost overlay** in the world (blocks,
fluids, block-entity models, and entities like item frames and armour stands), and
position it **directly on screen with drag-to-position** — no nested menus, no
manual coordinate entry. Basic controls (opacity, rotation, mirror) stay visible
on a single panel.

Standalone: reads the open, documented `.litematic` format directly. No dependency
on Litematica.

## Status

**0.1.0 — first public release, on Minecraft 26.1.2.** Built and verified in-game. Docs index:
[`docs/README.md`](docs/README.md) — plan, milestone history, capture sub-project, Litematica
config reference, code audit, post-launch plan, licensing, backlog. Known limitations:
[`KNOWN_ISSUES.md`](KNOWN_ISSUES.md).

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
| M19–M22 | Schematic capture — area select · `.litematic` writer · blocks + block entities + entities · `K`-screen row | ✅ |
| M23 | Render schematic entities in the ghost (item frames, armour stands, paintings…) | ✅ |
| M24 | UI/GUI pass — two tabs (Build / Create), sectioned panel, tooltips, list metadata on hover, collapsed HUD | ✅ |
| M26 | Off-thread mesh bake + persistent GPU buffer (large schematics don't hitch or cost frame rate) | ✅ |
| 0.1.0 polish | `/holoplace debug`, crash-report section, corner-anchored HUD, visibility-culled build-assist markers | ✅ |

Deferred to 0.2.0: port to MC 26.2 (branch `port/mc-26.2`), hover 3D thumbnail, Iris-aware
see-through. See [`docs/backlog.md`](docs/backlog.md) and [`docs/launch-checklist.md`](docs/launch-checklist.md).

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
| `/holoplace debug` | Print an environment + state block for a bug report (also in crash reports) |
| `B`, then left/right-click two blocks | Select a capture area (also on the `K` screen) |
| `/holoplace capture save <name>` | Save the selected area as a new `.litematic` |
| Look around (grab mode) | Position the ghost; snaps to the block face under the crosshair |
| Wheel · `Shift`+wheel (grab mode) | Reach distance · vertical offset |
| `G` | Toggle grab mode / lock |
| `R` · `Shift`+`R` | Rotate 90° CW · CCW |
| `M` | Cycle mirror (none → front-back → left-right) |
| `X` | Toggle see-through (draw the ghost over walls) |
| `H` | Toggle build-assist (hide blocks you've already placed) |
| `Alt`+wheel | Opacity ±5% |

The `K` screen has the opacity slider, rotate / mirror, the toggles (with their keys), a
schematic list with size/block-count on hover, and two "layer" sliders to view the build one
floor at a time. Advanced has "ignore block orientation", a details toggle, marker opacity, and
which screen corner the status panel anchors to. With build-assist on, a wrongly-placed block
drops its ghost model and shows a red marker (with a "should be X" tooltip and item icon at the
crosshair); terrain clipping into the build shows an orange marker. Markers only appear on
surfaces you can actually see and reach — the HUD shows `visible/total`, e.g. `30/500 wrong`.

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

## Support

HoloPlace is a one-person project, worked on in spare time. What that means in practice:

- **Bugs go in [GitHub issues](https://github.com/dgalodesign/holoplace/issues)**, using the
  template. Run `/holoplace debug` and attach your `latest.log`. Check
  [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) first.
- **No support over DMs, Discord, or Modrinth comments** — those get redirected to an issue.
- **Crashes and "won't start" come first.** Broken features are next. Nice-to-haves and
  single-mod compat quirks get batched.
- **Response time is "when I can"**, not an SLA. A clear, reproducible report with the debug
  block and a minimal-instance check gets looked at fastest.
- **Minecraft version support:** only versions where the full smoke test has passed. A new
  Minecraft release is *not* supported until then — `depends.minecraft` is pinned deliberately.
  0.1.x fixes target 26.1.2; 26.2 lands in 0.2.0.
- **Shaders:** the normal ghost works with Iris; see-through/x-ray is limited (see KNOWN_ISSUES).

Feature ideas are welcome, but HoloPlace stays small and deliberately does **not** place blocks,
touch your inventory, or run on the server.

## License

MIT — see [LICENSE](LICENSE). Standalone implementation of the open `.litematic` format; not
affiliated with Litematica (LGPL-3.0) or Mojang. See [`docs/licensing.md`](docs/licensing.md).
