package com.prisma.voxel;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class BlockBitmaskPalette {
    public static final int MAX_STATES = 32768;
    private static final long[] BITMASKS = new long[MAX_STATES];
    private static final boolean[] COMPUTED = new boolean[MAX_STATES];

    static {
        Arrays.fill(BITMASKS, 0L);
        Arrays.fill(COMPUTED, false);
    }

    private BlockBitmaskPalette() {}

    public static void clearCache() {
        Arrays.fill(BITMASKS, 0L);
        Arrays.fill(COMPUTED, false);
    }

    public static boolean isVegetation(final BlockState state) {
        net.minecraft.world.level.block.Block b = state.getBlock();
        return (b instanceof net.minecraft.world.level.block.VegetationBlock
                || state.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS)
                
                || state.is(net.minecraft.world.level.block.Blocks.TALL_GRASS)
                || state.is(net.minecraft.world.level.block.Blocks.FERN)
                || state.is(net.minecraft.world.level.block.Blocks.LARGE_FERN)
                || b instanceof net.minecraft.world.level.block.BushBlock
                || b instanceof net.minecraft.world.level.block.DoublePlantBlock
                || b instanceof net.minecraft.world.level.block.HangingRootsBlock
                || b instanceof net.minecraft.world.level.block.NetherWartBlock
                || b instanceof net.minecraft.world.level.block.SmallDripleafBlock
                || b instanceof net.minecraft.world.level.block.SporeBlossomBlock
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.COBWEB))
                && !(b instanceof net.minecraft.world.level.block.CropBlock)
                && !b.getClass().getSimpleName().contains("PinkPetal")
                && !b.getClass().getSimpleName().contains("LeafLitter");
    }

    public static long getBitmask(final BlockState state) {
        int id = Block.getId(state);
        if (id >= 0 && id < MAX_STATES) {
            if (COMPUTED[id]) {
                return BITMASKS[id];
            }
            long mask = computeBitmask(state);
            BITMASKS[id] = mask;
            COMPUTED[id] = true;
            return mask;
        }
        return computeBitmask(state);
    }

    public static long computeBitmask(final BlockState state) {
        if (state.isAir() || state.is(Blocks.LIGHT) || state.is(Blocks.STRUCTURE_VOID)) {
            return 0L;
        }

        
        
        if (isVegetation(state)) {
            return 0L;
        }

        
        
        if (state.getBlock() instanceof net.minecraft.world.level.block.TorchBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.WallTorchBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.RedstoneTorchBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.FenceBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.WallBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.StairBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) {
            return 0L;
        }

        
        
        if (state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND)) {
            return 0L;
        }

        
        if (state.getBlock() instanceof LeavesBlock) {
            return 0L;
        }

        
        
        
        VoxelShape shape;
        try {
            shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (Throwable t) {
            return 0L;
        }

        if (shape.isEmpty()) {
            return 0L;
        }

        List<AABB> aabbs = shape.toAabbs();
        if (aabbs.isEmpty()) {
            return 0L;
        }

        long mask = 0L;
        final float step = 0.25f;

        for (int z = 0; z < 4; z++) {
            float z0 = z * step;
            float z1 = z0 + step;
            for (int y = 0; y < 4; y++) {
                float y0 = y * step;
                float y1 = y0 + step;
                for (int x = 0; x < 4; x++) {
                    float x0 = x * step;
                    float x1 = x0 + step;

                    boolean intersects = false;
                    for (int i = 0; i < aabbs.size(); i++) {
                        AABB box = aabbs.get(i);
                        if (box.minX < x1 && box.maxX > x0 &&
                            box.minY < y1 && box.maxY > y0 &&
                            box.minZ < z1 && box.maxZ > z0) {
                            intersects = true;
                            break;
                        }
                    }

                    if (intersects) {
                        int bitIndex = (z * 4 + y) * 4 + x;
                        mask |= (1L << bitIndex);
                    }
                }
            }
        }

        return mask;
    }

    public static long[] getAllBitmasks() {
        return BITMASKS;
    }
}
