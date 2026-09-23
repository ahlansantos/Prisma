package com.prisma.mtl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum MTLCompareFunction {
    Never(0L),
    Less(1L),
    Equal(2L),
    LessEqual(3L),
    Greater(4L),
    NotEqual(5L),
    GreaterEqual(6L),
    Always(7L);

    public final long value;

    MTLCompareFunction(final long value) {
        this.value = value;
    }

    public static MTLCompareFunction from(final com.mojang.blaze3d.platform.CompareOp op) {
        return switch (op) {
            case NEVER_PASS -> Never;
            case LESS_THAN -> Less;
            case EQUAL -> Equal;
            case LESS_THAN_OR_EQUAL -> LessEqual;
            case GREATER_THAN -> Greater;
            case NOT_EQUAL -> NotEqual;
            case GREATER_THAN_OR_EQUAL -> GreaterEqual;
            case ALWAYS_PASS -> Always;
        };
    }
}
