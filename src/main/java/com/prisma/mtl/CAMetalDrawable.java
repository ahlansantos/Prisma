package com.prisma.mtl;

import com.prisma.objc.Msg;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.ADDRESS;

@Environment(EnvType.CLIENT)
public record CAMetalDrawable(MemorySegment handle) {
    private static final Msg TEXTURE = Msg.of("texture", ADDRESS);

    public MemorySegment texture() {
        return TEXTURE.sendPtr(handle);
    }
}
