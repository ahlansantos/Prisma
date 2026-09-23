package com.prisma.mtl;

import com.prisma.objc.AutoreleasePool;
import com.prisma.objc.Msg;
import com.prisma.objc.ObjC;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.ADDRESS;

@Environment(EnvType.CLIENT)
public final class MTLCommandQueue {
    private static final Msg COMMAND_BUFFER = Msg.of("commandBuffer", ADDRESS);
    private static final Msg SET_LABEL = Msg.ofVoid("setLabel:", ADDRESS);

    private static volatile boolean debugLabelsEnabled;

    private MemorySegment handle;

    MTLCommandQueue(final MemorySegment handle) {
        this.handle = handle;
    }

    public MemorySegment handle() {
        if (ObjC.isNil(handle)) {
            throw new IllegalStateException("MTLCommandQueue is closed");
        }
        return handle;
    }

    public static void setDebugLabelsEnabled(final boolean enabled) {
        debugLabelsEnabled = enabled;
    }

    public MTLCommandBuffer makeCommandBuffer(@Nullable final String label) {
        try (AutoreleasePool _ = AutoreleasePool.push()) {
            MemorySegment commandBuffer = COMMAND_BUFFER.sendPtr(handle);
            if (ObjC.isNil(commandBuffer)) {
                throw new IllegalStateException("Failed to create MTLCommandBuffer");
            }
            if (debugLabelsEnabled && label != null && !label.isEmpty()) {
                MemorySegment nsLabel = ObjC.nsString(label);
                SET_LABEL.send(commandBuffer, nsLabel);
                ObjC.release(nsLabel);
            }
            return new MTLCommandBuffer(ObjC.retain(commandBuffer));
        }
    }

    public void close() {
        if (ObjC.isNil(handle)) {
            return;
        }
        ObjC.release(handle);
        handle = MemorySegment.NULL;
    }
}
