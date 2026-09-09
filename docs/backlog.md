# Backlog

Ideas deliberately deferred from the MVP. Not scheduled — pick up if/when there's a real need.

## GPU vertex-buffer upload — DONE (2026-09-09, `GhostGpuMesh`)

The ghost's block quads are baked into a persistent `GpuBuffer` and redrawn each frame with one
`drawIndexed` (manual `RenderPass`, patterned on vanilla `WorldBorderRenderer` — local coords in the
buffer, camera-relative offset in `DynamicTransforms.ModelOffset`, which `core/block.vsh` applies).
Rebuilt only when the content key changes (mesh identity, opacity, build-assist cull, layer slice,
coarse anchor for biome tint). `GhostRenderer.renderBlocksImmediate` stays as a fallback that engages
if the GPU path ever throws (`gpuUnavailable` latch, logged once).

Triggered by a 23k-block statue (`estatua-thor.litematic`) dropping to ~20 FPS — the per-frame
`putBlockBakedQuad` loop over ~200k quads was the bottleneck.

Not yet done: per-frame translucent re-sort (uses the shared sequential index buffer, so overlapping
translucent faces can draw slightly out of order — minor at the ghost's opacity).

## Other deferred ideas (from the original plan, still not done)

- Multi-schematic / layering (place more than one at once).
- WorldEdit integration.
- Schematic version history.
