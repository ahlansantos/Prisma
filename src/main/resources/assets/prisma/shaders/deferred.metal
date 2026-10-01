            struct DeferredVertexOut {
              float4 position [[position]];
              float2 uv;
            };

            struct DeferredUniforms {
              float aspect;
              float fovScale;
              float sunAngle;
              float cameraPitch;
              float cameraYaw;
              float sunShadowsEnabled;
              float gameTime;
              float waterWaveStrength;
              float waterWaveSpeed;
              float waterAbsorption;
              float skyR;
              float skyG;
              float skyB;
              float sunriseAlpha;
              float sunriseR;
              float sunriseG;
              float sunriseB;
              float starBrightness;
              float maxPointLights;
              float reflectionPtShadows;
              float reflectionDirShadows;
              float doubleAoInReflections;
              
              float cloudsEnabled;
              float cloudSteps;
              float reflectionsEnabled;
              float cloudsInReflections;
              float rainStrength;
              float pointLightSoftShadows;
              float shadowRayCount;
              float volFogEnabled;   // 116
              float volFogSamples;   // 120
              float volFogIntensity; // 124
              float waterOnlyPass;   // 128
            };

                        
// --- Analytical Point Lights (replaced ReSTIR) ---

static inline float3 computeEclipseWaterWaves(float2 pWorldXZ, float time, float strength, float speed) {
              if (strength <= 0.001f) return float3(0.0f, 1.0f, 0.0f);

              // Gerstner Ocean Waves — directional parallel swells (replaces isotropic trochoidal noise).
              // Creates the realistic parallel-band ocean look from Imagem 3.
              // Wave parameters: (direction.x, direction.z, frequency, amplitude)
              const float4 waves[5] = {
                float4( 1.00f,  0.20f, 0.45f, 0.28f),  // primary swell: near-east direction
                float4( 0.85f, -0.52f, 0.80f, 0.18f),  // secondary swell: slight cross-chop
                float4( 0.40f,  0.92f, 1.30f, 0.09f),  // high-freq ripple
                float4(-0.70f,  0.72f, 1.90f, 0.04f),  // counter-swell chop
                float4( 0.10f,  1.00f, 2.60f, 0.02f),  // micro-ripple
              };

              float2 dX = float2(0.0f);  // partial derivative accumulator
              float wTime = time * 0.65f * speed;

              for (int i = 0; i < 5; i++) {
                float2 dir   = normalize(waves[i].xy);
                float  freq  = waves[i].z;
                float  amp   = waves[i].w * strength * 0.15f; // Extremely relaxed amplitude
                float  phase = wTime * (0.9f + float(i) * 0.12f);
                float  x     = dot(dir, pWorldXZ) * freq + phase;
                // Gerstner derivative: steepness on crests (sharper than sinusoidal)
                float  wave  = exp(sin(x) - 1.0f);
                float  deriv = wave * cos(x) * freq * amp * 2.2f;
                dX += dir * deriv;
              }

              return normalize(float3(-dX.x, 1.0f, -dX.y));
            }

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

            // =========================================================================
            // Luma-Preserving Filmic Tone Mapping (BSL / Complementary / Bliss Style)
            // Tones luminance alone and preserves true chromaticity:
            // - Blue sky stays deep azure, grass stays rich lush green, sunset stays fiery amber
            // - ZERO grey wash, ZERO desaturation veil
            // - Soft open toe: deep shadows and caves never crush to pitch black
            // - Extended shoulder: bright sun specular rolls off smoothly without clipping
            // =========================================================================
            static inline float3 lumaPreservingFilmic(float3 col, float exposure, float saturationBoost) {
                if (isnan(col.x) || isnan(col.y) || isnan(col.z) || isinf(col.x) || isinf(col.y) || isinf(col.z)) return float3(0.0f);
                col = max(col * exposure * 0.85f, float3(0.0f)); // slightly adjust exposure for ACES

                // ACES fitted curve
                float a = 2.51f;
                float b = 0.03f;
                float c = 2.43f;
                float d = 0.59f;
                float e = 0.14f;
                float3 tonedColor = saturate((col * (a * col + b)) / (col * (c * col + d) + e));

                // Apply dynamic saturation
                float luma = dot(tonedColor, float3(0.2126f, 0.7152f, 0.0722f));
                tonedColor = mix(float3(luma), tonedColor, saturationBoost);

                return saturate(tonedColor);
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
              sampler smp [[sampler(0)]],
              constant DeferredUniforms& u [[buffer(0)]],
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
              if (u.waterOnlyPass > 0.5f && rawDepth <= 0.00005f) {
                outTexture.write(float4(albedo.rgb, albedo.a), gid);
                return;
              }
              bool isHandPixel = (hDepth > wDepth + 0.0001f && hDepth > 0.0001f);
              float effectiveDepth = rawDepth;
              

              float sunTheta = u.sunAngle;
              float3 sunDir = normalize(float3(-sin(sunTheta), cos(sunTheta), 0.0f));
              float3 moonDir = -sunDir;
              float sunElevation = sunDir.y;
              float sunWeight = smoothstep(-0.08f, 0.04f, sunElevation);
              float sunsetFactor = (1.0f - smoothstep(0.02f, 0.32f, abs(sunElevation))) * smoothstep(-0.06f, 0.08f, sunElevation);
              float clampedSunrise = max(0.0f, u.sunriseAlpha);
              bool isNether = u.sunriseAlpha < -0.5f && u.sunriseAlpha > -1.5f;
              bool isEnd = u.sunriseAlpha < -1.5f;

              // --- Golden Hour Sun Color (2200K sunrise/sunset -> 5500K noon) ---
              // Noon: crisp slightly warm white. Sunrise/sunset: rich amber/orange cinematico.
              float3 noonSunColor   = float3(1.05f, 0.98f, 0.88f);
              // Deep golden hour: more saturated amber-red at very low sun angles
              float goldenBoost     = smoothstep(0.30f, 0.0f, abs(sunElevation)); // 1.0 near horizon
              float3 goldenHourColor = mix(float3(1.55f, 0.72f, 0.22f),  // warm amber
                                          float3(1.70f, 0.45f, 0.12f),  // deep orange-red at horizon
                                          goldenBoost * 0.6f);
              float3 currentSunColor = mix(noonSunColor, goldenHourColor, max(sunsetFactor, clampedSunrise)) * (1.0f - u.rainStrength * 0.98f);
              float3 currentMoonColor = float3(0.22f, 0.32f, 0.52f) * (1.0f - u.rainStrength * 0.95f);
              float3 celestialDir = sunWeight > 0.5f ? sunDir : moonDir;

              float3 actualSky = float3(u.skyR, u.skyG, u.skyB);
              float3 sunriseTint = float3(u.sunriseR, u.sunriseG, u.sunriseB);
              // Cooler, more balanced daySkyLight so ambient doesn't blow out blocks
              float3 daySkyLight = mix(float3(0.92f, 0.92f, 0.90f), sunriseTint * 1.10f, clampedSunrise);
              float3 nightSkyLight = float3(0.06f, 0.11f, 0.28f);
              float3 activeSkyLight = mix(nightSkyLight, daySkyLight, sunWeight) * (1.0f - u.rainStrength * 0.65f);

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

                float3 skyCol = evaluateSkyAndReflections(uVoxel.camPos.xyz, rayDir, u.gameTime, actualSky, sunriseTint, clampedSunrise, currentSunColor, currentMoonColor, sunWeight, sunDir, moonDir, u.starBrightness, u.cloudsEnabled, u.cloudSteps, u.rainStrength, 1e6f);
                float luma = dot(albedo.rgb, float3(0.299f, 0.587f, 0.114f)); float isRain = saturate((luma - 0.2f) * 10.0f) * u.rainStrength; outTexture.write(float4(mix(skyCol, albedo.rgb, isRain * 0.6f), 1.0f), gid); return;
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
              float3 pSurfaceRel = pWorld - uVoxel.camPos.xyz;
              float distToSurface = length(pSurfaceRel);
              float3 viewDir = distToSurface > 0.001f ? (pSurfaceRel / distToSurface) : float3(0.0f, -1.0f, 0.0f);

              if (!isEntity && !isCameraInFluid) {
                if (u.waterOnlyPass > 0.5f) {
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
              }

              // In water-only pass, process water AND translucent glass reflections!
              if (u.waterOnlyPass > 0.5f && !isWater && !isGlass) {
                outTexture.write(float4(albedo.rgb, albedo.a), gid);
                return;
              }

              
              float3 surfNormal = isEntity ? geomNormal : normalize(mix(geomNormal, nWorld, 0.70f));
              
              if (isWater) {

                if (abs(geomNormal.y) > 0.45f) {
                    float baseWaterY = (uVoxel.camPos.y >= pWorld.y) ? 1.0f : -1.0f;
                    float3 waveNorm = computeEclipseWaterWaves(pWorld.xz, u.gameTime, u.waterWaveStrength, u.waterWaveSpeed);
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

              float doubleAoStrength = uVoxel.camPos.w;
              float doubleAo = (!isEntity && !isWater && !isGlass && gridWeight > 0.05f && doubleAoStrength > 0.01f) ? computeDoubleAO(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, pWorld, nWorld, uVoxel.camPos.xyz, uVoxel.gridOrigin.w, (float2(gid) + 0.5f)) * doubleAoStrength * gridWeight : 0.0f;
              float ssao = (!isEntity && !isWater && !isGlass && doubleAoStrength > 0.01f && doubleAo < 0.92f) ? computeSSAO(worldDepthTex, smp, uv, rawDepth, pWorld, surfNormal, uVoxel.camPos.xyz, uVoxel.viewProj, (float2(gid) + 0.5f)) * doubleAoStrength : 0.0f;
              
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

              float combinedAo = saturate(max(doubleAo, ssao) * 0.80f) * saturate(1.0f - blockLevel * blockLevel);
              if (isFoliage) {
                combinedAo *= 0.40f; // Soften AO for tree leaves and vegetation
              }
              float ao = saturate(1.0f - combinedAo);
              float volumetricAo = mix(0.22f, 1.0f, pow(ao, 1.25f));




              
              // Calculate motion vectors. If we are drawing water on top of a seabed pixel, 
              // we MUST calculate velocity using the water surface position, otherwise parallax 
              // mismatch ruins MetalFX temporal accumulation (causing ghosting/Z-fighting on shallow water).
              float3 motionWorld = pWorld;
              if (isWater && u.waterOnlyPass > 0.5f) {
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
              float2 currentUv = (currentClip.xy / max(currentClip.w, 0.0001f)) * 0.5f + 0.5f;
              float2 prevUv = (prevClip.xy / max(prevClip.w, 0.0001f)) * 0.5f + 0.5f;
              // Motion vector in pixels (MetalFX requires pixel-space, Y points DOWN in texture space)
              float2 velocity = (currentUv - prevUv) * float2(float(velocityTex.get_width()), -float(velocityTex.get_height()));
              velocityTex.write(float4(velocity, 0.0f, 0.0f), gid);

              // Write dummy reservoir (kept for compatibility)
              currReservoirTex.write(uint4(0, 0, 0, 0), gid);


              
              bool isFirstPerson = (length(uVoxel.camPos.xyz - (uVoxel.playerPos.xyz + float3(0.0f, 1.5f, 0.0f))) < 0.60f);
              bool isGlassSurface = ((insideVox.x & 1) != 0) && (((insideVox.x >> 12) & 0x0F) == 1u);
              
              // --- Analytical Point Lights (deterministic, no noise) ---
              PointLightResult ptRes = PointLightResult{float3(0.0f), 0.0f, 0.0f};
              float3 pointLights = float3(0.0f);
              
              int lightCount = int(uVoxel.gridSize.w);
              if (lightCount > 0) {
                  float3 rayOrigin = pWorld + surfNormal * 0.05f;
                  bool ptShadowsEnabled = (uVoxel.camRight.w > 0.5f);
                  int rayCount = ptShadowsEnabled ? int(u.shadowRayCount) : 0;
                  
                  float ign = fract(52.9829189f * fract(dot(float2(gid), float2(0.06711056f, 0.00583715f))));
                  float dAngle = ign * 6.2831853f;
                  
                  float maxDarkening = 0.0f;
                          
                  for (int li = 0; li < lightCount && li < 32; li++) {
                      float3 lPos = uVoxel.lights[li].posAndRadius.xyz;
                      float3 toL = lPos - pWorld;
                      float distL = length(toL);
                      float lRad = uVoxel.lights[li].posAndRadius.w;
                      if (distL >= lRad || distL < 0.05f) continue;
                      
                      float3 L = toL / distL;
                      float NdotL = saturate(dot(surfNormal, L));
                      if (NdotL < 0.001f) continue;
                      
                      float atten = saturate(1.0f - (distL / lRad));
                      float smoothAtten = atten * atten;
                      float3 lColor = uVoxel.lights[li].colorAndIntensity.xyz * uVoxel.lights[li].colorAndIntensity.w;
                      
                      int numSamples = max(1, min(rayCount, 32));
                      float radius = (rayCount > 0) ? 0.30f : 0.0f;
                      
                      float totalVis = 0.0f;
                      float3 totalTint = float3(0.0f);
                      float3 up = abs(L.y) < 0.99f ? float3(0, 1, 0) : float3(1, 0, 0);
                      float3 tangent = normalize(cross(up, L));
                      float3 bitangent = cross(L, tangent);
                      
                      for (int si = 0; si < numSamples; si++) {
                          if (ptShadowsEnabled) {
                              float rRadius = sqrt((float(si) + 0.5f) / float(numSamples)) * radius;
                              float theta = float(si) * 2.399963f + dAngle;
                              float2 disk = float2(cos(theta), sin(theta)) * rRadius;
                              float3 offset = (tangent * disk.x + bitangent * disk.y);
                              
                              float3 jitteredLPos = lPos + offset;
                              float3 jitteredL = jitteredLPos - pWorld;
                              float jitteredDist = length(jitteredL);
                              float3 targetPos = rayOrigin + (jitteredL / jitteredDist) * jitteredDist;
                              
                              ShadowRayResult sr = traceDdaShadowRay(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, rayOrigin, targetPos, blockAtlasTex, smp, blockUvTable, bitmaskTable);
                              
                              bool isHandheld = (length(lPos - uVoxel.camPos.xyz) < 1.6f) || (length(lPos - uVoxel.playerPos.xyz) < 1.8f);
                              bool canCastPtPlayerShadow = (!isHandheld && uVoxel.shadowParams.w > 0.5f && length(rayOrigin.xz - uVoxel.playerPos.xz) < 12.0f);
                              if (isFirstPerson) {
                                  canCastPtPlayerShadow = canCastPtPlayerShadow && (rayOrigin.y <= uVoxel.playerPos.y + 1.4f);
                              }
                              
                              if (canCastPtPlayerShadow && sr.vis > 0.0f) {
                                  float3 ptL = normalize(targetPos - rayOrigin);
                                  PlayerHit hit; hit.hitDist = 1e6f;
                                  tracePlayerOBB(rayOrigin, ptL, uVoxel.playerPos.xyz, uVoxel.shadowParams.x, uVoxel.playerHead.x, uVoxel.playerHead.y, uVoxel.playerAnim.x, uVoxel.playerAnim.y, uVoxel.playerAnim.z, uVoxel.playerAnim.w, playerSkinTex, smp, hit);
                                  if (hit.hitDist > 0.0f && hit.hitDist < jitteredDist) {
                                      sr.vis = 0.0f;
                                  }
                              }
                              totalVis += sr.vis;
                              totalTint += sr.tint;
                          } else {
                              totalVis += 1.0f;
                              totalTint += float3(1.0f);
                          }
                      }
                      
                      float visibility = totalVis / float(numSamples);
                      float3 tintCol = totalTint / float(numSamples);
                      pointLights += lColor * tintCol * NdotL * smoothAtten * visibility;
                      
                      float currentDarkening = (1.0f - visibility) * NdotL * atten * saturate(uVoxel.lights[li].colorAndIntensity.w * 0.5f);
                      maxDarkening = max(maxDarkening, currentDarkening);
                  }
                  ptRes.shadowDarkening = maxDarkening;
              }
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
              float computedShadow = (u.sunShadowsEnabled > 0.5f && gridWeight > 0.02f && !isEntity) ? 0.0f : 1.0f;
              float3 computedTint = float3(1.0f);

              if (u.sunShadowsEnabled > 0.5f && gridWeight > 0.02f && !isEntity) {
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
                  
                  int rayCount = int(u.shadowRayCount);
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
                  
                  computedShadow = mix(computedShadow, 1.0f, u.rainStrength * 0.85f);
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
              float shadowOcclusion = saturate(ptRes.shadowDarkening * 0.55f * daylightShadowSuppression);

              float3 shadowedSkyLight = skyLight * (1.0f - shadowOcclusion);

              float3 baseLighting = shadowedSkyLight + totalBlockLight + float3(minAmbient);
              if (isEntity) {
                baseLighting = float3(1.0f) + totalBlockLight * 0.8f;
              }

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
              float wetFactor = u.rainStrength;
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
                  float rip1 = sin(length(fract(rippleUv) - 0.5f) * 22.0f - u.gameTime * 16.0f);
                  float rip2 = sin(length(fract(rippleUv + float2(0.43f, 0.17f)) - 0.5f) * 18.0f - u.gameTime * 12.0f);
                  float rip3 = sin(length(fract(rippleUv * 0.62f + float2(0.71f, 0.29f)) - 0.5f) * 14.0f - u.gameTime * 9.5f);
                  float rippleMask = smoothstep(0.3f, 0.7f, smoothNoise3D(float3(pWorld.xz * 1.0f, 0.0f)));
                  
                  float rainOnly = saturate(u.rainStrength * 2.0f);
                  float2 rippleOffset = float2(rip1 + rip2 * 0.7f + rip3 * 0.4f)
                                       * 0.040f * puddleMask * rainOnly * rippleMask;
                  surfNormal = normalize(float3(surfNormal.x + rippleOffset.x, surfNormal.y, surfNormal.z + rippleOffset.y));
                }
              }

              float3 reflectionCol = float3(0.0f);
              float reflectFactor = 0.0f;

              if ((isWater || isMetal || isGlass || isPuddle) && u.reflectionsEnabled > 0.5f) {
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
                    float cloudsRefl = (b == 0) ? (u.cloudsInReflections * 0.5f) : 0.0f;
                    float3 skyReflection = evaluateSkyAndReflections(currentRayOrigin, currentRayDir, u.gameTime, actualSky, sunriseTint, clampedSunrise, currentSunColor, currentMoonColor, sunWeight, sunDir, moonDir, u.starBrightness, cloudsRefl, u.cloudSteps, u.rainStrength, 1e6f) * skyLevel;
                    
                    VoxelReflResult vxr = traceVoxelReflections(voxelGrid, uVoxel.gridOrigin, uVoxel.gridSize, currentRayOrigin, currentRayDir, activeSkyLight, currentSunColor, currentMoonColor, celestialDir, sunWeight, blockAtlasTex, playerSkinTex, smp, blockUvTable, bitmaskTable, uVoxel.gridSize.w, uVoxel.lights, steps, u.maxPointLights, u.reflectionPtShadows, u.reflectionDirShadows, u.doubleAoInReflections, 0.0f, u.rainStrength, u.gameTime, uVoxel, u.shadowRayCount);
                    
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

                float f0 = isMetal ? 0.85f : (isGlass ? 0.15f : (isPuddle ? 0.15f : 0.02f));
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
                    float3 waterExtinction = float3(0.35f, 0.12f, 0.035f) * u.waterAbsorption;
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
                    if (u.reflectionsEnabled > 0.5f && gridWeight < 0.85f) {
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
                reflectFactor = isWater ? saturate(fresnel * 0.85f + 0.08f) : (isPuddle ? saturate(fresnel * 0.90f + 0.05f) : saturate(fresnel));
              }

              bool isEmissiveBlock = (insideVox.x & 8) != 0;
              if (isEmissiveBlock) {
                float emStr = isMetal ? 1.15f : 1.6f;
                baseLighting = max(baseLighting, float3(emStr));
              }

              float3 baseLit = albedo.rgb * baseLighting;
              float3 litRgb = baseLit;
              if ((isWater || isMetal || isGlass || isPuddle) && u.reflectionsEnabled > 0.5f) {
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
                                * saturate(u.rainStrength * 2.0f);

                float fogFactor = saturate(mix(clearDayFog, rainFog, u.rainStrength) + heightFog);
                float3 fogColor = evaluateSkyAndReflections(uVoxel.camPos.xyz, rayDir, u.gameTime, actualSky, sunriseTint, clampedSunrise, float3(0.0f), float3(0.0f), sunWeight, sunDir, moonDir, u.starBrightness, 0.0f, u.cloudSteps, u.rainStrength, 1e6f);

                // Darken fog in caves (keep bright under open sky)
                float surfaceBoost = saturate((pWorld.y - 50.0f) * 0.05f);
                float caveDarkness = saturate(skyLevel * 4.0f + surfaceBoost);
                fogColor *= max(caveDarkness, 0.0f);

                // Mie forward scattering glare toward sun (subtle, smooth, non-noisy)
                float cosSunTheta = dot(rayDir, sunDir);
                float miePhase = min(5.0f, (1.0f - 0.70f*0.70f) / pow(max(1.0f + 0.70f*0.70f - 2.0f*0.70f*cosSunTheta, 0.06f), 1.5f));
                float3 mieGlare = currentSunColor * (miePhase * 0.003f * sunWeight * (1.0f - u.rainStrength * 0.80f)) * skyLevel * celestialShadow;
                fogColor += mieGlare;

                // Final rain visibility bump (heavy rain nearly opaque at distance)
                fogFactor = saturate(fogFactor + u.rainStrength * 0.80f * (1.0f - exp(-distToCam * 0.04f)));
                litRgb = mix(litRgb, fogColor, fogFactor);
              }

              
              if (u.cloudsEnabled > 0.5f) {
                  float distToCam = length(pWorld - uVoxel.camPos.xyz);
                  float3 rayDir = normalize(pWorld - uVoxel.camPos.xyz);
                  float3 hazeColor = mix(float3(0.48f, 0.58f, 0.66f), sunriseTint * 1.15f, clampedSunrise);
                  if (sunWeight < 0.35f) hazeColor = mix(float3(0.12f, 0.16f, 0.26f), hazeColor, sunWeight * 2.85f);
                  
                  float4 cloudData = computeVolumetricClouds(uVoxel.camPos.xyz, rayDir, u.gameTime, hazeColor, sunWeight, sunDir, moonDir, currentSunColor, currentMoonColor, u.cloudsEnabled, u.cloudSteps, u.rainStrength, distToCam);
                  litRgb = litRgb * cloudData.a + cloudData.rgb;
              }
              
              // --- Ray Traced Volumetric Fog Scattering ---
              // A proper forward ray march from the camera to the hit surface.
              // Each step samples all point lights. Because we only march to tMax (the surface),
              // fog cannot physically leak through any wall — no binary shadow tests needed.
              if (u.volFogEnabled > 0.5f && u.waterOnlyPass < 0.5f && !isCameraUnderwater) {
                  float3 volumetricFog = float3(0.0f);
                  int lightCount = int(uVoxel.gridSize.w);
                  float3 ro = uVoxel.camPos.xyz;
                  float3 rd = normalize(pWorld - ro);
                  float tMax = length(pWorld - ro);

                  // Skip very close surfaces (avoids self-fog on first-person hand)
                  if (tMax > 0.5f) {
                      // Clamp the ray march distance to the local voxel grid radius (56 blocks).
                      // The voxel grid only extends ~64 blocks around the player.
                      // Marching hundreds of meters into sky/clouds or distant horizons causes
                      // huge 20-50m steps, producing severe stipple/dither noise on clouds and distant water.
                      // Dynamic fog march distance — scales with user voxel radius setting.
                      // gridSize.x = grid diameter in voxels, so half = radius in blocks.
                      float gridHalfDim = float(uVoxel.gridSize.x) * 0.5f;
                      float marchDist = min(tMax, gridHalfDim);
                      // Cap steps to 12 max — 2x2 Bayer jitter delivers 4x perceived samples smoothly on M1 Air!
                      int numSteps = max(4, min(int(u.volFogSamples), 12));
                      float stepSize = marchDist / float(numSteps);

                      // World-space anchored step alignment + 2x2 Spatial Bayer Jitter:
                      // Interleaving samples across 2x2 pixel blocks effectively 4x-es the perceived step count,
                      // blurring away discrete step bands so Low and Medium step settings look silky smooth!
                      float bayer = float((gid.x & 1u) ^ ((gid.y & 1u) << 1u)) * 0.25f;
                      float worldDist = dot(ro, celestialDir);
                      float worldPhase = fract((worldDist + bayer * stepSize) / stepSize);
                      float tStart = stepSize * (0.20f + worldPhase * 0.60f);

                      // Extinction coefficient (how dense the participating media is)
                      float extinction = 0.045f * u.volFogIntensity;
                      // Scattering albedo (how much light bounces vs is absorbed)
                      float scatteringAlbedo = 0.85f;

                      // Directional Sun Light for God Rays / Volumetric Sunlight
                      // God rays: warm golden tint at sunrise/sunset, neutral at noon
                      float3 sunRayColor = currentSunColor * float3(1.04f, 0.95f, 0.82f);
                      // Golden hour: extra beam intensity and warmth when sun is near horizon
                      float goldenFogBoost = 1.0f + sunsetFactor * 1.20f;
                      float3 celestialCol = (sunWeight > 0.5f) ? (sunRayColor * 1.15f * goldenFogBoost) : (currentMoonColor * 0.45f);

                      // Dual-lobe phase function for God Rays:
                      // 1. Forward lobe (gSun = 0.65) for brilliant beams when facing the sun
                      // 2. Side-scattering lobe (isotropic 1/4pi) so beams can be seen from the side passing through rooms!
                      float cosThetaSun = dot(rd, celestialDir);
                      float gSun = 0.65f;
                      float gSun2 = gSun * gSun;
                      float forwardPhase = (1.0f - gSun2) / (4.0f * 3.14159265f * pow(max(1.0f + gSun2 - 2.0f * gSun * cosThetaSun, 0.04f), 1.5f));
                      float sidePhase    = 1.0f / (4.0f * 3.14159265f);
                      float phaseSun     = forwardPhase * 0.85f + sidePhase * 0.40f;

                      for (int step = 0; step < numSteps; step++) {
                          float t = tStart + float(step) * stepSize;
                          if (t >= marchDist) break;

                          float3 samplePos = ro + rd * t;
                          uint2 sVox = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, int3(floor(samplePos)));
                          if ((sVox.x & 4) != 0) break; // Air fog stops at water boundary

                          float3 inScatter = float3(0.0f);

                          // --- 1. Volumetric Sun / Moon Rays (God Rays) ---
                          if (celestialDir.y > 0.02f) {
                              float3 sunRayTarget = samplePos + celestialDir * 32.0f;
                              float3 sunTint = float3(1.0f);
                              float sunVis = traceVoxelShadowFast(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, samplePos, sunRayTarget, 16, sunTint);
                              if (sunVis > 0.01f) {
                                  inScatter += (celestialCol * sunTint) * (phaseSun * scatteringAlbedo * sunVis * 0.75f);
                              }
                          }

                          // --- 2. Volumetric Point Lights (3D Spatial Light Volumes) ---
                          // Two components:
                          //   a) Phase-dependent: HG forward lobe toward camera (current behaviour)
                          //   b) Omnidirectional spatial radiance: light that EXISTS in the 3D volume
                          //      regardless of view angle — this is what makes corridors light up
                          //      from any direction (Rockstar Shaders / MHRT behaviour).
                          for (int li = 0; li < lightCount && li < 32; li++) {
                              float3 lPos = uVoxel.lights[li].posAndRadius.xyz;
                              float lRad = uVoxel.lights[li].posAndRadius.w;

                              float3 toLight = lPos - samplePos;
                              float dist = length(toLight);
                              if (dist >= lRad) continue;

                              float3 lColor = uVoxel.lights[li].colorAndIntensity.xyz
                                            * uVoxel.lights[li].colorAndIntensity.w;

                              // Henyey-Greenstein phase function
                              float cosTheta = dot(rd, toLight / dist);
                              float g = 0.3f;
                              float g2 = g * g;
                              float phase = (1.0f - g2) / (4.0f * 3.14159265f * pow(1.0f + g2 - 2.0f * g * cosTheta, 1.5f));

                              // Smooth quadratic radial attenuation
                              float distNorm = dist / lRad;
                              float window = saturate(1.0f - distNorm * distNorm);
                              float attenuation = window * window;

                              // Handheld lights get greatly dimmed fog so the haze stays local
                              bool isHandheld = (length(lPos - uVoxel.camPos.xyz) < 1.6f)
                                             || (length(lPos - uVoxel.playerPos.xyz) < 1.8f);
                              
                              float3 lColorBase = uVoxel.lights[li].colorAndIntensity.xyz;
                              float fogMultiplier = 0.50f;
                              
                              // Torches: gentle warm glow, not an overwhelming dense cloud
                              if (abs(lColorBase.r - 1.0f) < 0.03f && abs(lColorBase.g - 0.65f) < 0.05f && abs(lColorBase.b - 0.22f) < 0.05f) {
                                  fogMultiplier = 0.18f;
                              }
                              // Sea Lanterns: cool clean atmospheric radiance
                              else if (abs(lColorBase.r - 0.40f) < 0.05f && abs(lColorBase.g - 0.92f) < 0.05f && abs(lColorBase.b - 1.00f) < 0.05f) {
                                  fogMultiplier = 0.35f;
                              }
                              // Regular Lanterns
                              else if (abs(lColorBase.r - 1.0f) < 0.03f && abs(lColorBase.g - 0.70f) < 0.05f && abs(lColorBase.b - 0.24f) < 0.05f) {
                                  fogMultiplier = 0.30f;
                              }
                              
                              if (isHandheld) {
                                  fogMultiplier = 0.03f;
                              }

                              // Fast shadow ray: fix self-occlusion on solid emissive blocks (glowstone, froglights).
                              // Pull shadow target 0.35 blocks back from lPos toward samplePos so DDA
                              // does not start inside the emissive voxel and immediately return 0.
                              float shadowVis = 1.0f;
                              float3 shadowTint = float3(1.0f);
                              if (!isHandheld && dist > 0.30f) {
                                  float3 toSample = normalize(samplePos - lPos);
                                  float3 shadowTarget = lPos + toSample * min(0.35f, dist * 0.20f);
                                  shadowVis = traceVoxelShadowFast(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, samplePos, shadowTarget, 16, shadowTint);
                              }

                              // a) Phase-dependent HG forward lobe (view-angle dependent)
                              float3 phaseContrib = (lColor * shadowTint) * (phase * attenuation * scatteringAlbedo * fogMultiplier * shadowVis);
                              // b) Omnidirectional 3D spatial radiance (g=0 isotropic, 1/(4pi))
                              //    Makes light fill corridors/hallways from ANY view angle.
                              //    Small weight (0.18) keeps directionality dominant.
                              float isotropicPhase = 1.0f / (4.0f * 3.14159265f);
                              float3 spatialContrib = (lColor * shadowTint) * (isotropicPhase * attenuation * scatteringAlbedo * fogMultiplier * shadowVis * 0.18f);
                              inScatter += phaseContrib + spatialContrib;
                          }

                          // Beer-Lambert transmittance along the ray up to this step
                          float transmittance = exp(-extinction * t);
                          volumetricFog += inScatter * (extinction * transmittance * stepSize);
                      }
                      volumetricFog *= u.volFogIntensity;
                  }

                  litRgb += volumetricFog;
              }
              
              // --- Luma-Preserving Filmic Tone Mapping ---
              // Preserves Minecraft's vibrant colors (lush green grass, deep blue sky, rich sunset)
              // with zero grey veil and no harsh black crushing in shadows.
              {
                  float exposure = mix(1.18f, 1.00f, sunsetFactor * sunWeight);
                  float saturationBoost = mix(1.28f, 1.40f, sunsetFactor * sunWeight);
                  litRgb = lumaPreservingFilmic(litRgb, exposure, saturationBoost);

                  // Subtle golden hour warm grade on midtones
                  float3 warmGrade = mix(float3(1.0f), float3(1.04f, 0.98f, 0.90f), sunsetFactor * sunWeight);
                  litRgb = mix(litRgb, litRgb * warmGrade, 0.40f);
              }

              outTexture.write(float4(litRgb, albedo.a), gid);
              return;
            }
