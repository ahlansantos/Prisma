#include <metal_stdlib>
using namespace metal;

#include "prisma_api.metal"

kernel void prisma_denoiser_cs(
    uint2 gid [[thread_position_in_grid]],
    texture2d<float, access::read> giTexture [[texture(0)]],
    texture2d<float, access::write> denoisedGiTexture [[texture(1)]],
    texture2d<float> normalTex [[texture(2)]],
    depth2d<float> worldDepthTex [[texture(3)]],
    constant CameraData& camera [[buffer(10)]],
    constant EnvironmentData& env [[buffer(11)]],
    constant RenderSettings& settings [[buffer(12)]]
) {
    if (gid.x >= denoisedGiTexture.get_width() || gid.y >= denoisedGiTexture.get_height()) return;

    float centerDepth = worldDepthTex.read(gid);
    if (centerDepth >= 1.0f) {
        denoisedGiTexture.write(giTexture.read(gid), gid);
        return;
    }

    float3 centerNormal = normalTex.read(gid).xyz * 2.0f - 1.0f;
    float4 centerColor = giTexture.read(gid); // RGB: Diffuse, A: Shadow Darkening

    float4 sumColor = float4(0.0f);
    float sumWeight = 0.0f;

    // Fast 5x5 Edge-Preserving Spatial Filter (Bilateral / A-Trous inspired)
    // Drops noise from 1-spp GI significantly without blurring sharp edges.
    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            int2 sampleCoord = int2(gid) + int2(x, y);
            
            // Clamp coordinates
            sampleCoord = clamp(sampleCoord, int2(0), int2(giTexture.get_width() - 1, giTexture.get_height() - 1));

            float sampleDepth = worldDepthTex.read(uint2(sampleCoord));
            float3 sampleNormal = normalTex.read(uint2(sampleCoord)).xyz * 2.0f - 1.0f;
            float4 sampleColor = giTexture.read(uint2(sampleCoord));

            // Bilateral Weights
            float depthDiff = abs(centerDepth - sampleDepth);
            float normalDot = max(0.0f, dot(centerNormal, sampleNormal));
            
            // Spatial weight (Gaussian)
            float spatialWeight = exp(-(float(x*x + y*y)) / (2.0f * 1.5f * 1.5f));
            
            // Edge-stopping weights
            float depthWeight = exp(-depthDiff * 10000.0f); // Highly sensitive to depth discontinuities
            float normalWeight = pow(normalDot, 32.0f);    // Sensitive to angle changes

            float w = spatialWeight * depthWeight * normalWeight;
            sumColor += sampleColor * w;
            sumWeight += w;
        }
    }

    float4 finalDenoised = (sumWeight > 0.0001f) ? (sumColor / sumWeight) : centerColor;
    
    denoisedGiTexture.write(finalDenoised, gid);
}
