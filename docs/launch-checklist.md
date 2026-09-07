# HoloPlace — checklist del primer lanzamiento (0.1.0, early access)

Estado a 2026-09-07: **el código del MVP está listo**. Los hallazgos de código de la auditoría
(topes de tamaño en el lector, `NbtAccounter` acotado, guard de overflow en `LitematicaBitArray`)
están **hechos**. Lo que queda es empaquetado y publicación.

Decisión de alcance: se lanza como **0.1.0 / early access**, sin Easy Place. Easy Place y las demás
mejoras van a 0.2.0 — ver §4 y `litematica-reference.md` §8.3.

---

## 1. Bloqueantes (sin esto no se publica)

- [ ] **Repo público**. `fabric.mod.json` → `contact.sources = github.com/edgardgalof/holoplace`.
  Hoy no hay `git remote` configurado. Crear el repo en GitHub, `git remote add origin …`,
  `git push -u origin main`. Confirmar que es público y que tiene el código + `LICENSE` + `README`.
- [ ] **Ícono real**. `assets/holoplace/icon.png` es un placeholder generado (cubo de alambre cian).
  Diseñar uno de 256×256 o 512×512 (las tiendas escalan). Reemplazar el archivo; el campo `"icon"`
  de `fabric.mod.json` ya está.
- [ ] **Decisión 26.2**. `depends.minecraft` es `~26.1`, que **no** cubre 26.2 (ya disponible).
  Opciones: (a) dejarlo en 26.1.x para 0.1.0 y sacar 0.2.0 para 26.2, o (b) ampliar a `>=26.1 <26.3`
  y probar en 26.2 antes de lanzar. Reflejar la misma decisión en la ficha de la tienda.
- [ ] **CHANGELOG.md**. Crear con la entrada `0.1.0` (resumen de features del MVP). Modrinth pide
  notas por versión; hoy habría que copiarlas a mano desde `progress.md`.
- [ ] **Ficha Modrinth** (mínimo viable; CurseForge opcional en 0.1.0):
  - Nombre "HoloPlace", categoría Utility, client-side, MC 26.1.x, Fabric, requiere Fabric API.
  - Descripción con posicionamiento honesto: *"alternativa liviana y sin dependencias para
    previsualizar y construir desde esquemas `.litematic`"* — **no** "reemplazo de Litematica".
  - Disclaimer visible: **"No afiliado con Litematica ni con Mojang."**
  - Comparación de features solo factual, sin desprestigiar.
  - Marcar el release como `0.1.0` / early access / beta.
- [ ] **1–3 screenshots o un gif** para la ficha: ghost texturizado en el mundo, el panel `K`,
  build-assist con marcadores. (La ficha sin imágenes convierte mal.)

## 2. Recomendable antes de lanzar (no estrictamente bloqueante)

- [ ] **Smoke test de cada feature en una sesión** y pasar los milestones de `progress.md` de
  "needs in-game check" a "verificado" (o anotar los que sigan sin probar). Hoy casi todo dice
  "verificado" pero fue un playtest único del autor.
- [ ] **Compat con shaders**: probar con Sodium + Iris/shaders activos. El ghost usa render types y
  `RenderPipeline` propios; un shader pack puede alterarlo.
- [ ] **Compat con 2–3 mods populares** a la vez (Sodium ya validado). Zona sin probar:
  `MouseHandlerMixin` (scroll) vs otro mod que también intercepte la rueda.
- [ ] **Metadata de `fabric.mod.json`**: añadir `contact.homepage` e `contact.issues` (Modrinth los
  muestra). Opcional: entrypoint de Mod Menu para abrir la pantalla `K` desde ahí.
- [ ] **`build` limpio desde cero** (`./gradlew clean build`) y probar el jar resultante en una
  instancia de Fabric limpia (no el `runClient` de dev).

## 3. Post-lanzamiento inmediato (primeras 1–2 semanas)

- [ ] CurseForge si Modrinth arranca.
- [ ] Responder issues de compat que aparezcan; el `MouseHandlerMixin` es el candidato.
- [ ] Más idiomas solo si la comunidad los pide (ES/EN ya cubren mucho).

## 4. Fuera del primer lanzamiento — 0.2.0+

Priorizado por impacto para un jugador nuevo (de `litematica-reference.md` §8.3):

1. **Easy Place** — 1 clic coloca el bloque correcto del esquema, orientado, sin tenerlo exacto en
   mano. Es el mayor diferenciador de Litematica. **Una sola opción on/off**, no las ~10 de
   Litematica. Nota: es "colocar bloques por ti" — revisar la postura anti-ban que la ficha de
   0.1.0 vaya a comunicar (Litematica Easy Place ha tenido fricción con anticheats).
2. **Info de bloque al mirar** — ampliar el tooltip de M17: al mirar un bloque mal puesto, mostrar
   nombre + orientación esperada, no solo el icono.
3. **Pick-block del esquema** — rueda/tecla saca el bloque correcto. Una opción.
4. **"N bloques mal colocados"** en la salida de `/holoplace materials` (cerrar el círculo del
   verificador sin una GUI aparte).
5. **Carga de malla fuera del hilo de render** para esquemas grandes legítimos (backlog).
6. **26.2** si no entró en 0.1.0.

## 5. Ya resuelto (no repetir)

Topes de tamaño en `LitematicaSchematicReader` (ejes / volumen / nº de regiones), `NbtAccounter`
acotado a 256 MiB, guard de overflow en `LitematicaBitArray`, comentarios que nombraban internals
de Litematica reescritos, `LICENSE` MIT consistente en jar + `fabric.mod.json`, i18n ES/EN completo,
`docs/licensing.md` con la postura legal.
