package dev.holoplace.render;

import dev.holoplace.GhostState;
import dev.holoplace.HoloPlaceClient;
import dev.holoplace.schematic.MaterialList;
import dev.holoplace.schematic.PlacementTransform;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * While build-assist is on, looking at a block inside the schematic's footprint that doesn't match
 * the plan shows a small "should be" / "doesn't belong" hint near the crosshair, with the game's own
 * item icon for the correct block.
 */
public final class GhostTooltipHud {

    private static final int ICON_SIZE = 16;

    private GhostTooltipHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(HoloPlaceClient.id("tooltip"), (graphics, deltaTracker) -> render(graphics));
    }

    private static void render(GuiGraphicsExtractor graphics) {
        GhostState state = GhostState.get();
        if (!state.isVisible() || !state.hideMatched()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.level == null) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        PlacementTransform transform = state.transform();
        if (transform == null) {
            return;
        }

        BlockPos target = hit.getBlockPos();
        BlockPos anchor = state.anchor();
        int fx = target.getX() - anchor.getX();
        int fy = target.getY() - anchor.getY();
        int fz = target.getZ() - anchor.getZ();
        if (fx < 0 || fy < 0 || fz < 0
                || fx >= transform.footprintX() || fy >= transform.footprintY() || fz >= transform.footprintZ()) {
            return;
        }

        SchematicBlockView view = new SchematicBlockView(state.schematic(), anchor, transform);
        BlockState ghost = view.getBlockState(target);
        BlockState world = mc.level.getBlockState(target);

        Component message;
        ItemStack icon;
        if (ghost.isAir()) {
            if (world.isAir()) {
                return;
            }
            message = Component.translatable("holoplace.tooltip.extra");
            icon = new ItemStack(Items.BARRIER);
        } else if (!state.matches(world, ghost)) {
            Item item = MaterialList.itemFor(ghost);
            icon = item != null ? new ItemStack(item) : ItemStack.EMPTY;
            message = Component.translatable("holoplace.tooltip.should_be", ghost.getBlock().getName());
        } else {
            return;
        }

        Font font = mc.font;
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int iconY = height / 2 - 34;
        int textWidth = font.width(message);

        if (!icon.isEmpty()) {
            graphics.fakeItem(icon, centerX - ICON_SIZE / 2, iconY);
        }
        graphics.text(font, message, centerX - textWidth / 2, iconY + ICON_SIZE + 3, 0xFFFFFFFF, true);
    }
}
