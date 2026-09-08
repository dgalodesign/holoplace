# HoloPlace — plan del primer lanzamiento (0.1.0)

Estado a 2026-09-08. Decisiones tomadas con el usuario:

- **Alcance**: 0.1.0 mete mejoras de UX (rediseño UI, rendimiento) para que el mod sea **relevante**
  de salida. El paquete "asistencia solo-lectura" (M25) pasó a backlog el 2026-09-08 — el usuario
  quiere cero fricción con anticheats.
- **Easy Place descartado (2026-09-08)** — "1 clic = coloca el bloque por ti" tiene fricción
  histórica con anticheats. HoloPlace NO coloca bloques; se queda en "mira y construye a mano".
- **Versión de MC**: **0.1.0 sale en 26.1.2** (decisión 2026-09-08 — 26.2 reescribió el pipeline de
  render y el port es riesgo de regresión en la ruta al lanzamiento). Port a 26.2 = 0.2.0 fast-follow.
- **UI/GUI primero**: pase de rediseño de la pantalla `K` / HUD antes de las features nuevas.
- **Repo**: público, open source MIT (ver §1).
- Los hallazgos de código de la auditoría (topes de tamaño, `NbtAccounter`, overflow guard) están
  **hechos**. `CHANGELOG.md` creado con sección `[Unreleased]`.

---

## 0. Secuencia de trabajo (milestones)

| # | Milestone | Contenido | Esfuerzo |
|---|---|---|---|
| ~~M24~~ | Pase de UI/GUI | Pantalla `K` en dos pestañas (Construir/Crear) + secciones, rotar/espejo en el panel, tooltips, lista con scroll + metadatos al hover, HUD colapsado, celda-mal-colocada sin fantasma. **CERRADO — verificado in-game 2026-09-08** | medio |
| ~~M25 (Easy Place)~~ | ~~Easy Place~~ | **Descartado** — riesgo anticheat. HoloPlace no coloca bloques | — |
| ~~M25 (asistencia)~~ | Paquete "asistencia" (solo lectura) | Info de bloque al mirar + "N mal colocados" en `/materials`. **A BACKLOG (2026-09-08)** — el usuario no quiere ninguna fricción con anticheats; fuera de 0.1.0. Ver §6 | bajo |
| ~~M26~~ | Malla off-thread | `GhostMesh.bakeGeometry` en un worker daemon; mientras hornea se dibuja el contorno del footprint + "preparando el modelo…" en el HUD. **CERRADO — verificado in-game 2026-09-08** | medio |
| ~~M28~~ | Pre-lanzamiento | `fabric.mod.json` ✅ · **smoke test PASADO 2026-09-08** (jar real, Modrinth App). CHANGELOG → `[0.1.0]` | bajo |
| **—** | **Lanzamiento 0.1.0 en MC 26.1.2** | Repo GitHub público + push · ficha Modrinth (`docs/modrinth-listing.md`) + subir jar/ícono/screenshots (§4) | bajo |
| ~~M26.5~~ | Thumbnail al hover → **0.2.0** | **Aplazado (decisión 2026-09-08).** En 26.1 no hay estado PIP para "structure" — el 3D vivo pediría `RenderTarget` + pipeline propios a mano en la capa de render frágil, justo antes del lanzamiento. La lista se queda con el tooltip de metadatos. Se hace (2D isométrico o 3D) junto al port a 26.2 | pequeño-medio |
| ~~M27~~ | Port a 26.2 → **0.2.0** | **Aplazado tras el lanzamiento (decisión 2026-09-08).** 26.2 reescribió el pipeline de render de nivel (submit-node, sin `ShapeRenderer`/`bufferSource`). Recon + arreglos mecánicos hechos en rama `port/mc-26.2`; ver `docs/port-26.2-notes.md` | medio |

---

## 1. Repo público — por qué (y alternativa)

**No es técnicamente obligatorio.** Modrinth/CurseForge no exigen código fuente. Pero:

- `fabric.mod.json` declara `contact.sources = github.com/edgardgalof/holoplace`. Si no existe o es
  privado, es un enlace roto en Mod Menu → mala señal.
- Declarar licencia **MIT** con el repo cerrado es incoherente (MIT solo tiene efecto sobre quien
  recibe el código). Si va a ser closed-source, la licencia debería ser "all rights reserved".
- Para un mod nuevo que compite con Litematica (que es open source LGPL-3.0), **ser open source MIT
  es un argumento de venta**: "liviano, sin dependencias, y abierto". También da confianza
  (la gente ve que no hay nada raro en el jar).

**Decisión**: público, MIT. Pasos: crear el repo en GitHub, `git remote add origin …`,
`git push -u origin main`. Confirmar que es público y trae `LICENSE` + `README` + `docs/`.

Si se cambiara de idea → licencia "all rights reserved" en `fabric.mod.json`, quitar
`contact.sources`, y deja de ser bloqueante.

---

## 2. Ícono — HECHO (2026-09-08)

Martillo holográfico pixelado (glow cian, mango dorado, líneas de escaneo, fondo azul oscuro).
Lo hizo el usuario. En repo:
- `docs/assets/icon-512.png` (512×512 RGBA) — para las fichas de Modrinth / CurseForge.
- `docs/assets/icon.png` (128×128, recorte más cerrado a la cabeza) = copia exacta de
  `src/main/resources/assets/holoplace/icon.png`, el que usa el juego / Mod Menu.

Al publicar: subir el 512 a la ficha. (El 128 aguanta a ~32 px en las listas; el 512 es el que luce.)

---

## 3. Pre-lanzamiento — detalle de cada check (M28)

### 3.1 Smoke test completo + actualizar `progress.md`
**Checklist runnable: `docs/smoke-test-0.1.0.md`** — cada feature una vez, con el jar real.
**Por qué**: `progress.md` dice "verificado" en casi todo, pero fue verificación incremental
durante el desarrollo, no un pase completo con la build 0.1.0. Los bugs de integración
(una feature rompió otra) se escapan así. Nada visual es testeable con los 18 unit tests.
**Salida**: marcar en `progress.md` qué pasó el pase y qué no.

### 3.2 Compatibilidad con shaders (Iris)
Sodium + Iris + un shaderpack (Complementary / BSL), shaders activos, comprobar que el ghost
sigue viéndose: translúcido, con color de bioma, sin z-fighting agresivo, sin desaparecer.
**Por qué**: el ghost usa render types y un `RenderPipeline` propio (`GhostPipelines`). Iris
reemplaza los shaders del juego y puede ignorar o romper pipelines custom — es la incompatibilidad
más probable y la más reportada (Litematica la ha sufrido).
**Si rompe**: no bloquea el lanzamiento; se documenta "shaders: soporte limitado" y se abre issue.

### 3.3 Compatibilidad con otros mods
Instalar 2–3 mods populares a la vez (Litematica misma, un mod de zoom con scroll, WorldEdit CUI)
y jugar un rato.
**Por qué específico**: el único mixin invasivo es `MouseHandlerMixin` — intercepta el scroll con
`cancellable=true` en `HEAD`. Cualquier otro mod que también capture la rueda (zoom, otro asistente)
es un conflicto potencial de "quién consume el evento". Sodium ya se validó; el resto no.

### 3.4 Metadata de `fabric.mod.json` — HECHO (2026-09-08)
- ✅ `contact.homepage` (`modrinth.com/mod/holoplace` — **verificar tras crear el proyecto**) +
  `contact.issues` (GitHub issues).
- ✅ `authors`: `"Edgar D' Galo"` (nombre visible).
- ✅ `depends.minecraft`: `~26.1.2` (antes `~26.1` — ahora exacto a lo probado).
- ✅ `description` revisada — cabe.
- ⏭️ Entrypoint `modmenu`: **omitido en 0.1.0**. Añadiría `com.terraformersmc:modmenu` como
  `compileOnly` (poco, pero el "cero dependencias" es argumento de venta y la K abre con tecla).
  Retomable en 0.2.0.

### 3.5 Build limpio + probar el jar real
`./gradlew clean build` → coger `build/libs/holoplace-0.1.0.jar` (**no** el `-sources.jar`) →
instalarlo en una instancia de **producción** (launcher oficial o Prism con Fabric + Fabric API),
no en el `runClient` de desarrollo.
**Por qué**: `runClient` usa el classpath de dev sin remapear y con los sources cargados. El jar
real pasa por el `remapJar` de Loom y corre en otro entorno. Cosas que compilan y corren en dev
pueden fallar empaquetadas (refmap de mixins, recursos no incluidos, etc.).

---

## 4. Publicar en Modrinth — paso a paso

1. Cuenta en modrinth.com (GitHub o email).
2. **Create a project** → tipo **Mod**, loader **Fabric**, entorno **Client** (server: unsupported).
3. Slug `holoplace`, nombre `HoloPlace`, summary de una frase.
4. Descripción (markdown): la larga — features, cómo se usa, **disclaimer "no afiliado con
   Litematica ni Mojang"**, screenshots, posicionamiento honesto ("alternativa liviana sin
   dependencias", no "reemplazo de Litematica").
5. Licencia: MIT. Categorías: Utility, Game Mechanics. Links: source + issues.
6. Subir el ícono 512×512.
7. El proyecto entra en **cola de moderación de Modrinth** (< 48 h normalmente); no es público
   hasta que lo aprueban.
8. Crear una **Version**: subir `holoplace-0.1.0.jar`, número `0.1.0`, changelog (de
   `CHANGELOG.md`), MC `26.1.2`, loader Fabric, **dependencia Fabric API (required)**, canal
   **Release** (o Beta).
9. Publicar.

**Opcional**: el plugin Gradle `com.modrinth.minotaur` publica con `./gradlew modrinth` + un token
API — vale la pena si vas a sacar varias versiones.

CurseForge: proceso parecido pero moderación más lenta y tediosa — dejarlo para después de 0.1.0.

---

## 5. Ya resuelto (no repetir)

Topes de tamaño en `LitematicaSchematicReader` (ejes / volumen / nº de regiones), `NbtAccounter`
acotado a 256 MiB, guard de overflow en `LitematicaBitArray`, comentarios que nombraban internals
de Litematica reescritos, `LICENSE` MIT consistente en jar + `fabric.mod.json`, i18n ES/EN completo,
`docs/licensing.md`, `CHANGELOG.md` con `[Unreleased]`.

---

## 6. Backlog (fuera de 0.1.0)

**Decisión del usuario (2026-09-08): cero fricción con anticheats.** Todo lo que toque inventario,
hotbar o mundo se aparca, aunque el riesgo sea bajo. HoloPlace 0.1.0 es estrictamente "mira el
fantasma y construye a mano".

- **M25 — asistencia solo-lectura**: info del bloque bajo la mira (nombre + orientación esperada) y
  un contador "N mal colocados" en `/holoplace materials`. No toca nada del jugador, pero se aparca
  con el resto para no dispersar el alcance de 0.1.0. Retomable cuando el mod ya esté publicado.
- **Pick-block del esquema**: la rueda / una tecla saca el bloque correcto al hotbar (creativo) o lo
  selecciona (survival) — no coloca nada. Tecla dedicada sin bind por defecto, solo el bloque bajo
  la mira. Riesgo anticheat bajo pero toca el hotbar → aparcado.
