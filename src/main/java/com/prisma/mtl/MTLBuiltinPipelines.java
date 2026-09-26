/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  org.joml.Matrix4fc
 *  org.joml.Vector4fc
 *  org.jspecify.annotations.Nullable
 *  org.lwjgl.system.MemoryStack
 */
package com.prisma.mtl;

import com.prisma.config.PrismaConfig;
import com.prisma.mtl.CAMetalDrawable;
import com.prisma.mtl.CAMetalLayer;
import com.prisma.mtl.MTLBlendFactor;
import com.prisma.mtl.MTLBlendOperation;
import com.prisma.mtl.MTLColorWriteMask;
import com.prisma.mtl.MTLCommandBuffer;
import com.prisma.mtl.MTLCompareFunction;
import com.prisma.mtl.MTLDepthStencilDescriptor;
import com.prisma.mtl.MTLDevice;
import com.prisma.mtl.MTLFence;
import com.prisma.mtl.MTLPixelFormat;
import com.prisma.mtl.MTLPrimitiveType;
import com.prisma.mtl.MTLRenderCommandEncoder;
import com.prisma.mtl.MTLRenderPassDescriptor;
import com.prisma.mtl.MTLRenderPipelineDescriptor;
import com.prisma.mtl.MTLRenderStages;
import com.prisma.mtl.MTLSamplerAddressMode;
import com.prisma.mtl.MTLSamplerDescriptor;
import com.prisma.mtl.MTLSamplerMinMagFilter;
import com.prisma.mtl.MTLSamplerMipFilter;
import com.prisma.mtl.MTLTexture;
import com.prisma.objc.AutoreleasePool;
import com.prisma.objc.ObjC;
import com.prisma.voxel.PointLight;
import com.prisma.voxel.VoxelGridManager;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

@Environment(value=EnvType.CLIENT)
public final class MTLBuiltinPipelines {
    

    
    
    
    
    
    

    
    

        

    
        private static MTLDevice device;
    private static MemorySegment presentPipeline;
    private static MemorySegment presentLinearSampler;
    private static MemorySegment presentNearestSampler;
    private static final Map<Long, MemorySegment> clearPipelines;
    private static final Map<Long, MemorySegment> depthStencilStates;
    private static final Map<Long, MemorySegment> debugPipelines;
    private static MemorySegment deferredComputePipeline;
    private static final Map<Long, MemorySegment> deferredLightingPipelines;
    private static final Map<Long, MemorySegment> postProcessPipelines;

    private static String concat(String a, String b) {
        return a + b;
    }

    private MTLBuiltinPipelines() {
    }

    
    public static void reloadShaders() {
        if (presentPipeline != null) ObjC.release(presentPipeline);
        presentPipeline = MTLBuiltinPipelines.buildPipeline(PrismaShaderLoader.readShaderSource("present.metal"), "prisma_present_vs", "prisma_present_fs", MTLPixelFormat.BGRA8Unorm.value, MTLPixelFormat.Invalid.value, MTLColorWriteMask.All.value);
        
        for (MemorySegment p : clearPipelines.values()) ObjC.release(p);
        clearPipelines.clear();
        
        for (MemorySegment p : deferredLightingPipelines.values()) ObjC.release(p);
        deferredLightingPipelines.clear();
        
        if (deferredComputePipeline != null && !ObjC.isNil(deferredComputePipeline)) {
            ObjC.release(deferredComputePipeline);
            deferredComputePipeline = null;
        }

        for (MemorySegment p : postProcessPipelines.values()) ObjC.release(p);
        postProcessPipelines.clear();
        

    }

    public static void init(MTLDevice mtlDevice) {
        device = mtlDevice;
        presentPipeline = MTLBuiltinPipelines.buildPipeline(PrismaShaderLoader.readShaderSource("present.metal"), "prisma_present_vs", "prisma_present_fs", MTLPixelFormat.BGRA8Unorm.value, MTLPixelFormat.Invalid.value, MTLColorWriteMask.All.value);
        presentLinearSampler = MTLBuiltinPipelines.buildPresentSampler(MTLSamplerMinMagFilter.Linear);
        presentNearestSampler = MTLBuiltinPipelines.buildPresentSampler(MTLSamplerMinMagFilter.Nearest);
        MTLBuiltinPipelines.ensureClearPipeline(MTLPixelFormat.BGRA8Unorm.value, MTLPixelFormat.Depth32Float.value, true);
        MTLBuiltinPipelines.ensureClearPipeline(MTLPixelFormat.RGBA8Unorm.value, MTLPixelFormat.Depth32Float.value, true);
        MTLBuiltinPipelines.ensureClearPipeline(MTLPixelFormat.BGRA8Unorm.value, MTLPixelFormat.Invalid.value, true);
        MTLBuiltinPipelines.ensureDeferredComputePipeline();
        MTLBuiltinPipelines.ensurePostProcessPipeline(MTLPixelFormat.RGBA8Unorm.value);
        MTLBuiltinPipelines.ensurePostProcessPipeline(MTLPixelFormat.BGRA8Unorm.value);
    }

    public static void close() {
        if (!ObjC.isNil(presentPipeline)) {
            ObjC.release(presentPipeline);
            presentPipeline = MemorySegment.NULL;
        }
        if (!ObjC.isNil(presentLinearSampler)) {
            ObjC.release(presentLinearSampler);
            presentLinearSampler = MemorySegment.NULL;
        }
        if (!ObjC.isNil(presentNearestSampler)) {
            ObjC.release(presentNearestSampler);
            presentNearestSampler = MemorySegment.NULL;
        }
        if (!ObjC.isNil(deferredComputePipeline)) {
            ObjC.release(deferredComputePipeline);
            deferredComputePipeline = MemorySegment.NULL;
        }
        clearPipelines.values().forEach(ObjC::release);
        clearPipelines.clear();
        depthStencilStates.values().forEach(ObjC::release);
        depthStencilStates.clear();
        debugPipelines.values().forEach(ObjC::release);
        debugPipelines.clear();
        deferredLightingPipelines.values().forEach(ObjC::release);
        deferredLightingPipelines.clear();
        postProcessPipelines.values().forEach(ObjC::release);
        postProcessPipelines.clear();
        device = null;
    }

    static void clearDraw(MTLRenderCommandEncoder encoder, MemorySegment colorTexture, MemorySegment depthTexture, double viewportWidth, double viewportHeight, @Nullable Vector4fc clearColor, @Nullable Double clearDepth) {
        try (AutoreleasePool autoreleasePool = AutoreleasePool.push();){
            long depthFormat;
            MemorySegment sizeTexture = ObjC.isNil(colorTexture) ? depthTexture : colorTexture;
            MemorySegment memorySegment = sizeTexture;
            if (ObjC.isNil(sizeTexture)) {
                return;
            }
            long colorFormat = ObjC.isNil(colorTexture) ? MTLPixelFormat.Invalid.value : MTLTexture.pixelFormat(colorTexture);
            MemorySegment pipeline = MTLBuiltinPipelines.ensureClearPipeline(colorFormat, depthFormat = ObjC.isNil(depthTexture) ? MTLPixelFormat.Invalid.value : MTLTexture.pixelFormat(depthTexture), clearColor != null);
            if (ObjC.isNil(pipeline)) {
                return;
            }
            MemorySegment depthState = depthFormat != MTLPixelFormat.Invalid.value ? MTLBuiltinPipelines.ensureDepthStencilState(MTLCompareFunction.Always, clearDepth != null) : MemorySegment.NULL;
            long width = MTLTexture.width(sizeTexture);
            long height = MTLTexture.height(sizeTexture);
            if (width <= 0L || height <= 0L) {
                return;
            }
            MTLBuiltinPipelines.encodeClearDraw(encoder, pipeline, (long)viewportWidth, (long)viewportHeight, clearColor, 0L, 0L, width, height, depthState, clearDepth);
        }
    }

    static void clearColorDepthTexturesRegion(MTLCommandBuffer commandBuffer, MemorySegment colorTexture, Vector4fc clearColor, MemorySegment depthTexture, double clearDepth, int x, int y, int width, int height, MTLFence globalFence) {
        try (AutoreleasePool autoreleasePool = AutoreleasePool.push();){
            MTLRenderCommandEncoder encoder;
            if (width <= 0 || height <= 0) {
                return;
            }
            long textureWidth = Math.min(MTLTexture.width(colorTexture), MTLTexture.width(depthTexture));
            long textureHeight = Math.min(MTLTexture.height(colorTexture), MTLTexture.height(depthTexture));
            long clampedX = Math.max(x, 0);
            long clampedY = Math.max(y, 0);
            long clampedMaxX = Math.min((long)x + (long)width, textureWidth);
            long clampedMaxY = Math.min((long)y + (long)height, textureHeight);
            if (clampedX >= clampedMaxX || clampedY >= clampedMaxY) {
                return;
            }
            boolean fullRegion = clampedX == 0L && clampedY == 0L && clampedMaxX == textureWidth && clampedMaxY == textureHeight;
            try (MTLRenderPassDescriptor renderPass = new MTLRenderPassDescriptor();){
                renderPass.colorAttachment(0L, colorTexture, fullRegion ? 2L : 1L, 1L, clearColor);
                renderPass.depthAttachment(depthTexture, fullRegion ? 2L : 1L, 1L, clearDepth);
                if (MTLPixelFormat.hasStencil(MTLTexture.pixelFormat(depthTexture))) {
                    renderPass.stencilAttachment(depthTexture, 0L, 0L);
                }
                encoder = commandBuffer.makeRenderCommandEncoder(renderPass);
            }
            if (globalFence != null) {
                encoder.waitForFence(globalFence, MTLRenderStages.Fragment);
            }
            if (!fullRegion) {
                MemorySegment pipeline = MTLBuiltinPipelines.ensureClearPipeline(MTLTexture.pixelFormat(colorTexture), MTLTexture.pixelFormat(depthTexture), true);
                MemorySegment depthState = MTLBuiltinPipelines.ensureDepthStencilState(MTLCompareFunction.Always, true);
                if (ObjC.isNil(pipeline) || ObjC.isNil(depthState)) {
                    encoder.endEncoding();
                    return;
                }
                MTLBuiltinPipelines.encodeClearDraw(encoder, pipeline, textureWidth, textureHeight, clearColor, clampedX, clampedY, clampedMaxX - clampedX, clampedMaxY - clampedY, depthState, clearDepth);
            }
            if (globalFence != null) {
                encoder.updateFence(globalFence, MTLRenderStages.Fragment);
            }
            encoder.endEncoding();
        }
    }

    public static void encodeDeferredLightingPass(MTLCommandBuffer commandBuffer, MemorySegment targetColorTexture, MemorySegment albedoTexture, MemorySegment normalTexture, MemorySegment lightDataTexture, MemorySegment worldDepthTexture, MemorySegment handDepthTexture, MemorySegment playerSkinTexture, MemorySegment prevReservoirTex, MemorySegment currReservoirTex, MemorySegment velocityTex, float aspect, float fovScale, float sunAngle, float cameraPitch, float cameraYaw, org.joml.Matrix4fc prevViewProj, MTLFence globalFence) {
        MTLBuiltinPipelines.encodeDeferredLightingPass(commandBuffer, targetColorTexture, albedoTexture, normalTexture, lightDataTexture, worldDepthTexture, handDepthTexture, MemorySegment.NULL, playerSkinTexture, prevReservoirTex, currReservoirTex, velocityTex, aspect, fovScale, sunAngle, cameraPitch, cameraYaw, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.8f, 0.35f, 2.0f, false, true, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0, null, null, null, 1.0f, false, 0.53f, 0.7f, 1.0f, 0.0f, 1.0f, 0.4f, 0.2f, 0.0f, 1.0f, 16.0f, 0.0f, null, prevViewProj, globalFence);
    }

    public static void encodeDeferredLightingPass(MTLCommandBuffer commandBuffer, MemorySegment targetColorTexture, MemorySegment albedoTexture, MemorySegment normalTexture, MemorySegment lightDataTexture, MemorySegment worldDepthTexture, MemorySegment handDepthTexture, MemorySegment blockAtlasTexture, MemorySegment playerSkinTexture, MemorySegment prevReservoirTex, MemorySegment currReservoirTex, MemorySegment velocityTex, float aspect, float fovScale, float sunAngle, float cameraPitch, float cameraYaw, float camPosX, float camPosY, float camPosZ, float camRightX, float camRightY, float camRightZ, float playerPosX, float playerPosY, float playerPosZ, float playerHeight, float playerBodyYaw, float shadowQuality, boolean sunShadowsEnabled, boolean playerShadowEnabled, boolean playerReflectionEnabled, float playerLimbSwing, float playerLimbAmount, float playerIsCrouch, float playerAttackAnim, float playerHeadYawDelta, float playerHeadPitch, int activeMobCount, float[] mobData, org.joml.Matrix4fc invViewProj, org.joml.Matrix4fc viewProj, float vxaoStrength, boolean pointLightsEnabled, float skyR, float skyG, float skyB, float sunriseAlpha, float sunriseR, float sunriseG, float sunriseB, float starBrightness, float cloudsEnabled, float cloudSteps, float rainStrength, VoxelGridManager voxelManager, org.joml.Matrix4fc prevViewProj, MTLFence globalFence) {
        try (AutoreleasePool autoreleasePool = AutoreleasePool.push();){
            MTLComputeCommandEncoder encoder;
            if (ObjC.isNil(targetColorTexture) || ObjC.isNil(albedoTexture)) {
                return;
            }
            long width = MTLTexture.width(targetColorTexture);
            long height = MTLTexture.height(targetColorTexture);
            if (width <= 0L || height <= 0L) {
                return;
            }
            MemorySegment pipeline = MTLBuiltinPipelines.ensureDeferredComputePipeline();
            if (ObjC.isNil(pipeline)) {
                return;
            }
            encoder = commandBuffer.makeComputeCommandEncoder();
            if (globalFence != null) {
                encoder.waitForFence(globalFence);
            }
            
            encoder.setComputePipelineState(pipeline);
            encoder.setTexture(albedoTexture, 0L);
            encoder.setTexture(normalTexture, 1L);
            encoder.setTexture(lightDataTexture, 2L);
            encoder.setTexture(worldDepthTexture, 3L);
            encoder.setTexture(handDepthTexture, 4L);
            encoder.setTexture(blockAtlasTexture, 5L);
            encoder.setTexture(playerSkinTexture, 6L);
            encoder.setTexture(prevReservoirTex, 7L);
            encoder.setTexture(currReservoirTex, 8L);
            encoder.setTexture(velocityTex, 9L);
            encoder.setTexture(targetColorTexture, 10L);
            encoder.setSamplerState(presentLinearSampler, 0L);
            
            MTLBuiltinPipelines.bindVoxelUniformsForDeferred(encoder, voxelManager, camPosX, camPosY, camPosZ, camRightX, camRightY, camRightZ, playerPosX, playerPosY, playerPosZ, playerHeight, playerBodyYaw, shadowQuality, playerShadowEnabled, playerReflectionEnabled, playerLimbSwing, playerLimbAmount, playerIsCrouch, playerAttackAnim, playerHeadYawDelta, playerHeadPitch, activeMobCount, mobData, invViewProj, viewProj, vxaoStrength, pointLightsEnabled, prevViewProj);
            try (MemoryStack stack = MemoryStack.stackPush();){
                PrismaConfig cfg = PrismaConfig.INSTANCE;
                MemorySegment uniforms = MemorySegment.ofAddress(stack.nmalloc(16, 108)).reinterpret(108L);
                uniforms.set(ValueLayout.JAVA_FLOAT, 0L, aspect);
                uniforms.set(ValueLayout.JAVA_FLOAT, 4L, fovScale);
                uniforms.set(ValueLayout.JAVA_FLOAT, 8L, sunAngle);
                uniforms.set(ValueLayout.JAVA_FLOAT, 12L, cameraPitch);
                uniforms.set(ValueLayout.JAVA_FLOAT, 16L, cameraYaw);
                uniforms.set(ValueLayout.JAVA_FLOAT, 20L, cfg.sunShadowsEnabled ? 1.0f : 0.0f);
                float gameTime;
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null && mc.level != null) {
                    float partialTick = mc.getDeltaTracker() != null ? mc.getDeltaTracker().getGameTimeDeltaPartialTick(false) : 0.0f;
                    gameTime = ((float)(mc.level.getGameTime() % 2400000L) + partialTick) / 20.0f;
                } else {
                    gameTime = (float)(System.nanoTime() / 1000000L % 3600000L) / 1000.0f;
                }
                uniforms.set(ValueLayout.JAVA_FLOAT, 24L, gameTime);
                uniforms.set(ValueLayout.JAVA_FLOAT, 28L, cfg.waterWavesEnabled ? cfg.waterWaveStrength : 0.0f);
                uniforms.set(ValueLayout.JAVA_FLOAT, 32L, cfg.waterWaveSpeed);
                uniforms.set(ValueLayout.JAVA_FLOAT, 36L, cfg.waterAbsorptionStrength);
                uniforms.set(ValueLayout.JAVA_FLOAT, 40L, skyR);
                uniforms.set(ValueLayout.JAVA_FLOAT, 44L, skyG);
                uniforms.set(ValueLayout.JAVA_FLOAT, 48L, skyB);
                uniforms.set(ValueLayout.JAVA_FLOAT, 52L, sunriseAlpha);
                uniforms.set(ValueLayout.JAVA_FLOAT, 56L, sunriseR);
                uniforms.set(ValueLayout.JAVA_FLOAT, 60L, sunriseG);
                uniforms.set(ValueLayout.JAVA_FLOAT, 64L, sunriseB);
                uniforms.set(ValueLayout.JAVA_FLOAT, 68L, starBrightness);
                uniforms.set(ValueLayout.JAVA_FLOAT, 72L, 1024.0f);
                uniforms.set(ValueLayout.JAVA_FLOAT, 76L, cfg.reflectionPointLightShadows ? 1.0f : 0.0f);
                uniforms.set(ValueLayout.JAVA_FLOAT, 80L, cfg.reflectionDirectionalShadows ? 1.0f : 0.0f);
                uniforms.set(ValueLayout.JAVA_FLOAT, 84L, cfg.vxaoInReflections ? 1.0f : 0.0f);
                uniforms.set(ValueLayout.JAVA_FLOAT, 88L, cfg.volumetricCloudsEnabled ? 1.0f : 0.0f);
                uniforms.set(ValueLayout.JAVA_FLOAT, 92L, (float)cfg.cloudQualitySteps);
                uniforms.set(ValueLayout.JAVA_FLOAT, 96L, cfg.reflectionsEnabled ? 1.0f : 0.0f);
                uniforms.set(ValueLayout.JAVA_FLOAT, 100L, cfg.cloudsInReflections ? 1.0f : 0.0f);
                uniforms.set(ValueLayout.JAVA_FLOAT, 104L, rainStrength);
                encoder.setBytes(uniforms, 108L, 0L);
            }
            long tgWidth = (width + 15) / 16;
            long tgHeight = (height + 15) / 16;
            encoder.dispatchThreadgroups(tgWidth, tgHeight, 1, 16, 16, 1);
            if (globalFence != null) {
                encoder.updateFence(globalFence);
            }
            
            encoder.endEncoding();
        }
    }

    private static void bindVoxelUniformsForDeferred(MTLComputeCommandEncoder encoder, VoxelGridManager voxelManager, float camPosX, float camPosY, float camPosZ, float camRightX, float camRightY, float camRightZ, float playerPosX, float playerPosY, float playerPosZ, float playerHeight, float playerBodyYaw, float shadowQuality, boolean playerShadowEnabled, boolean playerReflectionEnabled, float playerLimbSwing, float playerLimbAmount, float playerIsCrouch, float playerAttackAnim, float playerHeadYawDelta, float playerHeadPitch, int activeMobCount, float[] mobData, org.joml.Matrix4fc invViewProj, org.joml.Matrix4fc viewProj, float vxaoStrength, boolean pointLightsEnabled, org.joml.Matrix4fc prevViewProj) {
        VoxelGridManager.GridState gridState = voxelManager != null ? voxelManager.activeState() : null;
        VoxelGridManager.GridState gridState2 = gridState;
        if (gridState != null && gridState.buffer() != null) {
            encoder.setBuffer(gridState.buffer(), 0L, 1L);
        } else {
            encoder.setBuffer(MemorySegment.NULL, 0L, 1L);
        }
        if (voxelManager != null && voxelManager.blockUvBuffer() != null) {
            encoder.setBuffer(voxelManager.blockUvBuffer().handle(), 0L, 3L);
        } else {
            encoder.setBuffer(MemorySegment.NULL, 0L, 3L);
        }
        if (voxelManager != null && voxelManager.blockBitmaskBuffer() != null) {
            encoder.setBuffer(voxelManager.blockBitmaskBuffer().handle(), 0L, 4L);
        } else {
            encoder.setBuffer(MemorySegment.NULL, 0L, 4L);
        }
        try (MemoryStack stack = MemoryStack.stackPush();){
            MemorySegment vUniforms = MemorySegment.ofAddress(stack.nmalloc(16, 37200)).reinterpret(37200L);
            vUniforms.fill((byte)0);
            if (gridState != null) {
                vUniforms.set(ValueLayout.JAVA_INT, 0L, gridState.originX());
                vUniforms.set(ValueLayout.JAVA_INT, 4L, gridState.originY());
                vUniforms.set(ValueLayout.JAVA_INT, 8L, gridState.originZ());
                vUniforms.set(ValueLayout.JAVA_INT, 12L, gridState.radius());
                vUniforms.set(ValueLayout.JAVA_INT, 16L, gridState.sizeX());
                vUniforms.set(ValueLayout.JAVA_INT, 20L, gridState.sizeY());
                vUniforms.set(ValueLayout.JAVA_INT, 24L, gridState.sizeZ());
                List<PointLight> lights = voxelManager != null ? voxelManager.getCombinedLights(voxelManager.handheldLight()) : gridState.lights();
                vUniforms.set(ValueLayout.JAVA_INT, 28L, Math.min(lights.size(), 64));
                vUniforms.set(ValueLayout.JAVA_FLOAT, 32L, camPosX);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 36L, camPosY);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 40L, camPosZ);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 44L, vxaoStrength);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 48L, camRightX);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 52L, camRightY);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 56L, camRightZ);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 60L, pointLightsEnabled ? 1.0f : 0.0f);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 64L, playerPosX);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 68L, playerPosY);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 72L, playerPosZ);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 76L, playerHeight);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 80L, playerBodyYaw);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 84L, shadowQuality);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 88L, com.prisma.config.PrismaConfig.INSTANCE.sdaaEnabled ? 1.0f : -1.0f);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 92L, playerShadowEnabled ? 1.0f : 0.0f);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 96L, playerLimbSwing);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 100L, playerLimbAmount);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 104L, playerIsCrouch);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 108L, playerAttackAnim);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 112L, playerHeadYawDelta);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 116L, playerHeadPitch);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 120L, com.prisma.config.PrismaConfig.INSTANCE.caveLighting);
                vUniforms.set(ValueLayout.JAVA_FLOAT, 124L, playerReflectionEnabled ? 1.0f : 0.0f);
                if (invViewProj != null) {
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 128L, invViewProj.m00());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 132L, invViewProj.m01());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 136L, invViewProj.m02());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 140L, invViewProj.m03());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 144L, invViewProj.m10());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 148L, invViewProj.m11());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 152L, invViewProj.m12());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 156L, invViewProj.m13());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 160L, invViewProj.m20());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 164L, invViewProj.m21());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 168L, invViewProj.m22());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 172L, invViewProj.m23());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 176L, invViewProj.m30());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 180L, invViewProj.m31());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 184L, invViewProj.m32());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 188L, invViewProj.m33());
                }
                if (viewProj != null) {
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 192L, viewProj.m00());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 196L, viewProj.m01());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 200L, viewProj.m02());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 204L, viewProj.m03());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 208L, viewProj.m10());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 212L, viewProj.m11());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 216L, viewProj.m12());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 220L, viewProj.m13());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 224L, viewProj.m20());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 228L, viewProj.m21());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 232L, viewProj.m22());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 236L, viewProj.m23());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 240L, viewProj.m30());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 244L, viewProj.m31());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 248L, viewProj.m32());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 252L, viewProj.m33());
                }
                if (prevViewProj != null) {
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 256L, prevViewProj.m00());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 260L, prevViewProj.m01());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 264L, prevViewProj.m02());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 268L, prevViewProj.m03());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 272L, prevViewProj.m10());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 276L, prevViewProj.m11());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 280L, prevViewProj.m12());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 284L, prevViewProj.m13());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 288L, prevViewProj.m20());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 292L, prevViewProj.m21());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 296L, prevViewProj.m22());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 300L, prevViewProj.m23());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 304L, prevViewProj.m30());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 308L, prevViewProj.m31());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 312L, prevViewProj.m32());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, 316L, prevViewProj.m33());
                }
                int maxL = Math.min(lights.size(), 64);
                for (int i = 0; i < maxL; ++i) {
                    PointLight pl = lights.get(i);
                    long offset = 320L + (long)i * 32L;
                    vUniforms.set(ValueLayout.JAVA_FLOAT, offset + 0L, pl.x());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, offset + 4L, pl.y());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, offset + 8L, pl.z());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, offset + 12L, pl.radius());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, offset + 16L, pl.r());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, offset + 20L, pl.g());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, offset + 24L, pl.b());
                    vUniforms.set(ValueLayout.JAVA_FLOAT, offset + 28L, pl.intensity());
                }
                vUniforms.set(ValueLayout.JAVA_INT, 33088L, Math.min(activeMobCount, 64));
                vUniforms.set(ValueLayout.JAVA_INT, 33092L, 0);
                vUniforms.set(ValueLayout.JAVA_INT, 33096L, 0);
                vUniforms.set(ValueLayout.JAVA_INT, 33100L, 0);
                if (mobData != null && activeMobCount > 0) {
                    int maxMobs = Math.min(activeMobCount, 64);
                    for (int i = 0; i < maxMobs; ++i) {
                        int mobBase = i * 16;
                        long dstBase = 33104L + (long)i * 64L;
                        for (int f = 0; f < 16; ++f) {
                            vUniforms.set(ValueLayout.JAVA_FLOAT, dstBase + (long)f * 4L, mobData[mobBase + f]);
                        }
                    }
                }
            }
            MTLBuffer buf = device.newBuffer(37200L, 0L);
            MemorySegment.copy(vUniforms, 0L, buf.contents().reinterpret(37200L), 0L, 37200L);
            encoder.setBuffer(buf.handle(), 0L, 2L);
            ObjC.release(buf.handle());
        }
    }

    static void encodePresentTextureToDrawable(MTLCommandBuffer commandBuffer, CAMetalLayer layer, MemorySegment sourceTexture, MTLFence globalFence) {
        try (AutoreleasePool autoreleasePool = AutoreleasePool.push();){
            MTLRenderCommandEncoder encoder;
            CAMetalDrawable drawable = layer.nextDrawable();
            if (drawable == null) {
                return;
            }
            MemorySegment drawableTexture = drawable.texture();
            try (MTLRenderPassDescriptor renderPass = new MTLRenderPassDescriptor();){
                renderPass.colorAttachment(0L, drawableTexture, 0L, 1L, null);
                encoder = commandBuffer.makeRenderCommandEncoder(renderPass);
            }
            if (globalFence != null) {
                encoder.waitForFence(globalFence, MTLRenderStages.Fragment);
            }
            long drawableWidth = MTLTexture.width(drawableTexture);
            long drawableHeight = MTLTexture.height(drawableTexture);
            encoder.setViewport(0.0, 0.0, drawableWidth, drawableHeight, 0.0, 1.0);
            encoder.setRenderPipelineState(presentPipeline);
            encoder.setFragmentTexture(sourceTexture, 0L);
            boolean requiresScaling = MTLTexture.width(sourceTexture) != drawableWidth || MTLTexture.height(sourceTexture) != drawableHeight;
            encoder.setFragmentSamplerState(requiresScaling ? presentLinearSampler : presentNearestSampler, 0L);
            encoder.drawPrimitives(MTLPrimitiveType.Triangle, 0, 3, 1, 0);
            if (globalFence != null) {
                encoder.updateFence(globalFence, MTLRenderStages.Fragment);
            }
            encoder.endEncoding();
            commandBuffer.presentDrawable(drawable);
        }
    }

    
    


    public static void encodePostProcessPass(MTLCommandBuffer commandBuffer, MemorySegment targetColorTexture, MemorySegment sourceHdrTexture, MemorySegment depthTexture, boolean fxaaEnabled, boolean motionBlurEnabled, float sunAngle, Matrix4fc viewProj, Matrix4fc prevViewProj, Matrix4fc invViewProj, float camPosX, float camPosY, float camPosZ, float prevCamPosX, float prevCamPosY, float prevCamPosZ, MTLFence globalFence) {
        try (AutoreleasePool autoreleasePool = AutoreleasePool.push();){
            MTLRenderCommandEncoder encoder;
            if (ObjC.isNil(targetColorTexture) || ObjC.isNil(sourceHdrTexture)) {
                return;
            }
            long width = MTLTexture.width(targetColorTexture);
            long height = MTLTexture.height(targetColorTexture);
            if (width <= 0L || height <= 0L) {
                return;
            }
            long colorFormat = MTLTexture.pixelFormat(targetColorTexture);
            MemorySegment pipeline = MTLBuiltinPipelines.ensurePostProcessPipeline(colorFormat);
            if (ObjC.isNil(pipeline)) {
                return;
            }
            try (MTLRenderPassDescriptor renderPass = new MTLRenderPassDescriptor();){
                renderPass.colorAttachment(0L, targetColorTexture, 0L, 1L, null);
                encoder = commandBuffer.makeRenderCommandEncoder(renderPass);
            }
            if (globalFence != null) {
                encoder.waitForFence(globalFence, MTLRenderStages.Fragment);
            }
            encoder.setViewport(0.0, 0.0, width, height, 0.0, 1.0);
            encoder.setRenderPipelineState(pipeline);
            encoder.setFragmentTexture(sourceHdrTexture, 0L);
            encoder.setFragmentTexture(depthTexture, 1L);
            encoder.setFragmentSamplerState(presentLinearSampler, 0L);
            try (MemoryStack stack = MemoryStack.stackPush();){
                int size = 240;
                MemorySegment uniforms = MemorySegment.ofAddress(stack.nmalloc(16, size)).reinterpret((long)size);
                        boolean isVXR = "VXR Default".equals(PrismaConfig.INSTANCE.shaderPack);

        float upscalingRatio = isVXR ? PrismaConfig.INSTANCE.upscalingRatio : 1.0f;
        
        long srcWidth = MTLTexture.width(sourceHdrTexture);
                long srcHeight = MTLTexture.height(sourceHdrTexture);
                uniforms.set(ValueLayout.JAVA_FLOAT, 0L, srcWidth > 0L ? 1.0f / (float)srcWidth : 0.0f);
                uniforms.set(ValueLayout.JAVA_FLOAT, 4L, srcHeight > 0L ? 1.0f / (float)srcHeight : 0.0f);
                uniforms.set(ValueLayout.JAVA_FLOAT, 8L, motionBlurEnabled ? 1.0f : 0.0f);
                float t = (float)(System.nanoTime() / 1000000L % 3600000L) / 1000.0f;
                uniforms.set(ValueLayout.JAVA_FLOAT, 12L, t);
                
                uniforms.set(ValueLayout.JAVA_FLOAT, 16L, sunAngle);
                uniforms.set(ValueLayout.JAVA_FLOAT, 20L, camPosX);
                uniforms.set(ValueLayout.JAVA_FLOAT, 24L, camPosY);
                uniforms.set(ValueLayout.JAVA_FLOAT, 28L, camPosZ);
                
                uniforms.set(ValueLayout.JAVA_FLOAT, 32L, prevCamPosX);
                uniforms.set(ValueLayout.JAVA_FLOAT, 36L, prevCamPosY);
                uniforms.set(ValueLayout.JAVA_FLOAT, 40L, prevCamPosZ);
                uniforms.set(ValueLayout.JAVA_FLOAT, 44L, com.prisma.config.PrismaConfig.INSTANCE.sdaaEnabled ? 1.0f : 0.0f);
                
                java.nio.ByteBuffer bb = uniforms.asByteBuffer().order(java.nio.ByteOrder.nativeOrder());
                viewProj.get(48, bb);
                prevViewProj.get(112, bb);
                invViewProj.get(176, bb);
                
                encoder.setFragmentBytes(uniforms, (long)size, 0L);
            }
            encoder.drawPrimitives(MTLPrimitiveType.Triangle, 0, 3, 1, 0);
            if (globalFence != null) {
                encoder.updateFence(globalFence, MTLRenderStages.Fragment);
            }
            encoder.endEncoding();
        }
    }

    private static void encodeClearDraw(MTLRenderCommandEncoder encoder, MemorySegment pipeline, long viewportWidth, long viewportHeight, @Nullable Vector4fc clearColor, long scissorX, long scissorY, long scissorWidth, long scissorHeight, MemorySegment depthState, @Nullable Double clearDepth) {
        encoder.setViewport(0.0, 0.0, viewportWidth, viewportHeight, 0.0, 1.0);
        encoder.setScissorRect(scissorX, scissorY, scissorWidth, scissorHeight);
        encoder.setRenderPipelineState(pipeline);
        if (!ObjC.isNil(depthState)) {
            encoder.setDepthStencilState(depthState);
        }
        try (MemoryStack stack = MemoryStack.stackPush();){
            MemorySegment uniforms = MemorySegment.ofAddress(stack.nmalloc(16, 48)).reinterpret(48L);
            float z = ObjC.isNil(depthState) || clearDepth == null ? 0.0f : (float)Math.clamp(clearDepth, 0.0, 1.0);
            uniforms.set(ValueLayout.JAVA_FLOAT, 0L, z);
            uniforms.set(ValueLayout.JAVA_FLOAT, 32L, clearColor == null ? 0.0f : clearColor.x());
            uniforms.set(ValueLayout.JAVA_FLOAT, 36L, clearColor == null ? 0.0f : clearColor.y());
            uniforms.set(ValueLayout.JAVA_FLOAT, 40L, clearColor == null ? 0.0f : clearColor.z());
            uniforms.set(ValueLayout.JAVA_FLOAT, 44L, clearColor == null ? 0.0f : clearColor.w());
            encoder.setVertexBytes(uniforms, 48L, 1L);
        }
        encoder.drawPrimitives(MTLPrimitiveType.Triangle, 0, 3, 1, 0);
    }

    private static MemorySegment ensureClearPipeline(long colorFormat, long depthFormat, boolean writeColor) {
        long key = colorFormat << 32 | depthFormat << 1 | (writeColor ? 1L : 0L);
        MemorySegment cached = clearPipelines.get(key);
        if (cached != null) {
            return cached;
        }
        MemorySegment pipeline = MTLBuiltinPipelines.buildPipeline(PrismaShaderLoader.readShaderSource("clear.metal"), "metallum_clear_vs", "metallum_clear_fs", colorFormat, depthFormat, writeColor ? MTLColorWriteMask.All.value : MTLColorWriteMask.None.value);
        if (!ObjC.isNil(pipeline)) {
            clearPipelines.put(key, pipeline);
        }
        return pipeline;
    }

    private static MemorySegment ensurePostProcessPipeline(long colorFormat) {
        MemorySegment cached = postProcessPipelines.get(colorFormat);
        if (cached != null) {
            return cached;
        }
        MemorySegment pipeline = MTLBuiltinPipelines.buildPipeline(PrismaShaderLoader.readShaderSource("postprocess.metal"), "prisma_postprocess_vs", "prisma_postprocess_fs", colorFormat, MTLPixelFormat.Invalid.value, MTLColorWriteMask.All.value);
        if (!ObjC.isNil(pipeline)) {
            postProcessPipelines.put(colorFormat, pipeline);
        }
        return pipeline;
    }

    private static MemorySegment ensureDeferredLightingPipeline(long colorFormat) {
        MemorySegment cached = deferredLightingPipelines.get(colorFormat);
        if (cached != null) {
            return cached;
        }
        MemorySegment pipeline = MTLBuiltinPipelines.buildPipeline(concat(PrismaShaderLoader.readShaderSource("voxel_common.metal") + "\n", PrismaShaderLoader.readShaderSource("deferred.metal")), "prisma_deferred_vs", "prisma_deferred_fs", colorFormat, MTLPixelFormat.Invalid.value, MTLColorWriteMask.All.value);
        if (!ObjC.isNil(pipeline)) {
            deferredLightingPipelines.put(colorFormat, pipeline);
        }
        return pipeline;
    }

    private static MemorySegment ensureDeferredComputePipeline() {
        if (!ObjC.isNil(deferredComputePipeline)) {
            return deferredComputePipeline;
        }
        MemorySegment function = device.newFunction(concat(PrismaShaderLoader.readShaderSource("voxel_common.metal") + "\n", PrismaShaderLoader.readShaderSource("deferred.metal")), "prisma_deferred_cs");
        if (ObjC.isNil(function)) {
            return MemorySegment.NULL;
        }
        deferredComputePipeline = device.newComputePipelineState(function);
        ObjC.release(function);
        return deferredComputePipeline;
    }

    private static MemorySegment ensureDepthStencilState(MTLCompareFunction compareOp, boolean writeDepth) {
        long key = compareOp.value << 1 | (writeDepth ? 1L : 0L);
        MemorySegment cached = depthStencilStates.get(key);
        if (cached != null) {
            return cached;
        }
        try (MTLDepthStencilDescriptor descriptor = MTLDepthStencilDescriptor.create();){
            MemorySegment memorySegment;
            descriptor.depthCompareFunction(compareOp);
            descriptor.depthWriteEnabled(writeDepth);
            MemorySegment state = device.newDepthStencilState(descriptor);
            if (!ObjC.isNil(state)) {
                depthStencilStates.put(key, state);
            }
            MemorySegment memorySegment2 = memorySegment = state;
            return memorySegment2;
        }
    }

    private static MemorySegment buildPipeline(String mslSource, String vertexEntry, String fragmentEntry, long colorFormat, long depthFormat, long writeMask) {
        MemorySegment pipeline;
        MemorySegment vertexFunction = device.newFunction(mslSource, vertexEntry);
        MemorySegment fragmentFunction = device.newFunction(mslSource, fragmentEntry);
        if (ObjC.isNil(vertexFunction) || ObjC.isNil(fragmentFunction)) {
            MTLBuiltinPipelines.releaseIfPresent(vertexFunction);
            MTLBuiltinPipelines.releaseIfPresent(fragmentFunction);
            return MemorySegment.NULL;
        }
        try (MTLRenderPipelineDescriptor descriptor = new MTLRenderPipelineDescriptor();){
            descriptor.setCompiledFunctions(vertexFunction, fragmentFunction);
            descriptor.setColorAttachmentFormat(0L, colorFormat);
            if (depthFormat != MTLPixelFormat.Invalid.value) {
                descriptor.setColorAttachmentFormat(1L, MTLPixelFormat.RGBA16Float.value);
                descriptor.disableBlending(1L, MTLColorWriteMask.None.value);
                descriptor.setColorAttachmentFormat(2L, MTLPixelFormat.RGBA8Unorm.value);
                descriptor.disableBlending(2L, MTLColorWriteMask.None.value);
            }
            descriptor.setDepthStencilFormats(depthFormat, MTLPixelFormat.Invalid.value);
            descriptor.disableBlending(0L, writeMask);
            pipeline = device.newRenderPipelineState(descriptor);
        }
        ObjC.release(vertexFunction);
        ObjC.release(fragmentFunction);
        return pipeline;
    }

    private static MemorySegment buildAdditivePipeline(String mslSource, String vertexEntry, String fragmentEntry, long colorFormat) {
        MemorySegment pipeline;
        MemorySegment vertexFunction = device.newFunction(mslSource, vertexEntry);
        MemorySegment fragmentFunction = device.newFunction(mslSource, fragmentEntry);
        if (ObjC.isNil(vertexFunction) || ObjC.isNil(fragmentFunction)) {
            MTLBuiltinPipelines.releaseIfPresent(vertexFunction);
            MTLBuiltinPipelines.releaseIfPresent(fragmentFunction);
            return MemorySegment.NULL;
        }
        try (MTLRenderPipelineDescriptor descriptor = new MTLRenderPipelineDescriptor();){
            descriptor.setCompiledFunctions(vertexFunction, fragmentFunction);
            descriptor.setColorAttachmentFormat(0L, colorFormat);
            descriptor.setBlendState(0L, MTLBlendFactor.One, MTLBlendFactor.One, MTLBlendOperation.Add, MTLBlendFactor.Zero, MTLBlendFactor.One, MTLBlendOperation.Add, MTLColorWriteMask.All.value);
            pipeline = device.newRenderPipelineState(descriptor);
        }
        ObjC.release(vertexFunction);
        ObjC.release(fragmentFunction);
        return pipeline;
    }

    private static MemorySegment buildPresentSampler(MTLSamplerMinMagFilter filter) {
        try (MTLSamplerDescriptor descriptor = MTLSamplerDescriptor.create();){
            MemorySegment memorySegment;
            descriptor.minFilter(filter);
            descriptor.magFilter(filter);
            descriptor.mipFilter(MTLSamplerMipFilter.NotMipmapped);
            descriptor.sAddressMode(MTLSamplerAddressMode.ClampToEdge);
            descriptor.tAddressMode(MTLSamplerAddressMode.ClampToEdge);
            MemorySegment memorySegment2 = memorySegment = device.newSamplerState(descriptor);
            return memorySegment2;
        }
    }

    private static void releaseIfPresent(MemorySegment object) {
        if (!ObjC.isNil(object)) {
            ObjC.release(object);
        }
    }

    static {
        presentPipeline = MemorySegment.NULL;
        presentLinearSampler = MemorySegment.NULL;
        presentNearestSampler = MemorySegment.NULL;
        clearPipelines = new HashMap<Long, MemorySegment>();
        depthStencilStates = new HashMap<Long, MemorySegment>();
        debugPipelines = new HashMap<Long, MemorySegment>();
        deferredLightingPipelines = new HashMap<Long, MemorySegment>();
        postProcessPipelines = new HashMap<Long, MemorySegment>();
    }
}
