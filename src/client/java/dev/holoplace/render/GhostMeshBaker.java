package dev.holoplace.render;

import dev.holoplace.HoloPlaceClient;
import dev.holoplace.schematic.PlacementTransform;
import dev.holoplace.schematic.Schematic;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Bakes the {@link GhostMesh} for a (schematic, rotation, mirror) off the render thread. The heavy
 * region walk ({@link GhostMesh#bakeGeometry}) runs on a single daemon worker; when it finishes the
 * cheap object construction ({@link GhostMesh.Geometry#assemble}) is done on the render thread, which
 * calls {@link #poll} once per frame. While a bake is in flight {@code poll} returns {@code null} and
 * the renderer shows a footprint outline instead of hitching.
 */
final class GhostMeshBaker {

    private static final Executor EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "holoplace-mesh-baker");
        t.setDaemon(true);
        // A frame must never wait on this; let real game threads win the CPU.
        t.setPriority(Thread.NORM_PRIORITY - 2);
        return t;
    });

    private static @Nullable GhostMesh ready;
    private static @Nullable CompletableFuture<GhostMesh.Geometry> pending;
    private static @Nullable Schematic pendingSchematic;
    private static @Nullable PlacementTransform pendingTransform;

    private GhostMeshBaker() {
    }

    /**
     * The baked mesh for this (schematic, transform), or {@code null} while it bakes. Kicks off a
     * bake if none is ready or in flight for this key. Render thread only.
     */
    static @Nullable GhostMesh poll(Schematic schematic, PlacementTransform transform) {
        if (ready != null && ready.matches(schematic, transform)) {
            return ready;
        }
        if (pending == null || pendingSchematic != schematic || !transform.equals(pendingTransform)) {
            pendingSchematic = schematic;
            pendingTransform = transform;
            long start = System.nanoTime();
            pending = CompletableFuture
                    .supplyAsync(() -> GhostMesh.bakeGeometry(schematic, transform), EXEC)
                    .whenComplete((geom, err) -> {
                        if (err != null) {
                            HoloPlaceClient.LOGGER.error("Ghost mesh bake failed", err);
                        } else {
                            HoloPlaceClient.LOGGER.debug("Ghost geometry baked: {} quads in {} ms",
                                    geom.totalQuads(), (System.nanoTime() - start) / 1_000_000);
                        }
                    });
        }
        if (!pending.isDone()) {
            return null;
        }

        GhostMesh.Geometry geom = pending.getNow(null);
        pending = null;
        if (geom == null) {
            return null; // bake threw; poll again next frame and it will restart
        }
        var registries = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.registryAccess() : null;
        try {
            ready = geom.assemble(registries);
        } catch (Exception e) {
            HoloPlaceClient.LOGGER.error("Ghost mesh assembly failed", e);
            return null;
        }
        return ready;
    }

    /** Drop the ready mesh and any in-flight bake (schematic cleared, world change). */
    static void invalidate() {
        ready = null;
        pending = null;
        pendingSchematic = null;
        pendingTransform = null;
    }
}
