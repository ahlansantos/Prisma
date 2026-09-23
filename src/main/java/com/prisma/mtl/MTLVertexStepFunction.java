package com.prisma.mtl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum MTLVertexStepFunction {
    Constant(0L),
    PerVertex(1L),
    PerInstance(2L),
    PerPatch(3L),
    PerPatchControlPoint(4L);

    public final long value;

    MTLVertexStepFunction(final long value) {
        this.value = value;
    }
}
