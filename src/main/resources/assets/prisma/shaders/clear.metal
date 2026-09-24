#include <metal_stdlib>
using namespace metal;

struct ClearUniforms {
  float z;
  float3 _padding0;
  float4 color;
};

struct ClearVertexOut {
  float4 position [[position]];
  float4 color;
};

vertex ClearVertexOut metallum_clear_vs(
  uint vertexId [[vertex_id]],
  constant ClearUniforms& u [[buffer(1)]]
) {
  const float2 positions[3] = {
    float2(-1.0,  1.0),
    float2( 3.0,  1.0),
    float2(-1.0, -3.0)
  };

  ClearVertexOut out;
  out.position = float4(positions[vertexId], u.z, 1.0);
  out.color = u.color;
  return out;
}

fragment float4 metallum_clear_fs(ClearVertexOut in [[stage_in]]) {
  return in.color;
}
