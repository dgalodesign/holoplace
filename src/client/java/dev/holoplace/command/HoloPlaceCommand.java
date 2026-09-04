package dev.holoplace.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import dev.holoplace.SchematicImport;
import dev.holoplace.SchematicLibrary;
import dev.holoplace.config.HoloPlaceConfig;
import dev.holoplace.config.WorldPlacements;
import dev.holoplace.placement.PlacementController;
import dev.holoplace.schematic.MaterialList;
import dev.holoplace.schematic.PlacementTransform;
import dev.holoplace.schematic.Schematic;
import dev.holoplace.schematic.SchematicRegion;
import dev.holoplace.schematic.LitematicaSchematicReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** {@code /holoplace} — MVP: inspect {@code .litematic} files. Placement/overlay commands land in later milestones. */
public final class HoloPlaceCommand {

    private HoloPlaceCommand() {
    }

    private static final SuggestionProvider<FabricClientCommandSource> FILE_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    SchematicLibrary.list().stream().map(HoloPlaceCommand::bareName).toList(), builder);

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("holoplace")
                        .executes(ctx -> list(ctx.getSource()))
                        .then(ClientCommands.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(ClientCommands.literal("info")
                                .then(ClientCommands.argument("file", StringArgumentType.greedyString())
                                        .suggests(FILE_SUGGESTIONS)
                                        .executes(HoloPlaceCommand::info)))
                        .then(ClientCommands.literal("show")
                                .executes(ctx -> showAgain(ctx.getSource()))
                                .then(ClientCommands.argument("file", StringArgumentType.greedyString())
                                        .suggests(FILE_SUGGESTIONS)
                                        .executes(HoloPlaceCommand::show)))
                        .then(ClientCommands.literal("hide").executes(ctx -> hide(ctx.getSource())))
                        .then(ClientCommands.literal("clear").executes(ctx -> clear(ctx.getSource())))
                        .then(ClientCommands.literal("reset").executes(ctx -> {
                            PlacementController.get().resetTransform();
                            return 1;
                        }))
                        .then(ClientCommands.literal("seethrough").executes(ctx -> {
                            PlacementController.get().toggleSeeThrough();
                            return 1;
                        }))
                        .then(ClientCommands.literal("buildassist").executes(ctx -> {
                            PlacementController.get().toggleBuildAssist();
                            return 1;
                        }))
                        .then(ClientCommands.literal("materials").executes(ctx -> materials(ctx.getSource())))
                        .then(ClientCommands.literal("move")
                                .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                        .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                                .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> {
                                                            PlacementController.get().moveTo(
                                                                    IntegerArgumentType.getInteger(ctx, "x"),
                                                                    IntegerArgumentType.getInteger(ctx, "y"),
                                                                    IntegerArgumentType.getInteger(ctx, "z"));
                                                            return 1;
                                                        })))))
                        .then(ClientCommands.literal("nudge")
                                .then(ClientCommands.argument("dir", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                new String[] {"north", "south", "east", "west", "up", "down"}, b))
                                        .executes(ctx -> nudge(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "dir"), 1))
                                        .then(ClientCommands.argument("amount", IntegerArgumentType.integer(1, 256))
                                                .executes(ctx -> nudge(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "dir"),
                                                        IntegerArgumentType.getInteger(ctx, "amount"))))))
                        .then(ClientCommands.literal("help").executes(ctx -> help(ctx.getSource())))
                        .then(ClientCommands.literal("layers")
                                .then(ClientCommands.literal("off").executes(ctx -> {
                                    PlacementController.get().clearLayers();
                                    return 1;
                                }))
                                .then(ClientCommands.argument("min", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            int v = IntegerArgumentType.getInteger(ctx, "min");
                                            PlacementController.get().setLayers(v, v);
                                            return 1;
                                        })
                                        .then(ClientCommands.argument("max", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    PlacementController.get().setLayers(
                                                            IntegerArgumentType.getInteger(ctx, "min"),
                                                            IntegerArgumentType.getInteger(ctx, "max"));
                                                    return 1;
                                                }))))));
    }

    private static int nudge(FabricClientCommandSource source, String dirName, int amount) {
        net.minecraft.core.Direction dir = net.minecraft.core.Direction.byName(dirName.toLowerCase());
        if (dir == null) {
            source.sendError(Component.literal("Unknown direction '" + dirName + "'"));
            return 0;
        }
        PlacementController.get().nudge(dir, amount);
        return 1;
    }

    private static int help(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("§e§lHoloPlace§r §7— place schematics on screen"));
        source.sendFeedback(Component.literal("§7Drop a §f.litematic§7 on the window, or §fK§7 for the menu."));
        source.sendFeedback(Component.literal("§fKeys:§7 G grab · R/⇧R rotate · M mirror · X x-ray · H build-assist · ⎇wheel opacity"));
        source.sendFeedback(Component.literal("§fCommands:§7 show <f> · hide · show · clear · reset · move <x y z> · nudge <dir> [n]"));
        source.sendFeedback(Component.literal("§7          seethrough · buildassist · materials · layers <min> <max>|off · info <f> · list"));
        return 1;
    }

    private static int list(FabricClientCommandSource source) {
        List<Path> files = SchematicLibrary.list();
        if (files.isEmpty()) {
            source.sendFeedback(Component.literal("No .litematic files found. Put them in "
                    + SchematicLibrary.primaryDir()));
            return 0;
        }
        source.sendFeedback(Component.literal("§e" + files.size() + " schematic(s):"));
        for (Path p : files) {
            source.sendFeedback(Component.literal(" §7- §f" + p.getFileName()));
        }
        return files.size();
    }

    private static int info(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "file");
        Optional<Path> path = SchematicLibrary.resolve(name);
        if (path.isEmpty()) {
            source.sendError(Component.literal("No schematic named '" + name + "'"));
            return 0;
        }
        try {
            Schematic schem = LitematicaSchematicReader.read(path.get());
            Vec3i size = schem.enclosingSize();
            source.sendFeedback(Component.literal("§e" + schem.name() + "§r "
                    + (schem.author().isBlank() ? "" : "§7by " + schem.author())));
            source.sendFeedback(Component.literal(String.format(
                    " §7size §f%d×%d×%d  §7regions §f%d  §7blocks §f%,d§7/§f%,d  §7mcver §f%d  §7fmt §fv%d",
                    size.getX(), size.getY(), size.getZ(),
                    schem.regions().size(), schem.totalNonAirBlocks(), schem.totalVolume(),
                    schem.minecraftDataVersion(), schem.schematicVersion())));
            if (!schem.missingBlocks().isEmpty()) {
                source.sendFeedback(Component.literal("  §c" + schem.missingBlocks().size()
                        + " unknown block id(s): §7" + firstFew(schem.missingBlocks())));
            }
            HoloPlaceClient.LOGGER.info("Inspected {}: {} regions, {} non-air blocks",
                    path.get().getFileName(), schem.regions().size(), schem.totalNonAirBlocks());
            return 1;
        } catch (Exception e) {
            source.sendError(Component.literal("Failed to read: " + e.getMessage()));
            HoloPlaceClient.LOGGER.error("Failed to read {}", path.get(), e);
            return 0;
        }
    }

    private static int show(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "file");
        Optional<Path> path = SchematicLibrary.resolve(name);
        if (path.isEmpty()) {
            source.sendError(Component.literal("No schematic named '" + name + "'"));
            return 0;
        }
        HoloPlaceConfig.get().lastSchematic = path.get().getFileName().toString();
        HoloPlaceConfig.save();
        SchematicImport.show(path.get(), true);
        return 1;
    }

    private static String bareName(Path file) {
        String name = file.getFileName().toString();
        return name.toLowerCase().endsWith(".litematic") ? name.substring(0, name.length() - 10) : name;
    }

    private static final int MATERIALS_SHOWN = 30;

    private static int materials(FabricClientCommandSource source) {
        GhostState ghost = GhostState.get();
        Schematic schematic = ghost.schematic();
        if (schematic == null) {
            source.sendError(Component.literal("No schematic loaded — /holoplace show <file>"));
            return 0;
        }

        Map<net.minecraft.world.item.Item, Integer> placed = placedCounts(source, ghost, schematic);
        List<MaterialList.Entry> totals = MaterialList.totals(schematic);
        List<MaterialList.Entry> remaining = placed == null
                ? totals
                : MaterialList.remaining(schematic, placed);

        int totalBlocks = totals.stream().mapToInt(MaterialList.Entry::count).sum();
        int leftBlocks = remaining.stream().mapToInt(MaterialList.Entry::count).sum();
        source.sendFeedback(Component.literal("§e" + schematic.name() + " §7— §f"
                + totals.size() + "§7 item types, §f" + String.format("%,d", totalBlocks) + "§7 blocks"
                + (placed == null ? "" : "  §a" + String.format("%,d", totalBlocks - leftBlocks)
                        + "§7/§f" + String.format("%,d", totalBlocks) + "§7 placed")));

        Map<net.minecraft.world.item.Item, Integer> totalByItem = new HashMap<>();
        totals.forEach(e -> totalByItem.put(e.item(), e.count()));

        List<MaterialList.Entry> rows = placed == null ? totals : remaining;
        int shown = Math.min(rows.size(), MATERIALS_SHOWN);
        for (int i = 0; i < shown; i++) {
            MaterialList.Entry e = rows.get(i);
            String name = new net.minecraft.world.item.ItemStack(e.item()).getHoverName().getString();
            if (placed == null) {
                source.sendFeedback(Component.literal(" §f" + String.format("%,d", e.count()) + "§7× §f" + name));
            } else {
                int total = totalByItem.getOrDefault(e.item(), e.count());
                source.sendFeedback(Component.literal(" §c" + String.format("%,d", e.count())
                        + "§7 left §8/ " + String.format("%,d", total) + " §7 §f" + name));
            }
        }
        if (rows.size() > shown) {
            source.sendFeedback(Component.literal(" §8… " + (rows.size() - shown) + " more"));
        }
        return 1;
    }

    private static Map<net.minecraft.world.item.Item, Integer> placedCounts(
            FabricClientCommandSource source, GhostState ghost, Schematic schematic) {
        var level = source.getLevel();
        PlacementTransform transform = ghost.transform();
        if (level == null || transform == null || !ghost.isVisible()) {
            return null;
        }
        var anchor = ghost.anchor();
        int schMinX = schematic.min().getX();
        int schMinY = schematic.min().getY();
        int schMinZ = schematic.min().getZ();
        Map<net.minecraft.world.item.Item, Integer> placed = new HashMap<>();
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos();

        for (SchematicRegion region : schematic.regions()) {
            int baseX = region.minCorner().getX() - schMinX;
            int baseY = region.minCorner().getY() - schMinY;
            int baseZ = region.minCorner().getZ() - schMinZ;
            for (int y = 0; y < region.sizeY(); y++) {
                for (int z = 0; z < region.sizeZ(); z++) {
                    for (int x = 0; x < region.sizeX(); x++) {
                        var raw = region.getBlockState(x, y, z);
                        net.minecraft.world.item.Item item = MaterialList.itemFor(raw);
                        if (item == null) {
                            continue;
                        }
                        int[] f = transform.forward(baseX + x, baseY + y, baseZ + z);
                        pos.set(anchor.getX() + f[0], anchor.getY() + f[1], anchor.getZ() + f[2]);
                        if (ghost.matches(level.getBlockState(pos), transform.applyToState(raw))) {
                            placed.merge(item, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        return placed;
    }

    /** Stop drawing the ghost but keep it placed — {@code /holoplace show} brings it back, and it
     *  still restores on rejoin. Use {@code /holoplace clear} to forget it entirely. */
    private static int hide(FabricClientCommandSource source) {
        GhostState ghost = GhostState.get();
        if (ghost.schematic() == null) {
            source.sendError(Component.literal("Nothing to hide"));
            return 0;
        }
        PlacementController.get().stopGrab(false);
        ghost.setVisible(false);
        WorldPlacements.saveCurrent();
        source.sendFeedback(Component.literal("§7Ghost hidden §8(/holoplace show to bring it back)"));
        return 1;
    }

    private static int showAgain(FabricClientCommandSource source) {
        GhostState ghost = GhostState.get();
        if (ghost.schematic() == null) {
            return list(source);
        }
        ghost.setVisible(true);
        WorldPlacements.saveCurrent();
        source.sendFeedback(Component.literal("§aShowing §e" + ghost.sourceName()));
        return 1;
    }

    /** Forget the placement entirely: no ghost, nothing restored on rejoin. */
    private static int clear(FabricClientCommandSource source) {
        PlacementController.get().stopGrab(false);
        GhostState.get().setSchematic(null, null);
        GhostState.get().setVisible(false);
        WorldPlacements.clearCurrent();
        source.sendFeedback(Component.literal("§7Placement cleared"));
        return 1;
    }

    private static String firstFew(Iterable<Identifier> ids) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Identifier id : ids) {
            if (n++ > 0) {
                sb.append(", ");
            }
            sb.append(id);
            if (n == 5) {
                sb.append(", …");
                break;
            }
        }
        return sb.toString();
    }
}
