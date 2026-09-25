package com.prisma.render;

import com.prisma.mtl.*;
import com.prisma.objc.ObjC;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import com.mojang.renderpearl.util.TextureViewAndSampler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import org.joml.Vector4fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDrawIndexedIndirectCommand;
import org.lwjgl.vulkan.VkDrawIndirectCommand;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
final class MetalRenderPass implements RenderPassBackend {
    static final boolean VALIDATION = SharedConstants.IS_RUNNING_IN_IDE;
    static final int MAX_VERTEX_BUFFERS = RenderPass.MAX_VERTEX_BUFFERS;
    private final MetalDevice device;
    private final MetalCommandEncoder commandEncoder;
    @Nullable
    private final String label;
    private final GpuTextureView colorTexture;
    @Nullable
    private final GpuTextureView depthTexture;
    private final RenderPass.RenderArea renderArea;
    @Nullable
    private Vector4fc clearColor;
    @Nullable
    private Double clearDepth;
    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private final GpuBufferSlice[] vertexBuffers = new GpuBufferSlice[MAX_VERTEX_BUFFERS];
    private final HashMap<Integer, GpuBufferSlice> uniforms = new HashMap<>();
    private final HashMap<Integer, TextureViewAndSampler> samplers = new HashMap<>();
    private long dirtyDescriptorMask;
    @Nullable
    private MetalCompiledRenderPipeline compiledPipeline;
    @Nullable
    private GpuBuffer indexBuffer;
    private MTLIndexType indexType = MTLIndexType.UInt16;
    private int pushedDebugGroups = 0;
    private boolean scissorDirty = true;
    private boolean vertexBuffersDirty = true;
    private boolean pipelineDirty = true;
    @Nullable
    private ByteBuffer pushConstantData;

    MetalRenderPass(
            final MetalDevice device,
            final MetalCommandEncoder encoder,
            final Supplier<String> label,
            final GpuTextureView colorTexture,
            @Nullable final GpuTextureView depthTexture,
            final RenderPass.RenderArea renderArea,
            @Nullable final Vector4fc clearColor,
            @Nullable final Double clearDepth
    ) {
        this.device = device;
        this.commandEncoder = encoder;
        this.label = device.useLabels() ? label.get() : null;
        this.colorTexture = colorTexture;
        this.depthTexture = depthTexture;
        this.renderArea = renderArea;
        this.clearColor = clearColor;
        this.clearDepth = clearDepth;
    }

    @Override
    public void pushDebugGroup(final @NonNull Supplier<String> label) {
        pushedDebugGroups++;
        if (device.useLabels()) {
            commandEncoder.commandBuffer().pushDebugGroup(label.get());
        }
    }

    @Override
    public void popDebugGroup() {
        if (pushedDebugGroups == 0) {
            throw new IllegalStateException("Can't pop more debug groups than was pushed!");
        }
        pushedDebugGroups--;
        if (device.useLabels()) {
            commandEncoder.commandBuffer().popDebugGroup();
        }
    }

    @Override
    public void setPipeline(final @NonNull BackendRenderPipeline pipeline) {
        if (!(pipeline instanceof MetalCompiledRenderPipeline metalPipeline)) {
            throw new IllegalArgumentException("Metal render pass requires a Metal pipeline, got " + pipeline);
        }
        if (this.compiledPipeline != metalPipeline) {
            this.compiledPipeline = metalPipeline;
            vertexBuffersDirty = true;
            pipelineDirty = true;
        }
    }

    @Override
    public void setUniform(final int index, @Nullable final Object value) {
        if (value == null) {
            this.uniforms.remove(index);
            this.samplers.remove(index);
            markDescriptorDirty(index);
            return;
        }

        if (value instanceof GpuBufferSlice slice) {
            this.uniforms.put(index, slice);
            markDescriptorDirty(index);
            return;
        }

        if (value instanceof GpuBuffer buffer) {
            this.uniforms.put(index, buffer.slice());
            markDescriptorDirty(index);
            return;
        }

        if (value instanceof TextureViewAndSampler textureBinding) {
            this.samplers.put(index, textureBinding);
            commandEncoder.flushPendingClear((MetalGpuTexture) textureBinding.view().texture());
            markDescriptorDirty(index);
            return;
        }

        throw new IllegalArgumentException("Unsupported uniform value at index " + index + ": " + value);
    }

    @Override
    public void pushConstants(final ByteBuffer value) {
        if (this.pushConstantData != null) {
            MemoryUtil.memFree(this.pushConstantData);
        }
        ByteBuffer copy = MemoryUtil.memAlloc(value.remaining());
        copy.put(value.duplicate());
        copy.flip();
        this.pushConstantData = copy;

        MetalCompiledRenderPipeline pipeline = this.compiledPipeline;
        if (pipeline != null && pipeline.pushConstantIndex() >= 0) {
            markDescriptorDirty(pipeline.pushConstantIndex());
        }
    }

    @Override
    public void enableScissor(final int x, final int y, final int width, final int height) {
        if (scissorEnabled && scissorX == x && scissorY == y && scissorW == width && scissorH == height) {
            return;
        }
        scissorEnabled = true;
        scissorX = x;
        scissorY = y;
        scissorW = width;
        scissorH = height;
        scissorDirty = true;
    }

    @Override
    public void disableScissor() {
        if (!scissorEnabled) {
            return;
        }
        scissorEnabled = false;
        scissorDirty = true;
    }

    @Override
    public void setVertexBuffer(final int slot, @Nullable final GpuBufferSlice vertexBuffer) {
        if (slot < 0 || slot >= MAX_VERTEX_BUFFERS) {
            throw new IllegalArgumentException("Unsupported Metal vertex buffer slot: " + slot);
        }

        if (!sameSlice(vertexBuffers[slot], vertexBuffer)) {
            vertexBuffers[slot] = vertexBuffer;
            vertexBuffersDirty = true;
        }
    }

    @Override
    public void setIndexBuffer(@Nullable final GpuBuffer indexBuffer, final @NonNull IndexType indexType) {
        setIndexBuffer(indexBuffer, MTLIndexType.from(indexType));
    }

    private void setIndexBuffer(@Nullable final GpuBuffer indexBuffer, final MTLIndexType indexType) {
        if (this.indexBuffer != indexBuffer || this.indexType != indexType) {
            this.indexBuffer = indexBuffer;
            this.indexType = indexType;
        }
    }

    @Override
    public void drawIndexed(final int indexCount, final int instanceCount, final int firstIndex, final int vertexOffset, final int firstInstance) {
        MetalGpuBuffer nativeIndexBuffer = (MetalGpuBuffer) indexBuffer;
        MTLRenderCommandEncoder enc = renderEncoder();

        bindDrawState(enc);
        drawIndexedNative(enc, nativeIndexBuffer, firstIndex, indexCount, vertexOffset, instanceCount, indexType, firstInstance);
    }

    @Override
    public void multiDrawIndexed(@NonNull IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
        MetalGpuBuffer nativeIndexBuffer = (MetalGpuBuffer) indexBuffer;
        MTLRenderCommandEncoder enc = renderEncoder();
        bindDrawState(enc);

        for (int i = 0; i < drawCount; i++) {
            int firstIndex = drawParameters.get(i * 3);
            int indexCount = drawParameters.get(i * 3 + 1);
            int baseVertex = drawParameters.get(i * 3 + 2);
            if (indexCount > 0) {
                drawIndexedNative(enc, nativeIndexBuffer, firstIndex, indexCount, baseVertex, instanceCount, indexType, firstInstance);
            }
        }
    }

    @Override
    public void multiDrawIndexed(@NonNull PointerBuffer firstIndexOffsets, @NonNull IntBuffer indexCounts, @NonNull IntBuffer vertexOffsets, int drawCount) {
        MTLPrimitiveType primitiveType = primitiveTopology();
        if (primitiveType == MTLPrimitiveType.TriangleFan) {
            throw new UnsupportedOperationException("Metal backend does not support triangle fan multiDrawIndexed");
        }

        MetalGpuBuffer nativeIndexBuffer = (MetalGpuBuffer) indexBuffer;
        MTLRenderCommandEncoder enc = renderEncoder();
        bindDrawState(enc);

        MTLBuffer indexBufferHandle = nativeIndexBuffer.metalBuffer();
        MemorySegment offsets = MemorySegment.ofAddress(MemoryUtil.memAddress(firstIndexOffsets)).reinterpret(drawCount * 8L);
        MemorySegment counts = MemorySegment.ofAddress(MemoryUtil.memAddress(indexCounts)).reinterpret(drawCount * 4L);
        MemorySegment vertices = MemorySegment.ofAddress(MemoryUtil.memAddress(vertexOffsets)).reinterpret(drawCount * 4L);
        for (int i = 0; i < drawCount; i++) {
            int indexCount = counts.get(ValueLayout.JAVA_INT, i * 4L);
            if (indexCount <= 0) {
                continue;
            }
            long firstIndexOffset = offsets.get(ValueLayout.JAVA_LONG, i * 8L);
            int baseVertex = vertices.get(ValueLayout.JAVA_INT, i * 4L);
            enc.drawIndexedPrimitives(primitiveType, indexCount, indexType, indexBufferHandle, firstIndexOffset, 1, baseVertex, 0);
        }
    }

    @Override
    public void drawIndexedIndirect(final @NonNull GpuBufferSlice commands, final int drawCount) {
        MTLPrimitiveType primitiveType = primitiveTopology();
        if (primitiveType == MTLPrimitiveType.TriangleFan) {
            throw new UnsupportedOperationException("Metal backend does not support triangle fan indirect draws");
        }

        MetalGpuBuffer nativeIndexBuffer = (MetalGpuBuffer) indexBuffer;
        MTLRenderCommandEncoder enc = renderEncoder();
        bindDrawState(enc);

        MTLBuffer indexBufferHandle = nativeIndexBuffer.metalBuffer();
        MTLBuffer indirectBuffer = ((MetalGpuBuffer) commands.buffer()).metalBuffer();
        long indirectOffset = commands.offset();
        for (int i = 0; i < drawCount; i++) {
            enc.drawIndexedPrimitivesIndirect(primitiveType, indexType, indexBufferHandle, indirectBuffer, indirectOffset);
            indirectOffset += VkDrawIndexedIndirectCommand.SIZEOF;
        }
    }

    @Override
    public void draw(final int vertexCount, final int instanceCount, final int firstVertex, final int firstInstance) {
        MTLPrimitiveType primitiveType = primitiveTopology();
        MTLRenderCommandEncoder enc = renderEncoder();

        bindDrawState(enc);

        if (primitiveType == MTLPrimitiveType.TriangleFan) {
            drawTriangleFan(enc, firstVertex, vertexCount, instanceCount, firstInstance);
        } else {
            enc.drawPrimitives(primitiveType, firstVertex, vertexCount, instanceCount, firstInstance);
        }
    }

    @Override
    public void multiDraw(@NonNull IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void multiDraw(@NonNull IntBuffer firstVertices, @NonNull IntBuffer vertexCounts, int drawCount) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void drawIndirect(final @NonNull GpuBufferSlice commands, final int drawCount) {
        MTLPrimitiveType primitiveType = primitiveTopology();
        if (primitiveType == MTLPrimitiveType.TriangleFan) {
            throw new UnsupportedOperationException("Metal backend does not support triangle fan indirect draws");
        }

        MTLRenderCommandEncoder enc = renderEncoder();
        bindDrawState(enc);

        MTLBuffer indirectBuffer = ((MetalGpuBuffer) commands.buffer()).metalBuffer();
        long indirectOffset = commands.offset();
        for (int i = 0; i < drawCount; i++) {
            enc.drawPrimitivesIndirect(primitiveType, indirectBuffer, indirectOffset);
            indirectOffset += VkDrawIndirectCommand.SIZEOF;
        }
    }

    @Override
    public void writeTimestamp(final @NonNull GpuQueryPool pool, final int index) {
        if (pool instanceof MetalGpuQueryPool metalPool && index >= 0 && index < pool.size()) {
            metalPool.setValue(index, device.getTimestampNow());
        }
    }

    MTLPixelFormat colorAttachmentFormat() {
        return ((MetalGpuTexture) colorTexture.texture()).mtlPixelFormat();
    }

    MTLPixelFormat depthAttachmentFormat() {
        if (depthTexture == null) {
            return MTLPixelFormat.Invalid;
        }
        return ((MetalGpuTexture) depthTexture.texture()).mtlPixelFormat();
    }

    MTLPixelFormat stencilAttachmentFormat() {
        if (depthTexture == null) {
            return MTLPixelFormat.Invalid;
        }
        return ((MetalGpuTexture) depthTexture.texture()).mtlStencilPixelFormat();
    }

    void materializePendingClear() {
        if (clearColor != null || clearDepth != null) {
            renderEncoder();
        }
    }

    private MTLRenderCommandEncoder renderEncoder() {
        MetalGpuTextureView colorTextureView = (MetalGpuTextureView) colorTexture;
        MetalGpuTextureView depthTextureView = depthTexture == null ? null : (MetalGpuTextureView) depthTexture;
        MTLRenderCommandEncoder encoder = commandEncoder.renderCommandEncoder(
                colorTextureView,
                depthTextureView,
                colorTexture.getWidth(0),
                colorTexture.getHeight(0),
                clearColor,
                clearDepth
        );
        clearColor = null;
        clearDepth = null;
        return encoder;
    }

    void invalidateEncoderState() {
        pipelineDirty = true;
        scissorDirty = true;
        vertexBuffersDirty = true;
    }

    GpuBufferSlice.MappedView allocateTransient(final long size, final long alignment, @GpuBuffer.Usage final int usage) {
        return commandEncoder.transientMemory().allocateGpuMapped(size, alignment, usage);
    }

    private void pushVertexBuffers(final MTLRenderCommandEncoder enc) {
        int firstSlot = compiledPipeline.firstAvailableVertexBufferSlot();
        int count = compiledPipeline.vertexBufferCount();
        for (int slot = 0; slot < count; slot++) {
            GpuBufferSlice vertexBuffer = vertexBuffers[slot];
            if (vertexBuffer == null) {
                continue;
            }
            if (VALIDATION && vertexBuffer.buffer().isClosed()) {
                throw new IllegalStateException("Vertex buffer at slot " + slot + " has been closed");
            }

            MetalGpuBuffer nativeVertexBuffer = (MetalGpuBuffer) vertexBuffer.buffer();
            int metalSlot = firstSlot + slot;
            enc.setVertexBuffer(nativeVertexBuffer.metalBuffer(), vertexBuffer.offset(), metalSlot);
        }
    }

    private void drawTriangleFan(MTLRenderCommandEncoder encoder, final int firstVertex, final int vertexCount, final int instanceCount, final int baseInstance) {
        int triangleCount = vertexCount - 2;
        int indexCount = triangleCount * 3;
        MTLIndexType fanIndexType = vertexCount - 1 <= 0xFFFF ? MTLIndexType.UInt16 : MTLIndexType.UInt32;

        try (GpuBufferSlice.MappedView mapped = commandEncoder.transientMemory().allocateGpuMapped((long) indexCount * fanIndexType.bytes, fanIndexType.bytes, GpuBuffer.USAGE_INDEX)) {
            if (fanIndexType == MTLIndexType.UInt16) {
                ShortBuffer indices = mapped.data().asShortBuffer();
                for (int i = 0; i < triangleCount; i++) {
                    indices.put((short) 0);
                    indices.put((short) (i + 1));
                    indices.put((short) (i + 2));
                }
            } else {
                IntBuffer indices = mapped.data().asIntBuffer();
                for (int i = 0; i < triangleCount; i++) {
                    indices.put(0);
                    indices.put(i + 1);
                    indices.put(i + 2);
                }
            }
            GpuBufferSlice slice = mapped.slice();
            encoder.drawIndexedPrimitives(MTLPrimitiveType.Triangle, indexCount, fanIndexType, ((MetalGpuBuffer) slice.buffer()).metalBuffer(), slice.offset(), instanceCount, firstVertex, baseInstance);
        }
    }

    private void drawIndexedNative(
            final MTLRenderCommandEncoder enc,
            final MetalGpuBuffer nativeIndexBuffer,
            final int firstIndex,
            final int indexCount,
            final int baseVertex,
            final int instanceCount,
            final MTLIndexType indexType,
            final int baseInstance
    ) {
        MTLPrimitiveType primitiveType = primitiveTopology();

        long indexOffsetBytes = (long) firstIndex * indexType.bytes;
        if (primitiveType == MTLPrimitiveType.TriangleFan) {
            if (indexCount < 3) {
                return;
            }
            int generatedIndexCount = (indexCount - 2) * 3;
            try (GpuBufferSlice.MappedView mapped = commandEncoder.transientMemory().allocateGpuMapped((long) generatedIndexCount * Integer.BYTES, Integer.BYTES, GpuBuffer.USAGE_INDEX)) {
                expandTriangleFan(mapped.data().asIntBuffer(), nativeIndexBuffer.metalBuffer(), indexOffsetBytes, indexCount, indexType);
                GpuBufferSlice slice = mapped.slice();
                enc.drawIndexedPrimitives(MTLPrimitiveType.Triangle, generatedIndexCount, MTLIndexType.UInt32, ((MetalGpuBuffer) slice.buffer()).metalBuffer(), slice.offset(), instanceCount, baseVertex, baseInstance);
            }
        } else {
            enc.drawIndexedPrimitives(primitiveType, indexCount, indexType, nativeIndexBuffer.metalBuffer(), indexOffsetBytes, instanceCount, baseVertex, baseInstance);
        }
    }

    private static void expandTriangleFan(final IntBuffer out, final MTLBuffer indexBuffer, final long indexOffsetBytes, final int indexCount, final MTLIndexType indexType) {
        MemorySegment indices = indexBuffer.contents()
                .reinterpret(indexOffsetBytes + (long) indexCount * indexType.bytes)
                .asSlice(indexOffsetBytes);
        int center = readIndex(indices, 0, indexType);
        for (int i = 1; i < indexCount - 1; i++) {
            out.put(center).put(readIndex(indices, i, indexType)).put(readIndex(indices, i + 1, indexType));
        }
    }

    private static void bindBuffer(final MTLRenderCommandEncoder enc, final MTLBuffer buffer, final long offset, final long index, final int stageMask) {
        if ((stageMask & MetalCompiledRenderPipeline.STAGE_VERTEX) != 0) {
            enc.setVertexBuffer(buffer, offset, index);
        }
        if ((stageMask & MetalCompiledRenderPipeline.STAGE_FRAGMENT) != 0) {
            enc.setFragmentBuffer(buffer, offset, index);
        }
    }

    private static void bindTexture(final MTLRenderCommandEncoder enc, final MemorySegment texture, final long index, final int stageMask) {
        if ((stageMask & MetalCompiledRenderPipeline.STAGE_VERTEX) != 0) {
            enc.setVertexTexture(texture, index);
        }
        if ((stageMask & MetalCompiledRenderPipeline.STAGE_FRAGMENT) != 0) {
            enc.setFragmentTexture(texture, index);
        }
    }

    private static void bindTextureAndSampler(final MTLRenderCommandEncoder enc, final MemorySegment texture, final MemorySegment sampler, final long index, final int stageMask) {
        if ((stageMask & MetalCompiledRenderPipeline.STAGE_VERTEX) != 0) {
            enc.setVertexTexture(texture, index);
            enc.setVertexSamplerState(sampler, index);
        }
        if ((stageMask & MetalCompiledRenderPipeline.STAGE_FRAGMENT) != 0) {
            enc.setFragmentTexture(texture, index);
            enc.setFragmentSamplerState(sampler, index);
        }
    }

    private static void bindBytes(final MTLRenderCommandEncoder enc, final MemorySegment bytes, final long length, final long index, final int stageMask) {
        if ((stageMask & MetalCompiledRenderPipeline.STAGE_VERTEX) != 0) {
            enc.setVertexBytes(bytes, length, index);
        }
        if ((stageMask & MetalCompiledRenderPipeline.STAGE_FRAGMENT) != 0) {
            enc.setFragmentBytes(bytes, length, index);
        }
    }

    private static int readIndex(final MemorySegment indices, final int index, final MTLIndexType indexType) {
        if (indexType == MTLIndexType.UInt16) {
            return Short.toUnsignedInt(indices.get(ValueLayout.JAVA_SHORT_UNALIGNED, index * 2L));
        }
        return indices.get(ValueLayout.JAVA_INT_UNALIGNED, index * 4L);
    }

    private void bindDrawState(final MTLRenderCommandEncoder enc) {
        if (compiledPipeline == null) {
            throw new IllegalStateException("Pipeline is missing");
        }

        if (pipelineDirty) {
            boolean useDepth = depthAttachmentFormat().value != MTLPixelFormat.Invalid.value;
            MemorySegment pipelineHandle = compiledPipeline.getNativePipeline(useDepth);
            if (ObjC.isNil(pipelineHandle)) {
                throw new IllegalStateException("Native pipeline is unavailable");
            }
            enc.setRenderPipelineState(pipelineHandle);
            pipelineDirty = false;

            if (useDepth) {
                MemorySegment depthState = compiledPipeline.getDepthStencilState();
                if (ObjC.isNil(depthState)) {
                    throw new IllegalStateException("Native depth state is unavailable");
                }
                enc.setDepthStencilState(depthState);
                enc.setDepthBias(
                        compiledPipeline.depthBiasConstant(),
                        compiledPipeline.depthBiasScaleFactor(),
                        0.0f
                );
            }

            enc.setFrontFacingWinding(MTLWinding.Clockwise);
            enc.setCullMode(compiledPipeline.cullMode());
            enc.setTriangleFillMode(compiledPipeline.fillMode());

            dirtyDescriptorMask |= compiledPipeline.allResourceMask();
        }

        if (scissorDirty) {
            pushEffectiveScissor(enc);
            scissorDirty = false;
        }

        if (vertexBuffersDirty) {
            pushVertexBuffers(enc);
            vertexBuffersDirty = false;
        }

        if (dirtyDescriptorMask != 0) {
            for (MetalCompiledRenderPipeline.ResourceBinding binding : compiledPipeline.resources()) {
                if ((dirtyDescriptorMask & (1L << binding.bindingIndex())) != 0L) {
                    pushDescriptor(enc, binding);
                }
            }
        }

        dirtyDescriptorMask = 0L;
    }

    private MTLPrimitiveType primitiveTopology() {
        if (compiledPipeline == null) {
            throw new IllegalStateException("Pipeline is missing");
        }
        return compiledPipeline.topology();
    }

    private void pushEffectiveScissor(final MTLRenderCommandEncoder enc) {
        int areaLeft = renderArea.x();
        int areaTop = renderArea.y();
        if (!scissorEnabled) {
            if (renderArea.fillsTexture(colorTexture)) {
                enc.setScissorRect(0L, 0L, colorTexture.getWidth(0), colorTexture.getHeight(0));
                return;
            }
            enc.setScissorRect(areaLeft, areaTop, renderArea.width(), renderArea.height());
            return;
        }

        int areaRight = areaLeft + renderArea.width();
        int areaBottom = areaTop + renderArea.height();
        int left = Math.max(areaLeft, scissorX);
        int top = Math.max(areaTop, scissorY);
        int right = Math.min(areaRight, scissorX + scissorW);
        int bottom = Math.min(areaBottom, scissorY + scissorH);
        if (right <= left || bottom <= top) {
            enc.setScissorRect(0, 0, 0, 0);
        } else {
            enc.setScissorRect(left, top, right - left, bottom - top);
        }
    }

    private void markDescriptorDirty(final int index) {
        if (index >= 0 && index < Long.SIZE) {
            dirtyDescriptorMask |= 1L << index;
        }
    }

    private void pushDescriptor(
            final MTLRenderCommandEncoder enc,
            final MetalCompiledRenderPipeline.ResourceBinding binding
    ) {
        if (binding.kind() == MetalCompiledRenderPipeline.ResourceKind.SAMPLED_IMAGE) {
            TextureViewAndSampler textureBinding = samplers.get(binding.bindingIndex());
            if (textureBinding == null) {
                throw new IllegalStateException("Missing sampler " + binding.name());
            }

            MetalGpuTextureView textureView = (MetalGpuTextureView) textureBinding.view();
            MetalGpuSampler sampler = (MetalGpuSampler) textureBinding.sampler();
            bindTextureAndSampler(enc, textureView.nativeHandle(), sampler.nativeHandle(), binding.bindingIndex(), binding.stageMask());
            return;
        }

        if (binding.kind() == MetalCompiledRenderPipeline.ResourceKind.TEXEL_BUFFER) {
            pushTexelBufferDescriptor(enc, binding);
            return;
        }

        if (binding.bindingIndex() == compiledPipeline.pushConstantIndex()) {
            if (this.pushConstantData == null) {
                throw new IllegalStateException("Missing push constants for " + binding.name());
            }
            bindBytes(
                    enc,
                    MemorySegment.ofAddress(MemoryUtil.memAddress(this.pushConstantData)).reinterpret(this.pushConstantData.remaining()),
                    this.pushConstantData.remaining(),
                    binding.bindingIndex(),
                    binding.stageMask()
            );
            return;
        }

        GpuBufferSlice uniformSlice = uniforms.get(binding.bindingIndex());
        if (uniformSlice == null) {
            throw new IllegalStateException("Missing uniform " + binding.name());
        }
        if (VALIDATION && uniformSlice.buffer().isClosed()) {
            throw new IllegalStateException("Uniform " + binding.name() + " buffer has been closed");
        }

        MetalGpuBuffer uniformBuffer = (MetalGpuBuffer) uniformSlice.buffer();
        bindBuffer(enc, uniformBuffer.metalBuffer(), uniformSlice.offset(), binding.bindingIndex(), binding.stageMask());
    }

    private void pushTexelBufferDescriptor(final MTLRenderCommandEncoder enc, final MetalCompiledRenderPipeline.ResourceBinding binding) {
        GpuBufferSlice texelSlice = uniforms.get(binding.bindingIndex());
        if (texelSlice == null) {
            throw new IllegalStateException("Missing texel buffer " + binding.name());
        }
        if (VALIDATION && texelSlice.buffer().isClosed()) {
            throw new IllegalStateException("Texel buffer " + binding.name() + " has been closed");
        }

        GpuFormat texelFormat = binding.texelBufferFormat();
        if (texelFormat == null) {
            throw new IllegalStateException("Texel buffer " + binding.name() + " is missing a format");
        }

        MetalGpuBuffer texelBuffer = (MetalGpuBuffer) texelSlice.buffer();
        long pixelFormat = MTLPixelFormat.from(texelFormat).value;
        int pixelSize = texelFormat.blockSize();
        long texelByteLength = texelSlice.length();
        if (texelByteLength <= 0L || texelByteLength % pixelSize != 0L) {
            throw new IllegalStateException("Texel buffer " + binding.name() + " length " + texelByteLength + " is not a valid " + texelFormat + " range");
        }
        long texelCount = texelByteLength / pixelSize;
        MemorySegment texelTexture = MTLTexture.newBufferTextureView(
                texelBuffer.nativeHandle(),
                pixelFormat,
                texelSlice.offset(),
                texelCount,
                texelByteLength
        );
        if (ObjC.isNil(texelTexture)) {
            throw new IllegalStateException("Failed to create Metal texel buffer texture for " + binding.name());
        }

        bindTexture(enc, texelTexture, binding.bindingIndex(), binding.stageMask());
        commandEncoder.queueForDestroy(() -> ObjC.release(texelTexture));
    }

    void close() {
        if (this.pushConstantData != null) {
            MemoryUtil.memFree(this.pushConstantData);
            this.pushConstantData = null;
        }
    }

    private static boolean sameSlice(@Nullable final GpuBufferSlice left, @Nullable final GpuBufferSlice right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.buffer() == right.buffer()
                && left.offset() == right.offset()
                && left.length() == right.length();
    }
}
