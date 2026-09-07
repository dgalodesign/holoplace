package dev.holoplace.capture;

import dev.holoplace.HoloPlaceClient;
import dev.holoplace.schematic.LitematicaSchematicWriter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Reads a selected world region into a {@link LitematicaSchematicWriter.Region}. Two modes:
 * <ul>
 *   <li>full — everything in the box, as-is (Litematica-style);
 *   <li>{@code onlyChanges != null} — only cells recorded in that {@link ChangeLog} keep their world
 *       state; the rest become air. This is "capture what I built this session".
 * </ul>
 * Block entities are stored in the format's layout: {@code saveCustomOnly} data plus region-relative
 * {@code x}/{@code y}/{@code z} ints, no vanilla id.
 */
final class CaptureWriter {

    private CaptureWriter() {
    }

    static LitematicaSchematicWriter.Region capture(String regionName, SelectionState sel, Level level,
                                                    @Nullable ChangeLog onlyChanges) {
        BlockPos lo = sel.min();
        Vec3i s = sel.size();
        int sx = s.getX();
        int sy = s.getY();
        int sz = s.getZ();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[] blocks = new BlockState[sx * sy * sz];
        List<CompoundTag> tileEntities = new ArrayList<>();
        HolderLookup.Provider registries = level.registryAccess();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int wx = lo.getX() + x;
                    int wy = lo.getY() + y;
                    int wz = lo.getZ() + z;
                    int index = y * sx * sz + z * sx + x;
                    if (onlyChanges != null && !onlyChanges.contains(wx, wy, wz)) {
                        blocks[index] = air;
                        continue;
                    }
                    p.set(wx, wy, wz);
                    BlockState state = level.getBlockState(p);
                    blocks[index] = state;
                    if (!state.hasBlockEntity()) {
                        continue;
                    }
                    BlockEntity be = level.getBlockEntity(p);
                    if (be == null) {
                        continue;
                    }
                    try {
                        CompoundTag data = be.saveCustomOnly(registries);
                        data.putInt("x", x);
                        data.putInt("y", y);
                        data.putInt("z", z);
                        tileEntities.add(data);
                    } catch (Exception e) {
                        HoloPlaceClient.LOGGER.warn("Skipping a block entity at {} during capture", p, e);
                    }
                }
            }
        }
        List<CompoundTag> entities = captureEntities(level, registries, lo, sx, sy, sz);
        return new LitematicaSchematicWriter.Region(regionName, sx, sy, sz, blocks, tileEntities, entities);
    }

    /**
     * Entities whose position is inside the box. Client-side data only: item frames, armour stands,
     * paintings and mobs' <em>visible</em> state come through; full mob NBT (AI, attributes,
     * inventory) is not synced to the client, so it won't be in the schematic.
     */
    private static List<CompoundTag> captureEntities(Level level, HolderLookup.Provider registries,
                                                     BlockPos lo, int sx, int sy, int sz) {
        AABB box = new AABB(lo.getX(), lo.getY(), lo.getZ(),
                lo.getX() + sx, lo.getY() + sy, lo.getZ() + sz);
        List<CompoundTag> out = new ArrayList<>();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box,
                e -> !(e instanceof Player) && !e.isRemoved())) {
            try {
                TagValueOutput sink = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
                if (!entity.save(sink)) {
                    continue;
                }
                CompoundTag tag = sink.buildResult();
                ListTag pos = new ListTag();
                pos.add(DoubleTag.valueOf(entity.getX() - lo.getX()));
                pos.add(DoubleTag.valueOf(entity.getY() - lo.getY()));
                pos.add(DoubleTag.valueOf(entity.getZ() - lo.getZ()));
                tag.put("Pos", pos);
                out.add(tag);
            } catch (Exception e) {
                HoloPlaceClient.LOGGER.warn("Skipping an entity during capture", e);
            }
        }
        return out;
    }
}
