#include <metal_stdlib>
using namespace metal;

struct SpaceWarpVertexOut {
  float4 position [[position]];
  float2 uv;
};

vertex SpaceWarpVertexOut prisma_spacewarp_vs(uint vertexId [[vertex_id]]) {
  const float2 positions[3] = { float2(-1.0, 1.0), float2(3.0, 1.0), float2(-1.0, -3.0) };
  const float2 uvs[3] = { float2(0.0, 0.0), float2(2.0, 0.0), float2(0.0, 2.0) };
  SpaceWarpVertexOut out;
  out.position = float4(positions[vertexId], 0.0, 1.0);
  out.uv = uvs[vertexId];
  return out;
}

struct SpaceWarpUniforms {
  float4x4 viewProj;
  float4x4 prevViewProj;
  float4x4 invViewProj;
  float4 camPos;
  float4 prevCamPos;
};


fragment float4 prisma_spacewarp_fs(
  SpaceWarpVertexOut in [[stage_in]],
  texture2d<float> prevHdrTex [[texture(0)]],
  depth2d<float> worldDepthTex [[texture(1)]],
  sampler smp [[sampler(0)]],
  constant SpaceWarpUniforms& u [[buffer(0)]]
) {
  uint2 depthGid = uint2(in.uv * float2(worldDepthTex.get_width(), worldDepthTex.get_height()));
  float depth = worldDepthTex.read(depthGid);
  
  float2 ndc = float2(in.uv.x * 2.0f - 1.0f, in.uv.y * 2.0f - 1.0f);
  float4 clipPos = float4(ndc, depth, 1.0f);
  float4 worldRel = u.invViewProj * clipPos;
  worldRel /= max(worldRel.w, 0.00001f);
  
  float3 globalPos = worldRel.xyz + u.camPos.xyz;
  float3 prevRelPos = globalPos - u.prevCamPos.xyz;
  
  float4 prevClip = u.prevViewProj * float4(prevRelPos, 1.0f);
  prevClip /= max(prevClip.w, 0.00001f);
  float2 prevUv = float2(prevClip.x * 0.5f + 0.5f, prevClip.y * 0.5f + 0.5f);
  
  if (prevUv.x < 0.0f || prevUv.x > 1.0f || prevUv.y < 0.0f || prevUv.y > 1.0f) {
      return float4(0.0f);
  }
  return prevHdrTex.sample(smp, prevUv);
}
