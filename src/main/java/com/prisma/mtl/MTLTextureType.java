package com.prisma.mtl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum MTLTextureType {
    Type1D(0L),
    Type1DArray(1L),
    Type2D(2L),
    Type2DArray(3L),
    TypeCube(5L),
    TypeCubeArray(6L);

    public final long value;

    MTLTextureType(final long value) {
        this.value = value;
    }
}
