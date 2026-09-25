package com.prisma.mtl;

import com.prisma.objc.Msg;
import com.prisma.objc.ObjC;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.system.MemoryStack;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

@Environment(EnvType.CLIENT)
public final class MTLComputeCommandEncoder extends MTLCommandEncoder {
    private static final Msg SET_COMPUTE_PIPELINE_STATE = Msg.ofVoid("setComputePipelineState:", ADDRESS);
    private static final Msg SET_TEXTURE = Msg.ofVoid("setTexture:atIndex:", ADDRESS, JAVA_LONG);
    private static final Msg SET_BUFFER = Msg.ofVoid("setBuffer:offset:atIndex:", ADDRESS, JAVA_LONG, JAVA_LONG);
    private static final Msg SET_SAMPLER_STATE = Msg.ofVoid("setSamplerState:atIndex:", ADDRESS, JAVA_LONG);
    private static final Msg SET_BYTES = Msg.ofVoid("setBytes:length:atIndex:", ADDRESS, JAVA_LONG, JAVA_LONG);
    private static final Msg DISPATCH_THREADGROUPS = Msg.ofVoid("dispatchThreadgroups:threadsPerThreadgroup:", ADDRESS, ADDRESS);
    private static final Msg UPDATE_FENCE = Msg.ofVoid("updateFence:", ADDRESS);
    private static final Msg WAIT_FOR_FENCE = Msg.ofVoid("waitForFence:", ADDRESS);

    MTLComputeCommandEncoder(final MemorySegment handle) {
        super(handle);
    }

    public void setComputePipelineState(final MemorySegment pipeline) {
        SET_COMPUTE_PIPELINE_STATE.send(handle(), ObjC.orNil(pipeline));
    }

    public void setTexture(final MemorySegment texture, final long index) {
        SET_TEXTURE.send(handle(), ObjC.orNil(texture), index);
    }

    public void setBuffer(final MTLBuffer buffer, final long offset, final long index) {
        SET_BUFFER.send(handle(), seg(buffer), offset, index);
    }

    public void setBuffer(final MemorySegment buffer, final long offset, final long index) {
        SET_BUFFER.send(handle(), ObjC.orNil(buffer), offset, index);
    }

    public void setBytes(final MemorySegment bytes, final long length, final long index) {
        SET_BYTES.send(handle(), bytes, length, index);
    }

    public void setSamplerState(final MemorySegment sampler, final long index) {
        SET_SAMPLER_STATE.send(handle(), ObjC.orNil(sampler), index);
    }

    public void updateFence(final MTLFence fence) {
        UPDATE_FENCE.send(handle(), fence.handle());
    }

    public void waitForFence(final MTLFence fence) {
        WAIT_FOR_FENCE.send(handle(), fence.handle());
    }

    public void dispatchThreadgroups(
            final long threadgroupsX, final long threadgroupsY, final long threadgroupsZ,
            final long threadsX, final long threadsY, final long threadsZ
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DISPATCH_THREADGROUPS.send(
                    handle(),
                    MTLSize.on(stack, threadgroupsX, threadgroupsY, threadgroupsZ),
                    MTLSize.on(stack, threadsX, threadsY, threadsZ)
            );
        }
    }

    private static MemorySegment seg(final MTLBuffer buffer) {
        return buffer == null ? MemorySegment.NULL : buffer.handle();
    }
}
