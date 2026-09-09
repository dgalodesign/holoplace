# Known issues & limitations

Check here before opening a bug — these are known and tracked.

_Last updated for 0.1.0._

## See-through / x-ray with shaders (Iris)

**Limited.** With an Iris shaderpack active, the see-through ghost can still be hidden behind the
world instead of showing through it. Iris manages its own framebuffers and the trick HoloPlace uses
for x-ray (a render pass with no depth attachment) doesn't reliably reach through it.

The **normal ghost** (see-through off) renders correctly with shaders. A proper fix needs Iris-aware
rendering and is planned alongside the MC 26.2 port.

## Very large schematics (4 million+ faces)

Not rendered — there's a hard cap to keep the game from stalling. The HUD says "too large to
render". Use the layer sliders to view a slice, or split the schematic.

## Fabric only

No Forge / NeoForge. Quilt is untested (it may work — reports welcome).

## Build-assist markers only on visible surfaces

By design: a wrong block five layers deep inside solid terrain gets no marker (you can't see or
reach it). The HUD shows `visible/total`, e.g. `30/500 wrong`, so the hidden ones are still counted.

## Placement doesn't snap to a schematic's own origin

The ghost centres on the block you're looking at, not on the schematic's authored origin corner.
This is intentional for now.

---

Something not on this list? Open an issue with `/holoplace debug` output.
