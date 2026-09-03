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
| M3 | Drag-to-position (raycast anchor, wheel distance/height) | 🚧 written, needs in-game check |
| M4 | HUD controls: opacity slider, rotate ±90°, mirror | |
| M5 | In-game file picker + OS drag-and-drop import; Sodium pass | |

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
