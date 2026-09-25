/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.renderpearl.api.GpuFormat
 *  com.mojang.renderpearl.api.buffers.GpuBuffer
 *  com.mojang.renderpearl.api.buffers.GpuBuffer$Usage
 *  com.mojang.renderpearl.backend.api.BackendRenderPipeline
 *  com.mojang.renderpearl.api.pipeline.RenderPipeline
 *  com.mojang.blaze3d.preprocessor.GlslPreprocessor
 *  com.mojang.renderpearl.api.device.GpuDebugOptions
 *  com.mojang.renderpearl.backend.api.ShaderSource
 *  com.mojang.renderpearl.backend.api.ShaderType
 *  com.mojang.renderpearl.api.device.DeviceFeatures
 *  com.mojang.renderpearl.api.device.DeviceInfo
 *  com.mojang.renderpearl.api.device.DeviceLimits
 *  com.mojang.renderpearl.api.device.DeviceType
 *  com.mojang.renderpearl.backend.api.GpuDeviceBackend
 *  com.mojang.renderpearl.api.commands.GpuQueryPool
 *  com.mojang.renderpearl.backend.api.GpuSurfaceBackend
 *  com.mojang.renderpearl.api.device.HintsAndWorkarounds
 *  com.mojang.renderpearl.api.textures.AddressMode
 *  com.mojang.renderpearl.api.textures.FilterMode
 *  com.mojang.renderpearl.api.textures.GpuSampler
 *  com.mojang.renderpearl.api.textures.GpuTexture
 *  com.mojang.renderpearl.api.textures.GpuTexture$Usage
 *  com.mojang.renderpearl.api.textures.GpuTextureView
 *  com.mojang.renderpearl.backend.glsl.GlslCompiler
 *  com.mojang.renderpearl.backend.glsl.IntermediaryShaderModule
 *  com.mojang.renderpearl.backend.glsl.ShaderCompileException
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.minecraft.client.renderer.ShaderDefines
 *  net.minecraft.resources.Identifier
 *  org.jspecify.annotations.NonNull
 *  org.jspecify.annotations.Nullable
 */
package com.prisma.render;

import com.mojang.renderpearl.api.GpuFormat;
import java.util.function.BooleanSupplier;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.device.DeviceFeatures;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.DeviceLimits;
import com.mojang.renderpearl.api.device.DeviceType;
import com.mojang.renderpearl.api.device.GpuDebugOptions;
import com.mojang.renderpearl.api.device.HintsAndWorkarounds;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.backend.api.GpuSurfaceBackend;

import com.prisma.mtl.CAMetalLayer;
import com.prisma.mtl.MTLBuiltinPipelines;
import com.prisma.mtl.MTLCommandQueue;
import com.prisma.mtl.MTLCompareFunction;
import com.prisma.mtl.MTLDepthStencilDescriptor;
import com.prisma.mtl.MTLDevice;
import com.prisma.objc.Cocoa;
import com.prisma.objc.ObjC;
import com.prisma.render.MetalCommandEncoder;
import com.prisma.render.MetalCompiledRenderPipeline;
import com.prisma.render.MetalCrossShaderCompiler;
import com.prisma.render.MetalGpuBuffer;
import com.prisma.render.MetalGpuQueryPool;
import com.prisma.render.MetalGpuSampler;
import com.prisma.render.MetalGpuTexture;
import com.prisma.render.MetalGpuTextureView;
import com.prisma.render.MetalSurface;
import com.prisma.render.PrismaMRTManager;
import com.prisma.voxel.VoxelGridManager;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Environment(value=EnvType.CLIENT)
public final class MetalDevice
implements GpuDeviceBackend {
    void attachWindow(final Cocoa cocoa, final CAMetalLayer metalLayer) {
        this.cocoa = cocoa;
        this.metalLayer = metalLayer;
    }

    private static final Pattern BLOCK_COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/");
    private static final Pattern LINE_COMMENTS = Pattern.compile("(?m)//.*$");
    private final MemorySegment metalDeviceHandle;
    private final MTLDevice metalDevice;
    private CAMetalLayer metalLayer;
    private Cocoa cocoa;
    private final GpuDebugOptions debugOptions;
    private final MetalCommandEncoder commandEncoder;
    private final DeviceInfo deviceInfo;
    public final MTLCommandQueue commandQueue;
    private final String deviceName;
            private final Map<MslFunctionKey, MemorySegment> functionCache = new HashMap<MslFunctionKey, MemorySegment>();
    private final Object shaderStateLock = new Object();
    private final Map<Long, MemorySegment> depthStencilStates = new HashMap<Long, MemorySegment>();
    private final PrismaMRTManager mrtManager;

    MetalDevice(GpuDebugOptions debugOptions, MemorySegment metalDeviceHandle, String deviceName) {
        this.debugOptions = debugOptions;
        this.metalDeviceHandle = metalDeviceHandle;
        this.metalDevice = new MTLDevice(metalDeviceHandle);
        
        this.deviceName = deviceName;
        this.deviceInfo = this.buildDeviceInfo(deviceName);
        MTLCommandQueue.setDebugLabelsEnabled(this.useLabels());
        this.commandQueue = this.metalDevice.newCommandQueue();
        MTLBuiltinPipelines.init(this.metalDevice);
        com.prisma.voxel.VoxelGridManager.INSTANCE.init(this.metalDevice);
        this.mrtManager = new PrismaMRTManager(this);
        this.commandEncoder = new MetalCommandEncoder(this);
    }

    public @NonNull GpuSurfaceBackend createSurface(long windowHandle, java.util.function.BooleanSupplier vsync) {
        return new MetalSurface(this, this.metalLayer);
    }

    public @NonNull MetalCommandEncoder createCommandEncoder() {
        return this.commandEncoder;
    }

    public @NonNull MetalCommandEncoder commandEncoder() {
        return this.commandEncoder;
    }

    public @NonNull GpuSampler createSampler(@NonNull AddressMode addressModeU, @NonNull AddressMode addressModeV, @NonNull FilterMode minFilter, @NonNull FilterMode magFilter, int maxAnisotropy, @NonNull OptionalDouble maxLod) {
        return new MetalGpuSampler(this, addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod);
    }

    public @NonNull GpuTexture createTexture(@Nullable Supplier<String> label, @GpuTexture.Usage int usage, @NonNull GpuFormat format, int width, int height, int depthOrLayers, int mipLevels) {
        return this.createTexture(this.useLabels() && label != null ? label.get() : null, usage, format, width, height, depthOrLayers, mipLevels);
    }

    public @NonNull GpuTexture createTexture(@Nullable String label, @GpuTexture.Usage int usage, @NonNull GpuFormat format, int width, int height, int depthOrLayers, int mipLevels) {
        return new MetalGpuTexture(this, usage, label == null ? "" : label, format, width, height, depthOrLayers, mipLevels);
    }

    public @NonNull GpuTextureView createTextureView(@NonNull GpuTexture texture) {
        return this.createTextureView(texture, 0, texture.getMipLevels());
    }

    public @NonNull GpuTextureView createTextureView(@NonNull GpuTexture texture, int baseMipLevel, int mipLevels) {
        return new MetalGpuTextureView(texture, baseMipLevel, mipLevels);
    }

    public @NonNull GpuBuffer createBuffer(@Nullable Supplier<String> label, @GpuBuffer.Usage int usage, long size) {
        return new MetalGpuBuffer(this, usage, size);
    }

    public @NonNull GpuBuffer createBuffer(@Nullable Supplier<String> label, @GpuBuffer.Usage int usage, ByteBuffer data) {
        MetalGpuBuffer buffer = (MetalGpuBuffer)this.createBuffer(label, usage | 8, data.remaining());
        if (buffer.isCpuAccessible()) {
            buffer.writeDirect(0L, data);
        } else {
            this.commandEncoder.writeToBuffer(buffer.slice(), data.duplicate());
        }
        return buffer;
    }

    public @NonNull List<String> getLastDebugMessages() {
        return List.of();
    }

    public boolean isDebuggingEnabled() {
        return this.debugOptions.logLevel() > 0 || this.debugOptions.useLabels() || this.debugOptions.useValidationLayers();
    }

    boolean useLabels() {
        return this.debugOptions.useLabels();
    }

    public BackendRenderPipeline.Pending compilePipeline(final BackendRenderPipeline.CreateInfo pipelineCreateInfo) {
        MetalCompiledRenderPipeline pipeline = MetalCrossShaderCompiler.compile(this, pipelineCreateInfo);
        return () -> pipeline;
    }

    public void close() {
        this.waitForSubmittedGpuWork();
        this.commandEncoder.close();
        for (MemorySegment function : this.functionCache.values()) {
            if (!ObjC.isNil(function)) {
                ObjC.release(function);
            }
        }
        this.functionCache.clear();
        if (this.cocoa != null) {
            try {
                this.cocoa.clearViewLayer();
            } catch (Throwable ignored) {
            }
        }
        this.mrtManager.close();
        MTLBuiltinPipelines.close();
        this.commandQueue.close();
        for (MemorySegment state : this.depthStencilStates.values()) {
            ObjC.release(state);
        }
        this.depthStencilStates.clear();
        ObjC.release(this.metalDeviceHandle);
    }

    public PrismaMRTManager mrtManager() {
        return this.mrtManager;
    }

    public @NonNull GpuQueryPool createTimestampQueryPool(int size) {
        return new MetalGpuQueryPool(size);
    }

    public long getTimestampCalibrationOffset() {
        return 0L;
    }

    long getTimestampNow() {
        return System.nanoTime();
    }

    public @NonNull DeviceInfo getDeviceInfo() {
        return this.deviceInfo;
    }

    MemorySegment metalDeviceHandle() {
        return this.metalDeviceHandle;
    }

    MTLDevice metalDevice() {
        return this.metalDevice;
    }

    MemorySegment depthStencilState(MTLCompareFunction compareFunction, boolean writeDepth) {
        long key = compareFunction.value << 1 | (writeDepth ? 1L : 0L);
        MemorySegment cached = this.depthStencilStates.get(key);
        if (cached != null) {
            return cached;
        }
        try (MTLDepthStencilDescriptor descriptor = MTLDepthStencilDescriptor.create();){
            descriptor.depthCompareFunction(compareFunction);
            descriptor.depthWriteEnabled(writeDepth);
            MemorySegment state = this.metalDevice.newDepthStencilState(descriptor);
            this.depthStencilStates.put(key, state);
            MemorySegment memorySegment = state;
            return memorySegment;
        }
    }

    void waitForSubmittedGpuWork() {
        this.commandEncoder.waitForSubmittedGpuWork();
    }

    void queueResourceRelease(MemorySegment handle) {
        this.commandEncoder.queueForDestroy(() -> ObjC.release(handle));
    }

    MemorySegment getOrCompileFunction(final String msl, final String entryPoint) {
        MslFunctionKey key = new MslFunctionKey(msl, entryPoint);
        MemorySegment cached = this.functionCache.get(key);
        if (cached != null) {
            return cached;
        }
        synchronized (this.shaderStateLock) {
            cached = this.functionCache.get(key);
            if (cached != null) {
                return cached;
            }
            MemorySegment function = this.metalDevice.newFunction(msl, entryPoint);
            if (!ObjC.isNil(function)) {
                this.functionCache.put(key, function);
            }
            return function;
        }
    }

    private DeviceInfo buildDeviceInfo(String deviceName) {
        DeviceType type = DeviceType.INTEGRATED;
        java.util.Set extensions = java.util.Set.of();
        String osVersion = System.getProperty("os.version", "").trim();
        String driverDescription = "macOS " + osVersion;
        long maxMemoryAllocationSize = Math.min(this.metalDevice.maxBufferLength(), this.metalDevice.recommendedMaxWorkingSetSize());
        return new DeviceInfo(deviceName, "Apple", driverDescription, true, "Metal", 1.0f, new DeviceLimits(16, 256, 16384, maxMemoryAllocationSize, 0, 1, 65536), new DeviceFeatures(true, false, false, true, true, true, false, true), extensions, new HintsAndWorkarounds(false, false, false, false), type);
    }



    private record MslFunctionKey(String msl, String entryPoint) {
    }
}
