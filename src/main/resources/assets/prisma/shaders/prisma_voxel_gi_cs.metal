#include <metal_stdlib>
using namespace metal;

#include "prisma_api.metal"
#include "voxel_common.metal"
#include "lib_lighting.metal"

kernel void prisma_voxel_gi_cs(
    uint2 gid [[thread_position_in_grid]],
    texture2d<float, access::write> giTexture [[texture(0)]],
    texture2d<float, access::write> vxgiTexture [[texture(7)]],
    texture2d<float> normalTex [[texture(1)]],
    depth2d<float> worldDepthTex [[texture(2)]],
    texture2d<float> blockAtlasTex [[texture(5)]],
    texture2d<float> playerSkinTex [[texture(6)]],
    sampler smp [[sampler(0)]],
    constant CameraData& camera [[buffer(10)]],
    constant EnvironmentData& env [[buffer(11)]],
    constant RenderSettings& settings [[buffer(12)]],
    constant VoxelUniforms& uVoxel [[buffer(2)]],
    device const uint2* voxelGrid [[buffer(1)]],
    constant float4* blockUvTable [[buffer(3)]],
    constant ulong* bitmaskTable [[buffer(4)]]
) {
    if (gid.x >= giTexture.get_width() || gid.y >= giTexture.get_height()) return;

    // Depth/normal textures may differ in size from the GI target (e.g. upscaling), so map by UV.
    float2 depthSize = float2(worldDepthTex.get_width(), worldDepthTex.get_height());
    float2 depthTexel = 1.0f / depthSize;
    uint2 maxD = uint2(worldDepthTex.get_width() - 1, worldDepthTex.get_height() - 1);

    float2 uv = (float2(gid) + 0.5f) / float2(giTexture.get_width(), giTexture.get_height());
    uint2 depthGid = min(uint2(uv * depthSize), maxD);
    float depth = worldDepthTex.read(depthGid);
    // Reverse-Z: sky/cleared depth is ~0.0
    if (depth <= 0.00005f) {
        giTexture.write(float4(0.0f), gid);
        vxgiTexture.write(float4(0.0f), gid);
        return;
    }

    float2 depthUv = (float2(depthGid) + 0.5f) * depthTexel;
    float3 camPos = uVoxel.camPos.xyz;
    float3 pWorld = reconstructWorldPos(depthUv, depth, camPos, uVoxel.invViewProj);
    float3 viewDir = normalize(pWorld - camPos);

    // Derive the geometric normal from depth derivatives, exactly like deferred.metal
    // (the MRT normal texture is not a reliable world-space normal).
    float depthL = worldDepthTex.read(uint2(max(int(depthGid.x) - 1, 0), depthGid.y));
    float depthR = worldDepthTex.read(uint2(min(depthGid.x + 1, maxD.x), depthGid.y));
    float depthU = worldDepthTex.read(uint2(depthGid.x, max(int(depthGid.y) - 1, 0)));
    float depthD = worldDepthTex.read(uint2(depthGid.x, min(depthGid.y + 1, maxD.y)));
    float diffL = (depthL > 0.00005f) ? abs(depth - depthL) : 1e6f;
    float diffR = (depthR > 0.00005f) ? abs(depth - depthR) : 1e6f;
    float diffU = (depthU > 0.00005f) ? abs(depth - depthU) : 1e6f;
    float diffD = (depthD > 0.00005f) ? abs(depth - depthD) : 1e6f;
    float3 pL = reconstructWorldPos(depthUv - float2(depthTexel.x, 0.0f), depthL, camPos, uVoxel.invViewProj);
    float3 pR = reconstructWorldPos(depthUv + float2(depthTexel.x, 0.0f), depthR, camPos, uVoxel.invViewProj);
    float3 pU = reconstructWorldPos(depthUv - float2(0.0f, depthTexel.y), depthU, camPos, uVoxel.invViewProj);
    float3 pD = reconstructWorldPos(depthUv + float2(0.0f, depthTexel.y), depthD, camPos, uVoxel.invViewProj);
    float3 dX = (diffL < diffR) ? (pWorld - pL) : (pR - pWorld);
    float3 dY = (diffU < diffD) ? (pWorld - pU) : (pD - pWorld);
    float3 crossDir = cross(dY, dX);
    float crossLen = dot(crossDir, crossDir);

    float distCam = length(pWorld - camPos);
    float texelFootprint = max(0.06f, distCam * depthTexel.y * 2.2f);
    float derivThreshold = max(0.40f, texelFootprint * texelFootprint * 1.6f);
    bool badDerivative = (dot(dX, dX) > derivThreshold) && (dot(dY, dY) > derivThreshold);

    float3 av = abs(viewDir);
    float3 fallbackN = (av.y >= av.x && av.y >= av.z) ? float3(0.0f, -sign(viewDir.y), 0.0f)
                     : (av.x >= av.z ? float3(-sign(viewDir.x), 0.0f, 0.0f) : float3(0.0f, 0.0f, -sign(viewDir.z)));
    float3 geomNormal = (crossLen > 1e-20f && !badDerivative) ? normalize(crossDir) : fallbackN;
    if (dot(geomNormal, pWorld - camPos) > 0.0f) geomNormal = -geomNormal;

    float3 absN = abs(geomNormal);
    float3 nWorld;
    if (absN.y >= absN.x && absN.y >= absN.z) nWorld = float3(0.0f, sign(geomNormal.y), 0.0f);
    else if (absN.x >= absN.z) nWorld = float3(sign(geomNormal.x), 0.0f, 0.0f);
    else nWorld = float3(0.0f, 0.0f, sign(geomNormal.z));

    int3 gsz = uVoxel.gridSize.xyz;
    int3 vCur = clamp(int3(floor(pWorld)) - uVoxel.gridOrigin.xyz, int3(0), gsz - int3(1));
    int3 vIn = clamp(int3(floor(pWorld - nWorld * 0.15f)) - uVoxel.gridOrigin.xyz, int3(0), gsz - int3(1));
    uint2 voxCurr = readVoxelLocal(voxelGrid, gsz, vCur);
    uint2 voxIn = readVoxelLocal(voxelGrid, gsz, vIn);
    bool onBlock = ((voxCurr.x & 7) != 0) || ((voxIn.x & 7) != 0);

    float3 surfNormal = onBlock ? normalize(mix(geomNormal, nWorld, 0.70f)) : geomNormal;

    bool isFirstPerson = (length(uVoxel.camPos.xyz - (uVoxel.playerPos.xyz + float3(0.0f, 1.5f, 0.0f))) < 0.60f);

    PointLightResult ptRes = evaluatePointLights(
        pWorld, surfNormal, viewDir, 0.0f, 0.0f,
        uVoxel, settings, voxelGrid, blockUvTable, bitmaskTable,
        blockAtlasTex, smp, playerSkinTex, isFirstPerson, gid
    );

    // Encode GI and shadowing into the RGBA texture
    // RGB = Point Light Diffuse, A = Shadow Darkening
    float3 ptSafe = (any(isnan(ptRes.color)) || any(isinf(ptRes.color))) ? float3(0.0f) : ptRes.color;
    giTexture.write(float4(ptSafe, ptRes.shadowDarkening), gid);

    // ---- True Voxel GI (1 spp diffuse bounce), denoised in the next pass ----
    float4 vxgiOut = float4(0.0f);
    float packedAoStrength = uVoxel.camPos.w;
    if (packedAoStrength >= 5.0f) {
        float3 gridMin = float3(uVoxel.gridOrigin.xyz);
        float3 gridMax = gridMin + float3(uVoxel.gridSize.xyz);
        float distToGridEdge = min(
            min(pWorld.x - gridMin.x, gridMax.x - pWorld.x),
            min(min(pWorld.y - gridMin.y, gridMax.y - pWorld.y),
                min(pWorld.z - gridMin.z, gridMax.z - pWorld.z)));
        float gridWeight = saturate((distToGridEdge + 1.5f) / 3.0f);
        float giDistFade = 1.0f - smoothstep(40.0f, 56.0f, length(pWorld - camPos));
        gridWeight *= giDistFade;

        if (gridWeight > 0.05f) {
            float sunTheta = env.sunAngle;
            float3 sunDir = normalize(float3(-sin(sunTheta), cos(sunTheta), 0.0f));
            float3 moonDir = -sunDir;
            float sunElevation = sunDir.y;
            float sunWeight = smoothstep(-0.08f, 0.04f, sunElevation);
            float sunsetFactor = (1.0f - smoothstep(0.02f, 0.32f, abs(sunElevation))) * smoothstep(-0.06f, 0.08f, sunElevation);
            float clampedSunrise = max(0.0f, env.sunriseAlpha);
            float3 noonSunColor = float3(1.05f, 0.98f, 0.88f);
            float goldenBoost = smoothstep(0.30f, 0.0f, abs(sunElevation));
            float3 goldenHourColor = mix(float3(1.55f, 0.72f, 0.22f), float3(1.70f, 0.45f, 0.12f), goldenBoost * 0.6f);
            float3 currentSunColor = mix(noonSunColor, goldenHourColor, max(sunsetFactor, clampedSunrise)) * (1.0f - env.rainStrength * 0.98f);
            float3 currentMoonColor = float3(0.22f, 0.32f, 0.52f) * (1.0f - env.rainStrength * 0.95f);
            float3 celestialDir = sunWeight > 0.5f ? sunDir : moonDir;
            float3 sunriseTint = float3(env.sunriseR, env.sunriseG, env.sunriseB);
            float3 daySkyLight = mix(float3(0.92f, 0.92f, 0.90f), sunriseTint * 1.10f, clampedSunrise);
            float3 nightSkyLight = float3(0.06f, 0.11f, 0.28f);
            float3 activeSkyLight = mix(nightSkyLight, daySkyLight, sunWeight) * (1.0f - env.rainStrength * 0.65f);

            // 1 SPP quasi-Monte Carlo sampling across the hemisphere with temporal reprojection:
            // High FPS indirect diffuse bounces without dropping 30 FPS!
            uint frameCount = uint(uVoxel.mobCounts.y);
            float3 tX = cross(surfNormal, float3(0.0f, 1.0f, 0.0f));
            if (dot(tX, tX) < 0.01f) tX = cross(surfNormal, float3(1.0f, 0.0f, 0.0f));
            tX = normalize(tX);
            float3 tY = normalize(cross(surfNormal, tX));
            float giIgn = fract(52.9829189f * fract(dot(float2(gid), float2(0.06711056f, 0.00583715f))));

            float3 giColor = float3(0.0f);

            float r1 = fract(giIgn + float(frameCount) * 0.6180339887f);
            float r2 = fract(giIgn * 1.3247179572f + float(frameCount) * 0.3819660113f);
            float phi = 6.2831853f * r1;
            float cosTheta = sqrt(max(0.04f, r2));
            float sinTheta = sqrt(max(0.0f, 1.0f - cosTheta * cosTheta));
            
            float3 giDir = normalize(tX * cos(phi) * sinTheta + tY * sin(phi) * sinTheta + surfNormal * cosTheta);
            float3 jitterOrigin = pWorld + surfNormal * 0.15f;
            VoxelReflResult giRes = traceVoxelReflections(voxelGrid, uVoxel.gridOrigin, uVoxel.gridSize, jitterOrigin, giDir, activeSkyLight, currentSunColor, currentMoonColor, celestialDir, sunWeight, blockAtlasTex, playerSkinTex, smp, blockUvTable, bitmaskTable, uVoxel.gridSize.w, uVoxel.lights, 8, min(int(settings.maxPointLights), 4), 0.0f, 0.0f, 0.0f, 1.0f, env.rainStrength, camera.gameTime, uVoxel, 0.0f);
            if (giRes.alpha > 0.01f && giRes.hitDist < 10.0f) {
                float d = giRes.hitDist;
                // Natural physically-based falloff without sharp spherical boundaries
                float falloff = 1.0f / (1.0f + d * 0.40f + d * d * 0.10f);
                float window = saturate(1.0f - d / 10.0f);
                float smoothWin = window * window * (3.0f - 2.0f * window);
                // Cosine-weighted sampling already cancels the cosine term: no extra cosTheta (halves variance).
                giColor = giRes.color * (falloff * smoothWin * 1.07f);
                if (any(isnan(giColor)) || any(isinf(giColor))) giColor = float3(0.0f);
                giColor = min(giColor, float3(6.0f));
            }
            vxgiOut = float4(giColor, 1.0f);
        }
    }
    vxgiTexture.write(vxgiOut, gid);

}
