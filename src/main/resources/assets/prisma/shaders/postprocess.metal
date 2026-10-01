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
  
  float isFinalPass;
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
  float2 texel = 1.0f / texSize;
  
  float3 c = hdrTex.sample(smp, in.uv).rgb;
  float3 color = c;

  // Contrast Adaptive Laplacian Sharpen
  float unsharpStrength = u._pad1; 
  if (unsharpStrength > 0.01f) {
      float3 n  = hdrTex.sample(smp, in.uv + float2(0, -texel.y)).rgb;
      float3 w  = hdrTex.sample(smp, in.uv + float2(-texel.x, 0)).rgb;
      float3 e  = hdrTex.sample(smp, in.uv + float2(texel.x, 0)).rgb;
      float3 s  = hdrTex.sample(smp, in.uv + float2(0, texel.y)).rgb;
      float3 minVal = min(c, min(min(n, s), min(w, e)));
      float3 maxVal = max(c, max(max(n, s), max(w, e)));
      // Strong Laplacian Sharpen (Reverse Blur) with clamping
      float3 laplacian = c * 4.0f - (n + s + w + e);
      color = clamp(c + laplacian * unsharpStrength * 2.0f, minVal, maxVal);
  }
  
  uint2 depthGid = uint2(in.uv * float2(depthTex.get_width(), depthTex.get_height()));
  float depth = depthTex.read(depthGid);
  if (depth > 0.00005f) {
      float4 clipPos = float4(in.uv.x * 2.0f - 1.0f, in.uv.y * 2.0f - 1.0f, depth, 1.0f);
      float4 worldRel = u.invViewProj * clipPos;
      worldRel /= max(worldRel.w, 0.00001f);
      
      float3 globalPos = worldRel.xyz + u.camPos;
      float3 prevRelPos = globalPos - u.prevCamPos;
      
      float4 prevClip = u.prevViewProj * float4(prevRelPos, 1.0f);
      prevClip /= max(prevClip.w, 0.00001f);
      float2 prevUv = float2(prevClip.x * 0.5f + 0.5f, prevClip.y * 0.5f + 0.5f);
      
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
      
      // === Auto-Focus Depth of Field (Bokeh) ===
      float focusDepthRaw = depthTex.read(uint2(depthTex.get_width() / 2, depthTex.get_height() / 2));
      float focusDist = 1000.0f;
      if (focusDepthRaw > 0.00005f) {
          float4 fClip = float4(0.0f, 0.0f, focusDepthRaw, 1.0f);
          float4 fRel = u.invViewProj * fClip;
          focusDist = length(fRel.xyz / max(fRel.w, 0.00001f));
      }
      
      float pixelDist = length(worldRel.xyz);
      // Suave cinematic transition: starts blurring much later and grows slowly
      float coc = clamp(abs(pixelDist - focusDist) * 0.008f - 0.1f, 0.0f, 1.0f);
      
      if (coc > 0.01f) {
          float3 dofSum = float3(0.0f);
          float dofWeight = 0.0f;
          // Substantially reduced max blur radius for a subtle, elegant bokeh
          float dofRadius = 0.005f * coc;
          float randomRot = fract(sin(dot(in.position.xy, float2(12.9898f, 78.233f))) * 43758.5453f) * 6.283185f;
          
          for (int i = 1; i <= 16; i++) {
              float r = sqrt(float(i) + 0.5f) / sqrt(16.0f);
              float theta = float(i) * 2.400000333f + randomRot;
              float2 offset = float2(cos(theta), sin(theta)) * r * dofRadius;
              offset.y *= (u.texelSize.x / u.texelSize.y);
              
              dofSum += hdrTex.sample(smp, saturate(in.uv + offset)).rgb;
              dofWeight += 1.0f;
          }
          color = mix(color, dofSum / dofWeight, coc);
      }

  }

  if (u.isFinalPass > 0.5f) {
  // Extract Bloom using Vogel Disk (Golden Angle)
  float3 bloomSum = float3(0.0f);
  float bloomWeight = 0.0f;
  float radius = 0.065f;
  int samples = 24;
  float goldenAngle = 2.400000333f;
  float randomRot = fract(sin(dot(in.position.xy, float2(12.9898f, 78.233f))) * 43758.5453f) * 6.283185f;
  
  for (int i = 1; i <= samples; i++) {
      float r = sqrt(float(i) + 0.5f) / sqrt(float(samples));
      float theta = float(i) * goldenAngle + randomRot;
      float2 offset = float2(cos(theta), sin(theta)) * r * radius;
      offset.y *= (u.texelSize.x / u.texelSize.y);
      
      float3 s = hdrTex.sample(smp, in.uv + offset).rgb;
      
      float knee = 0.40f;
      float threshold = 0.80f;
      float l = postLuma(s);
      float rq = clamp(l - threshold + knee, 0.0f, knee * 2.0f);
      rq = (rq * rq) / (4.0f * knee + 0.001f);
      float3 extracted = s * max(rq, l - threshold) / max(l, 0.001f);
      
      float w = 1.0f / (1.0f + r * 12.0f);
      bloomSum += extracted * w;
      bloomWeight += w;
  }
  

  if (bloomWeight > 0.0f) {
      float3 bloom = bloomSum / bloomWeight;
      color += bloom * 1.50f;
      
      // Procedural Ghosting Lens Flare (Anamorphic/Cinematic)
      float2 ghostVec = -(in.uv * 2.0f - 1.0f) * 0.45f;
      float3 ghostSum = float3(0.0f);
      float3 flareColors[4] = { float3(1.0, 0.5, 0.2), float3(0.3, 0.6, 1.0), float3(0.1, 0.9, 0.3), float3(0.8, 0.2, 1.0) };
      for (int k = 1; k <= 4; k++) {
          float2 guv = saturate(in.uv + ghostVec * float(k));
          float3 g = hdrTex.sample(smp, guv).rgb;
          float gl = postLuma(g);
          if (gl > 0.95f) {
              float weight = (5.0f - float(k)) * 0.08f;
              ghostSum += (g - 0.95f) * flareColors[k-1] * weight;
          }
      }
      // Halo
      float2 haloVec = normalize(ghostVec) * 0.40f;
      float2 huv = saturate(in.uv + haloVec);
      float3 h = hdrTex.sample(smp, huv).rgb;
      if (postLuma(h) > 0.95f) {
          ghostSum += (h - 0.95f) * float3(0.2, 0.4, 1.0) * 0.15f;
      }
      color += ghostSum;
  }

  
  // Color Grading: Saturation and Contrast
  float luma = postLuma(color);
  color = mix(float3(luma), color, 1.25f);
  // S-curve removed to prevent burnt clouds
  
  float2 vUv = in.uv * 2.0f - 1.0f;
  float dist = length(vUv);
  float vignette = 1.0f - smoothstep(0.80f, 1.6f, dist) * 0.45f;
  
  // Subtle Chromatic Aberration on edges
  float2 caOffset = vUv * 0.003f;
  float r_ca = hdrTex.sample(smp, in.uv - caOffset).r;
  float b_ca = hdrTex.sample(smp, in.uv + caOffset).b;
  // Apply tonemapping curve to CA samples to match
  r_ca = r_ca * r_ca * (3.0f - 2.0f * r_ca);
  b_ca = b_ca * b_ca * (3.0f - 2.0f * b_ca);
  
  color.r = mix(color.r, r_ca, smoothstep(0.5f, 1.5f, dist));
  color.b = mix(color.b, b_ca, smoothstep(0.5f, 1.5f, dist));
  
  color *= vignette;
  color = saturate(color);


  
  
  
  



  // Note: Tonemapping is handled cleanly in deferred compute shader via Luma-Preserving Filmic.
  // Duplicating ACES here was causing double-tonemapping, crushed shadows, and white edge halos.

  // Film grain removed per user request

  }

  
  

  return float4(color, 1.0f);
}
