# Auditoría de código — HoloPlace (2026-09-09, pre-0.1.0)

~6.900 LOC Java. `src/main` (1.046) = parser/escritor `.litematic` + lógica pura; `src/client` (5.159)
= render, UI, comandos, captura; `src/test` (658) = 6 suites sobre la capa pura.

## Veredicto

**Listo para publicar.** Código limpio y bien organizado: separación clara main/client, manejo de
errores consistente (log + feedback al jugador en todos lados), sin `TODO`/`printStackTrace`/`System.out`,
buen *hardening* del lector (topes de tamaño/volumen/regiones, `NbtAccounter` acotado, guard de
overflow en el bit array), y degradación elegante en todas las rutas frágiles (devuelven `null` +
fallback en vez de crashear).

## Corregido en esta pasada (commit de la auditoría)

| # | Archivo | Problema | Severidad |
|---|---|---|---|
| 1 | `GhostMeshBaker.poll()` | `CompletableFuture.getNow()` **relanza** si el future terminó con excepción → un bake fallido salía del evento de render en vez de caer al contorno. Ahora comprueba `isCompletedExceptionally()` primero. | media (latente) |
| 2 | `SchematicMeta.peek()` | `get`-luego-`put`: dos hovers seguidos podían lanzar dos lecturas de fondo del mismo archivo. → `computeIfAbsent`. | baja |
| 3 | `GhostGpuMesh.build()` | El `ByteBufferBuilder` empezaba en 4 MB y hacía realloc+copy varias veces para un schematic grande. Ahora se dimensiona a `totalQuads()` de una. | baja (perf) |

## Observaciones (no bloquean 0.1.0 — candidatas a 0.1.x / 0.2.0)

### Render

- **`GhostGpuMesh` sin re-sort translúcido por frame.** Usa el index buffer secuencial compartido,
  así que caras translúcidas del fantasma que se solapan pueden dibujarse ligeramente fuera de
  orden. Menor a la opacidad típica del fantasma. Follow-up: re-sort en un `ByteBufferBuilder` de
  índices cuando la cámara cruza un umbral (como hace el terreno translúcido de vanilla).
- **`scanExtraBlocks` recorre el volumen completo del footprint (hasta 2M celdas) en el hilo de
  render cada 250 ms** cuando el asistente (`H`) está activo — `view.getBlockState` + `level.getBlockState`
  por celda. Para `estatua-thor` (516k celdas) es un tirón periódico. Candidato: moverlo al worker
  de `GhostMeshBaker`, o bajar `EXTRA_SCAN_VOLUME_LIMIT`, o escanear solo la cáscara del footprint.
- **`bakeGeometry` lee modelos horneados fuera del hilo de render.** Seguro salvo durante un
  *resource reload* (F3+T) concurrente — probabilidad baja, y el fallo se loguea + se reintenta.
  Aceptable; documentado en `holoplace-render-gotchas`.
- **`GhostRenderer` (759 LOC)** hace render + escaneo + marcadores + submit + fluidos. Cohesivo
  pero grande; se podría extraer `GhostBuildAssistScan` y `GhostMarkers`.
- **`RenderTypeTextures`** usa reflexión sobre campos package-private de `RenderSetup` para la
  máquina de fade de BE (M23). Bien guardada (cachea, `reflectionFailed`, devuelve `null`), pero
  se romperá silenciosamente en una versión futura de MC → los modelos de cofres/carteles saldrían
  a opacidad plena en vez de fundirse. No crashea.

### Formato / IO

- **`LitematicaSchematicWriter.write` no es atómico** — `NbtIo.writeCompressed` directo al archivo
  final. Un crash a mitad de guardado deja un `.litematic` corrupto. Fix: escribir a `.tmp` +
  `Files.move(ATOMIC_MOVE)`.
- **`LitematicaSchematicWriter.Region`** no valida `blocks.length == volume()` — un desajuste da
  `AIOOBE` en `LitematicaBitArray.set`. `CaptureWriter` lo construye consistente; un `assert` o
  `IllegalArgumentException` sería más defensivo.

### Tests

- Sin cobertura para `SchematicMeta` (lectura de solo-cabecera), `WorldPlacements` (serialización
  Gson), y los casos límite de `PlacementTransform.inverse` (round-trip `forward`→`inverse`).
- La capa de render y UI no es testeable sin cliente — cubierta por `docs/smoke-test-0.1.0.md`.

### Menor

- Dos `Executors.newSingleThreadExecutor` daemon (`SchematicMeta`, `GhostMeshBaker`) nunca se
  cierran explícitamente. Correcto para hilos daemon; el proceso al salir los recoge.
- `GhostMeshBaker.invalidate()` no cancela el `CompletableFuture` en vuelo — el resultado se
  descarta pero el worker termina el trabajo. CPU malgastada, sin fuga.
- `HoloPlaceScreen.init()` se auto-invoca vía `rebuildWidgets()` en el clamp de scroll — recursión
  acotada a 1 nivel (el segundo pase ya tiene `scrollY` clampeado). Correcto pero sutil.

## Fortalezas a mantener

- **Standalone real**: parser propio, cero dependencia de Litematica.
- **Todo lo pesado fuera del hilo de render**: horneado de malla (M26), lectura de metadatos, y
  ahora la geometría en GPU (0.1.0).
- **Fallbacks en todas partes**: `GhostGpuMesh` → `renderBlocksImmediate`; reflexión de texturas →
  `null`; bloque desconocido en la paleta → AIR + aviso; bake fallido → contorno.
- **Config vs. persistencia por-mundo separadas** (`HoloPlaceConfig` / `WorldPlacements`), ambas
  tolerantes a archivos corruptos/ausentes.
