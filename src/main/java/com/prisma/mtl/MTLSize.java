package com.prisma.mtl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.system.MemoryStack;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

@Environment(EnvType.CLIENT)
public final class MTLSize {
    private MTLSize() {
    }

    static MemorySegment on(final MemoryStack stack, final long width, final long height, final long depth) {
        MemorySegment size = MemorySegment.ofAddress(stack.nmalloc(8, 24)).reinterpret(24);
        size.set(JAVA_LONG, 0, width);
        size.set(JAVA_LONG, 8, height);
        size.set(JAVA_LONG, 16, depth);
        return size;
    }
}
