# HoloPlace — plan del primer lanzamiento (0.1.0)

Estado a 2026-09-07. Decisiones tomadas con el usuario en esta fecha:

- **Alcance**: 0.1.0 NO es un MVP mínimo. Se meten dentro las mejoras que estaban en "0.2.0"
  (Easy Place, asistencia al construir, rendimiento) para que el mod sea **relevante** de salida.
- **Versión de MC**: portar a **26.2** (la última estable) y soltar 26.1.x.
- **UI/GUI primero**: un pase de rediseño de la pantalla `K` / HUD antes de tocar features nuevas.
- **Repo**: público, open source MIT (ver §1).
- Los hallazgos de código de la auditoría (topes de tamaño, `NbtAccounter`, overflow guard) están
  **hechos**. `CHANGELOG.md` creado con sección `[Unreleased]`.

---

## 0. Secuencia de trabajo (milestones)

| # | Milestone | Contenido | Esfuerzo |
|---|---|---|---|
| **M24** | Pase de UI/GUI | Rediseñar la pantalla `K` (jerarquía, agrupación, iconos, quizá secciones); pulir HUD, tooltip y marcadores; decidir qué se ve y qué no | medio |
| **M25** | Easy Place | 1 clic = bloque correcto del esquema, orientado, sin tenerlo exacto en mano. **Un solo toggle** | medio-alto |
| **M26** | Paquete "asistencia" | Info de bloque al mirar (nombre + orientación esperada) · pick-block del esquema (rueda saca el bloque correcto) · "N bloques mal colocados" en `/holoplace materials` | bajo-medio |
| **M27** | Malla off-thread | Mover `GhostMesh.build` a un hilo de trabajo con placeholder mientras carga; quita el hitch de esquemas grandes | medio |
| **M28** | Port a 26.2 | Subir `minecraft_version` / `fabric_api_version` / Loom, `genSources`, arreglar API rota, re-test. Soltar 26.1 | medio, incierto |
| **M29** | Pre-lanzamiento | §3 de este doc (smoke test, shaders, mods, jar real, metadata) | bajo |
| **—** | Lanzamiento | Ficha Modrinth + release 0.1.0 (§4) | bajo |

---

## 1. Repo público — por qué (y alternativa)

**No es técnicamente obligatorio.** Modrinth/CurseForge no exigen código fuente. Pero:

- `fabric.mod.json` declara `contact.sources = github.com/edgardgalof/holoplace`. Si no existe o es
  privado, es un enlace roto en Mod Menu → mala señal.
- Declarar licencia **MIT** con el repo cerrado es incoherente (MIT solo tiene efecto sobre quien
  recibe el código). Si va a ser closed-source, la licencia debería ser "all rights reserved".
- Para un mod nuevo que compite con Litematica (que es open source LGPL-3.0), **ser open source MIT
  es un argumento de venta**: "liviano, sin dependencias, y abierto". También da confianza
  (la gente ve que no hay nada raro en el jar).

**Decisión**: público, MIT. Pasos: crear el repo en GitHub, `git remote add origin …`,
`git push -u origin main`. Confirmar que es público y trae `LICENSE` + `README` + `docs/`.

Si se cambiara de idea → licencia "all rights reserved" en `fabric.mod.json`, quitar
`contact.sources`, y deja de ser bloqueante.

---

## 2. Ícono

- **Formato**: PNG con transparencia.
- **Tamaño**: 512×512 para las tiendas (Modrinth y CurseForge lo reescalan). El del mod
  (`assets/holoplace/icon.png`) puede ser el mismo a 256 o 128.
- **Legibilidad**: se muestra a ~32×32 en las listas de mods. Silueta clara, pocos colores,
  contraste alto.
- **Estilo**: un martillo pixel-art encaja. Para coherencia con "HoloPlace" (holograma / blueprint):
  martillo cian o blanco sobre fondo azul oscuro, opcionalmente con efecto holográfico
  (líneas de escaneo, glow tenue).
- El usuario lo hace. Al tenerlo: reemplazar `assets/holoplace/icon.png` y subir el 512 a la ficha.

---

## 3. Pre-lanzamiento — detalle de cada check (M29)

### 3.1 Smoke test completo + actualizar `progress.md`
Una sesión de juego ejercitando **cada** feature una vez, de forma deliberada, con la build final:
cargar esquema → arrastrar → rotar → espejar → opacidad → ver-a-través → build-assist con bloques
bien / mal / extra → tooltip al mirar → lista de materiales → capturar un área y recargarla →
capas → salir y reconectar (persistencia) → soltar un archivo en la ventana.
**Por qué**: `progress.md` dice "verificado" en casi todo, pero fue verificación incremental
durante el desarrollo, no un pase completo con la build 0.1.0. Los bugs de integración
(una feature rompió otra) se escapan así. Nada visual es testeable con los 18 unit tests.
**Salida**: marcar en `progress.md` qué pasó el pase y qué no.

### 3.2 Compatibilidad con shaders (Iris)
Sodium + Iris + un shaderpack (Complementary / BSL), shaders activos, comprobar que el ghost
sigue viéndose: translúcido, con color de bioma, sin z-fighting agresivo, sin desaparecer.
**Por qué**: el ghost usa render types y un `RenderPipeline` propio (`GhostPipelines`). Iris
reemplaza los shaders del juego y puede ignorar o romper pipelines custom — es la incompatibilidad
más probable y la más reportada (Litematica la ha sufrido).
**Si rompe**: no bloquea el lanzamiento; se documenta "shaders: soporte limitado" y se abre issue.

### 3.3 Compatibilidad con otros mods
Instalar 2–3 mods populares a la vez (Litematica misma, un mod de zoom con scroll, WorldEdit CUI)
y jugar un rato.
**Por qué específico**: el único mixin invasivo es `MouseHandlerMixin` — intercepta el scroll con
`cancellable=true` en `HEAD`. Cualquier otro mod que también capture la rueda (zoom, otro asistente)
es un conflicto potencial de "quién consume el evento". Sodium ya se validó; el resto no.

### 3.4 Metadata de `fabric.mod.json`
- Añadir `contact.homepage` (ficha Modrinth) e `contact.issues` (GitHub issues) — Mod Menu los
  muestra como botones.
- `authors`: nombre visible, no solo el handle.
- Revisar `description` (Mod Menu la trunca si es muy larga).
- Opcional: entrypoint `modmenu` para que el botón de config de Mod Menu abra la pantalla `K`.

### 3.5 Build limpio + probar el jar real
`./gradlew clean build` → coger `build/libs/holoplace-0.1.0.jar` (**no** el `-sources.jar`) →
instalarlo en una instancia de **producción** (launcher oficial o Prism con Fabric + Fabric API),
no en el `runClient` de desarrollo.
**Por qué**: `runClient` usa el classpath de dev sin remapear y con los sources cargados. El jar
real pasa por el `remapJar` de Loom y corre en otro entorno. Cosas que compilan y corren en dev
pueden fallar empaquetadas (refmap de mixins, recursos no incluidos, etc.).

---

## 4. Publicar en Modrinth — paso a paso

1. Cuenta en modrinth.com (GitHub o email).
2. **Create a project** → tipo **Mod**, loader **Fabric**, entorno **Client** (server: unsupported).
3. Slug `holoplace`, nombre `HoloPlace`, summary de una frase.
4. Descripción (markdown): la larga — features, cómo se usa, **disclaimer "no afiliado con
   Litematica ni Mojang"**, screenshots, posicionamiento honesto ("alternativa liviana sin
   dependencias", no "reemplazo de Litematica").
5. Licencia: MIT. Categorías: Utility, Game Mechanics. Links: source + issues.
6. Subir el ícono 512×512.
7. El proyecto entra en **cola de moderación de Modrinth** (< 48 h normalmente); no es público
   hasta que lo aprueban.
8. Crear una **Version**: subir `holoplace-0.1.0.jar`, número `0.1.0`, changelog (de
   `CHANGELOG.md`), MC `26.2`, loader Fabric, **dependencia Fabric API (required)**, canal
   **Release** (o Beta).
9. Publicar.

**Opcional**: el plugin Gradle `com.modrinth.minotaur` publica con `./gradlew modrinth` + un token
API — vale la pena si vas a sacar varias versiones.

CurseForge: proceso parecido pero moderación más lenta y tediosa — dejarlo para después de 0.1.0.

---

## 5. Ya resuelto (no repetir)

Topes de tamaño en `LitematicaSchematicReader` (ejes / volumen / nº de regiones), `NbtAccounter`
acotado a 256 MiB, guard de overflow en `LitematicaBitArray`, comentarios que nombraban internals
de Litematica reescritos, `LICENSE` MIT consistente en jar + `fabric.mod.json`, i18n ES/EN completo,
`docs/licensing.md`, `CHANGELOG.md` con `[Unreleased]`.
