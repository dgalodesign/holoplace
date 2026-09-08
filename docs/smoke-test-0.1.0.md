# Smoke test — HoloPlace 0.1.0 (MC 26.1.2)

Una pasada deliberada por **cada** feature, una vez, con el **jar real** (no `runClient`).
Marca ✅ / ❌ y anota cualquier cosa rara. Si algo falla → issue, no bloquea salvo que sea grave.

## Preparación

- [ ] `./gradlew clean build` → coger `build/libs/holoplace-0.1.0.jar` (**no** el `-sources.jar`)
- [ ] Instancia de **producción** (launcher oficial o Prism): Fabric Loader 0.19.5 + Fabric API
      `0.155.2+26.1.2` + este jar. MC **26.1.2**.
- [ ] Arranca. Mod Menu (si lo usas): HoloPlace aparece, ícono OK, botones de homepage / source /
      issues funcionan, autor "Edgar D' Galo".
- [ ] Entra a un mundo creativo plano. Mensaje de bienvenida en el chat a la primera.
- [ ] Ten 2-3 `.litematic` a mano en `config/holoplace/schematics/` (o para arrastrar).

## Carga y colocación

- [ ] `/holoplace help` — lista completa de comandos y teclas.
- [ ] **Arrastrar** un `.litematic` a la ventana → se importa y aparece el fantasma, en modo grab.
- [ ] `K` → pestaña **Construir**: la lista muestra los schematics; el cargado marcado con ●.
- [ ] Hover sobre una fila → tooltip con tamaño / nº de bloques / regiones / versión de datos.
- [ ] Click en otra fila → carga ese schematic, cierra la pantalla.
- [ ] Botón "Abrir carpeta de schematics" → abre el explorador.
- [ ] `G` (grab): el fantasma sigue la cruceta, se pega a la cara del bloque.
- [ ] Rueda del ratón → cambia distancia. `Shift`+rueda → altura. `G` de nuevo → bloquea.
- [ ] `Alt`+rueda (con fantasma visible) → opacidad.

## Visualización (pestaña Construir + teclas)

- [ ] Slider de **opacidad** — el fantasma (bloques, fluidos, modelos de BE, entidades) se desvanece junto.
- [ ] **Rotar** `-90°` / `+90°` (y tecla `R` / `Shift+R`) — gira sobre el centro del footprint.
      En un esquema **grande**: aparece el contorno cian + "preparando el modelo…" y luego el modelo girado.
- [ ] **Espejo** (botón y tecla `M`) — cicla —/FB/LR.
- [ ] **Reset** — vuelve a 0° / sin espejo.
- [ ] **Ver a través** (checkbox y tecla `X`) — el fantasma se dibuja sobre las paredes.
- [ ] **Capas** — los dos sliders (Desde / Hasta) recortan el fantasma en Y; "Todas" resetea.
- [ ] Avanzado ▸ **Ignorar orientación del bloque**, **Detalles** (cofres/carteles/marcos/cuadros),
      **Opacidad de marcadores**.

## Asistente de construcción (`H`)

- [ ] `H` → oculta bloques ya colocados correctamente; línea de progreso con % en el HUD y en la pantalla K.
- [ ] Coloca un bloque **equivocado** donde va otro → cubo de alambre rojo, **sin** fantasma encima;
      mirándolo, tooltip "Debería ser: X" con el icono del ítem correcto.
- [ ] Deja un bloque **de más** (donde el schematic quiere aire) → cubo de alambre naranja.
- [ ] Con see-through activo (`X` + `H`) → los marcadores rojos/naranjas se ven a través de las paredes.
- [ ] Esquema grande mal colocado → muchos marcadores, sin caja envolvente, sin freeze; HUD dice `N mal · M sobran`.
- [ ] `/holoplace materials` — lista de bloques necesarios y los que faltan.

## Crear schematic (pestaña Crear)

- [ ] `K` → pestaña **Crear**. Botón "Seleccionar área" → cierra la pantalla, entra en modo selección.
      (o tecla `B`)
- [ ] Click izq / der en dos bloques → esquinas 1 y 2, caja verde/naranja + cubos de esquina, caras traslúcidas.
- [ ] `K` → Crear: muestra las coordenadas de las esquinas y el tamaño / nº de celdas.
- [ ] Escribe un nombre → botón "Guardar" → `§aGuardado …` y el archivo aparece en la carpeta.
- [ ] Carga ese schematic recién creado → se ve igual que el original (bloques + cofres + entidades).
- [ ] Botón "Limpiar" borra la selección.

## Persistencia y comandos

- [ ] Coloca y bloquea un fantasma. Sal del mundo y vuelve a entrar → sigue donde lo dejaste.
- [ ] `Ocultar` (botón / `/holoplace hide`) → desaparece el fantasma pero NO la colocación;
      `Mostrar` / `/holoplace show` lo trae de vuelta.
- [ ] `/holoplace clear` → borra la colocación del mundo.
- [ ] `/holoplace move <x y z>`, `/holoplace nudge <dir> [n]`, `/holoplace layers <min> <max>|off`,
      `/holoplace info <archivo>`, `/holoplace list` — todos con tab-completion.
- [ ] Idioma: cambia el juego a español → HUD, tooltips, comandos y pantalla K en español.
      Vuelve a inglés → todo en inglés.

## Compatibilidad (§3.2 / §3.3 del checklist)

- [ ] **Sodium** (ya validado en dev, re-confirmar con el jar): fantasma OK, sin z-fighting raro.
- [ ] **Iris + shaderpack** (Complementary / BSL) con shaders activos: ¿el fantasma sigue visible,
      traslúcido, con color de bioma? Si rompe → documentar "shaders: soporte limitado", NO bloquea.
- [ ] **Litematica instalada a la vez**: ambos cargan, sin choque de teclas obvio (Litematica usa `M`+…,
      HoloPlace usa `M` solo — revisar). Los dos fantasmas conviven.
- [ ] Un **mod de zoom con scroll** (p. ej. Zoomify / Ok Zoomer): el scroll de zoom sigue funcionando
      cuando HoloPlace NO está en grab mode. En grab mode HoloPlace se queda el scroll — esperado.
- [ ] WorldEdit CUI u otro overlay de selección: sin parpadeos ni crashes.

## Resultado

Anota aquí qué pasó la pasada y qué no, y pásalo a `progress.md`.
