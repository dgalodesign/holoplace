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
| [`launch-checklist.md`](launch-checklist.md) | Plan del primer lanzamiento (0.1.0): secuencia M24–M29, bloqueantes, checks pre-lanzamiento explicados, flujo de publicación en Modrinth | Antes de publicar |
| [`ui-redesign-m24.md`](ui-redesign-m24.md) | Propuesta de rediseño de la pantalla `K` y el HUD (M24): análisis del estado actual, layout nuevo, tooltips, preguntas abiertas | Antes de tocar la UI |
| [`licensing.md`](licensing.md) | Postura legal: Litematica/MaLiLib son LGPL-3.0; HoloPlace es MIT e independiente. Checklist previo a publicar | Antes de publicar o de copiar cualquier cosa |
| [`backlog.md`](backlog.md) | Ideas aplazadas deliberadamente (subida de vértices a GPU, multi-schematic, WorldEdit, historial de versiones) | Cuando algo real justifique retomar una |

## Estado actual (2026-09)

- **MVP M0–M23 completo.** El código compila; casi todo verificado en el juego por el autor.
- **Falta para publicar**: confirmar repo público, fichas Modrinth/CurseForge, ícono real,
  CHANGELOG, decisión sobre 26.2. Detalle en `progress.md` (sección "Reader hardening + licensing")
  y en `licensing.md` §"Practical checklist before publishing".
- **Easy Place descartado (2026-09-08)** — "1 clic = coloca el bloque por ti" arrastra fricción con
  anticheats. HoloPlace no coloca bloques por decisión de producto. Ver `litematica-reference.md` §8.3
  y `launch-checklist.md`.

## Toolchain (recordatorio)

MC 26.1.2 · Java 25 · Fabric Loader 0.19.5 · Fabric API 0.155.2+26.1.2 · Loom 1.17 · Gradle 9.5.1.
No hay JDK del sistema: usar el JDK bundle de Minecraft (`java-runtime-epsilon`) como `JAVA_HOME`.
`./gradlew genSources` decompila MC a `.gradle/loom-cache/minecraftMaven/**/*-sources.jar`.
Detalle en `progress.md` §"Toolchain reality".
