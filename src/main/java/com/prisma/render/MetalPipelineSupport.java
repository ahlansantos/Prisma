package com.prisma.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.MemorySegment;

@Environment(EnvType.CLIENT)
final class MetalPipelineSupport {
    private MetalPipelineSupport() {
    }

    static boolean sameHandle(@Nullable final MemorySegment left, @Nullable final MemorySegment right) {
        long leftValue = left == null ? 0L : left.address();
        long rightValue = right == null ? 0L : right.address();
        return leftValue == rightValue;
    }
}
