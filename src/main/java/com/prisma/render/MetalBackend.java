package com.prisma.render;

import com.prisma.Prisma;
import com.prisma.mtl.CAMetalLayer;
import com.prisma.mtl.MTLDevice;
import com.prisma.objc.Cocoa;
import com.prisma.objc.Msg;
import com.prisma.objc.ObjC;
import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.api.device.GpuBackend;
import com.mojang.renderpearl.api.device.GpuDebugOptions;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

@Environment(EnvType.CLIENT)
public class MetalBackend implements GpuBackend {
    private static final Msg CONTENT_VIEW = Msg.of("contentView", ValueLayout.ADDRESS);

    private long windowHandle;
    private MTLDevice metalDevice;
    private MetalDevice device;

    private static @Nullable MetalDevice activeDevice;

    public static @Nullable MetalDevice getActiveDevice() {
        return activeDevice;
    }

    @Override
    public @NonNull String getName() {
        return "Metal";
    }

    @Override
    public void loadLibrary() {
        // Metal is a system framework, exposed through the Objective-C runtime: nothing to load.
    }

    @Override
    public void unloadLibrary() {
    }

    @Override
    public long createWindow(final String title, final int width, final int height, final long flags) {
        long handle = SDLVideo.SDL_CreateWindow(title, width, height, flags | SDLVideo.SDL_WINDOW_METAL);
        this.windowHandle = handle;
        if (handle != 0L && this.device != null) {
            this.attachWindowLayer();
        }
        return handle;
    }

    @Override
    public @NonNull GpuDevice createDevice(final @NonNull GpuDebugOptions debugOptions) throws BackendCreationException {
        MTLDevice metalDevice = MTLDevice.createSystemDefault();
        if (metalDevice == null) {
            throw new BackendCreationException("MTLCreateSystemDefaultDevice returned null", BackendCreationException.Reason.OTHER);
        }
        this.metalDevice = metalDevice;

        String deviceName = metalDevice.name();
        if (deviceName.isBlank()) {
            deviceName = "<unknown Metal device>";
        }

        Prisma.LOGGER.info("Metal device: {}", deviceName);

        try {
            MetalDevice device = new MetalDevice(debugOptions, metalDevice.handle(), deviceName);
            this.device = device;
            activeDevice = device;
            if (this.windowHandle != 0L) {
                this.attachWindowLayer();
            }
            return new FrontendGpuDevice(device);
        } catch (Throwable throwable) {
            throw new BackendCreationException("Metal device initialization failed: " + throwable.getMessage(), BackendCreationException.Reason.OTHER);
        }
    }

    private void attachWindowLayer() {
        int properties = SDLVideo.SDL_GetWindowProperties(this.windowHandle);
        long windowPointer = SDLProperties.SDL_GetPointerProperty(properties, SDLVideo.SDL_PROP_WINDOW_COCOA_WINDOW_POINTER, 0L);
        MemorySegment nsWindow = MemorySegment.ofAddress(windowPointer);
        if (ObjC.isNil(nsWindow)) {
            throw new IllegalStateException("SDL window does not expose a Cocoa NSWindow");
        }

        MemorySegment nsView = CONTENT_VIEW.sendPtr(nsWindow);
        if (ObjC.isNil(nsView)) {
            throw new IllegalStateException("SDL window does not expose a Cocoa content view");
        }

        Cocoa cocoa = new Cocoa(nsWindow, nsView);
        CAMetalLayer metalLayer = new CAMetalLayer(this.metalDevice, cocoa.backingScaleFactor());
        cocoa.setViewLayer(metalLayer.handle());
        this.device.attachWindow(cocoa, metalLayer);
    }
}
