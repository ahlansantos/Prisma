#include <metal_stdlib>
using namespace metal;


// Analytical Point Lights (deterministic, no noise)
static inline PointLightResult evaluatePointLights(
    float3 pWorld, float3 surfNormal, float3 viewDir, float roughness, float metallic,
    constant VoxelUniforms& uVoxel, constant RenderSettings& settings,
    device const uint2* voxelGrid, constant float4* blockUvTable, constant ulong* bitmaskTable,
    texture2d<float> blockAtlasTex, sampler smp, texture2d<float> playerSkinTex,
    bool isFirstPerson, uint2 gid
) {
    PointLightResult ptRes = PointLightResult{float3(0.0f), 0.0f, 0.0f};
    float3 pointLights = float3(0.0f);
    
    int lightCount = int(uVoxel.gridSize.w);
    if (lightCount <= 0) return ptRes;
    
    float3 rayOrigin = pWorld + surfNormal * 0.05f;
    bool ptShadowsEnabled = (uVoxel.camRight.w > 0.5f);
    int rayCount = ptShadowsEnabled ? int(settings.shadowRayCount) : 0;
    
    float ign = fract(52.9829189f * fract(dot(float2(gid), float2(0.06711056f, 0.00583715f))));
    float dAngle = ign * 6.2831853f;
    
    float maxDarkening = 0.0f;
            
    for (int li = 0; li < lightCount && li < 32; li++) {
        float3 lPos = uVoxel.lights[li].posAndRadius.xyz;
        float3 toL = lPos - pWorld;
        float distL = length(toL);
        float lRad = uVoxel.lights[li].posAndRadius.w;
        if (distL >= lRad || distL < 0.05f) continue;
        
        float3 L = toL / distL;
        float NdotL = saturate(dot(surfNormal, L));
        if (NdotL < 0.001f) continue;
        
        float atten = saturate(1.0f - (distL / lRad));
        float smoothAtten = atten * atten;
        float3 lColor = uVoxel.lights[li].colorAndIntensity.xyz * uVoxel.lights[li].colorAndIntensity.w;
        
        int numSamples = max(1, min(rayCount, 32));
        float radius = (rayCount > 0) ? 0.30f : 0.0f;
        
        float totalVis = 0.0f;
        float3 totalTint = float3(0.0f);
        float3 up = abs(L.y) < 0.99f ? float3(0, 1, 0) : float3(1, 0, 0);
        float3 tangent = normalize(cross(up, L));
        float3 bitangent = cross(L, tangent);
        
        for (int si = 0; si < numSamples; si++) {
            if (ptShadowsEnabled) {
                float rRadius = sqrt((float(si) + 0.5f) / float(numSamples)) * radius;
                float theta = float(si) * 2.399963f + dAngle;
                float2 disk = float2(cos(theta), sin(theta)) * rRadius;
                float3 offset = (tangent * disk.x + bitangent * disk.y);
                
                float3 jitteredLPos = lPos + offset;
                float3 jitteredL = jitteredLPos - pWorld;
                float jitteredDist = length(jitteredL);
                float3 targetPos = rayOrigin + (jitteredL / jitteredDist) * jitteredDist;
                
                ShadowRayResult sr = traceDdaShadowRay(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, rayOrigin, targetPos, blockAtlasTex, smp, blockUvTable, bitmaskTable);
                
                bool isHandheld = (length(lPos - uVoxel.camPos.xyz) < 1.6f) || (length(lPos - uVoxel.playerPos.xyz) < 1.8f);
                bool canCastPtPlayerShadow = (!isHandheld && uVoxel.shadowParams.w > 0.5f && length(rayOrigin.xz - uVoxel.playerPos.xz) < 12.0f);
                if (isFirstPerson) {
                    canCastPtPlayerShadow = canCastPtPlayerShadow && (rayOrigin.y <= uVoxel.playerPos.y + 1.4f);
                }
                
                if (canCastPtPlayerShadow && sr.vis > 0.0f) {
                    float3 ptL = normalize(targetPos - rayOrigin);
                    PlayerHit hit; hit.hitDist = 1e6f;
                    tracePlayerOBB(rayOrigin, ptL, uVoxel.playerPos.xyz, uVoxel.shadowParams.x, uVoxel.playerHead.x, uVoxel.playerHead.y, uVoxel.playerAnim.x, uVoxel.playerAnim.y, uVoxel.playerAnim.z, uVoxel.playerAnim.w, playerSkinTex, smp, hit);
                    if (hit.hitDist > 0.0f && hit.hitDist < jitteredDist) {
                        sr.vis = 0.0f;
                    }
                }
                totalVis += sr.vis;
                totalTint += sr.tint;
            } else {
                totalVis += 1.0f;
                totalTint += float3(1.0f);
            }
        }
        
        float visibility = totalVis / float(numSamples);
        float3 tintCol = totalTint / float(numSamples);
        pointLights += lColor * tintCol * NdotL * smoothAtten * visibility;
        
        float currentDarkening = (1.0f - visibility) * NdotL * atten * saturate(uVoxel.lights[li].colorAndIntensity.w * 0.5f);
        maxDarkening = max(maxDarkening, currentDarkening);
    }
    ptRes.color = pointLights;
    ptRes.shadowDarkening = maxDarkening;
    return ptRes;
}
