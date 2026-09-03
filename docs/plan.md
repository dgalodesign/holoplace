# Plan MVP — UX moderna para schematics `.litematic` (mod Fabric standalone)

## Contexto

Litematica es el estándar para previsualizar y construir desde schematics en Minecraft Java, pero su
interacción es confusa: menús anidados, entrada manual de coordenadas, nada de arrastrar y soltar.
SchematicPreview cubre el preview 3D en GUI pero no toca la interacción in-world. **El hueco real es la
interacción**: elegir y posicionar un schematic directamente en pantalla, sin submenús ni coordenadas a mano.

Este plan cubre un **mod Fabric standalone y autocontenido** (decisión confirmada con el usuario) que:
lee `.litematic` directamente, lo dibuja como overlay fantasma texturizado en el mundo, y lo posiciona
con *drag-to-position* (raycast desde la mira) más controles siempre visibles de opacidad, rotación y espejo.

No se depende del código de Litematica (evita el acoplamiento a su estructura interna y el área del aviso
de seguridad — un *directory traversal* en `schematics/transmit/`, corregido en 0.26.11, ajeno al core de
parsing/render). La rotación/espejo de bloques la resuelve vanilla con `BlockState.rotate()/.mirror()`.

## Decisiones confirmadas

| Tema | Decisión |
|---|---|
| Target | **Minecraft 26.1.2** (data version 4790, Java 25), Fabric Loader 0.19.3 |
| Arquitectura | **Standalone autocontenido** — parser propio + renderer ligero propio, cero dependencia de Litematica |
| Mappings | **Mojang oficiales (Mojmaps)** vía `loom.officialMojangMappings()` |
| Fidelidad render MVP | **Bloques con textura, translúcidos** (modelos de bloque reales, estilo ghost) |
| Lado | 100% cliente (no server-side) |

## Hallazgos técnicos clave para 26.1

- **26.1 reescribió el pipeline de render** (el cambio de API más grande en un release). Es la **última
  versión solo-OpenGL**; 26.2 introduce Vulkan conmutable → mantener la superficie de `RenderPipeline`
  custom mínima y aislada.
- `WorldRenderEvents` → renombrado a **`LevelRenderEvents`** (`LevelRenderContext`). Disponible en Fabric API 26.1.
  Modelo nuevo: fase *extraction* (`LevelExtractionEvents.END_EXTRACTION`) + fase *draw*
  (`LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN`, etc.).
- `RenderType`/`RenderLayer` de terreno → **`ChunkSectionLayer`**.
- **Render de modelos de bloque disponible** en core MC (`BlockRenderDispatcher`) con asignación
  automática de capa según el sprite. **No requiere** la Fabric Renderer API (Indigo).
- **Riesgo**: Fabric **Renderer/Indigo y Model Loading API pueden no estar listas** en el release inicial
  de 26.1. Mitigación: usar solo core MC (`BlockRenderDispatcher`), no FRAPI.
- Toolchain: **Loom 1.15, Gradle 9.4.0, Java 25**, IntelliJ 2025.3+ (mixins).
- **Referencia viva**: `sakura-ryoko/litematica` ya portado a 26.1.2/26.2 — consultar su port del
  pipeline de render, el `LitematicaBitArray` y el parseo de formato (no copiar; referencia de API).

## Arquitectura del mod

```
Carga archivo → Parser → SchematicData (inmutable)
                              │
                     SchematicBlockView  (implements BlockAndTintGetter)  ← adaptador de solo-lectura
                              │
                     SectionMeshBuilder → VBO por sección 16³ (cache, rebuild solo al cambiar transform)
                              │
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN → dibuja VBOs con pipeline translúcido (α ajustable)
```

Componentes:

1. **Parser** (`parse/`): `NbtIo.readCompressed` (GZIP NBT nativo) → recorre `Regions`. Implementa
   `LitematicaBitArray` (variante con entradas que **cruzan** el límite de `long`, distinta del
   `BitStorage` no-straddling de MC 26.1). Normaliza `Size` negativos. Sin librerías externas de NBT.
2. **Modelo de datos** (`schematic/`): `SchematicData` = lista de regiones; cada región con paleta
   `BlockState[]`, índices decodificados, block entities (`CompoundTag`), entities. Inmutable.
3. **Vista de render** (`schematic/SchematicBlockView`): implementa `BlockAndTintGetter`
   (`getBlockState`, `getFluidState`, `getBlockEntity`, `getBrightness` → full-bright o muestreo simple,
   `getBlockTint` → colores de bioma del mundo real en el ancla, `getShade`). Evita construir un `Level`
   falso completo (enfoque mucho más ligero que el de Litematica).
4. **Renderer** (`render/`): `SectionMeshBuilder` usa `BlockRenderDispatcher.renderBatched(...)` sobre
   `SchematicBlockView` hacia un `BufferBuilder` por `ChunkSectionLayer`; empaqueta `MeshData` en un
   `VertexBuffer` (VBO) por sección. `GhostRenderer` dibuja los VBOs cada frame con un `RenderPipeline`
   derivado del translúcido vanilla: alpha uniforme configurable + variante "ver a través de bloques"
   (depth test off). Rebuild de una sección **solo** cuando cambia el transform o se marca sucia;
   *debounce* durante el arrastre (solo bounding box mientras se mueve, calidad completa al soltar).
5. **Placement** (`placement/`): `Placement` = ancla `BlockPos` + `Rotation` + `Mirror` + offset.
   `PlacementController` hace raycast desde la cámara (`Minecraft.player.pick` / `clipInclusiveAABB`),
   snap del ancla a la cara del bloque apuntado, rueda del mouse = distancia, Shift+rueda = altura.
   Al construir la malla aplica `state.rotate(rot).mirror(mir)` y transforma las coords locales.
6. **UI** (`ui/`): `GhostHud` — panel único siempre visible (sin submenús): nombre del schematic,
   coords del ancla (campo editable = caso "avanzado", no requerido), slider de opacidad, indicador y
   botones de rotación (±90°) y espejo, botón reset. `SchematicPickerScreen` — lista plana de
   `.litematic` en `config/litematica-ux/schematics/` (+ `.minecraft/schematics/`), un clic para cargar.
7. **Import por drag & drop del SO**: `GLFW.glfwSetDropCallback` sobre la ventana (o mixin a
   `Window`); un `.litematic` soltado en la ventana se copia a la carpeta y se carga.
8. **Config** (`config/`): JSON simple (opacidad, ver-a-través, última colocación, ruta de carpeta).

## Alcance del MVP

**Incluye:**
- Cargar un `.litematic` (multi-región: itera todas las regiones) → overlay fantasma texturizado translúcido.
- Renderiza **todos** los bloques del schematic (no solo el diff con el mundo).
- Toggle "ver a través de bloques".
- Drag-to-position: modo grab (keybind), ancla por raycast con snap a cara, rueda = distancia,
  Shift+rueda = altura, clic/Enter = fijar, Esc = cancelar. **Reemplaza el menú de coordenadas.**
- Controles visibles: slider de opacidad, rotar ±90°, ciclo de espejo.
- Persistencia de preferencias.
- **Persistencia de la colocación por mundo**: al cerrar y volver a entrar a un mundo/servidor,
  el schematic colocado (archivo + ancla + rotación + espejo) se restaura en el mismo sitio.
  Clave por carpeta de mundo (singleplayer) o IP (multijugador); se guarda en
  `config/holoplace/placements.json`. Se limpia al ocultar el fantasma. *(implementado en M10)*
- Compatibilidad verificada con Sodium (setup del usuario).

**Fuera del MVP (anotado como siguiente iteración):**
- Multi-schematic / layering.
- Integración con WorldEdit.
- Historial de versiones.
- Lista de materiales / verificación de progreso de construcción.
- Modo build-assist (resaltar el siguiente bloque a colocar, diff con el mundo).
- Render de block entities complejos (cofres, carteles con texto) — el MVP dibuja el modelo base del bloque.
- Pegado/colocación real de bloques en creativo.

## Diseño de interacción (núcleo del proyecto)

| Acción | Input | Resultado |
|---|---|---|
| Abrir selector de schematic | keybind (p.ej. `K`) | `SchematicPickerScreen`, lista plana, 1 clic carga |
| Importar desde el SO | arrastrar `.litematic` a la ventana | copia + carga automática |
| Entrar/salir modo grab | keybind (p.ej. `G`) | el ghost sigue la mira |
| Posicionar | mover la mira | ancla snapea a la cara del bloque apuntado |
| Distancia del ghost | rueda del mouse | acerca/aleja el ancla |
| Altura del ghost | Shift + rueda | sube/baja el ancla |
| Fijar colocación | clic izq. / Enter | bloquea; sale de modo grab |
| Cancelar | Esc | vuelve a la colocación previa |
| Rotar | `R` / `Shift+R` (o botones del HUD) | `Rotation` ±90°, rebuild |
| Espejo | `M` (o botón del HUD) | ciclo NONE / FRONT_BACK / LEFT_RIGHT |
| Opacidad | slider del HUD | alpha del pipeline, sin rebuild |
| Coords exactas (avanzado) | campo editable del HUD | set directo del ancla |

Principio: **una sola superficie visible**, cero menús anidados; las coordenadas son opcionales, no la vía principal.

## Plan de implementación por milestones

- **M0 — Scaffolding**: Gradle+Loom 1.15, Java 25, `fabric.mod.json`, `ClientModInitializer`,
  Fabric API (rendering, keybindings, lifecycle, resource-loader, screen). `runClient` arranca; keybind loguea.
- **M1 — Parser**: `.litematic` → `SchematicData`. `LitematicaBitArray` con tests unitarios (contra un
  `.litematic` de muestra: dims, `TotalBlocks`, tamaño de paleta esperados). Comando `/schematic-info` que loguea el resumen.
- **M2 — Overlay estático**: ancla fija en la posición del jugador. `SchematicBlockView` +
  `SectionMeshBuilder` + cache de VBOs + `GhostRenderer` en `LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN`.
  Pipeline translúcido con alpha fijo. Toggle ver-a-través. Validar con schematic de 1 y de varias regiones.
- **M3 — Drag-to-position**: `PlacementController` (raycast + snap), rueda distancia/altura, fijar/cancelar,
  persistencia en config. El ghost se mueve sin rebuild (solo re-translación de la matriz) salvo cambio de rotación.
- **M4 — Controles**: `GhostHud` con slider de opacidad + rotar ±90° + espejo + reset + campo de coords.
  Aplicar `Rotation`/`Mirror` en el mesh builder (`state.rotate().mirror()` + transformación de coords locales).
  *Debounce* de rebuild durante interacción.
- **M5 — UX de carga + pulido**: `SchematicPickerScreen`, import por drag&drop del SO, prueba con Sodium
  (sin crash, sin z-fighting), medición de frame-time (rebuild vs. estado estable) en un schematic ~50³.

## Estructura de archivos (nueva — el directorio está vacío)

```
build.gradle, settings.gradle, gradle.properties      · Loom 1.15 / Gradle 9.4 / Java 25 / Mojmaps
src/main/resources/fabric.mod.json, *.mixins.json, assets/<modid>/lang, icon
src/main/java/<pkg>/
  LitematicaUxClient.java            · ClientModInitializer: keybinds, eventos, config
  parse/LitematicReader.java         · NbtIo.readCompressed → SchematicData
  parse/LitematicaBitArray.java      · desempaque bit-packed (straddling)
  schematic/SchematicData.java       · modelo inmutable (regiones, paleta, índices, BEs)
  schematic/SchematicRegion.java
  schematic/SchematicBlockView.java  · implements BlockAndTintGetter
  render/SectionMeshBuilder.java     · BlockRenderDispatcher → MeshData por ChunkSectionLayer
  render/GhostRenderer.java          · cache VBO + draw en LevelRenderEvents
  render/GhostPipelines.java         · RenderPipeline translúcido + variante see-through (aislado)
  placement/Placement.java           · ancla + Rotation + Mirror + offset
  placement/PlacementController.java · raycast, snap, rueda, fijar/cancelar
  ui/GhostHud.java                   · HUD panel único
  ui/SchematicPickerScreen.java      · lista plana de archivos
  io/OsFileDropHandler.java          · GLFW drop callback
  config/ModConfig.java              · JSON
src/test/java/<pkg>/                 · tests del parser + bit array
```

## Dependencias y toolchain

- **Sin librerías de NBT externas** — `net.minecraft.nbt.NbtIo` / `CompoundTag` / `NbtAccounter`.
- Fabric API módulos: `fabric-rendering-v1`, `fabric-key-binding-api-v1`, `fabric-lifecycle-events-v1`,
  `fabric-resource-loader-v0`, `fabric-screen-api-v1`, `fabric-command-api-v2`.
- Rotación/espejo: `net.minecraft.world.level.block.Rotation` / `Mirror`, `BlockState#rotate/#mirror` (vanilla).
- Render: `BlockRenderDispatcher` (`Minecraft.getInstance().getBlockRenderer()`), `ChunkSectionLayer`,
  `com.mojang.blaze3d.vertex.*` (`PoseStack`, `BufferBuilder`, `MeshData`, `VertexBuffer`), `RenderPipeline`/`RenderPipelines`.
- **Verificar firmas exactas contra el source de `sakura-ryoko/litematica` @ 26.1.2** (el pipeline 26.1 es nuevo y la doc es escasa).

## Bit array `.litematic` (implementación crítica)

```
bitsPerEntry = max(2, ceil(log2(paletteSize)))        // NO 4 mínimo como MC; 2 mínimo
maxValue     = (1L << bitsPerEntry) - 1
get(i):
  bitIndex   = i * bitsPerEntry
  startLong  = bitIndex >> 6
  endLong    = ((i + 1) * bitsPerEntry - 1) >> 6
  startOffset= bitIndex & 63
  if (startLong == endLong)
      return (longs[startLong] >>> startOffset) & maxValue
  else
      end = 64 - startOffset
      return ((longs[startLong] >>> startOffset) | (longs[endLong] << end)) & maxValue
```

Índice de bloque en la región: `index = (y * abs(sizeX) * abs(sizeZ)) + (z * abs(sizeX)) + x`.
`Size` puede ser negativo → normalizar a esquina mínima + dimensiones absolutas.
Referencia cruzada: `litemapy` (Python) y `SkytAsul/Lite2Edit` (Java) para el formato exacto.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Fabric Renderer/Model Loading API no lista en 26.1 | Usar solo `BlockRenderDispatcher` core; no depender de FRAPI |
| Pipeline de render 26.1 nuevo, doc escasa | Referencia `sakura-ryoko/litematica`; superficie custom mínima |
| 26.2 rompe el `RenderPipeline` custom (Vulkan) | Aislar todo el GL en `GhostPipelines`; derivar de pipelines vanilla |
| Rebuild de malla causa hitching | Cache por sección; rebuild solo al cambiar rotación/mirror; debounce en drag; bounding box mientras se arrastra |
| Sodium reemplaza el render de terreno | Dibujar en `LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN` (post-terreno); test explícito con Sodium en M5 |
| Iluminación/AO en la vista falsa | `getBrightness` full-bright o muestreo simple del mundo; aceptable para ghost |
| Schematics enormes | Límite configurable de volumen para el MVP; aviso si se supera |

## Verificación

1. **Unit tests** (`./gradlew test`): parser + `LitematicaBitArray` contra un `.litematic` de muestra
   (crear uno con Litematica o descargar uno pequeño). Aserciones: dimensiones, `TotalBlocks`,
   contenido de paleta, bloques concretos en coords conocidas, y un archivo multi-región.
2. **`./gradlew runClient`** con Fabric API + **Sodium** instalados (replicar el setup del usuario):
   - Mundo superflat creativo. `/schematic-info` loguea el resumen correcto.
   - Cargar schematic desde el selector → aparece el ghost texturizado translúcido en el jugador (M2).
   - Toggle ver-a-través: el ghost se ve detrás de bloques sólidos.
   - Modo grab: el ghost sigue la mira, snapea a caras; rueda acerca/aleja; Shift+rueda sube/baja;
     clic fija; Esc cancela (M3).
   - HUD: slider cambia opacidad en vivo (sin hitch); rotar ±90° y espejo se reflejan correctamente
     en bloques direccionales (escaleras, puertas, troncos) (M4).
   - Reiniciar el cliente → la última colocación y preferencias persisten.
   - Arrastrar un `.litematic` desde el explorador a la ventana → se importa y carga (M5).
3. **Rendimiento** (M5): schematic ~50³; medir frame-time en estado estable vs. durante rotación.
   Objetivo: estado estable sin coste perceptible; rotación con hitch < ~1 frame o mitigado por debounce.
4. **Regresión Sodium**: sin crash al entrar/salir de mundos, sin z-fighting notable, culling correcto.

## Referencias

- Formato: [litemapy docs](https://litemapy.readthedocs.io/en/latest/litematics.html) ·
  [SkytAsul/Lite2Edit](https://modrinth.com/mod/lite2edit) · `sakura-ryoko/litematica`
- Aviso de seguridad: [abfielder.com/litematica-security-update](https://abfielder.com/litematica-security-update)
  (corregido en 0.26.11; es un directory traversal en la transferencia servidor→cliente, ajeno a este mod)
- Render 26.1: [Fabric 26.1](https://fabricmc.net/2026/03/14/261.html) ·
  [Rendering in the World (Fabric docs)](https://docs.fabricmc.net/develop/rendering/world) ·
  [Porting to Fabric API 26.1](https://docs.fabricmc.net/develop/porting/fabric-api)
- Precedente de compat: SchematicPreview, Litematica (ambos Fabric-nativos)
