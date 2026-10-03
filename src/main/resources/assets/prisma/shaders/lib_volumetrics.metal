#include <metal_stdlib>
using namespace metal;

static inline float3 evaluateVolumetricFog(
    float3 ro, float3 rd, float3 pWorld, float3 celestialDir, float3 celestialCol, float sunWeight,
    constant VoxelUniforms& uVoxel, constant RenderSettings& settings,
    device const uint2* voxelGrid
) {
    if (settings.volFogEnabled < 0.5f) return float3(0.0f);
    
    float3 volumetricFog = float3(0.0f);
    int numSteps = int(settings.volFogSamples);
    if (numSteps <= 0) return float3(0.0f);
    
    float marchDist = min(length(pWorld - ro), 48.0f);
    if (marchDist < 0.5f) return float3(0.0f);
    
    float stepSize = marchDist / float(numSteps);
    float tStart = stepSize * 0.5f;
    float scatteringAlbedo = 0.85f;
    float extinction = 0.08f;
    
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

        float3 inScatter = float3(0.0f);

        // --- 1. Volumetric Sun / Moon Rays (God Rays) ---
        if (celestialDir.y > 0.02f) {
            float3 sunRayTarget = samplePos + celestialDir * 32.0f;
            float3 sunTint = float3(1.0f);
            float sunVis = traceVoxelShadowFast(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, samplePos, sunRayTarget, 16, sunTint);
            if (sunVis > 0.01f) {
                inScatter += (celestialCol * sunTint) * (phaseSun * scatteringAlbedo * sunVis * 0.75f);
            }
        }

        // --- 2. Volumetric Point Lights (3D Spatial Light Volumes) ---
        for (int li = 0; li < lightCount && li < 32; li++) {
            float3 lPos = uVoxel.lights[li].posAndRadius.xyz;
            float lRad = uVoxel.lights[li].posAndRadius.w;

            float3 toLight = lPos - samplePos;
            float dist = length(toLight);
            if (dist >= lRad) continue;

            float3 lColor = uVoxel.lights[li].colorAndIntensity.xyz * uVoxel.lights[li].colorAndIntensity.w;

            float cosTheta = dot(rd, toLight / dist);
            float g = 0.3f;
            float g2 = g * g;
            float phase = (1.0f - g2) / (4.0f * 3.14159265f * pow(1.0f + g2 - 2.0f * g * cosTheta, 1.5f));

            float distNorm = dist / lRad;
            float window = saturate(1.0f - distNorm * distNorm);
            float attenuation = window * window;

            bool isHandheld = (length(lPos - uVoxel.camPos.xyz) < 1.6f) || (length(lPos - uVoxel.playerPos.xyz) < 1.8f);
            
            float3 lColorBase = uVoxel.lights[li].colorAndIntensity.xyz;
            float fogMultiplier = 0.50f;
            
            if (abs(lColorBase.r - 1.0f) < 0.03f && abs(lColorBase.g - 0.65f) < 0.05f && abs(lColorBase.b - 0.22f) < 0.05f) {
                fogMultiplier = 0.18f;
            } else if (abs(lColorBase.r - 0.40f) < 0.05f && abs(lColorBase.g - 0.92f) < 0.05f && abs(lColorBase.b - 1.00f) < 0.05f) {
                fogMultiplier = 0.35f;
            } else if (abs(lColorBase.r - 1.0f) < 0.03f && abs(lColorBase.g - 0.70f) < 0.05f && abs(lColorBase.b - 0.24f) < 0.05f) {
                fogMultiplier = 0.30f;
            }
            
            if (isHandheld) {
                fogMultiplier = 0.03f;
            }

            float shadowVis = 1.0f;
            float3 shadowTint = float3(1.0f);
            if (!isHandheld && dist > 0.30f) {
                float3 toSample = normalize(samplePos - lPos);
                float3 shadowTarget = lPos + toSample * min(0.35f, dist * 0.20f);
                shadowVis = traceVoxelShadowFast(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, samplePos, shadowTarget, 16, shadowTint);
            }

            float3 phaseContrib = (lColor * shadowTint) * (phase * attenuation * scatteringAlbedo * fogMultiplier * shadowVis);
            float isotropicPhase = 1.0f / (4.0f * 3.14159265f);
            float3 spatialContrib = (lColor * shadowTint) * (isotropicPhase * attenuation * scatteringAlbedo * fogMultiplier * shadowVis * 0.18f);
            inScatter += phaseContrib + spatialContrib;
        }

        float transmittance = exp(-extinction * t);
        volumetricFog += inScatter * (extinction * transmittance * stepSize);
    }
    return volumetricFog * settings.volFogIntensity;
}
