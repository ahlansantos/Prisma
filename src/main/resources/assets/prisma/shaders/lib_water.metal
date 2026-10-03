#pragma once
#include <metal_stdlib>
using namespace metal;

static inline float3 computeEclipseWaterWaves(float2 pWorldXZ, float time, float strength, float speed) {
    float4 waves[5] = {
      float4( 1.00f,  0.20f, 0.45f, 0.28f),
      float4( 0.85f, -0.52f, 0.80f, 0.18f),
      float4( 0.40f,  0.92f, 1.30f, 0.09f),
      float4(-0.70f,  0.72f, 1.90f, 0.04f),
      float4( 0.10f,  1.00f, 2.60f, 0.02f)
    };
    
    float2 dX = float2(0.0f);
    float  timePhased = time * speed;
    
    for (int i = 0; i < 5; i++) {
      float2 dir   = normalize(waves[i].xy);
      float  freq  = waves[i].z;
      float  amp   = waves[i].w * strength * 0.15f;
      float  phase = speed * freq * time;
      float  x     = dot(dir, pWorldXZ) * freq + phase;
      
      float  wa    = amp * freq;
      float  S     = sin(x);
      float  C     = cos(x);
      
      dX += dir * wa * C * 0.8f;
    }
    
    float3 n = float3(-dX.x, 1.0f - max(0.0f, dot(dX, dX)), -dX.y);
    return normalize(n);
}
