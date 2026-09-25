package com.prisma.render;

import com.prisma.mtl.*;
import com.prisma.objc.Msg;
import com.prisma.objc.ObjC;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.common.BaseGpuTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.MemorySegment;

@Environment(EnvType.CLIENT)
final class MetalGpuTexture extends BaseGpuTexture {
    private static final Msg SET_LABEL = Msg.ofVoid("setLabel:", java.lang.foreign.ValueLayout.ADDRESS);

    private final MetalDevice device;
    private final MTLPixelFormat mtlPixelFormat;
    private boolean closed;
    @Nullable
    private Vector4fc materializedColorClear;
    @Nullable
    private Double materializedDepthClear;
    private int views = 1;
    @Nullable
    private MemorySegment nativeHandle;

    MetalGpuTexture(
            final MetalDevice device,
            @GpuTexture.Usage final int usage,
            final String label,
            final GpuFormat format,
            final int width,
            final int height,
            final int depthOrLayers,
            final int mipLevels
    ) {
        super(usage, label, format, width, height, depthOrLayers, mipLevels);
        this.device = device;
        this.mtlPixelFormat = MTLPixelFormat.from(format);

        try (MTLTextureDescriptor descriptor = MTLTextureDescriptor.create()) {
            descriptor.pixelFormat(this.mtlPixelFormat);
            descriptor.width(width);
            descriptor.height(height);
            if ((usage & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0) {
                if (depthOrLayers > 6) {
                    descriptor.textureType(MTLTextureType.TypeCubeArray);
                    descriptor.arrayLength(depthOrLayers / 6);
                } else {
                    descriptor.textureType(MTLTextureType.TypeCube);
                    descriptor.arrayLength(1);
                }
            } else if (depthOrLayers > 1) {
                descriptor.textureType(MTLTextureType.Type2DArray);
                descriptor.arrayLength(depthOrLayers);
            }
            descriptor.mipmapLevelCount(Math.max(mipLevels, 1));
            descriptor.usage(toMtlTextureUsage(usage));
            descriptor.storageMode(MTLStorageMode.Private);
            descriptor.hazardTrackingMode(MTLHazardTrackingMode.Untracked);
            this.nativeHandle = device.metalDevice().newTexture(descriptor);
        }
        if (label != null) {
            MemorySegment nsLabel = ObjC.nsString(label);
            SET_LABEL.send(this.nativeHandle, nsLabel);
            ObjC.release(nsLabel);
        }
    }

    int pixelSize() {
        return this.getFormat().blockSize();
    }

    void recordMaterializedClear(@Nullable final Vector4fc color, @Nullable final Double depth) {
        if (color != null) {
            this.materializedColorClear = color;
        }
        if (depth != null) {
            this.materializedDepthClear = depth;
        }
    }

    boolean clearIsRedundant(@Nullable final Vector4fc color, @Nullable final Double depth) {
        return (color == null || color.equals(this.materializedColorClear))
                && (depth == null || depth.equals(this.materializedDepthClear));
    }

    void markContentsDirty() {
        this.materializedColorClear = null;
        this.materializedDepthClear = null;
    }

    MemorySegment nativeHandle() {
        if (this.nativeHandle == null) {
            throw new IllegalStateException("Native Metal texture is closed");
        }
        return this.nativeHandle;
    }

    void queueNativeRelease(final MemorySegment handle) {
        this.device.queueResourceRelease(handle);
    }

    void addView() {
        this.views++;
    }

    void removeView() {
        this.views--;
        if (this.views < 0) {
            throw new IllegalStateException("Too many views removed from texture");
        }
        if (this.closed && this.views == 0 && this.nativeHandle != null) {
            MemorySegment handle = this.nativeHandle;
            this.nativeHandle = null;
            this.device.queueResourceRelease(handle);
        }
    }

    MTLPixelFormat mtlPixelFormat() {
        return this.mtlPixelFormat;
    }

    MTLPixelFormat mtlStencilPixelFormat() {
        return this.mtlPixelFormat.hasStencil() ? this.mtlPixelFormat : MTLPixelFormat.Invalid;
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.removeView();
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    private static long toMtlTextureUsage(@GpuTexture.Usage final int usage) {
        long result = 0L;
        if ((usage & GpuTexture.USAGE_TEXTURE_BINDING) != 0 || (usage & GpuTexture.USAGE_COPY_DST) != 0 || (usage & GpuTexture.USAGE_COPY_SRC) != 0) {
            result |= MTLTextureUsage.ShaderRead.value;
        }
        if ((usage & GpuTexture.USAGE_RENDER_ATTACHMENT) != 0) {
            result |= MTLTextureUsage.RenderTarget.value;
            result |= MTLTextureUsage.ShaderRead.value;
        }
        return result == 0L ? MTLTextureUsage.ShaderRead.value : result;
    }
}
