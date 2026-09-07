# Licensing & clean-room notes

*Not legal advice — but the reasoning is standard and the sources are cited. If a real dispute ever
looms, get a lawyer. This file exists so the project's posture is written down.*

## Question

Does any license stop HoloPlace from being a standalone mod that does what Litematica does?

## Short answer

**No.** Litematica and its library MaLiLib are **LGPL-3.0**. That copyleft license governs *their
source code and works derived from it* — it does not reserve the *idea* of a schematic mod, its
features, its UI concepts, or the `.litematic` file format. An independent implementation that does
not copy their code or assets is not a derivative work and is not bound by their license. HoloPlace
is MIT, has its own parser and renderer, and depends on neither mod.

## What the licenses actually cover

| Project | License (verified via GitHub API + `LICENSE.txt`, 2026-09) | What it binds |
|---|---|---|
| `maruohon/litematica` | LGPL-3.0 (standard, no addendum) | Litematica's own source and forks/derivatives of it |
| `maruohon/malilib` | LGPL-3.0 (standard, no addendum) | MaLiLib's own source and derivatives |
| `sakura-ryoko/litematica` (the active fork) | LGPL-3.0 | same |
| `SmylerMC/litemapy` (Python `.litematic` reader/writer) | GPL-3.0 | its own source |
| **HoloPlace (this repo)** | MIT | this repo's source |

LGPL/GPL are *distribution* licenses. They attach to you only if you **distribute their code** (or a
work that links against / is derived from it). Write your own code and their license is simply not in
the picture.

## Why "similar functionality" is fine

Copyright protects **expression**, not **ideas or functionality**:

- **EU** — *SAS Institute v. World Programming* (CJEU, C-406/10): "the functionality of a computer
  program" and "the programming language and the format of data files used… in order to exploit
  certain of its functions" are **not protected by copyright**. WPL legally rebuilt SAS's behaviour
  in a different language.
- **US** — *Google v. Oracle* (2021): reimplementing an API's structure/behaviour to build a
  competing, compatible system is fair use; functional elements dictated by compatibility are not
  where copyright bites.
- **File formats are not copyrightable.** The `.litematic` layout (GZIP NBT, `Regions`, a
  `{Name, Properties}` palette, a straddling bit-packed `long[]`) has been independently
  re-implemented several times — litemapy (Python), Lite2Edit (Java), and now this mod — which is
  exactly what an open functional spec allows.

So: another schematic-overlay mod with a ghost render, build-assist markers, a material list, area
selection and capture is **not** a copyright problem, as long as the code and art are your own.

## What *would* be a problem (and how this repo avoids it)

| Risk | Status here |
|---|---|
| Copying Litematica / MaLiLib **source** (even short non-trivial snippets) | Not done. Parser written from the documented format (litemapy docs, Lite2Edit) + vanilla `NbtIo`/`NbtUtils`. Renderer is our own against 26.1's `LevelRenderEvents` pipeline. |
| Copying **assets** — textures, GUI layouts, `lang` files | Not done. Own (minimal) HUD, own `en_us`/`es_es` written from scratch. |
| Line-by-line **porting an algorithm** from their source into an MIT repo | Not done. The bit-packing and size-normalisation are dictated by the format itself (they're arithmetic, not authored expression); a few code comments referenced Litematica method *names* for traceability and have been reworded to describe the format instead. |
| **Trademark** — using the name "Litematica", or "Minecraft" in the mod name | Not done. Name is "HoloPlace". Mojang brand guidelines (no "Minecraft" in the mod name, include a "not affiliated with Mojang" disclaimer) — the name already complies; add the disclaimer to the store listing. |
| Implying endorsement by masa / the Litematica project | Don't. The store listing should say "independent, no affiliation", and compare features factually without disparaging. |

## Practical checklist before publishing

- [x] Own license file present and consistent (MIT).
- [x] No Litematica/MaLiLib code or assets in the tree.
- [x] Code comments that named Litematica internals reworded to describe the format
  (`LitematicaSchematicReader`, `LitematicaBitArray`, `PlacementTransform`, `LitematicaSchematicWriter`,
  `CaptureWriter`). Class names keep the format's common name (like any `PngDecoder`); the one
  remaining factual reference — `GhostMesh` noting the format omits the vanilla BE `id`/`x`/`y`/`z` —
  describes the file layout, not their code.
- [ ] Store listing: "not affiliated with Litematica or Mojang", factual feature comparison only.
- [x] This provenance note kept in the repo (alongside `docs/plan.md`'s "written from the spec, not copied").

## Sources

- [github.com/maruohon/litematica — LICENSE.txt (LGPL-3.0)](https://github.com/maruohon/litematica)
- [github.com/maruohon/malilib (LGPL-3.0)](https://github.com/maruohon/malilib)
- [SAS Institute v. World Programming — no copyright in software functionality (RPC)](https://www.rpclegal.com/thinking/ip/no-copyright-in-software-functionality-sas-v-wpl-the-final-chapter/)
- [SAS Institute Inc v World Programming Ltd (Wikipedia)](https://en.wikipedia.org/wiki/SAS_Institute_Inc_v_World_Programming_Ltd)
- [github.com/SmylerMC/litemapy — an independent `.litematic` implementation (GPL-3.0)](https://github.com/SmylerMC/litemapy)
