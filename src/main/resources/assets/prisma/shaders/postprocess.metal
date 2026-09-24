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
  float motionBlurEnabled;
  float time;
  
  float sunAngle;
  packed_float3 camPos;
  
  packed_float3 prevCamPos;
  float _pad1;
  
  float4x4 viewProj;
  float4x4 prevViewProj;
  float4x4 invViewProj;
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

fragment float4 prisma_postprocess_fs(

  PostVertexOut in [[stage_in]],
  texture2d<float> hdrTex [[texture(0)]],
  depth2d<float> depthTex [[texture(1)]],
  sampler smp [[sampler(0)]],
  constant PostUniforms& u [[buffer(0)]]
) {
                float2 texSize = float2(hdrTex.get_width(), hdrTex.get_height());
  float2 dx = float2(1.0f / texSize.x, 0.0f);
  float2 dy = float2(0.0f, 1.0f / texSize.y);
  float3 cCol = sampleBicubic(hdrTex, smp, in.uv).rgb;
  float3 nCol = sampleBicubic(hdrTex, smp, in.uv - dy).rgb;
  float3 sCol = sampleBicubic(hdrTex, smp, in.uv + dy).rgb;
  float3 wCol = sampleBicubic(hdrTex, smp, in.uv - dx).rgb;
  float3 eCol = sampleBicubic(hdrTex, smp, in.uv + dx).rgb;
  float3 sharpCol = cCol + (cCol * 4.0f - nCol - sCol - wCol - eCol) * 0.85f;
  float3 minCol = min(cCol, min(min(nCol, sCol), min(wCol, eCol)));
  float3 maxCol = max(cCol, max(max(nCol, sCol), max(wCol, eCol)));
  float3 color = clamp(sharpCol, minCol, maxCol);

  float depth = depthTex.sample(smp, in.uv);
  if (depth < 1.0f) {
      float4 clipPos = float4(in.uv.x * 2.0f - 1.0f, in.uv.y * 2.0f - 1.0f, depth, 1.0f);
      float4 worldRel = u.invViewProj * clipPos;
      worldRel /= max(worldRel.w, 0.00001f);
      
      float3 globalPos = worldRel.xyz + u.camPos;
      float3 prevRelPos = globalPos - u.prevCamPos;
      
      float4 prevClip = u.prevViewProj * float4(prevRelPos, 1.0f);
      prevClip /= max(prevClip.w, 0.00001f);
      float2 prevUv = prevClip.xy * 0.5f + 0.5f;
      // Metal y is inverted clip space usually? Let's check if prevUv y needs flip.
      // In Space warp we did `prevClip.xy * 0.5 + 0.5`, no flip, and it worked perfectly.
      
      float2 velocity = in.uv - prevUv;
      
      float velLen = length(velocity);
      if (velLen > 0.0005f && u.motionBlurEnabled > 0.5f) {
          velocity *= clamp(0.04f / velLen, 0.0f, 1.0f); // Max velocity length
          int mbSamples = 6;
          float2 velStep = velocity / float(mbSamples);
          float2 mbUv = in.uv;
          float3 mbColor = float3(0.0f);
          for (int i = 0; i < mbSamples; i++) {
              mbColor += hdrTex.sample(smp, mbUv).rgb;
              mbUv -= velStep;
          }
          color = mix(color, mbColor / float(mbSamples), saturate(velLen * 50.0f));
      }
  }

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
  
  
  
  

  // Tonemapper (ACES)
  
  
  // Boost Exposure
  color *= 1.35f;

  // AgX Tonemap
  const float3x3 agx_mat = float3x3(
    float3(0.84166f, 0.04639f, 0.04018f),
    float3(0.05737f, 0.81755f, 0.14197f),
    float3(0.10097f, 0.13606f, 0.81785f)
  );
  const float3x3 agx_mat_inv = float3x3(
    float3(1.19688f, -0.09802f, -0.09903f),
    float3(-0.05290f, 1.15190f, -0.22208f),
    float3(-0.14398f, -0.05388f, 1.32111f)
  );
  
  float3 min_ev = float3(-10.0f);
  float3 max_ev = float3(6.5f);
  
  color = agx_mat * color;
  color = clamp((log2(max(color, 1e-10f)) - min_ev) / (max_ev - min_ev), 0.0f, 1.0f);
  
  // AgX Default Contrast
  float3 x2 = color * color;
  float3 x4 = x2 * x2;
  color = 15.5f * x4 * x2 - 40.14f * x4 * color + 31.96f * x4 - 6.868f * x2 * color + 0.4298f * x2 + 0.1191f * color - 0.00232f;
  
  color = agx_mat_inv * color;
  color = max(color, float3(0.0f));
  
  
  // Make it darker / Punchy (Strong Contrast S-Curve)
  color = max(color, float3(0.0f));
  
  // Strong contrast curve
  color = pow(max(color, float3(0.0f)), float3(1.45f)); // Strong shadow crush
 
  
  // Boost Saturation (Vibrance)
  float lumaSat = dot(color, float3(0.2126f, 0.7152f, 0.0722f));
  color = mix(float3(lumaSat), color, 1.35f);



  float postLumaVal = postLuma(color);
  color = mix(float3(postLumaVal), color, 0.95f);

  float2 vUv = in.uv * 2.0f - 1.0f;
  float vignette = 1.0f - dot(vUv, vUv) * 0.30f;
  color *= smoothstep(0.0f, 1.0f, vignette);

  
  

  return float4(color, 1.0f);
}
