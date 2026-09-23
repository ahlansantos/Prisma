package com.prisma.mtl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.foreign.MemorySegment;

@Environment(EnvType.CLIENT)
public record MTLFence(MemorySegment handle) {
}
