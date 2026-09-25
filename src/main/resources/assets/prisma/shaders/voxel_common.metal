

#include <metal_stdlib>
using namespace metal;

struct PointLightData {
  float4 posAndRadius;
  float4 colorAndIntensity;
};

struct MobData {
  float4 posAndType;
  float4 rotAndScale;
  float4 animData;
  float4 flags;
};

struct VoxelUniforms {
  int4 gridOrigin;
  int4 gridSize;
  float4 camPos;
  float4 camRight;
  float4 playerPos;
  float4 shadowParams;
  float4 playerAnim;
  float4 playerHead;
  float4x4 invViewProj;
  float4x4 viewProj;
  PointLightData lights[1024];
  int4 mobCounts;
  MobData mobs[64];
};


static inline float sampleSceneDepth(depth2d<float> wTex, depth2d<float> hTex, sampler smp, float2 uv) {
  float w = wTex.sample(smp, uv);
  float h = hTex.sample(smp, uv);
  return max(w, h);
}

static inline float linearizeDepth(float rawDepth) {
  if (rawDepth <= 0.00005f) return 1000.0f;
  return 0.05f / max(rawDepth, 0.00005f);
}

static inline float3 reconstructWorldPos(
  float2 uv,
  float rawDepth,
  float3 camPos,
  float4x4 invViewProj
) {
  float2 ndc = float2(uv.x * 2.0f - 1.0f, uv.y * 2.0f - 1.0f);
  float4 clipPos = float4(ndc, rawDepth, 1.0f);
  float4 worldRel = invViewProj * clipPos;
  return camPos + (worldRel.xyz / max(worldRel.w, 0.00001f));
}

static inline float get_vanilla_brightness(float level) {
  float f = saturate(level);
  return f / (4.0f - 3.0f * f);
}

static inline uint2 readVoxel(
  device const uint2* voxelGrid,
  int3 origin,
  int3 size,
  int3 blockPos
) {
  if (voxelGrid == nullptr) return uint2(0, 0);
  int3 local = blockPos - origin;
  if (local.x < 0 || local.x >= size.x ||
      local.y < 0 || local.y >= size.y ||
      local.z < 0 || local.z >= size.z) {
    return uint2(0, 0);
  }
  uint index = (uint(local.z) * uint(size.y) + uint(local.y)) * uint(size.x) + uint(local.x);
  return voxelGrid[index];
}

static inline float3 unpackVoxelColor(uint2 vox) {
  uint shapeId = (vox.y >> 24) & 0xFF;
  if (shapeId == 5 || shapeId == 7 || shapeId == 11 || shapeId == 18 || shapeId == 20) {
    float r = float((vox.y >> 12) & 0x3F) / 63.0f;
    float g = float((vox.y >> 6) & 0x3F) / 63.0f;
    float b = float(vox.y & 0x3F) / 63.0f;
    return float3(r, g, b);
  }
  if (shapeId == 16 || shapeId == 17) {
    return float3(0.55f, 0.35f, 0.15f);
  }
  float r = float((vox.y >> 16) & 0xFF) / 255.0f;
  float g = float((vox.y >> 8) & 0xFF) / 255.0f;
  float b = float(vox.y & 0xFF) / 255.0f;
  return float3(r, g, b);
}

static inline float2 unpackVoxelLight(uint2 vox) {
  float block = float((vox.x >> 4) & 0x0F) / 15.0f;
  float sky = float((vox.x >> 8) & 0x0F) / 15.0f;
  return float2(block, sky);
}

static inline uint unpackVoxelBlockId(uint2 vox) {
  return (vox.x >> 16) & 0xFFFF;
}

static inline uint2 readVoxelLocal(
  device const uint2* voxelGrid,
  int3 size,
  int3 local
) {
  if (voxelGrid == nullptr) return uint2(0, 0);
  if (local.x < 0 || local.x >= size.x ||
      local.y < 0 || local.y >= size.y ||
      local.z < 0 || local.z >= size.z) {
    return uint2(0, 0);
  }
  uint index = (uint(local.z) * uint(size.y) + uint(local.y)) * uint(size.x) + uint(local.x);
  return voxelGrid[index];
}

static inline float3 sampleVoxelTexture(
  texture2d<float> atlasTex,
  sampler smp,
  constant float4* uvTable,
  uint blockId,
  bool isTinted,
  bool isFullBlock,
  float3 hitPos,
  float3 hitNormal,
  float3 fallbackColor
) {
  if (uvTable == nullptr || blockId == 0) return fallbackColor;
  float4 uvBounds;
  if (abs(hitNormal.y) > 0.5f) {
    uvBounds = (hitNormal.y > 0.0f) ? uvTable[blockId * 3] : uvTable[blockId * 3 + 1];
  } else {
    uvBounds = uvTable[blockId * 3 + 2];
  }
  if (uvBounds.z <= uvBounds.x || uvBounds.w <= uvBounds.y) return fallbackColor;
  float3 fractPos = fract(hitPos);
  float2 faceUv;
  if (abs(hitNormal.y) > 0.5f) {
    faceUv = hitNormal.y > 0.0f ? fractPos.xz : float2(fractPos.x, 1.0f - fractPos.z);
  } else if (abs(hitNormal.x) > 0.5f) {
    faceUv = float2(fractPos.z, 1.0f - fractPos.y);
  } else {
    faceUv = float2(fractPos.x, 1.0f - fractPos.y);
  }
  float2 atlasUv = mix(uvBounds.xy, uvBounds.zw, faceUv);
  float4 sampleCol = atlasTex.sample(smp, atlasUv);
  if (sampleCol.a < 0.15f) return fallbackColor;
  if (isTinted) {
    if (!isFullBlock || hitNormal.y > 0.5f) {
      return fallbackColor * sampleCol.rgb;
    }
    return sampleCol.rgb;
  }
  return sampleCol.rgb;
}

static inline float sampleVoxelAlpha(
  texture2d<float> atlasTex,
  sampler smp,
  constant float4* uvTable,
  uint blockId,
  float2 faceUv,
  int faceIndex
) {
  if (uvTable == nullptr || blockId == 0) return -1.0f;
  float4 uvBounds = uvTable[blockId * 3 + faceIndex];
  if (uvBounds.z <= uvBounds.x || uvBounds.w <= uvBounds.y) return -1.0f;
  float2 atlasUv = mix(uvBounds.xy, uvBounds.zw, saturate(faceUv));
  return atlasTex.sample(smp, atlasUv).a;
}

struct FaceUvResult {
  float2 uv;
  int index;
};

static inline FaceUvResult getVoxelFaceUv(float3 pLocal) {
  float dX = min(pLocal.x, 1.0f - pLocal.x);
  float dY = min(pLocal.y, 1.0f - pLocal.y);
  float dZ = min(pLocal.z, 1.0f - pLocal.z);
  if (dY <= dX && dY <= dZ) {
    int idx = (pLocal.y >= 0.5f) ? 0 : 1;
    return FaceUvResult{ float2(saturate(pLocal.x), saturate(pLocal.z)), idx };
  } else if (dZ <= dX) {
    return FaceUvResult{ float2(saturate(pLocal.x), saturate(1.0f - pLocal.y)), 2 };
  } else {
    return FaceUvResult{ float2(saturate(pLocal.z), saturate(1.0f - pLocal.y)), 2 };
  }
}

static inline float computeVXAO(
  device const uint2* voxelGrid,
  int3 origin,
  int3 size,
  float3 pWorld,
  float3 nWorld,
  float3 camPos,
  int radius,
  float2 screenPos
) {
  if (voxelGrid == nullptr) return 0.0f;

  float distToCam = length(pWorld - camPos);
  float maxVoxelDist = float(radius * 16) - 4.0f;
  if (distToCam >= maxVoxelDist) return 0.0f;
  float distFade = saturate((maxVoxelDist - distToCam) / 16.0f);

  float3 up = abs(nWorld.y) < 0.99f ? float3(0.0f, 1.0f, 0.0f) : float3(1.0f, 0.0f, 0.0f);
  float3 tangent = normalize(cross(up, nWorld));
  float3 bitangent = cross(nWorld, tangent);

  float ign = fract(52.9829189f * fract(dot(screenPos, float2(0.06711056f, 0.00583715f))));
  float rotAngle = ign * 6.2831853f;
  float cosR = cos(rotAngle);
  float sinR = sin(rotAngle);
  float3 rotTangent = tangent * cosR + bitangent * sinR;
  float3 rotBitangent = cross(nWorld, rotTangent);

  const float3 sampleDirs[4] = {
    float3( 0.00f,  1.00f,  0.00f),
    float3( 0.81f,  0.58f,  0.00f),
    float3(-0.40f,  0.58f,  0.70f),
    float3(-0.40f,  0.58f, -0.70f)
  };

  float totalOcclusion = 0.0f;
  float maxWeight = 0.0f;

  const float stepDist[2] = { 0.25f, 0.55f };
  const float stepWeight[2] = { 1.20f, 0.80f };

  float3 start = pWorld + nWorld * 0.06f;

  for (int d = 0; d < 4; d++) {
    float3 dir = rotTangent * sampleDirs[d].x + nWorld * sampleDirs[d].y + rotBitangent * sampleDirs[d].z;
    float dirWeight = max(dot(dir, nWorld), 0.15f);
    float stepJitter = fract(ign * 7.13f + float(d) * 0.25f);
    int stepsForDir = (d == 0) ? 2 : 1;
    for (int s = 0; s < stepsForDir; s++) {
      float dist = stepDist[s] * (0.80f + stepJitter * 0.40f);
      float3 samplePos = start + dir * dist;
      int3 voxelPos = int3(floor(samplePos));
      uint2 vox = readVoxel(voxelGrid, origin, size, voxelPos);
      if ((vox.x & 1) != 0) {
        uint shapeId = (vox.y >> 24) & 0xFF;
        if (shapeId == 0) {
          totalOcclusion += stepWeight[s] * dirWeight;
          maxWeight += stepWeight[s] * dirWeight;
          break;
        } else {
          maxWeight += stepWeight[s] * dirWeight;
        }
      } else {
        maxWeight += stepWeight[s] * dirWeight;
      }
    }
  }

  float rawAo = (maxWeight > 0.0001f) ? (totalOcclusion / maxWeight) : 0.0f;
  float ao = saturate(rawAo * 1.35f);
  return ao * distFade;
}

static inline float computeSSAO(
  depth2d<float> worldDepthTex,
  sampler smp,
  float2 uv,
  float rawDepth,
  float3 pWorld,
  float3 nWorld,
  float3 camPos,
  float4x4 viewProj,
  float2 screenPos
) {
  if (rawDepth <= 0.00005f) return 0.0f;

  float distToCam = length(pWorld - camPos);
  if (distToCam > 48.0f) return 0.0f;
  float distFade = saturate((48.0f - distToCam) / 12.0f);

  float3 up = abs(nWorld.y) < 0.99f ? float3(0.0f, 1.0f, 0.0f) : float3(1.0f, 0.0f, 0.0f);
  float3 tangent = normalize(cross(up, nWorld));
  float3 bitangent = cross(nWorld, tangent);

  float ign = fract(52.9829189f * fract(dot(screenPos, float2(0.06711056f, 0.00583715f))));
  float rotAngle = ign * 6.2831853f;
  float cosR = cos(rotAngle);
  float sinR = sin(rotAngle);
  float3 rotTangent = tangent * cosR + bitangent * sinR;
  float3 rotBitangent = cross(nWorld, rotTangent);

  const float3 sampleKernel[4] = {
    float3( 0.5381f,  0.1856f, 0.4319f),
    float3(-0.3371f,  0.5679f, 0.3551f),
    float3( 0.1689f, -0.4598f, 0.5547f),
    float3(-0.1533f,  0.3596f, 0.7411f)
  };

  const float radius = 0.60f;
  float occlusion = 0.0f;

  for (int i = 0; i < 5; i++) {
    float3 kDir = sampleKernel[i];
    float3 dirHemi = rotTangent * kDir.x + rotBitangent * kDir.y + nWorld * kDir.z;
    float scale = float(i + 1) / 4.0f;
    scale = mix(0.20f, 1.0f, scale * scale);
    float3 samplePos = pWorld + nWorld * 0.03f + normalize(dirHemi) * (radius * scale);

    float3 sampleRel = samplePos - camPos;
    float4 clip = viewProj * float4(sampleRel, 1.0f);
    if (clip.w <= 0.0001f) continue;
    float2 sUv = (clip.xy / clip.w) * 0.5f + 0.5f;
    if (sUv.x < 0.0f || sUv.x > 1.0f || sUv.y < 0.0f || sUv.y > 1.0f) continue;

    float sDepth = worldDepthTex.sample(smp, sUv);
    if (sDepth <= 0.00005f) continue;

    float distGeom = linearizeDepth(sDepth);
    float depthDiff = clip.w - distGeom;

    if (depthDiff > 0.020f && depthDiff < radius) {
      float rangeAtten = smoothstep(radius, 0.020f, depthDiff);
      occlusion += rangeAtten;
    }
  }

  float ao = occlusion / 4.0f;
  return saturate(ao * 1.15f) * distFade;
}

struct PointLightResult {
  float3 color;
  float coverage;
  float shadowDarkening;
};

static inline bool intersectAABB(
  float3 rOrigin,
  float3 invDir,
  float3 bMin,
  float3 bMax,
  float maxDist
) {
  float3 t0 = (bMin - rOrigin) * invDir;
  float3 t1 = (bMax - rOrigin) * invDir;
  float3 tMin = min(t0, t1);
  float3 tMax = max(t0, t1);
  float tNear = max(max(tMin.x, tMin.y), tMin.z);
  float tFar = min(min(tMax.x, tMax.y), tMax.z);
  return (tNear <= tFar) && (tFar > 0.05f) && (tNear < maxDist);
}

static inline bool intersectAABBDist(
  float3 rOrigin,
  float3 invDir,
  float3 bMin,
  float3 bMax,
  float maxDist,
  thread float& outT
) {
  float3 t0 = (bMin - rOrigin) * invDir;
  float3 t1 = (bMax - rOrigin) * invDir;
  float3 tMin = min(t0, t1);
  float3 tMax = max(t0, t1);
  float tNear = max(max(tMin.x, tMin.y), tMin.z);
  float tFar = min(min(tMax.x, tMax.y), tMax.z);
  if (tNear <= tFar && tFar > 0.08f && tNear > 0.08f && tNear < maxDist) {
    outT = tNear;
    return true;
  }
  return false;
}
struct ShadowRayResult {  float vis;  float3 tint;}; static inline float hash3D(float3 p) {     return fract(sin(dot(p, float3(12.9898f, 78.233f, 45.164f))) * 43758.5453f); } static inline float smoothNoise3D(float3 p) {     float3 i = floor(p);     float3 f = fract(p);     float3 u = f * f * (3.0f - 2.0f * f);     return mix(mix(mix(hash3D(i + float3(0,0,0)), hash3D(i + float3(1,0,0)), u.x),                    mix(hash3D(i + float3(0,1,0)), hash3D(i + float3(1,1,0)), u.x), u.y),                mix(mix(hash3D(i + float3(0,0,1)), hash3D(i + float3(1,0,1)), u.x),                    mix(hash3D(i + float3(0,1,1)), hash3D(i + float3(1,1,1)), u.x), u.y), u.z); } static inline float fbmClouds(float3 p) {
    float f = 0.0f; float w = 0.5f;
    f += w * smoothNoise3D(p); p *= 2.5f; w *= 0.4f;
    f += w * smoothNoise3D(p); p *= 2.5f; w *= 0.4f;
    f += w * smoothNoise3D(p); p *= 2.5f; w *= 0.4f;
    f += w * smoothNoise3D(p);
    return f;
}

static inline float4 computeVolumetricClouds(
    float3 pWorld, float3 rWorld, float gameTime, float3 hazeColor, float sunWeight, float3 sunDir, float3 moonDir, float3 currentSunColor, float3 currentMoonColor, float cloudsEnabled, float cloudSteps, float rainStrength, float maxDist
) {
    if (cloudsEnabled < 0.5f || abs(rWorld.y) < 0.001f) return float4(0.0f, 0.0f, 0.0f, 1.0f);
    
    float cMin = 650.0f;
    float cMax = 950.0f;
    float tm = (cMin - pWorld.y) / rWorld.y;
    float tM = (cMax - pWorld.y) / rWorld.y;
    if (tM <= 0.0f) return float4(0.0f, 0.0f, 0.0f, 1.0f);
    
    if (tm > tM) { float temp = tm; tm = tM; tM = temp; }
    tm = max(tm, 0.0f);
    tM = min(tM, min(tm + 3500.0f, maxDist));
    if (tM <= tm) return float4(0.0f, 0.0f, 0.0f, 1.0f);
    
    float3 sPos = pWorld + rWorld * tm;
    float3 ePos = pWorld + rWorld * tM;
    float mL = length(ePos - sPos);
    float sC = max(10.0f, min(80.0f, cloudSteps));
    float sZ = mL / sC;
    float3 st = rWorld * sZ;
    float3 cP = sPos;
    float tr = 1.0f;
    float3 sL = float3(0.0f);
    
    float cosSunTheta = dot(rWorld, sunDir);
    float cosMoonTheta = dot(rWorld, moonDir);
    float hgF = (1.0f - 0.45f*0.45f) / pow(max(1.0f + 0.45f*0.45f - 2.0f*0.45f*cosSunTheta, 0.01f), 1.5f);
    float hgB = (1.0f - 0.25f*0.25f) / pow(max(1.0f + 0.25f*0.25f + 2.0f*0.25f*cosSunTheta, 0.01f), 1.5f);
    float ph = max(0.75f, mix(hgB * 0.85f, hgF, 0.65f));
    
    float moonHgF = (1.0f - 0.45f*0.45f) / pow(max(1.0f + 0.45f*0.45f - 2.0f*0.45f*cosMoonTheta, 0.01f), 1.5f);
    float moonHgB = (1.0f - 0.25f*0.25f) / pow(max(1.0f + 0.25f*0.25f + 2.0f*0.25f*cosMoonTheta, 0.01f), 1.5f);
    float moonPh = max(0.75f, mix(moonHgB * 0.85f, moonHgF, 0.65f));
    
    float3 dayCloudAmbient = mix(float3(0.38f, 0.48f, 0.60f), hazeColor, 0.35f);
    float3 nightCloudAmbient = float3(0.015f, 0.025f, 0.06f);
    float3 rainCloudAmbient = mix(float3(0.05f, 0.07f, 0.10f), float3(0.24f, 0.26f, 0.30f), sunWeight);
    float3 baseCloudAmbient = mix(nightCloudAmbient, dayCloudAmbient, sunWeight);
    float3 cloudAmbient = mix(baseCloudAmbient, rainCloudAmbient, rainStrength);
    
    float3 celDir = (sunWeight > 0.5f) ? sunDir : moonDir;
    float cloudThreshold = mix(0.40f, 0.20f, rainStrength);
    float cloudDensityMult = mix(8.0f, 12.0f, rainStrength);
    
    for(int i = 0; i < int(sC); i++) {
        if(tr < 0.05f) break;
        
        float3 q = cP * 0.0025f + float3(gameTime * 0.015f, gameTime * 0.008f, gameTime * 0.005f);
        float n = fbmClouds(q);
        float d = max(0.0f, n - cloudThreshold) * cloudDensityMult;
        
        float hF = (cP.y - cMin) / (cMax - cMin);
        d *= smoothstep(0.0f, 0.15f, hF) * smoothstep(1.0f, 0.65f, hF);
        
        float currDist = length(cP - pWorld);
        d *= smoothstep(4500.0f, 1500.0f, currDist);
        
        if(d > 0.01f){
            float stT = exp(-d * 0.06f * sZ);
            
            float3 lPos = cP + celDir * 25.0f;
            float3 lq = lPos * 0.0025f + float3(gameTime * 0.015f, gameTime * 0.008f, gameTime * 0.005f);
            float ln = smoothNoise3D(lq) * 0.85f;
            float ld = max(0.0f, ln - cloudThreshold) * cloudDensityMult;
            
            float lT = exp(-ld * 0.06f * 25.0f);
            float powder = 1.0f - exp(-d * 2.5f);
            float inscatter = lT * powder;
            
            float3 sunDirect = currentSunColor * (inscatter * ph * sunWeight * 0.95f);
            float3 moonDirect = currentMoonColor * (inscatter * moonPh * (1.0f - sunWeight) * 0.01f);
            float3 celestialDirect = (sunDirect + moonDirect) * (1.0f - rainStrength * 0.75f);
            
            float3 S = celestialDirect + cloudAmbient;
            sL += tr * d * 0.06f * S * sZ;
            tr *= stT;
        }
        cP += st;
    }
    return float4(sL, tr);
} static inline bool testSubVoxelBit(constant ulong* bitmaskTable, uint blockId, float3 localPos) {  if (bitmaskTable == nullptr || blockId >= 32768u) return true;  ulong mask = bitmaskTable[blockId];  if (mask == 0xFFFFFFFFFFFFFFFFULL) return true;  if (mask == 0ULL) return false;  int3 sub = clamp(int3(floor(localPos * 4.0f)), int3(0), int3(3));  uint bitIdx = uint((sub.z * 4 + sub.y) * 4 + sub.x);  return ((mask >> bitIdx) & 1ULL) != 0ULL;}struct OBB {
  float3 center;
  float3 extents;
  float3x3 rotation;
};

static inline float3x3 rotY(float a) {
    float s = sin(a); float c = cos(a);
    return float3x3(float3(c, 0, -s), float3(0, 1, 0), float3(s, 0, c));
}
static inline float3x3 rotX(float a) {
    float s = sin(a); float c = cos(a);
    return float3x3(float3(1, 0, 0), float3(0, c, s), float3(0, -s, c));
}
static inline float3x3 rotZ(float a) {
    float s = sin(a); float c = cos(a);
    return float3x3(float3(c, s, 0), float3(-s, c, 0), float3(0, 0, 1));
}

static inline float intersectOBB(float3 rayOrigin, float3 rayDir, OBB obb, thread float3& outNormal, thread float3& outLocalP, thread float3& outLocalN) {
    float3 p = rayOrigin - obb.center;
    float3 d = rayDir;
    float3 localP = float3(dot(p, obb.rotation[0]), dot(p, obb.rotation[1]), dot(p, obb.rotation[2]));
    float3 localD = float3(dot(d, obb.rotation[0]), dot(d, obb.rotation[1]), dot(d, obb.rotation[2]));
    float3 invD = 1.0f / localD;
    float3 t0 = (-obb.extents - localP) * invD;
    float3 t1 = (obb.extents - localP) * invD;
    float3 tmin = min(t0, t1);
    float3 tmax = max(t0, t1);
    float tNear = max(max(tmin.x, tmin.y), tmin.z);
    float tFar = min(min(tmax.x, tmax.y), tmax.z);
    if (tNear > tFar || tFar < 0.0f) return -1.0f;
    float3 hitLocal = localP + localD * tNear;
    float3 normalLocal = float3(0.0f);
    float3 absHit = abs(hitLocal) / obb.extents;
    if (absHit.x > absHit.y && absHit.x > absHit.z) {
        normalLocal.x = sign(hitLocal.x);
    } else if (absHit.y > absHit.z) {
        normalLocal.y = sign(hitLocal.y);
    } else {
        normalLocal.z = sign(hitLocal.z);
    }
    outNormal = normalLocal.x * obb.rotation[0] + normalLocal.y * obb.rotation[1] + normalLocal.z * obb.rotation[2];
    outLocalP = hitLocal / obb.extents;
    outLocalN = normalLocal;
    return tNear;
}

struct PlayerHit {
    float hitDist;
    float3 normal;
    float3 color;
};

static inline float2 getSkinUV(float3 localP, float3 localN, int partId) {
    float uOff = 0.0f, vOff = 0.0f;
    float w = 0.0f, h = 0.0f, d = 0.0f;
    if (partId == 0) {
        uOff = 0.0f; vOff = 0.0f; w = 8.0f; h = 8.0f; d = 8.0f;
    } else if (partId == 1) {
        uOff = 16.0f; vOff = 16.0f; w = 8.0f; h = 12.0f; d = 4.0f;
    } else if (partId == 2) {
        uOff = 40.0f; vOff = 16.0f; w = 4.0f; h = 12.0f; d = 4.0f;
    } else if (partId == 3) {
        uOff = 32.0f; vOff = 48.0f; w = 4.0f; h = 12.0f; d = 4.0f;
    } else if (partId == 4) {
        uOff = 0.0f; vOff = 16.0f; w = 4.0f; h = 12.0f; d = 4.0f;
    } else if (partId == 5) {
        uOff = 16.0f; vOff = 48.0f; w = 4.0f; h = 12.0f; d = 4.0f;
    }
    float px = localP.x * 0.5f + 0.5f;
    float py = 1.0f - (localP.y * 0.5f + 0.5f);
    float pz = localP.z * 0.5f + 0.5f;
    float faceU = 0.0f;
    float faceV = py;
    float2 baseUv = float2(0.0f);
    float2 size = float2(0.0f);
    if (localN.y > 0.5f) {
        baseUv = float2(uOff + d, vOff);
        size = float2(w, d);
        faceU = px; 
        faceV = 1.0f - pz;
    } else if (localN.y < -0.5f) {
        baseUv = float2(uOff + d + w, vOff);
        size = float2(w, d);
        faceU = px;
        faceV = pz;
    } else if (localN.x < -0.5f) {
        baseUv = float2(uOff + 0.0f, vOff + d);
        size = float2(d, h);
        faceU = 1.0f - pz;
    } else if (localN.x > 0.5f) {
        baseUv = float2(uOff + d + w, vOff + d);
        size = float2(d, h);
        faceU = pz;
    } else if (localN.z > 0.5f) {
        baseUv = float2(uOff + d, vOff + d);
        size = float2(w, h);
        faceU = px;
    } else {
        baseUv = float2(uOff + d + w + d, vOff + d);
        size = float2(w, h);
        faceU = 1.0f - px;
    }
    return (baseUv + float2(faceU * size.x, faceV * size.y)) / 64.0f;
}
static inline void tracePlayerOBB(float3 ro, float3 rd, float3 playerPos, float bodyYaw, float headYaw, float headPitch, float swing, float swingAmount, float isCrouch, float attackAnim, texture2d<float> skinTex, sampler smp, thread PlayerHit& hit) { hit.hitDist = 1e6f; hit.normal = float3(0.0f); hit.color = float3(0.0f); float3x3 bodyRot = rotY(-bodyYaw); float3x3 headRot = rotY(-bodyYaw - headYaw) * rotX(headPitch); float animT = swing * 0.6662f; swingAmount *= 0.5f; float armPitchL = cos(animT + 3.14159f) * 2.0f * swingAmount * 0.5f; float armPitchR = cos(animT) * 2.0f * swingAmount * 0.5f; float legPitchL = cos(animT) * 1.4f * swingAmount; float legPitchR = cos(animT + 3.14159f) * 1.4f * swingAmount; if (attackAnim > 0.0f) { float attackSwing = sin(attackAnim * 3.14159f);  armPitchR -= attackSwing * 1.5f; } float3 pBase = playerPos; float sneak_head_Y = isCrouch > 0.5f ? 0.2625f : 0.0f; float sneak_body_Y = isCrouch > 0.5f ? 0.2f : 0.0f; float sneak_arm_Y = isCrouch > 0.5f ? 0.2f : 0.0f; float sneak_leg_Y = isCrouch > 0.5f ? -0.0125f : 0.0f; float sneak_leg_Z = isCrouch > 0.5f ? -0.25f : 0.0f; float sneak_body_pitch = isCrouch > 0.5f ? 0.5f : 0.0f; float sneak_arm_pitch = isCrouch > 0.5f ? 0.4f : 0.0f; OBB head; head.extents = float3(0.25f, 0.25f, 0.25f); float3 headPivot = pBase + float3(0.0f, 1.5f - sneak_head_Y, 0.0f); head.center = headPivot + headRot * float3(0.0f, 0.25f, 0.0f); head.rotation = headRot; OBB body; body.extents = float3(0.25f, 0.375f, 0.125f); float3 bodyPivot = pBase + float3(0.0f, 1.5f - sneak_body_Y, 0.0f); body.rotation = bodyRot * rotX(sneak_body_pitch); body.center = bodyPivot + body.rotation * float3(0.0f, -0.375f, 0.0f); OBB armL; armL.extents = float3(0.125f, 0.375f, 0.125f); float3 armLPivot = pBase + float3(0.0f, 1.375f - sneak_arm_Y, 0.0f) + bodyRot * float3(0.375f, 0.0f, 0.0f); armL.rotation = bodyRot * rotX(armPitchL + sneak_arm_pitch); armL.center = armLPivot + armL.rotation * float3(0.0f, -0.25f, 0.0f); OBB armR; armR.extents = float3(0.125f, 0.375f, 0.125f); float3 armRPivot = pBase + float3(0.0f, 1.375f - sneak_arm_Y, 0.0f) + bodyRot * float3(-0.375f, 0.0f, 0.0f); armR.rotation = bodyRot * rotX(armPitchR + sneak_arm_pitch); armR.center = armRPivot + armR.rotation * float3(0.0f, -0.25f, 0.0f); OBB legL; legL.extents = float3(0.125f, 0.375f, 0.125f); float3 legLPivot = pBase + float3(0.0f, 0.75f + sneak_leg_Y, 0.0f) + bodyRot * float3(0.125f, 0.0f, sneak_leg_Z); legL.rotation = bodyRot * rotX(legPitchL); legL.center = legLPivot + legL.rotation * float3(0.0f, -0.375f, 0.0f); OBB legR; legR.extents = float3(0.125f, 0.375f, 0.125f); float3 legRPivot = pBase + float3(0.0f, 0.75f + sneak_leg_Y, 0.0f) + bodyRot * float3(-0.125f, 0.0f, sneak_leg_Z); legR.rotation = bodyRot * rotX(legPitchR); legR.center = legRPivot + legR.rotation * float3(0.0f, -0.375f, 0.0f);     float t, minT = 1e6f;
    float3 n, bestN = float3(0.0f);
    float3 lp, bestLp = float3(0.0f);
    float3 ln, bestLn = float3(0.0f);
    int bestPart = -1;
    t = intersectOBB(ro, rd, head, n, lp, ln); if (t > 0.0f && t < minT) { minT = t; bestN = n; bestLp = lp; bestLn = ln; bestPart = 0; }
    t = intersectOBB(ro, rd, body, n, lp, ln); if (t > 0.0f && t < minT) { minT = t; bestN = n; bestLp = lp; bestLn = ln; bestPart = 1; }
    t = intersectOBB(ro, rd, armR, n, lp, ln); if (t > 0.0f && t < minT) { minT = t; bestN = n; bestLp = lp; bestLn = ln; bestPart = 2; }
    t = intersectOBB(ro, rd, armL, n, lp, ln); if (t > 0.0f && t < minT) { minT = t; bestN = n; bestLp = lp; bestLn = ln; bestPart = 3; }
    t = intersectOBB(ro, rd, legR, n, lp, ln); if (t > 0.0f && t < minT) { minT = t; bestN = n; bestLp = lp; bestLn = ln; bestPart = 4; }
    t = intersectOBB(ro, rd, legL, n, lp, ln); if (t > 0.0f && t < minT) { minT = t; bestN = n; bestLp = lp; bestLn = ln; bestPart = 5; }
    if (minT < 1e6f) {
        float2 uv = getSkinUV(bestLp, bestLn, bestPart);
        constexpr sampler nearestSampler(coord::normalized, address::clamp_to_edge, filter::nearest); float4 skinCol = skinTex.sample(nearestSampler, uv);
        if (skinCol.a < 0.1f) {
            if (bestPart == 0) skinCol.rgb = float3(0.8f, 0.6f, 0.5f);
            else if (bestPart == 1) skinCol.rgb = float3(0.2f, 0.5f, 0.7f);
            else if (bestPart == 2 || bestPart == 3) skinCol.rgb = float3(0.2f, 0.5f, 0.7f);
            else skinCol.rgb = float3(0.1f, 0.2f, 0.5f);
        }
        hit.hitDist = minT;
        hit.normal = bestN;
        hit.color = skinCol.rgb;
    }
}

static inline float3 safeRayInv(float3 d) {  return float3(    1.0f / (abs(d.x) > 1e-5f ? d.x : (d.x >= 0.0f ? 1e-5f : -1e-5f)),    1.0f / (abs(d.y) > 1e-5f ? d.y : (d.y >= 0.0f ? 1e-5f : -1e-5f)),    1.0f / (abs(d.z) > 1e-5f ? d.z : (d.z >= 0.0f ? 1e-5f : -1e-5f))  );}static inline bool intersectLocalAABB(  float3 pLocal,  float3 rayInv,  float3 bMin,  float3 bMax,  float tIn,  float nextT,  float rayDist,  int s,  thread float& tHit) {  float3 t0 = (bMin - pLocal) * rayInv;  float3 t1 = (bMax - pLocal) * rayInv;  float3 tA = min(t0, t1);  float3 tB = max(t0, t1);  float tNear = max(max(tA.x, tA.y), tA.z);  float tFar = min(min(tB.x, tB.y), tB.z);  tHit = tNear;  return (tNear <= tFar && tFar >= tIn && tNear <= nextT && tNear <= rayDist && (s > 0 || tNear > 0.005f));}static inline ShadowRayResult traceDdaShadowRay(  device const uint2* voxelGrid,  int3 origin,  int3 size,  float3 rayStart,  float3 rayEnd,  texture2d<float> blockAtlasTex,  sampler smp,  constant float4* blockUvTable,  constant ulong* bitmaskTable) {  ShadowRayResult res;  res.vis = 1.0f;  res.tint = float3(1.0f);  float3 rayDelta = rayEnd - rayStart;  float rayDist = length(rayDelta);  if (rayDist < 0.35f) return res;  float3 rDir = rayDelta / rayDist;  float3 rayInv = safeRayInv(rDir);  float3 localStart = rayStart - float3(origin.xyz);  int3 currentVoxel = int3(floor(localStart));  float3 localEnd = rayEnd - float3(origin);  int3 targetVoxel = int3(floor(localEnd));  int3 step = int3(sign(rDir));  float3 invDir = 1.0f / max(abs(rDir), float3(0.00001f));  float3 tDelta = invDir;  float3 tMax;  tMax.x = (rDir.x > 0.0f) ? (float(currentVoxel.x + 1) - localStart.x) * invDir.x : (rDir.x < 0.0f) ? (localStart.x - float(currentVoxel.x)) * invDir.x : 1e30f;  tMax.y = (rDir.y > 0.0f) ? (float(currentVoxel.y + 1) - localStart.y) * invDir.y : (rDir.y < 0.0f) ? (localStart.y - float(currentVoxel.y)) * invDir.y : 1e30f;  tMax.z = (rDir.z > 0.0f) ? (float(currentVoxel.z + 1) - localStart.z) * invDir.z : (rDir.z < 0.0f) ? (localStart.z - float(currentVoxel.z)) * invDir.z : 1e30f;  float tIn = 0.0f;  for (int s = 0; s < 24; s++) {    if (currentVoxel.x == targetVoxel.x &&        currentVoxel.y == targetVoxel.y &&        currentVoxel.z == targetVoxel.z) {      break;    }    float nextT = min(tMax.x, min(tMax.y, tMax.z));    uint2 vox = readVoxelLocal(voxelGrid, size.xyz, currentVoxel);
    uint shapeCheck = (vox.y >> 24) & 0xFF;
    if (shapeCheck == 3 || shapeCheck == 6 || shapeCheck == 19 || shapeCheck == 22) { vox.x &= ~1u; }    if ((vox.x & 1) != 0) {      uint reflType = (vox.x >> 12) & 0x0F;      if (reflType == 1u) {        float3 pIn = clamp(localStart + rDir * max(tIn, 0.01f) - float3(currentVoxel), 0.0f, 0.999f);        float3 pOut = clamp(localStart + rDir * min(nextT, rayDist) - float3(currentVoxel), 0.0f, 0.999f);        if (s > 0 || length(mix(pIn, pOut, 0.5f) + float3(currentVoxel) - localStart) > 0.025f) {          float3 glassCol = unpackVoxelColor(vox);          bool isClear = (length_squared(glassCol) < 0.05f) || (glassCol.r > 0.92f && glassCol.g > 0.92f && glassCol.b > 0.92f);          uint blockId = unpackVoxelBlockId(vox);          FaceUvResult res_glassUv = getVoxelFaceUv(pIn);          float2 glassUv = res_glassUv.uv;          float glassAlpha = sampleVoxelAlpha(blockAtlasTex, smp, blockUvTable, blockId, glassUv, res_glassUv.index);          bool isBorder = false;          if (glassAlpha >= 0.0f) {            isBorder = (glassAlpha > 0.55f);          } else {            isBorder = (glassUv.x < 0.0625f || glassUv.x > 0.9375f || glassUv.y < 0.0625f || glassUv.y > 0.9375f);          }          if (isBorder) {            res.vis *= (isClear ? 0.78f : 0.62f);            if (!isClear) {              float maxC = max(glassCol.r, max(glassCol.g, glassCol.b));              float3 normCol = (maxC > 0.05f) ? saturate(glassCol / maxC) : glassCol;              res.tint *= normCol * 0.85f;            }          } else {            res.vis *= (isClear ? 0.99f : 0.95f);            if (!isClear) {              float maxC = max(glassCol.r, max(glassCol.g, glassCol.b));              float3 normCol = (maxC > 0.05f) ? saturate(glassCol / maxC) : glassCol;              res.tint *= normCol;            }          }          if (res.vis < 0.005f) {            res.vis = 0.0f;            return res;          }        }      } else if ((vox.x & 4) != 0) {      } else {        uint shapeId = (vox.y >> 24) & 0xFF;        uint blockId = unpackVoxelBlockId(vox);        if (shapeId == 11) {          float3 off = float3(            float((vox.y >> 21) & 7u) / 14.0f - 0.25f,            0.0f,            float((vox.y >> 18) & 7u) / 14.0f - 0.25f          );          float3 pLocal = (localStart - float3(currentVoxel)) - off;          bool hitCross = false;          float denom1 = rDir.x - rDir.z;          if (abs(denom1) > 1e-5f) {            float t1 = (pLocal.z - pLocal.x) / denom1;            if (t1 >= (tIn - 0.001f) && t1 <= (nextT + 0.001f) && t1 <= rayDist) {              if (s > 0 || t1 > 0.001f) {                float3 pHit = pLocal + rDir * t1;                if (pHit.x >= -0.01f && pHit.x <= 1.01f && pHit.y >= -0.01f && pHit.y <= 1.01f && pHit.z >= -0.01f && pHit.z <= 1.01f) {                  float2 uv = float2(saturate(pHit.x), saturate(1.0f - pHit.y));                  float alpha = sampleVoxelAlpha(blockAtlasTex, smp, blockUvTable, blockId, uv, 2);                  if (alpha >= 0.25f) {                    hitCross = true;                  } else if (alpha < 0.0f) {                    if (pHit.y <= 0.60f && abs(pHit.x - 0.5f) <= 0.08f) hitCross = true;                  }                }              }            }          }          if (!hitCross) {            float denom2 = rDir.x + rDir.z;            if (abs(denom2) > 1e-5f) {              float t2 = (1.0f - pLocal.x - pLocal.z) / denom2;              if (t2 >= (tIn - 0.001f) && t2 <= (nextT + 0.001f) && t2 <= rayDist) {                if (s > 0 || t2 > 0.001f) {                  float3 pHit = pLocal + rDir * t2;                  if (pHit.x >= -0.01f && pHit.x <= 1.01f && pHit.y >= -0.01f && pHit.y <= 1.01f && pHit.z >= -0.01f && pHit.z <= 1.01f) {                    float2 uv = float2(saturate(1.0f - pHit.x), saturate(1.0f - pHit.y));                    float alpha = sampleVoxelAlpha(blockAtlasTex, smp, blockUvTable, blockId, uv, 2);                    if (alpha >= 0.25f) {                      hitCross = true;                    } else if (alpha < 0.0f) {                      if (pHit.y <= 0.60f && abs(pHit.x - 0.5f) <= 0.08f) hitCross = true;                    }                  }                }              }            }          }          if (hitCross) {            res.vis = 0.0f;            return res;          }        } else if (shapeId == 20) {          float3 off = float3(            float((vox.y >> 21) & 7u) / 14.0f - 0.25f,            0.0f,            float((vox.y >> 18) & 7u) / 14.0f - 0.25f          );          float3 pLocal = (localStart - float3(currentVoxel)) - off;          float tHit;          if (intersectLocalAABB(pLocal, rayInv, float3(0.3125f, 0.0f, 0.3125f), float3(0.6875f, 1.0f, 0.6875f), tIn, nextT, rayDist, s, tHit)) {            res.vis = 0.0f;            return res;          }        } else if (shapeId == 12) {          float3 pIn = clamp(localStart + rDir * max(tIn, 0.001f) - float3(currentVoxel), 0.0f, 1.0f);          float3 pOut = clamp(localStart + rDir * min(nextT, rayDist) - float3(currentVoxel), 0.0f, 1.0f);          FaceUvResult res_uvEntry = getVoxelFaceUv(pIn);          float2 uvEntry = res_uvEntry.uv;          float alpha1 = sampleVoxelAlpha(blockAtlasTex, smp, blockUvTable, blockId, uvEntry, res_uvEntry.index);          FaceUvResult res_uvExit = getVoxelFaceUv(pOut);          float2 uvExit = res_uvExit.uv;          float alpha2 = sampleVoxelAlpha(blockAtlasTex, smp, blockUvTable, blockId, uvExit, res_uvExit.index);          bool hitLeaf = false;          if (alpha1 >= 0.0f || alpha2 >= 0.0f) {            hitLeaf = (alpha1 >= 0.25f) || (alpha2 >= 0.25f);          } else {            float3 pMid = mix(pIn, pOut, 0.5f);            int3 sub = clamp(int3(floor(pMid * 4.0f)), int3(0), int3(3));            hitLeaf = ((sub.x * 3 + sub.y * 5 + sub.z * 7) % 4) != 0;          }          if (hitLeaf) {            res.vis = 0.0f;            return res;          }        } else if (shapeId == 15) {          if (currentVoxel.x != targetVoxel.x || currentVoxel.y != targetVoxel.y || currentVoxel.z != targetVoxel.z) {            float3 pLocal = localStart - float3(currentVoxel);            float tHit;            if (intersectLocalAABB(pLocal, rayInv, float3(0.4375f, 0.0f, 0.4375f), float3(0.5625f, 0.625f, 0.5625f), tIn, nextT, rayDist, s, tHit)) {              res.vis = 0.0f;              return res;            }          }        } else if (shapeId == 16) {          uint chestData = vox.y & 0x00FFFFFF;          float x0 = float(chestData & 0x1F) / 16.0f;          float x1 = float((chestData >> 5) & 0x1F) / 16.0f;          float z0 = float((chestData >> 10) & 0x1F) / 16.0f;          float z1 = float((chestData >> 15) & 0x1F) / 16.0f;          if (x1 <= x0 || z1 <= z0) {            x0 = 0.0625f; x1 = 0.9375f; z0 = 0.0625f; z1 = 0.9375f;          }          float3 pLocal = localStart - float3(currentVoxel);          float tHit;          if (intersectLocalAABB(pLocal, rayInv, float3(x0, 0.0f, z0), float3(x1, 0.875f, z1), tIn, nextT, rayDist, s, tHit)) {            res.vis = 0.0f;            return res;          }        } else if (shapeId == 17) {          uint doorData = vox.y & 0x00FFFFFF;          float x0 = float(doorData & 0x1F) / 16.0f;          float x1 = float((doorData >> 5) & 0x1F) / 16.0f;          float z0 = float((doorData >> 10) & 0x1F) / 16.0f;          float z1 = float((doorData >> 15) & 0x1F) / 16.0f;          bool isUpper = ((doorData >> 20) & 1) != 0;          float3 pLocal = localStart - float3(currentVoxel);          float tHit;          if (intersectLocalAABB(pLocal, rayInv, float3(x0, 0.0f, z0), float3(x1, 1.0f, z1), tIn, nextT, rayDist, s, tHit)) {            float3 pHit = pLocal + rDir * max(tHit, 0.0f);            bool isHole = false;            if (isUpper) {              float uHoriz = (abs(x1 - x0) > abs(z1 - z0)) ? saturate(pHit.x) : saturate(pHit.z);              float vVert = saturate(1.0f - pHit.y);              float alpha = sampleVoxelAlpha(blockAtlasTex, smp, blockUvTable, blockId, float2(uHoriz, vVert), 2);              if (alpha > 0.0f) {                isHole = (alpha < 0.25f);              } else {                bool uPane = (uHoriz >= 0.1875f && uHoriz <= 0.4375f) || (uHoriz >= 0.5625f && uHoriz <= 0.8125f);                bool vPane = (pHit.y >= 0.35f && pHit.y <= 0.55f) || (pHit.y >= 0.65f && pHit.y <= 0.88f);                isHole = uPane && vPane;              }            }            if (!isHole) {              res.vis = 0.0f;              return res;            }          }        } else if (shapeId == 18) {          uint fenceData = (vox.y >> 18) & 0x0F;          float3 pLocal = localStart - float3(currentVoxel);          float tHit;          bool hitFence = intersectLocalAABB(pLocal, rayInv, float3(0.375f, 0.0f, 0.375f), float3(0.625f, 1.0f, 0.625f), tIn, nextT, rayDist, s, tHit);          if (!hitFence && (fenceData & 1) != 0) {            hitFence = intersectLocalAABB(pLocal, rayInv, float3(0.4375f, 0.75f, 0.0f), float3(0.5625f, 0.9375f, 0.375f), tIn, nextT, rayDist, s, tHit)                    || intersectLocalAABB(pLocal, rayInv, float3(0.4375f, 0.375f, 0.0f), float3(0.5625f, 0.5625f, 0.375f), tIn, nextT, rayDist, s, tHit);          }          if (!hitFence && (fenceData & 2) != 0) {            hitFence = intersectLocalAABB(pLocal, rayInv, float3(0.4375f, 0.75f, 0.625f), float3(0.5625f, 0.9375f, 1.0f), tIn, nextT, rayDist, s, tHit)                    || intersectLocalAABB(pLocal, rayInv, float3(0.4375f, 0.375f, 0.625f), float3(0.5625f, 0.5625f, 1.0f), tIn, nextT, rayDist, s, tHit);          }          if (!hitFence && (fenceData & 4) != 0) {            hitFence = intersectLocalAABB(pLocal, rayInv, float3(0.0f, 0.75f, 0.4375f), float3(0.375f, 0.9375f, 0.5625f), tIn, nextT, rayDist, s, tHit)                    || intersectLocalAABB(pLocal, rayInv, float3(0.0f, 0.375f, 0.4375f), float3(0.375f, 0.5625f, 0.5625f), tIn, nextT, rayDist, s, tHit);          }          if (!hitFence && (fenceData & 8) != 0) {            hitFence = intersectLocalAABB(pLocal, rayInv, float3(0.625f, 0.75f, 0.4375f), float3(1.0f, 0.9375f, 0.5625f), tIn, nextT, rayDist, s, tHit)                    || intersectLocalAABB(pLocal, rayInv, float3(0.625f, 0.375f, 0.4375f), float3(1.0f, 0.5625f, 0.5625f), tIn, nextT, rayDist, s, tHit);          }          if (hitFence) {            res.vis = 0.0f;            return res;          }        } else if (shapeId == 7) {          uint tData = (vox.y >> 18) & 0x3F;          bool tTop = (tData & 1u) != 0u;          bool tOpen = (tData & 2u) != 0u;          uint tFacing = (tData >> 2u) & 3u;          float3 bMin = float3(0.0f);          float3 bMax = float3(1.0f);          if (!tOpen) {            bMin = float3(0.0f, tTop ? 0.8125f : 0.0f, 0.0f);            bMax = float3(1.0f, tTop ? 1.0f : 0.1875f, 1.0f);          } else {            if (tFacing == 0u) { bMin = float3(0.0f, 0.0f, 0.8125f); bMax = float3(1.0f, 1.0f, 1.0f); }            else if (tFacing == 1u) { bMin = float3(0.0f, 0.0f, 0.0f); bMax = float3(1.0f, 1.0f, 0.1875f); }            else if (tFacing == 2u) { bMin = float3(0.8125f, 0.0f, 0.0f); bMax = float3(1.0f, 1.0f, 1.0f); }            else { bMin = float3(0.0f, 0.0f, 0.0f); bMax = float3(0.1875f, 1.0f, 1.0f); }          }          float3 pLocal = localStart - float3(currentVoxel);          float tHit;          if (intersectLocalAABB(pLocal, rayInv, bMin, bMax, tIn, nextT, rayDist, s, tHit)) {            float3 pHit = pLocal + rDir * max(tHit, 0.0f);            float2 trapUv = (!tOpen) ? float2(saturate(pHit.x), saturate(pHit.z))                                     : ((tFacing < 2u) ? float2(saturate(pHit.x), saturate(1.0f - pHit.y))                                                        : float2(saturate(pHit.z), saturate(1.0f - pHit.y)));            float alpha = sampleVoxelAlpha(blockAtlasTex, smp, blockUvTable, blockId, trapUv, 2);            if (alpha >= 0.25f || alpha < 0.0f) {              res.vis = 0.0f;              return res;            }          }        } else if (shapeId == 5) {          uint stairData = (vox.y >> 18) & 0x3F;          uint sHalf = stairData & 1u;          uint sFacing = (stairData >> 1) & 3u;          uint sShape = (stairData >> 3) & 7u;          float3 pLocal = localStart - float3(currentVoxel);          float tHit;          float y0 = (sHalf == 0u) ? 0.0f : 0.5f;          float y1 = (sHalf == 0u) ? 0.5f : 1.0f;          if (intersectLocalAABB(pLocal, rayInv, float3(0.0f, y0, 0.0f), float3(1.0f, y1, 1.0f), tIn, nextT, rayDist, s, tHit)) {            res.vis = 0.0f;            return res;          }          float sy0 = (sHalf == 0u) ? 0.5f : 0.0f;          float sy1 = (sHalf == 0u) ? 1.0f : 0.5f;          float4 mStep = float4(0.0f, 0.0f, 1.0f, 1.0f);          float4 extraCorner = float4(-1.0f);          if (sFacing == 0u) {            if (sShape == 0u) mStep = float4(0.0f, 0.0f, 1.0f, 0.5f);            else if (sShape == 1u) { mStep = float4(0.0f, 0.0f, 1.0f, 0.5f); extraCorner = float4(0.0f, 0.5f, 0.5f, 1.0f); }            else if (sShape == 2u) { mStep = float4(0.0f, 0.0f, 1.0f, 0.5f); extraCorner = float4(0.5f, 0.5f, 1.0f, 1.0f); }            else if (sShape == 3u) mStep = float4(0.0f, 0.0f, 0.5f, 0.5f);            else if (sShape == 4u) mStep = float4(0.5f, 0.0f, 1.0f, 0.5f);          } else if (sFacing == 1u) {            if (sShape == 0u) mStep = float4(0.0f, 0.5f, 1.0f, 1.0f);            else if (sShape == 1u) { mStep = float4(0.0f, 0.5f, 1.0f, 1.0f); extraCorner = float4(0.5f, 0.0f, 1.0f, 0.5f); }            else if (sShape == 2u) { mStep = float4(0.0f, 0.5f, 1.0f, 1.0f); extraCorner = float4(0.0f, 0.0f, 0.5f, 0.5f); }            else if (sShape == 3u) mStep = float4(0.5f, 0.5f, 1.0f, 1.0f);            else if (sShape == 4u) mStep = float4(0.0f, 0.5f, 0.5f, 1.0f);          } else if (sFacing == 2u) {            if (sShape == 0u) mStep = float4(0.0f, 0.0f, 0.5f, 1.0f);            else if (sShape == 1u) { mStep = float4(0.0f, 0.0f, 0.5f, 1.0f); extraCorner = float4(0.5f, 0.5f, 1.0f, 1.0f); }            else if (sShape == 2u) { mStep = float4(0.0f, 0.0f, 0.5f, 1.0f); extraCorner = float4(0.5f, 0.0f, 1.0f, 0.5f); }            else if (sShape == 3u) mStep = float4(0.0f, 0.5f, 0.5f, 1.0f);            else if (sShape == 4u) mStep = float4(0.0f, 0.0f, 0.5f, 0.5f);          } else {            if (sShape == 0u) mStep = float4(0.5f, 0.0f, 1.0f, 1.0f);            else if (sShape == 1u) { mStep = float4(0.5f, 0.0f, 1.0f, 1.0f); extraCorner = float4(0.0f, 0.0f, 0.5f, 0.5f); }            else if (sShape == 2u) { mStep = float4(0.5f, 0.0f, 1.0f, 1.0f); extraCorner = float4(0.0f, 0.5f, 0.5f, 1.0f); }            else if (sShape == 3u) mStep = float4(0.5f, 0.0f, 1.0f, 0.5f);            else if (sShape == 4u) mStep = float4(0.5f, 0.5f, 1.0f, 1.0f);          }          if (intersectLocalAABB(pLocal, rayInv, float3(mStep.x, sy0, mStep.y), float3(mStep.z, sy1, mStep.w), tIn, nextT, rayDist, s, tHit)) {            res.vis = 0.0f;            return res;          }          if (extraCorner.x >= 0.0f) {            if (intersectLocalAABB(pLocal, rayInv, float3(extraCorner.x, sy0, extraCorner.y), float3(extraCorner.z, sy1, extraCorner.w), tIn, nextT, rayDist, s, tHit)) {              res.vis = 0.0f;              return res;            }          }        } else if (shapeId == 19) {        } else if (shapeId == 0) {          if (s > 0) {            res.vis = 0.0f;            return res;          }        } else {          if (bitmaskTable != nullptr && blockId < 32768u) {            ulong mask = bitmaskTable[blockId];            if (mask != 0ULL) {              float3 pIn = clamp(localStart + rDir * max(tIn, 0.01f) - float3(currentVoxel), 0.0f, 0.999f);              float3 pOut = clamp(localStart + rDir * min(nextT, rayDist) - float3(currentVoxel), 0.0f, 0.999f);              bool hitSolid = false;              for (float st = 0.05f; st <= 0.95f; st += 0.12f) {                float3 pSample = mix(pIn, pOut, st);                int3 sub = clamp(int3(floor(pSample * 4.0f)), int3(0), int3(3));                uint bitIdx = uint((sub.z * 4 + sub.y) * 4 + sub.x);                if (((mask >> bitIdx) & 1ULL) != 0ULL) {                  hitSolid = true;                  break;                }              }              if (hitSolid) {                res.vis = 0.0f;                return res;              }            }          }        }      }    }    if (nextT >= (rayDist - 0.02f)) {      break;    }    tIn = nextT;    if (tMax.x < tMax.y) {      if (tMax.x < tMax.z) {        currentVoxel.x += step.x;        tMax.x += tDelta.x;      } else {        currentVoxel.z += step.z;        tMax.z += tDelta.z;      }    } else {      if (tMax.y < tMax.z) {        currentVoxel.y += step.y;        tMax.y += tDelta.y;      } else {        currentVoxel.z += step.z;        tMax.z += tDelta.z;      }    }  }  return res;}static inline PointLightResult computePointLights(  device const uint2* voxelGrid,  int3 origin,  int3 size,  float3 pWorld,  float3 nWorld,  int lightCount,  constant PointLightData* lights,  float4 playerPosAndHeight,  float4 shadowParams,  float4 playerAnim,  float4 playerHead,  int activeMobCount,  constant MobData* mobs,  float2 screenPos,  texture2d<float> blockAtlasTex,  sampler smp,  constant float4* blockUvTable,  constant ulong* bitmaskTable,  bool isGlassSurface) {  PointLightResult res;  res.color = float3(0.0f);  res.coverage = 0.0f;  res.shadowDarkening = 0.0f;  if (voxelGrid == nullptr || lightCount <= 0) return res;  float3 totalLight = float3(0.0f);  float maxCoverage = 0.0f;  float maxShadowDarkening = 0.0f;  for (int i = 0; i < lightCount; i++) {    float3 lPos = lights[i].posAndRadius.xyz;    float lRadius = lights[i].posAndRadius.w;    float3 lColor = lights[i].colorAndIntensity.xyz;    float rawIntensity = lights[i].colorAndIntensity.w;    uint facingCode = uint(floor(rawIntensity / 10.0f + 0.05f));    float lIntensity = rawIntensity - float(facingCode) * 10.0f;    float3 toLight = lPos - pWorld;    float dist = length(toLight);    if (dist > lRadius || dist < 0.05f) continue;    float cov = saturate((lRadius - dist) / max(lRadius * 0.30f, 0.10f));    float3 lDir = toLight / dist;    if (facingCode != 0u) {      float3 torchDir = float3(0.0f);      if (facingCode == 1u) torchDir = float3(0.0f, 0.0f, -1.0f);      else if (facingCode == 2u) torchDir = float3(0.0f, 0.0f, 1.0f);      else if (facingCode == 3u) torchDir = float3(-1.0f, 0.0f, 0.0f);      else if (facingCode == 4u) torchDir = float3(1.0f, 0.0f, 0.0f);      float planeOffset = dot(pWorld - lPos, torchDir) + 0.30f;      if (planeOffset < -0.95f) continue;      float wallAtten = (planeOffset >= 0.0f) ? 1.0f : smoothstep(-0.95f, 0.0f, planeOffset);      lIntensity *= wallAtten;      cov *= wallAtten;    }    maxCoverage = max(maxCoverage, cov);    float dotNL = dot(nWorld, lDir);    bool isBackLit = dotNL <= 0.001f;    if (isBackLit && !isGlassSurface) continue;    float NdotL = isBackLit ? 1.0f : saturate((dotNL + 0.12f) / 1.12f);    float transmission = 1.0f;    if (isGlassSurface) {      if (isBackLit) {        NdotL = saturate((-dotNL + 0.12f) / 1.12f);        transmission = 0.55f;      } else {        transmission = 1.0f;      }    }    float slopeBias = 0.002f + 0.002f * saturate(1.0f - abs(dotNL));    float3 biasNormal = isBackLit ? -nWorld : nWorld;    float3 rayStart = pWorld + biasNormal * slopeBias + lDir * 0.001f;    ShadowRayResult shadowRes;    shadowRes.vis = 1.0f;    shadowRes.tint = float3(1.0f);    if (dist > 0.15f && dist < 16.0f) {      float3 shadowTarget = lPos;      float pSoft = 0.0f;      shadowRes = traceDdaShadowRay(voxelGrid, origin.xyz, size.xyz, rayStart, shadowTarget, blockAtlasTex, smp, blockUvTable, bitmaskTable); if (shadowRes.vis > 0.0f) { bool isHandheld = length(shadowTarget - playerPosAndHeight.xyz) < 1.5f; if (!isHandheld && shadowParams.w > 0.5f) { PlayerHit ph; tracePlayerOBB(rayStart, normalize(shadowTarget - rayStart), playerPosAndHeight.xyz, shadowParams.x, playerHead.x, playerHead.y, playerAnim.x, playerAnim.y, playerAnim.z, playerAnim.w, blockAtlasTex, smp, ph); if (ph.hitDist < dist) { shadowRes.vis = 0.0f; } } } }    if (shadowRes.vis > 0.001f) {      float distNorm = dist / lRadius;      float window = saturate(1.0f - distNorm);      float smoothWin = window * window * (3.0f - 2.0f * window);      float falloff = smoothWin / (dist * dist * 0.12f + dist * 0.35f + 0.80f);      totalLight += (lColor * shadowRes.tint) * (lIntensity * falloff * NdotL * shadowRes.vis * transmission);    } else if (!isBackLit) {      float distNorm = dist / lRadius;      float window = saturate(1.0f - distNorm);      float smoothWin = window * window * (3.0f - 2.0f * window);      float shadowStrength = (1.0f - shadowRes.vis) * smoothWin * NdotL;      maxShadowDarkening = max(maxShadowDarkening, shadowStrength);    }  }  res.color = totalLight;  res.coverage = maxCoverage;  res.shadowDarkening = max(0.0f, maxShadowDarkening - saturate(length(totalLight) * 0.75f));  return res;}struct EntityHit {  float dist;  float2 uv;  int triangleId;};static inline EntityHit traceEntityTriangles(    float3 rayStart,    float3 rayDir,    device const float* triangleBuffer,    int numTriangles) {    EntityHit hit = { 1e6f, float2(0.0f), -1 };    for (int i = 0; i < numTriangles; i++) {        float3 v0 = float3(triangleBuffer[i*9 + 0], triangleBuffer[i*9 + 1], triangleBuffer[i*9 + 2]);        float3 v1 = float3(triangleBuffer[i*9 + 3], triangleBuffer[i*9 + 4], triangleBuffer[i*9 + 5]);        float3 v2 = float3(triangleBuffer[i*9 + 6], triangleBuffer[i*9 + 7], triangleBuffer[i*9 + 8]);        float3 e1 = v1 - v0;        float3 e2 = v2 - v0;        float3 h = cross(rayDir, e2);        float a = dot(e1, h);        if (a > -0.00001f && a < 0.00001f) continue;        float f = 1.0f / a;        float3 s = rayStart - v0;        float u = f * dot(s, h);        if (u < 0.0f || u > 1.0f) continue;        float3 q = cross(s, e1);        float v = f * dot(rayDir, q);        if (v < 0.0f || u + v > 1.0f) continue;        float t = f * dot(e2, q);        if (t > 0.001f && t < hit.dist) {            hit.dist = t;            hit.uv = float2(u, v);            hit.triangleId = i;        }    }    return hit;}static inline float traceVoxelShadowFast(  device const uint2* voxelGrid,  int3 origin,  int3 size,  float3 pStart,  float3 pEnd,  int maxSteps) {  float3 delta = pEnd - pStart;  float dist = length(delta);  if (dist < 0.40f) return 1.0f;  float3 dir = delta / dist;  float3 localStart = pStart - float3(origin);  int3 current = int3(floor(localStart));  int3 target = int3(floor(pEnd - float3(origin.xyz)));  int3 step = int3(sign(dir));  float3 invDir = 1.0f / max(abs(dir), float3(0.00001f));  float3 tDelta = invDir;  float3 tMax;  tMax.x = (dir.x > 0.0f) ? (float(current.x + 1) - localStart.x) * invDir.x : (dir.x < 0.0f) ? (localStart.x - float(current.x)) * invDir.x : 1e30f;  tMax.y = (dir.y > 0.0f) ? (float(current.y + 1) - localStart.y) * invDir.y : (dir.y < 0.0f) ? (localStart.y - float(current.y)) * invDir.y : 1e30f;  tMax.z = (dir.z > 0.0f) ? (float(current.z + 1) - localStart.z) * invDir.z : (dir.z < 0.0f) ? (localStart.z - float(current.z)) * invDir.z : 1e30f;  for (int s = 0; s < maxSteps; s++) {    if (current.x == target.x && current.y == target.y && current.z == target.z) break;    float nextT = min(tMax.x, min(tMax.y, tMax.z));    if (nextT >= (dist - 0.1f)) break;    uint2 v = readVoxelLocal(voxelGrid, size.xyz, current);    if (((v.x >> 12) & 0x0F) == 1u) { } else if ((v.x & 1) != 0 && (v.x & 4) == 0) {      uint shapeId = (v.y >> 24) & 0xFF;      if (shapeId != 19 && shapeId != 11) {        return 0.0f;      }    }    if (tMax.x < tMax.y) {      if (tMax.x < tMax.z) { current.x += step.x; tMax.x += tDelta.x; }      else { current.z += step.z; tMax.z += tDelta.z; }    } else {      if (tMax.y < tMax.z) { current.y += step.y; tMax.y += tDelta.y; }      else { current.z += step.z; tMax.z += tDelta.z; }    }  }  return 1.0f;}struct VoxelReflResult {  float3 color;  float hitDist;  float alpha;  float3 normal;  float reflectivity;};static inline VoxelReflResult traceVoxelReflections(device const uint2* voxelGrid, int4 origin, int4 size, float3 pWorld, float3 rWorld, float3 activeSkyLight, float3 currentSunColor, float3 currentMoonColor, float3 celestialDir, float sunWeight, texture2d<float> blockAtlasTex, texture2d<float> playerSkinTex, sampler smp, constant float4* blockUvTable, constant ulong* bitmaskTable, int lightCount, constant const PointLightData* lights, int maxSteps, float maxPointLights, float reflectionPtShadows, float reflectionDirShadows, float vxaoInReflections, float giInReflections, float rainStrength, float gameTime, constant VoxelUniforms& uVoxel) {  if (voxelGrid == nullptr) return VoxelReflResult{ float3(0.0f), 1e6f, 0.0f, float3(0.0f), 0.0f };  float3 rayStart = pWorld + normalize(rWorld) * 0.25f;  float3 dir = normalize(rWorld);  float3 rayInv = safeRayInv(dir);  float3 localStart = rayStart - float3(origin.xyz);  int3 currentVoxel = int3(floor(localStart));  int3 prevVoxel = currentVoxel;  float3 stepDir = sign(dir);  float3 tDelta = abs(1.0f / max(abs(dir), float3(0.0001f)));  float3 tMax = (stepDir * (float3(currentVoxel) + stepDir * 0.5f + 0.5f - localStart)) * tDelta;  float tIn = 0.0f; PlayerHit ph; if (uVoxel.playerHead.w > 0.5f) { tracePlayerOBB(pWorld, rWorld, uVoxel.playerPos.xyz, uVoxel.shadowParams.x, uVoxel.playerHead.x, uVoxel.playerHead.y, uVoxel.playerAnim.x, uVoxel.playerAnim.y, uVoxel.playerAnim.z, uVoxel.playerAnim.w, playerSkinTex, smp, ph); }  for (int s = 0; s < maxSteps; s++) {    uint2 vox = readVoxelLocal(voxelGrid, size.xyz, currentVoxel);
    uint shapeCheck = (vox.y >> 24) & 0xFF;
    if (shapeCheck == 3 || shapeCheck == 6 || shapeCheck == 19 || shapeCheck == 22) { vox.x &= ~1u; }    bool isSolidHit = false;    if (((vox.x & 1) != 0 || (vox.x & 2) != 0 || (vox.x & 8) != 0) && ((vox.x & 4) == 0)) {      uint shapeId = (vox.y >> 24) & 0xFF;      uint blockId = unpackVoxelBlockId(vox);      bool isCrossHit = false;      float2 crossHitUv = float2(0.0f);      float3 crossHitNorm = float3(0.0f);      bool isTrapdoorHit = false;      float2 trapHitUv = float2(0.0f);      float3 trapHitNorm = float3(0.0f);      bool hasCustomNormal = false;      float3 customNormal = float3(0.0f);      isSolidHit = true;      if (shapeId == 11) {        float3 off = float3(          float((vox.y >> 21) & 7u) / 14.0f - 0.25f,          0.0f,          float((vox.y >> 18) & 7u) / 14.0f - 0.25f        );        float3 pLocal = (localStart - float3(currentVoxel)) - off;        bool hitCross = false;        float crossHitT = 0.0f;        float denom1 = dir.x - dir.z;        if (abs(denom1) > 1e-5f) {          float t1 = (pLocal.z - pLocal.x) / denom1;          if (t1 >= (tIn - 0.001f) && t1 <= (min(tMax.x, min(tMax.y, tMax.z)) + 0.001f)) {            float3 pHit = pLocal + dir * t1;            if (pHit.x >= -0.01f && pHit.x <= 1.01f && pHit.y >= -0.01f && pHit.y <= 1.01f && pHit.z >= -0.01f && pHit.z <= 1.01f) {              float2 uv = float2(saturate(pHit.x), saturate(1.0f - pHit.y));              float alpha = sampleVoxelAlpha(blockAtlasTex, smp, blockUvTable, blockId, uv, 2);              if (alpha >= 0.25f || (alpha < 0.0f && pHit.y <= 0.60f && abs(pHit.x - 0.5f) <= 0.08f)) {                hitCross = true;                crossHitT = t1;                crossHitUv = uv;                crossHitNorm = normalize(float3(-denom1, 0.0f, denom1));              }            }          }        }        if (!hitCross) {          float denom2 = dir.x + dir.z;          if (abs(denom2) > 1e-5f) {            float t2 = (1.0f - pLocal.x - pLocal.z) / denom2;            if (t2 >= (tIn - 0.001f) && t2 <= (min(tMax.x, min(tMax.y, tMax.z)) + 0.001f)) {              float3 pHit = pLocal + dir * t2;              if (pHit.x >= -0.01f && pHit.x <= 1.01f && pHit.y >= -0.01f && pHit.y <= 1.01f && pHit.z >= -0.01f && pHit.z <= 1.01f) {                float2 uv = float2(saturate(1.0f - pHit.x), saturate(1.0f - pHit.y));                float alpha = sampleVoxelAlpha(blockAtlasTex, smp, blockUvTable, blockId, uv, 2);                if (alpha >= 0.25f || (alpha < 0.0f && pHit.y <= 0.60f && abs(pHit.x - 0.5f) <= 0.08f)) {                  hitCross = true;                  crossHitT = t2;                  crossHitUv = uv;                  crossHitNorm = normalize(float3(-denom2, 0.0f, -denom2));                }              }            }          }        }        if (hitCross) {          tIn = crossHitT;          isSolidHit = true;          isCrossHit = true;          hasCustomNormal = true;          customNormal = (dot(crossHitNorm, dir) < 0.0f) ? crossHitNorm : -crossHitNorm;        } else {          isSolidHit = false;        }      } else if (shapeId == 12) {        float stepMaxT = min(tMax.x, min(tMax.y, tMax.z));        float3 pIn = clamp(localStart + dir * max(tIn, 0.001f) - float3(currentVoxel), 0.0f, 1.0f);        float3 pOut = clamp(localStart + dir * stepMaxT - float3(currentVoxel), 0.0f, 1.0f);        FaceUvResult res_uvEntry = getVoxelFaceUv(pIn);          float2 uvEntry = res_uvEntry.uv;          float alpha1 = sampleVoxelAlpha(blockAtlasTex, smp, blockUvTable, blockId, uvEntry, res_uvEntry.index);        FaceUvResult res_uvExit = getVoxelFaceUv(pOut);          float2 uvExit = res_uvExit.uv;          float alpha2 = sampleVoxelAlpha(blockAtlasTex, smp, blockUvTable, blockId, uvExit, res_uvExit.index);        bool hitLeaf = false;        if (alpha1 >= 0.0f || alpha2 >= 0.0f) {          hitLeaf = (alpha1 >= 0.25f) || (alpha2 >= 0.25f);        } else {          float3 pMid = mix(pIn, pOut, 0.5f);          int3 sub = clamp(int3(floor(pMid * 4.0f)), int3(0), int3(3));          hitLeaf = ((sub.x * 3 + sub.y * 5 + sub.z * 7) % 4) != 0;        }        if (hitLeaf) {          isSolidHit = true;          hasCustomNormal = true;          customNormal = (alpha1 >= 0.25f) ? ((s > 0) ? float3(prevVoxel - currentVoxel) : -stepDir) : stepDir;          if (alpha1 < 0.25f) tIn = mix(max(tIn, 0.001f), stepMaxT, 0.5f);        } else {          isSolidHit = false;        }      } else if (shapeId == 19) {        isSolidHit = false;      } else if (shapeId == 15) {        float3 pLocal = localStart - float3(currentVoxel);        float tHit;        float stepMaxT = min(tMax.x, min(tMax.y, tMax.z));        if (intersectLocalAABB(pLocal, rayInv, float3(0.4375f, 0.0f, 0.4375f), float3(0.5625f, 0.625f, 0.5625f), tIn, stepMaxT, 100.0f, s, tHit)) {          tIn = max(tHit, tIn);          isSolidHit = true;        } else {          isSolidHit = false;        }      } else if (shapeId == 16) {        uint chestData = vox.y & 0x00FFFFFF;        float x0 = float(chestData & 0x1F) / 16.0f;        float x1 = float((chestData >> 5) & 0x1F) / 16.0f;        float z0 = float((chestData >> 10) & 0x1F) / 16.0f;        float z1 = float((chestData >> 15) & 0x1F) / 16.0f;        if (x1 <= x0 || z1 <= z0) {          x0 = 0.0625f; x1 = 0.9375f; z0 = 0.0625f; z1 = 0.9375f;        }        float3 pLocal = localStart - float3(currentVoxel);        float tHit;        float stepMaxT = min(tMax.x, min(tMax.y, tMax.z));        if (intersectLocalAABB(pLocal, rayInv, float3(x0, 0.0f, z0), float3(x1, 0.875f, z1), tIn, stepMaxT, 100.0f, s, tHit)) {          tIn = max(tHit, tIn);          isSolidHit = true;        } else {          isSolidHit = false;        }      } else if (shapeId == 17) {        uint doorData = vox.y & 0x00FFFFFF;        float x0 = float(doorData & 0x1F) / 16.0f;        float x1 = float((doorData >> 5) & 0x1F) / 16.0f;        float z0 = float((doorData >> 10) & 0x1F) / 16.0f;        float z1 = float((doorData >> 15) & 0x1F) / 16.0f;        bool isUpper = ((doorData >> 20) & 1) != 0;        float3 pLocal = localStart - float3(currentVoxel);        float tHit;        float stepMaxT = min(tMax.x, min(tMax.y, tMax.z));        if (intersectLocalAABB(pLocal, rayInv, float3(x0, 0.0f, z0), float3(x1, 1.0f, z1), tIn, stepMaxT, 100.0f, s, tHit)) {          float3 pHit = pLocal + dir * max(tHit, 0.0f);          bool isHole = false;          if (isUpper) {            float uHoriz = (abs(x1 - x0) > abs(z1 - z0)) ? saturate(pHit.x) : saturate(pHit.z);            float vVert = saturate(1.0f - pHit.y);            float alpha = sampleVoxelAlpha(blockAtlasTex, smp, blockUvTable, blockId, float2(uHoriz, vVert), 2);            if (alpha > 0.0f) {              isHole = (alpha < 0.25f);            } else {              bool uPane = (uHoriz >= 0.1875f && uHoriz <= 0.4375f) || (uHoriz >= 0.5625f && uHoriz <= 0.8125f);              bool vPane = (pHit.y >= 0.35f && pHit.y <= 0.55f) || (pHit.y >= 0.65f && pHit.y <= 0.88f);              isHole = uPane && vPane;            }          }          if (!isHole) {            tIn = max(tHit, tIn);            isSolidHit = true;          } else {            isSolidHit = false;          }        } else {          isSolidHit = false;        }      } else if (shapeId == 18) {        uint fenceData = (vox.y >> 18) & 0x0F;        float3 pLocal = localStart - float3(currentVoxel);        float tHit;        float stepMaxT = min(tMax.x, min(tMax.y, tMax.z));        bool hitFence = intersectLocalAABB(pLocal, rayInv, float3(0.375f, 0.0f, 0.375f), float3(0.625f, 1.0f, 0.625f), tIn, stepMaxT, 100.0f, s, tHit);        if (!hitFence && (fenceData & 1) != 0) {          hitFence = intersectLocalAABB(pLocal, rayInv, float3(0.4375f, 0.75f, 0.0f), float3(0.5625f, 0.9375f, 0.375f), tIn, stepMaxT, 100.0f, s, tHit)                  || intersectLocalAABB(pLocal, rayInv, float3(0.4375f, 0.375f, 0.0f), float3(0.5625f, 0.5625f, 0.375f), tIn, stepMaxT, 100.0f, s, tHit);        }        if (!hitFence && (fenceData & 2) != 0) {          hitFence = intersectLocalAABB(pLocal, rayInv, float3(0.4375f, 0.75f, 0.625f), float3(0.5625f, 0.9375f, 1.0f), tIn, stepMaxT, 100.0f, s, tHit)                  || intersectLocalAABB(pLocal, rayInv, float3(0.4375f, 0.375f, 0.625f), float3(0.5625f, 0.5625f, 1.0f), tIn, stepMaxT, 100.0f, s, tHit);        }        if (!hitFence && (fenceData & 4) != 0) {          hitFence = intersectLocalAABB(pLocal, rayInv, float3(0.0f, 0.75f, 0.4375f), float3(0.375f, 0.9375f, 0.5625f), tIn, stepMaxT, 100.0f, s, tHit)                  || intersectLocalAABB(pLocal, rayInv, float3(0.0f, 0.375f, 0.4375f), float3(0.375f, 0.5625f, 0.5625f), tIn, stepMaxT, 100.0f, s, tHit);        }        if (!hitFence && (fenceData & 8) != 0) {          hitFence = intersectLocalAABB(pLocal, rayInv, float3(0.625f, 0.75f, 0.4375f), float3(1.0f, 0.9375f, 0.5625f), tIn, stepMaxT, 100.0f, s, tHit)                  || intersectLocalAABB(pLocal, rayInv, float3(0.625f, 0.375f, 0.4375f), float3(1.0f, 0.5625f, 0.5625f), tIn, stepMaxT, 100.0f, s, tHit);        }        if (hitFence) {          tIn = max(tHit, tIn);          isSolidHit = true;          hasCustomNormal = true;          float3 pBoxHit = pLocal + dir * max(tHit, 0.0f) - float3(0.5f, 0.5f, 0.5f);          float3 absBox = abs(pBoxHit);          if (absBox.y >= absBox.x && absBox.y >= absBox.z) {            customNormal = float3(0.0f, sign(pBoxHit.y), 0.0f);          } else if (absBox.x >= absBox.z) {            customNormal = float3(sign(pBoxHit.x), 0.0f, 0.0f);          } else {            customNormal = float3(0.0f, 0.0f, sign(pBoxHit.z));          }          if (dot(customNormal, dir) > 0.0f) customNormal = -customNormal;        } else {          isSolidHit = false;        }      } else if (shapeId == 5) {        uint stairData = (vox.y >> 18) & 0x3F;        uint sHalf = stairData & 1u;        uint sFacing = (stairData >> 1) & 3u;        uint sShape = (stairData >> 3) & 7u;        float3 pLocal = localStart - float3(currentVoxel);        float tHit;        float stepMaxT = min(tMax.x, min(tMax.y, tMax.z));        float y0 = (sHalf == 0u) ? 0.0f : 0.5f;        float y1 = (sHalf == 0u) ? 0.5f : 1.0f;        bool hitStair = intersectLocalAABB(pLocal, rayInv, float3(0.0f, y0, 0.0f), float3(1.0f, y1, 1.0f), tIn, stepMaxT, 100.0f, s, tHit);        float sy0 = (sHalf == 0u) ? 0.5f : 0.0f;        float sy1 = (sHalf == 0u) ? 1.0f : 0.5f;        float4 mStep = float4(0.0f, 0.0f, 1.0f, 1.0f);        float4 extraCorner = float4(-1.0f);        if (sFacing == 0u) {          if (sShape == 0u) mStep = float4(0.0f, 0.0f, 1.0f, 0.5f);          else if (sShape == 1u) { mStep = float4(0.0f, 0.0f, 1.0f, 0.5f); extraCorner = float4(0.0f, 0.5f, 0.5f, 1.0f); }          else if (sShape == 2u) { mStep = float4(0.0f, 0.0f, 1.0f, 0.5f); extraCorner = float4(0.5f, 0.5f, 1.0f, 1.0f); }          else if (sShape == 3u) mStep = float4(0.0f, 0.0f, 0.5f, 0.5f);          else if (sShape == 4u) mStep = float4(0.5f, 0.0f, 1.0f, 0.5f);        } else if (sFacing == 1u) {          if (sShape == 0u) mStep = float4(0.0f, 0.5f, 1.0f, 1.0f);          else if (sShape == 1u) { mStep = float4(0.0f, 0.5f, 1.0f, 1.0f); extraCorner = float4(0.5f, 0.0f, 1.0f, 0.5f); }          else if (sShape == 2u) { mStep = float4(0.0f, 0.5f, 1.0f, 1.0f); extraCorner = float4(0.0f, 0.0f, 0.5f, 0.5f); }          else if (sShape == 3u) mStep = float4(0.5f, 0.5f, 1.0f, 1.0f);          else if (sShape == 4u) mStep = float4(0.0f, 0.5f, 0.5f, 1.0f);        } else if (sFacing == 2u) {          if (sShape == 0u) mStep = float4(0.0f, 0.0f, 0.5f, 1.0f);          else if (sShape == 1u) { mStep = float4(0.0f, 0.0f, 0.5f, 1.0f); extraCorner = float4(0.5f, 0.5f, 1.0f, 1.0f); }          else if (sShape == 2u) { mStep = float4(0.0f, 0.0f, 0.5f, 1.0f); extraCorner = float4(0.5f, 0.0f, 1.0f, 0.5f); }          else if (sShape == 3u) mStep = float4(0.0f, 0.5f, 0.5f, 1.0f);          else if (sShape == 4u) mStep = float4(0.0f, 0.0f, 0.5f, 0.5f);        } else {          if (sShape == 0u) mStep = float4(0.5f, 0.0f, 1.0f, 1.0f);          else if (sShape == 1u) { mStep = float4(0.5f, 0.0f, 1.0f, 1.0f); extraCorner = float4(0.0f, 0.0f, 0.5f, 0.5f); }          else if (sShape == 2u) { mStep = float4(0.5f, 0.0f, 1.0f, 1.0f); extraCorner = float4(0.0f, 0.5f, 0.5f, 1.0f); }          else if (sShape == 3u) mStep = float4(0.5f, 0.0f, 1.0f, 0.5f);          else if (sShape == 4u) mStep = float4(0.5f, 0.5f, 1.0f, 1.0f);        }        if (!hitStair) {          hitStair = intersectLocalAABB(pLocal, rayInv, float3(mStep.x, sy0, mStep.y), float3(mStep.z, sy1, mStep.w), tIn, stepMaxT, 100.0f, s, tHit);        }        if (!hitStair && extraCorner.x >= 0.0f) {          hitStair = intersectLocalAABB(pLocal, rayInv, float3(extraCorner.x, sy0, extraCorner.y), float3(extraCorner.z, sy1, extraCorner.w), tIn, stepMaxT, 100.0f, s, tHit);        }        if (hitStair) {          tIn = max(tHit, tIn);          isSolidHit = true;          hasCustomNormal = true;          float3 pHit = pLocal + dir * max(tHit, 0.0f);          if (abs(pHit.y - y1) < 0.08f || abs(pHit.y - sy1) < 0.08f) {            customNormal = float3(0.0f, 1.0f, 0.0f);          } else {            customNormal = (sFacing == 0u) ? float3(0.0f, 0.0f, -1.0f) : ((sFacing == 1u) ? float3(0.0f, 0.0f, 1.0f) : ((sFacing == 2u) ? float3(-1.0f, 0.0f, 0.0f) : float3(1.0f, 0.0f, 0.0f)));            if (dot(customNormal, dir) > 0.0f) customNormal = -customNormal;          }        } else {          isSolidHit = true;          hasCustomNormal = true;          customNormal = float3(0.0f, 1.0f, 0.0f);        }      } else if (shapeId == 7) {        uint tData = (vox.y >> 18) & 0x3F;        bool tTop = (tData & 1u) != 0u;        bool tOpen = (tData & 2u) != 0u;        uint tFacing = (tData >> 2u) & 3u;        float3 bMin = float3(0.0f);        float3 bMax = float3(1.0f);        if (!tOpen) {          bMin = float3(0.0f, tTop ? 0.8125f : 0.0f, 0.0f);          bMax = float3(1.0f, tTop ? 1.0f : 0.1875f, 1.0f);        } else {          if (tFacing == 0u) { bMin = float3(0.0f, 0.0f, 0.8125f); bMax = float3(1.0f, 1.0f, 1.0f); }          else if (tFacing == 1u) { bMin = float3(0.0f, 0.0f, 0.0f); bMax = float3(1.0f, 1.0f, 0.1875f); }          else if (tFacing == 2u) { bMin = float3(0.8125f, 0.0f, 0.0f); bMax = float3(1.0f, 1.0f, 1.0f); }          else { bMin = float3(0.0f, 0.0f, 0.0f); bMax = float3(0.1875f, 1.0f, 1.0f); }        }        float3 pLocal = localStart - float3(currentVoxel);        float tHit;        float stepMaxT = min(tMax.x, min(tMax.y, tMax.z));        if (intersectLocalAABB(pLocal, rayInv, bMin, bMax, tIn, stepMaxT, 100.0f, s, tHit)) {          float3 pHit = pLocal + dir * max(tHit, 0.0f);          float2 trapUv = (!tOpen) ? float2(saturate(pHit.x), saturate(pHit.z))                                   : ((tFacing < 2u) ? float2(saturate(pHit.x), saturate(1.0f - pHit.y))                                                      : float2(saturate(pHit.z), saturate(1.0f - pHit.y)));          float alpha = sampleVoxelAlpha(blockAtlasTex, smp, blockUvTable, blockId, trapUv, 2);          if (alpha >= 0.25f || alpha < 0.0f) {            tIn = max(tHit, tIn);            isSolidHit = true;            isTrapdoorHit = true;            trapHitUv = trapUv;            trapHitNorm = (!tOpen) ? (dir.y < 0.0f ? float3(0.0f, 1.0f, 0.0f) : float3(0.0f, -1.0f, 0.0f))                                   : ((tFacing == 0u) ? float3(0.0f, 0.0f, -1.0f) : ((tFacing == 1u) ? float3(0.0f, 0.0f, 1.0f) : ((tFacing == 2u) ? float3(-1.0f, 0.0f, 0.0f) : float3(1.0f, 0.0f, 0.0f))));            if (dot(trapHitNorm, dir) > 0.0f) trapHitNorm = -trapHitNorm;            hasCustomNormal = true;            customNormal = trapHitNorm;          } else {            isSolidHit = false;          }        } else {          isSolidHit = false;        }      } else if (shapeId != 0 && bitmaskTable != nullptr && blockId < 32768u) {        ulong mask = bitmaskTable[blockId];        if (mask != 0ULL) {          float nextT = min(tMax.x, min(tMax.y, tMax.z));          float3 pIn = clamp(localStart + dir * max(tIn, 0.02f) - float3(currentVoxel), 0.0f, 0.999f);          float3 pOut = clamp(localStart + dir * nextT - float3(currentVoxel), 0.0f, 0.999f);          bool hitSub = false;          for (float st = 0.05f; st <= 0.95f; st += 0.12f) {            float3 pSample = mix(pIn, pOut, st);            int3 sub = clamp(int3(floor(pSample * 4.0f)), int3(0), int3(3));            uint bitIdx = uint((sub.z * 4 + sub.y) * 4 + sub.x);            if (((mask >> bitIdx) & 1ULL) != 0ULL) {              hitSub = true;              break;            }          }          if (!hitSub) {            isSolidHit = false;          }        } else {          isSolidHit = false;        }      }      if (isSolidHit) { if (ph.hitDist < tIn) { return VoxelReflResult{ ph.color, ph.hitDist, 1.0f, ph.normal, 0.0f }; }        float3 hitNormal = hasCustomNormal ? customNormal : ((s > 0) ? float3(prevVoxel - currentVoxel) : -stepDir);        if (dot(hitNormal, dir) > 0.0f) hitNormal = -hitNormal;        float3 hitWorldPos = rayStart + dir * tIn;        float3 voxColor = unpackVoxelColor(vox);        bool isTinted = ((vox.x & 2) != 0) || ((vox.x & 4) != 0);        uint forceShape = (vox.y >> 24) & 0xFF;        if (forceShape == 11) isTinted = true;         if (isCrossHit) {          float4 uvBounds = (blockUvTable != nullptr && blockId < 32768u) ? blockUvTable[blockId * 3 + 2] : float4(0.0f);          if (uvBounds.z > uvBounds.x && uvBounds.w > uvBounds.y) {            float4 sampleCol = blockAtlasTex.sample(smp, mix(uvBounds.xy, uvBounds.zw, crossHitUv));            if (isTinted) {              voxColor = voxColor * sampleCol.rgb;            } else {              voxColor = sampleCol.rgb;            }          }        } else if (isTrapdoorHit) {          float4 uvBounds = (blockUvTable != nullptr && blockId < 32768u) ? blockUvTable[blockId * 3 + 0] : float4(0.0f);          if (uvBounds.z > uvBounds.x && uvBounds.w > uvBounds.y) {            float4 sampleCol = blockAtlasTex.sample(smp, mix(uvBounds.xy, uvBounds.zw, trapHitUv));            if (sampleCol.a >= 0.15f) voxColor = sampleCol.rgb;          }        } else if (shapeId == 16) {          voxColor = float3(0.55f, 0.35f, 0.15f);        } else {          voxColor = sampleVoxelTexture(blockAtlasTex, smp, blockUvTable, blockId, isTinted, (shapeId == 0 || shapeId == 19), hitWorldPos, hitNormal, voxColor);          if (((vox.x >> 12) & 0x0F) == 1u) {            float3 pLocal = clamp(localStart + dir * max(tIn, 0.001f) - float3(currentVoxel), 0.0f, 1.0f);            FaceUvResult res_faceUv = getVoxelFaceUv(pLocal);            float2 faceUv = res_faceUv.uv;            float4 uvBounds = (blockUvTable != nullptr && blockId < 32768u) ? blockUvTable[blockId * 3 + res_faceUv.index] : float4(0.0f);            if (uvBounds.z > uvBounds.x && uvBounds.w > uvBounds.y) {              float4 sampleCol = blockAtlasTex.sample(smp, mix(uvBounds.xy, uvBounds.zw, faceUv));              if (sampleCol.a > 0.15f) {                voxColor = mix(voxColor, sampleCol.rgb, 0.75f);              } else {                voxColor = mix(voxColor * 0.45f, float3(0.70f, 0.85f, 0.95f), 0.25f);              }            }          }        }        if (length_squared(voxColor) < 0.001f) {          voxColor = ((vox.x & 8) != 0) ? float3(1.0f, 0.50f, 0.12f) : ((vox.x & 2) != 0 ? float3(0.22f, 0.38f, 0.16f) : float3(0.48f, 0.48f, 0.50f));        }        int3 airVoxel = (s > 0) ? prevVoxel : (currentVoxel - int3(stepDir));        uint2 airVox = readVoxelLocal(voxelGrid, size.xyz, airVoxel);        float2 voxLight = max(unpackVoxelLight(vox), unpackVoxelLight(airVox));        float skyLevel = get_vanilla_brightness(voxLight.y);        float blockLevel = get_vanilla_brightness(voxLight.x);        float celestialNdotL = saturate(dot(hitNormal, celestialDir));        float3 celestialDirectCol = (sunWeight > 0.5f) ? (currentSunColor * 1.30f) : (currentMoonColor * 0.80f);        float celestialShadow = (voxLight.y > 0.80f || reflectionDirShadows < 0.5f) ? 1.0f : 0.0f; float3 celestialTint = float3(1.0f); if (celestialNdotL > 0.01f && celestialDir.y > 0.0f && voxLight.y > 0.80f && reflectionDirShadows > 0.5f) {            if (reflectionDirShadows > 0.5f) {                float celestialSlopeBias = mix(0.045f, 0.012f, celestialNdotL);                float3 sRayStart = hitWorldPos + hitNormal * celestialSlopeBias;                float3 shadowTarget = sRayStart + celestialDir * 32.0f;                ShadowRayResult cRes = traceDdaShadowRay(voxelGrid, origin.xyz, size.xyz, sRayStart, shadowTarget, blockAtlasTex, smp, blockUvTable, bitmaskTable);                celestialShadow = cRes.vis;                celestialTint = cRes.tint;            }        }        float3 ambientSky = max(activeSkyLight * (skyLevel * 0.68f), float3(0.03f, 0.025f, 0.02f)) * mix(0.50f, 1.0f, celestialShadow);        float3 directCelestial = celestialDirectCol * (celestialNdotL * skyLevel * 0.80f * celestialShadow) * celestialTint;        float3 skyLight = ambientSky + directCelestial;        float3 pointLightTotal = float3(0.0f);        int testCount = min(lightCount, (int)maxPointLights);        for (int i = 0; i < testCount; i++) {          float3 toL = lights[i].posAndRadius.xyz - hitWorldPos;          float dL = length(toL);          float lRad = lights[i].posAndRadius.w;          if (dL < lRad && dL > 0.05f) {            float3 lDir = toL / dL;            float NdotL = saturate(dot(hitNormal, lDir));            if (NdotL > 0.01f) {              float ptShadow = 1.0f;              float3 ptTint = float3(1.0f);              if (reflectionPtShadows > 0.5f) {                  float3 ptRayStart = hitWorldPos + hitNormal * 0.035f;                  ShadowRayResult ptRes = traceDdaShadowRay(voxelGrid, origin.xyz, size.xyz, ptRayStart, lights[i].posAndRadius.xyz, blockAtlasTex, smp, blockUvTable, bitmaskTable);                  ptShadow = ptRes.vis;                  ptTint = ptRes.tint;              }              if (ptShadow > 0.01f) {                float distNorm = dL / lRad;                float window = saturate(1.0f - distNorm);                float smoothWin = window * window * (3.0f - 2.0f * window);                float falloff = smoothWin / (dL * dL * 0.12f + dL * 0.35f + 0.80f);                pointLightTotal += lights[i].colorAndIntensity.xyz * (lights[i].colorAndIntensity.w * falloff * NdotL * ptShadow) * ptTint;              }            }          }        }        float dayDampen = mix(1.0f, 0.22f, sunWeight * skyLevel);        float3 scaledPt = pointLightTotal * dayDampen;        float3 smoothPointLights = scaledPt / (1.0f + scaledPt * 0.35f);        float3 warmTorchTint = mix(float3(1.0f, 0.55f, 0.18f), float3(1.0f, 0.88f, 0.72f), smoothstep(0.0f, 1.0f, voxLight.x));        float3 fallbackBlockLight = warmTorchTint * blockLevel;        float fallbackWeight = saturate(1.0f - length(smoothPointLights) * 1.5f) * saturate(1.0f - skyLevel * 0.85f);        float3 totalBlockLight = smoothPointLights + fallbackBlockLight * fallbackWeight;        float minAmbient = mix(0.015f, 0.03f, sunWeight);        float vxao = 0.0f;        if (vxaoInReflections > 0.5f) {            vxao = computeVXAO(voxelGrid, origin.xyz, size.xyz, hitWorldPos, hitNormal, hitWorldPos, 4, hitWorldPos.xz * 128.0f);        }        float daylightShadowSuppression = mix(1.0f, 0.30f, sunWeight * skyLevel);        float shadowOcclusion = saturate(vxao * 1.55f * daylightShadowSuppression);
        shadowOcclusion = mix(shadowOcclusion, 0.0f, rainStrength * 0.85f);                 float3 shadowedSkyLight = skyLight * (1.0f - shadowOcclusion);        float3 vLight = shadowedSkyLight + totalBlockLight + float3(minAmbient);
        if (rainStrength > 0.01f && hitNormal.y > 0.82f && skyLevel > 0.95f) {
            float puddleNoise = smoothNoise3D(float3(hitWorldPos.xz * 0.35f, 0.0f));
            float puddleMask = smoothstep(0.40f, 0.65f, puddleNoise) * saturate(rainStrength * 1.5f);
            if (puddleMask > 0.15f) {
               float2 rippleUv = hitWorldPos.xz * 1.5f;
               float rip1 = sin(length(fract(rippleUv) - 0.5f) * 24.0f - gameTime * 18.0f);
               float rip2 = sin(length(fract(rippleUv + 0.43f) - 0.5f) * 20.0f - gameTime * 14.0f);
               float rippleMask = smoothstep(0.3f, 0.7f, smoothNoise3D(float3(hitWorldPos.xz * 1.2f, 0.0f))); 
               float2 rippleOffset = float2(rip1 + rip2) * 0.045f * puddleMask * rainStrength * rippleMask;
               float3 newNormal = normalize(float3(hitNormal.x + rippleOffset.x, hitNormal.y, hitNormal.z + rippleOffset.y));
               float3 secondaryRay = reflect(rWorld, newNormal);
               float3 skyCol = activeSkyLight;
               if (sunWeight > 0.5f && dot(secondaryRay, celestialDir) > 0.95f) {
                   skyCol += currentSunColor * 1.5f;
               }
               return VoxelReflResult{ skyCol, tIn, 1.0f, newNormal, 0.85f };
            }
        }        if ((vox.x & 8) != 0) {          vLight = max(vLight, float3(1.30f));        }        float fade = (s >= (maxSteps - 3)) ? saturate(float(maxSteps - 1 - s) / 2.0f) : 1.0f;        uint rType = (vox.x >> 12) & 0x0F;
        float rFactor = (rType == 2u) ? 0.85f : ((rType == 1u) ? 0.15f : 0.0f);
        return VoxelReflResult{ voxColor * vLight, tIn, fade, hitNormal, rFactor };      }    }    prevVoxel = currentVoxel;    tIn = min(tMax.x, min(tMax.y, tMax.z));    if (tIn > 56.0f) break;    if (tMax.x < tMax.y) {      if (tMax.x < tMax.z) {        currentVoxel.x += int(stepDir.x);        tMax.x += tDelta.x;      } else {        currentVoxel.z += int(stepDir.z);        tMax.z += tDelta.z;      }    } else {      if (tMax.y < tMax.z) {        currentVoxel.y += int(stepDir.y);        tMax.y += tDelta.y;      } else {        currentVoxel.z += int(stepDir.z);        tMax.z += tDelta.z;      }    }  } if (ph.hitDist < 1e6f) { return VoxelReflResult{ ph.color, ph.hitDist, 1.0f, ph.normal, 0.0f }; } return VoxelReflResult{ float3(0.0f), 1e6f, 0.0f, float3(0.0f), 0.0f }; }static inline float3 evaluateSkyAndReflections(  float3 pWorld,  float3 rWorld,  float gameTime,  float3 actualSkyColor,  float3 sunriseTint,  float sunriseFactor,  float3 currentSunColor,  float3 currentMoonColor,  float sunWeight,  float3 sunDir,  float3 moonDir,  float starBrightness, float cloudsEnabled, float cloudSteps, float rainStrength, float maxDist) {  float cosViewZenith = saturate(rWorld.y);  float opticalMass = 1.0f / max(cosViewZenith + 0.08f, 0.05f);  float cosSunTheta = dot(rWorld, sunDir);  float cosMoonTheta = dot(rWorld, moonDir);  float rayleighPhase = 0.75f * (1.0f + cosSunTheta * cosSunTheta);      float3 rayleighSky = float3(0.18f, 0.28f, 0.42f) * rayleighPhase;  float g = 0.78f;  float g2 = g * g;  float miePhase = (1.0f - g2) / pow(max(1.0f + g2 - 2.0f * g * cosSunTheta, 0.01f), 1.5f);  float3 mieColor = mix(float3(0.92f, 0.95f, 1.00f), float3(1.8f, 0.5f, 0.1f), max(sunriseFactor * 1.5f, 0.20f));  float3 mieInscattering = mieColor * (miePhase * 0.045f * sunWeight);  float horizonHaze = saturate(1.0f - exp(-opticalMass * 0.18f));  float3 hazeColor = mix(float3(0.48f, 0.58f, 0.66f), sunriseTint * 1.15f, sunriseFactor);  if (sunWeight < 0.35f) {    hazeColor = mix(float3(0.12f, 0.16f, 0.26f), hazeColor, sunWeight * 2.85f);  }  float3 daySky = mix(rayleighSky + mieInscattering, hazeColor, horizonHaze * 0.45f);  if (sunriseFactor > 0.01f) {    float sunsetBelt = pow(saturate(1.0f - abs(rWorld.y - 0.05f) * 4.0f), 2.0f);    float sunForward = pow(max(cosSunTheta, 0.0f), 2.2f);    float3 sunsetGlow = sunriseTint * (sunsetBelt * sunForward * 2.5f * sunriseFactor);    daySky += sunsetGlow;  }  float3 overcastSky = float3(0.35f, 0.40f, 0.46f);  daySky = mix(daySky, overcastSky, rainStrength * 0.70f);  float3 nightZenith = float3(0.04f, 0.07f, 0.14f);  float3 nightHorizon = float3(0.09f, 0.13f, 0.22f);  float3 nightSky = mix(nightHorizon, nightZenith, pow(cosViewZenith, 0.65f));  float moonMiePhase = (1.0f - 0.55f * 0.55f) / pow(max(1.0f + 0.30f - 1.1f * cosMoonTheta, 0.01f), 1.5f);  nightSky += currentMoonColor * (moonMiePhase * 0.025f * (1.0f - sunWeight));  float3 finalSky = mix(nightSky, daySky, sunWeight);  if (sunWeight > 0.05f && cosSunTheta > 0.0f) {    float3 pProj = rWorld / max(cosSunTheta, 0.001f);    float3 sUp = normalize(cross(sunDir, abs(sunDir.y) > 0.99f ? float3(1, 0, 0) : float3(0, 1, 0)));    float3 sRight = cross(sunDir, sUp);    float uSun = dot(pProj - sunDir, sRight);    float vSun = dot(pProj - sunDir, sUp);    float sunRadius = 0.042f;    float dSun = length(float2(uSun, vSun));    float limbDarkening = 0.0f;    if (dSun < sunRadius) {      limbDarkening = pow(saturate(1.0f - dSun / sunRadius), 0.45f);    }    float sunCorona = pow(saturate(1.0f - dSun / 0.32f), 3.5f) * 1.25f;    finalSky += currentSunColor * ((6.5f * limbDarkening + sunCorona) * sunWeight * (1.0f - rainStrength * 0.85f));  }  if (sunWeight < 0.85f && cosMoonTheta > 0.0f) {    float3 pProj = rWorld / max(cosMoonTheta, 0.001f);    float3 mUp = normalize(cross(moonDir, abs(moonDir.y) > 0.99f ? float3(1, 0, 0) : float3(0, 1, 0)));    float3 mRight = cross(moonDir, mUp);    float uMoon = dot(pProj - moonDir, mRight);    float vMoon = dot(pProj - moonDir, mUp);    float moonRadius = 0.036f;    float dMoon = length(float2(uMoon, vMoon));    float limbFade = 0.0f;    if (dMoon < moonRadius) {      limbFade = smoothstep(moonRadius, moonRadius * 0.90f, dMoon);    }    float moonCorona = pow(saturate(1.0f - dMoon / 0.22f), 3.0f) * 0.65f;    finalSky += currentMoonColor * ((4.2f * limbFade + moonCorona) * (1.0f - sunWeight) * (1.0f - rainStrength * 0.85f));  }    float4 cloudData = computeVolumetricClouds(pWorld, rWorld, gameTime, hazeColor, sunWeight, sunDir, moonDir, currentSunColor, currentMoonColor, cloudsEnabled, cloudSteps, rainStrength, maxDist);
finalSky = finalSky * cloudData.a + cloudData.rgb;
return finalSky;}