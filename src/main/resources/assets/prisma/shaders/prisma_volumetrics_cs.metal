#include <metal_stdlib>
using namespace metal;

#include "prisma_api.metal"
#include "voxel_common.metal"
#include "lib_volumetrics.metal"

kernel void prisma_volumetrics_cs(
    uint2 gid [[thread_position_in_grid]],
    texture2d<float, access::write> volumetricsTexture [[texture(0)]],
    depth2d<float> worldDepthTex [[texture(2)]],
    constant CameraData& camera [[buffer(10)]],
    constant EnvironmentData& env [[buffer(11)]],
    constant RenderSettings& settings [[buffer(12)]],
    constant VoxelUniforms& uVoxel [[buffer(2)]],
    device const uint2* voxelGrid [[buffer(3)]]
) {
    if (gid.x >= volumetricsTexture.get_width() || gid.y >= volumetricsTexture.get_height()) return;

    float depth = worldDepthTex.read(gid);
    // Even if depth is 1.0 (sky), we still compute volumetrics (fog against the sky)
    
    float2 uv = float2(gid) / float2(volumetricsTexture.get_width(), volumetricsTexture.get_height());
    float2 clipSpace = uv * 2.0f - 1.0f;
    clipSpace.y = -clipSpace.y;
    
    float4 worldPosH = uVoxel.invViewProj * float4(clipSpace, depth, 1.0f);
    float3 pWorld = worldPosH.xyz / worldPosH.w;
    float3 viewDir = normalize(pWorld - uVoxel.camPos.xyz);
    
    float timeOfDay = camera.gameTime;
    float sunAngle = env.sunAngle;
    float3 sunDir = normalize(float3(-sin(sunAngle), cos(sunAngle), 0.0f));
    float3 moonDir = -sunDir;
    
    float dayCycle = fract(timeOfDay + 0.25f);
    float sunWeight = smoothstep(0.42f, 0.58f, 1.0f - abs(dayCycle - 0.5f) * 2.0f);
    float3 celestialDir = sunWeight > 0.5f ? sunDir : moonDir;

    // Approximated celestial color (we can refine this later to match deferred exactly)
    float sunriseFactor = smoothstep(0.40f, 0.50f, dayCycle) - smoothstep(0.50f, 0.60f, dayCycle);
    float sunsetFactor  = smoothstep(0.90f, 1.00f, dayCycle) - smoothstep(0.00f, 0.10f, dayCycle);
    float clampedSunrise = max(sunriseFactor, sunsetFactor);
    float3 noonSunColor   = float3(1.05f, 0.98f, 0.88f);
    float3 goldenHourColor= float3(1.40f, 0.65f, 0.15f);
    float3 currentSunColor = mix(noonSunColor, goldenHourColor, max(sunsetFactor, clampedSunrise)) * (1.0f - env.rainStrength * 0.98f);
    float3 nightMoonColor = float3(0.06f, 0.10f, 0.18f);
    float3 redMoonColor   = float3(0.18f, 0.05f, 0.05f);
    float moonAltitude    = max(0.0f, moonDir.y);
    float3 currentMoonColor = mix(redMoonColor, nightMoonColor, smoothstep(0.0f, 0.2f, moonAltitude)) * (1.0f - env.rainStrength * 0.85f);
    float3 celestialDirectCol = (sunWeight > 0.5f) ? (currentSunColor * 0.78f * (1.0f + sunsetFactor * 0.50f)) : (currentMoonColor * 0.70f);

    float3 volumetricFog = evaluateVolumetricFog(
        uVoxel.camPos.xyz, viewDir, pWorld, celestialDir, celestialDirectCol, sunWeight,
        uVoxel, settings, voxelGrid
    );

    volumetricsTexture.write(float4(volumetricFog, 1.0f), gid);
}
