package com.prisma.mtl;

import com.prisma.objc.Msg;
import com.prisma.objc.ObjC;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.*;

@Environment(EnvType.CLIENT)
public final class MTLDepthStencilDescriptor implements AutoCloseable {
    private static final MemorySegment CLS = ObjC.clazz("MTLDepthStencilDescriptor");
    private static final Msg NEW = Msg.of("new", ADDRESS);
    private static final Msg SET_DEPTH_COMPARE_FUNCTION = Msg.ofVoid("setDepthCompareFunction:", JAVA_LONG);
    private static final Msg SET_DEPTH_WRITE_ENABLED = Msg.ofVoid("setDepthWriteEnabled:", JAVA_BOOLEAN);

    private final MemorySegment handle;

    private MTLDepthStencilDescriptor(final MemorySegment handle) {
        this.handle = handle;
    }

    public static MTLDepthStencilDescriptor create() {
        return new MTLDepthStencilDescriptor(NEW.sendPtr(CLS));
    }

    public MemorySegment handle() {
        return handle;
    }

    public void depthCompareFunction(final MTLCompareFunction function) {
        SET_DEPTH_COMPARE_FUNCTION.send(handle, function.value);
    }

    public void depthWriteEnabled(final boolean enabled) {
        SET_DEPTH_WRITE_ENABLED.send(handle, enabled);
    }

    @Override
    public void close() {
        ObjC.release(handle);
    }
}
