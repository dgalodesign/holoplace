package dev.holoplace.capture;

import dev.holoplace.HoloPlaceClient;
import dev.holoplace.schematic.LitematicaSchematicWriter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads a selected world region into a {@link LitematicaSchematicWriter.Region} — the "full capture"
 * path (everything in the box, as-is). Block entities are stored in litematica's layout:
 * {@code saveCustomOnly} data plus region-relative {@code x}/{@code y}/{@code z} ints, no vanilla id.
 */
final class CaptureWriter {

    private CaptureWriter() {
    }

    static LitematicaSchematicWriter.Region capture(String regionName, SelectionState sel, Level level) {
        BlockPos lo = sel.min();
        Vec3i s = sel.size();
        int sx = s.getX();
        int sy = s.getY();
        int sz = s.getZ();
        BlockState[] blocks = new BlockState[sx * sy * sz];
        List<CompoundTag> tileEntities = new ArrayList<>();
        HolderLookup.Provider registries = level.registryAccess();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    p.set(lo.getX() + x, lo.getY() + y, lo.getZ() + z);
                    BlockState state = level.getBlockState(p);
                    blocks[y * sx * sz + z * sx + x] = state;
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
        return new LitematicaSchematicWriter.Region(regionName, sx, sy, sz, blocks, tileEntities, List.of());
    }
}
