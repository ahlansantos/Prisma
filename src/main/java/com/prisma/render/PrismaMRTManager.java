package com.prisma.render;

import com.prisma.mtl.*;
import com.prisma.objc.ObjC;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.foreign.MemorySegment;

@Environment(EnvType.CLIENT)
public final class PrismaMRTManager implements AutoCloseable {
    private final MetalDevice device;
    private MemorySegment normalTexture = MemorySegment.NULL;
    private MemorySegment fallbackNormalTexture = MemorySegment.NULL;
    private MemorySegment fallbackDepthTexture = MemorySegment.NULL;
    private MemorySegment lightDataTexture = MemorySegment.NULL;
    private MemorySegment fallbackLightDataTexture = MemorySegment.NULL;
    private MemorySegment hdrColorTexture = MemorySegment.NULL;

    private MemorySegment previousHdrTexture = MemorySegment.NULL;
    public org.joml.Matrix4f prevViewProj = new org.joml.Matrix4f();
    public org.joml.Matrix4f prevInvViewProj = new org.joml.Matrix4f();
    public org.joml.Vector3f prevCamPos = new org.joml.Vector3f();
    public long frameIndex = 0;

            private MemorySegment lastDepthTexture = MemorySegment.NULL;
    private long currentWidth = 0;
    private long currentHeight = 0;
    private long currentScale = 1;

    private MemorySegment savedWorldDepthTexture = MemorySegment.NULL;
    private MemorySegment savedHandDepthTexture = MemorySegment.NULL;
    private MemorySegment savedWorldColorTexture = MemorySegment.NULL;
    private long savedWidth = 0;
    private long savedHeight = 0;
    private int depthSnapshotCount = 0;
    private boolean hasWorldDepthSnapshot = false;
    private boolean hasHandDepthSnapshot = false;
    private boolean hasWorldColorSnapshot = false;

    public PrismaMRTManager(final MetalDevice device) {
        this.device = device;
        createFallbackTextures();
    }

    private void createFallbackTextures() {
        try (MTLTextureDescriptor desc = MTLTextureDescriptor.create()) {
            desc.textureType(MTLTextureType.Type2D);
            desc.pixelFormat(MTLPixelFormat.RGBA16Float);
            desc.width(1);
            desc.height(1);
            desc.mipmapLevelCount(1);
            desc.usage(MTLTextureUsage.ShaderRead.value | MTLTextureUsage.RenderTarget.value);
            desc.storageMode(MTLStorageMode.Private);
            this.fallbackNormalTexture = device.metalDevice().newTexture(desc);
        }

        try (MTLTextureDescriptor desc = MTLTextureDescriptor.create()) {
            desc.textureType(MTLTextureType.Type2D);
            desc.pixelFormat(MTLPixelFormat.Depth32Float);
            desc.width(1);
            desc.height(1);
            desc.mipmapLevelCount(1);
            desc.usage(MTLTextureUsage.ShaderRead.value | MTLTextureUsage.RenderTarget.value);
            desc.storageMode(MTLStorageMode.Private);
            this.fallbackDepthTexture = device.metalDevice().newTexture(desc);
        }

        try (MTLTextureDescriptor desc = MTLTextureDescriptor.create()) {
            desc.textureType(MTLTextureType.Type2D);
            desc.pixelFormat(MTLPixelFormat.RGBA32Float);
            desc.width(1);
            desc.height(1);
            desc.mipmapLevelCount(1);
            desc.usage(MTLTextureUsage.ShaderRead.value | MTLTextureUsage.RenderTarget.value);
            desc.storageMode(MTLStorageMode.Private);
            this.fallbackLightDataTexture = device.metalDevice().newTexture(desc);
        }
    }

    public void ensureMrtTextures(final long width, final long height) {
        long upscaleFactor = 1;

        if (width <= 0 || height <= 0) return;
        upscaleFactor = ("VXR Default".equals(com.prisma.config.PrismaConfig.INSTANCE.shaderPack) ? com.prisma.config.PrismaConfig.INSTANCE.upscalingMode : 1);
        if (upscaleFactor < 1) upscaleFactor = 1;
        if (this.currentWidth != width || this.currentHeight != height || this.currentScale != upscaleFactor || ObjC.isNil(this.normalTexture) || ObjC.isNil(this.lightDataTexture)) {
            this.currentScale = upscaleFactor;
            if (!ObjC.isNil(this.normalTexture)) {
                device.queueResourceRelease(this.normalTexture);
                this.normalTexture = MemorySegment.NULL;
            }
            if (!ObjC.isNil(this.lightDataTexture)) {
                device.queueResourceRelease(this.lightDataTexture);
                this.lightDataTexture = MemorySegment.NULL;
            }

        if (!ObjC.isNil(this.hdrColorTexture)) {
                device.queueResourceRelease(this.hdrColorTexture);
                this.hdrColorTexture = MemorySegment.NULL;
            }

        if (!ObjC.isNil(this.previousHdrTexture)) {
            device.queueResourceRelease(this.previousHdrTexture);
            this.previousHdrTexture = MemorySegment.NULL;
        }



            try (MTLTextureDescriptor desc = MTLTextureDescriptor.create()) {
                desc.textureType(MTLTextureType.Type2D);
                desc.pixelFormat(MTLPixelFormat.RGBA16Float);
                desc.width(width);
                desc.height(height);
                desc.mipmapLevelCount(1);
                desc.usage(MTLTextureUsage.ShaderRead.value | MTLTextureUsage.RenderTarget.value);
                desc.storageMode(MTLStorageMode.Private);
            }
            try (MTLTextureDescriptor desc = MTLTextureDescriptor.create()) {
                desc.textureType(MTLTextureType.Type2D);
                desc.pixelFormat(MTLPixelFormat.RGBA16Float);
                desc.width(width);
                desc.height(height);
                desc.mipmapLevelCount(1);
                desc.usage(MTLTextureUsage.ShaderRead.value | MTLTextureUsage.RenderTarget.value);
                desc.storageMode(MTLStorageMode.Private);
                this.normalTexture = device.metalDevice().newTexture(desc);
            }

            try (MTLTextureDescriptor desc = MTLTextureDescriptor.create()) {
                desc.textureType(MTLTextureType.Type2D);
                desc.pixelFormat(MTLPixelFormat.RGBA32Float);
                desc.width(width);
                desc.height(height);
                desc.mipmapLevelCount(1);
                desc.usage(MTLTextureUsage.ShaderRead.value | MTLTextureUsage.RenderTarget.value);
                desc.storageMode(MTLStorageMode.Private);
                this.lightDataTexture = device.metalDevice().newTexture(desc);
            }


            try (MTLTextureDescriptor desc = MTLTextureDescriptor.create()) {
                desc.textureType(MTLTextureType.Type2D);
                desc.pixelFormat(MTLPixelFormat.RGBA16Float);
                desc.width(width);
                desc.height(height);
                desc.mipmapLevelCount(1);
                desc.usage(MTLTextureUsage.ShaderRead.value | MTLTextureUsage.RenderTarget.value);
                desc.storageMode(MTLStorageMode.Private);
            }
            try (MTLTextureDescriptor desc = MTLTextureDescriptor.create()) {
                desc.textureType(MTLTextureType.Type2D);
                desc.pixelFormat(MTLPixelFormat.RGBA16Float);
                
                upscaleFactor = ("VXR Default".equals(com.prisma.config.PrismaConfig.INSTANCE.shaderPack) ? com.prisma.config.PrismaConfig.INSTANCE.upscalingMode : 1);
                if (upscaleFactor < 1) upscaleFactor = 1;
                long hdrW = Math.max(1L, width / upscaleFactor);
                long hdrH = Math.max(1L, height / upscaleFactor);
                
                desc.width(hdrW);
                desc.height(hdrH);
                desc.mipmapLevelCount(1);
                desc.usage(MTLTextureUsage.ShaderRead.value | MTLTextureUsage.RenderTarget.value);
                desc.storageMode(MTLStorageMode.Private);
                this.hdrColorTexture = device.metalDevice().newTexture(desc);
            }

            try (MTLTextureDescriptor desc = MTLTextureDescriptor.create()) {
                desc.textureType(MTLTextureType.Type2D);
                desc.pixelFormat(MTLPixelFormat.RGBA16Float);
                
                upscaleFactor = ("VXR Default".equals(com.prisma.config.PrismaConfig.INSTANCE.shaderPack) ? com.prisma.config.PrismaConfig.INSTANCE.upscalingMode : 1);
                if (upscaleFactor < 1) upscaleFactor = 1;
                long hdrW = Math.max(1L, width / upscaleFactor);
                long hdrH = Math.max(1L, height / upscaleFactor);
                
                desc.width(hdrW);
                desc.height(hdrH);
                desc.mipmapLevelCount(1);
                desc.usage(MTLTextureUsage.ShaderRead.value | MTLTextureUsage.RenderTarget.value);
                desc.storageMode(MTLStorageMode.Private);
                this.previousHdrTexture = device.metalDevice().newTexture(desc);
            }

            try (MTLTextureDescriptor desc = MTLTextureDescriptor.create()) {
                desc.textureType(MTLTextureType.Type2D);
                desc.pixelFormat(MTLPixelFormat.RGBA16Float);
                desc.width(width);
                desc.height(height);
                desc.mipmapLevelCount(1);
                desc.usage(MTLTextureUsage.ShaderRead.value | MTLTextureUsage.RenderTarget.value);
                desc.storageMode(MTLStorageMode.Private);
                                            }
            try (MTLTextureDescriptor desc = MTLTextureDescriptor.create()) {
                desc.textureType(MTLTextureType.Type2D);
                desc.pixelFormat(MTLPixelFormat.RGBA16Float);
                desc.width(width);
                desc.height(height);
                desc.mipmapLevelCount(1);
                desc.usage(MTLTextureUsage.ShaderRead.value | MTLTextureUsage.RenderTarget.value);
                desc.storageMode(MTLStorageMode.Private);
            }

            this.currentWidth = width;
            this.currentHeight = height;
        }
    }

    public MemorySegment ensureNormalTexture(final long width, final long height) {
        ensureMrtTextures(width, height);
        return normalTexture();
    }

    public MemorySegment normalTexture() {
        return ObjC.isNil(this.normalTexture) ? this.fallbackNormalTexture : this.normalTexture;
    }

    public MemorySegment ensureLightDataTexture(final long width, final long height) {
        ensureMrtTextures(width, height);
        return lightDataTexture();
    }

    public MemorySegment lightDataTexture() {
        return ObjC.isNil(this.lightDataTexture) ? this.fallbackLightDataTexture : this.lightDataTexture;
    }


    public MemorySegment hdrColorTexture() {
        return ObjC.isNil(this.hdrColorTexture) ? this.fallbackNormalTexture : this.hdrColorTexture;
    }

    public void setLastDepthTexture(final MemorySegment depthTexture, final long width, final long height) {
        if (!ObjC.isNil(depthTexture)) {
            if (ObjC.isNil(this.lastDepthTexture) || (width >= this.currentWidth && height >= this.currentHeight)) {
                this.lastDepthTexture = depthTexture;
            }
        }
    }

    public void ensureSavedTextures(final long width, final long height, final long colorPixelFormat) {
        if (width <= 0 || height <= 0) return;
        if (this.savedWidth != width || this.savedHeight != height) {
            if (!ObjC.isNil(this.savedWorldDepthTexture)) {
                device.queueResourceRelease(this.savedWorldDepthTexture);
                this.savedWorldDepthTexture = MemorySegment.NULL;
            }
            if (!ObjC.isNil(this.savedHandDepthTexture)) {
                device.queueResourceRelease(this.savedHandDepthTexture);
                this.savedHandDepthTexture = MemorySegment.NULL;
            }
            if (!ObjC.isNil(this.savedWorldColorTexture)) {
                device.queueResourceRelease(this.savedWorldColorTexture);
                this.savedWorldColorTexture = MemorySegment.NULL;
            }

            try (MTLTextureDescriptor desc = MTLTextureDescriptor.create()) {
                desc.textureType(MTLTextureType.Type2D);
                desc.pixelFormat(MTLPixelFormat.Depth32Float);
                desc.width(width);
                desc.height(height);
                desc.mipmapLevelCount(1);
                desc.usage(MTLTextureUsage.ShaderRead.value | MTLTextureUsage.RenderTarget.value);
                desc.storageMode(MTLStorageMode.Private);
                this.savedWorldDepthTexture = device.metalDevice().newTexture(desc);
                this.savedHandDepthTexture = device.metalDevice().newTexture(desc);
            }

            try (MTLTextureDescriptor desc = MTLTextureDescriptor.create()) {
                desc.textureType(MTLTextureType.Type2D);
                desc.pixelFormat(colorPixelFormat);
                desc.width(width);
                desc.height(height);
                desc.mipmapLevelCount(1);
                desc.usage(MTLTextureUsage.ShaderRead.value | MTLTextureUsage.RenderTarget.value);
                desc.storageMode(MTLStorageMode.Private);
                this.savedWorldColorTexture = device.metalDevice().newTexture(desc);
            }

            this.savedWidth = width;
            this.savedHeight = height;
        }
    }

    public void onWorldDepthClear(
            final MTLCommandBuffer commandBuffer,
            final MemorySegment sourceDepth,
            final MemorySegment sourceColor,
            final long width,
            final long height,
            final MTLFence fence
    ) {
        if (ObjC.isNil(sourceDepth) || width <= 0 || height <= 0) return;
        long colorFormat = !ObjC.isNil(sourceColor) ? MTLTexture.pixelFormat(sourceColor) : MTLPixelFormat.RGBA8Unorm.value;
        ensureSavedTextures(width, height, colorFormat);

        if (depthSnapshotCount == 0) {
            if (!ObjC.isNil(this.savedWorldDepthTexture)) {
                MTLBlitCommandEncoder blit = commandBuffer.makeBlitCommandEncoder();
                if (fence != null) blit.waitForFence(fence);
                blit.copyFromTextureToTexture(sourceDepth, 0L, 0L, 0L, 0L, width, height, this.savedWorldDepthTexture, 0L, 0L, 0L, 0L);
                if (!ObjC.isNil(sourceColor) && !ObjC.isNil(this.savedWorldColorTexture)) {
                    blit.copyFromTextureToTexture(sourceColor, 0L, 0L, 0L, 0L, width, height, this.savedWorldColorTexture, 0L, 0L, 0L, 0L);
                    this.hasWorldColorSnapshot = true;
                }
                if (fence != null) blit.updateFence(fence);
                blit.endEncoding();
                this.hasWorldDepthSnapshot = true;
            }
            depthSnapshotCount = 1;
        } else if (depthSnapshotCount == 1) {
            MTLBlitCommandEncoder blit = commandBuffer.makeBlitCommandEncoder();
            if (fence != null) blit.waitForFence(fence);
            if (!ObjC.isNil(this.savedHandDepthTexture)) {
                blit.copyFromTextureToTexture(sourceDepth, 0L, 0L, 0L, 0L, width, height, this.savedHandDepthTexture, 0L, 0L, 0L, 0L);
                this.hasHandDepthSnapshot = true;
            }
            if (!ObjC.isNil(sourceColor) && !ObjC.isNil(this.savedWorldColorTexture)) {
                blit.copyFromTextureToTexture(sourceColor, 0L, 0L, 0L, 0L, width, height, this.savedWorldColorTexture, 0L, 0L, 0L, 0L);
                this.hasWorldColorSnapshot = true;
            }
            if (fence != null) blit.updateFence(fence);
            blit.endEncoding();
            depthSnapshotCount = 2;
        }
    }

    public MemorySegment worldDepthTexture() {
        if (!ObjC.isNil(this.savedWorldDepthTexture) && this.hasWorldDepthSnapshot) {
            return this.savedWorldDepthTexture;
        }
        return ObjC.isNil(this.lastDepthTexture) ? this.fallbackDepthTexture : this.lastDepthTexture;
    }

    public MemorySegment handDepthTexture() {
        if (!ObjC.isNil(this.savedHandDepthTexture) && this.hasHandDepthSnapshot) {
            return this.savedHandDepthTexture;
        }
        return this.fallbackDepthTexture;
    }

    public MemorySegment worldColorTexture(final MemorySegment fallback) {
        if (!ObjC.isNil(this.savedWorldColorTexture) && this.hasWorldColorSnapshot) {
            return this.savedWorldColorTexture;
        }
        return fallback;
    }

    private static MemorySegment getPlayerSkinTexture(net.minecraft.client.Minecraft mc) {
        if (mc.player == null) return MemorySegment.NULL;
        net.minecraft.client.renderer.texture.TextureManager tm = mc.getTextureManager();
        var skinLoc = mc.player.getSkin().body().texturePath();
        if (skinLoc != null) {
            net.minecraft.client.renderer.texture.AbstractTexture skinTex = tm.getTexture(skinLoc);
            if (skinTex != null) {
                if (skinTex.getTexture() instanceof com.prisma.render.MetalGpuTexture mtlTex) {
                    return mtlTex.nativeHandle();
                }
                if (skinTex.getTextureView() instanceof com.prisma.render.MetalGpuTextureView mtlView) {
                    return mtlView.nativeHandle();
                }
            }
        }
        return MemorySegment.NULL;
    }

    public void applyDeferredLightingPass(
            final MetalCommandEncoder encoder,
            final com.mojang.blaze3d.textures.GpuTexture colorGpuTex,
            final com.mojang.blaze3d.textures.GpuTexture currentDepthGpuTex,
            final float aspect,
            final float fovScale,
            final float sunAngle,
            final float cameraPitch,
            final float cameraYaw,
            final float camPosX,
            final float camPosY,
            final float camPosZ,
            final float camRightX,
            final float camRightY,
            final float camRightZ,
            final float playerPosX,
            final float playerPosY,
            final float playerPosZ,
            final float playerHeight,
            final float playerBodyYaw,
            final float shadowQuality,
            final boolean sunShadowsEnabled,
            final boolean playerShadowEnabled,
            final boolean playerReflectionEnabled,
            final float playerLimbSwing,
            final float playerLimbAmount,
            final float playerIsCrouch,
            final float playerAttackAnim,
            final float playerHeadYawDelta,
            final float playerHeadPitch,
            final int activeMobCount,
            final float[] mobData,
            final org.joml.Matrix4fc invViewProj,
            final org.joml.Matrix4fc viewProj,
            final float vxaoStrength,
            final boolean pointLightsEnabled,
            final float skyR,
            final float skyG,
            final float skyB,
            final float sunriseAlpha,
            final float sunriseR,
            final float sunriseG,
            final float sunriseB,
            final float starBrightness,
            final float rainStrength,
            final com.prisma.voxel.VoxelGridManager voxelManager
    ) {
        if (!(colorGpuTex instanceof MetalGpuTexture colorTex)) {
            return;
        }

        encoder.flushPendingClear(colorTex);
        encoder.submitRenderPass();
        encoder.endEncoder();

        long width = colorTex.getWidth(0);
        long height = colorTex.getHeight(0);
        long colorFormat = colorTex.mtlPixelFormat().value;

        ensureSavedTextures(width, height, colorFormat);

        if (!ObjC.isNil(this.savedWorldColorTexture)) {
            MTLBlitCommandEncoder blit = encoder.commandBuffer().makeBlitCommandEncoder();
            if (encoder.fence() != null) blit.waitForFence(encoder.fence());
            blit.copyFromTextureToTexture(colorTex.nativeHandle(), 0L, 0L, 0L, 0L, width, height, this.savedWorldColorTexture, 0L, 0L, 0L, 0L);
            if (encoder.fence() != null) blit.updateFence(encoder.fence());
            blit.endEncoding();
            this.hasWorldColorSnapshot = true;
        }

        MemorySegment targetColor = colorTex.nativeHandle();
        MemorySegment currentDepth = currentDepthGpuTex instanceof MetalGpuTexture depthTex ? depthTex.nativeHandle() : MemorySegment.NULL;
        MemorySegment worldDepth = (!ObjC.isNil(this.savedWorldDepthTexture) && this.hasWorldDepthSnapshot)
                ? this.savedWorldDepthTexture
                : (!ObjC.isNil(currentDepth) ? currentDepth : this.fallbackDepthTexture);
        MemorySegment handDepth = (!ObjC.isNil(this.savedHandDepthTexture) && this.hasHandDepthSnapshot)
                ? this.savedHandDepthTexture
                : (!ObjC.isNil(currentDepth) ? currentDepth : worldDepth);
        MemorySegment albedo = worldColorTexture(targetColor);
        MemorySegment normal = normalTexture();
        MemorySegment lightData = lightDataTexture();
        MemorySegment hdrTarget = hdrColorTexture();


        

        
        this.frameIndex++;
        boolean doSpaceWarp = com.prisma.config.PrismaConfig.INSTANCE.spaceWarpEnabled && (this.frameIndex % 2 != 0);
        
        if (doSpaceWarp && !ObjC.isNil(this.previousHdrTexture)) {
            MTLBuiltinPipelines.encodeSpaceWarpPass(
                encoder.commandBuffer(),
                hdrTarget,
                this.previousHdrTexture,
                worldDepth,
                viewProj,
                this.prevViewProj,
                invViewProj,
                camPosX, camPosY, camPosZ,
                this.prevCamPos.x, this.prevCamPos.y, this.prevCamPos.z,
                encoder.fence()
            );
        } else {
            MTLBuiltinPipelines.encodeDeferredLightingPass(
                    encoder.commandBuffer(),
                    hdrTarget,
                    albedo,
                normal,
                lightData,
                worldDepth,
                handDepth,
                blockAtlasTexture(),
                    getPlayerSkinTexture(net.minecraft.client.Minecraft.getInstance()),
                aspect,
                fovScale,
                sunAngle,
                cameraPitch,
                cameraYaw,
                camPosX,
                camPosY,
                camPosZ,
                camRightX,
                camRightY,
                camRightZ,
                playerPosX,
                playerPosY,
                playerPosZ,
                playerHeight,
                playerBodyYaw,
                shadowQuality,
                sunShadowsEnabled,
                playerShadowEnabled,
                playerReflectionEnabled,
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
                pointLightsEnabled,
                skyR,
                skyG,
                skyB,
                sunriseAlpha,
                sunriseR,
                sunriseG,
                sunriseB,
                starBrightness,
                com.prisma.config.PrismaConfig.INSTANCE.volumetricCloudsEnabled ? 1.0f : 0.0f,
                (float) com.prisma.config.PrismaConfig.INSTANCE.cloudQualitySteps,
                rainStrength,
                voxelManager,
                encoder.fence()
        );
        }

                
        if (!doSpaceWarp && !ObjC.isNil(this.previousHdrTexture)) {
            MTLBlitCommandEncoder blit = encoder.commandBuffer().makeBlitCommandEncoder();
            
            long upscaleFactor = ("VXR Default".equals(com.prisma.config.PrismaConfig.INSTANCE.shaderPack) ? com.prisma.config.PrismaConfig.INSTANCE.upscalingMode : 1);
            if (upscaleFactor < 1) upscaleFactor = 1;
            long hdrW = Math.max(1L, width / upscaleFactor);
            long hdrH = Math.max(1L, height / upscaleFactor);
            if (encoder.fence() != null) blit.waitForFence(encoder.fence());
            blit.copyFromTextureToTexture(hdrTarget, 0L, 0L, 0L, 0L, hdrW, hdrH, this.previousHdrTexture, 0L, 0L, 0L, 0L);
            if (encoder.fence() != null) blit.updateFence(encoder.fence());
            blit.endEncoding();
            this.prevViewProj.set(viewProj);
            this.prevInvViewProj.set(invViewProj);
            this.prevCamPos.set(camPosX, camPosY, camPosZ);
        }
        
        MTLBuiltinPipelines.encodePostProcessPass(
                encoder.commandBuffer(),
                targetColor,
                hdrTarget,
                worldDepth,
                false,
                com.prisma.config.PrismaConfig.INSTANCE.motionBlurEnabled,
                sunAngle,
                viewProj,
                this.prevViewProj,
                invViewProj,
                camPosX, camPosY, camPosZ,
                this.prevCamPos.x, this.prevCamPos.y, this.prevCamPos.z,
                encoder.fence()
        );
    }

    public void resetFrame() {
        this.depthSnapshotCount = 0;
        this.hasWorldDepthSnapshot = false;
        this.hasHandDepthSnapshot = false;
        this.hasWorldColorSnapshot = false;
    }

    public long width() {
        return this.currentWidth;
    }

    public long height() {
        return this.currentHeight;
    }

    public MemorySegment blockAtlasTexture() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null) return MemorySegment.NULL;
            net.minecraft.client.renderer.texture.TextureManager tm = mc.getTextureManager();
            if (tm == null) return MemorySegment.NULL;
            net.minecraft.client.renderer.texture.AbstractTexture tex = tm.getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
            if (tex != null) {
                if (tex.getTexture() instanceof MetalGpuTexture mtlTex) {
                    return mtlTex.nativeHandle();
                }
                if (tex.getTextureView() instanceof MetalGpuTextureView mtlView) {
                    return mtlView.nativeHandle();
                }
            }
        } catch (Throwable ignored) {
        }
        return MemorySegment.NULL;
    }

    @Override
    public void close() {
        if (!ObjC.isNil(this.normalTexture)) {
            ObjC.release(this.normalTexture);
            this.normalTexture = MemorySegment.NULL;
        }
        if (!ObjC.isNil(this.fallbackNormalTexture)) {
            ObjC.release(this.fallbackNormalTexture);
            this.fallbackNormalTexture = MemorySegment.NULL;
        }
        if (!ObjC.isNil(this.fallbackDepthTexture)) {
            ObjC.release(this.fallbackDepthTexture);
            this.fallbackDepthTexture = MemorySegment.NULL;
        }
        if (!ObjC.isNil(this.lightDataTexture)) {
            ObjC.release(this.lightDataTexture);
            this.lightDataTexture = MemorySegment.NULL;
        }
        if (!ObjC.isNil(this.fallbackLightDataTexture)) {
            ObjC.release(this.fallbackLightDataTexture);
            this.fallbackLightDataTexture = MemorySegment.NULL;
        }

        if (!ObjC.isNil(this.hdrColorTexture)) {
            ObjC.release(this.hdrColorTexture);
            this.hdrColorTexture = MemorySegment.NULL;
        }

        if (!ObjC.isNil(this.previousHdrTexture)) {
            ObjC.release(this.previousHdrTexture);
            this.previousHdrTexture = MemorySegment.NULL;
        }

                        if (!ObjC.isNil(this.savedWorldDepthTexture)) {
            ObjC.release(this.savedWorldDepthTexture);
            this.savedWorldDepthTexture = MemorySegment.NULL;
        }
        if (!ObjC.isNil(this.savedHandDepthTexture)) {
            ObjC.release(this.savedHandDepthTexture);
            this.savedHandDepthTexture = MemorySegment.NULL;
        }
        if (!ObjC.isNil(this.savedWorldColorTexture)) {
            ObjC.release(this.savedWorldColorTexture);
            this.savedWorldColorTexture = MemorySegment.NULL;
        }
        this.lastDepthTexture = MemorySegment.NULL;
    }
}
