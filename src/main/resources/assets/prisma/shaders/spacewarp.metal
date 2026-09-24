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


// Fast Catmull-Rom bicubic interpolation
static inline float4 sampleBicubic(texture2d<float> tex, sampler smp, float2 uv) {
    float2 texSize = float2(tex.get_width(), tex.get_height());
    float2 invTexSize = 1.0f / texSize;
    
    uv = uv * texSize - 0.5f;
    float2 fxy = fract(uv);
    uv -= fxy;

    float2 p0 = (3.0f - 2.0f * fxy) * fxy * fxy;
    float2 p1 = (3.0f - 2.0f * (1.0f - fxy)) * (1.0f - fxy) * (1.0f - fxy);
    float2 w0 = (1.0f - fxy) * (1.0f - fxy) * (1.0f - fxy);
    float2 w1 = p1 + 3.0f * (1.0f - fxy) * (1.0f - fxy) * fxy;
    float2 w2 = p0 + 3.0f * fxy * fxy * (1.0f - fxy);
    float2 w3 = fxy * fxy * fxy;

    float2 weight12 = w1 + w2;
    float2 offset12 = w2 / (weight12 + 0.0001f);

    float2 tc0 = (uv - 1.0f) * invTexSize;
    float2 tc3 = (uv + 2.0f) * invTexSize;
    float2 tc12 = (uv + offset12) * invTexSize;

    float4 c00 = tex.sample(smp, float2(tc12.x, tc0.y));
    float4 c01 = tex.sample(smp, float2(tc0.x, tc12.y));
    float4 c11 = tex.sample(smp, float2(tc12.x, tc12.y));
    float4 c12 = tex.sample(smp, float2(tc3.x, tc12.y));
    float4 c22 = tex.sample(smp, float2(tc12.x, tc3.y));

    float4 color = c00 * w0.y * weight12.x +
                   c01 * w0.x * weight12.y +
                   c11 * weight12.x * weight12.y +
                   c12 * w3.x * weight12.y +
                   c22 * w3.y * weight12.x;

    return color / (w0.y * weight12.x + w0.x * weight12.y + weight12.x * weight12.y + w3.x * weight12.y + w3.y * weight12.x);
}

fragment float4 prisma_spacewarp_fs(
  SpaceWarpVertexOut in [[stage_in]],
  texture2d<float> prevHdrTex [[texture(0)]],
  depth2d<float> worldDepthTex [[texture(1)]],
  sampler smp [[sampler(0)]],
  constant SpaceWarpUniforms& u [[buffer(0)]]
) {
  float depth = worldDepthTex.sample(smp, in.uv);
  
  float2 ndc = float2(in.uv.x * 2.0f - 1.0f, in.uv.y * 2.0f - 1.0f);
  float4 clipPos = float4(ndc, depth, 1.0f);
  float4 worldRel = u.invViewProj * clipPos;
  worldRel /= max(worldRel.w, 0.00001f);
  
  float3 globalPos = worldRel.xyz + u.camPos.xyz;
  float3 prevRelPos = globalPos - u.prevCamPos.xyz;
  
  float4 prevClip = u.prevViewProj * float4(prevRelPos, 1.0f);
  prevClip /= max(prevClip.w, 0.00001f);
  float2 prevUv = prevClip.xy * 0.5f + 0.5f;
  
  if (prevUv.x < 0.0f || prevUv.x > 1.0f || prevUv.y < 0.0f || prevUv.y > 1.0f) {
      return float4(0.0f);
  }
  return sampleBicubic(prevHdrTex, smp, prevUv);
}
