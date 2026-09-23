package com.prisma.voxel;

import com.prisma.config.PrismaConfig;
import com.prisma.mtl.MTLBuffer;
import com.prisma.mtl.MTLDevice;
import com.prisma.objc.ObjC;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import java.lang.foreign.MemorySegment;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.TrapDoorBlock;

@Environment(EnvType.CLIENT)
public final class VoxelGridManager {
    public static final VoxelGridManager INSTANCE = new VoxelGridManager();

    public static final int FLAG_OCCUPIED = 1;
    public static final int FLAG_FOLIAGE = 2;
    public static final int FLAG_WATER = 4;
    public static final int FLAG_EMISSIVE = 8;
    public static final int FLAG_LAVA = 16;

    public static final int REFLECT_NONE = 0;
    public static final int REFLECT_GLASS = 1;
    public static final int REFLECT_METAL = 2;
    public static final int REFLECT_POLISHED = 3;

    public static final int SHAPE_FULL = 0;
    public static final int SHAPE_SLAB_BOTTOM = 1;
    public static final int SHAPE_SLAB_TOP = 2;
    public static final int SHAPE_CARPET = 3;
    public static final int SHAPE_POST = 4;
    public static final int SHAPE_STAIR = 5;
    public static final int SHAPE_TRAPDOOR = 7;
    public static final int SHAPE_GLASS = 8;
    public static final int SHAPE_GATE = 9;
    public static final int SHAPE_SLAB_DOUBLE = 10;
    public static final int SHAPE_CROSS_PLANT = 11;
    public static final int SHAPE_LEAVES = 12;
    public static final int SHAPE_CUTOUT = 12;
    public static final int SHAPE_LANTERN = 13;
    public static final int SHAPE_NON_FULL = 14;
    public static final int SHAPE_TORCH = 15;
    public static final int SHAPE_CHEST = 16;
    public static final int SHAPE_DOOR = 17;
    public static final int SHAPE_FENCE = 18;
    public static final int SHAPE_NO_SHADOW = 19;
    public static final int SHAPE_BAMBOO = 20;

    public record GridState(
            MTLBuffer buffer,
            int originX,
            int originY,
            int originZ,
            int sizeX,
            int sizeY,
            int sizeZ,
            int radius,
            List<PointLight> lights
    ) {}

    private MTLDevice device;
    private MTLBuffer voxelBufferA;
    private MemorySegment voxelBufferContentsA = MemorySegment.NULL;
    private MTLBuffer voxelBufferB;
    private MemorySegment voxelBufferContentsB = MemorySegment.NULL;
    private MTLBuffer voxelBufferC;
    private MemorySegment voxelBufferContentsC = MemorySegment.NULL;
    private final AtomicInteger activeBufferIndex = new AtomicInteger(0);

    private MTLBuffer blockUvBuffer;
    private MemorySegment blockUvContents = MemorySegment.NULL;
    private boolean blockUvInitialized = false;

    private MTLBuffer blockBitmaskBuffer;
    private MemorySegment blockBitmaskContents = MemorySegment.NULL;
    private boolean blockBitmaskInitialized = false;

    private volatile GridState activeState;

    private int currentRadius = -1;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private int heightSections = 16;

    private int lastCamChunkX = Integer.MIN_VALUE;
    private int lastCamChunkZ = Integer.MIN_VALUE;
    private int lastCamSectionY = Integer.MIN_VALUE;
    private long lastUpdateFrame = -1;
    private long lastUpdateMillis = 0L;
    private final AtomicBoolean dirty = new AtomicBoolean(true);

    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Prisma-Voxel-Worker");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final AtomicBoolean isUpdating = new AtomicBoolean(false);
    private final ReentrantReadWriteLock bufferLock = new ReentrantReadWriteLock();

    private VoxelGridManager() {
    }

    public void markDirty() {
        this.dirty.set(true);
    }

    public void invalidateBlockTextureCache() {
        this.blockUvInitialized = false;
        this.blockBitmaskInitialized = false;
        this.dirty.set(true);
    }

    public void init(final MTLDevice mtlDevice) {
        this.device = mtlDevice;
        ensureBuffer();
    }

    public void ensureBuffer() {
        if (device == null) return;

        int radius = Math.clamp(PrismaConfig.INSTANCE.voxelRadius, 2, 16);
        if (voxelBufferA != null && this.currentRadius == radius) {
            return;
        }

        bufferLock.writeLock().lock();
        try {
            if (voxelBufferA != null && this.currentRadius == radius) {
                return;
            }

            if (voxelBufferA != null) {
                ObjC.release(voxelBufferA.handle());
                voxelBufferA = null;
                voxelBufferContentsA = MemorySegment.NULL;
            }
            if (voxelBufferB != null) {
                ObjC.release(voxelBufferB.handle());
                voxelBufferB = null;
                voxelBufferContentsB = MemorySegment.NULL;
            }
            if (voxelBufferC != null) {
                ObjC.release(voxelBufferC.handle());
                voxelBufferC = null;
                voxelBufferContentsC = MemorySegment.NULL;
            }

            this.currentRadius = radius;
            int diameterChunks = radius * 2;
            this.sizeX = diameterChunks * 16;
            this.sizeZ = diameterChunks * 16;
            this.sizeY = heightSections * 16;

            long totalVoxels = (long) sizeX * (long) sizeY * (long) sizeZ;
            long totalBytes = totalVoxels * 8L;

            this.voxelBufferA = device.newBuffer(totalBytes, 0L);
            this.voxelBufferContentsA = this.voxelBufferA.contents().reinterpret(totalBytes);
            this.voxelBufferContentsA.fill((byte) 0);

            this.voxelBufferB = device.newBuffer(totalBytes, 0L);
            this.voxelBufferContentsB = this.voxelBufferB.contents().reinterpret(totalBytes);
            this.voxelBufferContentsB.fill((byte) 0);

            this.voxelBufferC = device.newBuffer(totalBytes, 0L);
            this.voxelBufferContentsC = this.voxelBufferC.contents().reinterpret(totalBytes);
            this.voxelBufferContentsC.fill((byte) 0);

            if (this.blockUvBuffer == null) {
                long uvBytes = 32768L * 48L;
                this.blockUvBuffer = device.newBuffer(uvBytes, 0L);
                this.blockUvContents = this.blockUvBuffer.contents().reinterpret(uvBytes);
                this.blockUvContents.fill((byte) 0);
                this.blockUvInitialized = false;
            }

            if (this.blockBitmaskBuffer == null) {
                long maskBytes = 32768L * 8L;
                this.blockBitmaskBuffer = device.newBuffer(maskBytes, 0L);
                this.blockBitmaskContents = this.blockBitmaskBuffer.contents().reinterpret(maskBytes);
                this.blockBitmaskContents.fill((byte) 0);
                this.blockBitmaskInitialized = false;
            }

            this.activeState = new GridState(
                    this.voxelBufferA,
                    0, 0, 0,
                    this.sizeX, this.sizeY, this.sizeZ,
                    this.currentRadius,
                    List.of()
            );

            this.activeBufferIndex.set(0);
            this.lastCamChunkX = Integer.MIN_VALUE;
            this.lastCamChunkZ = Integer.MIN_VALUE;
            this.lastCamSectionY = Integer.MIN_VALUE;
        } finally {
            bufferLock.writeLock().unlock();
        }
    }

    public void update(final ClientLevel level, final Camera camera, final long frameCount) {
        if (level == null || camera == null || device == null) return;

        ensureBuffer();
        if (voxelBufferA == null || voxelBufferB == null || voxelBufferC == null) return;

        Vec3 camPos = camera.position();
        int camBlockX = (int) Math.floor(camPos.x);
        int camBlockY = (int) Math.floor(camPos.y);
        int camBlockZ = (int) Math.floor(camPos.z);

        int camChunkX = camBlockX >> 4;
        int camChunkZ = camBlockZ >> 4;
        int camSectionY = camBlockY >> 4;

        if (this.lastCamChunkX == Integer.MIN_VALUE) {
            this.lastCamChunkX = camChunkX;
            this.lastCamChunkZ = camChunkZ;
            this.lastCamSectionY = camSectionY;
            this.lastUpdateFrame = frameCount;
            this.lastUpdateMillis = System.currentTimeMillis();
            rebuildGrid(level, camPos, camChunkX, camChunkZ, camSectionY);
            return;
        }

        long now = System.currentTimeMillis();
        boolean moved = (camChunkX != lastCamChunkX || camChunkZ != lastCamChunkZ || Math.abs(camSectionY - lastCamSectionY) >= 1);
        boolean isDirty = this.dirty.getAndSet(false);
        if (!moved && !isDirty) {
            return;
        }
        if (!isDirty && (now - lastUpdateMillis < 33L)) {
            return;
        }

        if (!isUpdating.compareAndSet(false, true)) {
            if (isDirty) this.dirty.set(true);
            return;
        }

        this.lastCamChunkX = camChunkX;
        this.lastCamChunkZ = camChunkZ;
        this.lastCamSectionY = camSectionY;
        this.lastUpdateFrame = frameCount;
        this.lastUpdateMillis = now;

        updateExecutor.submit(() -> {
            try {
                rebuildGrid(level, camPos, camChunkX, camChunkZ, camSectionY);
            } catch (Throwable ignored) {
            } finally {
                isUpdating.set(false);
            }
        });
    }

    private void rebuildGrid(final ClientLevel level, final Vec3 camPos, final int camChunkX, final int camChunkZ, final int camSectionY) {
        if (!bufferLock.readLock().tryLock()) {
            return;
        }
        try {
            if (this.currentRadius <= 0 || voxelBufferA == null || voxelBufferB == null || voxelBufferC == null) {
                return;
            }
            final int localRadius = this.currentRadius;
            final int localSizeX = this.sizeX;
            final int localSizeY = this.sizeY;
            final int localSizeZ = this.sizeZ;
            final int localHeightSections = this.heightSections;

            int minChunkX = camChunkX - localRadius;
            int maxChunkX = camChunkX + localRadius - 1;
            int minChunkZ = camChunkZ - localRadius;
            int maxChunkZ = camChunkZ + localRadius - 1;

            int minSecY = Math.max(level.getMinSectionY(), camSectionY - 3);
            int maxSecY = Math.min(level.getMaxSectionY(), minSecY + localHeightSections - 1);

            int originX = minChunkX << 4;
            int originY = minSecY << 4;
            int originZ = minChunkZ << 4;

            int targetIndex = (activeBufferIndex.get() + 1) % 3;
            MTLBuffer backBuffer = (targetIndex == 0) ? voxelBufferA : (targetIndex == 1 ? voxelBufferB : voxelBufferC);
            MemorySegment backContents = (targetIndex == 0) ? voxelBufferContentsA : (targetIndex == 1 ? voxelBufferContentsB : voxelBufferContentsC);

            backContents.fill((byte) 0);

            final List<PointLight> localLights = new ArrayList<>();
            final BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();
            long[] floodQueue = new long[16384];
            int floodTail = 0;

            LevelLightEngine lightEngine = level.getLightEngine();
            LayerLightEventListener skyListener = lightEngine != null ? lightEngine.getLayerListener(LightLayer.SKY) : null;
            LayerLightEventListener blockListener = lightEngine != null ? lightEngine.getLayerListener(LightLayer.BLOCK) : null;

            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    LevelChunk chunk = level.getChunk(cx, cz);
                    if (chunk == null || chunk.isEmpty()) continue;

                    for (int sy = minSecY; sy <= maxSecY; sy++) {
                        int secIdx = level.getSectionIndexFromSectionY(sy);
                        if (secIdx < 0 || secIdx >= chunk.getSections().length) continue;

                        LevelChunk section = chunk;
                        LevelChunkSection chunkSection = chunk.getSection(secIdx);
                        if (chunkSection == null || chunkSection.hasOnlyAir()) continue;

                        int secSolidCount = 0;
                        int[] octCount = new int[8];
                        int[] octMinX = new int[8];
                        int[] octMinY = new int[8];
                        int[] octMinZ = new int[8];
                        int[] octMaxX = new int[8];
                        int[] octMaxY = new int[8];
                        int[] octMaxZ = new int[8];
                        for (int k = 0; k < 8; k++) {
                            octMinX[k] = Integer.MAX_VALUE; octMinY[k] = Integer.MAX_VALUE; octMinZ[k] = Integer.MAX_VALUE;
                            octMaxX[k] = Integer.MIN_VALUE; octMaxY[k] = Integer.MIN_VALUE; octMaxZ[k] = Integer.MIN_VALUE;
                        }

                        DataLayer skyData = skyListener != null ? skyListener.getDataLayerData(SectionPos.of(cx, sy, cz)) : null;
                        DataLayer blockData = blockListener != null ? blockListener.getDataLayerData(SectionPos.of(cx, sy, cz)) : null;

                        int baseWx = cx << 4;
                        int baseWy = sy << 4;
                        int baseWz = cz << 4;

                        for (int by = 0; by < 16; by++) {
                            int wy = baseWy + by;
                            int vy = wy - originY;
                            if (vy < 0 || vy >= localSizeY) continue;

                            for (int bz = 0; bz < 16; bz++) {
                                int wz = baseWz + bz;
                                int vz = wz - originZ;
                                if (vz < 0 || vz >= localSizeZ) continue;

                                for (int bx = 0; bx < 16; bx++) {
                                    int wx = baseWx + bx;
                                    int vx = wx - originX;
                                    if (vx < 0 || vx >= localSizeX) continue;

                                    BlockState state = chunkSection.getBlockState(bx, by, bz);
                                    if (state.isAir()) {
                                        int sky = skyData != null ? skyData.get(bx, by, bz) : 15;
                                        int block = blockData != null ? blockData.get(bx, by, bz) : 0;
                                        if (sky > 0 || block > 0) {
                                            int low32 = ((block & 0x0F) << 4) | ((sky & 0x0F) << 8);
                                            long voxelIndex = ((long) vz * (long) localSizeY + (long) vy) * (long) localSizeX + (long) vx;
                                            backContents.set(JAVA_LONG, voxelIndex * 8L, (long) low32 & 0xFFFFFFFFL);
                                        }
                                        continue;
                                    }

                                    int emission = state.getLightEmission();
                                    if (state.is(Blocks.REDSTONE_BLOCK)) {
                                        emission = 10;
                                    }
                                    boolean isLeaves = state.getBlock() instanceof LeavesBlock;
                                    boolean isFluid = !state.getFluidState().isEmpty();
                                    boolean isFoliage = isLeaves || (state.getBlock() instanceof net.minecraft.world.level.block.BushBlock) || (state.getBlock() instanceof net.minecraft.world.level.block.VineBlock) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.LILY_PAD);
                                    boolean isSolid = !state.isAir() && !isFluid && !state.is(Blocks.LIGHT) && !state.is(Blocks.STRUCTURE_VOID) && !state.is(Blocks.BARRIER);

                                    int flags = 0;
                                    if (isSolid) flags |= FLAG_OCCUPIED;
                                    if (isFoliage) flags |= FLAG_FOLIAGE;
                                    if (isFluid) {
                                        flags |= FLAG_WATER;
                                    }
                                    if (emission >= 7 || (!state.getFluidState().isEmpty() && !state.getFluidState().isSourceOfType(net.minecraft.world.level.material.Fluids.WATER) && !state.getFluidState().is(net.minecraft.world.level.material.Fluids.FLOWING_WATER))) {
                                        flags |= FLAG_EMISSIVE;
                                    }

                                    int sky = skyData != null ? skyData.get(bx, by, bz) : 15;
                                    int block = blockData != null ? blockData.get(bx, by, bz) : 0;
                                    if (isSolid) {
                                        if (skyData != null) {
                                            int maxSky = sky;
                                            if (by + 1 < 16) maxSky = Math.max(maxSky, skyData.get(bx, by + 1, bz));
                                            else maxSky = 15;
                                            if (bx + 1 < 16) maxSky = Math.max(maxSky, skyData.get(bx + 1, by, bz));
                                            if (bx - 1 >= 0) maxSky = Math.max(maxSky, skyData.get(bx - 1, by, bz));
                                            if (bz + 1 < 16) maxSky = Math.max(maxSky, skyData.get(bx, by, bz + 1));
                                            if (bz - 1 >= 0) maxSky = Math.max(maxSky, skyData.get(bx, by, bz - 1));
                                            sky = maxSky;
                                        }
                                        if (blockData != null) {
                                            int maxBlock = block;
                                            if (by + 1 < 16) maxBlock = Math.max(maxBlock, blockData.get(bx, by + 1, bz));
                                            if (bx + 1 < 16) maxBlock = Math.max(maxBlock, blockData.get(bx + 1, by, bz));
                                            if (bx - 1 >= 0) maxBlock = Math.max(maxBlock, blockData.get(bx - 1, by, bz));
                                            if (bz + 1 < 16) maxBlock = Math.max(maxBlock, blockData.get(bx, by, bz + 1));
                                            if (bz - 1 >= 0) maxBlock = Math.max(maxBlock, blockData.get(bx, by, bz - 1));
                                            block = maxBlock;
                                        }
                                    }
                                    if (emission > block) {
                                        block = emission;
                                    }

                                    int reflectType = REFLECT_NONE;
                                    boolean isGlass = (state.getBlock() instanceof TransparentBlock && !state.is(Blocks.TINTED_GLASS))
                                            || state.is(Blocks.GLASS)
                                            || (state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock && !state.is(Blocks.IRON_BARS))
                                            || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.FROSTED_ICE);
                                    if (isGlass) {
                                        reflectType = REFLECT_GLASS;
                                    } else if (state.is(Blocks.DIAMOND_BLOCK) || state.is(Blocks.IRON_BLOCK) || state.is(Blocks.RAW_IRON_BLOCK)
                                            || state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.RAW_GOLD_BLOCK)
                                            || state.is(Blocks.EMERALD_BLOCK) || state.is(Blocks.NETHERITE_BLOCK)
                                            || state.getBlock() instanceof net.minecraft.world.level.block.WeatheringCopper || state.is(Blocks.RAW_COPPER_BLOCK)
                                            || state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.LAPIS_BLOCK)) {
                                        reflectType = REFLECT_METAL;
                                    } else if (state.is(Blocks.SEA_LANTERN)) {
                                        reflectType = REFLECT_POLISHED;
                                    }

                                    int shapeId = SHAPE_FULL;
                                    int doorData = 0;
                                    int fenceData = 0;
                                    int stairData = 0;
                                    int trapdoorData = 0;
                                    int chestData = 0;
                                    if (state.getBlock() instanceof net.minecraft.world.level.block.TorchBlock
                                            || state.getBlock() instanceof net.minecraft.world.level.block.WallTorchBlock
                                            || state.getBlock() instanceof net.minecraft.world.level.block.RedstoneTorchBlock) {
                                        shapeId = SHAPE_TORCH;
                                    } else if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                                            || state.getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock) {
                                        shapeId = SHAPE_CHEST;
                                        int x0 = 1, x1 = 15, z0 = 1, z1 = 15;
                                        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                                                && state.hasProperty(net.minecraft.world.level.block.ChestBlock.TYPE)
                                                && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                                            Direction conn = net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state);
                                            if (conn == Direction.EAST) x1 = 16;
                                            else if (conn == Direction.WEST) x0 = 0;
                                            else if (conn == Direction.SOUTH) z1 = 16;
                                            else if (conn == Direction.NORTH) z0 = 0;
                                        }
                                        chestData = (x0 & 0x1F) | ((x1 & 0x1F) << 5) | ((z0 & 0x1F) << 10) | ((z1 & 0x1F) << 15);
                                    } else if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                                        shapeId = SHAPE_DOOR;
                                        net.minecraft.world.phys.shapes.VoxelShape doorShape = state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                                        List<net.minecraft.world.phys.AABB> doorBoxes = doorShape.toAabbs();
                                        if (!doorBoxes.isEmpty()) {
                                            net.minecraft.world.phys.AABB db = doorBoxes.get(0);
                                            int x0 = (int) Math.round(db.minX * 16.0);
                                            int x1 = (int) Math.round(db.maxX * 16.0);
                                            int z0 = (int) Math.round(db.minZ * 16.0);
                                            int z1 = (int) Math.round(db.maxZ * 16.0);
                                            int isUpper = (state.hasProperty(net.minecraft.world.level.block.DoorBlock.HALF)
                                                    && state.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) ? 1 : 0;
                                            doorData = (x0 & 0x1F) | ((x1 & 0x1F) << 5) | ((z0 & 0x1F) << 10) | ((z1 & 0x1F) << 15) | (isUpper << 20);
                                        }
                                    } else if (state.getBlock() instanceof net.minecraft.world.level.block.FenceBlock
                                            || state.getBlock() instanceof net.minecraft.world.level.block.WallBlock
                                            || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
                                        shapeId = SHAPE_FENCE;
                                        if (state.hasProperty(net.minecraft.world.level.block.FenceBlock.NORTH) && Boolean.TRUE.equals(state.getValue(net.minecraft.world.level.block.FenceBlock.NORTH))) fenceData |= 1;
                                        if (state.hasProperty(net.minecraft.world.level.block.FenceBlock.SOUTH) && Boolean.TRUE.equals(state.getValue(net.minecraft.world.level.block.FenceBlock.SOUTH))) fenceData |= 2;
                                        if (state.hasProperty(net.minecraft.world.level.block.FenceBlock.WEST) && Boolean.TRUE.equals(state.getValue(net.minecraft.world.level.block.FenceBlock.WEST))) fenceData |= 4;
                                        if (state.hasProperty(net.minecraft.world.level.block.FenceBlock.EAST) && Boolean.TRUE.equals(state.getValue(net.minecraft.world.level.block.FenceBlock.EAST))) fenceData |= 8;
                                        if (state.getBlock() instanceof net.minecraft.world.level.block.WallBlock) {
                                            if (state.hasProperty(net.minecraft.world.level.block.WallBlock.NORTH) && state.getValue(net.minecraft.world.level.block.WallBlock.NORTH) != net.minecraft.world.level.block.state.properties.WallSide.NONE) fenceData |= 1;
                                            if (state.hasProperty(net.minecraft.world.level.block.WallBlock.SOUTH) && state.getValue(net.minecraft.world.level.block.WallBlock.SOUTH) != net.minecraft.world.level.block.state.properties.WallSide.NONE) fenceData |= 2;
                                            if (state.hasProperty(net.minecraft.world.level.block.WallBlock.WEST) && state.getValue(net.minecraft.world.level.block.WallBlock.WEST) != net.minecraft.world.level.block.state.properties.WallSide.NONE) fenceData |= 4;
                                            if (state.hasProperty(net.minecraft.world.level.block.WallBlock.EAST) && state.getValue(net.minecraft.world.level.block.WallBlock.EAST) != net.minecraft.world.level.block.state.properties.WallSide.NONE) fenceData |= 8;
                                        } else if (state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
                                            boolean open = state.hasProperty(net.minecraft.world.level.block.FenceGateBlock.OPEN) && Boolean.TRUE.equals(state.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN));
                                            net.minecraft.core.Direction facing = state.hasProperty(net.minecraft.world.level.block.FenceGateBlock.FACING) ? state.getValue(net.minecraft.world.level.block.FenceGateBlock.FACING) : net.minecraft.core.Direction.NORTH;
                                            if (!open) {
                                                if (facing.getAxis() == net.minecraft.core.Direction.Axis.Z) fenceData = 4 | 8;
                                                else fenceData = 1 | 2;
                                            }
                                        }
                                    } else if (state.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
                                        net.minecraft.world.level.block.state.properties.SlabType sType = state.getValue(net.minecraft.world.level.block.SlabBlock.TYPE);
                                        if (sType == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM) shapeId = SHAPE_SLAB_BOTTOM;
                                        else if (sType == net.minecraft.world.level.block.state.properties.SlabType.TOP) shapeId = SHAPE_SLAB_TOP;
                                        else shapeId = SHAPE_FULL;
                                    } else if (state.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
                                        shapeId = SHAPE_STAIR;
                                        boolean isTop = state.hasProperty(net.minecraft.world.level.block.StairBlock.HALF)
                                                && state.getValue(net.minecraft.world.level.block.StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.TOP;
                                        net.minecraft.core.Direction facing = state.hasProperty(net.minecraft.world.level.block.StairBlock.FACING)
                                                ? state.getValue(net.minecraft.world.level.block.StairBlock.FACING) : net.minecraft.core.Direction.NORTH;
                                        int facingCode = (facing == net.minecraft.core.Direction.NORTH) ? 0 : (facing == net.minecraft.core.Direction.SOUTH ? 1 : (facing == net.minecraft.core.Direction.WEST ? 2 : 3));
                                        net.minecraft.world.level.block.state.properties.StairsShape sShape = state.hasProperty(net.minecraft.world.level.block.StairBlock.SHAPE)
                                                ? state.getValue(net.minecraft.world.level.block.StairBlock.SHAPE) : net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT;
                                        int shapeCode = sShape.ordinal();
                                        stairData = (isTop ? 1 : 0) | (facingCode << 1) | (shapeCode << 3);
                                    } else if (state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) {
                                        shapeId = SHAPE_TRAPDOOR;
                                        boolean isTop = state.hasProperty(net.minecraft.world.level.block.TrapDoorBlock.HALF)
                                                && state.getValue(net.minecraft.world.level.block.TrapDoorBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.TOP;
                                        boolean isOpen = state.hasProperty(net.minecraft.world.level.block.TrapDoorBlock.OPEN)
                                                && Boolean.TRUE.equals(state.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN));
                                        net.minecraft.core.Direction facing = state.hasProperty(net.minecraft.world.level.block.TrapDoorBlock.FACING)
                                                ? state.getValue(net.minecraft.world.level.block.TrapDoorBlock.FACING) : net.minecraft.core.Direction.NORTH;
                                        int facingCode = (facing == net.minecraft.core.Direction.NORTH) ? 0 : (facing == net.minecraft.core.Direction.SOUTH ? 1 : (facing == net.minecraft.core.Direction.WEST ? 2 : 3));
                                        trapdoorData = (isTop ? 1 : 0) | (isOpen ? 2 : 0) | (facingCode << 2);
                                    } else {
                                        long maskCheck = BlockBitmaskPalette.getBitmask(state);
                                        boolean isPartialSnow = state.is(Blocks.SNOW) && state.hasProperty(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) && state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) < 8;
                                        boolean isSign = state.getBlock() instanceof net.minecraft.world.level.block.SignBlock || state.getBlock() instanceof net.minecraft.world.level.block.WallSignBlock;
                                        boolean isPlateOrButton = state.getBlock() instanceof net.minecraft.world.level.block.BasePressurePlateBlock || state.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock;
                                        if (isSign || isPlateOrButton || isPartialSnow || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND) || state.getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock || state.is(Blocks.MOSS_CARPET) || state.is(Blocks.PINK_PETALS) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("leaf_litter") || state.getBlock() instanceof net.minecraft.world.level.block.VineBlock) {
                                            shapeId = SHAPE_NO_SHADOW;
                                        } else if (maskCheck != -1L || state.is(Blocks.SPAWNER) || state.is(Blocks.POINTED_DRIPSTONE)) {
                                            boolean isFire = state.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock;
                                            boolean isPot = state.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock;
                                            boolean isBamboo = state.getBlock() instanceof net.minecraft.world.level.block.BambooStalkBlock;
                                            if (isBamboo) {
                                                shapeId = SHAPE_BAMBOO;
                                            } else if (BlockBitmaskPalette.isVegetation(state) || state.getBlock() instanceof net.minecraft.world.level.block.TorchBlock || state.getBlock() instanceof net.minecraft.world.level.block.WallTorchBlock || isFire || isPot) {
                                                shapeId = SHAPE_CROSS_PLANT;
                                            } else if (state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) {
                                                shapeId = SHAPE_LEAVES;
                                            } else {
                                                shapeId = SHAPE_NON_FULL;
                                            }
                                        }
                                    }

                                    int mapCol = BlockColorPalette.getColor(state, level, mutPos.set(wx, wy, wz));

                                    int r = (mapCol >> 16) & 0xFF;
                                    int g = (mapCol >> 8) & 0xFF;
                                    int b = mapCol & 0xFF;

                                    int stateId = net.minecraft.world.level.block.Block.getId(state) & 0xFFFF;
                                    long mask = BlockBitmaskPalette.getBitmask(state);
                                    if (blockBitmaskContents != MemorySegment.NULL && stateId < 32768) {
                                        blockBitmaskContents.set(JAVA_LONG, (long) stateId * 8L, mask);
                                    }
                                    int low32 = (flags & 0x0F)
                                            | ((block & 0x0F) << 4)
                                            | ((sky & 0x0F) << 8)
                                            | ((reflectType & 0x0F) << 12)
                                            | (stateId << 16);
                                    int high32;
                                    if (shapeId == SHAPE_DOOR) {
                                        high32 = ((shapeId & 0xFF) << 24) | (doorData & 0x00FFFFFF);
                                    } else if (shapeId == SHAPE_CHEST) {
                                        high32 = ((shapeId & 0xFF) << 24) | (chestData & 0x00FFFFFF);
                                    } else if (shapeId == SHAPE_CROSS_PLANT || shapeId == SHAPE_BAMBOO) {
                                        net.minecraft.world.phys.Vec3 off = state.hasOffsetFunction() ? state.getOffset(mutPos.set(wx, wy, wz)) : net.minecraft.world.phys.Vec3.ZERO;
                                        int ox3 = Math.clamp((int) Math.round((off.x + 0.25) * 14.0), 0, 7);
                                        int oz3 = Math.clamp((int) Math.round((off.z + 0.25) * 14.0), 0, 7);
                                        int r6 = (r >> 2) & 0x3F;
                                        int g6 = (g >> 2) & 0x3F;
                                        int b6 = (b >> 2) & 0x3F;
                                        high32 = ((shapeId & 0xFF) << 24) | (ox3 << 21) | (oz3 << 18) | (r6 << 12) | (g6 << 6) | b6;
                                    } else if (shapeId == SHAPE_FENCE || shapeId == SHAPE_STAIR || shapeId == SHAPE_TRAPDOOR) {
                                        int shapeData = 0;
                                        if (shapeId == SHAPE_FENCE) shapeData = fenceData & 0x0F;
                                        else if (shapeId == SHAPE_STAIR) shapeData = stairData & 0x3F;
                                        else if (shapeId == SHAPE_TRAPDOOR) shapeData = trapdoorData & 0x0F;
                                        int r6 = (r >> 2) & 0x3F;
                                        int g6 = (g >> 2) & 0x3F;
                                        int b6 = (b >> 2) & 0x3F;
                                        high32 = ((shapeId & 0xFF) << 24) | ((shapeData & 0x3F) << 18) | (r6 << 12) | (g6 << 6) | b6;
                                    } else {
                                        high32 = ((shapeId & 0xFF) << 24) | (r << 16) | (g << 8) | b;
                                    }
                                    long packedVoxel = ((long) high32 << 32) | ((long) low32 & 0xFFFFFFFFL);

                                    long voxelIndex = ((long) vz * (long) localSizeY + (long) vy) * (long) localSizeX + (long) vx;
                                    backContents.set(JAVA_LONG, voxelIndex * 8L, packedVoxel);

                                    if ((flags & FLAG_OCCUPIED) != 0) {
                                        int oct = ((by >= 8 ? 1 : 0) << 2) | ((bz >= 8 ? 1 : 0) << 1) | (bx >= 8 ? 1 : 0);
                                        octCount[oct]++;
                                        secSolidCount++;
                                        if (wx < octMinX[oct]) octMinX[oct] = wx;
                                        if (wy < octMinY[oct]) octMinY[oct] = wy;
                                        if (wz < octMinZ[oct]) octMinZ[oct] = wz;
                                        if (wx + 1 > octMaxX[oct]) octMaxX[oct] = wx + 1;
                                        if (wy + 1 > octMaxY[oct]) octMaxY[oct] = wy + 1;
                                        if (wz + 1 > octMaxZ[oct]) octMaxZ[oct] = wz + 1;
                                    }

                                    if (emission >= 2) {
                                        if (floodTail >= floodQueue.length) {
                                            floodQueue = Arrays.copyOf(floodQueue, floodQueue.length * 2);
                                        }
                                        floodQueue[floodTail++] = ((long) emission << 32) | ((long) vz << 20) | ((long) vy << 10) | (long) vx;
                                    }

                                    if (emission >= 2 && !state.is(Blocks.BROWN_MUSHROOM) && !state.is(Blocks.RED_MUSHROOM) && !state.is(Blocks.GLOW_LICHEN)) {
                                        float lx = wx + 0.5f;
                                        float ly = wy + 0.5f;
                                        float lz = wz + 0.5f;
                                        if (state.is(Blocks.TORCH) || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.REDSTONE_TORCH)) {
                                            ly = wy + 0.68f;
                                        } else if (state.getBlock() instanceof WallTorchBlock) {
                                            Direction facing = state.getValue(WallTorchBlock.FACING);

                                            lx -= facing.getStepX() * 0.23f;
                                            lz -= facing.getStepZ() * 0.23f;
                                            ly = wy + 0.65f;
                                        } else if (state.getBlock() instanceof LanternBlock) {
                                            if (state.hasProperty(LanternBlock.HANGING) && state.getValue(LanternBlock.HANGING)) {
                                                ly = wy + 0.45f;
                                            } else {
                                                ly = wy + 0.35f;
                                            }
                                        } else if (isSolid) {
                                            int openCount = 0;
                                            int sumDx = 0;
                                            int sumDy = 0;
                                            int sumDz = 0;
                                            for (Direction dir : Direction.values()) {
                                                BlockPos np = mutPos.set(wx + dir.getStepX(), wy + dir.getStepY(), wz + dir.getStepZ());
                                                BlockState ns = level.getBlockState(np);
                                                boolean neighborIsGlass = (ns.getBlock() instanceof TransparentBlock && !ns.is(Blocks.TINTED_GLASS))
                                                        || ns.is(Blocks.GLASS)
                                                        || (ns.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock && !ns.is(Blocks.IRON_BARS))
                                                        || ns.is(Blocks.ICE) || ns.is(Blocks.PACKED_ICE) || ns.is(Blocks.BLUE_ICE) || ns.is(Blocks.FROSTED_ICE);
                                                boolean neighborHasGap = ns.getBlock() instanceof net.minecraft.world.level.block.StairBlock
                                                        || ns.getBlock() instanceof net.minecraft.world.level.block.FenceBlock
                                                        || ns.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock
                                                        || ns.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock;
                                                if (!ns.canOcclude() || neighborIsGlass || neighborHasGap) {
                                                    openCount++;
                                                    sumDx += dir.getStepX();
                                                    sumDy += dir.getStepY();
                                                    sumDz += dir.getStepZ();
                                                }
                                            }
                                            if (openCount == 0) {
                                                continue;
                                            }
                                            if (openCount < 6) {
                                                float shift = 0.52f;
                                                lx += ((float) sumDx / openCount) * shift;
                                                ly += ((float) sumDy / openCount) * shift;
                                                lz += ((float) sumDz / openCount) * shift;
                                            }
                                        }
                                        int facingCode = 0;
                                        if (state.getBlock() instanceof WallTorchBlock) {
                                            Direction facing = state.getValue(WallTorchBlock.FACING);
                                            if (facing == Direction.NORTH) facingCode = 1;
                                            else if (facing == Direction.SOUTH) facingCode = 2;
                                            else if (facing == Direction.WEST) facingCode = 3;
                                            else if (facing == Direction.EAST) facingCode = 4;
                                        }
                                        PointLight pl = createPointLight(state, lx, ly, lz, emission, mapCol, facingCode);
                                        if (pl != null) {
                                            localLights.add(pl);
                                        }
                                    }
                                }
                            }


                        }
                    }
                }
            }

            int floodHead = 0;
            while (floodHead < floodTail) {
                long entry = floodQueue[floodHead++];
                int emissionLevel = (int) (entry >>> 32);
                if (emissionLevel <= 1) continue;

                int qx = (int) (entry & 0x3FF);
                int qy = (int) ((entry >>> 10) & 0x3FF);
                int qz = (int) ((entry >>> 20) & 0x3FF);

                int nextLight = emissionLevel - 1;

                for (int dir = 0; dir < 6; dir++) {
                    int nx = qx + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                    int ny = qy + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                    int nz = qz + (dir == 4 ? 1 : dir == 5 ? -1 : 0);

                    if (nx < 0 || nx >= localSizeX || ny < 0 || ny >= localSizeY || nz < 0 || nz >= localSizeZ) continue;

                    long neighborVoxelIndex = ((long) nz * (long) localSizeY + (long) ny) * (long) localSizeX + (long) nx;
                    long nPacked = backContents.get(JAVA_LONG, neighborVoxelIndex * 8L);
                    int nLow = (int) nPacked;

                    if ((nLow & FLAG_OCCUPIED) != 0) continue;

                    int curBlockLight = (nLow >> 4) & 0x0F;
                    if (curBlockLight < nextLight) {
                        int updatedLow = (nLow & ~0xF0) | (nextLight << 4);
                        long updatedPacked = (nPacked & 0xFFFFFFFF00000000L) | ((long) updatedLow & 0xFFFFFFFFL);
                        backContents.set(JAVA_LONG, neighborVoxelIndex * 8L, updatedPacked);

                        if (nextLight > 1) {
                            if (floodTail >= floodQueue.length) {
                                floodQueue = Arrays.copyOf(floodQueue, floodQueue.length * 2);
                            }
                            floodQueue[floodTail++] = ((long) nextLight << 32) | ((long) nz << 20) | ((long) ny << 10) | (long) nx;
                        }
                    }
                }
            }

            localLights.sort(Comparator.comparingDouble(l -> {
                double dx = l.x() - camPos.x;
                double dy = l.y() - camPos.y;
                double dz = l.z() - camPos.z;
                return dx * dx + dy * dy + dz * dz;
            }));

            int maxLights = Math.min(localLights.size(), 512);
            List<PointLight> lights = new ArrayList<>(maxLights);
            for (int i = 0; i < maxLights; i++) {
                lights.add(localLights.get(i));
            }

            this.activeState = new GridState(
                    backBuffer,
                    originX,
                    originY,
                    originZ,
                    localSizeX,
                    localSizeY,
                    localSizeZ,
                    localRadius,
                    List.copyOf(lights)
            );
            this.activeBufferIndex.set(targetIndex);
        } finally {
            bufferLock.readLock().unlock();
        }
    }

    private PointLight createPointLight(final BlockState state, final float x, final float y, final float z, final int emission, final int mapCol) {
        return createPointLight(state, x, y, z, emission, mapCol, 0);
    }

    private PointLight createPointLight(final BlockState state, final float x, final float y, final float z, final int emission, final int mapCol, final int facingCode) {
        if (emission < 4 || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.GLOW_LICHEN)) {
            return null;
        }

        float r = ((mapCol >> 16) & 0xFF) / 255.0f;
        float g = ((mapCol >> 8) & 0xFF) / 255.0f;
        float b = (mapCol & 0xFF) / 255.0f;
        float maxC = Math.max(r, Math.max(g, b));
        if (maxC > 0.01f) {
            r /= maxC;
            g /= maxC;
            b /= maxC;
        } else {
            r = 1.0f; g = 0.65f; b = 0.22f;
        }

        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN)) {
            r = 1.00f; g = 0.65f; b = 0.22f;
        } else if (state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH) || state.is(Blocks.SOUL_LANTERN) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.SOUL_CAMPFIRE)) {
            r = 0.25f; g = 0.75f; b = 1.00f;
        } else if (state.is(Blocks.LAVA) || state.is(Blocks.LAVA_CAULDRON) || state.is(Blocks.FIRE) || state.is(Blocks.CAMPFIRE)) {
            r = 1.00f; g = 0.45f; b = 0.08f;
        } else if (state.is(Blocks.SEA_LANTERN)) {
            r = 0.40f; g = 0.92f; b = 1.00f;
        } else if (state.is(Blocks.CONDUIT)) {
            r = 0.30f; g = 0.85f; b = 1.00f;
        } else if (state.is(Blocks.GLOWSTONE) || state.is(Blocks.SHROOMLIGHT) || state.is(Blocks.OCHRE_FROGLIGHT)) {
            r = 1.00f; g = 0.85f; b = 0.40f;
        } else if (state.is(Blocks.VERDANT_FROGLIGHT)) {
            r = 0.40f; g = 1.00f; b = 0.60f;
        } else if (state.is(Blocks.PEARLESCENT_FROGLIGHT)) {
            r = 0.95f; g = 0.65f; b = 0.95f;
        } else if (state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH) || state.is(Blocks.REDSTONE_BLOCK)) {
            r = 1.00f; g = 0.08f; b = 0.05f;
        } else if (state.is(Blocks.END_ROD)) {
            r = 0.95f; g = 0.90f; b = 1.00f;
        } else if (state.is(Blocks.AMETHYST_CLUSTER)) {
            r = 0.85f; g = 0.50f; b = 0.95f;
        } else if (state.is(Blocks.CRYING_OBSIDIAN)) {
            r = 0.70f; g = 0.20f; b = 0.95f;
        }

        float radius = Math.max(emission * 0.90f, 6.0f);
        float baseIntensity = Math.min((emission / 15.0f) * 1.35f, 1.40f);
        float encodedIntensity = baseIntensity + (float) facingCode * 10.0f;

        return new PointLight(x, y, z, radius, r, g, b, encodedIntensity);
    }

    private volatile PointLight handheldLight = null;

    public void setHandheldLight(final PointLight light) {
        this.handheldLight = light;
    }

    public PointLight handheldLight() {
        return this.handheldLight;
    }

    public GridState activeState() {
        return activeState;
    }

    public List<PointLight> getCombinedLights(final PointLight handheldLight) {
        GridState state = activeState;
        List<PointLight> baseLights = state != null ? state.lights() : List.of();
        List<PointLight> combined = new ArrayList<>(baseLights.size() + 4);
        if (handheldLight != null) {
            combined.add(handheldLight);
        }


        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.level != null && mc.player != null) {
            net.minecraft.world.phys.Vec3 pPos = mc.player.position();
            net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(
                    pPos.x - 32.0, pPos.y - 20.0, pPos.z - 32.0,
                    pPos.x + 32.0, pPos.y + 20.0, pPos.z + 32.0
            );
            for (net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal : mc.level.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class, searchBox)) {
                if (crystal.isAlive()) {
                    float bob = net.minecraft.util.Mth.sin(crystal.time / 10.0f) * 0.1f;
                    combined.add(new PointLight((float) crystal.getX(), (float) crystal.getY() + 0.85f + bob, (float) crystal.getZ(), 14.0f, 0.95f, 0.45f, 1.00f, 1.40f));
                }
            }
        }

        List<PointLight> sortedBase = new ArrayList<>(baseLights);
        if (mc != null && mc.player != null) {
            net.minecraft.world.phys.Vec3 pPos = mc.player.position();
            sortedBase.sort(Comparator.comparingDouble(l -> {
                double dx = l.x() - pPos.x;
                double dy = l.y() - pPos.y;
                double dz = l.z() - pPos.z;
                return dx * dx + dy * dy + dz * dz;
            }));
        }

        int remaining = Math.max(0, 64 - combined.size());
        int limit = Math.min(sortedBase.size(), remaining);
        for (int i = 0; i < limit; i++) {
            combined.add(sortedBase.get(i));
        }
        return combined;
    }

    public MTLBuffer voxelBuffer() {
        GridState state = activeState;
        return state != null ? state.buffer() : null;
    }

    public int originX() {
        GridState state = activeState;
        return state != null ? state.originX() : 0;
    }

    public int originY() {
        GridState state = activeState;
        return state != null ? state.originY() : 0;
    }

    public int originZ() {
        GridState state = activeState;
        return state != null ? state.originZ() : 0;
    }

    public int sizeX() {
        GridState state = activeState;
        return state != null ? state.sizeX() : 0;
    }

    public int sizeY() {
        GridState state = activeState;
        return state != null ? state.sizeY() : 0;
    }

    public int sizeZ() {
        GridState state = activeState;
        return state != null ? state.sizeZ() : 0;
    }

    public int currentRadius() {
        GridState state = activeState;
        return state != null ? state.radius() : 0;
    }

    public List<PointLight> nearbyLights() {
        GridState state = activeState;
        return state != null ? state.lights() : List.of();
    }

    public void ensureBlockUvTable() {
        if (blockUvInitialized || blockUvContents == MemorySegment.NULL) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) return;
        net.minecraft.client.resources.model.ModelManager mm = mc.getModelManager();
        if (mm == null) return;
        net.minecraft.client.renderer.block.BlockStateModelSet modelSet = mm.getBlockStateModelSet();
        if (modelSet == null) return;

        for (net.minecraft.world.level.block.Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                int stateId = net.minecraft.world.level.block.Block.getId(state);
                if (stateId >= 0 && stateId < 32768) {
                    try {
                        net.minecraft.client.renderer.block.dispatch.BlockStateModel bsm = modelSet.get(state);
                        net.minecraft.client.renderer.texture.TextureAtlasSprite spriteTop = getSpriteForFace(bsm, net.minecraft.core.Direction.UP);
                        net.minecraft.client.renderer.texture.TextureAtlasSprite spriteBottom = getSpriteForFace(bsm, net.minecraft.core.Direction.DOWN);
                        net.minecraft.client.renderer.texture.TextureAtlasSprite spriteSide = getSpriteForFace(bsm, net.minecraft.core.Direction.NORTH);

                        net.minecraft.client.renderer.texture.TextureAtlasSprite particleSprite = null;
                        net.minecraft.client.resources.model.sprite.Material.Baked mat = modelSet.getParticleMaterial(state);
                        if (mat != null) particleSprite = mat.sprite();
                        if (particleSprite == null && bsm != null) {
                            mat = bsm.particleMaterial();
                            if (mat != null) particleSprite = mat.sprite();
                        }
                        if (particleSprite == null) {
                            mat = modelSet.getParticleMaterial(block.defaultBlockState());
                            if (mat != null) particleSprite = mat.sprite();
                        }

                        if (spriteTop == null) spriteTop = particleSprite;
                        if (spriteBottom == null) spriteBottom = spriteTop != null ? spriteTop : particleSprite;
                        if (spriteSide == null) spriteSide = spriteTop != null ? spriteTop : particleSprite;

                        if (spriteTop != null && spriteBottom != null && spriteSide != null) {
                            long offset = (long) stateId * 48L;
                            this.blockUvContents.set(JAVA_FLOAT, offset + 0L, spriteTop.getU0());
                            this.blockUvContents.set(JAVA_FLOAT, offset + 4L, spriteTop.getV0());
                            this.blockUvContents.set(JAVA_FLOAT, offset + 8L, spriteTop.getU1());
                            this.blockUvContents.set(JAVA_FLOAT, offset + 12L, spriteTop.getV1());

                            this.blockUvContents.set(JAVA_FLOAT, offset + 16L, spriteBottom.getU0());
                            this.blockUvContents.set(JAVA_FLOAT, offset + 20L, spriteBottom.getV0());
                            this.blockUvContents.set(JAVA_FLOAT, offset + 24L, spriteBottom.getU1());
                            this.blockUvContents.set(JAVA_FLOAT, offset + 28L, spriteBottom.getV1());

                            this.blockUvContents.set(JAVA_FLOAT, offset + 32L, spriteSide.getU0());
                            this.blockUvContents.set(JAVA_FLOAT, offset + 36L, spriteSide.getV0());
                            this.blockUvContents.set(JAVA_FLOAT, offset + 40L, spriteSide.getU1());
                            this.blockUvContents.set(JAVA_FLOAT, offset + 44L, spriteSide.getV1());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        this.blockUvInitialized = true;
    }

    public MTLBuffer blockUvBuffer() {
        ensureBlockUvTable();
        return blockUvBuffer;
    }

    public void ensureBlockBitmaskTable() {
        if (blockBitmaskInitialized || blockBitmaskContents == MemorySegment.NULL) return;
        for (net.minecraft.world.level.block.Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                int stateId = net.minecraft.world.level.block.Block.getId(state);
                if (stateId >= 0 && stateId < 32768) {
                    long mask = BlockBitmaskPalette.getBitmask(state);
                    blockBitmaskContents.set(JAVA_LONG, (long) stateId * 8L, mask);
                }
            }
        }
        this.blockBitmaskInitialized = true;
    }

    public MTLBuffer blockBitmaskBuffer() {
        ensureBlockBitmaskTable();
        return blockBitmaskBuffer;
    }


    private net.minecraft.client.renderer.texture.TextureAtlasSprite getSpriteForFace(net.minecraft.client.renderer.block.dispatch.BlockStateModel bsm, net.minecraft.core.Direction dir) {
        if (bsm == null) return null;
        java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new java.util.ArrayList<>();
        try {
            bsm.collectParts(net.minecraft.util.RandomSource.create(42L), parts);
            for (var part : parts) {
                var quads = part.getQuads(dir);
                if (quads != null && !quads.isEmpty() && quads.get(0).materialInfo() != null) {
                    return quads.get(0).materialInfo().sprite();
                }
            }
            for (var part : parts) {
                var quads = part.getQuads(null);
                if (quads != null && !quads.isEmpty() && quads.get(0).materialInfo() != null) {
                    return quads.get(0).materialInfo().sprite();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

}
