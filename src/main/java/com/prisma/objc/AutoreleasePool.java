package com.prisma.objc;

import java.lang.foreign.MemorySegment;

public final class AutoreleasePool implements AutoCloseable {
    private final MemorySegment handle;
    private boolean closed;

    private AutoreleasePool(MemorySegment handle) {
        this.handle = handle;
    }

    public static AutoreleasePool push() {
        return new AutoreleasePool(ObjC.autoreleasePoolPush());
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            ObjC.autoreleasePoolPop(handle);
        }
    }
}
