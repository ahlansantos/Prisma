#include <metal_stdlib>
using namespace metal;

#include "prisma_api.metal"
#include "voxel_common.metal"

kernel void prisma_denoiser_cs(
    uint2 gid [[thread_position_in_grid]],
    texture2d<float, access::read> giTexture [[texture(0)]],
    texture2d<float, access::write> denoisedGiTexture [[texture(1)]],
    texture2d<float, access::read> vxgiIn [[texture(4)]],
    texture2d<float, access::write> vxgiOut [[texture(5)]],
    texture2d<float, access::read> vxgiHistory [[texture(6)]],
    texture2d<float> normalTex [[texture(2)]],
    depth2d<float> worldDepthTex [[texture(3)]],
    constant CameraData& camera [[buffer(10)]],
    constant EnvironmentData& env [[buffer(11)]],
    constant RenderSettings& settings [[buffer(12)]],
    constant VoxelUniforms& uVoxel [[buffer(2)]],
    constant float4& prevCam [[buffer(13)]]
) {
    if (gid.x >= denoisedGiTexture.get_width() || gid.y >= denoisedGiTexture.get_height()) return;

    float2 dnW = float2(worldDepthTex.get_width(), worldDepthTex.get_height());
    float2 dnN = float2(normalTex.get_width(), normalTex.get_height());
    float2 dnG = float2(giTexture.get_width(), giTexture.get_height());
    uint2 dMax = uint2(worldDepthTex.get_width() - 1, worldDepthTex.get_height() - 1);
    uint2 nMax = uint2(normalTex.get_width() - 1, normalTex.get_height() - 1);
    float2 cuv = (float2(gid) + 0.5f) / dnG;

    float centerDepth = worldDepthTex.read(min(uint2(cuv * dnW), dMax));
    if (centerDepth <= 0.00005f) {
        denoisedGiTexture.write(giTexture.read(gid), gid);
        vxgiOut.write(vxgiIn.read(gid), gid);
        return;
    }

    float3 centerNormal = normalTex.read(min(uint2(cuv * dnN), nMax)).xyz * 2.0f - 1.0f;
    float4 centerColor = giTexture.read(gid); // RGB: Diffuse, A: Shadow Darkening

    float4 sumColor = float4(0.0f);
    float sumWeight = 0.0f;

    // Fast 5x5 Edge-Preserving Spatial Filter (Bilateral / A-Trous inspired)
    // Drops noise from 1-spp GI significantly without blurring sharp edges.
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            int2 sampleCoord = int2(gid) + int2(x, y);
            
            // Clamp coordinates
            sampleCoord = clamp(sampleCoord, int2(0), int2(giTexture.get_width() - 1, giTexture.get_height() - 1));

            float2 suv = (float2(sampleCoord) + 0.5f) / dnG;
            float sampleDepth = worldDepthTex.read(min(uint2(suv * dnW), dMax));
            float3 sampleNormal = normalTex.read(min(uint2(suv * dnN), nMax)).xyz * 2.0f - 1.0f;
            float4 sampleColor = giTexture.read(uint2(sampleCoord));

            // Bilateral Weights
            float depthDiff = abs(centerDepth - sampleDepth);
            float normalDot = max(0.0f, dot(centerNormal, sampleNormal));
            
            // Spatial weight (Gaussian)
            float spatialWeight = exp(-(float(x*x + y*y)) / (2.0f * 4.0f * 4.0f));
            
            // Edge-stopping weights
            float depthWeight = exp(-depthDiff * 10000.0f); // Highly sensitive to depth discontinuities
            float normalWeight = pow(normalDot, 4.0f);

            float w = spatialWeight * depthWeight * normalWeight;
            sumColor += sampleColor * w;
            sumWeight += w;
        }
    }

    float4 finalDenoised = (sumWeight > 0.0001f) ? (sumColor / sumWeight) : centerColor;
    
    denoisedGiTexture.write(finalDenoised, gid);

    // ---- VXGI: 5x5 edge-aware cross-bilateral spatial filter + temporal accumulation ----
    float4 vSum = float4(0.0f);
    float vW = 0.0f;
    float4 raw = vxgiIn.read(gid);
    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            if (abs(x) == 2 && abs(y) == 2) continue; // Round 5x5 kernel (21 taps)
            int2 sc = clamp(int2(gid) + int2(x, y), int2(0), int2(giTexture.get_width() - 1, giTexture.get_height() - 1));
            float2 suv = (float2(sc) + 0.5f) / dnG;
            float sd = worldDepthTex.read(min(uint2(suv * dnW), dMax));
            float3 sn = normalTex.read(min(uint2(suv * dnN), nMax)).xyz * 2.0f - 1.0f;
            
            float relDepth = abs(centerDepth - sd) / max(centerDepth, 1e-5f);
            float nDot = max(0.0f, dot(centerNormal, sn));
            
            float4 v = vxgiIn.read(uint2(sc));
            float distSq = float(x * x + y * y);
            float spatialW = exp(-distSq * 0.25f);
            float depthW = exp(-relDepth * 300.0f);
            float normalW = pow(nDot, 16.0f);
            float w = spatialW * depthW * normalW * ((sd > 0.00005f) ? 1.0f : 0.0f) * v.a;
            vSum += v * w;
            vW += w;
        }
    }
    float3 spatial = (vW > 0.0001f) ? (vSum.rgb / vW) : raw.rgb;

    float3 camPos = uVoxel.camPos.xyz;
    float3 pWorld = reconstructWorldPos(cuv, centerDepth, camPos, uVoxel.invViewProj);
    float curDist = length(pWorld - camPos);
    float3 outRgb = spatial;
    if (raw.a > 0.0f) {
        // Correct camera-relative world space reprojection
        float3 prevRelPos = pWorld - prevCam.xyz;
        float4 pc = uVoxel.prevViewProj * float4(prevRelPos, 1.0f);
        if (pc.w > 0.0001f) {
            float2 puv = (pc.xy / pc.w) * 0.5f + 0.5f;
            if (all(puv >= 0.001f) && all(puv <= 0.999f)) {
                uint2 hc = min(uint2(puv * dnG), uint2(giTexture.get_width() - 1, giTexture.get_height() - 1));
                float4 hist = vxgiHistory.read(hc);
                float prevDist = length(prevRelPos);
                bool valid = (hist.a > 0.0f) && abs(hist.a - prevDist) < (0.12f * prevDist + 0.25f);
                if (valid) {
                    float3 histRgb = hist.rgb;
                    if (any(isnan(histRgb)) || any(isinf(histRgb))) histRgb = spatial;
                    float3 boxCenter = spatial;
                    float3 boxExt = max(spatial * 0.90f, float3(0.15f));
                    float3 clampedHist = clamp(histRgb, boxCenter - boxExt, boxCenter + boxExt);
                    // ~25 frame effective accumulation: near-zero flicker, still reacts to light changes
                    outRgb = mix(clampedHist, spatial, 0.04f);
                }
            }
        }
    }
    if (any(isnan(outRgb)) || any(isinf(outRgb))) outRgb = float3(0.0f);
    vxgiOut.write(float4(outRgb, (raw.a > 0.0f) ? curDist : 0.0f), gid);
}
