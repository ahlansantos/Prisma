package com.prisma.mtl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum MTLBlendOperation {
    Add(0L),
    Subtract(1L),
    ReverseSubtract(2L),
    Min(3L),
    Max(4L);

    public final long value;

    MTLBlendOperation(final long value) {
        this.value = value;
    }

    public static MTLBlendOperation from(final com.mojang.renderpearl.api.pipeline.BlendOp op) {
        return switch (op) {
            case ADD -> Add;
            case SUBTRACT -> Subtract;
            case REVERSE_SUBTRACT -> ReverseSubtract;
            case MIN -> Min;
            case MAX -> Max;
        };
    }
}
