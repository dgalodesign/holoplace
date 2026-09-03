package dev.holoplace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Locates {@code .litematic} files. Reads from HoloPlace's own folder
 * ({@code config/holoplace/schematics/}) and, as a convenience, the conventional
 * {@code <gamedir>/schematics/} folder that other schematic mods use.
 */
public final class SchematicLibrary {

    private SchematicLibrary() {
    }

    public static Path primaryDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("holoplace").resolve("schematics");
    }

    private static List<Path> searchDirs() {
        List<Path> dirs = new ArrayList<>();
        dirs.add(primaryDir());
        dirs.add(FabricLoader.getInstance().getGameDir().resolve("schematics"));
        return dirs;
    }

    public static void ensurePrimaryDir() {
        try {
            Files.createDirectories(primaryDir());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** All discovered {@code .litematic} files, de-duplicated by file name, newest first. */
    public static List<Path> list() {
        LinkedHashSet<Path> out = new LinkedHashSet<>();
        Set<String> seenNames = new LinkedHashSet<>();
        for (Path dir : searchDirs()) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".litematic"))
                        .sorted(Comparator.comparingLong(SchematicLibrary::lastModified).reversed())
                        .forEach(p -> {
                            if (seenNames.add(p.getFileName().toString())) {
                                out.add(p);
                            }
                        });
            } catch (IOException e) {
                HoloPlaceClient.LOGGER.warn("Could not list schematics in {}", dir, e);
            }
        }
        return new ArrayList<>(out);
    }

    /** Resolve a user-supplied name (with or without the {@code .litematic} suffix) to a file. */
    public static Optional<Path> resolve(String name) {
        String needle = name.toLowerCase();
        if (!needle.endsWith(".litematic")) {
            needle = needle + ".litematic";
        }
        for (Path p : list()) {
            if (p.getFileName().toString().equalsIgnoreCase(needle)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
