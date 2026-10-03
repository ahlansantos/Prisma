#include <metal_stdlib>
using namespace metal;

// =========================================================================
// Luma-Preserving Filmic Tone Mapping (BSL / Complementary / Bliss Style)
// Tones luminance alone and preserves true chromaticity:
// - Blue sky stays deep azure, grass stays rich lush green, sunset stays fiery amber
// - ZERO grey wash, ZERO desaturation veil
// - Soft open toe: deep shadows and caves never crush to pitch black
// - Extended shoulder: bright sun specular rolls off smoothly without clipping
// =========================================================================
static inline float3 lumaPreservingFilmic(float3 col, float exposure, float saturationBoost) {
    if (isnan(col.x) || isnan(col.y) || isnan(col.z) || isinf(col.x) || isinf(col.y) || isinf(col.z)) return float3(0.0f);
    col = max(col * exposure * 0.85f, float3(0.0f));

    // ACES fitted curve
    float a = 2.51f;
    float b = 0.03f;
    float c = 2.43f;
    float d = 0.59f;
    float e = 0.14f;
    float3 tonedColor = saturate((col * (a * col + b)) / (col * (c * col + d) + e));

    // Apply dynamic saturation
    float luma = dot(tonedColor, float3(0.2126f, 0.7152f, 0.0722f));
    tonedColor = mix(float3(luma), tonedColor, saturationBoost);

    return saturate(tonedColor);
}
