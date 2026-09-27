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
                    target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"
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
                com.prisma.render.PrismaDeferredRenderer.capturedViewProj.set(viewProj);
                com.prisma.render.PrismaDeferredRenderer.capturedInvViewProj.set(this.prisma$capturedInvViewProj);
                com.prisma.render.PrismaDeferredRenderer.hasCapturedInvViewProj = true;
                com.prisma.render.PrismaDeferredRenderer.capturedCamPos = this.prisma$capturedCamPos;
                com.prisma.render.PrismaDeferredRenderer.isLightingAppliedThisFrame = false;
            }
        }
        return projMatrix;
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;clearDepthTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V"
            )
    )
    private void prisma$onBeforeGui(CallbackInfo ci) {
        boolean renderLevel = true;
        DeltaTracker deltaTracker = this.minecraft.getDeltaTracker();
        if (this.minecraft.options != null && Boolean.TRUE.equals(this.minecraft.options.entityShadows().get())) {
            this.minecraft.options.entityShadows().set(false);
        }

        if (this.minecraft.options != null && com.prisma.config.PrismaConfig.INSTANCE.volumetricCloudsEnabled) {
            this.minecraft.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
        }
        if (this.minecraft.level != null) {
            RenderTarget mainTarget = ((GameRenderer) (Object) this).mainRenderTarget();
            if (mainTarget != null) {
                com.prisma.render.PrismaDeferredRenderer.performDeferredLighting(this.minecraft, mainTarget);
            }
        }
        com.prisma.render.PrismaDeferredRenderer.isLightingAppliedThisFrame = false;
        this.prisma$hasCapturedInvViewProj = false;
    }
}