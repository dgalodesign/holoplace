package dev.holoplace;

import dev.holoplace.render.GhostRenderer;
import dev.holoplace.schematic.Schematic;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;

/**
 * The block of environment + state that {@code /holoplace debug} prints and the crash report carries.
 * Everything here is best-effort — any lookup that throws is caught and reported as {@code ?} so the
 * block is always produced.
 */
public final class Diagnostics {

    /** Mod ids that most often interact with HoloPlace (rendering, input, scroll). Reported with
     *  their version when present; anything else is left out to keep the block short. */
    private static final String[] WATCH = {
            "sodium", "sodium-extra", "iris", "indium", "canvas", "immediatelyfast", "moreculling",
            "entityculling", "c2me", "lithium", "scalablelux", "voxy", "distanthorizons", "nvidium",
            "litematica", "malilib", "worldedit", "worldeditcui",
            "fabric-language-kotlin", "modmenu", "optifabric",
            "zoomify", "ok_zoomer", "logical_zoom", "wi_zoom", "just_zoom", "shouldersurfing",
            "scrollwalk", "controlify",
    };

    private Diagnostics() {
    }

    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        FabricLoader fl = FabricLoader.getInstance();

        out.add("HoloPlace " + version(fl, "holoplace")
                + "  ·  MC " + mcVersion()
                + "  ·  Fabric Loader " + version(fl, "fabricloader")
                + "  ·  Fabric API " + version(fl, "fabric-api"));
        out.add("Java " + prop("java.version") + "  ·  OS " + prop("os.name") + " " + prop("os.version")
                + "  ·  " + gpu());
        out.add("Renderer mods: " + watchList(fl));

        Schematic s = GhostState.get().schematic();
        if (s == null) {
            out.add("Ghost: none loaded");
        } else {
            GhostState g = GhostState.get();
            Vec3i sz = s.enclosingSize();
            out.add("Ghost: '" + GhostState.get().sourceName() + "'  "
                    + sz.getX() + "x" + sz.getY() + "x" + sz.getZ()
                    + "  ·  " + s.totalNonAirBlocks() + " blocks"
                    + "  ·  " + s.regions().size() + " region(s)"
                    + "  ·  fmt v" + s.schematicVersion()
                    + (s.missingBlocks().isEmpty() ? "" : "  ·  " + s.missingBlocks().size() + " unknown block id(s)"));
            out.add("  " + renderStatus());
            out.add("  assist=" + g.hideMatched() + " seeThrough=" + g.seeThrough()
                    + " matchBlockOnly=" + g.matchBlockOnly() + " visible=" + g.isVisible());
        }
        return out;
    }

    /** Single string for the crash report / chat. */
    public static String block() {
        return String.join("\n", lines());
    }

    private static String mcVersion() {
        try {
            return SharedConstants.getCurrentVersion().name();
        } catch (Throwable t) {
            try {
                return Minecraft.getInstance().getLaunchedVersion();
            } catch (Throwable t2) {
                return "?";
            }
        }
    }

    private static String version(FabricLoader fl, String id) {
        return fl.getModContainer(id)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("absent");
    }

    private static String prop(String key) {
        try {
            return System.getProperty(key, "?");
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String gpu() {
        try {
            var d = com.mojang.blaze3d.systems.RenderSystem.tryGetDevice();
            if (d == null) {
                return "GPU: <no device>";
            }
            return "GPU: " + d.getRenderer() + " (" + d.getVendor() + ", " + d.getVersion() + ")";
        } catch (Throwable t) {
            return "GPU: ?";
        }
    }

    private static String watchList(FabricLoader fl) {
        TreeMap<String, String> present = new TreeMap<>();
        for (String id : WATCH) {
            fl.getModContainer(id).ifPresent(c ->
                    present.put(id, c.getMetadata().getVersion().getFriendlyString()));
        }
        if (present.isEmpty()) {
            return "(none of the usual)";
        }
        StringBuilder sb = new StringBuilder();
        present.forEach((id, v) -> {
            sb.append(sb.isEmpty() ? "" : ", ").append(id).append(' ').append(v);
            if (id.equals("iris")) {
                sb.append(" [shaders: ").append(irisShaders()).append(']');
            }
        });
        return sb.toString();
    }

    /** Whether an Iris shaderpack is actually active, via Iris's stable v0 API. */
    private static String irisShaders() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            Object on = api.getMethod("isShaderPackInUse").invoke(inst);
            return Boolean.TRUE.equals(on) ? "ON" : "off";
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String renderStatus() {
        try {
            return GhostRenderer.debugStatus();
        } catch (Throwable t) {
            return "render status: ? (" + t.getClass().getSimpleName() + ")";
        }
    }
}
