package com.prisma.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MobShadowManager {
    public static final int MAX_MOBS = 16;
    public static final int FLOATS_PER_MOB = 16;

    private static final float[] MOB_DATA = new float[MAX_MOBS * FLOATS_PER_MOB];
    private static final List<LivingEntity> NEARBY_CANDIDATES = new ArrayList<>(32);

    private MobShadowManager() {
    }

    public static int classifyMob(final LivingEntity entity) {
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        return switch (path) {
            case "zombie", "husk", "drowned", "zombie_villager" -> 0;
            case "skeleton", "wither_skeleton", "stray", "bogged" -> 1;
            case "villager", "witch", "pillager", "vindicator", "evoker", "illusioner" -> 2;
            case "creeper" -> 3;
            case "cow", "mooshroom" -> 4;
            case "pig" -> 5;
            case "sheep" -> 6;
            case "chicken" -> 7;
            case "spider", "cave_spider" -> 8;
            case "wolf" -> 9;
            case "cat", "ocelot" -> 10;
            default -> -1;
        };
    }

    public static int collectMobs(
            final ClientLevel level,
            final Player player,
            final double camX,
            final double camY,
            final double camZ,
            final float partialTick
    ) {
        if (level == null) return 0;

        NEARBY_CANDIDATES.clear();
        AABB searchBox = new AABB(
                camX - 24.0, camY - 16.0, camZ - 24.0,
                camX + 24.0, camY + 16.0, camZ + 24.0
        );

        for (Entity e : level.getEntities((Entity) null, searchBox, entity -> entity instanceof LivingEntity && entity != player && entity.isAlive())) {
            LivingEntity living = (LivingEntity) e;
            if (classifyMob(living) >= 0) {
                NEARBY_CANDIDATES.add(living);
            }
        }

        if (NEARBY_CANDIDATES.isEmpty()) {
            return 0;
        }

        if (NEARBY_CANDIDATES.size() > 1) {
            NEARBY_CANDIDATES.sort(Comparator.comparingDouble(m -> m.distanceToSqr(camX, camY, camZ)));
        }

        int count = Math.min(NEARBY_CANDIDATES.size(), MAX_MOBS);
        for (int i = 0; i < count; i++) {
            LivingEntity mob = NEARBY_CANDIDATES.get(i);
            int mobType = classifyMob(mob);
            int base = i * FLOATS_PER_MOB;

            float mx = (float) net.minecraft.util.Mth.lerp(partialTick, mob.xo, mob.getX());
            float my = (float) net.minecraft.util.Mth.lerp(partialTick, mob.yo, mob.getY());
            float mz = (float) net.minecraft.util.Mth.lerp(partialTick, mob.zo, mob.getZ());

            float bodyYawDeg = net.minecraft.util.Mth.rotLerp(partialTick, mob.yBodyRotO, mob.yBodyRot);
            float headYawDeg = net.minecraft.util.Mth.rotLerp(partialTick, mob.yHeadRotO, mob.yHeadRot);
            float bodyYaw = (float) Math.toRadians(bodyYawDeg);
            float headYawDelta = (float) Math.toRadians(headYawDeg - bodyYawDeg);
            float headPitch = (float) Math.toRadians(net.minecraft.util.Mth.lerp(partialTick, mob.xRotO, mob.getXRot()));
            float scale = mob.isBaby() ? 0.5f : 1.0f;

            float limbSwing = (float) mob.walkAnimation.position(partialTick);
            float limbAmount = (float) mob.walkAnimation.speed(partialTick);
            float isCrouch = mob.isCrouching() ? 1.0f : 0.0f;
            float isBaby = mob.isBaby() ? 1.0f : 0.0f;

            MOB_DATA[base + 0] = mx;
            MOB_DATA[base + 1] = my;
            MOB_DATA[base + 2] = mz;
            MOB_DATA[base + 3] = (float) mobType;

            MOB_DATA[base + 4] = bodyYaw;
            MOB_DATA[base + 5] = headYawDelta;
            MOB_DATA[base + 6] = headPitch;
            MOB_DATA[base + 7] = scale;

            MOB_DATA[base + 8] = limbSwing;
            MOB_DATA[base + 9] = limbAmount;
            MOB_DATA[base + 10] = isCrouch;
            MOB_DATA[base + 11] = isBaby;

            float isSwimming = (mob.isSwimming() || mob.isVisuallySwimming() || mob.getPose() == net.minecraft.world.entity.Pose.SWIMMING) ? 1.0f : 0.0f;

            MOB_DATA[base + 12] = isSwimming;
            MOB_DATA[base + 13] = 0.0f;
            MOB_DATA[base + 14] = 0.0f;
            MOB_DATA[base + 15] = 0.0f;
        }

        return count;
    }

    public static float[] getMobData() {
        return MOB_DATA;
    }
}
