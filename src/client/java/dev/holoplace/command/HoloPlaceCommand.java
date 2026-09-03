package dev.holoplace.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import dev.holoplace.SchematicImport;
import dev.holoplace.SchematicLibrary;
import dev.holoplace.config.HoloPlaceConfig;
import dev.holoplace.placement.PlacementController;
import dev.holoplace.schematic.Schematic;
import dev.holoplace.schematic.LitematicaSchematicReader;
import java.nio.file.Path;
import java.util.List;
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
                                .then(ClientCommands.argument("file", StringArgumentType.greedyString())
                                        .suggests(FILE_SUGGESTIONS)
                                        .executes(HoloPlaceCommand::show)))
                        .then(ClientCommands.literal("hide").executes(ctx -> hide(ctx.getSource())))
                        .then(ClientCommands.literal("reset").executes(ctx -> {
                            PlacementController.get().resetTransform();
                            return 1;
                        }))));
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

    private static int hide(FabricClientCommandSource source) {
        PlacementController.get().stopGrab(false);
        GhostState.get().setVisible(false);
        source.sendFeedback(Component.literal("§7Ghost hidden"));
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
