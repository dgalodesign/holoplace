# Plan — captura de schematics (crear `.litematic` desde el mundo)

## Contexto

La auditoría de publicación (2026-09-05) marcó "capturar el mundo → schematic" como el hueco de
funciones más grande frente a Litematica: HoloPlace solo **leía** `.litematic`, nunca los creaba.
Este plan cerró ese hueco (M19–M22), continuando la numeración desde M18.

## Estado

**Enviado:** captura completa — seleccionás un área de dos esquinas y se guarda tal cual está,
estilo Litematica (bloques + block entities + entidades).

**Descartado:** el modo "solo cambios" (capturar únicamente lo que puso el jugador). Se probó a
fondo (registro pasivo de `Level.setBlock` filtrado por una ventana de interacción de 1,5s) y
funcionaba, pero el usuario decidió pausarlo y después removerlo por completo. Todo lo relacionado
(`ChangeLog`, `ChangeTracker`, `LevelBlockChangeMixin`, `HoloPlaceConfig.captureChangesOnly`, el
selector de modo, los tests de `ChangeLog`) se eliminó en 2026-09. Si se retoma, la nota clave es:
**Minecraft no guarda quién puso un bloque**, así que "detectar al guardar, sin rastreo previo" es
imposible — hay que rastrear cambios en vivo o comparar contra una foto base.

## Arquitectura (lo que quedó)

```
main/  capture/
  SelectionState.java      · pos1/pos2 (mundo), normalizados a min/max; tamaño/volumen — lógica pura,
                             testeable                                                        [M19]
main/  schematic/
  LitematicaSchematicWriter.java · Region (grilla BlockState[] + block entities + entidades) →
                                   CompoundTag → .litematic (gzip); inverso del lector, testeable [M20]
client/ capture/
  CaptureController.java   · singleton; alterna "modo selección"; fija esquinas (clic o /cmd); guarda;
                             cancela                                                          [M19/M20]
  CaptureWriter.java       · lee la región del mundo del cliente hacia una Region             [M20/M22]
  SelectionRenderer.java   · caja de alambre + caras traslúcidas + cubos de esquina           [M19]
  CaptureHud.java          · panel arriba a la derecha: esquinas, tamaño, volumen, aviso      [M19]
```

## Cómo funciona

- Keybind `B` (`CAPTURE_SELECT`) alterna "modo selección". Con el modo activo: clic izq fija la
  esquina 1 (cubo verde), clic der la esquina 2 (cubo naranja), vía `AttackBlockCallback` /
  `UseBlockCallback` de Fabric API (se devuelven `InteractionResult.FAIL` para que el clic no rompa
  ni coloque nada; **no** un mixin nuevo). `/holoplace capture [pos1|pos2|clear]` como alternativa.
- La caja se dibuja con `ShapeRenderer` + `RenderTypes.lines()` (aristas) y `RenderTypes.debugQuads()`
  (6 caras traslúcidas), igual que los marcadores de bloque incorrecto/sobrante.
- `/holoplace capture save [nombre]` (o el botón `[Guardar]` de la pantalla `K`) → `CaptureWriter`
  recorre la región: `BlockState` directo de `level.getBlockState`; block entities vía
  `BlockEntity.saveCustomOnly` + `x`/`y`/`z` relativos a la región (formato litematica, sin `id`);
  entidades dentro de la caja vía `entity.save` + `Pos` reescrito relativo a la región (**solo datos
  del cliente** — marcos, soportes de armadura, cuadros, estado visible de mobs; el NBT completo de
  IA/inventario de un mob no se sincroniza al cliente). Sin nombre → `capture-AAAAMMDD-HHMMSS`.
- Se escribe en `config/holoplace/schematics/`, así el schematic aparece de inmediato en el picker.
- Topes: 8M celdas para el `save` (rechaza antes de reservar), aviso en el HUD sobre 5M.

## Milestones

- **M19 — Herramienta de selección** ✅ verificado en el juego.
- **M20 — Escritor `.litematic` + captura completa** ✅ verificado en el juego. 3 tests de
  *round-trip* (`LitematicaSchematicWriterTest`): grilla con bloques con estado → releer con
  `LitematicaSchematicReader` → comparar celda a celda; todo-aire; NBT de block entity por posición.
- **M21 — Modo "solo cambios"** ❌ construido y probado, luego **removido** (ver "Descartado" arriba).
- **M22 (parcial)** ✅ compilado: fila de captura en la pantalla `K` (`[Área]` + campo de nombre +
  `[Guardar]`); captura de entidades; ícono del mod (`assets/holoplace/icon.png`, placeholder) +
  campo `icon` en `fabric.mod.json`. Falta: barra de scroll en la pantalla `K` (se está poniendo
  alta); render de entidades del schematic (hoy el fantasma solo dibuja bloques/fluidos/BEs, así que
  una entidad capturada no se ve en HoloPlace — sí en Litematica).

## Riesgos / límites conocidos

| Tema | Estado |
|---|---|
| Selección enorme cuelga el cliente | Tope de 8M celdas en el `save`, antes de reservar memoria |
| Entidades: datos incompletos | Límite del lado del cliente (Litematica tiene el mismo); mobs guardan solo su estado visible |
| Entidades capturadas no se ven en HoloPlace | HoloPlace no renderiza entidades de un schematic todavía — abrir en Litematica para verlas |
| Pantalla `K` cada vez más alta | Falta scrollbar |
