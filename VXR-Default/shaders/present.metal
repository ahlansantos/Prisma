#include <metal_stdlib>
using namespace metal;

struct PresentVertexOut {
  float4 position [[position]];
  float2 uv;
};

vertex PresentVertexOut prisma_present_vs(uint vertexId [[vertex_id]]) {
  const float2 positions[3] = {
    float2(-1.0,  1.0),
    float2( 3.0,  1.0),
    float2(-1.0, -3.0)
  };

  const float2 uvs[3] = {
    float2(0.0,  1.0),
    float2(2.0,  1.0),
    float2(0.0, -1.0)
  };

  PresentVertexOut out;
  out.position = float4(positions[vertexId], 0.0, 1.0);
  out.uv = uvs[vertexId];
  return out;
}

fragment float4 prisma_present_fs(
  PresentVertexOut in [[stage_in]],
  texture2d<float> colorTex [[texture(0)]],
  sampler smp [[sampler(0)]]
) {
  return float4(colorTex.sample(smp, in.uv).rgb, 1.0f);
}
