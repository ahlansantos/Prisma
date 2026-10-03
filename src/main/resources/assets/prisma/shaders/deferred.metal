#include <metal_stdlib>
using namespace metal;

#include "prisma_api.metal"
#include "voxel_common.metal"
#include "lib_tonemapping.metal"
#include "lib_water.metal"
#include "lib_lighting.metal"
#include "lib_volumetrics.metal"

            struct DeferredVertexOut {
              float4 position [[position]];
              float2 uv;
            };

            

                        
// --- Analytical Point Lights (replaced ReSTIR) ---

              vertex DeferredVertexOut prisma_deferred_vs(uint vertexId [[vertex_id]]) {
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

              DeferredVertexOut out;
              out.position = float4(positions[vertexId], 0.0, 1.0);
              out.uv = uvs[vertexId];
              return out;
            }

            

kernel void prisma_deferred_cs(
              uint2 gid [[thread_position_in_grid]],
              texture2d<float, access::read_write> outTexture [[texture(10)]],
              texture2d<float> albedoTex [[texture(0)]],
              texture2d<float> normalTex [[texture(1)]],
              texture2d<float> lightDataTex [[texture(2)]],
              depth2d<float> worldDepthTex [[texture(3)]],
              depth2d<float> handDepthTex [[texture(4)]],
              texture2d<float> blockAtlasTex [[texture(5)]],
              texture2d<float> playerSkinTex [[texture(6)]],
              texture2d<uint, access::read> prevReservoirTex [[texture(7)]],
              texture2d<uint, access::write> currReservoirTex [[texture(8)]],
              texture2d<float, access::write> velocityTex [[texture(9)]],
              texture2d<float> giTexture [[texture(11)]],
              texture2d<float> volumetricsTexture [[texture(12)]],
              sampler smp [[sampler(0)]],
              constant CameraData& camera [[buffer(10)]],
              constant EnvironmentData& env [[buffer(11)]],
              constant RenderSettings& settings [[buffer(12)]],
              device const uint2* voxelGrid [[buffer(1)]],
              constant VoxelUniforms& uVoxel [[buffer(2)]],
              constant float4* blockUvTable [[buffer(3)]],
              constant ulong* bitmaskTable [[buffer(4)]]
            ) {
    if (gid.x >= outTexture.get_width() || gid.y >= outTexture.get_height()) return;
    float2 uv = (float2(gid) + 0.5f) / float2(outTexture.get_width(), outTexture.get_height());
    uint2 depthGid = uint2(uv * float2(worldDepthTex.get_width(), worldDepthTex.get_height()));
              float4 albedo = albedoTex.sample(smp, uv);
              float wDepth = worldDepthTex.read(depthGid);
              float hDepth = handDepthTex.read(depthGid);
              // In Reverse-Z: larger value = closer. Use the closer depth (larger value).
              float rawDepth = max(wDepth, hDepth);
              if (settings.waterOnlyPass > 0.5f && rawDepth <= 0.00005f) {
                outTexture.write(float4(albedo.rgb, albedo.a), gid);
                return;
              }
              bool isHandPixel = (hDepth > wDepth + 0.0001f && hDepth > 0.0001f);
              float effectiveDepth = rawDepth;
              

              float sunTheta = env.sunAngle;
              float3 sunDir = normalize(float3(-sin(sunTheta), cos(sunTheta), 0.0f));
              float3 moonDir = -sunDir;
              float sunElevation = sunDir.y;
              float sunWeight = smoothstep(-0.08f, 0.04f, sunElevation);
              float sunsetFactor = (1.0f - smoothstep(0.02f, 0.32f, abs(sunElevation))) * smoothstep(-0.06f, 0.08f, sunElevation);
              float clampedSunrise = max(0.0f, env.sunriseAlpha);
              bool isNether = env.sunriseAlpha < -0.5f && env.sunriseAlpha > -1.5f;
              bool isEnd = env.sunriseAlpha < -1.5f;

              // --- Golden Hour Sun Color (2200K sunrise/sunset -> 5500K noon) ---
              // Noon: crisp slightly warm white. Sunrise/sunset: rich amber/orange cinematico.
              float3 noonSunColor   = float3(1.05f, 0.98f, 0.88f);
              // Deep golden hour: more saturated amber-red at very low sun angles
              float goldenBoost     = smoothstep(0.30f, 0.0f, abs(sunElevation)); // 1.0 near horizon
              float3 goldenHourColor = mix(float3(1.55f, 0.72f, 0.22f),  // warm amber
                                          float3(1.70f, 0.45f, 0.12f),  // deep orange-red at horizon
                                          goldenBoost * 0.6f);
              float3 currentSunColor = mix(noonSunColor, goldenHourColor, max(sunsetFactor, clampedSunrise)) * (1.0f - env.rainStrength * 0.98f);
              float3 currentMoonColor = float3(0.22f, 0.32f, 0.52f) * (1.0f - env.rainStrength * 0.95f);
              float3 celestialDir = sunWeight > 0.5f ? sunDir : moonDir;

              float3 actualSky = float3(env.skyR, env.skyG, env.skyB);
              float3 sunriseTint = float3(env.sunriseR, env.sunriseG, env.sunriseB);
              // Cooler, more balanced daySkyLight so ambient doesn't blow out blocks
              float3 daySkyLight = mix(float3(0.92f, 0.92f, 0.90f), sunriseTint * 1.10f, clampedSunrise);
              float3 nightSkyLight = float3(0.06f, 0.11f, 0.28f);
              float3 activeSkyLight = mix(nightSkyLight, daySkyLight, sunWeight) * (1.0f - env.rainStrength * 0.65f);

              if (effectiveDepth <= 0.00005f && hDepth <= 0.0001f) {
                if (isNether || isEnd) {
                    outTexture.write(float4(albedo.rgb, albedo.a), gid); return; // Let vanilla handle Nether and End skies
                }
                float2 skyNdc = float2(uv.x * 2.0f - 1.0f, uv.y * 2.0f - 1.0f);
                float4 nearPoint = uVoxel.invViewProj * float4(skyNdc, 1.0f, 1.0f);
                float4 farPoint = uVoxel.invViewProj * float4(skyNdc, 0.001f, 1.0f);
                float3 pNear = nearPoint.xyz / max(nearPoint.w, 0.00001f);
                float3 pFar = farPoint.xyz / max(farPoint.w, 0.00001f);
                float3 rayDir = normalize(pFar - pNear);

                float3 skyCol = evaluateSkyAndReflections(uVoxel.camPos.xyz, rayDir, camera.gameTime, actualSky, sunriseTint, clampedSunrise, currentSunColor, currentMoonColor, sunWeight, sunDir, moonDir, env.starBrightness, settings.cloudsEnabled, settings.cloudSteps, env.rainStrength, 1e6f);
                float luma = dot(albedo.rgb, float3(0.299f, 0.587f, 0.114f)); float isRain = saturate((luma - 0.2f) * 10.0f) * env.rainStrength; outTexture.write(float4(mix(skyCol, albedo.rgb, isRain * 0.6f), 1.0f), gid); return;
              }


              if (isHandPixel) {
                outTexture.write(float4(albedo.rgb, albedo.a), gid); return;
              }

              float2 depthTexSize = float2(worldDepthTex.get_width(), worldDepthTex.get_height());
              float2 depthTexel = 1.0f / depthTexSize;
              uint2 maxDepthGid = uint2(worldDepthTex.get_width() - 1, worldDepthTex.get_height() - 1);
              depthGid = min(depthGid, maxDepthGid);

              float2 depthUv = (float2(depthGid) + 0.5f) * depthTexel;
              float3 pWorld = reconstructWorldPos(depthUv, effectiveDepth, uVoxel.camPos.xyz, uVoxel.invViewProj);

              // Sanity: if pWorld is more than 1000 blocks away, reconstruction failed -> pass-through
              {
                float3 delta = pWorld - uVoxel.camPos.xyz;
                if (dot(delta, delta) > 1000.0f * 1000.0f) {
                  outTexture.write(float4(albedo.rgb, albedo.a), gid); return;
                }
              }

              float depthL = worldDepthTex.read(uint2(max(int(depthGid.x) - 1, 0), depthGid.y));
              float depthR = worldDepthTex.read(uint2(min(depthGid.x + 1, maxDepthGid.x), depthGid.y));
              float depthU = worldDepthTex.read(uint2(depthGid.x, max(int(depthGid.y) - 1, 0)));
              float depthD = worldDepthTex.read(uint2(depthGid.x, min(depthGid.y + 1, maxDepthGid.y)));

              float diffL = (depthL > 0.00005f) ? abs(effectiveDepth - depthL) : 1e6f;
              float diffR = (depthR > 0.00005f) ? abs(effectiveDepth - depthR) : 1e6f;
              float diffU = (depthU > 0.00005f) ? abs(effectiveDepth - depthU) : 1e6f;
              float diffD = (depthD > 0.00005f) ? abs(effectiveDepth - depthD) : 1e6f;

              float3 pL = reconstructWorldPos(depthUv - float2(depthTexel.x, 0.0f), depthL, uVoxel.camPos.xyz, uVoxel.invViewProj);
              float3 pR = reconstructWorldPos(depthUv + float2(depthTexel.x, 0.0f), depthR, uVoxel.camPos.xyz, uVoxel.invViewProj);
              float3 pU = reconstructWorldPos(depthUv - float2(0.0f, depthTexel.y), depthU, uVoxel.camPos.xyz, uVoxel.invViewProj);
              float3 pD = reconstructWorldPos(depthUv + float2(0.0f, depthTexel.y), depthD, uVoxel.camPos.xyz, uVoxel.invViewProj);

              float3 dX = (diffL < diffR) ? (pWorld - pL) : (pR - pWorld);
              float3 dY = (diffU < diffD) ? (pWorld - pU) : (pD - pWorld);
              float3 crossDir = cross(dY, dX);
              float crossLen = dot(crossDir, crossDir);

              float2 ndcTrue = float2(uv.x * 2.0f - 1.0f, uv.y * 2.0f - 1.0f);
              float4 nearP = uVoxel.invViewProj * float4(ndcTrue, 1.0f, 1.0f);
              float4 farP = uVoxel.invViewProj * float4(ndcTrue, 0.0f, 1.0f);
              float3 trueRayDir = normalize(farP.xyz / max(farP.w, 1e-5f) - nearP.xyz / max(nearP.w, 1e-5f));
              float3 viewDirCam = -trueRayDir;

              float3 pLocal = pWorld - floor(pWorld);
              float3 distMin = pLocal;
              float3 distMax = 1.0f - pLocal;

              // Nearest boundary on unit cube: compare closest distances directly
              float3 blockNormal = float3(0.0f, 1.0f, 0.0f);
              float minDist = 1000.0f;
              if (distMin.y < minDist && viewDirCam.y < -0.01f) { minDist = distMin.y; blockNormal = float3(0.0f, -1.0f, 0.0f); }
              if (distMax.y < minDist && viewDirCam.y >  0.01f) { minDist = distMax.y; blockNormal = float3(0.0f, 1.0f, 0.0f); }
              if (distMin.x < minDist && abs(viewDirCam.x) > 0.01f) { minDist = distMin.x; blockNormal = float3(-1.0f, 0.0f, 0.0f); }
              if (distMax.x < minDist && abs(viewDirCam.x) > 0.01f) { minDist = distMax.x; blockNormal = float3(1.0f, 0.0f, 0.0f); }
              if (distMin.z < minDist && abs(viewDirCam.z) > 0.01f) { minDist = distMin.z; blockNormal = float3(0.0f, 0.0f, -1.0f); }
              if (distMax.z < minDist && abs(viewDirCam.z) > 0.01f) { minDist = distMax.z; blockNormal = float3(0.0f, 0.0f, 1.0f); }

              // Adaptive depth derivative threshold scaled by resolution footprint:
              // At <= 1050p, each depth texel spans more world space.
              // Scaling the threshold prevents false-positive edge detection on slopes and flat block surfaces.
              float distCam = length(pWorld - uVoxel.camPos.xyz);
              float texelFootprint = max(0.06f, distCam * depthTexel.y * 2.2f);
              float derivThreshold = max(0.40f, texelFootprint * texelFootprint * 1.6f);

              bool badX = dot(dX, dX) > derivThreshold;
              bool badY = dot(dY, dY) > derivThreshold;
              bool badDerivative = badX && badY;

              float3 geomNormal = (crossLen > 1e-20f && !badDerivative) ? normalize(crossDir) : blockNormal;
              if (dot(geomNormal, pWorld - uVoxel.camPos.xyz) > 0.0f) {
                  geomNormal = -geomNormal;
              }

              float3 nWorld = geomNormal;
              float3 absN = abs(geomNormal);
              if (absN.y >= absN.x && absN.y >= absN.z) {
                  nWorld = float3(0.0f, sign(geomNormal.y), 0.0f);
              } else if (absN.x >= absN.z) {
                  nWorld = float3(sign(geomNormal.x), 0.0f, 0.0f);
              } else {
                  nWorld = float3(0.0f, 0.0f, sign(geomNormal.z));
              }

              float3 gridMin = float3(uVoxel.gridOrigin.xyz);
              float3 gridMax = gridMin + float3(uVoxel.gridSize.xyz);
              float distToGridEdge = min(
                  min(pWorld.x - gridMin.x, gridMax.x - pWorld.x),
                  min(min(pWorld.y - gridMin.y, gridMax.y - pWorld.y),
                      min(pWorld.z - gridMin.z, gridMax.z - pWorld.z))
              );
              bool insideGrid = (distToGridEdge >= 0.0f);
              float gridWeight = saturate((distToGridEdge + 1.5f) / 3.0f);

              int3 currVoxPos = int3(floor(pWorld));
              float3 viewDirToBlock = normalize(pWorld - uVoxel.camPos.xyz);
              int3 insideVoxPos1 = int3(floor(pWorld - nWorld * 0.15f));
              int3 insideVoxPos2 = int3(floor(pWorld + viewDirToBlock * 0.12f));

              uint2 voxCurr = (distToGridEdge > -2.0f) ? readVoxelLocal(voxelGrid, uVoxel.gridSize.xyz, clamp(currVoxPos - uVoxel.gridOrigin.xyz, int3(0), uVoxel.gridSize.xyz - int3(1))) : uint2(0, 0);
              uint2 voxIn1  = (distToGridEdge > -2.0f) ? readVoxelLocal(voxelGrid, uVoxel.gridSize.xyz, clamp(insideVoxPos1 - uVoxel.gridOrigin.xyz, int3(0), uVoxel.gridSize.xyz - int3(1))) : uint2(0, 0);
              uint2 voxIn2  = (distToGridEdge > -2.0f) ? readVoxelLocal(voxelGrid, uVoxel.gridSize.xyz, clamp(insideVoxPos2 - uVoxel.gridOrigin.xyz, int3(0), uVoxel.gridSize.xyz - int3(1))) : uint2(0, 0);

              bool isCurrBlock = ((voxCurr.x & 7) != 0);
              bool isIn1Block  = ((voxIn1.x & 7) != 0);
              bool isIn2Block  = ((voxIn2.x & 7) != 0);

              bool isEntity = (gridWeight > 0.1f) && !isCurrBlock && !isIn1Block && !isIn2Block;
              
              uint2 insideVox = isIn1Block ? voxIn1 : (isIn2Block ? voxIn2 : voxCurr);
              uint2 currVox = voxCurr;

              bool isFoliage = (gridWeight > 0.1f) && (((insideVox.x & 2) != 0) || (((insideVox.y >> 24) & 0xFF) == 12) || ((currVox.x & 2) != 0) || (((currVox.y >> 24) & 0xFF) == 12));

              int3 camVoxel = int3(floor(uVoxel.camPos.xyz));
              uint2 camVox = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, camVoxel);
              bool isCameraInFluid = ((camVox.x & 4) != 0);
              if (isCameraInFluid) {
                uint2 aboveCamVox = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, camVoxel + int3(0, 1, 0));
                if ((aboveCamVox.x & 4) == 0) {
                  float surfaceY = float(camVoxel.y) + 0.88f;
                  if (uVoxel.camPos.y > surfaceY) {
                    isCameraInFluid = false;
                  }
                }
              }
              bool isCameraInLava = isCameraInFluid && ((camVox.x & 8) != 0);
              bool isCameraUnderwater = isCameraInFluid && !isCameraInLava;

              bool isWater = false;
              bool isMetal = false;
              bool isGlass = false;
              bool isPolished = false;
              float3 pSurfaceRel = pWorld - uVoxel.camPos.xyz;
              float distToSurface = length(pSurfaceRel);
              float3 viewDir = distToSurface > 0.001f ? (pSurfaceRel / distToSurface) : float3(0.0f, -1.0f, 0.0f);

              if (!isEntity && !isCameraInFluid) {
                if (settings.waterOnlyPass > 0.5f) {
                  // Push slightly into the block surface to avoid floating-point boundary noise (Z-fighting)
                  // We MUST use geomNormal (perfect cube normal), not nWorld (which can be bumped by textures)
                  float3 checkPt = pWorld - geomNormal * 0.05f;
                  int3 vPos = int3(floor(checkPt));
                  
                  uint2 vCur = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, vPos);
                  uint2 vBelow = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, vPos - int3(0, 1, 0));
                  uint2 vAbove = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, vPos + int3(0, 1, 0));
                  
                  bool isWaterCurrent = ((vCur.x & 4) != 0 && (vCur.x & 8) == 0);
                  bool isWaterBelow   = ((vBelow.x & 4) != 0 && (vBelow.x & 8) == 0);
                  bool isWaterAbove   = ((vAbove.x & 4) != 0 && (vAbove.x & 8) == 0);
                  bool isSolidBlock   = ((vCur.x & 1) != 0 && (vCur.x & 4) == 0);
                  
                  // 1. isWaterCurrent: perfect hit inside water volume
                  // 2. !isSolidBlock && isWaterBelow: precision pushed us UP into Air, water is below
                  // 3. isSolidBlock && isWaterAbove: precision pushed us DOWN into Seabed, water is above
                  if (isWaterCurrent || (!isSolidBlock && isWaterBelow) || (isSolidBlock && isWaterAbove)) {
                      isWater = true;
                  }
                }
                uint reflectType = (insideVox.x >> 12) & 0x0Fu;
                if (reflectType == 2u) {
                  isMetal = true;
                }
                if (reflectType == 1u) {
                  isGlass = true;
                }
                if (reflectType == 3u) {
                  isPolished = true;
                }
              }

              // In water-only pass, process water AND translucent glass reflections!
              if (settings.waterOnlyPass > 0.5f && !isWater && !isGlass) {
                outTexture.write(float4(albedo.rgb, albedo.a), gid);
                return;
              }

              
              float3 surfNormal = isEntity ? geomNormal : normalize(mix(geomNormal, nWorld, 0.70f));
              
              if (isWater) {

                if (abs(geomNormal.y) > 0.45f) {
                    float baseWaterY = (uVoxel.camPos.y >= pWorld.y) ? 1.0f : -1.0f;
                    float3 waveNorm = computeEclipseWaterWaves(pWorld.xz, camera.gameTime, settings.waterWaveStrength, settings.waterWaveSpeed);
                    surfNormal = normalize(float3(waveNorm.x, baseWaterY * waveNorm.y, waveNorm.z));
                    nWorld = float3(0.0f, baseWaterY, 0.0f);
                } else {
                    surfNormal = geomNormal;
                    nWorld = geomNormal;
                }
              }
              if (isGlass || isMetal) {
                if (abs(nWorld.y) > 0.65f) {
                  surfNormal = float3(0.0f, sign(nWorld.y), 0.0f);
                } else if (abs(nWorld.x) > 0.65f) {
                  surfNormal = float3(sign(nWorld.x), 0.0f, 0.0f);
                } else if (abs(nWorld.z) > 0.65f) {
                  surfNormal = float3(0.0f, 0.0f, sign(nWorld.z));
                }
              }

              float packedAoStrength = uVoxel.camPos.w;
              bool vxgiEnabled = packedAoStrength >= 5.0f;
              float doubleAoStrength = fmod(packedAoStrength, 10.0f);
              float doubleAo = (!isEntity && !isWater && !isGlass && gridWeight > 0.05f && doubleAoStrength > 0.01f) ? computeDoubleAO(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, pWorld, nWorld, uVoxel.camPos.xyz, uVoxel.gridOrigin.w, (float2(gid) + 0.5f), doubleAoStrength) * gridWeight : 0.0f;
              float ssao = 0.0f; // 100% removed as requested
              
              float2 smoothVoxLight = sampleSmoothVoxelLight(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, pWorld, nWorld);
              float outsideSky = (surfNormal.y > -0.2f ? 1.0f : 0.5f);
              float outsideBlock = 0.0f;
              float rawSky = mix(outsideSky, smoothVoxLight.y, gridWeight);
              float rawBlock = mix(outsideBlock, smoothVoxLight.x, gridWeight);
              if (isEntity) {

                  int3 localPos = int3(floor(pWorld - float3(uVoxel.gridOrigin.xyz)));
                  for (int yOffset = 0; yOffset <= 3; yOffset++) {
                      uint2 entVox = readVoxelLocal(voxelGrid, uVoxel.gridSize.xyz, localPos - int3(0, yOffset, 0));
                      float vSky = float((entVox.x >> 8) & 0x0F) / 15.0f;
                      float vBlock = float((entVox.x >> 4) & 0x0F) / 15.0f;
                      if (vSky > 0.0f || vBlock > 0.0f || (entVox.x & 1) != 0) {
                          rawSky = vSky;
                          rawBlock = vBlock;
                          break;
                      }
                  }
              }
              float skyLevel = get_vanilla_brightness(rawSky);
              float blockLevel = get_vanilla_brightness(rawBlock);

              float combinedAo = saturate(max(doubleAo, ssao) * 1.20f) * saturate(1.0f - blockLevel * blockLevel);
              if (isFoliage) {
                combinedAo *= 0.60f; // Soften AO for tree leaves and vegetation
              }
              float ao = saturate(1.0f - combinedAo);
              float volumetricAo = mix(0.04f, 1.0f, pow(ao, 1.8f));




              
              // Calculate motion vectors. If we are drawing water on top of a seabed pixel, 
              // we MUST calculate velocity using the water surface position, otherwise parallax 
              // mismatch ruins MetalFX temporal accumulation (causing ghosting/Z-fighting on shallow water).
              float3 motionWorld = pWorld;
              if (isWater && settings.waterOnlyPass > 0.5f) {
                  // We recalculate vPos because it's out of scope here
                  int3 mvPos = int3(floor(pWorld - geomNormal * 0.05f));
                  uint2 mvCur = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, mvPos);
                  bool mvIsSolid = ((mvCur.x & 1) != 0 && (mvCur.x & 4) == 0);
                  
                  if (mvIsSolid) {
                      float waterY = float(mvPos.y + 1);
                      float3 rayDir = normalize(pWorld - uVoxel.camPos.xyz);
                      if (abs(rayDir.y) > 0.001f) {
                          float t = (waterY - uVoxel.camPos.xyz.y) / rayDir.y;
                          if (t > 0.0f) {
                              motionWorld = uVoxel.camPos.xyz + rayDir * t;
                          }
                      }
                  }
              }

              float4 currentClip = uVoxel.viewProj * float4(motionWorld, 1.0f);
              float4 prevClip = uVoxel.prevViewProj * float4(motionWorld, 1.0f);
              float2 currentUv = float2(currentClip.x, -currentClip.y) / max(currentClip.w, 0.0001f) * 0.5f + 0.5f;
              float2 prevUv = float2(prevClip.x, -prevClip.y) / max(prevClip.w, 0.0001f) * 0.5f + 0.5f;
              // Motion vector from current to previous in pixel space
              float2 velocity = (prevUv - currentUv) * float2(float(velocityTex.get_width()), float(velocityTex.get_height()));
              velocityTex.write(float4(velocity, 0.0f, 0.0f), gid);

              // Write dummy reservoir (kept for compatibility)
              currReservoirTex.write(uint4(0, 0, 0, 0), gid);


              
              bool isFirstPerson = (length(uVoxel.camPos.xyz - (uVoxel.playerPos.xyz + float3(0.0f, 1.5f, 0.0f))) < 0.60f);
              bool isGlassSurface = ((insideVox.x & 1) != 0) && (((insideVox.x >> 12) & 0x0F) == 1u);
              
              // --- Analytical Point Lights (deterministic, no noise) ---
              float4 giData = giTexture.read(gid);
              float3 pointLights = giData.rgb;
              // giData.a is giData.a but we don't strictly need to assign it back if maxDarkening isn't heavily used.
              // Wait, we DO use maxDarkening implicitly? Actually we just need pointLights.
                            float dayDampen = mix(1.0f, 0.22f, sunWeight * skyLevel);
              float3 scaledPtLight = pointLights * dayDampen;
              float3 smoothPointLights = scaledPtLight / (1.0f + scaledPtLight * 0.35f);
              float3 totalBlockLight = smoothPointLights;

              float minAmbient = mix(0.045f, 0.055f, sunWeight);
              if (isNether) {
                minAmbient = max(minAmbient, 0.12f);
              }
              minAmbient = max(minAmbient, uVoxel.playerHead.z);

              // Hemispherical sky factor: vertical walls receive 60% sky, upward faces receive 100%
              float hemiSky = saturate(surfNormal.y * 0.40f + 0.60f);
              float3 nightSkyAmbient = mix(float3(0.022f, 0.030f, 0.050f), float3(0.040f, 0.058f, 0.085f), skyLevel);
              float3 ambientSky = max(activeSkyLight * (skyLevel * 0.72f * hemiSky), nightSkyAmbient);
              float celestialNdotL = saturate(dot(surfNormal, celestialDir));
              // Direct sun: 0.88x at noon (prevents washed-out), boosted to 1.30x at golden hour for dramatic rim light
              float goldenRimBoost = 1.0f + sunsetFactor * 0.50f;
              float3 celestialDirectCol = (sunWeight > 0.5f) ? (currentSunColor * 0.78f * goldenRimBoost) : (currentMoonColor * 0.70f);

              float outsideShadow = 1.0f;
              float computedShadow = (settings.sunShadowsEnabled > 0.5f && gridWeight > 0.02f && !isEntity) ? 0.0f : 1.0f;
              float3 computedTint = float3(1.0f);

              if (settings.sunShadowsEnabled > 0.5f && gridWeight > 0.02f && !isEntity) {
                if (celestialNdotL > 0.01f && celestialDir.y > 0.001f && rawSky > 0.05f) {
                  float celestialSlopeBias = max(0.04f, 0.06f * (1.0f - celestialNdotL));
                  float3 rayStart = pWorld + nWorld * celestialSlopeBias;

                  float ign = fract(52.9829189f * fract(dot(float2(gid), float2(0.06711056f, 0.00583715f))));
                  float dAngle = ign * 6.2831853f;
                  
                  
                  float radius = 0.8f;
                  
                  bool canCastPlayerShadow = (uVoxel.shadowParams.w > 0.5f && length(rayStart.xz - uVoxel.playerPos.xz) < 12.0f);
                  if (isFirstPerson) {
                      canCastPlayerShadow = canCastPlayerShadow && (rayStart.y <= uVoxel.playerPos.y + 1.4f);
                  }
                  
                  int rayCount = int(settings.shadowRayCount);
                  int numSamples = max(1, min(rayCount, 32));
                  if (rayCount == 0) radius = 0.0f;
                  
                  float totalVis = 0.0f;
                  float3 totalTint = float3(0.0f);
                  
                  for (int si = 0; si < numSamples; si++) {
                      float rRadius = sqrt((float(si) + 0.5f) / float(numSamples)) * radius;
                      float theta = float(si) * 2.399963f + dAngle;
                      float2 disk = float2(cos(theta), sin(theta)) * rRadius;
                      float3 offset = float3(disk.x, 0.0f, disk.y);
                      
                      float3 rDir = normalize(celestialDir * 40.0f + offset);
                      if (dot(rDir, nWorld) < 0.02f) {
                          rDir = normalize(rDir + nWorld * (0.02f - dot(rDir, nWorld)));
                      }
                      float3 t1 = rayStart + rDir * 40.0f;
                      ShadowRayResult cRes = traceDdaShadowRay(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, rayStart, t1, blockAtlasTex, smp, blockUvTable, bitmaskTable);
                      
                      if (canCastPlayerShadow) {
                          PlayerHit hit; hit.hitDist = 1e6f;
                          tracePlayerOBB(rayStart, rDir, uVoxel.playerPos.xyz, uVoxel.shadowParams.x, uVoxel.playerHead.x, uVoxel.playerHead.y, uVoxel.playerAnim.x, uVoxel.playerAnim.y, uVoxel.playerAnim.z, uVoxel.playerAnim.w, playerSkinTex, smp, hit);
                          if (hit.hitDist > 0.0000f && hit.hitDist < 40.0f) cRes.vis = 0.0f;
                      }
                      totalVis += cRes.vis;
                      totalTint += cRes.tint;
                  }
                  
                  
                  computedShadow = totalVis / float(numSamples);
                  computedTint = totalTint / float(numSamples);
                  
                  computedShadow = mix(computedShadow, 1.0f, env.rainStrength * 0.85f);
                }
              }

              // === Distant Terrain Lighting & Shadow (Smooth voxel grid transition) ===
              // Eliminates the harsh black ring and z-fighting at the voxel boundary (~7 chunks).
              float distantTerrainShadow = saturate(celestialNdotL * 0.60f + 0.40f);
              float gridBlend = smoothstep(0.05f, 0.95f, gridWeight);
              float celestialShadow = mix(distantTerrainShadow, computedShadow, gridBlend);
              float3 celestialTint = mix(float3(1.0f), computedTint, gridBlend);

              float3 directCelestial = celestialDirectCol * (celestialNdotL * skyLevel * 0.80f * celestialShadow) * celestialTint;
              // Never crush sky ambient on outdoor faces: outdoor shadow factor is at least 0.50f
              float shadowAmbientFactor = mix(mix(1.0f, 0.55f, skyLevel), 1.0f, celestialShadow);
              float3 baseAmbient = ambientSky * shadowAmbientFactor;



              float3 skyLight = baseAmbient + directCelestial;

              float daylightShadowSuppression = mix(1.0f, 0.30f, sunWeight * skyLevel);
              float shadowOcclusion = saturate(giData.a * 0.55f * daylightShadowSuppression);

              float3 shadowedSkyLight = skyLight * (1.0f - shadowOcclusion);

              float3 baseLighting = shadowedSkyLight + totalBlockLight + float3(minAmbient);
              if (isEntity) {
                baseLighting = float3(1.0f) + totalBlockLight * 0.8f;
              }
              
              // === True Voxel Global Illumination (VXGI) ===
              float3 giColor = float3(0.0f);
              if (!isEntity && !isWater && !isGlass && gridWeight > 0.05f && vxgiEnabled) {
                  int blurRays = int(clamp(doubleAoStrength, 1.0f, 4.0f));
                  uint frameCount = uint(camera.gameTime * 60.0f) % 256u;
                  float2 seedBase = pWorld.xz * 31.415f + pWorld.yy * 47.123f;
                  
                  float3 tX = cross(surfNormal, float3(0.0f, 1.0f, 0.0f));
                  if (dot(tX, tX) < 0.01f) tX = cross(surfNormal, float3(1.0f, 0.0f, 0.0f));
                  tX = normalize(tX);
                  float3 tY = normalize(cross(surfNormal, tX));
                  
                  for (int b = 0; b < blurRays; b++) {
                      float fOffset = float(frameCount) * 0.618f + float(b) * 13.37f;
                      float rand1 = fract(sin(dot(seedBase + fOffset, float2(12.9898f, 78.233f))) * 43758.5453f);
                      float rand2 = fract(sin(dot(seedBase - fOffset, float2(39.346f, 11.135f))) * 43758.5453f);
                      
                      float phi = 6.2831853f * rand1;
                      float cosTheta = sqrt(rand2);
                      float sinTheta = sqrt(1.0f - rand2);
                      
                      float3 giDir = normalize(tX * cos(phi) * sinTheta + tY * sin(phi) * sinTheta + surfNormal * cosTheta);
                      
                                            float3 jitterOrigin = pWorld + surfNormal * 0.15f;
                      VoxelReflResult giRes = traceVoxelReflections(voxelGrid, uVoxel.gridOrigin, uVoxel.gridSize, jitterOrigin, giDir, activeSkyLight, currentSunColor, currentMoonColor, celestialDir, sunWeight, blockAtlasTex, playerSkinTex, smp, blockUvTable, bitmaskTable, uVoxel.gridSize.w, uVoxel.lights, 6, settings.maxPointLights, 0.0f, 0.0f, 0.0f, 0.0f, env.rainStrength, camera.gameTime, uVoxel, 0.0f);
                      
                      if (giRes.alpha > 0.01f && giRes.hitDist < 5.0f) {
                          float distFalloff = pow(saturate(1.0f - giRes.hitDist / 5.0f), 2.0f);
                          giColor += giRes.color * distFalloff;
                      }
                  }
                  giColor /= float(blurRays);
              }
              
              baseLighting += giColor * 1.8f;

              baseLighting *= volumetricAo;
              
              // Phase 2: Prevent Pitch Black (Total Darkness)
              float3 baseAmbientFloor = mix(float3(0.004f, 0.005f, 0.008f), float3(0.008f, 0.010f, 0.015f), sunWeight);
              baseLighting = max(baseLighting, baseAmbientFloor);

              if (isWater) {
                float waveSlopeSun = (surfNormal.x * celestialDir.x + surfNormal.z * celestialDir.z) * 0.45f * sunWeight;
                float waveSlopeSky = (surfNormal.y - 1.0f) * 0.50f;
                baseLighting *= clamp(1.0f + waveSlopeSun + waveSlopeSky, 0.65f, 1.35f);
              }

              // Caustics disabled per user request

              bool isPuddle = false;
              // Puddles form on horizontal outdoor surfaces during rain
              // Also form on surfaces near water bodies (wet ground / wet sand effect)


              // Rain creates actual puddles (reflective)
              float wetFactor = env.rainStrength;
              if (wetFactor > 0.01f && !isWater && !isEntity && !isCameraInFluid && surfNormal.y > 0.82f && rawSky > 0.90f) {
                // Multi-octave noise for organic puddle blob shapes (like Worley)
                float pN1 = smoothNoise3D(float3(pWorld.xz * 0.30f, 0.0f));
                float pN2 = smoothNoise3D(float3(pWorld.xz * 0.80f, 1.7f)) * 0.50f;
                float pN3 = smoothNoise3D(float3(pWorld.xz * 1.80f, 3.2f)) * 0.25f;
                float puddleNoise = (pN1 + pN2 + pN3) / 1.75f;
                
                float puddleThresh = mix(0.50f, 0.38f, wetFactor);
                float puddleMask = smoothstep(puddleThresh, puddleThresh + 0.18f, puddleNoise) * saturate(wetFactor * 1.8f);
                if (puddleMask > 0.01f) {
                  isPuddle = (puddleMask > 0.12f);
                  albedo.rgb *= mix(1.0f, 0.55f, puddleMask);
                  
                  // Circular ripple normals (rain drops)
                  float2 rippleUv = pWorld.xz * 1.8f;
                  float rip1 = sin(length(fract(rippleUv) - 0.5f) * 22.0f - camera.gameTime * 16.0f);
                  float rip2 = sin(length(fract(rippleUv + float2(0.43f, 0.17f)) - 0.5f) * 18.0f - camera.gameTime * 12.0f);
                  float rip3 = sin(length(fract(rippleUv * 0.62f + float2(0.71f, 0.29f)) - 0.5f) * 14.0f - camera.gameTime * 9.5f);
                  float rippleMask = smoothstep(0.3f, 0.7f, smoothNoise3D(float3(pWorld.xz * 1.0f, 0.0f)));
                  
                  float rainOnly = saturate(env.rainStrength * 2.0f);
                  float2 rippleOffset = float2(rip1 + rip2 * 0.7f + rip3 * 0.4f)
                                       * 0.040f * puddleMask * rainOnly * rippleMask;
                  surfNormal = normalize(float3(surfNormal.x + rippleOffset.x, surfNormal.y, surfNormal.z + rippleOffset.y));
                }
              }

              float3 reflectionCol = float3(0.0f);
              float reflectFactor = 0.0f;

              if ((isWater || isMetal || isGlass || isPolished || isPuddle) && settings.reflectionsEnabled > 0.5f) {
                float3 viewDir = viewDirCam;
                float NdotV = saturate(dot(surfNormal, viewDir));

                float3 currentRayOrigin = pWorld + surfNormal * 0.04f;
                float3 currentRayDir = reflect(-viewDir, surfNormal);
                if (isWater || (isPuddle && surfNormal.y > 0.7f)) {
                  currentRayDir.y = max(currentRayDir.y, 0.025f);
                  currentRayDir = normalize(currentRayDir);
                }
                float3 accumulatedScene = float3(0.0f);
                float currentAttenuation = 1.0f;
                int maxBounces = max(1, int(uVoxel.shadowParams.y));
                
                float3 specPoints = float3(0.0f);

                for (int b = 0; b < maxBounces; b++) {
                    int steps = (b == 0) ? 40 : 18;
                    float cloudsRefl = (b == 0) ? (settings.cloudsInReflections * 0.5f) : 0.0f;
                    float3 skyReflection = evaluateSkyAndReflections(currentRayOrigin, currentRayDir, camera.gameTime, actualSky, sunriseTint, clampedSunrise, currentSunColor, currentMoonColor, sunWeight, sunDir, moonDir, env.starBrightness, cloudsRefl, settings.cloudSteps, env.rainStrength, 1e6f) * skyLevel;
                    
                    VoxelReflResult vxr = traceVoxelReflections(voxelGrid, uVoxel.gridOrigin, uVoxel.gridSize, currentRayOrigin, currentRayDir, activeSkyLight, currentSunColor, currentMoonColor, celestialDir, sunWeight, blockAtlasTex, playerSkinTex, smp, blockUvTable, bitmaskTable, uVoxel.gridSize.w, uVoxel.lights, steps, settings.maxPointLights, settings.reflectionPtShadows, settings.reflectionDirShadows, settings.doubleAoInReflections, 0.0f, env.rainStrength, camera.gameTime, uVoxel, settings.shadowRayCount);
                    
                    float reflDist = vxr.hitDist;
                    float rawReflFog = saturate(1.0f - exp(-pow(reflDist * 0.003f, 3.5f)));
                    float3 reflFogColor = actualSky * (sunWeight * 0.85f + 0.15f);
                    
                    float reflY = currentRayOrigin.y + currentRayDir.y * reflDist;
                    float reflSkyLvl = saturate((reflY - 24.0f) / 64.0f);
                    float3 attenuatedReflFog = mix(reflFogColor * 0.05f, reflFogColor, reflSkyLvl);
                    vxr.color = mix(vxr.color, attenuatedReflFog, rawReflFog * vxr.alpha);
                    
                    float3 bounceColor = mix(skyReflection, vxr.color, vxr.alpha);
                    
                    if (b == 0) {
                        int lightCount = uVoxel.gridSize.w;
                        for (int i = 0; i < lightCount; i++) {
                            float3 toLight = uVoxel.lights[i].posAndRadius.xyz - pWorld;
                            float dist = length(toLight);
                            float lRad = uVoxel.lights[i].posAndRadius.w;
                            if (dist < lRad && dist > 0.05f) {
                                float3 L = toLight / dist;
                                float3 H = normalize(L + viewDir);
                                float NdotH = saturate(dot(surfNormal, H));
                                float NdotL = saturate(dot(surfNormal, L));
                                float spec = pow(NdotH, 24.0f) * NdotL;
                                float atten = saturate(1.0f - dist / lRad);
                                float smoothAtten = atten * atten;
                                specPoints += uVoxel.lights[i].colorAndIntensity.xyz * (uVoxel.lights[i].colorAndIntensity.w * spec * smoothAtten * 0.3f);
                            }
                        }
                    }

                    accumulatedScene += bounceColor * currentAttenuation;
                    
                    if (vxr.alpha < 0.1f) {
                        break;
                    }
                    
                    if (vxr.reflectivity < 0.05f) {
                        break;
                    }
                    
                    currentRayOrigin = currentRayOrigin + currentRayDir * vxr.hitDist + vxr.normal * 0.04f;
                    currentRayDir = reflect(currentRayDir, vxr.normal);
                    currentAttenuation *= vxr.reflectivity;
                }

                float f0 = isMetal ? 0.85f : (isPolished ? 0.06f : (isGlass ? 0.15f : (isPuddle ? 0.15f : 0.02f)));
                float fresnel = f0 + (1.0f - f0) * pow(1.0f - NdotV, 5.0f);

                if (isWater) {
                    // === Physically-Based Water Depth (Vertical Voxel Count) ===
                    // Ensure waterColPos points to the actual water block, even if pWorld dropped into seabed
                    int3 waterColPos = int3(floor(pWorld));
                    uint2 wcpVox = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, waterColPos);
                    if (((wcpVox.x & 1) != 0 && (wcpVox.x & 4) == 0)) {
                        waterColPos += int3(0, 1, 0); // push it up into the water block
                    }

                    // Count physical water voxels straight DOWN from the surface to the seabed.
                    float realDepth = 1.0f;
                    for (int dy = 1; dy <= 24; dy++) {
                        int3 checkBelow = waterColPos - int3(0, dy, 0);
                        uint2 belowVox = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, checkBelow);
                        if ((belowVox.x & 4) != 0 && (belowVox.x & 8) == 0) {
                            realDepth += 1.0f;
                        } else {
                            break;
                        }
                    }

                    float depthFactor = smoothstep(0.1f, 8.0f, realDepth);

                    // Beer-Lambert spectral absorption (use true depth for physically correct tint)
                    float3 waterExtinction = float3(0.35f, 0.12f, 0.035f) * settings.waterAbsorption;
                    float3 transmitted = exp(-waterExtinction * realDepth);

                    // Shallow water: crystal clear turquoise
                    float3 crystalShallow = float3(0.18f, 0.76f, 0.85f);
                    // Deep ocean: rich oceanic midnight navy
                    float3 crystalDeep   = float3(0.01f, 0.06f, 0.22f);

                    // Use BLURRED depth factor so colour gradient is soft across column boundaries
                    float3 waterBodyColor = mix(crystalShallow, crystalDeep, depthFactor);

                    // Shallow water: very transparent, so you see seabed clearly
                    float waterOpacity = saturate((1.0f - transmitted.b * 0.85f) * mix(0.18f, 1.0f, depthFactor));

                    // Seabed tinted by Beer-Lambert absorption + a faint cyan overlay in shallow areas
                    // so the bottom looks teal/cyan rather than raw seabed colour.
                    float3 seabedFiltered = albedo.rgb * transmitted;
                    float3 cyanOverlay = float3(0.12f, 0.72f, 0.82f);
                    seabedFiltered = mix(seabedFiltered, cyanOverlay * seabedFiltered, 0.40f * (1.0f - depthFactor));
                    albedo.rgb = mix(seabedFiltered, waterBodyColor, waterOpacity);

                    // === Visible Gerstner Waves on Water Surface ===
                    // (Diffuse wave shading is automatically handled by baseLighting via surfNormal)
                    // We only add the intense specular sun/moon glint to the reflections!
                    float3 halfVec = normalize(celestialDir - viewDir);
                    float waveSpec = pow(saturate(dot(surfNormal, halfVec)), 90.0f);
                    float3 waveGlint = celestialDirectCol * (waveSpec * 1.5f) * celestialShadow * skyLevel;
                    
                    accumulatedScene += waveGlint;

                    // Smooth transition from local voxel reflections to distant sky reflections outside grid
                    if (settings.reflectionsEnabled > 0.5f && gridWeight < 0.85f) {
                        float3 reflDir = reflect(-viewDir, surfNormal);
                        if (reflDir.y > -0.1f) {
                            float3 skyRefl = actualSky * (sunWeight * 0.85f + 0.15f);
                            accumulatedScene = mix(skyRefl, accumulatedScene, smoothstep(0.05f, 0.80f, gridWeight));
                        }
                    }
                }

                if (isMetal) {
                    accumulatedScene = min(accumulatedScene, 1.3f);
                    accumulatedScene *= mix(float3(1.0f), albedo.rgb, 0.90f);
                    fresnel = saturate(fresnel * 0.35f);
                }
                
                reflectionCol = accumulatedScene + specPoints * (isMetal ? 0.3f : 0.8f);
                reflectFactor = isWater ? saturate(fresnel * 0.85f + 0.08f) : (isPolished ? saturate(fresnel * 0.25f) : (isPuddle ? saturate(fresnel * 0.90f + 0.05f) : saturate(fresnel)));
              }

              bool isEmissiveBlock = (insideVox.x & 8) != 0;
              if (isEmissiveBlock) {
                float emStr = isMetal ? 1.15f : 1.6f;
                baseLighting = max(baseLighting, float3(emStr));
              }

              float3 baseLit = albedo.rgb * baseLighting;
              float3 litRgb = baseLit;
              if ((isWater || isMetal || isGlass || isPolished || isPuddle) && settings.reflectionsEnabled > 0.5f) {
                litRgb = mix(baseLit, reflectionCol, reflectFactor);
              }

              if (isCameraUnderwater) {
                float distToCam = length(pWorld - uVoxel.camPos.xyz);
                float fogFactor = saturate(1.0f - exp(-distToCam * 0.055f));
                float3 rtxWater = mix(float3(0.02f, 0.22f, 0.45f), float3(0.10f, 0.65f, 0.85f), saturate(1.0f - distToCam / 40.0f));
                litRgb = mix(litRgb, rtxWater * max(activeSkyLight * 1.2f, float3(0.45f)), fogFactor);
              } else if (isCameraInLava) {
                float distToCam = length(pWorld - uVoxel.camPos.xyz);
                float fogFactor = saturate(distToCam * 0.40f);
                litRgb = mix(litRgb, float3(0.85f, 0.15f, 0.02f), fogFactor);
              } else if (isNether) {
                float distToCam = length(pWorld - uVoxel.camPos.xyz);
                float fogFactor = saturate(1.0f - exp(-distToCam * distToCam * 0.00018f)) * skyLevel;
                float3 netherFogColor = float3(0.30f, 0.10f, 0.08f);
                litRgb = mix(litRgb, netherFogColor, fogFactor);
                            } else if (!isEnd) {
                float distToCam = length(pWorld - uVoxel.camPos.xyz);
                float3 rayDir = normalize(pWorld - uVoxel.camPos.xyz);

                // Clear-day atmospheric Rayleigh haze (always present, very gradual).
                // Creates natural horizon haze on mountains even without rain.
                // Kicks in after 80 blocks, full at ~200 blocks.
                float clearDayFog = 1.0f - exp(-pow(distToCam * 0.0028f, 3.8f));

                // Rain: thick visibility reduction starting much sooner
                float rainFog = 1.0f - exp(-pow(distToCam * 0.006f, 3.0f));

                // Height fog: mist in valleys during rain only (not clear days)
                float heightFog = exp(-(pWorld.y - 40.0f) * 0.03f)
                                * saturate(distToCam * 0.015f)
                                * saturate(env.rainStrength * 2.0f);

                float fogFactor = saturate(mix(clearDayFog, rainFog, env.rainStrength) + heightFog);
                float3 fogColor = evaluateSkyAndReflections(uVoxel.camPos.xyz, rayDir, camera.gameTime, actualSky, sunriseTint, clampedSunrise, float3(0.0f), float3(0.0f), sunWeight, sunDir, moonDir, env.starBrightness, 0.0f, settings.cloudSteps, env.rainStrength, 1e6f);

                // Darken fog in caves (keep bright under open sky)
                float surfaceBoost = saturate((pWorld.y - 50.0f) * 0.05f);
                float caveDarkness = saturate(skyLevel * 4.0f + surfaceBoost);
                fogColor *= max(caveDarkness, 0.0f);

                // Mie forward scattering glare toward sun (subtle, smooth, non-noisy)
                float cosSunTheta = dot(rayDir, sunDir);
                float miePhase = min(5.0f, (1.0f - 0.70f*0.70f) / pow(max(1.0f + 0.70f*0.70f - 2.0f*0.70f*cosSunTheta, 0.06f), 1.5f));
                float3 mieGlare = currentSunColor * (miePhase * 0.003f * sunWeight * (1.0f - env.rainStrength * 0.80f)) * skyLevel * celestialShadow;
                fogColor += mieGlare;

                // Final rain visibility bump (heavy rain nearly opaque at distance)
                fogFactor = saturate(fogFactor + env.rainStrength * 0.80f * (1.0f - exp(-distToCam * 0.04f)));
                litRgb = mix(litRgb, fogColor, fogFactor);
              }

              
              if (settings.cloudsEnabled > 0.5f) {
                  float distToCam = length(pWorld - uVoxel.camPos.xyz);
                  float3 rayDir = normalize(pWorld - uVoxel.camPos.xyz);
                  float3 hazeColor = mix(float3(0.48f, 0.58f, 0.66f), sunriseTint * 1.15f, clampedSunrise);
                  if (sunWeight < 0.35f) hazeColor = mix(float3(0.12f, 0.16f, 0.26f), hazeColor, sunWeight * 2.85f);
                  
                  float4 cloudData = computeVolumetricClouds(uVoxel.camPos.xyz, rayDir, camera.gameTime, hazeColor, sunWeight, sunDir, moonDir, currentSunColor, currentMoonColor, settings.cloudsEnabled, settings.cloudSteps, env.rainStrength, distToCam);
                  litRgb = litRgb * cloudData.a + cloudData.rgb;
              }
              
              // --- Ray Traced Volumetric Fog Scattering ---
                  // Volumetrics are rendered at half resolution
                  uint2 halfGid = uint2(gid.x / 2, gid.y / 2);
                  float3 volumetricFog = volumetricsTexture.read(halfGid).rgb;

                  litRgb += volumetricFog;

              
              // --- Luma-Preserving Filmic Tone Mapping ---
              // Preserves Minecraft's vibrant colors (lush green grass, deep blue sky, rich sunset)
              // with zero grey veil and no harsh black crushing in shadows.
              {
                  float exposure = mix(1.18f, 1.00f, sunsetFactor * sunWeight);
                  float saturationBoost = mix(1.28f, 1.40f, sunsetFactor * sunWeight);
                  
                  if (isnan(litRgb.x) || isnan(litRgb.y) || isnan(litRgb.z) || isinf(litRgb.x) || isinf(litRgb.y) || isinf(litRgb.z)) {
                      litRgb = float3(0.0f);
                  }
                  litRgb = settings.hdrEnabled > 0.5f ? (litRgb * exposure) : lumaPreservingFilmic(litRgb, exposure, saturationBoost);


                  // Subtle golden hour warm grade on midtones
                  float3 warmGrade = mix(float3(1.0f), float3(1.04f, 0.98f, 0.90f), sunsetFactor * sunWeight);
                  litRgb = mix(litRgb, litRgb * warmGrade, 0.40f);
              }

              outTexture.write(float4(litRgb, albedo.a), gid);
              return;
            }
