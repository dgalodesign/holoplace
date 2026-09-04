package dev.holoplace.render;

import dev.holoplace.HoloPlaceClient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Best-effort extraction of a {@link RenderType}'s primary texture, so an opaque BER render type can
 * be swapped for a translucent one on the same atlas. Reflective (the texture map is package-private)
 * and cached; returns {@code null} if the layout changes.
 */
final class RenderTypeTextures {

    private static final Map<RenderType, Optional<Identifier>> CACHE = new ConcurrentHashMap<>();

    private static @Nullable Field stateField;
    private static @Nullable Field texturesField;
    private static @Nullable Method locationMethod;
    private static boolean reflectionFailed;

    private RenderTypeTextures() {
    }

    static @Nullable Identifier of(RenderType renderType) {
        return CACHE.computeIfAbsent(renderType, RenderTypeTextures::extract).orElse(null);
    }

    private static Optional<Identifier> extract(RenderType renderType) {
        if (reflectionFailed && stateField == null) {
            return Optional.empty();
        }
        try {
            if (stateField == null) {
                stateField = RenderType.class.getDeclaredField("state");
                stateField.setAccessible(true);
            }
            Object setup = stateField.get(renderType);
            if (texturesField == null) {
                texturesField = setup.getClass().getDeclaredField("textures");
                texturesField.setAccessible(true);
            }
            Object texturesObj = texturesField.get(setup);
            if (!(texturesObj instanceof Map<?, ?> map) || map.isEmpty()) {
                return Optional.empty();
            }
            Object binding = firstNonSampler(map);
            if (binding == null) {
                return Optional.empty();
            }
            if (locationMethod == null) {
                locationMethod = binding.getClass().getDeclaredMethod("location");
                locationMethod.setAccessible(true);
            }
            Object loc = locationMethod.invoke(binding);
            return loc instanceof Identifier id ? Optional.of(id) : Optional.empty();
        } catch (Exception e) {
            if (!reflectionFailed) {
                reflectionFailed = true;
                HoloPlaceClient.LOGGER.debug("RenderType texture reflection unavailable", e);
            }
            return Optional.empty();
        }
    }

    private static @Nullable Object firstNonSampler(Map<?, ?> textures) {
        Object first = null;
        for (Map.Entry<?, ?> e : textures.entrySet()) {
            if ("Sampler0".equals(e.getKey())) {
                return e.getValue();
            }
            if (first == null) {
                first = e.getValue();
            }
        }
        if (first == null) {
            Collection<?> values = textures.values();
            return values.isEmpty() ? null : values.iterator().next();
        }
        return first;
    }
}
