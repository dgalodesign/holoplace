# Post-launch — cómo detectar y triar problemas

HoloPlace es client-side y **no tiene telemetría** (ni la va a tener). El único canal de señal son
los reportes de la gente + los logs que traen. Todo este plan gira en torno a que un reporte llegue
**accionable a la primera** y a saber de antemano **qué es lo más probable que se rompa**.

---

## 1. Registro de riesgos

Ordenado por (probabilidad × radio de impacto). "Señal" = cómo lo reconocerás en un reporte/log.

| # | Fallo | Prob. | Impacto | Señal | Mitigación actual |
|---|---|---|---|---|---|
| R1 | **Shaders (Iris) rompen el see-through / x-ray** | alta | medio (feature, no crash) | "el fantasma no se ve a través de paredes con shaders"; log `Missing program` (ya no aparece) o simplemente el ghost ocluido | Documentado como limitación. Pipelines custom eliminados → sin spam de errores |
| R2 | **Actualización de MC** rompe la capa de render | alta (cada ~2-3 meses) | alto (todo el render) | crash al cargar / `NoSuchMethodError` / ghost invisible | Rama `port/mc-26.2` con recon hecha; `depends.minecraft` fijado exacto |
| R3 | **Conflicto de scroll** con otro mod (zoom, otro asistente) | media | bajo-medio | "el zoom no funciona" / "el scroll se lo come HoloPlace" | `MouseHandlerMixin` solo cancela si `handleScroll` devuelve true (grab activo o Alt) — ya conservador |
| R4 | **`GhostGpuMesh` (RenderPass manual) falla** en un driver/GPU concreto | media | medio (cae al fallback lento pero funciona) | log `Ghost GPU render failed — falling back`; FPS bajos en esquemas grandes | Fallback automático `renderBlocksImmediate` + latch `gpuUnavailable` |
| R5 | **`.litematic` raro** (versión vieja de Litematica, multi-región exótico, waterlogged masivo) | media | bajo (error claro al jugador) | `Failed to read` / bloques invisibles / geometría mal | Reader acotado (topes de eje/volumen/región), throws `IOException` legible, bloques desconocidos → AIRE + aviso |
| R6 | **Otro mod de rendering** (Sodium extra, moreculling, etc.) interfiere con `LevelRenderEvents` | media | medio | ghost parpadea / desaparece a distancia / z-fighting | Sodium validado; el resto no. `AFTER_TRANSLUCENT_TERRAIN` es un hook estándar |
| R7 | **Fuga / crash en cambio de mundo o dimensión** | baja | alto (crash) | crash al cambiar de dimensión con ghost activo | `WorldPlacements.onDisconnect` → `GhostRenderer.invalidate()` (cierra GPU buffer, tira mesh) |
| R8 | **Resource reload (F3+T)** con un bake en vuelo | baja | bajo | ghost con texturas mal una vez / log `Ghost mesh bake failed` | `whenComplete` loguea, `poll()` reintenta; no crashea |
| R9 | **Modelos de BE/entidades** de un mod de contenido no se construyen | baja | bajo | `Ghost block entities: N of M submitted` con M>N; cofres/carteles como caja de alambre | Best-effort, cae a marcador de alambre |
| R10 | **`RenderTypeTextures` (reflexión)** se rompe en una versión futura | baja | bajo | modelos de cofres/carteles a opacidad plena (no se funden) | Guardado, cachea, devuelve `null` sin crashear |

**Regla de oro para triar:** R1, R6 → "¿pasa sin shaders / sin ese mod?". R2 → versión de MC. R4 → buscar
`falling back` en el log. R5 → pedir el `.litematic`.

---

## 2. Hacer que los reportes sean accionables

### 2.1 Comando `/holoplace debug`  *(a implementar antes de lanzar — 30 min)*

Vuelca un bloque que la persona pega tal cual en el issue:

```
HoloPlace 0.1.0 · MC 26.1.2 · Fabric Loader 0.19.5 · Fabric API 0.155.2+26.1.2
Java 25 · OS Windows 11 · GPU: NVIDIA … (driver …)
Sodium: sí (0.6.x) · Iris: sí, shaders ACTIVOS (Complementary…) · Litematica: sí
Ghost: 'estatua-thor' 64×126×64 · 23 574 bloques · 1 región · GPU path: OK (o: fallback)
Mesh baker: idle · Build-assist: on · See-through: on
Mods que tocan input/render: <lista filtrada>
```

Fuentes: `FabricLoader.getAllMods()`, `Minecraft`/`Window`, `GhostState`, `GhostRenderer` (exponer
`gpuUnavailable()` y stats del mesh), `RenderSystem.getDevice()` para GPU. Filtrar la lista de mods a
categorías relevantes (rendering, input, sodium/iris/litematica y derivados).

### 2.2 Convenciones de log

- **ERROR** solo para cosas que degradan una feature de forma visible (`Ghost GPU render failed`,
  `Failed to read <file>`). Nunca por frame.
- **WARN** para límites alcanzados (`Schematic too large`).
- **INFO** para hitos de sesión (`HoloPlace ready`, `Ghost mesh: N BE cells`, `Restored <placement>`).
  Revisar que no haya nada INFO por-frame.
- **DEBUG** para diagnóstico fino (`Ghost geometry baked: N quads in M ms`, submit fails).
- Todos los mensajes con prefijo identificable (`HoloPlace`) — ya lo hace el `LOGGER`.

### 2.3 Enriquecer el crash report

`fabric-crash-report-info-v1` ya está de dependencia transitiva. Añadir una sección al crash report
con lo mismo que `/holoplace debug` (versión, ghost cargado, GPU path). Así un crash trae contexto
aunque la persona no sepa sacar el log.

### 2.4 Plantillas de issue en GitHub  *(a crear antes de lanzar)*

`.github/ISSUE_TEMPLATE/bug_report.yml` con campos **obligatorios**:
- Versión de HoloPlace + salida de `/holoplace debug`
- `latest.log` completo (adjunto, no pegado) o `crash-reports/…`
- Pasos para reproducir + qué esperabas
- **¿Pasa con solo HoloPlace + Fabric API + Sodium?** (sí/no/no probado)
- **¿Pasa sin shaders?** (sí/no/no aplica)
- El `.litematic` si es específico de un archivo

Y `config.yml` que desactive issues en blanco y enlace a Modrinth para "cómo se usa".

---

## 3. Flujo de triaje

**Dónde llegan los reportes:** GitHub Issues (canal principal), pestaña de Modrinth (revisar 1×/semana),
comentarios de Modrinth. Redirigir todo lo de Modrinth a un issue.

**Labels:** `crash` · `rendering` · `compat:<mod>` · `shaders` · `perf` · `.litematic` · `mc-version` ·
`needs-info` · `wontfix` · `upstream` (bug de otro mod).

**Severidad → respuesta:**
- **S1 crash / arranque roto** → fix o `wontfix` documentado en < 1 semana; sacar 0.1.x.
- **S2 feature rota para muchos** (p. ej. build-assist no marca nada) → siguiente 0.1.x.
- **S3 caso de nicho / un mod concreto** → backlog, agrupar.
- **S4 cosmético / shaders** → `known-issue`, agrupar en `KNOWN_ISSUES.md`.

**Checklist "¿es culpa nuestra?":** pedir repro en instancia mínima (HoloPlace + Fabric API + Sodium,
sin shaders). Si ahí no pasa → `compat:` + pedir binario del conflicto. Si pasa → es nuestro.

**Expectativas públicas** (README + Modrinth): proyecto de una persona, hobby. "Reporto en GitHub,
respondo cuando puedo, los crashes van primero. No hay soporte por DM."

---

## 4. Detección proactiva

- **Smoke test cada release** — `docs/smoke-test-0.1.0.md` (renombrar a `smoke-test.md`, versionar).
- **Matriz de compat mínima cada release**: instancia con Sodium + Iris(shaders) + Litematica + un
  mod de zoom con scroll. 15 min. Anotar resultado en el CHANGELOG de la versión.
- **Watch de versiones de MC**: `depends.minecraft` **nunca** con wildcard amplio. Cuando salga una
  MC nueva: NO se soporta hasta que el smoke test pase en ella. Mantener `port/mc-26.2` (y futuras)
  vivas para no partir de cero.
- **CI en GitHub Actions** *(opcional, barato)*: `./gradlew build` en cada push a `main` y en PRs.
  No prueba render pero pilla que compile + los 18 tests. ~20 líneas de yaml.
- **NO** telemetría / beacon de errores — privacidad + alcance. Los reportes son la señal, por eso §2.

---

## 5. Limitaciones conocidas — publicarlas

`KNOWN_ISSUES.md` en el repo + sección en la ficha de Modrinth, actualizado cada release:

- **See-through / x-ray con shaders (Iris)**: soporte limitado — Iris gestiona sus propios
  framebuffers y el fantasma puede quedar ocluido. El fantasma normal sí funciona con shaders.
- **Esquemas de 4M+ quads**: no se renderizan (tope de seguridad).
- **Solo Fabric.** No Forge/NeoForge, no Quilt (sin probar).
- (Lo que salga del smoke test / primeros reportes.)

---

## 6. Higiene de releases

- **SemVer**: 0.1.x = fixes; 0.2.0 = port a 26.2 + features de backlog; API rota entre esquemas
  guardados → bump de minor y nota de migración.
- **CHANGELOG.md**: sección por versión con fecha; el bloque de la versión va también al subir a Modrinth.
- **Git tags + GitHub Releases**: `git tag v0.1.0` + release con el jar adjunto. Da un punto de
  rollback y un historial legible.
- **Rollback**: no hay nada server-side; la persona elige una versión anterior en Modrinth. Mantener
  las versiones viejas publicadas (no borrarlas).
- **Rama de trabajo**: features en rama, `main` siempre compilando y con el smoke test pasado.

---

## 7. Qué métricas SÍ tienes

- **Modrinth**: descargas totales/diarias, adopción por versión (si el 80% sigue en 0.1.0 tras
  sacar 0.1.1, algo del 0.1.1 espanta), game-versions que la gente usa.
- **GitHub**: issues abiertos/cerrados, estrellas (proxy de interés), tráfico del repo.
- Nada más. Sin errores agregados, sin "cuántos tienen shaders". De ahí que §2.1 (`/holoplace debug`)
  sea la inversión de mayor retorno antes de lanzar.

---

## Acciones antes de lanzar (derivadas de este plan)

- [x] `/holoplace debug` (§2.1) — `dev.holoplace.Diagnostics`, comando `debug`
- [x] Sección en el crash report (§2.3) — `MinecraftCrashReportMixin` (config opcional, `required: false`)
- [x] `.github/ISSUE_TEMPLATE/bug_report.yml` + `feature_request.yml` + `config.yml` (§2.4)
- [x] `KNOWN_ISSUES.md` (§5) + sección en la ficha de Modrinth
- [x] CI en Actions (§4) — `.github/workflows/build.yml` (`gradlew build` + tests en push/PR, sube el jar)
- [ ] `git tag v0.1.0` + GitHub Release al publicar (§6)
- [x] Expectativas de soporte en el README (§3) — sección "Support"
