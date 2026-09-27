package com.prisma.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.prisma.config.PrismaConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

@Environment(EnvType.CLIENT)
public final class PrismaDeferredRenderer {
    public static final Matrix4f capturedViewProj = new Matrix4f();
    public static final Matrix4f capturedInvViewProj = new Matrix4f();
    public static boolean hasCapturedInvViewProj = false;
    public static Vec3 capturedCamPos = null;
    public static boolean isLightingAppliedThisFrame = false;

    private PrismaDeferredRenderer() {}

    public static void performDeferredLighting(Minecraft minecraft, RenderTarget mainTarget) {
        performDeferredLighting(minecraft, mainTarget, false);
    }

    public static void performDeferredLighting(Minecraft minecraft, RenderTarget mainTarget, boolean waterOnlyPass) {
        if (!waterOnlyPass && isLightingAppliedThisFrame) return;
        if (minecraft == null || minecraft.level == null || !hasCapturedInvViewProj) return;

        MetalDevice metalDevice = MetalBackend.getActiveDevice();
        if (metalDevice == null || mainTarget == null) return;

        net.minecraft.client.Camera camera = minecraft.gameRenderer.mainCamera();
        if (camera == null) return;

        DeltaTracker deltaTracker = minecraft.getDeltaTracker();
        float partialTick = deltaTracker != null ? deltaTracker.getGameTimeDeltaPartialTick(false) : 0.0f;

        float fov = (float) minecraft.options.fov().get();
        float fovScale = (float) Math.tan(Math.toRadians(fov * 0.5));
        float aspect = (float) minecraft.getWindow().getWidth() / (float) Math.max(minecraft.getWindow().getHeight(), 1);

        float sunAngle = 0.0f;
        try {
            var probe = camera.attributeProbe();
            if (probe != null) {
                Float deg = (Float) probe.getValue(net.minecraft.world.attribute.EnvironmentAttributes.SUN_ANGLE, partialTick);
                if (deg != null) {
                    sunAngle = (float) Math.toRadians(deg);
                }
            }
        } catch (Throwable ignored) {
        }

        float skyR = 0.53f, skyG = 0.70f, skyB = 1.00f;
        float sunriseAlpha = 0.0f, sunriseR = 1.0f, sunriseG = 0.4f, sunriseB = 0.2f;
        float starBrightness = 0.0f;

        var gameRenderState = minecraft.gameRenderer.gameRenderState();
        if (gameRenderState != null && gameRenderState.levelRenderState != null) {
            var skyState = gameRenderState.levelRenderState.skyRenderState;
            if (skyState != null) {
                if (skyState.skyColor != null) {
                    int skyCol = ((int) (skyState.skyColor.x() * 255.0f) << 16) | ((int) (skyState.skyColor.y() * 255.0f) << 8) | ((int) (skyState.skyColor.z() * 255.0f));
                    skyR = ((skyCol >> 16) & 0xFF) / 255.0f;
                    skyG = ((skyCol >> 8) & 0xFF) / 255.0f;
                    skyB = (skyCol & 0xFF) / 255.0f;
                }

                int sunCol = skyState.sunriseAndSunsetColor != null ? (((int) (skyState.sunriseAndSunsetColor.x() * 255.0f) << 16) | ((int) (skyState.sunriseAndSunsetColor.y() * 255.0f) << 8) | ((int) (skyState.sunriseAndSunsetColor.z() * 255.0f))) : 0;
                if (sunCol != 0) {
                    sunriseAlpha = ((sunCol >> 24) & 0xFF) / 255.0f;
                    sunriseR = ((sunCol >> 16) & 0xFF) / 255.0f;
                    sunriseG = ((sunCol >> 8) & 0xFF) / 255.0f;
                    sunriseB = (sunCol & 0xFF) / 255.0f;
                }
                starBrightness = skyState.starBrightness;
                if (sunAngle == 0.0f) {
                    sunAngle = skyState.sunAngle;
                }
            }
        }

        if (minecraft.level.dimension() == net.minecraft.world.level.Level.NETHER) {
            sunriseAlpha = -1.0f;
        } else if (minecraft.level.dimension() == net.minecraft.world.level.Level.END) {
            sunriseAlpha = -2.0f;
        }

        float cameraPitch = (float) Math.toRadians(camera.xRot());
        float cameraYaw = (float) Math.toRadians(camera.yRot());

        var voxelManager = com.prisma.voxel.VoxelGridManager.INSTANCE;
        voxelManager.update(minecraft.level, camera, minecraft.level.getGameTime());

        var camPos = camera.position();
        double camX = capturedCamPos != null ? capturedCamPos.x : camPos.x;
        double camY = capturedCamPos != null ? capturedCamPos.y : camPos.y;
        double camZ = capturedCamPos != null ? capturedCamPos.z : camPos.z;
        Matrix4fc invViewProj = capturedInvViewProj;
        Matrix4fc viewProj = capturedViewProj;

        var leftVec = camera.leftVector();
        var upVec = camera.upVector();
        var fwdVec = camera.forwardVector();

        float camRightX = -leftVec.x();
        float camRightY = -leftVec.y();
        float camRightZ = -leftVec.z();

        var player = minecraft.player;
        float playerX, playerY, playerZ, playerHeight, playerBodyYaw;
        float playerLimbSwing = 0.0f;
        float playerLimbAmount = 0.0f;
        float playerIsCrouch = 0.0f;
        float playerHeadYawDelta = 0.0f;
        float playerHeadPitch = 0.0f;
        float playerAttackAnim = 0.0f;

        if (player != null) {
            playerX = (float) net.minecraft.util.Mth.lerp(partialTick, player.xo, player.getX());
            playerY = (float) net.minecraft.util.Mth.lerp(partialTick, player.yo, player.getY());
            playerZ = (float) net.minecraft.util.Mth.lerp(partialTick, player.zo, player.getZ());
            playerHeight = (float) player.getBbHeight();
            float pBodyDeg = net.minecraft.util.Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
            float pHeadDeg = net.minecraft.util.Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
            playerBodyYaw = (float) Math.toRadians(pBodyDeg);
            playerLimbSwing = (float) player.walkAnimation.position(partialTick);
            playerLimbAmount = (float) player.walkAnimation.speed(partialTick);
            playerIsCrouch = player.isCrouching() ? 1.0f : 0.0f;
            playerHeadYawDelta = (float) Math.toRadians(pHeadDeg - pBodyDeg);
            playerHeadPitch = (float) Math.toRadians(net.minecraft.util.Mth.lerp(partialTick, player.xRotO, player.getXRot()));
            playerAttackAnim = 0.0f;
        } else {
            playerX = (float) camX;
            playerY = (float) (camY - 1.62f);
            playerZ = (float) camZ;
            playerHeight = 1.8f;
            playerBodyYaw = 0.0f;
        }

        int activeMobCount = com.prisma.render.MobShadowManager.collectMobs(
                minecraft.level,
                player,
                camX, camY, camZ,
                partialTick
        );
        float[] mobData = com.prisma.render.MobShadowManager.getMobData();

        var handheldLight = com.prisma.render.HandheldLightManager.getHandheldLight(
                player,
                camPos,
                camRightX,
                camRightY,
                camRightZ,
                new net.minecraft.world.phys.Vec3(fwdVec.x(), fwdVec.y(), fwdVec.z()),
                new net.minecraft.world.phys.Vec3(upVec.x(), upVec.y(), upVec.z())
        );
        voxelManager.setHandheldLight(handheldLight);

        boolean sunShadows = PrismaConfig.INSTANCE.sunShadowsEnabled;
        boolean playerShadow = PrismaConfig.INSTANCE.playerShadowEnabled;
        boolean playerReflection = true;
        float shadowQuality = 2.0f;

        float doubleAoStrength = PrismaConfig.INSTANCE.doubleAoEnabled ? PrismaConfig.INSTANCE.doubleAoStrength : 0.0f;
        boolean ptLights = PrismaConfig.INSTANCE.pointLightsEnabled;
        float rainStrength = minecraft.level.getRainLevel(partialTick);

        metalDevice.mrtManager().applyDeferredLightingPass(
                metalDevice.commandEncoder(),
                mainTarget.getColorTexture(),
                mainTarget.getDepthTexture(),
                aspect,
                fovScale,
                sunAngle,
                cameraPitch,
                cameraYaw,
                (float) camX,
                (float) camY,
                (float) camZ,
                camRightX,
                camRightY,
                camRightZ,
                playerX,
                playerY,
                playerZ,
                playerHeight,
                playerBodyYaw,
                shadowQuality,
                sunShadows,
                playerShadow,
                playerReflection,
                playerLimbSwing,
                playerLimbAmount,
                playerIsCrouch,
                playerAttackAnim,
                playerHeadYawDelta,
                playerHeadPitch,
                activeMobCount,
                mobData,
                invViewProj,
                viewProj,
                doubleAoStrength,
                ptLights,
                skyR,
                skyG,
                skyB,
                sunriseAlpha,
                sunriseR,
                sunriseG,
                sunriseB,
                starBrightness,
                rainStrength,
                voxelManager,
                waterOnlyPass
        );

        if (!waterOnlyPass) {
            isLightingAppliedThisFrame = true;
        }
    }
}
