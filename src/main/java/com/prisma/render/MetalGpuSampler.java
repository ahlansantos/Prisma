package com.prisma.render;

import com.prisma.mtl.MTLSamplerAddressMode;
import com.prisma.mtl.MTLSamplerDescriptor;
import com.prisma.mtl.MTLSamplerMinMagFilter;
import com.prisma.mtl.MTLSamplerMipFilter;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.NonNull;

import java.lang.foreign.MemorySegment;
import java.util.OptionalDouble;

@Environment(EnvType.CLIENT)
final class MetalGpuSampler implements GpuSampler {
    private final MetalDevice device;
    private final MemorySegment nativeHandle;
    private final AddressMode addressModeU;
    private final AddressMode addressModeV;
    private final FilterMode minFilter;
    private final FilterMode magFilter;
    private final int maxAnisotropy;
    private final OptionalDouble maxLod;
    private boolean closed;

    MetalGpuSampler(
            final MetalDevice device,
            final AddressMode addressModeU,
            final AddressMode addressModeV,
            final FilterMode minFilter,
            final FilterMode magFilter,
            final int maxAnisotropy,
            final OptionalDouble maxLod
    ) {
        this.device = device;
        try (MTLSamplerDescriptor descriptor = MTLSamplerDescriptor.create()) {
            descriptor.minFilter(MTLSamplerMinMagFilter.from(minFilter));
            descriptor.magFilter(MTLSamplerMinMagFilter.from(magFilter));
            descriptor.mipFilter(toMtlMipFilter(maxLod));
            descriptor.sAddressMode(MTLSamplerAddressMode.from(addressModeU));
            descriptor.tAddressMode(MTLSamplerAddressMode.from(addressModeV));
            descriptor.maxAnisotropy(Math.max(1, maxAnisotropy));
            descriptor.lodMinClamp(0.0f);
            double lodMaxClamp = toMtlMaxLodClamp(maxLod);
            descriptor.lodMaxClamp(lodMaxClamp >= 0.0 && Double.isFinite(lodMaxClamp) ? (float) lodMaxClamp : Float.MAX_VALUE);
            this.nativeHandle = device.metalDevice().newSamplerState(descriptor);
        }
        this.addressModeU = addressModeU;
        this.addressModeV = addressModeV;
        this.minFilter = minFilter;
        this.magFilter = magFilter;
        this.maxAnisotropy = maxAnisotropy;
        this.maxLod = maxLod;
    }

    @Override
    public @NonNull AddressMode getAddressModeU() {
        return this.addressModeU;
    }

    @Override
    public @NonNull AddressMode getAddressModeV() {
        return this.addressModeV;
    }

    @Override
    public @NonNull FilterMode getMinFilter() {
        return this.minFilter;
    }

    @Override
    public @NonNull FilterMode getMagFilter() {
        return this.magFilter;
    }

    @Override
    public int getMaxAnisotropy() {
        return this.maxAnisotropy;
    }

    @Override
    public @NonNull OptionalDouble getMaxLod() {
        return this.maxLod;
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.device.queueResourceRelease(this.nativeHandle);
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    MemorySegment nativeHandle() {
        return this.nativeHandle;
    }

    private static MTLSamplerMipFilter toMtlMipFilter(final OptionalDouble maxLod) {
        return maxLod.orElse(1000.0) > 0.25 ? MTLSamplerMipFilter.Linear : MTLSamplerMipFilter.Nearest;
    }

    private static double toMtlMaxLodClamp(final OptionalDouble maxLod) {
        return Math.max(0.25, maxLod.orElse(1000.0));
    }
}
