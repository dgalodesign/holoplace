# Backlog

Ideas deliberately deferred from the MVP. Not scheduled — pick up if/when there's a real need.

## GPU vertex-buffer upload

**Problem**: the ghost's block quads are re-emitted into the frame's vertex buffer every frame
(`VertexConsumer.putBlockBakedQuad` per quad in `GhostRenderer.render`). `GhostMesh` already bakes
the geometry once (no re-tesselation), but the per-frame vertex *write* is still O(quads). Fine up to
the current 4M-quad cap; a very large schematic (megabuild-sized) would visibly cost frame time.

**Fix**: upload the baked mesh to a persistent `GpuBuffer` once (per mesh build) and draw it each
frame with a `RenderPass` (`setPipeline` / `setVertexBuffer` / `drawIndexed`), the way vanilla's
`ChunkSectionsToRender` and litematica's `WorldRendererSchematic` do — instead of walking a `List` and
calling `addVertex` per vertex every frame.

**Why deferred**: real GPU-command-queue work on 26.1's newly-rewritten rendering pipeline
(`RenderPipeline` / `RenderPass` / `GpuBuffer` / `DynamicTransforms` uniforms) — sparse docs, harder to
debug blind, meaningfully riskier than everything shipped so far. See [[holoplace-render-gotchas]]
(session memory) for the API notes gathered while scoping this.

**Trigger to revisit**: a schematic that visibly stutters to load/rotate, or hits the 4M-quad cap.

## Other deferred ideas (from the original plan, still not done)

- Multi-schematic / layering (place more than one at once).
- WorldEdit integration.
- Schematic version history.
