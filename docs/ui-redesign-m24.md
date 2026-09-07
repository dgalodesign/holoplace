# M24 — pase de UI/GUI

Rediseño de la pantalla `K` (`HoloPlaceScreen`) y retoque del HUD.

**Estado (2026-09-07)**: estructura aprobada por el usuario. Mockup renderizado en
`docs/assets/k-screen-mockup.html` (publicado como artifact). Pendiente: el visto bueno final
sobre el mockup, y luego implementar.

**Decisiones**:
- Cabeceras de sección fijas + solo «Avanzado» plegable. ✅
- Rotar/Espejo = fila de botones `[↺] [↻] [espejo ▸]` en el panel. ✅
- Lista de schematics = lista con scroll (sin paginador). ✅
- HUD: se retoca en este milestone (colapsar a 2 líneas cuando está bloqueado). ✅
- Preview 3D al hover: **su propio milestone, después de M27** (ver §7).

Norte (de `litematica-reference.md` §8): un jugador nuevo necesita ~6 controles visibles; el resto
se agrupa, se esconde o se explica con tooltip. Sigue siendo **una sola pantalla, sin pestañas**
(diferenciador vs Litematica).

---

## 1. Cómo está hoy

Pila vertical plana, centrada, sin scroll:

```
HoloPlace
[============ Opacidad 55% ============]
[✓ Ver a través (X)] [✓ Ocultar colocados (H)] [✓ Solo coincidir bloque] [✓ Ocultar incorrectos también]
[✓ Modelos de entidades de bloque] [✓ Entidades] [✓ Sombreado]
§7xyz [__] [__] [__] [Mover]
§7capas 3–7 de 12        [Todas]
[Desde ────●───]
[Hasta ──────●─]
§7Captura [Área] [______nombre______] [Guardar]
[Ocultar] [Restablecer rot/espejo]
§7Schematics
[ schematic-a ]
[ schematic-b ]
... (hasta 10)
[ < ]  página 1/2  [ > ]
[ Hecho ]
```

### Problemas (evaluados para "fácil para nuevos")

| # | Problema |
|---|---|
| 1 | **Sin jerarquía visual**. ~10 filas de widgets mezclados, todos con el mismo peso. No se distingue "esto es build-assist" de "esto es qué se dibuja". |
| 2 | **Etiquetas jerga sin tooltip**. "Solo coincidir bloque", "Ocultar incorrectos también", "Modelos de entidades de bloque", "Sombreado" — un jugador nuevo no sabe qué hacen. |
| 3 | **Sub-opciones de build-assist siempre visibles** aunque build-assist esté apagado. "Solo coincidir bloque" y "Ocultar incorrectos también" solo importan con "Ocultar colocados" activo → 2 controles muertos casi siempre. |
| 4 | **Rotar / espejo NO están en la pantalla**. Solo por hotkey. El propio pitch del mod dice "controles básicos (opacidad, rotación, espejo) siempre visibles" — y no lo están aquí. El estado de rotación/espejo tampoco se muestra. |
| 5 | **La pantalla es alta** (~20 filas). En escala de GUI grande o ventana pequeña se sale (sin scroll). Lo marcó la auditoría. |
| 6 | **La lista de schematics está al fondo**, debajo de todos los controles — y los controles solo importan *después* de cargar uno. |
| 7 | **Captura mezclada con controles de visualización**. Es otra tarea/modo, va encajada entre capas y acciones. |
| 8 | **Sin cabecera de estado**. Al abrir `K` sin nada cargado, la mayoría de controles están inertes sin explicación. |
| 9 | **Fila de coordenadas** (`xyz [ ][ ][ ] [Mover]`) — escribir coords a mano contradice el "arrastra, no escribas" y está en primer plano. |

---

## 2. Propuesta

Una pantalla, en **secciones con cabecera**, envuelta en un contenedor con scroll (red de
seguridad — nunca se sale). La lista de schematics pasa a ser una **lista con scroll propio**
(~6 filas visibles) para que el resto quepa sin scroll de página en condiciones normales.

```
┌─ ❖ HoloPlace ─────────────────────────────────────────── [x] ┐
│                                                              │
│  bigcastle   ·  24×18×31   ·  rot 90°  espejo LR             │  ← cabecera de estado
│  [ Ocultar fantasma ]                                        │     (o "Elige un schematic abajo, o
│                                                              │      suelta un .litematic en la ventana")
│  ── VISUALIZACIÓN ─────────────────────────────────────      │
│  Opacidad   [=========|====]  55%                            │
│  Rotar   [ ↺ ]   90°   [ ↻ ]      Espejo  [ LR ▸ ]   [Reset] │  ← NUEVO en el panel
│  [✓] Ver a través de paredes                        (X)      │
│  [✓] Sombreado de caras                                      │
│                                                              │
│  ── ASISTENTE DE CONSTRUCCIÓN ─────────────────────────      │
│  [✓] Ocultar lo que ya coloqué                      (H)      │
│       340 / 512 colocados  (66%)                             │  ← solo si está activo
│       [✓] Ignorar la orientación del bloque                  │  ← indentado, gris si el padre está off
│       [✓] Ocultar también mis errores                        │  ← indentado, gris si el padre está off
│  Capas   [ 3 ──●────────●── 7 ]  de 12          [ Todas ]    │  ← control de rango compacto
│                                                              │
│  ── SCHEMATICS ───────────────────────────────────────       │
│  ┌────────────────────────────────────────────────────┐     │
│  │  bigcastle                                    ●     │ ▲   │  ← lista con scroll,
│  │  starter-house                                      │ █   │     ~6 filas visibles,
│  │  redstone-door                                      │ ░   │     la cargada marcada
│  │  ...                                                │ ▼   │
│  └────────────────────────────────────────────────────┘     │
│  [ Abrir carpeta de schematics ]                            │
│                                                              │
│  ── CREAR UN SCHEMATIC ───────────────────────────────       │
│  [ Seleccionar área ] (B)   [____ nombre ____]   [ Guardar ] │
│  §8 clic izq. y der. en dos bloques para marcar las esquinas │
│                                                              │
│  ▸ Avanzado                                                  │  ← fila plegable, cerrada por defecto
│  ┆ Mover a coords   x[__] y[__] z[__]   [ Ir ]              │
│  ┆ [✓] Modelos de entidades de bloque   [✓] Entidades       │
│                                                              │
│                                          [ Hecho ]           │
└──────────────────────────────────────────────────────────────┘
```

### Cambios concretos

| Cambio | Por qué |
|---|---|
| **Cabecera de estado** (nombre · tamaño · rot · espejo · botón Ocultar/Mostrar) | Da contexto; resuelve #8 |
| **Secciones con cabecera** (VISUALIZACIÓN / ASISTENTE / SCHEMATICS / CREAR / Avanzado) | Jerarquía; resuelve #1, #7 |
| **Rotar / Espejo en el panel** (botones `↺ 90° ↻` y `espejo ▸`, reusan `PlacementController.rotate/cycleMirror`) | Cumple el pitch; resuelve #4 |
| **Sub-opciones de build-assist indentadas y deshabilitadas cuando el padre está off** | Resuelve #3 |
| **Progreso `340/512 (66%)` bajo el toggle**, solo cuando está activo | Feedback donde se usa |
| **Lista de schematics = lista con scroll** (~6 filas), la cargada marcada con `●` | Resuelve #5, #6; quita el paginador |
| **Coords + modelos BE + entidades → sección "Avanzado" plegable, cerrada** | Resuelve #9; son "casi nunca lo tocas" |
| **Contenedor con scroll** alrededor de todo | Red de seguridad para #5 |
| **Tooltip en cada control no obvio** (§3) | Resuelve #2 |
| Renombrar: "Sombreado" → "Sombreado de caras"; "Solo coincidir bloque" → "Ignorar la orientación del bloque"; "Ocultar incorrectos también" → "Ocultar también mis errores"; "Modelos de entidades de bloque" → sin cambio (ya en Avanzado) | Claridad |

### Qué NO cambia

- Sigue siendo **una pantalla, sin pestañas**.
- Los hotkeys (`X`, `H`, `R`, `M`, `B`, `G`, `K`) y su comportamiento — solo se muestran junto a su control.
- La lógica de `PlacementController` / `GhostState` / `HoloPlaceConfig` — el rediseño es de presentación.
- Persistencia: cada toggle sigue guardando en `config.json` al cambiar.

---

## 3. Tooltips (texto propuesto, ES)

| Control | Tooltip |
|---|---|
| Ver a través de paredes | Dibuja el fantasma incluso a través de bloques sólidos. |
| Sombreado de caras | Oscurece las caras como un bloque real (arriba más claro, lados más oscuros). |
| Ocultar lo que ya coloqué | Deja de dibujar los bloques que ya construiste bien. Muestra un % de progreso. |
| Ignorar la orientación del bloque | Cuenta un bloque como colocado aunque mire hacia otro lado. |
| Ocultar también mis errores | Donde pusiste el bloque equivocado, oculta también el modelo del fantasma — solo queda el contorno rojo. Menos que renderizar. |
| Capas | Muestra solo una franja horizontal del schematic, para construir piso por piso. |
| Modelos de entidades de bloque | Dibuja cofres, carteles, etc. como modelos reales en vez de una caja de alambre. |
| Entidades | Dibuja marcos, soportes de armadura y cuadros del schematic. |
| Seleccionar área | Después, haz clic izquierdo y derecho en dos bloques para marcar las esquinas de lo que quieres guardar. |
| Rotar / Espejo | Gira o refleja la colocación 90°. También con las teclas R / M. |

(Versión EN equivalente en el mismo commit.)

---

## 4. HUD (retoque menor, mismo milestone)

El HUD in-world está bien pero es denso (6 líneas siempre). Propuesta suave:

- **Mientras agarras** (`grab`): las 6 líneas completas (posición, tamaño, rot/espejo/opacidad, hint).
- **Bloqueado**: colapsar a 2 líneas — `❖ nombre` + (si build-assist) `340/512 (66%)`. La línea de
  hint solo mientras agarras o al abrir `K`.
- Mantener el panel de captura (`CaptureHud`) como está.

Alternativa si prefieres no tocar el HUD ahora: dejarlo y solo hacer la pantalla `K`.

---

## 5. Alcance / esfuerzo

- `HoloPlaceScreen` reescrito (la lógica de acción ya existe en `PlacementController`).
- Nuevo widget: lista de schematics con scroll (`ContainerObjectSelectionList` de vanilla, o una
  `ScrollableLayout` con botones).
- Nuevo: fila "Avanzado" plegable (un botón que muestra/oculta un sub-layout + `rebuildWidgets`).
- Tooltips: `Checkbox.builder(...).tooltip(Tooltip.create(...))` y `Button.tooltip(...)`.
- Botones rotar/espejo: 3-4 botones nuevos que llaman a `PlacementController` y hacen `rebuildWidgets`.
- ~20-30 claves de lang nuevas (ES + EN), tooltips incluidos.
- Sin cambios en el modelo de datos.

Estimación: **1 pase de tamaño medio**. Riesgo bajo (es UI, testeable a ojo, sin lógica de render).

---

## 7. Preview 3D del schematic al hacer hover — veredicto

**Posible, pero es su propio milestone, no un extra de M24.**

- 26.1 trae un sistema *picture-in-picture* para renderizar 3D dentro de la GUI
  (`net.minecraft.client.renderer.state.gui.pip.*` — `GuiEntityRenderState`, `GuiSignRenderState`,
  `GuiSkinRenderState`, `GuiBookModelRenderState`…). No hay un estado «renderizar una estructura»,
  pero se puede registrar uno propio que dibuje un `GhostMesh` ya horneado en un rectángulo
  recortado, con cámara ortográfica isométrica.
- **El coste real es hornear.** Para previsualizar un schematic hay que parsearlo + hornear su
  malla — exactamente el hitch que **M27** (carga de malla off-thread) va a resolver.
- Necesitaría: caché LRU (últimos 3–4 en hover), tope de tamaño (schematics enormes → info en
  texto, no 3D), y el render PIP a medida.
- **Encaje limpio**: hacerlo **después de M27**, reutilizando su horneador en segundo plano.
  Milestone pequeño, tipo «M27.5».
- **En M24, gratis y útil ya**: al hacer hover sobre una fila de la lista, mostrar **metadatos**
  (tamaño, nº de bloques, versión de datos de MC, nº de bloques desconocidos) — solo requiere leer
  la cabecera del `.litematic`, sin hornear nada. Es lo que de verdad ayuda a elegir el archivo.

→ M24 hace el hover-con-metadatos; el thumbnail 3D entra como M27.5.
