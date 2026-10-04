#include <metal_stdlib>
using namespace metal;

static inline float3 computeEclipseWaterWaves(float2 pWorldXZ, float time, float strength, float speed) {
    if (strength <= 0.001f) return float3(0.0f, 1.0f, 0.0f);

    // Fine, realistic ripple spectrum (small choppy wavelets instead of huge swells)
    const float4 waves[5] = {
      float4( 1.00f,  0.30f, 1.10f, 0.050f),
      float4( 0.80f, -0.60f, 1.90f, 0.035f),
      float4( 0.30f,  0.95f, 3.20f, 0.022f),
      float4(-0.75f,  0.65f, 5.40f, 0.014f),
      float4( 0.15f, -1.00f, 8.50f, 0.008f),
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
      float  deriv = wave * cos(x) * freq * amp * 4.0f;
      dX += dir * deriv;
    }

    return normalize(float3(-dX.x, 1.0f, -dX.y));
}
