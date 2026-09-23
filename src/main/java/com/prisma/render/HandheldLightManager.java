package com.prisma.render;

import com.prisma.voxel.PointLight;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class HandheldLightManager {
    private HandheldLightManager() {}

    public record LightEmission(int emission, float r, float g, float b) {}

    public static LightEmission getEmission(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(Items.TORCH)) return new LightEmission(14, 1.0f, 0.65f, 0.22f);
        if (stack.is(Items.LANTERN)) return new LightEmission(15, 1.0f, 0.70f, 0.24f);
        if (stack.is(Items.SOUL_TORCH)) return new LightEmission(10, 0.25f, 0.75f, 1.00f);
        if (stack.is(Items.SOUL_LANTERN)) return new LightEmission(10, 0.25f, 0.75f, 1.00f);
        if (stack.is(Items.CAMPFIRE)) return new LightEmission(15, 1.0f, 0.55f, 0.15f);
        if (stack.is(Items.SOUL_CAMPFIRE)) return new LightEmission(10, 0.25f, 0.75f, 1.00f);
        if (stack.is(Items.LAVA_BUCKET)) return new LightEmission(15, 1.0f, 0.45f, 0.08f);
        if (stack.is(Items.GLOWSTONE)) return new LightEmission(15, 1.0f, 0.85f, 0.40f);
        if (stack.is(Items.SEA_LANTERN)) return new LightEmission(15, 0.40f, 0.92f, 1.00f);
        if (stack.is(Items.REDSTONE_TORCH)) return new LightEmission(7, 1.0f, 0.15f, 0.05f);
        if (stack.is(Items.MAGMA_BLOCK)) return new LightEmission(8, 1.0f, 0.35f, 0.05f);
        if (stack.is(Items.GLOW_BERRIES)) return new LightEmission(14, 1.0f, 0.75f, 0.20f);
        if (stack.is(Items.AMETHYST_SHARD)) return new LightEmission(5, 0.80f, 0.40f, 0.95f);

        if (stack.getItem() instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            int em = state.getLightEmission();
            if (em > 0) {
                int color = com.prisma.voxel.BlockColorPalette.getColor(state, null, null);
                float r = ((color >> 16) & 0xFF) / 255.0f;
                float g = ((color >> 8) & 0xFF) / 255.0f;
                float b = (color & 0xFF) / 255.0f;
                return new LightEmission(em, r, g, b);
            }
        }
        return null;
    }

    public static PointLight getHandheldLight(
            final Player player,
            final Vec3 camPos,
            final float camRightX,
            final float camRightY,
            final float camRightZ,
            final Vec3 fwdVec,
            final Vec3 upVec
    ) {
        if (player == null || camPos == null) return null;

        ItemStack mainHand = player.getMainHandItem();
        LightEmission mainEm = getEmission(mainHand);
        ItemStack offHand = player.getOffhandItem();
        LightEmission offEm = getEmission(offHand);

        if (mainEm == null && offEm == null) return null;

        boolean useMain = mainEm != null && (offEm == null || mainEm.emission() >= offEm.emission());
        LightEmission em = useMain ? mainEm : offEm;
        float sideSign = useMain ? 1.0f : -1.0f;

        float fwdX = fwdVec != null ? (float) fwdVec.x : 0.0f;
        float fwdY = fwdVec != null ? (float) fwdVec.y : 0.0f;
        float fwdZ = fwdVec != null ? (float) fwdVec.z : 0.0f;

        float upX = upVec != null ? (float) upVec.x : 0.0f;
        float upY = upVec != null ? (float) upVec.y : 1.0f;
        float upZ = upVec != null ? (float) upVec.z : 0.0f;

        float hx = (float) camPos.x + camRightX * (0.35f * sideSign) + fwdX * 0.40f - upX * 0.20f;
        float hy = (float) camPos.y + camRightY * (0.35f * sideSign) + fwdY * 0.40f - upY * 0.20f;
        float hz = (float) camPos.z + camRightZ * (0.35f * sideSign) + fwdZ * 0.40f - upZ * 0.20f;

        float radius = Math.max(em.emission() * 0.90f, 6.0f);
        float intensity = Math.min((em.emission() / 15.0f) * 1.35f, 1.40f);

        return new PointLight(hx, hy, hz, radius, em.r(), em.g(), em.b(), intensity);
    }
}
