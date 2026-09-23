/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.GpuFormat
 *  com.mojang.blaze3d.buffers.GpuBuffer
 *  com.mojang.blaze3d.buffers.GpuBuffer$Usage
 *  com.mojang.blaze3d.pipeline.CompiledRenderPipeline
 *  com.mojang.blaze3d.pipeline.RenderPipeline
 *  com.mojang.blaze3d.preprocessor.GlslPreprocessor
 *  com.mojang.blaze3d.shaders.GpuDebugOptions
 *  com.mojang.blaze3d.shaders.ShaderSource
 *  com.mojang.blaze3d.shaders.ShaderType
 *  com.mojang.blaze3d.systems.DeviceFeatures
 *  com.mojang.blaze3d.systems.DeviceInfo
 *  com.mojang.blaze3d.systems.DeviceLimits
 *  com.mojang.blaze3d.systems.DeviceType
 *  com.mojang.blaze3d.systems.GpuDeviceBackend
 *  com.mojang.blaze3d.systems.GpuQueryPool
 *  com.mojang.blaze3d.systems.GpuSurfaceBackend
 *  com.mojang.blaze3d.systems.HintsAndWorkarounds
 *  com.mojang.blaze3d.textures.AddressMode
 *  com.mojang.blaze3d.textures.FilterMode
 *  com.mojang.blaze3d.textures.GpuSampler
 *  com.mojang.blaze3d.textures.GpuTexture
 *  com.mojang.blaze3d.textures.GpuTexture$Usage
 *  com.mojang.blaze3d.textures.GpuTextureView
 *  com.mojang.blaze3d.vulkan.glsl.GlslCompiler
 *  com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule
 *  com.mojang.blaze3d.vulkan.glsl.ShaderCompileException
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.minecraft.client.renderer.ShaderDefines
 *  net.minecraft.resources.Identifier
 *  org.jspecify.annotations.NonNull
 *  org.jspecify.annotations.Nullable
 */
package com.prisma.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.DeviceFeatures;
import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.DeviceLimits;
import com.mojang.blaze3d.systems.DeviceType;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.systems.HintsAndWorkarounds;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.glsl.GlslCompiler;
import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import com.mojang.blaze3d.vulkan.glsl.ShaderCompileException;
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
    private static final Pattern BLOCK_COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/");
    private static final Pattern LINE_COMMENTS = Pattern.compile("(?m)//.*$");
    private final MemorySegment metalDeviceHandle;
    private final MTLDevice metalDevice;
    private final CAMetalLayer metalLayer;
    private final Cocoa cocoa;
    private final GpuDebugOptions debugOptions;
    private final MetalCommandEncoder commandEncoder;
    private final DeviceInfo deviceInfo;
    public final MTLCommandQueue commandQueue;
    private final Map<RenderPipeline, MetalCompiledRenderPipeline> compiledPipelines = new IdentityHashMap<RenderPipeline, MetalCompiledRenderPipeline>();
    private final Map<ShaderCompilationKey, IntermediaryShaderModule> shaderCache = new HashMap<ShaderCompilationKey, IntermediaryShaderModule>();
    private final Map<MslFunctionKey, MemorySegment> functionCache = new HashMap<MslFunctionKey, MemorySegment>();
    private final Map<Long, MemorySegment> depthStencilStates = new HashMap<Long, MemorySegment>();
    private final ShaderSource defaultShaderSource;
    private final PrismaMRTManager mrtManager;

    MetalDevice(ShaderSource defaultShaderSource, GpuDebugOptions debugOptions, MemorySegment metalDeviceHandle, CAMetalLayer metalLayer, String deviceName, Cocoa cocoa) {
        this.defaultShaderSource = defaultShaderSource;
        this.debugOptions = debugOptions;
        this.metalDeviceHandle = metalDeviceHandle;
        this.metalDevice = new MTLDevice(metalDeviceHandle);
        this.metalLayer = metalLayer;
        this.cocoa = cocoa;
        MTLCommandQueue.setDebugLabelsEnabled(this.useLabels());
        this.commandQueue = this.metalDevice.newCommandQueue();
        MTLBuiltinPipelines.init(this.metalDevice);
        VoxelGridManager.INSTANCE.init(this.metalDevice);
        this.mrtManager = new PrismaMRTManager(this);
        this.commandEncoder = new MetalCommandEncoder(this);
        this.deviceInfo = this.buildDeviceInfo(deviceName);
    }

    public @NonNull GpuSurfaceBackend createSurface(long windowHandle) {
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
        return this.createTexture(this.resolveDebugLabel(label), usage, format, width, height, depthOrLayers, mipLevels);
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

    public @NonNull CompiledRenderPipeline precompilePipeline(@NonNull RenderPipeline pipeline, @Nullable ShaderSource shaderSource) {
        ShaderSource effectiveSource = shaderSource == null ? this.defaultShaderSource : shaderSource;
        return this.compiledPipelines.computeIfAbsent(pipeline, p -> MetalCrossShaderCompiler.compile(this, p, effectiveSource));
    }

    public void clearPipelineCache() {
        this.waitForSubmittedGpuWork();
        this.compiledPipelines.values().forEach(MetalCompiledRenderPipeline::close);
        this.compiledPipelines.clear();
        this.shaderCache.values().forEach(IntermediaryShaderModule::close);
        this.shaderCache.clear();
        for (MemorySegment function : this.functionCache.values()) {
            if (ObjC.isNil(function)) continue;
            ObjC.release(function);
        }
        this.functionCache.clear();
    }

    public void close() {
        this.waitForSubmittedGpuWork();
        this.commandEncoder.close();
        this.clearPipelineCache();
        try {
            this.cocoa.clearViewLayer();
        }
        catch (Throwable throwable) {
            // empty catch block
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

    public long getTimestampNow() {
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

    MetalCompiledRenderPipeline getOrCompilePipeline(RenderPipeline pipeline) {
        return this.compiledPipelines.computeIfAbsent(pipeline, p -> MetalCrossShaderCompiler.compile(this, p, this.defaultShaderSource));
    }

    IntermediaryShaderModule getOrCompileShader(Identifier id, ShaderType type, ShaderDefines defines, ShaderSource shaderSource) {
        ShaderCompilationKey key = new ShaderCompilationKey(id, type, defines);
        return this.shaderCache.computeIfAbsent(key, k -> {
            IntermediaryShaderModule intermediaryShaderModule;
            String source = shaderSource.get(k.id(), k.type());
            if (source == null) {
                return IntermediaryShaderModule.INVALID;
            }
            String patchedSource = MetalDevice.patchShaderSource(k.id(), k.type(), source);
            String sourceWithDefines = MetalDevice.prepareShaderSource(patchedSource, k.defines());
            GlslCompiler glslCompiler = new GlslCompiler();
            try {
                intermediaryShaderModule = glslCompiler.createIntermediary(k.id().toDebugFileName(), sourceWithDefines, k.type());
            }
            catch (Throwable t$) {
                try {
                    try {
                        glslCompiler.close();
                    }
                    catch (Throwable x2) {
                        t$.addSuppressed(x2);
                    }
                    throw t$;
                }
                catch (ShaderCompileException e) {
                    throw new IllegalStateException("Failed to compile shader " + String.valueOf(k.id()), e);
                }
            }
            glslCompiler.close();
            return intermediaryShaderModule;
        });
    }

    private static String patchShaderSource(Identifier id, ShaderType type, String source) {
        if ("sodium".equals(id.getNamespace()) && id.getPath().contains("block_layer_")) {
            if (type == ShaderType.VERTEX) {
                String patched = source;
                if (patched.contains("out vec4 v_Color;")) {
                    patched = patched.replace("out vec4 v_Color;", "out vec4 v_Color;\nout vec2 v_LightCoord;\nout vec3 v_WorldPosition;");
                }
                if (patched.contains("v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);")) {
                    patched = patched.replace("v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);", "v_Color = _vert_color;\n    v_LightCoord = _vert_tex_light_coord;\n    v_WorldPosition = position;");
                }
                return (String) patched;
            }
            if (type == ShaderType.FRAGMENT) {
                String patched = source;
                if (patched.contains("in vec4 v_Color;")) {
                    patched = patched.replace("in vec4 v_Color;", "in vec4 v_Color;\nin vec2 v_LightCoord;\nin vec3 v_WorldPosition;");
                }
                if (patched.contains("out vec4 fragColor;") && (patched = patched.replace("out vec4 fragColor;", "layout(location = 0) out vec4 fragColor;\nlayout(location = 1) out vec4 fragNormal;\nlayout(location = 2) out vec4 fragLight;")).contains("fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog, fadeFactor);")) {
                    patched = patched.replace("fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog, fadeFactor);", "fragColor = color;\n    vec3 fdx = dFdx(v_WorldPosition);\n    vec3 fdy = dFdy(v_WorldPosition);\n    vec3 worldNorm = normalize(cross(fdx, fdy));\n    fragNormal = vec4(worldNorm, 0.0);\n    fragLight = vec4(v_LightCoord, 1.0, gl_FragCoord.z);\n");
                }
                return (String) patched;
            }
        } else if ("minecraft".equals(id.getNamespace()) && !id.getPath().contains("shadow") && (id.getPath().contains("rendertype_entity_") || id.getPath().endsWith("core/entity"))) {
            if (type == ShaderType.VERTEX) {
                int lastBrace;
                Object patched = source;
                if (((String)patched).contains("out vec2 texCoord0;") && ((String)patched).contains("in vec3 Normal;") && (lastBrace = ((String)(patched = ((String)patched).replace("out vec2 texCoord0;", "out vec2 texCoord0;\nout vec3 v_EntityNormal;"))).lastIndexOf(125)) != -1) {
                    patched = ((String)patched).substring(0, lastBrace) + "    v_EntityNormal = Normal;\n}\n" + ((String)patched).substring(lastBrace + 1);
                }
                return (String) patched;
            }
            if (type == ShaderType.FRAGMENT) {
                Object patched = source;
                if (((String)patched).contains("out vec4 fragColor;")) {
                    patched = ((String)patched).replace("out vec4 fragColor;", "in vec3 v_EntityNormal;\nlayout(location = 0) out vec4 fragColor;\nlayout(location = 1) out vec4 fragNormal;\nlayout(location = 2) out vec4 fragLight;\nvec4 tempColor;");
                    int lastBrace = ((String)(patched = ((String)patched).replace("fragColor = ", "tempColor = "))).lastIndexOf(125);
                    if (lastBrace != -1) {
                        patched = ((String)patched).substring(0, lastBrace) + "    if (tempColor.a < 0.1) discard;\n    fragColor = tempColor;\n    fragNormal = vec4(normalize(v_EntityNormal), 0.0);\n    fragLight = vec4(0.0);\n}\n" + ((String)patched).substring(lastBrace + 1);
                    }
                }
                return (String) patched;
            }
        }
        return source;
    }

    private static String prepareShaderSource(String source, ShaderDefines defines) {
        String stripped = BLOCK_COMMENTS.matcher(source).replaceAll("");
        stripped = LINE_COMMENTS.matcher(stripped).replaceAll("").stripLeading();
        return GlslPreprocessor.injectDefines((String)stripped, (ShaderDefines)defines);
    }

    MemorySegment getOrCompileFunction(String msl, String entryPoint) {
        return this.functionCache.computeIfAbsent(new MslFunctionKey(msl, entryPoint), key -> this.metalDevice.newFunction(key.msl(), key.entryPoint()));
    }

    private DeviceInfo buildDeviceInfo(String deviceName) {
        DeviceType type = DeviceType.INTEGRATED;
        Set extensions = Set.of();
        String osVersion = System.getProperty("os.version", "").trim();
        String driverDescription = "macOS " + osVersion;
        long maxMemoryAllocationSize = Math.min(this.metalDevice.maxBufferLength(), this.metalDevice.recommendedMaxWorkingSetSize());
        return new DeviceInfo(deviceName, "Apple", driverDescription, true, "Metal", 1.0f, new DeviceLimits(16, 256, 16384, maxMemoryAllocationSize, 0, 1), new DeviceFeatures(false, false, true, true, true, false, true), extensions, new HintsAndWorkarounds(false, false), type);
    }

    private @Nullable String resolveDebugLabel(@Nullable Supplier<String> label) {
        return this.useLabels() && label != null ? label.get() : null;
    }

    private record ShaderCompilationKey(Identifier id, ShaderType type, ShaderDefines defines) {
    }

    private record MslFunctionKey(String msl, String entryPoint) {
    }
}
