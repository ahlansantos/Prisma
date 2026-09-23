package com.prisma.mtl;

import com.prisma.objc.Msg;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.system.MemoryStack;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

@Environment(EnvType.CLIENT)
public final class MTLBlitCommandEncoder extends MTLCommandEncoder {
    private static final Msg COPY_BUFFER_TO_BUFFER = Msg.ofVoid("copyFromBuffer:sourceOffset:toBuffer:destinationOffset:size:",
            ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_LONG);
    private static final Msg COPY_BUFFER_TO_TEXTURE = Msg.ofVoid("copyFromBuffer:sourceOffset:sourceBytesPerRow:sourceBytesPerImage:sourceSize:toTexture:destinationSlice:destinationLevel:destinationOrigin:",
            ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS);
    private static final Msg COPY_TEXTURE_TO_TEXTURE = Msg.ofVoid("copyFromTexture:sourceSlice:sourceLevel:sourceOrigin:sourceSize:toTexture:destinationSlice:destinationLevel:destinationOrigin:",
            ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS);
    private static final Msg COPY_TEXTURE_TO_BUFFER = Msg.ofVoid("copyFromTexture:sourceSlice:sourceLevel:sourceOrigin:sourceSize:toBuffer:destinationOffset:destinationBytesPerRow:destinationBytesPerImage:",
            ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG);
    private static final Msg UPDATE_FENCE = Msg.ofVoid("updateFence:", ADDRESS);
    private static final Msg WAIT_FOR_FENCE = Msg.ofVoid("waitForFence:", ADDRESS);

    MTLBlitCommandEncoder(final MemorySegment handle) {
        super(handle);
    }

    public void copyFromBufferToBuffer(
            final MTLBuffer sourceBuffer,
            final long sourceOffset,
            final MTLBuffer destinationBuffer,
            final long destinationOffset,
            final long size
    ) {
        COPY_BUFFER_TO_BUFFER.send(handle(), sourceBuffer.handle(), sourceOffset, destinationBuffer.handle(), destinationOffset, size);
    }

    public void copyFromBufferToTexture(
            final MTLBuffer sourceBuffer,
            final long sourceOffset,
            final long sourceBytesPerRow,
            final long sourceBytesPerImage,
            final long width,
            final long height,
            final MemorySegment texture,
            final long destinationSlice,
            final long destinationLevel,
            final long destinationX,
            final long destinationY
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            COPY_BUFFER_TO_TEXTURE.send(
                    handle(),
                    sourceBuffer.handle(),
                    sourceOffset,
                    sourceBytesPerRow,
                    sourceBytesPerImage,
                    MTLSize.on(stack, width, height, 1),
                    texture,
                    destinationSlice,
                    destinationLevel,
                    MTLOrigin.on(stack, destinationX, destinationY, 0)
            );
        }
    }

    public void copyFromTextureToTexture(
            final MemorySegment sourceTexture,
            final long sourceSlice,
            final long sourceLevel,
            final long sourceX,
            final long sourceY,
            final long width,
            final long height,
            final MemorySegment destinationTexture,
            final long destinationSlice,
            final long destinationLevel,
            final long destinationX,
            final long destinationY
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            COPY_TEXTURE_TO_TEXTURE.send(
                    handle(),
                    sourceTexture,
                    sourceSlice,
                    sourceLevel,
                    MTLOrigin.on(stack, sourceX, sourceY, 0),
                    MTLSize.on(stack, width, height, 1),
                    destinationTexture,
                    destinationSlice,
                    destinationLevel,
                    MTLOrigin.on(stack, destinationX, destinationY, 0)
            );
        }
    }

    public void copyFromTextureToBuffer(
            final MemorySegment sourceTexture,
            final long sourceSlice,
            final long sourceLevel,
            final long sourceX,
            final long sourceY,
            final long width,
            final long height,
            final MTLBuffer destinationBuffer,
            final long destinationOffset,
            final long destinationBytesPerRow,
            final long destinationBytesPerImage
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            COPY_TEXTURE_TO_BUFFER.send(
                    handle(),
                    sourceTexture,
                    sourceSlice,
                    sourceLevel,
                    MTLOrigin.on(stack, sourceX, sourceY, 0),
                    MTLSize.on(stack, width, height, 1),
                    destinationBuffer.handle(),
                    destinationOffset,
                    destinationBytesPerRow,
                    destinationBytesPerImage
            );
        }
    }

    public void updateFence(final MTLFence fence) {
        UPDATE_FENCE.send(handle(), fence.handle());
    }

    public void waitForFence(final MTLFence fence) {
        WAIT_FOR_FENCE.send(handle(), fence.handle());
    }
}
