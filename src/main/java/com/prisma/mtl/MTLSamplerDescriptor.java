package com.prisma.mtl;

import com.prisma.objc.Msg;
import com.prisma.objc.ObjC;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.*;

@Environment(EnvType.CLIENT)
public final class MTLSamplerDescriptor implements AutoCloseable {
    private static final MemorySegment CLS = ObjC.clazz("MTLSamplerDescriptor");
    private static final Msg NEW = Msg.of("new", ADDRESS);
    private static final Msg SET_MIN_FILTER = Msg.ofVoid("setMinFilter:", JAVA_LONG);
    private static final Msg SET_MAG_FILTER = Msg.ofVoid("setMagFilter:", JAVA_LONG);
    private static final Msg SET_MIP_FILTER = Msg.ofVoid("setMipFilter:", JAVA_LONG);
    private static final Msg SET_S_ADDRESS_MODE = Msg.ofVoid("setSAddressMode:", JAVA_LONG);
    private static final Msg SET_T_ADDRESS_MODE = Msg.ofVoid("setTAddressMode:", JAVA_LONG);
    private static final Msg SET_MAX_ANISOTROPY = Msg.ofVoid("setMaxAnisotropy:", JAVA_LONG);
    private static final Msg SET_LOD_MIN_CLAMP = Msg.ofVoid("setLodMinClamp:", JAVA_FLOAT);
    private static final Msg SET_LOD_MAX_CLAMP = Msg.ofVoid("setLodMaxClamp:", JAVA_FLOAT);

    private final MemorySegment handle;

    private MTLSamplerDescriptor(final MemorySegment handle) {
        this.handle = handle;
    }

    public static MTLSamplerDescriptor create() {
        return new MTLSamplerDescriptor(NEW.sendPtr(CLS));
    }

    public MemorySegment handle() {
        return handle;
    }

    public void minFilter(final MTLSamplerMinMagFilter filter) {
        SET_MIN_FILTER.send(handle, filter.value);
    }

    public void magFilter(final MTLSamplerMinMagFilter filter) {
        SET_MAG_FILTER.send(handle, filter.value);
    }

    public void mipFilter(final MTLSamplerMipFilter filter) {
        SET_MIP_FILTER.send(handle, filter.value);
    }

    public void sAddressMode(final MTLSamplerAddressMode mode) {
        SET_S_ADDRESS_MODE.send(handle, mode.value);
    }

    public void tAddressMode(final MTLSamplerAddressMode mode) {
        SET_T_ADDRESS_MODE.send(handle, mode.value);
    }

    public void maxAnisotropy(final long maxAnisotropy) {
        SET_MAX_ANISOTROPY.send(handle, maxAnisotropy);
    }

    public void lodMinClamp(final float clamp) {
        SET_LOD_MIN_CLAMP.send(handle, clamp);
    }

    public void lodMaxClamp(final float clamp) {
        SET_LOD_MAX_CLAMP.send(handle, clamp);
    }

    @Override
    public void close() {
        ObjC.release(handle);
    }
}
