# Plan — captura de schematics (crear `.litematic` desde el mundo)

## Contexto

La auditoría de publicación (2026-09-05) marcó "capturar el mundo → schematic" como el hueco de
funciones más grande frente a Litematica: hoy HoloPlace solo **lee** `.litematic`, nunca los crea.
Este plan cierra ese hueco, continuando la numeración de milestones desde [`docs/progress.md`](progress.md)
(M18 fue el último).

**Diferenciador pedido por el usuario**: Litematica captura el área seleccionada tal cual está ahora
mismo — todo lo que haya dentro de la caja, sin importar si el jugador lo puso o ya estaba ahí.
HoloPlace debe poder capturar **solo lo que cambió desde que el jugador empezó a construir**, además
de (no en vez de) la captura completa al estilo Litematica.

## Decisión de diseño: por qué "revisar al guardar, sin haber rastreado nada" no es posible

El usuario propuso una tercera variante, todavía más simple en apariencia: construir primero,
seleccionar el área recién al final (cuando ya se sabe el tamaño real), y que al guardar el propio
mod distinga qué bloques puso el jugador y cuáles ya estaban generados por el mundo — **sin** ningún
paso de grabación de por medio.

Eso no es posible, y vale la pena dejar constancia del porqué: **Minecraft no guarda en ningún lado
quién puso un bloque**. Un bloque de piedra colocado a mano y uno generado por el terreno son
idénticos bit a bit — mismo `BlockState`, mismo NBT, nada que los distinga después del hecho. La única
forma real de saber "esto lo generó el mundo" es regenerar el chunk desde la seed y comparar (la
técnica detrás de `//regen` de WorldEdit) — inviable acá: requiere conocer la seed exacta, reimplementar
la generación de mundo de esa versión de Minecraft, y en multijugador el cliente ni siquiera tiene
acceso a la seed del servidor. Con cero información previa, no hay heurística confiable — no es un
límite de esta implementación, es un límite de qué datos existen.

### Diseño corregido — seguimiento pasivo, selección al final

La solución no es eliminar el rastreo, sino **correrlo hacia atrás en el tiempo**: en vez de que el
jugador tenga que acordarse de apretar "empezar a grabar" antes de construir, el mod empieza a mirar
solo, en cuanto entra a un mundo, sin que nadie tenga que activarlo:

1. Desde que HoloPlace carga un mundo, queda escuchando **en segundo plano** cada cambio de estado de
   bloque que ocurra en los chunks que ya están cargados (colocar, romper, reemplazar, pistón, agua,
   crecimiento — lo mismo que se consideraba antes) y va guardando el conjunto de posiciones tocadas.
   No hay ningún botón de "empezar": simplemente, si jugás con el mod puesto, ya está mirando.
2. El jugador construye lo que quiera, sin pensar en el mod para nada.
3. Cuando termina, usa la **misma** herramienta de selección de dos esquinas del modo completo —
   ahora que puede ver el tamaño real de lo que hizo, en vez de tener que adivinarlo antes de empezar.
4. Al guardar, elige entre:
   - **Completo**: todo lo que hay dentro de la caja, tal cual está ahora (Litematica).
   - **Automático**: de las celdas dentro de la caja, solo las que aparecen en el registro de
     cambios se exportan con su estado actual; el resto sale como aire.

Como la caja la dibuja el jugador recién al final, alrededor de lo que ya construyó, el "vigilar todo
lo que pasa en los chunks cargados" deja de ser un problema — cualquier cambio ajeno que haya ocurrido
en otra parte del chunk (otro jugador, un pistón de otra cosa) simplemente queda afuera de la caja que
el jugador dibuja alrededor de *su* construcción. No hace falta ningún radio de vigilancia ajustable
ni ningún paso de "empezar": la selección posterior ya cumple ese filtro.

**Límite honesto que hay que decir con todas las letras**: esto solo reconoce cambios ocurridos
*mientras HoloPlace estaba corriendo y el chunk estaba cargado*. Una construcción hecha antes de tener
el mod instalado, en una sesión anterior si el registro no sobrevive al reinicio (ver M23), o en un
chunk que nunca llegó a cargarse del lado de este cliente, no tiene historial — el modo automático la
va a tratar como "no es mía" y la va a dejar afuera. Para esos casos, el modo completo sigue estando.

Las dos modalidades conviven, elegibles por el jugador **al guardar**, no al empezar:

| Modo | Cuándo se usa | Qué exporta |
|---|---|---|
| **Completo** (como Litematica) | Ya existe algo construido (o de una sesión sin historial) y quiero capturarlo tal cual | Todo lo que hay dentro de las dos esquinas elegidas |
| **Automático** (nuevo, el diferenciador) | Construí algo nuevo en esta sesión y quiero solo eso | De la caja elegida, solo las celdas con historial de cambio |

## Arquitectura nueva

```
main/  capture/
  SelectionState.java      · pos1/pos2 (mundo), normalizados a min/max; tamaño/volumen — lógica pura
                             en el source set main para poder testearla; un solo mecanismo de
                             selección, usado por los dos modos                      [M19 ✅]
  ChangeLog.java           · registro de posiciones con historial de cambio (LongOpenHashSet o
                             similar), alimentado por el mixin, consultado recién al guardar   [M21]
  CaptureSnapshot.java     · BlockState[] + BlockEntity NBT por celda, relativo a la esquina mínima [M20]
client/ capture/
  CaptureController.java   · singleton; alterna "modo selección"; fija esquinas (clic o /cmd);
                             guardar (completo/automático) [M20/M21]; cancelar             [M19 ✅]
  SelectionRenderer.java   · caja de alambre de la selección — reusa ShapeRenderer + RenderTypes.lines(),
                             igual que los marcadores de bloque incorrecto/sobrante       [M19 ✅]
  CaptureHud.java          · panel arriba a la derecha: esquinas, tamaño, volumen, aviso de tamaño
                             grande; el selector completo/automático se suma en M21       [M19 ✅]
client/ schematic/
  LitematicaSchematicWriter.java · CaptureSnapshot → CompoundTag → .litematic (gzip)          [M20]
client/ mixin/
  LevelBlockChangeMixin.java · hook al método de bajo nivel que aplica un cambio de estado de bloque;
                                siempre activo mientras hay un mundo cargado (ver más abajo), solo
                                agrega una posición a ChangeLog — sin costo de render, sin UI propia [M21]
```

Selección M19: keybind `B` (`CAPTURE_SELECT`) alterna el modo; clic izq/der sobre un bloque fija la
esquina 1 / 2 vía `AttackBlockCallback` / `UseBlockCallback` (consumidos solo mientras el modo está
activo); `/holoplace capture [pos1|pos2|clear]` como alternativa por comando.

Todo nuevo, sin tocar el camino de lectura/render existente — la captura es un subsistema paralelo
que solo comparte utilidades (`ShapeRenderer`, `LitematicaBitArray`, la carpeta de schematics). El
seguimiento pasivo (`ChangeLog`) y la selección de área son independientes entre sí: uno corre todo el
tiempo en segundo plano, la otra se activa solo cuando el jugador quiere guardar algo.

### Selección de área (un solo mecanismo, para ambos modos)

- Un keybind activa "modo selección" (mismo espíritu que el modo *grab* de `PlacementController`,
  pero para marcar dos esquinas en vez de mover un fantasma).
- Mientras está activo: clic izquierdo sobre un bloque fija la esquina 1, clic derecho fija la
  esquina 2 — vía `AttackBlockCallback` / `UseBlockCallback` de Fabric API, **no un mixin nuevo**
  (la auditoría ya señaló que cada mixin extra es una fuente de conflictos con otros mods; estos
  eventos de Fabric API están pensados exactamente para esto y solo cancelan el clic real cuando el
  modo selección está activo).
- Caja de alambre siempre visible entre las dos esquinas mientras se define/ajusta.
- HUD con esquinas, tamaño X×Y×Z, volumen, y una advertencia si el volumen pasa de un límite (mismo
  espíritu que `GhostRenderer.MAX_QUADS` / `EXTRA_SCAN_VOLUME_LIMIT`, con un tope propio — a definir
  en M19, probablemente unos pocos millones de celdas).
- Al guardar, un selector simple (checkbox/comando) elige **completo** o **automático** — la selección
  en sí no cambia entre los dos modos, solo cómo se decide qué exportar dentro de ella.

### Modo automático — mecánica exacta

1. En cuanto el mod tiene un mundo cargado, `LevelBlockChangeMixin` está activo: cada cambio de
   estado de bloque en un chunk ya cargado agrega esa posición a `ChangeLog`. No hay ningún paso de
   "empezar" — corre solo, siempre, sin HUD propio ni indicador (es deliberadamente invisible: el
   costo es agregar una posición a un set, nada de render ni de I/O).
2. El jugador construye lo que quiera, cuando quiera, sin acordarse del mod para nada.
3. Cuando termina, dibuja la selección de dos esquinas alrededor de lo que construyó (ya con el
   tamaño real a la vista) y guarda en modo **automático**.
4. Para cada celda dentro de la caja: si está en `ChangeLog` → su estado actual; si no → aire.

**Cómo se detecta "cambió un bloque"**: hookear el método de bajo nivel por el que tanto la
colocación/rotura predicha localmente por el cliente como los paquetes de actualización del servidor
terminan pasando (en versiones recientes de MC, algo como `Level#setBlock` /
`ClientLevel#setBlockAndUpdate` — **nombre y firma exactos a verificar contra el código de 26.1**,
mismo tipo de investigación que ya hizo falta para `LevelRenderEvents` y el resto del pipeline nuevo).
Ese método es el punto de unión de *todas* las formas en que un bloque cambia (colocarlo, romperlo,
un pistón, agua/lava, crecimiento, otro jugador) — un solo hook para todo, sin combinar varios eventos
de interacción ni perder cambios indirectos (un pistón empujando algo forma parte de lo que el
jugador construyó tanto como el bloque puesto a mano).

**Por qué ya no hace falta un radio de vigilancia**: como la caja la dibuja el jugador recién al
final, alrededor de lo que ya construyó, cualquier cambio ajeno que haya quedado registrado en otra
parte de un chunk cargado (otro jugador, un pistón de otra cosa) simplemente cae fuera de esa caja —
la selección posterior ya cumple el rol de filtro que antes necesitaba un radio ajustable aparte.

**Límite de v1, dicho sin rodeos**: `ChangeLog` vive en memoria mientras dura la sesión de juego. Un
cierre del cliente lo pierde — una construcción de varias sesiones necesita, para v1, terminarse y
guardarse antes de cerrar el juego, o usar modo completo. Persistirlo en disco entre reinicios es
exactamente lo que cubre M23.

En modo **completo** no hay nada de esto: `capture save` simplemente lee la selección tal cual está
ahora (paridad con Litematica).

### Escritor `.litematic`

Espejo del lector que ya existe (`LitematicaSchematicReader`), en el sentido inverso:

- Paleta: `BlockState`s únicos encontrados en la snapshot a exportar (índice 0 reservado a aire, igual
  que hace el lector al rellenar paletas vacías).
- Bit-packing: `LitematicaBitArray` **ya tiene** un método `set(index, value)` implementado y sin usar
  todavía — se escribió en su momento por simetría con `get()`; ahora por fin tiene un consumidor real.
- `TileEntities`: por cada block entity dentro de la región, guardar su NBT en el mismo formato que el
  lector espera de vuelta (litematica omite `id`/`x`/`y`/`z` vanilla y guarda `x`/`y`/`z`
  *relativos a la región* aparte) — hay que verificar la firma exacta de guardado de `BlockEntity` en
  26.1 (`saveWithFullMetadata`/`saveCustomOnly` o el equivalente post-refactor de `TagValueOutput`;
  esto es simétrico a `TagValueInput` que ya se usó al leer, ver [[holoplace-render-gotchas]]).
- `Metadata`: `Name`, `Author` (nombre del jugador que captura, por defecto), `Description` (vacío o
  editable), `EnclosingSize`, `TimeCreated`. `Version` en el mismo rango 4–7 que ya lee
  `LitematicaSchematicReader` (para poder hacer *round-trip*: escribir y releer con el propio lector).
- Salida: `NbtIo.writeCompressed` (gzip) a `config/holoplace/schematics/<nombre>.litematic` — la misma
  carpeta que ya usa la biblioteca, así el schematic recién creado aparece de inmediato en el picker.

### Reutilización del hallazgo de la auditoría

El escritor comparte el mismo riesgo que ya se documentó para el lector: no reservar memoria a partir
de un volumen sin tope. Tanto la selección de dos esquinas como el volumen de vigilancia de la
grabación los define el propio jugador (no un archivo de un desconocido), así que el riesgo es menor,
pero igual conviene un límite explícito con aviso en vez de dejar que una caja enorme cuelgue el
cliente.

## Milestones

- **M19 — Herramienta de selección** ✅ (compilado, `build` verde, sin probar en el juego todavía):
  `CaptureController` (modo selección, keybind `B` + `/holoplace capture`), `SelectionRenderer` (caja
  de alambre), `CaptureHud` (panel arriba a la derecha con esquinas/tamaño/volumen y aviso si supera
  ~5M celdas). Sin escritura a disco todavía. Sirve para los dos modos por igual. 5 tests unitarios
  nuevos en `SelectionStateTest` (normalización de esquinas sin importar el orden de clic, tamaño
  inclusivo, volumen en `long`) — 23 tests en total.
- **M20 — Escritor `.litematic` + captura completa** ✅ (compilado, `build` verde, 26 tests, sin
  probar en el juego todavía): `LitematicaSchematicWriter` (en `src/main`, formato v6, paleta con
  aire en índice 0, bit-packing por `LitematicaBitArray.set`), `CaptureWriter` (lee la región del
  mundo del cliente, block entities vía `saveCustomOnly` + x/y/z relativos), `/holoplace capture save
  [nombre]` (sin nombre → `capture-AAAAMMDD-HHMMSS`), tope de 8M celdas. 3 tests de *round-trip*
  (`LitematicaSchematicWriterTest`): grilla con bloques con estado → releer con
  `LitematicaSchematicReader` → comparar celda a celda; selección todo-aire; NBT de block entity por
  posición local. Falta: entidades (marcos, soportes de armadura…) — se guardan vacías por ahora.
- **M21 — Modo automático (el diferenciador)** ✅ (compilado, `build` verde, 30 tests, sin probar en
  el juego todavía): `ChangeLog` (en `src/main`, `LongOpenHashSet` con tope de 3M) + `ChangeTracker`
  (cliente, singleton, se limpia en join/disconnect) + `LevelBlockChangeMixin` (`@Inject` HEAD en
  `Level.setBlock(BlockPos, BlockState, int, int)` — el punto por el que pasan tanto la predicción
  local como los paquetes del servidor; la carga masiva de un chunk NO pasa por ahí, así que se
  filtra sola; el guard `isClientSide()` descarta el servidor integrado en un jugador). `CaptureWriter`
  toma un `@Nullable ChangeLog`: si viene, solo las celdas registradas conservan su estado, el resto
  sale aire. `/holoplace capture save changes [nombre]` para el modo automático (sin `changes` sigue
  siendo completo). HUD muestra "N cambios registrados". 4 tests de `ChangeLog` (registrar/consultar
  por coords, dedupe, clear, tope). **Pendiente de verificar en el juego**: que el hook realmente no
  dispare en la carga inicial de un chunk (si dispara, hay que distinguir "chunk recién cargado" de
  "cambio real").
- **M22 — Pulido + i18n**: strings nuevas en `en_us.json`/`es_es.json` siguiendo el patrón ya
  establecido; integrar controles de captura en la pantalla `K` o una pantalla propia si no entra sin
  amontonar; actualizar `docs/progress.md` y `README.md`.
- **M23 (recomendado, no solo "nice to have")**: persistir `ChangeLog` en disco por mundo. Con el modo
  automático corriendo siempre en segundo plano, una construcción real puede durar varias sesiones de
  juego repartidas en días — sin esto, cualquier cierre del cliente a mitad de camino tira el
  historial y obliga a terminar todo de una sentada o caer a modo completo.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Selección enorme cuelga el cliente al capturar | Límite explícito antes de reservar memoria (M19/M20), con aviso — mismo espíritu que `MAX_QUADS` |
| API exacta del hook de cambio de bloque en 26.1 incierta | Primer paso de M21: confirmarla contra el código fuente de 26.1, igual que se hizo para `LevelRenderEvents` y el resto del pipeline nuevo |
| El hook elegido también dispara durante la carga masiva de un chunk (falsos positivos) | Verificar empíricamente al implementar; si hace falta, distinguir explícitamente "chunk recién cargado" de "cambio real en un chunk ya conocido" |
| `ChangeLog` crece sin límite en una sesión muy larga | Tope de posiciones rastreadas con aviso (M21); es un `long`/posición por entrada, footprint modesto incluso en sesiones largas |
| El registro pasivo también anota cambios ajenos al jugador (otro jugador, un pistón, un mob) en un chunk cargado | Sin impacto real: la selección se dibuja al final alrededor de la construcción propia, así que lo ajeno normalmente queda fuera de la caja; documentado como límite conocido para el caso en que no sea así |
| Construcción de varios días de juego pierde el historial si se cierra el cliente | M23 (persistencia en disco); mientras tanto, avisar con claridad en vez de fallar en silencio, y modo completo como salida de emergencia |
