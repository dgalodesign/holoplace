# Ficha de Modrinth — HoloPlace 0.1.0

Todo lo de aquí es para **pegar** en el formulario de Modrinth. No es documentación interna.

---

## Campos del proyecto

| Campo | Valor |
|---|---|
| **Name** | `HoloPlace` |
| **Slug / vanity URL** | `holoplace` → `modrinth.com/mod/holoplace` |
| **Summary** (una frase, ~150 car.) | `See any .litematic schematic as a textured hologram and build from it — drag to position, no dependencies.` |
| **Project type** | Mod |
| **Client side** | Required |
| **Server side** | Unsupported |
| **Categories** | Utility · Game Mechanics |
| **License** | MIT |
| **Links** | Source: `https://github.com/edgardgalof/holoplace` · Issues: `https://github.com/edgardgalof/holoplace/issues` |
| **Icon** | `docs/assets/icon-512.png` |
| **Environment** | Client |

> Slug: si `holoplace` ya está cogido, usar `holoplace-schematics`. Actualizar entonces
> `contact.homepage` en `fabric.mod.json` y volver a compilar.

---

## Descripción (markdown — pegar en "Description")

```markdown
# HoloPlace

**Load a `.litematic` schematic and see it as a textured, translucent hologram in the world — then build it by hand.** No coordinate typing, no nested config menus, no extra libraries.

![placeholder: a schematic shown as a textured ghost over a build site](https://REPLACE-with-screenshot-url)

## What it does

- **Textured ghost overlay.** Blocks with their real textures and biome tint (grass, leaves, water), face shading, fluids, and real block-entity models — chests, signs, beds, item frames, paintings — all fading together with a single opacity slider.
- **Drag to position.** The hologram snaps to the block face under your crosshair. Mouse wheel sets distance, `Shift`+wheel sets height. Press `G` to lock it in place.
- **Rotate and mirror** about the footprint centre, from the panel or with `R` / `M`.
- **See-through mode** (`X`) draws the ghost over walls you've already built.
- **Build-assist** (`H`): hides blocks you've already placed correctly and shows a live progress %. Wrong blocks get a red outline and a "should be: X" tooltip at your crosshair; blocks that don't belong get an orange one.
- **Layer sliders** to view the build one floor at a time.
- **Material list**: `/holoplace materials` shows what you need and what's still missing.
- **Create schematics from the world.** Select a two-corner area (`B`), name it, save — blocks, block entities and entities all go into a new `.litematic`.
- **Just drop a `.litematic` on the game window** to import it.
- **Per-world memory**: where you leave a schematic is where you find it when you rejoin.
- **English and Spanish**, fully — HUD, tooltips, commands, the whole screen.

## Quick start

1. Put your `.litematic` files in `.minecraft/config/holoplace/schematics/` (or just drag one onto the window).
2. Press `K`, pick a schematic.
3. Press `G` and look where you want it. Wheel = distance, `Shift`+wheel = height. `G` again to lock.
4. Press `H` while building to hide what you've already placed.

## Controls

| Key | Action |
|---|---|
| `K` | Open the HoloPlace screen |
| `G` | Grab / lock the hologram's position |
| `R` / `Shift`+`R` | Rotate 90° clockwise / counter-clockwise |
| `M` | Mirror (cycle) |
| `X` | See-through (draw over walls) |
| `H` | Build-assist (hide placed blocks) |
| `B` | Start a capture-area selection |
| `Alt`+wheel | Opacity (whenever a hologram is visible) |

All keys are rebindable. `/holoplace help` lists everything.

## Commands

`/holoplace` — `show <file>` · `hide` · `show` · `clear` · `reset` · `move <x y z>` · `nudge <dir> [n]` · `layers <min> <max>|off` · `materials` · `capture [pos1|pos2|clear|save <name>]` · `info <file>` · `list` · `help`

## Compatibility

- **Fabric only.** Requires [Fabric API](https://modrinth.com/mod/fabric-api).
- **Client-side.** Works on any server; nothing is installed server-side.
- Fine alongside **Sodium**.
- **Shaders (Iris):** the hologram may render differently or not at all with some shaderpacks — this is a known limitation, reports welcome.
- Reads the `.litematic` format directly. You do **not** need Litematica installed, and HoloPlace does not depend on it or share any code.

## What HoloPlace does *not* do

It never places blocks for you, and never touches your inventory or the world. You look at the hologram and build it yourself — so there's nothing for an anti-cheat to object to.

---

*HoloPlace is an independent project. It is not affiliated with, endorsed by, or connected to Litematica, MaLiLib, or Mojang. "Minecraft" is a trademark of Mojang Synergies AB.*

---

## En español

HoloPlace carga un esquema `.litematic` y lo muestra como un **holograma texturizado** en el mundo para que lo construyas a mano. Sin escribir coordenadas, sin menús anidados, sin librerías extra.

- Holograma con texturas reales, tinte de bioma, fluidos y modelos de cofres/carteles/marcos, todo con un único slider de opacidad.
- **Arrastrar para posicionar**: el holograma se pega a la cara del bloque bajo la mira (`G` para fijarlo).
- Rotar / espejo, ver a través de paredes (`X`), asistente de construcción (`H`) que oculta lo ya colocado y marca los errores.
- **Crear esquemas** seleccionando un área del mundo (`B`).
- Suelta un `.litematic` en la ventana para importarlo.
- Interfaz completa en español e inglés.

No coloca bloques por ti ni toca tu inventario.
```

---

## Changelog de la versión 0.1.0 (pegar al subir el archivo)

```markdown
First public release.

- Textured translucent hologram of any `.litematic`: blocks, biome tint, fluids, block-entity and entity models, one opacity slider.
- Drag-to-position (raycast from the crosshair), rotate / mirror about the centre, see-through mode.
- Build-assist: hide placed blocks, progress %, red/orange markers for wrong / extra blocks, "should be X" tooltip.
- Layer view, material list (`/holoplace materials`).
- Create a `.litematic` from a two-corner world selection (`B`) — blocks, block entities, entities.
- Drag-and-drop import, per-world placement memory, full English + Spanish.
- Ghost mesh baked on a background thread — large schematics don't freeze the game.
- Bounded schematic loading (size / volume / region caps) against corrupt or hostile files.

Client-side, Fabric, requires Fabric API. MC 26.1.2.
```

---

## Datos de la Version (al subir el jar)

| Campo | Valor |
|---|---|
| Version number | `0.1.0` |
| Version type | Release |
| Loaders | Fabric |
| Game versions | `26.1.2` |
| Dependencies | Fabric API — **Required** |
| Archivo | `build/libs/holoplace-0.1.0.jar` (**no** el `-sources.jar`) |

---

## Pendiente antes de publicar

- [ ] Sustituir las URLs `REPLACE-with-screenshot-url` por capturas reales (o quitar las líneas de
      imagen y subir las capturas en la pestaña **Gallery** de Modrinth).
- [ ] Confirmar el slug y, si cambió, actualizar `fabric.mod.json` → `contact.homepage`.
- [ ] Smoke test pasado (`smoke-test-0.1.0.md`).
