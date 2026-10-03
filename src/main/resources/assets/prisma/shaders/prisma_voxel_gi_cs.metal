#include <metal_stdlib>
using namespace metal;

#include "prisma_api.metal"
#include "voxel_common.metal"
#include "lib_lighting.metal"

kernel void prisma_voxel_gi_cs(
    uint2 gid [[thread_position_in_grid]],
    texture2d<float, access::write> giTexture [[texture(0)]],
    texture2d<float> normalTex [[texture(1)]],
    depth2d<float> worldDepthTex [[texture(2)]],
    texture2d<float> blockAtlasTex [[texture(5)]],
    texture2d<float> playerSkinTex [[texture(6)]],
    sampler smp [[sampler(0)]],
    constant CameraData& camera [[buffer(10)]],
    constant EnvironmentData& env [[buffer(11)]],
    constant RenderSettings& settings [[buffer(12)]],
    constant VoxelUniforms& uVoxel [[buffer(2)]],
    device const uint2* voxelGrid [[buffer(3)]],
    constant float4* blockUvTable [[buffer(4)]],
    constant ulong* bitmaskTable [[buffer(5)]]
) {
    if (gid.x >= giTexture.get_width() || gid.y >= giTexture.get_height()) return;

    float depth = worldDepthTex.read(gid);
    if (depth >= 1.0f) {
        giTexture.write(float4(0.0f), gid);
        return;
    }

    float2 uv = float2(gid) / float2(giTexture.get_width(), giTexture.get_height());
    float2 clipSpace = uv * 2.0f - 1.0f;
    clipSpace.y = -clipSpace.y;
    float4 viewPosH = uVoxel.invProj * float4(clipSpace, depth, 1.0f);
    float3 viewPos = viewPosH.xyz / viewPosH.w;
    float4 worldPosH = uVoxel.invView * float4(viewPos, 1.0f);
    float3 pWorld = worldPosH.xyz;
    float3 surfNormal = normalTex.read(gid).xyz * 2.0f - 1.0f;
    float3 viewDir = normalize(pWorld - uVoxel.camPos.xyz);
    
    bool isFirstPerson = (length(uVoxel.camPos.xyz - (uVoxel.playerPos.xyz + float3(0.0f, 1.5f, 0.0f))) < 0.60f);

    PointLightResult ptRes = evaluatePointLights(
        pWorld, surfNormal, viewDir, 0.0f, 0.0f,
        uVoxel, settings, voxelGrid, blockUvTable, bitmaskTable,
        blockAtlasTex, smp, playerSkinTex, isFirstPerson, gid
    );

    // Encode GI and shadowing into the RGBA texture
    // RGB = Point Light Diffuse, A = Shadow Darkening
    giTexture.write(float4(ptRes.color, ptRes.shadowDarkening), gid);
}
