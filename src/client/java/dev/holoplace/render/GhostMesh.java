package dev.holoplace.render;

import dev.holoplace.HoloPlaceClient;
import dev.holoplace.schematic.PlacementTransform;
import dev.holoplace.schematic.Schematic;
import dev.holoplace.schematic.SchematicRegion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

/**
 * Baked ghost geometry for one (schematic, rotation, mirror) combination. Model tesselation and face
 * culling happen once, in {@link #build}. The result is addressable per source block — a block's
 * footprint-local position, its transformed {@link BlockState} (for comparing against the real world)
 * and the slice of {@link #quads} it produced — so the renderer can skip whole blocks each frame
 * (e.g. build-assist mode) without any re-tesselation.
 */
final class GhostMesh {

    /** Quad plus its block origin in footprint-local space (0..footprint on each axis). */
    record Quad(float x, float y, float z, BakedQuad quad, boolean tinted) {
    }

    /** A deserialised schematic entity (item frame, armour stand, painting, mob…) with its
     *  footprint-local position and yaw (transform already applied). */
    record GhostEntity(net.minecraft.world.entity.Entity entity, double x, double y, double z, float yaw) {
    }

    private static final Direction[] FACES = Direction.values();

    private final Schematic schematic;
    private final PlacementTransform transform;

    private final int[] blockX;
    private final int[] blockY;
    private final int[] blockZ;
    private final BlockState[] blockStates;
    private final int[] quadStart; // length blockCount + 1
    private final Quad[] quads;

    private final int[] fluidX;
    private final int[] fluidY;
    private final int[] fluidZ;
    private final BlockState[] fluidStates;

    /** Blocks with a block entity (chests, signs, beds, skulls, …). */
    private final int[] beX;
    private final int[] beY;
    private final int[] beZ;
    private final BlockState[] beStates;
    private final @Nullable BlockEntity[] beEntities;

    private final GhostEntity[] entities;

    private GhostMesh(Schematic schematic, PlacementTransform transform,
                     int[] blockX, int[] blockY, int[] blockZ, BlockState[] blockStates,
                     int[] quadStart, Quad[] quads,
                     int[] fluidX, int[] fluidY, int[] fluidZ, BlockState[] fluidStates,
                     int[] beX, int[] beY, int[] beZ, BlockState[] beStates,
                     @Nullable BlockEntity[] beEntities,
                     GhostEntity[] entities) {
        this.schematic = schematic;
        this.transform = transform;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.blockStates = blockStates;
        this.quadStart = quadStart;
        this.quads = quads;
        this.fluidX = fluidX;
        this.fluidY = fluidY;
        this.fluidZ = fluidZ;
        this.fluidStates = fluidStates;
        this.beX = beX;
        this.beY = beY;
        this.beZ = beZ;
        this.beStates = beStates;
        this.beEntities = beEntities;
        this.entities = entities;
    }

    int entityCount() {
        return entities.length;
    }

    GhostEntity entity(int i) {
        return entities[i];
    }

    boolean matches(Schematic schematic, PlacementTransform transform) {
        return this.schematic == schematic && this.transform.equals(transform);
    }

    Schematic schematic() {
        return schematic;
    }

    PlacementTransform transform() {
        return transform;
    }

    int blockCount() {
        return blockX.length;
    }

    int blockX(int i) {
        return blockX[i];
    }

    int blockY(int i) {
        return blockY[i];
    }

    int blockZ(int i) {
        return blockZ[i];
    }

    BlockState blockState(int i) {
        return blockStates[i];
    }

    int quadStart(int i) {
        return quadStart[i];
    }

    Quad quad(int q) {
        return quads[q];
    }

    int totalQuads() {
        return quads.length;
    }

    int fluidCount() {
        return fluidX.length;
    }

    int fluidX(int i) {
        return fluidX[i];
    }

    int fluidY(int i) {
        return fluidY[i];
    }

    int fluidZ(int i) {
        return fluidZ[i];
    }

    BlockState fluidState(int i) {
        return fluidStates[i];
    }

    int blockEntityCount() {
        return beX.length;
    }

    int beX(int i) {
        return beX[i];
    }

    int beY(int i) {
        return beY[i];
    }

    int beZ(int i) {
        return beZ[i];
    }

    BlockState beState(int i) {
        return beStates[i];
    }

    @Nullable BlockEntity blockEntity(int i) {
        return beEntities[i];
    }

    static GhostMesh build(Schematic schematic, PlacementTransform transform) {
        Minecraft mc = Minecraft.getInstance();
        BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
        SchematicBlockView view = new SchematicBlockView(schematic, BlockPos.ZERO, transform);
        RandomSource random = RandomSource.create();
        List<BlockStateModelPart> parts = new ArrayList<>();

        List<int[]> blockPositions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Quad> allQuads = new ArrayList<>();
        List<int[]> fluidPositions = new ArrayList<>();
        List<BlockState> fluidStateList = new ArrayList<>();
        List<int[]> bePositions = new ArrayList<>();
        List<BlockState> beStateList = new ArrayList<>();
        List<BlockEntity> beEntityList = new ArrayList<>();
        List<GhostEntity> ghostEntities = new ArrayList<>();
        var registries = mc.level != null ? mc.level.registryAccess() : null;

        int schMinX = schematic.min().getX();
        int schMinY = schematic.min().getY();
        int schMinZ = schematic.min().getZ();
        BlockPos.MutableBlockPos fp = new BlockPos.MutableBlockPos();

        for (SchematicRegion region : schematic.regions()) {
            BlockPos origin = region.minCorner();
            int authoredBaseX = origin.getX() - schMinX;
            int authoredBaseY = origin.getY() - schMinY;
            int authoredBaseZ = origin.getZ() - schMinZ;

            for (CompoundTag entityTag : region.entities()) {
                GhostEntity ge = makeEntity(registries, entityTag, transform,
                        authoredBaseX, authoredBaseY, authoredBaseZ);
                if (ge != null) {
                    ghostEntities.add(ge);
                }
            }

            for (int y = 0; y < region.sizeY(); y++) {
                for (int z = 0; z < region.sizeZ(); z++) {
                    for (int x = 0; x < region.sizeX(); x++) {
                        BlockState raw = region.getBlockState(x, y, z);
                        if (raw.isAir()) {
                            continue;
                        }
                        int[] f = transform.forward(authoredBaseX + x, authoredBaseY + y, authoredBaseZ + z);
                        fp.set(f[0], f[1], f[2]);
                        BlockState state = transform.applyToState(raw);

                        if (!state.getFluidState().isEmpty()) {
                            fluidPositions.add(new int[] {f[0], f[1], f[2]});
                            fluidStateList.add(state);
                        }

                        int before = allQuads.size();
                        collectBlock(allQuads, view, models, random, parts, state, fp);
                        boolean producedQuads = allQuads.size() != before;

                        if (state.hasBlockEntity()) {
                            bePositions.add(new int[] {f[0], f[1], f[2]});
                            beStateList.add(state);
                            beEntityList.add(makeBlockEntity(registries, fp.immutable(), state,
                                    region.blockEntityNbt(x, y, z)));
                        }
                        if (!producedQuads) {
                            continue;
                        }
                        blockPositions.add(new int[] {f[0], f[1], f[2]});
                        states.add(state);
                        starts.add(before);
                    }
                }
            }
        }

        int blockCount = blockPositions.size();
        int[] bx = new int[blockCount];
        int[] by = new int[blockCount];
        int[] bz = new int[blockCount];
        int[] quadStart = new int[blockCount + 1];
        BlockState[] stateArr = states.toArray(new BlockState[0]);
        for (int i = 0; i < blockCount; i++) {
            int[] p = blockPositions.get(i);
            bx[i] = p[0];
            by[i] = p[1];
            bz[i] = p[2];
            quadStart[i] = starts.get(i);
        }
        quadStart[blockCount] = allQuads.size();

        long beOk = beEntityList.stream().filter(java.util.Objects::nonNull).count();
        if (!bePositions.isEmpty() || !ghostEntities.isEmpty()) {
            HoloPlaceClient.LOGGER.info("Ghost mesh: {} block-entity cells ({} constructed), {} entities",
                    bePositions.size(), beOk, ghostEntities.size());
        }

        return new GhostMesh(schematic, transform, bx, by, bz, stateArr, quadStart,
                allQuads.toArray(new Quad[0]),
                col(fluidPositions, 0), col(fluidPositions, 1), col(fluidPositions, 2),
                fluidStateList.toArray(new BlockState[0]),
                col(bePositions, 0), col(bePositions, 1), col(bePositions, 2),
                beStateList.toArray(new BlockState[0]),
                beEntityList.toArray(new BlockEntity[0]),
                ghostEntities.toArray(new GhostEntity[0]));
    }

    /**
     * Deserialise one schematic entity and place it in footprint-local space with the ghost's
     * rotation/mirror applied. Returns the value that, passed to {@code entity.setPos(anchor + …)} at
     * render time, positions it correctly (an item frame's is its attachment-block centre, since
     * {@code BlockAttachedEntity.setPos} treats its arg as the attach block). {@code null} on failure.
     */
    private static @Nullable GhostEntity makeEntity(HolderLookup.@Nullable Provider registries,
            CompoundTag tag, PlacementTransform transform, int baseX, int baseY, int baseZ) {
        Minecraft mc = Minecraft.getInstance();
        if (registries == null || mc.level == null) {
            return null;
        }
        try {
            var pos = tag.getListOrEmpty("Pos");
            double px = baseX + pos.getDoubleOr(0, 0.0);
            double py = baseY + pos.getDoubleOr(1, 0.0);
            double pz = baseZ + pos.getDoubleOr(2, 0.0);
            double[] f = transform.forwardExact(px, py, pz);

            var input = TagValueInput.create(ProblemReporter.DISCARDING, registries, tag);
            var created = EntityType.create(input, mc.level, EntitySpawnReason.LOAD);
            if (created.isEmpty()) {
                return null;
            }
            Entity entity = created.get();

            // Mirror then rotate (matches PlacementTransform); each reads the current yaw.
            entity.setYRot(entity.mirror(transform.mirror()));
            entity.setYRot(entity.rotate(transform.rotation()));
            float yaw = entity.getYRot();
            if (entity instanceof LivingEntity living) {
                // ArmorStand.setYBodyRot has a vanilla quirk (leaves yBodyRot itself untouched), so
                // set the rotation fields directly — nothing ticks these afterwards.
                living.setYRot(yaw);
                living.yRotO = yaw;
                living.yBodyRot = living.yBodyRotO = yaw;
                living.yHeadRot = living.yHeadRotO = yaw;
            }

            if (entity instanceof HangingEntity hanging) {
                Direction d = transform.rotation().rotate(transform.mirror().mirror(hanging.getDirection()));
                double ax = Math.floor(f[0]) + 0.5;
                double ay = Math.floor(f[1]) + 0.5;
                double az = Math.floor(f[2]) + 0.5;
                // Put the attach block near where the frame sits, then point it — setDirection recalcs
                // the bounding box, so the entity centre ends up right.
                entity.setPos(ax, ay, az);
                ((dev.holoplace.mixin.HangingEntityInvoker) hanging).holoplace$setDirection(d);
                entity.setOldPosAndRot();
                HoloPlaceClient.LOGGER.info(
                        "Ghost entity {}: facing {} -> {}, pos ({},{},{}), invisible={}, bb={}",
                        tag.getStringOr("id", "?"), hanging.getDirection(), d, ax, ay, az,
                        entity.isInvisible(), entity.getBoundingBox());
                return new GhostEntity(entity, ax, ay, az, yaw);
            }

            entity.snapTo(f[0], f[1], f[2], yaw, entity.getXRot());
            entity.setOldPosAndRot();
            return new GhostEntity(entity, f[0], f[1], f[2], yaw);
        } catch (Exception e) {
            HoloPlaceClient.LOGGER.debug("Ghost entity load failed", e);
            return null;
        }
    }

    private static @Nullable BlockEntity makeBlockEntity(
            HolderLookup.@Nullable Provider registries,
            BlockPos footprintPos, BlockState state, @Nullable CompoundTag nbt) {
        if (registries == null || !(state.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }
        BlockEntity be;
        try {
            be = entityBlock.newBlockEntity(footprintPos, state);
        } catch (Exception e) {
            return null;
        }
        if (be == null) {
            return null;
        }
        be.setLevel(Minecraft.getInstance().level);

        // Litematica strips the vanilla id/x/y/z from block-entity NBT; re-derive the id and load
        // whatever data (chest contents, sign text, banner patterns…) is present.
        if (nbt != null && !nbt.isEmpty()) {
            try {
                CompoundTag data = nbt.copy();
                data.remove("x");
                data.remove("y");
                data.remove("z");
                data.putString("id",
                        BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
                be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, data));
            } catch (Exception e) {
                HoloPlaceClient.LOGGER.debug("Block entity data load failed for {} at {}",
                        state, footprintPos, e);
            }
        }
        return be;
    }

    private static int[] col(List<int[]> rows, int axis) {
        int[] out = new int[rows.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = rows.get(i)[axis];
        }
        return out;
    }

    private static void collectBlock(List<Quad> out, SchematicBlockView view, BlockStateModelSet models,
                                     RandomSource random, List<BlockStateModelPart> parts,
                                     BlockState state, BlockPos pos) {
        BlockStateModel model = models.get(state);
        random.setSeed(state.getSeed(pos));
        parts.clear();
        model.collectParts(random, parts);
        if (parts.isEmpty()) {
            return;
        }
        float px = pos.getX();
        float py = pos.getY();
        float pz = pos.getZ();
        for (BlockStateModelPart part : parts) {
            addQuads(out, part.getQuads(null), px, py, pz);
            for (Direction face : FACES) {
                if (view.getBlockState(pos.relative(face)).isSolidRender()) {
                    continue;
                }
                addQuads(out, part.getQuads(face), px, py, pz);
            }
        }
    }

    private static void addQuads(List<Quad> out, List<BakedQuad> quads, float px, float py, float pz) {
        for (BakedQuad quad : quads) {
            out.add(new Quad(px, py, pz, quad, quad.materialInfo().isTinted()));
        }
    }
}
