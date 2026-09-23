package com.prisma.mtl;

import com.prisma.objc.Msg;
import com.prisma.objc.ObjC;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

@Environment(EnvType.CLIENT)
public final class MTLTextureDescriptor implements AutoCloseable {
    private static final MemorySegment CLS = ObjC.clazz("MTLTextureDescriptor");
    private static final Msg NEW = Msg.of("new", ADDRESS);
    private static final Msg SET_TEXTURE_TYPE = Msg.ofVoid("setTextureType:", JAVA_LONG);
    private static final Msg SET_PIXEL_FORMAT = Msg.ofVoid("setPixelFormat:", JAVA_LONG);
    private static final Msg SET_WIDTH = Msg.ofVoid("setWidth:", JAVA_LONG);
    private static final Msg SET_HEIGHT = Msg.ofVoid("setHeight:", JAVA_LONG);
    private static final Msg SET_MIPMAP_LEVEL_COUNT = Msg.ofVoid("setMipmapLevelCount:", JAVA_LONG);
    private static final Msg SET_ARRAY_LENGTH = Msg.ofVoid("setArrayLength:", JAVA_LONG);
    private static final Msg SET_USAGE = Msg.ofVoid("setUsage:", JAVA_LONG);
    private static final Msg SET_STORAGE_MODE = Msg.ofVoid("setStorageMode:", JAVA_LONG);
    private static final Msg SET_HAZARD_TRACKING_MODE = Msg.ofVoid("setHazardTrackingMode:", JAVA_LONG);

    private final MemorySegment handle;

    private MTLTextureDescriptor(final MemorySegment handle) {
        this.handle = handle;
    }

    public static MTLTextureDescriptor create() {
        return new MTLTextureDescriptor(NEW.sendPtr(CLS));
    }

    public MemorySegment handle() {
        return handle;
    }

    public void textureType(final MTLTextureType type) {
        SET_TEXTURE_TYPE.send(handle, type.value);
    }

    public void pixelFormat(final MTLPixelFormat format) {
        SET_PIXEL_FORMAT.send(handle, format.value);
    }

    public void pixelFormat(final long formatValue) {
        SET_PIXEL_FORMAT.send(handle, formatValue);
    }

    public void width(final long width) {
        SET_WIDTH.send(handle, width);
    }

    public void height(final long height) {
        SET_HEIGHT.send(handle, height);
    }

    public void mipmapLevelCount(final long count) {
        SET_MIPMAP_LEVEL_COUNT.send(handle, count);
    }

    public void arrayLength(final long length) {
        SET_ARRAY_LENGTH.send(handle, length);
    }

    public void usage(final long usage) {
        SET_USAGE.send(handle, usage);
    }

    public void storageMode(final MTLStorageMode mode) {
        SET_STORAGE_MODE.send(handle, mode.value);
    }

    public void hazardTrackingMode(final MTLHazardTrackingMode mode) {
        SET_HAZARD_TRACKING_MODE.send(handle, mode.value);
    }

    @Override
    public void close() {
        ObjC.release(handle);
    }
}
