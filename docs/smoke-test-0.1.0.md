# Smoke test — HoloPlace 0.1.0 (MC 26.1.2)

Una pasada deliberada por **cada** feature, una vez, con el **jar real** (no `runClient`).
Marca ✅ / ❌ y anota cualquier cosa rara. Si algo falla → issue, no bloquea salvo que sea grave.

## Preparación

### 1. Compilar el jar

```bash
./gradlew clean build
```

Sale en `build/libs/holoplace-0.1.0.jar`. **Ignora** el `holoplace-0.1.0-sources.jar` — ese no se instala.

### 2. Montar la instancia en la Modrinth App

1. **New instance** → Loader **Fabric**, versión de Minecraft **26.1.2**. (La app descarga sola el
   Java 25 que necesita 26.1.2.)
2. Con la instancia abierta → pestaña **Content** → **Add content** → busca **Fabric API** e
   instala la versión **`0.155.2+26.1.2`** (filtra por game version 26.1.2).
3. Meter el jar local: en la instancia → menú **···** (o *Options*) → **Open folder** → entra en
   `mods/` → copia ahí `holoplace-0.1.0.jar`.
   - La app puede marcarlo como "contenido desconocido" / no gestionado por Modrinth. Es normal
     para un jar local — no lo quites.
4. (Opcional pero recomendado) instala también **Mod Menu** y **Sodium** desde la pestaña Content,
   para las comprobaciones de más abajo.
5. **Play**.

### 3. Primer arranque

- [ ] El juego abre sin crash. Revisa el log (instancia → **Logs**) — sin errores de mixin ni de
      HoloPlace.
- [ ] **Mod Menu**: HoloPlace aparece en la lista, ícono correcto, autor "Edgar D' Galo", y los
      botones **Home / Source / Issues** abren las URLs.
- [ ] Entra a un mundo **creativo** plano. Aparece el mensaje de bienvenida en el chat (solo la 1ª vez).
- [ ] Ten 2-3 `.litematic` listos: pequeño, mediano y uno grande (>50k bloques) para probar el
      horneado. Ponlos en `<instancia>/config/holoplace/schematics/` o arrástralos a la ventana.

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
