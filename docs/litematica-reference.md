# Litematica — referencia de configuración (competidor de referencia)

Transcripción y análisis del panel de configuración completo de **Litematica 0.27.14**
(fork `sakura-ryoko`, MC 26.1.x, idioma "Español (España)"), a partir de capturas del
usuario 2026-09-07. Sirve para dos cosas:

1. **Conocer la superficie completa** de opciones que un usuario de Litematica ve.
2. **Calibrar el norte de diseño de HoloPlace**: *más óptimo y fácil para jugadores nuevos*
   — decidir qué de todo esto es esencial, qué es ruido, y qué conviene tomar prestado.

> Las etiquetas en español están tal cual salen en la traducción de Litematica (algunas mal
> escritas: "Habilatado", "habilita rRenderizado", "rSuper posición"…). Entre paréntesis va el
> nombre interno real de la opción cuando se pudo identificar.

---

## 0. Resumen ejecutivo

| | Litematica | HoloPlace (hoy) |
|---|---|---|
| Config expuesta | **~250 opciones** en 6 pestañas | ~15 (una pantalla `K` + `config.json`) |
| Modelo mental | "todo es un toggle, tú lo armas" | "valores por defecto sensatos, toca poco" |
| Dependencias | MaLiLib (obligatoria) | ninguna |
| Coloca bloques por ti | sí (Easy Place — fricción con anticheats) | **no, por decisión** (2026-09-08) |
| Posicionar la colocación | coordenadas / herramienta de esquinas | arrastrar en pantalla |
| Cada toggle de render tiene | valor **+ hotkey + hotkey-toggle** (3 columnas) | un checkbox |
| Idioma | ~30 vía Crowdin, se elige en config | ES/EN incluidos, sin pasos |

**Lectura para HoloPlace**: la fuerza de Litematica (todo configurable) es también su barrera de
entrada. El 90% de estas opciones son *defaults que nadie toca* o *arreglos de bugs históricos*.
Un jugador nuevo necesita ~6 controles: opacidad, rotar, espejo, ver-a-través, ocultar-colocados,
lista de materiales. Todo lo demás debería tener un default correcto y **no aparecer**.

---

## 1. Pestañas

Litematica divide la config en 6 pestañas (+ "All" que las concatena):

| Pestaña | Contenido | Nº aprox. |
|---|---|---|
| **Genérico** | comportamiento: colocación asistida, pegar, comandos, pick-block, data-fixer, idioma | ~90 |
| **Superposiciones de Información** | HUDs y texto en pantalla: info de bloque, estado, verificador; alineación / escala / offset de cada uno | ~35 |
| **Visuales** | qué se dibuja: esquema, cajas, overlay de diferencias, iluminación falsa, alfas, anchos de línea | ~55 |
| **Colores** | color ARGB (`#AARRGGBB`) de cada elemento del overlay | ~11 |
| **Teclas Rápidas** | ~80 acciones rebindables | ~80 |
| **Capas de Renderizado** | modo de capa (todas / una / rango / encima / debajo) + límites | (GUI aparte) |

Cada fila de una opción **booleana de render** trae 3 columnas:
`[valor TRUE/FALSE]` · `[hotkey para alternarla]` · `[tipo de activación del hotkey]`.
Esto triplica el ruido visual de la pantalla.

---

## 2. Genérico

### 2.1 Colocación asistida (Easy Place) — *el feature estrella de Litematica*

| Etiqueta ES (interno) | Default | Qué hace | ¿Nuevo jugador? |
|---|---|---|---|
| Modo Asistente Colocación (`easyPlaceMode`) | FALSE | Un clic coloca el bloque correcto del esquema en la orientación correcta (sin tener el bloque exacto en mano) | **Sí —核心** |
| Primero Asistente Colocación (`easyPlaceFirst`) | TRUE | Easy Place tiene prioridad sobre la interacción normal | interno |
| Mantener Habilitado Asistente Colocación (`easyPlaceHoldEnabled`) | TRUE | Requiere mantener pulsada la tecla de uso | opcional |
| Modo Asistente Colocación (Un Jugador) | TRUE | Habilita Easy Place en mundos locales | interno |
| Validación de Colocación fácil en un jugador | TRUE | Verifica el resultado en SP | interno |
| Versión Protocolo EasyPlace (`easyPlaceProtocolVersion`) | Auto | Protocolo para servidores (Slot / V2 / V3) | interno |
| Swing Mano Asistente Colocación | TRUE | Anima el brazo al colocar | cosmético |
| Alcance Vanilla EasyPlace | FALSE | Limita el alcance al de vanilla | opcional |
| easyPlaceClickAdjacent | FALSE | Permite click en cara adyacente | interno |
| EasyPlacePostRewrite | FALSE | Implementación reescrita (experimental) | interno |
| Intervalo de Intercambio Asistente Colocación | 0 | ticks entre swaps de hotbar | interno |
| Requiere Sostener Herramienta para Ejecutar | TRUE | Solo actúa con el "tool item" en mano | opcional |
| enableDifferentBlocks | FALSE | Permite colocar aunque el bloque en mano difiera | avanzado |

### 2.2 Pegar / edición de mundo (`//paste` estilo WorldEdit) — *power users*

| Etiqueta ES | Default | Nota |
|---|---|---|
| Usar Editar Mundo Comando | FALSE | Usa `/` comandos para pegar |
| Pegar Usando Comando de Llenar | TRUE | `fill` en vez de `setblock` cuando puede |
| Pegar Usando Comandos en Sp | FALSE | |
| Pegar Usando Servux | TRUE | Requiere el mod de servidor Servux |
| Pegar Siempre Usando Llenar | FALSE | |
| Pegar Ignorando Entidades / Inventarios / Entidades de Bloque (varios) | FALSE/TRUE | qué se omite al pegar |
| Comportamiento Restaurar Nbt Pegar (`pasteNbtBehavior`) | Ninguno | None / Place&Modify / Teleport |
| Comportamiento Reemplazar Pegar (`pasteReplaceBehavior`) | Ninguno | None / Everything / With non-air |
| pasteLayerBehavior | All Layers | respeta el modo de capa al pegar |
| Volumen Máximo Llenado Por Chunk | 32768 | tope de `fill` |
| Límite Comando Por Tick | 8 | throttle de comandos |
| Intervalo Tarea Comando | 1 | ticks entre tareas |
| Nombre Comando Clonar / Llenar / Colocar Bloque / Invocar | clone/fill/setblock/summon | por si el servidor los renombra |
| commandFillNoChunkClamp | FALSE | |
| Usar parámetro estricto de comandos | TRUE | |
| Desactivar Retroalimentación Comando (`commandDisableFeedback`) | TRUE | silencia el spam de chat de los comandos |
| Tasa de Petición de Nbt del Servidor | 2 | Hz de sync de NBT |

**HoloPlace**: nada de esto entra en el MVP (es "escribir en el mundo", no "construir a mano").
La captura de esquemas de HoloPlace es el único punto de contacto y ya es mucho más simple.

### 2.3 Pick-block (rueda / tecla saca el bloque correcto)

| Etiqueta ES (interno) | Default |
|---|---|
| PickBlock Habilitado (`pickBlockEnabled`) | TRUE |
| PickBlock Evitar Dañables (`pickBlockAvoidDamageable`) | TRUE |
| PickBlock Evitar Herramientas | FALSE |
| PickBlock Shulkers | FALSE |
| Espacios Bloqueables de PickBlock | 1,2,3,4,5 | slots de hotbar que puede pisar |
| Resaltar Bloque en Inventario (`highlightBlockInInventory`) | FALSE |

*Útil para nuevo jugador* ("saca el bloque que necesito"), pero acoplado a un montón de sub-opciones.

### 2.4 Selección de área / esquemas

| Etiqueta ES | Default | Nota |
|---|---|---|
| Selecciones Área Por Mundo | TRUE | selecciones distintas por mundo |
| Cambiar Esquina Seleccionada Al Mover | TRUE | |
| Modo Esquinas Selección (`selectionCornersMode`) | Esquinas | Corners / Expand |
| Clonar En Posición Original | FALSE | |
| Deduplicar entidades de esquema | FALSE | |
| Directorio Base Esquema Personalizado (+ Habilitado) | ruta / FALSE | carpeta alternativa de esquemas |
| Generar Nombres en Minúsculas | FALSE | |
| Modo Data fixer (`datafixerMode`) | Siempre | Always / Schematic Only / Never — **convierte esquemas de MC viejo** |
| Esquema Predeterminado Data fixer | 1139 | data version asumida si falta |

### 2.5 Lista de materiales

| Etiqueta ES | Default |
|---|---|
| Detalles de recetas en la lista de materiales | TRUE |
| Ignorar Estado de Lista de Materiales | FALSE |
| materialListCountEnderCache | FALSE | cuenta el Ender Chest / shulkers |
| Renderizar Lista de Materiales en Guis | TRUE |

### 2.6 Arreglos de bugs históricos (siempre TRUE, nadie los toca)

`Corregir Espejo de Cofre` (`fixChestMirror`), `Corregir Rotación de Riel` (`fixRailRotation`),
`fixStairsMirror`. → En HoloPlace esto se resuelve una vez en `PlacementTransform.applyToState`
y no es una opción.

### 2.7 Varios / dev

`Modo del HUD de depuración`, `Mundo en HUD de depuración`, `Registro Debug`,
`Mostrar comentarios de operaciones de archivo`, `Número de hilos del gestor de colocaciones` (2),
`Hilos … en el HUD de depuración`, `Invertir Dirección de Modo de Operación`,
`Renderizar Hilo Sin Tiempo Límite`, `Modo Capa Sigue al Jugador`,
`Sincronización de Datos de Entidad` (+ Respaldo + Tiempo de Espera de Caché 2.75),
`Elemento Herramienta` (`minecraft:stick`) + `Componentes` (EMPTY),
`Pegar Texto de Cartel` (TRUE), `Elemento Herramienta Habilitado` (TRUE),
`Mostrar Schematic VCS` (FALSE) + `schematicVcsDeleteMode` (Matching Block).

### 2.8 Idioma

| Etiqueta ES | Valor |
|---|---|
| Idioma de traducción | Español (España) |
| Modo de traducción | Follow Minecraft / Force language |

→ HoloPlace: incluido de fábrica, sigue a MC, sin pantalla.

---

## 3. Superposiciones de Información (HUDs)

Litematica tiene **4 overlays de texto** independientes, cada uno con su alineación, escala y offset:

| Overlay | Toggle | Para qué |
|---|---|---|
| **Líneas Info Bloque** (`blockInfoLines`) | TRUE | al mirar un bloque del esquema, lista name/props esperados vs reales |
| **Superposición Info Bloque** (`blockInfoOverlay`) | TRUE | recuadro flotante junto al bloque objetivo |
| **HUD Info Estado** (`statusInfoHud`) | FALSE (auto TRUE) | panel de estado (esquema activo, capa, modo…) |
| **Superposición Verificador** (`verifierOverlay`) | TRUE | resalta en el mundo los errores del schematic verifier |

Ajustes repetidos ×4 (alineación, X/Y offset, escala) + `Modo de selección predeterminado` (Simple),
`Superposición Info Fluidos Objetivo` (FALSE), `Advertir Renderizado Desactivado` (TRUE),
`Líneas Máximas HUD Info` (10), `Líneas Máximas HUD Lista Material` (10),
`Alpha Resaltar Error Verificador` (0.2), `Máx Posiciones Resaltar Error Verificador` (1000).

Alineaciones vistas: `Arriba a la derecha`, `Centro Superior`, `Abajo a la derecha`,
`Abajo a la izquierda`. Escala fuente líneas info bloque `0.5`.

**HoloPlace**: hoy tiene 1 HUD (panel `K` + contador de restantes). El "info de bloque al mirar"
(qué debería ir aquí) sí es valioso para un nuevo jugador y ya existe en forma de marcador
crosshair (M17). No necesita 4 HUDs ni 12 sliders de posición.

---

## 4. Visuales

### 4.1 Toggles maestros (jerarquía de "habilitar renderizado")

```
Habilitar Renderizado                         (master global)
└─ Habilitar Renderizado Esquema
   ├─ Habilitar Renderizado Bloques Esquema
   ├─ Habilitar Renderizado Fluido Esquema
   ├─ renderSchematicEntities        (TRUE)
   ├─ renderSchematicTileEntities    (TRUE)
   ├─ Activar cajas de colisión de entidades del esquema
   └─ enableSchematicFakeLighting    (TRUE) → renderFakeLightingLevel 15
└─ Habilitar Renderizado Cajas Selección Área
└─ Habilitar Renderizado Cajas Colocación
└─ Habilitar Superposición Esquema             (el overlay de diferencias)
   ├─ Activar Contornos / Activar Lados
   ├─ Superposición Modelo Esquema Contorno / Lados
   ├─ Renderizar A través Bloques   (FALSE)
   └─ tipos: Faltante / Extra / Bloque Incorrecto / Estado Incorrecto  (todos TRUE)
```

### 4.2 Overlay de diferencias (schematic overlay) — *el equivalente al "build-assist" de HoloPlace*

| Tipo | Default | HoloPlace |
|---|---|---|
| Faltante (missing) | TRUE | el ghost mismo |
| Extra (sobra un bloque) | TRUE | marcador naranja (M18) |
| Bloque Incorrecto (wrong block) | TRUE | marcador rojo (M18) |
| Estado Incorrecto (wrong state) | TRUE | cubierto por "match exacto vs solo-bloque" |
| `schematicOverlayTypeDiffBlock` | TRUE | |

### 4.3 Ajustes finos de render

| Etiqueta ES | Default |
|---|---|
| Alpha Bloque Fantasma (`ghostBlockAlpha`) | 0.5 | ← HoloPlace: slider de opacidad, 0.55 |
| Alpha Lado Caja Colocación | 0.2 |
| Anchura Superposición Esquema Contorno / … A través | 1.0 / 3.0 |
| Renderizar Bloques Como Translúcidos (`renderBlocksAsTranslucent`) | FALSE | ← HoloPlace lo hace siempre |
| renderEnableTranslucentResorting | TRUE |
| Renderizar Lados Internos Bloques Translúcidos | FALSE |
| Súper posición Reducir Lados Internos | FALSE |
| renderAOModernEnable | FALSE | ambient occlusion |
| Renderizar Bloques Colisionando Esquema | FALSE |
| Renderizar Conexiones / Lados Marcador Error | FALSE / TRUE |
| Renderizar Caja Envolvente Colocación (+ Lados) | TRUE / FALSE |
| Renderizar Lados Caja Selección Área / Colocación | TRUE / FALSE |
| Ignorar Fluidos Existentes / `ignoreExistingBlocks` / `ignorableExistingBlocks` / `ignoreCropAge` | FALSE / FALSE / [] / FALSE |

**Lectura**: la mitad de "Visuales" es afinar el *overlay de wireframe* de diferencias (colores,
contornos vs caras, grosor, a-través-de-paredes). HoloPlace lo sustituye por: ghost texturizado +
3 tipos de marcador con color fijo. Menos preciso de tunear, drásticamente más simple.

---

## 5. Colores (`#AARRGGBB`)

| Elemento | Valor | Color |
|---|---|---|
| Lado Caja Selección Área | `#30FFFFFF` | blanco |
| Resaltar Bloque En Inventario | `#30FF30FF` | magenta |
| Recuentos lista de materiales (HUD) | `#FFFFAA00` | naranja |
| Rehacer Esquema · Romper→Colocar | `#4C33CC33` | verde |
| Rehacer Esquema · Romper excepto colocar | `#4CF03030` | rojo |
| Rehacer Esquema · Reemplazar | `#4CF0A010` | naranja |
| `schematicOverlayColorDiffBlock` | `#30F8D650` | amarillo |
| Overlay · Extra | `#4CFF4CE6` | magenta |
| Overlay · Faltante | `#2C33B3E6` | azul |
| Overlay · Bloque Incorrecto | `#4CFF3333` | rojo |
| Overlay · Estado Incorrecto | `#4CFF9010` | naranja |

→ HoloPlace fija estos colores en código (`GhostRenderer.WRONG_COLOR`, `EXTRA_COLOR`, etc.).
Personalizarlos no es una necesidad de jugador nuevo.

---

## 6. Teclas Rápidas (hotkeys)

~80 acciones. Las que un jugador nuevo realmente usa están en **negrita**.

### 6.1 GUIs
| Acción | Bind visto |
|---|---|
| **Abrir Menú Principal GUI** | `M` |
| Abrir Configuraciones GUI | `M + C` |
| **Abrir Lista Material GUI** | `M + L` |
| Abrir Verificador Esquemas GUI | `M + V` |
| Abrir Colocaciones Esquemas GUI | `M + P` |
| Abrir Esquemas Cargados GUI | NONE |
| Abrir Gerente Selección Área GUI | `M + S` |
| Abrir Configuración Área / Colocación GUI | `KP_MULTIPLY` / `KP_SUBTRACT` |
| Abrir Proyectos Esquemas GUI | NONE |

### 6.2 Construir
| Acción | Bind visto |
|---|---|
| **Tecla para el uso Asistente Colocación** | `BUTTON_2` (clic derecho) |
| **Alternar Renderizado Esquema** | `M + G` |
| **Alternar Todo Renderizado** | `M + R` |
| Alternar Habilitar Herramienta | `M + T` |
| Refrescar Esquema | `F3 + M` |
| Renderizar Superposición Info (mantener) | `I` |
| Renderizar Superposición A través Bloques (mantener) | `RIGHT_CONTROL` |
| Modificador Modo Operación | `LEFT_CONTROL` |
| Pick Block Primero / Alternar | `BUTTON_3` / `M + BUTTON_3` |

### 6.3 Capas (layer mode)
| Acción | Bind visto |
|---|---|
| **Capa Siguiente / Anterior** | `PAGE_UP` / `PAGE_DOWN` |
| **Modo Capa Siguiente / Anterior** | `M + PAGE_UP` / `M + PAGE_DOWN` |
| Capa Establecer Aquí | NONE |
| Ciclo Modo Selección | `LEFT_CONTROL + M` |

### 6.4 Rotar / espejo / mover (¡sin bind por defecto!)
| Acción | Bind visto |
|---|---|
| Rotación Colocación Esquema | **NONE** |
| Espejo Colocación Esquema | **NONE** |
| Mover Selección Entera Aquí | NONE |

> Litematica **no asigna** teclas para rotar/espejar la colocación — el usuario nuevo tiene que
> descubrir que existe la acción y bindearla, o hacerlo desde la GUI de colocaciones.
> **HoloPlace las tiene siempre visibles en el panel `K` + hotkeys por defecto.** Ventaja clara.

### 6.5 Selección de esquinas (la herramienta)
| Acción | Bind visto |
|---|---|
| Herramienta Colocar Esquina 1 / 2 | `BUTTON_1` / `BUTTON_2` |
| Herramienta Seleccionar Elementos | `BUTTON_3` |
| Herramienta Seleccionar Modificar Bloque 1 / 2 | `LEFT_ALT` / `LEFT_SHIFT` |
| Establecer Posición Caja Selección 1 / 2 | NONE |
| Añadir Caja Selección | `M + A` |
| Guardar Área Como Esquema En Archivo | `LEFT_CONTROL + LEFT_ALT + S` |
| Guardar Área Como Esquema En Memoria | NONE |

### 6.6 Editar esquema in-place (VCS / delete-by-placement) — power users
`Editar Esquema Romper/Colocar Todos`, `… Romper Todo Excepto`, `… Reemplazar Todo/Bloque/Dirección`,
`schematicVCSDeleteBlockByPlacement`, `Ciclo Siguiente/Anterior Versión Esquema` (+ modificador) —
todos NONE por defecto.

### 6.7 Alternar renderizado (una hotkey por cada toggle de Visuales)
`Alternar Renderizado {Cajas Selección Área, Superposición Info, Superposición, Contorno Superposición,
Lados Superposición, Cajas Colocación, Bloques Esquema, Translúcido, Superposición Verificador}`,
`Alternar Restricción Colocación`, `Alternar Pegar Texto Cartel` — casi todos NONE.
Esto es ~15 hotkeys que existen "por si acaso".

---

## 7. Capas de Renderizado (no screenshot — de conocimiento)

GUI aparte (`Abrir Configuración Área GUI`). Modos:
`All` · `Single Layer` · `Layer Range` · `All Above` · `All Below` · `Hull`.
Se opera con `PAGE_UP/DOWN` (mover la capa) y `M + PAGE_UP/DOWN` (cambiar de modo).

→ **HoloPlace ya lo tiene** (`GhostState.layerClip` / `layerVisible`, controles en `K`).
El modelo de HoloPlace (un rango Y con dos flechas) es más directo que 6 modos.

---

## 8. Evaluación para HoloPlace — "más óptimo y fácil para jugadores nuevos"

### 8.1 Lo que Litematica hace que un jugador nuevo necesita (y HoloPlace debe igualar o superar)

| Necesidad | Litematica | HoloPlace | Estado |
|---|---|---|---|
| Ver el esquema en el mundo | ghost + overlay wireframe | ghost texturizado | ✅ mejor (texturas) |
| Posicionarlo | coords / herramienta esquinas | arrastrar en pantalla | ✅ mucho mejor |
| Rotar / espejar | acción sin bind, o GUI | panel `K` + hotkeys | ✅ mejor |
| Opacidad | slider en Visuales | slider en `K` | ✅ igual, más accesible |
| Ocultar lo ya colocado | overlay "missing" + layer mode | "ocultar colocados" + % + "ocultar incorrectos" | ✅ igual/mejor |
| Saber qué falta | schematic verifier (GUI) + material list | lista de materiales + contador restantes | ✅ más simple |
| Marcar errores (bloque incorrecto / sobra) | overlay de 4 tipos, configurable | 3 marcadores de color fijo | ✅ suficiente, más simple |
| Ver a través de paredes | `renderThroughBlocks` (hotkey) | toggle see-through en `K` | ✅ igual |
| Colocar "1 clic = bloque correcto" (Easy Place) | `easyPlaceMode` + ~10 sub-opciones | **descartado** (2026-09-08, riesgo anticheat) | ✅ decisión de producto — no coloca bloques |
| Construir por capas | 6 modos de layer | rango Y con 2 flechas | ✅ más simple |
| Crear un esquema | herramienta de esquinas + GUI guardar | seleccionar área + `Guardar` | ✅ más simple |

### 8.2 Lo que Litematica expone y HoloPlace **NO debería** exponer

- Los ~15 arreglos de bugs (`fixChestMirror`, etc.) → default correcto en código.
- Los ~15 "Alternar Renderizado X" hotkeys → un toggle en la pantalla basta.
- Las 3 columnas por fila (valor + hotkey + tipo-de-activación) → un checkbox.
- 12 sliders de posición/escala de HUD → una posición fija bien elegida.
- 11 colores personalizables → colores fijos con buen contraste.
- Todo el árbol de "pegar / editar mundo con comandos" → fuera de alcance.
- `datafixerMode`, `commandName*`, `Servux`, `entitiesDataSync`, threads → interno.
- Selección de idioma → seguir a MC.

### 8.3 Lo que conviene **tomar prestado** de Litematica

> **Easy Place (`easyPlaceMode`) — descartado (2026-09-08).** Era el mayor diferenciador de
> Litematica, pero "colocar el bloque por ti" arrastra fricción histórica con anticheats de
> servidores. Decisión de producto: **HoloPlace no coloca bloques**. Se queda en "mira y construye
> a mano" — y ahí ya gana en posicionamiento (arrastrar), rotar/espejo (visibles) y sin dependencias.

1. **Info de bloque al mirar** ("Líneas Info Bloque"): "esto debería ser X orientado así". HoloPlace
   ya tiene el germen (marcador crosshair M17); vale la pena que muestre nombre + orientación
   esperada cuando el bloque puesto está mal. → M25.
2. **"N bloques mal colocados"** en la salida de `/holoplace materials` (schematic verifier como
   lista, no como overlay). El scan `wrongBlock[]` ya existe. → M25.
3. **Pick-block del esquema**: rueda/tecla saca el bloque correcto. Sin riesgo anticheat real (es un
   pick-block vanilla), pero toca el hotbar — **decisión pendiente**, ver `launch-checklist.md` §6.
4. **Marcador de "sobra un bloque"** (extra): ya tomado (M18).

### 8.4 Riesgo de diseño a vigilar

El punto en que HoloPlace deje de ser "6 controles" y empiece a crecer una pestaña de settings.
Regla propuesta: **cada opción nueva necesita justificar por qué su default no sirve para el 90%**.
Si no puede, es un default, no una opción.

---

## 9. Fuentes

- Capturas del panel de config de Litematica 0.27.14 (usuario, 2026-09-07), 16 pantallas,
  pestañas Genérico / Info Overlays / Visuales / Colores / Hotkeys.
- Nombres internos y semántica: conocimiento del proyecto Litematica (masa / sakura-ryoko),
  contrastado con `docs/plan.md` §Contexto y la auditoría de publicación (`docs/progress.md`).
