#include <metal_stdlib>
using namespace metal;

struct PostVertexOut {
  float4 position [[position]];
  float2 uv;
};

vertex PostVertexOut prisma_postprocess_vs(uint vertexId [[vertex_id]]) {
  const float2 positions[3] = {
    float2(-1.0,  1.0),
    float2( 3.0,  1.0),
    float2(-1.0, -3.0)
  };
  const float2 uvs[3] = {
    float2(0.0, 0.0),
    float2(2.0, 0.0),
    float2(0.0, 2.0)
  };
  PostVertexOut out;
  out.position = float4(positions[vertexId], 0.0, 1.0);
  out.uv = uvs[vertexId];
  return out;
}

struct PostUniforms {
  float2 texelSize;
  float time;
  float sunAngle;
  float3 _pad;
  float4x4 viewProj;
};

static inline float postLuma(float3 c) {
  return dot(c, float3(0.299f, 0.587f, 0.114f));
}


static inline float3 applyFxaa(texture2d<float> tex, sampler smp, float2 uv, float2 texel) {
  float3 rgbCenter = tex.sample(smp, uv).rgb;

  float lumaN = postLuma(tex.sample(smp, uv + float2(0.0f, -texel.y)).rgb);
  float lumaS = postLuma(tex.sample(smp, uv + float2(0.0f,  texel.y)).rgb);
  float lumaE = postLuma(tex.sample(smp, uv + float2( texel.x, 0.0f)).rgb);
  float lumaW = postLuma(tex.sample(smp, uv + float2(-texel.x, 0.0f)).rgb);
  float lumaCenter = postLuma(rgbCenter);

  float lumaMin = min(lumaCenter, min(min(lumaN, lumaS), min(lumaE, lumaW)));
  float lumaMax = max(lumaCenter, max(max(lumaN, lumaS), max(lumaE, lumaW)));
  float lumaRange = lumaMax - lumaMin;


  if (lumaRange < max(0.0312f, lumaMax * 0.125f)) {
    return rgbCenter;
  }

  float2 dir;
  dir.x = -((lumaN + lumaS) - 2.0f * lumaCenter) * 2.0f - ((lumaE + lumaW) - 2.0f * lumaCenter);
  dir.y = ((lumaE + lumaW) - 2.0f * lumaCenter) * 2.0f + ((lumaN + lumaS) - 2.0f * lumaCenter);
  dir = float2(lumaW - lumaE, lumaN - lumaS);

  float dirLen = length(dir);
  if (dirLen < 1e-5f) {
    return rgbCenter;
  }
  dir = dir / dirLen;

  float3 rgbBlur = tex.sample(smp, uv + dir * texel * 1.5f).rgb * 0.5f
                  + tex.sample(smp, uv - dir * texel * 1.5f).rgb * 0.5f;

  float blendAmount = saturate(lumaRange / max(lumaMax, 0.0001f));
  return mix(rgbCenter, rgbBlur, blendAmount * 0.75f);
}

fragment float4 prisma_postprocess_fs(
  PostVertexOut in [[stage_in]],
  texture2d<float> hdrTex [[texture(0)]],
  sampler smp [[sampler(0)]],
  constant PostUniforms& u [[buffer(0)]]
) {
                float2 texSize = float2(hdrTex.get_width(), hdrTex.get_height());
  float2 dx = float2(1.0f / texSize.x, 0.0f);
  float2 dy = float2(0.0f, 1.0f / texSize.y);
  float3 cCol = hdrTex.sample(smp, in.uv).rgb;
  float3 nCol = hdrTex.sample(smp, in.uv - dy).rgb;
  float3 sCol = hdrTex.sample(smp, in.uv + dy).rgb;
  float3 wCol = hdrTex.sample(smp, in.uv - dx).rgb;
  float3 eCol = hdrTex.sample(smp, in.uv + dx).rgb;
  float3 sharpCol = cCol + (cCol * 4.0f - nCol - sCol - wCol - eCol) * 0.85f;
  float3 minCol = min(cCol, min(min(nCol, sCol), min(wCol, eCol)));
  float3 maxCol = max(cCol, max(max(nCol, sCol), max(wCol, eCol)));
  float3 color = clamp(sharpCol, minCol, maxCol);

    // Extract Bloom using Vogel Disk (Golden Angle)
  float3 bloomSum = float3(0.0f);
  float bloomWeight = 0.0f;
  float radius = 0.12f;
  int samples = 32;
  float goldenAngle = 2.400000333f;
  float randomRot = fract(sin(dot(in.position.xy, float2(12.9898f, 78.233f))) * 43758.5453f) * 6.283185f;
  
  for (int i = 1; i <= samples; i++) {
      float r = sqrt(float(i) + 0.5f) / sqrt(float(samples));
      float theta = float(i) * goldenAngle + randomRot;
      float2 offset = float2(cos(theta), sin(theta)) * r * radius;
      offset.y *= (u.texelSize.x / u.texelSize.y);
      
      float3 s = hdrTex.sample(smp, in.uv + offset).rgb;
      
      float knee = 0.5f;
      float l = postLuma(s);
      float rq = clamp(l - 0.75f + knee, 0.0f, knee * 2.0f);
      rq = (rq * rq) / (4.0f * knee + 0.001f);
      float3 extracted = s * max(rq, l - 0.75f) / max(l, 0.001f);
      
      float w = 1.0f / (1.0f + r * 15.0f);
      bloomSum += extracted * w;
      bloomWeight += w;
  }
  
  if (bloomWeight > 0.0f) {
      float3 bloom = bloomSum / bloomWeight;
      color += bloom * 1.50f;
  }
  
  
  // Uchimura (Gran Turismo) Tonemapper
  float P = 1.0f;  // max display brightness
  float a = 1.0f;  // contrast
  float m = 0.22f; // linear section start
  float l = 0.4f;  // linear section length
  float c = 0.33f; // black
  float b = 0.0f;  // pedestal
  
  float l0 = ((P - m) * l) / a;
  float L0 = m - m / a;
  float L1 = m + (1.0f - m) / a;
  float S0 = m + l0;
  float S1 = m + a * l0;
  float C2 = (a * P) / (P - S1);
  float CP = -C2 / P;

  float3 w0 = 1.0f - smoothstep(0.0f, m, color);
  float3 w2 = step(m + l0, color);
  float3 w1 = 1.0f - w0 - w2;

  float3 T = m * pow(color / m, c) + b;
  float3 S = P - (P - S1) * exp(CP * (color - S0));
  float3 L = m + a * (color - m);

  color = T * w0 + L * w1 + S * w2;


  float postLumaVal = postLuma(color);
  color = mix(float3(postLumaVal), color, 0.95f);

  float2 vUv = in.uv * 2.0f - 1.0f;
  float vignette = 1.0f - dot(vUv, vUv) * 0.30f;
  color *= smoothstep(0.0f, 1.0f, vignette);

  
  

  return float4(color, 1.0f);
}
