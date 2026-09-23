package com.prisma.mtl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.system.MemoryStack;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

@Environment(EnvType.CLIENT)
public final class MTLOrigin {
    private MTLOrigin() {
    }

    static MemorySegment on(final MemoryStack stack, final long x, final long y, final long z) {
        MemorySegment origin = MemorySegment.ofAddress(stack.nmalloc(8, 24)).reinterpret(24);
        origin.set(JAVA_LONG, 0, x);
        origin.set(JAVA_LONG, 8, y);
        origin.set(JAVA_LONG, 16, z);
        return origin;
    }
}
