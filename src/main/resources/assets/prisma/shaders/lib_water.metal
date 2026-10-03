#include <metal_stdlib>
using namespace metal;

static inline float3 computeEclipseWaterWaves(float2 pWorldXZ, float time, float strength, float speed) {
    if (strength <= 0.001f) return float3(0.0f, 1.0f, 0.0f);

    const float4 waves[5] = {
      float4( 1.00f,  0.20f, 0.45f, 0.28f),
      float4( 0.85f, -0.52f, 0.80f, 0.18f),
      float4( 0.40f,  0.92f, 1.30f, 0.09f),
      float4(-0.70f,  0.72f, 1.90f, 0.04f),
      float4( 0.10f,  1.00f, 2.60f, 0.02f),
    };

    float2 dX = float2(0.0f);
    float wTime = time * 0.65f * speed;

    for (int i = 0; i < 5; i++) {
      float2 dir   = normalize(waves[i].xy);
      float  freq  = waves[i].z;
      float  amp   = waves[i].w * strength * 0.15f;
      float  phase = wTime * (0.9f + float(i) * 0.12f);
      float  x     = dot(dir, pWorldXZ) * freq + phase;
      
      float  wave  = exp(sin(x) - 1.0f);
      float  deriv = wave * cos(x) * freq * amp * 2.2f;
      dX += dir * deriv;
    }

    return normalize(float3(-dX.x, 1.0f, -dX.y));
}
