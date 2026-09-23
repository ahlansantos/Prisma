package com.prisma.mtl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.system.MemoryStack;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

@Environment(EnvType.CLIENT)
public final class MTLScissorRect {
    private MTLScissorRect() {
    }

    static MemorySegment on(final MemoryStack stack, final long x, final long y, final long width, final long height) {
        MemorySegment rect = MemorySegment.ofAddress(stack.nmalloc(8, 32)).reinterpret(32);
        rect.set(JAVA_LONG, 0, x);
        rect.set(JAVA_LONG, 8, y);
        rect.set(JAVA_LONG, 16, width);
        rect.set(JAVA_LONG, 24, height);
        return rect;
    }
}
