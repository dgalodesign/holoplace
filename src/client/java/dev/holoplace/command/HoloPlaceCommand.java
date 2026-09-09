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
                        .then(ClientCommands.literal("capture")
                                .executes(ctx -> {
                                    dev.holoplace.capture.CaptureController.get().toggleSelecting();
                                    return 1;
                                })
                                .then(ClientCommands.literal("pos1").executes(ctx -> {
                                    dev.holoplace.capture.CaptureController.get().setCornerFromLook(true);
                                    return 1;
                                }))
                                .then(ClientCommands.literal("pos2").executes(ctx -> {
                                    dev.holoplace.capture.CaptureController.get().setCornerFromLook(false);
                                    return 1;
                                }))
                                .then(ClientCommands.literal("clear").executes(ctx -> {
                                    dev.holoplace.capture.CaptureController.get().clearSelection();
                                    return 1;
                                }))
                                .then(ClientCommands.literal("save")
                                        .executes(ctx -> {
                                            dev.holoplace.capture.CaptureController.get().save(null);
                                            return 1;
                                        })
                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    dev.holoplace.capture.CaptureController.get().save(
                                                            StringArgumentType.getString(ctx, "name"));
                                                    return 1;
                                                }))))
                        .then(ClientCommands.literal("help").executes(ctx -> help(ctx.getSource())))
                        .then(ClientCommands.literal("debug").executes(ctx -> debug(ctx.getSource())))
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

    /** Print the diagnostics block to chat and the log — the thing to paste into a bug report. */
    private static int debug(FabricClientCommandSource source) {
        List<String> lines = dev.holoplace.Diagnostics.lines();
        source.sendFeedback(Component.literal("§e§l[HoloPlace] debug §7— copy this into a bug report:"));
        for (String line : lines) {
            source.sendFeedback(Component.literal("§7" + line));
        }
        HoloPlaceClient.LOGGER.info("=== /holoplace debug ===\n{}", String.join("\n", lines));
        source.sendFeedback(Component.literal("§8(also written to the log — latest.log)"));
        return 1;
    }

    private static int nudge(FabricClientCommandSource source, String dirName, int amount) {
        net.minecraft.core.Direction dir = net.minecraft.core.Direction.byName(dirName.toLowerCase());
        if (dir == null) {
            source.sendError(Component.translatable("holoplace.cmd.unknown_direction", dirName));
            return 0;
        }
        PlacementController.get().nudge(dir, amount);
        return 1;
    }

    private static int help(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("holoplace.cmd.help.title"));
        source.sendFeedback(Component.translatable("holoplace.cmd.help.drop"));
        source.sendFeedback(Component.translatable("holoplace.cmd.help.keys"));
        source.sendFeedback(Component.translatable("holoplace.cmd.help.commands1"));
        source.sendFeedback(Component.translatable("holoplace.cmd.help.commands2"));
        return 1;
    }

    private static int list(FabricClientCommandSource source) {
        List<Path> files = SchematicLibrary.list();
        if (files.isEmpty()) {
            source.sendFeedback(Component.translatable("holoplace.cmd.list.none", SchematicLibrary.primaryDir()));
            return 0;
        }
        source.sendFeedback(Component.translatable("holoplace.cmd.list.count", files.size()));
        for (Path p : files) {
            source.sendFeedback(Component.translatable("holoplace.cmd.list.entry", p.getFileName()));
        }
        return files.size();
    }

    private static int info(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "file");
        Optional<Path> path = SchematicLibrary.resolve(name);
        if (path.isEmpty()) {
            source.sendError(Component.translatable("holoplace.cmd.no_schematic", name));
            return 0;
        }
        try {
            Schematic schem = LitematicaSchematicReader.read(path.get());
            Vec3i size = schem.enclosingSize();
            source.sendFeedback(Component.literal("§e" + schem.name() + "§r "
                    + (schem.author().isBlank() ? "" : "§7" + text("holoplace.cmd.info.by", schem.author()))));
            source.sendFeedback(Component.literal(String.format(
                    " §7%s §f%d×%d×%d  §7%s §f%d  §7%s §f%s§7/§f%s  §7%s §f%d  §7%s §fv%d",
                    text("holoplace.cmd.info.size"), size.getX(), size.getY(), size.getZ(),
                    text("holoplace.cmd.info.regions"), schem.regions().size(),
                    text("holoplace.cmd.info.blocks"),
                    String.format("%,d", schem.totalNonAirBlocks()), String.format("%,d", schem.totalVolume()),
                    text("holoplace.cmd.info.mcver"), schem.minecraftDataVersion(),
                    text("holoplace.cmd.info.fmt"), schem.schematicVersion())));
            if (!schem.missingBlocks().isEmpty()) {
                source.sendFeedback(Component.translatable("holoplace.cmd.info.unknown_blocks",
                        schem.missingBlocks().size(), firstFew(schem.missingBlocks())));
            }
            HoloPlaceClient.LOGGER.info("Inspected {}: {} regions, {} non-air blocks",
                    path.get().getFileName(), schem.regions().size(), schem.totalNonAirBlocks());
            return 1;
        } catch (Exception e) {
            source.sendError(Component.translatable("holoplace.cmd.info.failed", e.getMessage()));
            HoloPlaceClient.LOGGER.error("Failed to read {}", path.get(), e);
            return 0;
        }
    }

    private static int show(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "file");
        Optional<Path> path = SchematicLibrary.resolve(name);
        if (path.isEmpty()) {
            source.sendError(Component.translatable("holoplace.cmd.no_schematic", name));
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
            source.sendError(Component.translatable("holoplace.cmd.materials.none"));
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
                + totals.size() + text("holoplace.cmd.materials.item_types") + ", §f"
                + String.format("%,d", totalBlocks) + text("holoplace.cmd.materials.blocks")
                + (placed == null ? "" : "  §a" + String.format("%,d", totalBlocks - leftBlocks)
                        + "§7/§f" + String.format("%,d", totalBlocks) + text("holoplace.cmd.materials.placed"))));

        Map<net.minecraft.world.item.Item, Integer> totalByItem = new HashMap<>();
        totals.forEach(e -> totalByItem.put(e.item(), e.count()));

        List<MaterialList.Entry> rows = placed == null ? totals : remaining;
        int shown = Math.min(rows.size(), MATERIALS_SHOWN);
        for (int i = 0; i < shown; i++) {
            MaterialList.Entry e = rows.get(i);
            String name = new net.minecraft.world.item.ItemStack(e.item()).getHoverName().getString();
            if (placed == null) {
                source.sendFeedback(Component.translatable("holoplace.cmd.materials.row_need",
                        String.format("%,d", e.count()), name));
            } else {
                int total = totalByItem.getOrDefault(e.item(), e.count());
                source.sendFeedback(Component.translatable("holoplace.cmd.materials.row_left",
                        String.format("%,d", e.count()), String.format("%,d", total), name));
            }
        }
        if (rows.size() > shown) {
            source.sendFeedback(Component.translatable("holoplace.cmd.materials.more", rows.size() - shown));
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
            source.sendError(Component.translatable("holoplace.cmd.hide.nothing"));
            return 0;
        }
        PlacementController.get().stopGrab(false);
        ghost.setVisible(false);
        WorldPlacements.saveCurrent();
        source.sendFeedback(Component.translatable("holoplace.cmd.hide.done"));
        return 1;
    }

    private static int showAgain(FabricClientCommandSource source) {
        GhostState ghost = GhostState.get();
        if (ghost.schematic() == null) {
            return list(source);
        }
        ghost.setVisible(true);
        WorldPlacements.saveCurrent();
        source.sendFeedback(Component.translatable("holoplace.cmd.show.done", ghost.sourceName()));
        return 1;
    }

    /** Forget the placement entirely: no ghost, nothing restored on rejoin. */
    private static int clear(FabricClientCommandSource source) {
        PlacementController.get().stopGrab(false);
        GhostState.get().setSchematic(null, null);
        GhostState.get().setVisible(false);
        WorldPlacements.clearCurrent();
        source.sendFeedback(Component.translatable("holoplace.cmd.clear.done"));
        return 1;
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
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
