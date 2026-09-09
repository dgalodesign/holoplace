# HoloPlace — índice de documentación

Mod Fabric client-side para Minecraft 26.1.2 que da UX moderna a los esquemas `.litematic`.
Ver el [`README.md`](../README.md) raíz para la descripción del producto y el estado de milestones.

## Documentos

| Documento | Qué es | Cuándo leerlo |
|---|---|---|
| [`plan.md`](plan.md) | Plan del MVP: contexto, decisiones de arquitectura, diseño de interacción, plan por milestones (M0–M18) | Para entender **por qué** el mod es como es |
| [`progress.md`](progress.md) | Bitácora de milestones: qué se construyó en cada uno, bugs encontrados y cómo se arreglaron, gaps conocidos. Fuente de verdad del estado real | Para saber **qué está hecho y verificado** y qué no |
| [`plan-capture.md`](plan-capture.md) | Plan del sub-proyecto "crear esquemas desde el mundo" (M19–M20). Incluye el modo "solo cambios" que se **descartó** | Antes de tocar la captura de esquemas |
| [`litematica-reference.md`](litematica-reference.md) | Transcripción y análisis del panel de config completo de Litematica (el competidor de referencia) + evaluación de qué tomar / evitar para el objetivo "fácil para nuevos jugadores" | Para decisiones de alcance y UX |
| [`launch-checklist.md`](launch-checklist.md) | Plan del primer lanzamiento: **0.1.0 en MC 26.1.2**, secuencia de milestones, checks pre-lanzamiento explicados, flujo de publicación en Modrinth, backlog para 0.2.0 | Antes de publicar |
| [`smoke-test-0.1.0.md`](smoke-test-0.1.0.md) | Checklist runnable: montar la instancia (Modrinth App) + probar cada feature una vez con el jar real | Antes de publicar 0.1.0 |
| [`modrinth-listing.md`](modrinth-listing.md) | Ficha de Modrinth lista para pegar: campos del proyecto, descripción markdown (EN + ES), changelog de la versión, datos del archivo | Al crear el proyecto en Modrinth |
| [`port-26.2-notes.md`](port-26.2-notes.md) | Hallazgos del port a MC 26.2 (0.2.0): matriz de versiones, arreglos mecánicos hechos, la reescritura del pipeline de render pendiente. Trabajo en rama `port/mc-26.2` | Al retomar el port a 26.2 |
| [`code-audit-2026-09-09.md`](code-audit-2026-09-09.md) | Auditoría de calidad de todo el código pre-0.1.0: veredicto, 3 fixes aplicados, observaciones para 0.1.x / 0.2.0 | Referencia de deuda técnica conocida |
| [`post-launch-plan.md`](post-launch-plan.md) | Estrategia para detectar y triar problemas tras el lanzamiento: registro de riesgos, `/holoplace debug`, plantillas de issue, flujo de triaje, higiene de releases | Antes de publicar y como referencia continua |
| [`ui-redesign-m24.md`](ui-redesign-m24.md) | Propuesta de rediseño de la pantalla `K` y el HUD (M24): análisis del estado actual, layout nuevo, tooltips, preguntas abiertas | Antes de tocar la UI |
| [`licensing.md`](licensing.md) | Postura legal: Litematica/MaLiLib son LGPL-3.0; HoloPlace es MIT e independiente. Checklist previo a publicar | Antes de publicar o de copiar cualquier cosa |
| [`backlog.md`](backlog.md) | Ideas aplazadas deliberadamente (subida de vértices a GPU, multi-schematic, WorldEdit, historial de versiones) | Cuando algo real justifique retomar una |

## Estado actual (2026-09-08)

- **MVP + M24 (UI) + M26 (malla off-thread) hechos y verificados in-game.** Ícono final, CHANGELOG.
- **0.1.0 sale en MC 26.1.2.** 26.2 reescribió el pipeline de render de nivel; portar es riesgo de
  regresión en la ruta al lanzamiento → 0.2.0 (recon en `port-26.2-notes.md`, rama `port/mc-26.2`).
- **En curso (M28)**: `fabric.mod.json` listo; falta smoke test (`smoke-test-0.1.0.md`), compat
  Iris/mods, repo GitHub y ficha Modrinth.
- **Aplazado a 0.2.0**: port a 26.2, thumbnail al hover (M26.5), paquete de asistencia solo-lectura
  (M25), entrypoint ModMenu.
- **Descartado**: Easy Place / cualquier colocación de bloques (fricción con anticheats, 2026-09-08).

## Toolchain (recordatorio)

MC 26.1.2 · Java 25 · Fabric Loader 0.19.5 · Fabric API 0.155.2+26.1.2 · Loom 1.17 · Gradle 9.5.1.
No hay JDK del sistema: usar el JDK bundle de Minecraft (`java-runtime-epsilon`) como `JAVA_HOME`.
`./gradlew genSources` decompila MC a `.gradle/loom-cache/minecraftMaven/**/*-sources.jar`.
Detalle en `progress.md` §"Toolchain reality".
