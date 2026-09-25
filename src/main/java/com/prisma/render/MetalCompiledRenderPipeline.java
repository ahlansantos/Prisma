package com.prisma.render;

import com.prisma.Prisma;
import com.prisma.mtl.*;

import com.prisma.objc.ObjC;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
final class MetalCompiledRenderPipeline implements BackendRenderPipeline {
    enum ResourceKind {
        UNIFORM_BUFFER,
        SAMPLED_IMAGE,
        TEXEL_BUFFER
    }

    static final int STAGE_VERTEX = 1;
    static final int STAGE_FRAGMENT = 2;
    static final int STAGE_ALL = STAGE_VERTEX | STAGE_FRAGMENT;

    record ResourceBinding(ResourceKind kind, String name, int bindingIndex, int stageMask,
                           @Nullable GpuFormat texelBufferFormat) {
    }

    private final List<ResourceBinding> resources;
    private final Map<String, ResourceBinding> resourcesByName;
    private final long allResourceMask;
    private final int firstAvailableVertexBufferSlot;
    private final int pushConstantIndex;
    private final int pushConstantStageMask;
    private final MTLCullMode cullMode;
    private final MTLTriangleFillMode fillMode;
    private final float depthBiasScaleFactor;
    private final float depthBiasConstant;
    private final MTLPrimitiveType topology;
    private final int vertexBufferCount;

    private final MemorySegment depthStencilState;
    private final MemorySegment withDepthPipeline;
    private final MemorySegment withoutDepthPipeline;
    private boolean closed;

    MetalCompiledRenderPipeline(
            final MetalDevice device,
            final BackendRenderPipeline.CreateInfo info,
            final String vertexMsl,
            final String fragmentMsl,
            final String vertexEntryPoint,
            final String fragmentEntryPoint,
            final List<ResourceBinding> resources,
            final int pushConstantIndex,
            final int pushConstantStageMask
    ) {
        this.resources = resources;
        this.resourcesByName = resources.stream().collect(Collectors.toUnmodifiableMap(ResourceBinding::name, binding -> binding));
        this.pushConstantIndex = pushConstantIndex;
        this.pushConstantStageMask = pushConstantStageMask;

        int maxBindingIndex = -1;
        long resourceMask = 0L;
        for (ResourceBinding binding : resources) {
            maxBindingIndex = Math.max(maxBindingIndex, binding.bindingIndex());
            resourceMask |= 1L << binding.bindingIndex();
        }
        if (maxBindingIndex >= Long.SIZE) {
            throw new IllegalStateException("Pipeline " + info.name() + " has binding index " + maxBindingIndex + ", limit is " + (Long.SIZE - 1));
        }
        this.allResourceMask = resourceMask;

        this.firstAvailableVertexBufferSlot = firstAvailableVertexBufferSlot(resources);
        this.cullMode = info.cull() ? MTLCullMode.Back : MTLCullMode.None;
        this.fillMode = info.polygonMode() == PolygonMode.WIREFRAME ? MTLTriangleFillMode.Lines : MTLTriangleFillMode.Fill;
        this.topology = MTLPrimitiveType.from(info.primitiveTopology());
        this.vertexBufferCount = info.vertexBuffers().size();

        MTLCompareFunction depthCompareOp;
        int depthWrite;
        DepthStencilState depthStencilState = info.depthStencilState();
        if (depthStencilState == null) {
            depthCompareOp = MTLCompareFunction.Always;
            depthWrite = 0;
            this.depthBiasScaleFactor = 0.0f;
            this.depthBiasConstant = 0.0f;
        } else {
            depthCompareOp = MTLCompareFunction.from(depthStencilState.depthTest());
            depthWrite = depthStencilState.writeDepth() ? 1 : 0;
            this.depthBiasScaleFactor = depthStencilState.depthBiasScaleFactor();
            this.depthBiasConstant = depthStencilState.depthBiasConstant();
        }

        this.depthStencilState = device.depthStencilState(depthCompareOp, depthWrite != 0);

        List<ColorTargetState> colorTargetStates = info.colorTargetStates();
        ColorTargetState colorTarget = colorTargetStates.isEmpty() ? null : colorTargetStates.getFirst();
        MTLPixelFormat colorFormat = colorTarget != null ? MTLPixelFormat.from(colorTarget.format()) : MTLPixelFormat.RGBA8Unorm;

        MemorySegment vertexFunction = device.getOrCompileFunction(vertexMsl, vertexEntryPoint);
        MemorySegment fragmentFunction = device.getOrCompileFunction(fragmentMsl, fragmentEntryPoint);

        try (MTLVertexDescriptor vertexDescriptor = buildVertexDescriptor(info, this.firstAvailableVertexBufferSlot)) {
            this.withDepthPipeline = createPipeline(device, colorTarget, vertexFunction, fragmentFunction, vertexDescriptor, colorFormat, MTLPixelFormat.Depth32Float);
            this.withoutDepthPipeline = createPipeline(device, colorTarget, vertexFunction, fragmentFunction, vertexDescriptor, colorFormat, MTLPixelFormat.Depth32Float);
        }
    }

    private static MemorySegment createPipeline(
            final MetalDevice device,
            @Nullable final ColorTargetState colorTarget,
            final MemorySegment vertexFunction,
            final MemorySegment fragmentFunction,
            final MTLVertexDescriptor vertexDescriptor,
            final MTLPixelFormat colorFormat,
            final MTLPixelFormat depthFormat
    ) {
        if (ObjC.isNil(vertexFunction) || ObjC.isNil(fragmentFunction)) {
            return MemorySegment.NULL;
        }

        Optional<BlendFunction> blendFunction = colorTarget == null ? Optional.empty() : colorTarget.blendFunction();
        long writeMask = colorTarget == null ? MTLColorWriteMask.All.value : MTLColorWriteMask.from(colorTarget.writeMask());

        try (MTLRenderPipelineDescriptor pipelineDesc = new MTLRenderPipelineDescriptor()) {
            pipelineDesc.setCompiledFunctions(vertexFunction, fragmentFunction);
            pipelineDesc.setVertexDescriptor(vertexDescriptor);
            pipelineDesc.setColorAttachmentFormat(0, colorFormat);
            if (depthFormat != MTLPixelFormat.Invalid) {
                pipelineDesc.setColorAttachmentFormat(1, MTLPixelFormat.RGBA16Float);
                pipelineDesc.disableBlending(1, MTLColorWriteMask.All.value);
                pipelineDesc.setColorAttachmentFormat(2, MTLPixelFormat.RGBA32Float);
                pipelineDesc.disableBlending(2, MTLColorWriteMask.All.value);
            }
            pipelineDesc.setDepthStencilFormats(depthFormat, MTLPixelFormat.Invalid);

            if (blendFunction.isPresent()) {
                var function = blendFunction.get();
                pipelineDesc.setBlendState(
                        0,
                        MTLBlendFactor.from(function.color().sourceFactor()),
                        MTLBlendFactor.from(function.color().destFactor()),
                        MTLBlendOperation.from(function.color().op()),
                        MTLBlendFactor.from(function.alpha().sourceFactor()),
                        MTLBlendFactor.from(function.alpha().destFactor()),
                        MTLBlendOperation.from(function.alpha().op()),
                        writeMask
                );
            } else {
                pipelineDesc.disableBlending(0, writeMask);
            }

            MemorySegment pipeline = device.metalDevice().newRenderPipelineState(pipelineDesc);
            if (ObjC.isNil(pipeline)) {
                Prisma.LOGGER.error("[prisma] Pipeline {} failed to build with depth format {}", colorTarget, depthFormat);
            }
            return pipeline;
        }
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    List<ResourceBinding> resources() {
        return this.resources;
    }

    long allResourceMask() {
        return this.allResourceMask;
    }

    @Nullable
    ResourceBinding resource(final String name) {
        return this.resourcesByName.get(name);
    }

    int firstAvailableVertexBufferSlot() {
        return this.firstAvailableVertexBufferSlot;
    }

    int pushConstantIndex() {
        return this.pushConstantIndex;
    }

    int pushConstantStageMask() {
        return this.pushConstantStageMask;
    }

    float depthBiasScaleFactor() {
        return this.depthBiasScaleFactor;
    }

    float depthBiasConstant() {
        return this.depthBiasConstant;
    }

    MemorySegment getDepthStencilState() {
        return this.depthStencilState;
    }

    MemorySegment getNativePipeline(final boolean useDepth) {
        return useDepth ? this.withDepthPipeline : this.withoutDepthPipeline;
    }

    MTLCullMode cullMode() {
        return this.cullMode;
    }

    MTLTriangleFillMode fillMode() {
        return this.fillMode;
    }

    MTLPrimitiveType topology() {
        return this.topology;
    }

    int vertexBufferCount() {
        return this.vertexBufferCount;
    }

    private static MTLVertexDescriptor buildVertexDescriptor(
            final BackendRenderPipeline.CreateInfo info,
            final int firstMetalVertexBufferSlot
    ) {
        MTLVertexDescriptor vertexDesc = new MTLVertexDescriptor();

        for (BackendRenderPipeline.CreateInfo.AttribBinding binding : info.attribBindings()) {
            MTLVertexFormat format = MTLVertexFormat.from(binding.format());
            if (format == MTLVertexFormat.Invalid) {
                throw new IllegalStateException("Unsupported vertex attribute format: " + binding.format());
            }
            int metalSlot = firstMetalVertexBufferSlot + binding.bufferSlot();
            vertexDesc.setAttribute(binding.location(), format.value, binding.offset(), metalSlot);
        }

        for (BackendRenderPipeline.CreateInfo.VertexBuffer vertexBuffer : info.vertexBuffers()) {
            int metalSlot = firstMetalVertexBufferSlot + vertexBuffer.bufferSlot();
            int stepRate = vertexBuffer.stepRate();
            MTLVertexStepFunction stepFunction = stepRate > 0 ? MTLVertexStepFunction.PerInstance : MTLVertexStepFunction.PerVertex;
            vertexDesc.setLayout(metalSlot, vertexBuffer.stride(), stepFunction, stepRate > 0 ? stepRate : 1);
        }

        return vertexDesc;
    }

    private static int firstAvailableVertexBufferSlot(final List<ResourceBinding> resources) {
        int maxVertexBufferBinding = -1;
        for (ResourceBinding resource : resources) {
            if (resource.kind() == ResourceKind.UNIFORM_BUFFER && (resource.stageMask() & STAGE_VERTEX) != 0) {
                maxVertexBufferBinding = Math.max(maxVertexBufferBinding, resource.bindingIndex());
            }
        }
        return maxVertexBufferBinding + 1;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            if (!ObjC.isNil(this.withDepthPipeline)) {
                ObjC.release(this.withDepthPipeline);
            }
            if (!ObjC.isNil(this.withoutDepthPipeline)) {
                ObjC.release(this.withoutDepthPipeline);
            }
        }
    }
}
