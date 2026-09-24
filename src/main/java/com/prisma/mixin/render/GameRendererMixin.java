package com.prisma.mixin.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.prisma.config.PrismaConfig;
import com.prisma.render.MetalBackend;
import com.prisma.render.MetalDevice;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private net.minecraft.client.renderer.state.GameRenderState gameRenderState;

    private final Matrix4f prisma$capturedViewProj = new Matrix4f();
    private final Matrix4f prisma$capturedInvViewProj = new Matrix4f();
    private boolean prisma$hasCapturedInvViewProj = false;
    private net.minecraft.world.phys.Vec3 prisma$capturedCamPos = null;

    @ModifyArg(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
            ),
            index = 0
    )
    private Matrix4f prisma$captureLevelProjection(Matrix4f projMatrix) {
        if (this.gameRenderState != null && this.gameRenderState.levelRenderState != null) {
            var camState = this.gameRenderState.levelRenderState.cameraRenderState;
            if (camState != null && camState.viewRotationMatrix != null) {
                Matrix4f viewProj = new Matrix4f(projMatrix).mul(camState.viewRotationMatrix);
                this.prisma$capturedViewProj.set(viewProj);
                viewProj.invert(this.prisma$capturedInvViewProj);
                this.prisma$hasCapturedInvViewProj = true;
                if (camState.pos != null) {
                    this.prisma$capturedCamPos = camState.pos;
                }
            }
        }
        return projMatrix;
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"
            )
    )
    private void prisma$onBeforeGui(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (this.minecraft.options != null && Boolean.TRUE.equals(this.minecraft.options.entityShadows().get())) {
            this.minecraft.options.entityShadows().set(false);
        }

        if (this.minecraft.options != null && com.prisma.config.PrismaConfig.INSTANCE.volumetricCloudsEnabled) {
            this.minecraft.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        }
        if (renderLevel && this.minecraft.level != null) {
            int debugMode = PrismaConfig.INSTANCE.debugView.getShaderMode();
            MetalDevice metalDevice = MetalBackend.getActiveDevice();
            if (metalDevice != null) {
                float fov = (float) this.minecraft.options.fov().get();
                float fovScale = (float) Math.tan(Math.toRadians(fov * 0.5));
                float aspect = (float) this.minecraft.getWindow().getWidth() / (float) Math.max(this.minecraft.getWindow().getHeight(), 1);
                RenderTarget mainTarget = ((GameRenderer) (Object) this).mainRenderTarget();
                if (mainTarget != null) {
                    net.minecraft.client.Camera camera = this.minecraft.gameRenderer.mainCamera();
                    float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
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
                    if (this.gameRenderState != null && this.gameRenderState.levelRenderState != null) {
                        var skyState = this.gameRenderState.levelRenderState.skyRenderState;
                        if (skyState != null) {
                            int skyCol = skyState.skyColor;
                            skyR = ((skyCol >> 16) & 0xFF) / 255.0f;
                            skyG = ((skyCol >> 8) & 0xFF) / 255.0f;
                            skyB = (skyCol & 0xFF) / 255.0f;

                            int sunCol = skyState.sunriseAndSunsetColor;
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

                    if (this.minecraft.level != null) {
                        if (this.minecraft.level.dimension() == net.minecraft.world.level.Level.NETHER) {
                            sunriseAlpha = -1.0f;
                        } else if (this.minecraft.level.dimension() == net.minecraft.world.level.Level.END) {
                            sunriseAlpha = -2.0f;
                        }

                        long dayTime = this.minecraft.level.getOverworldClockTime() % 24000L;
                        float timeFraction = ((float) dayTime + partialTick) / 24000.0f;
                        sunAngle = (timeFraction - 0.25f) * 2.0f * (float) Math.PI;
                    }
                    float cameraPitch = (float) Math.toRadians(camera.xRot());
                    float cameraYaw = (float) Math.toRadians(camera.yRot());

                    var voxelManager = com.prisma.voxel.VoxelGridManager.INSTANCE;
                    voxelManager.update(this.minecraft.level, camera, this.minecraft.level.getGameTime());

                    var camPos = camera.position();
                    double camX = this.prisma$capturedCamPos != null ? this.prisma$capturedCamPos.x : camPos.x;
                    double camY = this.prisma$capturedCamPos != null ? this.prisma$capturedCamPos.y : camPos.y;
                    double camZ = this.prisma$capturedCamPos != null ? this.prisma$capturedCamPos.z : camPos.z;
                    Matrix4fc invViewProj = this.prisma$hasCapturedInvViewProj ? this.prisma$capturedInvViewProj : null;
                    Matrix4fc viewProj = this.prisma$hasCapturedInvViewProj ? this.prisma$capturedViewProj : null;

                    var leftVec = camera.leftVector();
                    var upVec = camera.upVector();
                    var fwdVec = camera.forwardVector();

                    float camRightX = -leftVec.x();
                    float camRightY = -leftVec.y();
                    float camRightZ = -leftVec.z();

                    var player = this.minecraft.player;
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
                        playerBodyYaw = (float) Math.toRadians(pBodyDeg + 180.0f);
                        playerLimbSwing = (float) player.walkAnimation.position(partialTick);
                        playerLimbAmount = (float) player.walkAnimation.speed(partialTick);
                        playerIsCrouch = player.isCrouching() ? 1.0f : 0.0f;
                        playerHeadYawDelta = (float) Math.toRadians(pHeadDeg - pBodyDeg);
                        playerHeadPitch = (float) Math.toRadians(net.minecraft.util.Mth.lerp(partialTick, player.xRotO, player.getXRot()));
                        playerAttackAnim = player.getAttackAnim(partialTick);
                    } else {
                        playerX = (float) camX;
                        playerY = (float) (camY - 1.62f);
                        playerZ = (float) camZ;
                        playerHeight = 1.8f;
                        playerBodyYaw = 0.0f;
                    }

                    int activeMobCount = com.prisma.render.MobShadowManager.collectMobs(
                            this.minecraft.level,
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
                    boolean playerReflection = PrismaConfig.INSTANCE.playerReflectionEnabled;
                    float shadowQuality = (float) PrismaConfig.INSTANCE.reflectionBounces;
                    float penumbraSoftness = PrismaConfig.INSTANCE.penumbraSoftness;

                    if (debugMode != 0) {
                        metalDevice.mrtManager().applyDebugPass(
                                metalDevice.commandEncoder(),
                                mainTarget.getColorTexture(),
                                mainTarget.getDepthTexture(),
                                debugMode,
                                aspect,
                                fovScale,
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
                                penumbraSoftness,
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
                                voxelManager
                        );
                    } else {
                        float vxaoStrength = PrismaConfig.INSTANCE.vxaoEnabled ? PrismaConfig.INSTANCE.vxaoStrength : 0.0f;
                        boolean ptLights = PrismaConfig.INSTANCE.pointLightsEnabled;

                        float rainStrength = this.minecraft.level != null ? this.minecraft.level.getRainLevel(partialTick) : 0.0f;
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
                                penumbraSoftness,
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
                                vxaoStrength,
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
                                voxelManager
                        );
                    }
                }
            }
        }
    }
}