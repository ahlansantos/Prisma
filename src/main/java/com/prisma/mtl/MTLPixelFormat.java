package com.prisma.mtl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum MTLPixelFormat {
    R8Unorm(10L),
    R8Snorm(12L),
    R8Uint(13L),
    R8Sint(14L),

    R16Unorm(20L),
    R16Snorm(22L),
    R16Uint(23L),
    R16Sint(24L),
    R16Float(25L),

    RG8Unorm(30L),
    RG8Snorm(32L),
    RG8Uint(33L),
    RG8Sint(34L),

    R32Uint(53L),
    R32Sint(54L),
    R32Float(55L),

    RG16Unorm(60L),
    RG16Snorm(62L),
    RG16Uint(63L),
    RG16Sint(64L),
    RG16Float(65L),

    RGBA8Unorm(70L),
    BGRA8Unorm(80L),
    RGBA8Snorm(72L),
    RGBA8Uint(73L),
    RGBA8Sint(74L),

    RGB10A2Unorm(90L),
    RG11B10Float(92L),

    RG32Uint(103L),
    RG32Sint(104L),
    RG32Float(105L),

    RGBA16Unorm(110L),
    RGBA16Snorm(112L),
    RGBA16Uint(113L),
    RGBA16Sint(114L),
    RGBA16Float(115L),

    RGBA32Uint(123L),
    RGBA32Sint(124L),
    RGBA32Float(125L),

    Depth16Unorm(250L),
    Depth32Float(252L),
    Stencil8(253L),
    Depth24Unorm_Stencil8(255L),
    Depth32Float_Stencil8(260L),

    Invalid(0L);

    public final long value;

    MTLPixelFormat(final long value) {
        this.value = value;
    }

    public boolean hasStencil() {
        return hasStencil(this.value);
    }

    public static boolean hasStencil(final long pixelFormat) {
        return pixelFormat == Depth24Unorm_Stencil8.value || pixelFormat == Depth32Float_Stencil8.value;
    }

    public static MTLPixelFormat from(final com.mojang.blaze3d.GpuFormat format) {
        return switch (format) {
            case R8_UNORM -> R8Unorm;
            case R8_SNORM -> R8Snorm;
            case R8_UINT -> R8Uint;
            case R8_SINT -> R8Sint;
            case R16_UNORM -> R16Unorm;
            case R16_SNORM -> R16Snorm;
            case R16_UINT -> R16Uint;
            case R16_SINT -> R16Sint;
            case R16_FLOAT -> R16Float;
            case RG8_UNORM -> RG8Unorm;
            case RG8_SNORM -> RG8Snorm;
            case RG8_UINT -> RG8Uint;
            case RG8_SINT -> RG8Sint;
            case R32_UINT -> R32Uint;
            case R32_SINT -> R32Sint;
            case R32_FLOAT -> R32Float;
            case RG16_UNORM -> RG16Unorm;
            case RG16_SNORM -> RG16Snorm;
            case RG16_UINT -> RG16Uint;
            case RG16_SINT -> RG16Sint;
            case RG16_FLOAT -> RG16Float;
            case RGBA8_UNORM -> RGBA8Unorm;
            case RGBA8_SNORM -> RGBA8Snorm;
            case RGBA8_UINT -> RGBA8Uint;
            case RGBA8_SINT -> RGBA8Sint;
            case RGB10A2_UNORM -> RGB10A2Unorm;
            case RG11B10_FLOAT -> RG11B10Float;
            case RG32_UINT -> RG32Uint;
            case RG32_SINT -> RG32Sint;
            case RG32_FLOAT -> RG32Float;
            case RGBA16_UNORM -> RGBA16Unorm;
            case RGBA16_SNORM -> RGBA16Snorm;
            case RGBA16_UINT -> RGBA16Uint;
            case RGBA16_SINT -> RGBA16Sint;
            case RGBA16_FLOAT -> RGBA16Float;
            case RGBA32_UINT -> RGBA32Uint;
            case RGBA32_SINT -> RGBA32Sint;
            case RGBA32_FLOAT -> RGBA32Float;
            case D16_UNORM -> Depth16Unorm;
            case D32_FLOAT -> Depth32Float;
            case S8_UINT -> Stencil8;
            case D24_UNORM_S8_UINT -> Depth24Unorm_Stencil8;
            case D32_FLOAT_S8_UINT -> Depth32Float_Stencil8;
            default -> throw new IllegalStateException("Unsupported Metal texel buffer format: " + format);
        };
    }
}
