package com.prisma.mtl;

import com.prisma.objc.Msg;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

@Environment(EnvType.CLIENT)
public final class MTLBuffer {
    private static final Msg CONTENTS = Msg.of("contents", ADDRESS);
    private static final Msg LENGTH = Msg.of("length", JAVA_LONG);

    private final MemorySegment handle;

    MTLBuffer(final MemorySegment handle) {
        if (handle == null || handle.address() == 0L) {
            throw new IllegalArgumentException("MTLBuffer handle is null");
        }
        this.handle = handle;
    }

    public MemorySegment handle() {
        return handle;
    }

    public MemorySegment contents() {
        return CONTENTS.sendPtr(handle);
    }

    public long length() {
        return LENGTH.sendLong(handle);
    }
}
