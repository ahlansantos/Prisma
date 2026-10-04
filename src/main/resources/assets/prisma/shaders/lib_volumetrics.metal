#include <metal_stdlib>
using namespace metal;

static inline float3 evaluateVolumetricFog(
    float3 ro, float3 rd, float3 pWorld, float3 celestialDir, float3 celestialCol, float sunWeight,
    constant VoxelUniforms& uVoxel, constant RenderSettings& settings,
    device const uint2* voxelGrid,
    uint2 gid,
    texture2d<float> blockAtlasTex, sampler smp,
    constant float4* blockUvTable, constant ulong* bitmaskTable
) {
    if (settings.volFogEnabled < 0.5f) return float3(0.0f);
    
    float3 volumetricFog = float3(0.0f);
    int numSteps = max(4, min(int(settings.volFogSamples), 16));
    if (numSteps <= 0) return float3(0.0f);
    
    // Cap march distance so stepSize doesn't become huge and over-multiply local lights
    float marchDist = min(min(length(pWorld - ro), float(uVoxel.gridSize.x) * 0.5f), 56.0f);
    if (marchDist < 0.5f) return float3(0.0f);
    
    float stepSize = marchDist / float(numSteps);
    
    // Interleaved Gradient Noise (zero banding, smooth dither instead of scanlines)
    float ign = fract(52.9829189f * fract(dot(float2(gid), float2(0.06711056f, 0.00583715f))));
    float tStart = stepSize * ign;
    
    float scatteringAlbedo = 0.85f;
    float extinction = 0.085f * settings.volFogIntensity;
    
    float cosThetaS = dot(rd, celestialDir);
    float gF = 0.65f;
    float gB = -0.15f;
    float forwardPhase = (1.0f - gF*gF) / (4.0f * 3.14159f * pow(1.0f + gF*gF - 2.0f * gF * cosThetaS, 1.5f));
    float sidePhase    = (1.0f - gB*gB) / (4.0f * 3.14159f * pow(1.0f + gB*gB - 2.0f * gB * cosThetaS, 1.5f));
    float phaseSun     = forwardPhase * 0.85f + sidePhase * 0.40f;
    
    int lightCount = int(uVoxel.gridSize.w);

    for (int step = 0; step < numSteps; step++) {
        float t = tStart + float(step) * stepSize;
        if (t >= marchDist) break;

        float3 samplePos = ro + rd * t;
        uint2 sVox = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, int3(floor(samplePos)));
        if ((sVox.x & 4) != 0) break; // Air fog stops at water boundary
        uint sShape = (sVox.y >> 24) & 0xFF;
        if (((sVox.x & 1) != 0) && (sShape == 0 || sShape == 10)) {
            break;
        }

        float2 voxLight = unpackVoxelLight(sVox);
        float sampleSky = voxLight.y;
        float sampleBlock = voxLight.x;
        if (sampleSky < 0.015f && sampleBlock < 0.015f) {
            continue;
        }

        float3 inScatterSun = float3(0.0f);
        float3 inScatterPts = float3(0.0f);

        // --- 1. Volumetric Sun / Moon Rays (God Rays) ---
        if (celestialDir.y > 0.02f && sampleSky > 0.02f) {
            float3 sunRayTarget = samplePos + celestialDir * 32.0f;
            float3 sunTint = float3(1.0f);
            ShadowRayResult sunSr = traceDdaShadowRay(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, samplePos, sunRayTarget, blockAtlasTex, smp, blockUvTable, bitmaskTable);
            float sunVis = sunSr.vis; sunTint = sunSr.tint;
            if (sunVis > 0.01f) {
                inScatterSun += (celestialCol * sunTint) * (phaseSun * scatteringAlbedo * sunVis * 1.15f);
            }
        }

        // --- 2. Volumetric Point Lights (3D Spatial Light Volumes) ---
        int maxVolLights = min(lightCount, min(int(settings.maxPointLights), 64));
        for (int li = 0; li < maxVolLights; li++) {
            float3 lPos = uVoxel.lights[li].posAndRadius.xyz;
            float lRad = uVoxel.lights[li].posAndRadius.w;

            float3 toLight = lPos - samplePos;
            float dist = length(toLight);
            if (dist >= lRad) continue;

            float ptIntensity = min(uVoxel.lights[li].colorAndIntensity.w, 3.5f);
            float3 lColor = uVoxel.lights[li].colorAndIntensity.xyz * ptIntensity;

            float cosTheta = dot(rd, toLight / max(dist, 0.0001f));
            float g = 0.3f;
            float g2 = g * g;
            float phase = (1.0f - g2) / (4.0f * 3.14159265f * pow(1.0f + g2 - 2.0f * g * cosTheta, 1.5f));

            float distNorm = dist / lRad;
            float window = saturate(1.0f - distNorm * distNorm);
            float attenuation = window * window;

            bool isHandheld = (length(lPos - uVoxel.camPos.xyz) < 1.6f) || (length(lPos - uVoxel.playerPos.xyz) < 1.8f);
            
            float3 lColorBase = uVoxel.lights[li].colorAndIntensity.xyz;
            float fogMultiplier = 0.15f;
            
            if (abs(lColorBase.r - 1.0f) < 0.03f && abs(lColorBase.g - 0.65f) < 0.05f && abs(lColorBase.b - 0.22f) < 0.05f) {
                fogMultiplier = 0.08f;
            } else if (abs(lColorBase.r - 0.40f) < 0.05f && abs(lColorBase.g - 0.92f) < 0.05f && abs(lColorBase.b - 1.00f) < 0.05f) {
                fogMultiplier = 0.12f;
            } else if (abs(lColorBase.r - 1.0f) < 0.03f && abs(lColorBase.g - 0.70f) < 0.05f && abs(lColorBase.b - 0.24f) < 0.05f) {
                fogMultiplier = 0.06f; 
            }
            
            if (isHandheld) {
                fogMultiplier = 0.02f;
            }

            float shadowVis = 1.0f;
            float3 shadowTint = float3(1.0f);
            if (!isHandheld && dist > 0.30f) {
                ShadowRayResult ptSr = traceDdaShadowRay(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, samplePos, lPos, blockAtlasTex, smp, blockUvTable, bitmaskTable);
                shadowVis = ptSr.vis; shadowTint = ptSr.tint;
            }

            float3 phaseContrib = (lColor * shadowTint) * (phase * attenuation * scatteringAlbedo * fogMultiplier * shadowVis);
            float isotropicPhase = 1.0f / (4.0f * 3.14159265f);
            float3 spatialContrib = (lColor * shadowTint) * (isotropicPhase * attenuation * scatteringAlbedo * fogMultiplier * shadowVis * 0.18f);
            inScatterPts += phaseContrib + spatialContrib;
        }

        float transmittance = exp(-extinction * t);
        
        // Sun rays scale by stepSize natively, but local point lights must be capped so they don't blow out on large step sizes
        float ptStepWeight = min(stepSize, 2.5f);
        volumetricFog += inScatterSun * (extinction * transmittance * stepSize) + inScatterPts * (extinction * transmittance * ptStepWeight);
    }
    return volumetricFog * settings.volFogIntensity;
}
