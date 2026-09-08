# M27 — port a MC 26.2: hallazgos

Rama: `port/mc-26.2`. Estado: **bump + arreglos mecánicos hechos, reescritura del render pendiente.**

## Versiones (verificadas en meta.fabricmc.net / maven, 2026-09-08)

| | 26.1.2 (actual) | 26.2 (destino) |
|---|---|---|
| `minecraft_version` | 26.1.2 | 26.2 |
| `fabric_api_version` | 0.155.2+26.1.2 | **0.160.0+26.2** |
| `loader_version` | 0.19.5 | 0.19.5 (igual) |
| `loom_version` | 1.17-SNAPSHOT | igual (resuelve 1.17.20) |

26.3 existe solo como pre-release (`26.3-pre-2`), no estable. `fabric.mod.json` → `"minecraft": "~26.2"`.

## Arreglos mecánicos (hechos, commit en la rama)

| 26.1.2 | 26.2 |
|---|---|
| `mc.setScreen(x)` / `mc.screen` | `mc.gui.setScreen(x)` / `mc.gui.screen()` |
| `gameRenderer.getMainCamera()` | `gameRenderer.mainCamera()` |
| `mc.gui.setOverlayMessage(...)` | `mc.gui.hud.setOverlayMessage(...)` (split Gui / Hud) |
| `EntityType.create(in, lvl, EntitySpawnReason.LOAD)` | `... new EntitySpawnRequest(EntitySpawnReason.LOAD, false)` |

## Lo grande: 26.2 reescribió el pipeline de render de nivel

El modelo pasó a **submit → collect → execute** (colectores de nodos + un sistema nuevo de "Gizmos"
para líneas de debug). Impacto:

- **`LevelRenderContext.bufferSource()` eliminado.** Ya no hay modo inmediato. Todo va por
  `ctx.submitNodeCollector().order(0)` → `OrderedSubmitNodeCollector`.
- **`net.minecraft.client.renderer.ShapeRenderer` eliminado.** Reemplazo directo:
  `collector.submitShapeOutline(poseStack, VoxelShape, RenderType, int color, float width, boolean afterTerrain)`.
- **Quads de bloque / fluidos del fantasma**: `collector.submitCustomGeometry(poseStack, renderType,
  (pose, buffer) -> { ...putBlockBakedQuad... })`. El buffer sigue en espacio cámara-relativo, así que
  se hornea el offset `anchor - cam` en las coords igual que ahora, o en el `PoseStack`.
- **`OrderedSubmitNodeCollector` cambió firmas** (afecta `GhostOrderedSubmitCollector`, la máquina de
  fade de M23):
  - `submitModelPart` — fuera `sheeted` / `hasFoil` (9 params, no 11)
  - `submitNameTag` — fuera `distanceToCameraSq`, ahora termina en `CameraRenderState`
  - `submitMovingBlock(PoseStack, MovingBlockRenderState, int outlineColor)` — +`int`
  - `submitBreakingBlockModel(PoseStack, List<BlockStateModelPart> parts, int progress)` — era `(BlockStateModel, long seed, int)`
  - `submitParticleGroup(ParticleGroupRenderer)` — **eliminado**; ahora `submitQuadParticleGroup(QuadParticleRenderState)`
  - **nuevos abstractos**: `submitShapeOutline(...)`, `submitGizmoPrimitives(DrawableGizmoPrimitives.Group, CameraRenderState, boolean)`
  - `submitModel`, `submitText`, `submitShadow`, `submitFlame`, `submitLeash`, `submitBlockModel` (no-mesh) — sin cambios
- **El evento**: mover el grueso de `GhostRenderer.render` de `AFTER_TRANSLUCENT_TERRAIN` a
  `COLLECT_SUBMITS` (ahí es donde se hace submit). Ambos eventos siguen existiendo y reciben
  `LevelRenderContext`.
- `GhostPipelines` **compila igual** en 26.2 (`RenderType.create`, `RenderSetup.builder`,
  `RenderPipeline` custom — todo sigue). Los `RenderType` see-through se reusan tal cual.

## Trabajo pendiente en la rama (estimado ~3 h + re-test completo in-game)

1. `GhostRenderer` — `render()` / `renderFluids` / `renderMarkerSet` / `renderExtraBlocks` /
   `renderBlockEntityMarkers` / `renderFootprintOutline` → `submitCustomGeometry` + `submitShapeOutline`,
   movido a `COLLECT_SUBMITS`.
2. `SelectionRenderer` — caja + caras + cubos de esquina → `submitShapeOutline` + `submitCustomGeometry`.
3. `GhostOrderedSubmitCollector` + `GhostSubmitCollector` — re-ajustar a las firmas nuevas de
   `OrderedSubmitNodeCollector`, mantener la semántica de fade. Es lo más delicado.
4. Build + iterar.
5. **Re-verificar in-game TODO el render**: fantasma texturizado, tinte de bioma, shading, fluidos,
   modelos de BE con fade, entidades con fade, see-through, marcadores (mal/sobra/BE), caja de captura,
   cubos de esquina, contorno de footprint mientras hornea (M26).

## Alternativa: quedarse en 26.1.2 para 0.1.0

26.1.2 sigue siendo estable y listada (salió junto a 26.2). Litematica mantiene varias versiones a la
vez. Publicar 0.1.0 en 26.1.2 ahora y portar a 26.2 como 0.2.0 quita el riesgo de regresión del
pipeline de render de la ruta al lanzamiento.
