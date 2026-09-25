package com.prisma.render;

import com.prisma.Prisma;
import com.prisma.mtl.CAMetalLayer;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.backend.api.GpuSurfaceBackend;
import com.mojang.renderpearl.api.device.SurfaceException;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
final class MetalSurface implements GpuSurfaceBackend {
    private static final Set<GpuSurface.PresentMode> SUPPORTED_PRESENT_MODES = EnumSet.of(GpuSurface.PresentMode.FIFO, GpuSurface.PresentMode.MAILBOX);
    private static final AtomicBoolean LOGGED_FIRST_CONFIG = new AtomicBoolean();
    private final MetalDevice device;
    private final CAMetalLayer metalLayer;
    private GpuSurface.Configuration configuration;
    private MetalCommandEncoder pendingPresentEncoder;

    MetalSurface(final MetalDevice device, final CAMetalLayer metalLayer) {
        this.device = device;
        this.metalLayer = metalLayer;
    }

    @Override
    public void configure(final GpuSurface.Configuration config) throws SurfaceException {
        if (config.width() <= 0 || config.height() <= 0) {
            throw new SurfaceException("Metal surface configuration must be positive, got " + config.width() + "x" + config.height());
        }

        this.metalLayer.configure(
                config.width(),
                config.height(),
                config.presentMode() == GpuSurface.PresentMode.MAILBOX
        );

        if (LOGGED_FIRST_CONFIG.compareAndSet(false, true)) {
            Prisma.LOGGER.info("Metal: surface configured {}x{} ({})", config.width(), config.height(), config.presentMode());
        }

        this.configuration = config;
    }

    @Override
    public boolean isSuboptimal() {
        return false;
    }

    @Override
    public void acquireNextTexture() {
    }

    @Override
    public void blitFromTexture(final @NonNull CommandEncoderBackend commandEncoder, final @NonNull GpuTextureView textureView) {
        if (!(commandEncoder instanceof MetalCommandEncoder metalEncoder)) {
            throw new IllegalArgumentException("Metal surface requires MetalCommandEncoder");
        }

        metalEncoder.presentTextureToDrawable(metalLayer, textureView);
        this.pendingPresentEncoder = metalEncoder;
    }

    @Override
    public void present() {
        pendingPresentEncoder.submit();
    }

    @Override
    public void close() {
    }

    @Override
    public @NonNull Collection<GpuSurface.PresentMode> supportedPresentModes() {
        return SUPPORTED_PRESENT_MODES;
    }
}
