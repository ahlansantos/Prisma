#include <metal_stdlib>
using namespace metal;

#include "prisma_api.metal"
#include "voxel_common.metal"
#include "lib_volumetrics.metal"

kernel void prisma_volumetrics_cs(
    uint2 gid [[thread_position_in_grid]],
    texture2d<float, access::write> volumetricsTexture [[texture(0)]],
    depth2d<float> worldDepthTex [[texture(2)]],
    texture2d<float> blockAtlasTex [[texture(5)]],
    sampler smp [[sampler(0)]],
    constant CameraData& camera [[buffer(10)]],
    constant EnvironmentData& env [[buffer(11)]],
    constant RenderSettings& settings [[buffer(12)]],
    constant VoxelUniforms& uVoxel [[buffer(2)]],
    device const uint2* voxelGrid [[buffer(1)]],
    constant float4* blockUvTable [[buffer(3)]],
    constant ulong* bitmaskTable [[buffer(4)]]
) {
    if (gid.x >= volumetricsTexture.get_width() || gid.y >= volumetricsTexture.get_height()) return;

    float2 uv = (float2(gid) + 0.5f) / float2(volumetricsTexture.get_width(), volumetricsTexture.get_height());
    uint2 depthGid = min(uint2(uv * float2(worldDepthTex.get_width(), worldDepthTex.get_height())), uint2(worldDepthTex.get_width() - 1, worldDepthTex.get_height() - 1));
    float depth = worldDepthTex.read(depthGid);
    float2 clipSpace = uv * 2.0f - 1.0f;
    float4 worldPosH = uVoxel.invViewProj * float4(clipSpace, depth, 1.0f);
    float3 pWorld = uVoxel.camPos.xyz + (worldPosH.xyz / max(worldPosH.w, 0.00001f));
    float3 viewDir = normalize(pWorld - uVoxel.camPos.xyz);
    
    // Identical celestial setup to deferred.metal (driven by env.sunAngle, NOT by gameTime seconds).
    float sunTheta = env.sunAngle;
    float3 sunDir = normalize(float3(-sin(sunTheta), cos(sunTheta), 0.0f));
    float3 moonDir = -sunDir;
    float sunElevation = sunDir.y;
    float sunWeight = smoothstep(-0.08f, 0.04f, sunElevation);
    float3 celestialDir = sunWeight > 0.5f ? sunDir : moonDir;
    float sunsetFactor = (1.0f - smoothstep(0.02f, 0.32f, abs(sunElevation))) * smoothstep(-0.06f, 0.08f, sunElevation);
    float clampedSunrise = max(0.0f, env.sunriseAlpha);
    float3 noonSunColor = float3(1.05f, 0.98f, 0.88f);
    float goldenBoost = smoothstep(0.30f, 0.0f, abs(sunElevation));
    float3 goldenHourColor = mix(float3(1.55f, 0.72f, 0.22f), float3(1.70f, 0.45f, 0.12f), goldenBoost * 0.6f);
    float3 currentSunColor = mix(noonSunColor, goldenHourColor, max(sunsetFactor, clampedSunrise)) * (1.0f - env.rainStrength * 0.98f);
    float3 currentMoonColor = float3(0.22f, 0.32f, 0.52f) * (1.0f - env.rainStrength * 0.95f);
    float3 sunRayColor = currentSunColor * float3(1.04f, 0.95f, 0.82f);
    float goldenFogBoost = 1.0f + sunsetFactor * 1.20f;
    float3 celestialDirectCol = (sunWeight > 0.5f) ? (sunRayColor * 1.15f * goldenFogBoost) : (currentMoonColor * 0.45f);

    float3 volumetricFog = evaluateVolumetricFog(
        uVoxel.camPos.xyz, viewDir, pWorld, celestialDir, celestialDirectCol, sunWeight,
        uVoxel, settings, voxelGrid, gid, blockAtlasTex, smp, blockUvTable, bitmaskTable
    );

    if (any(isnan(volumetricFog)) || any(isinf(volumetricFog))) volumetricFog = float3(0.0f);
    volumetricsTexture.write(float4(volumetricFog, 1.0f), gid);
}
