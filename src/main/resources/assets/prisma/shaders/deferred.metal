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
              float vxaoInReflections;
              
              float cloudsEnabled;
              float cloudSteps;
              float reflectionsEnabled;
              float cloudsInReflections;
              float rainStrength;
            };

                        static inline float3 computeEclipseWaterWaves(float2 pWorldXZ, float time, float strength, float speed) {
              if (strength <= 0.001f) return float3(0.0f, 1.0f, 0.0f);

              float2 wavePos = pWorldXZ * 1.5f;
              float angle = 0.0f;
              float frequency = 1.0f;
              float wSpeed = 1.2f * speed;
              float weight = 1.0f;
              float waveSum = 0.0f;
              float modTime = time * 0.65f;
              float2 dx = float2(0.0f);

              const float GOLDEN_ANGLE = 2.39996f;

              for (int i = 0; i < 5; i++) {
                float2 dir = float2(cos(angle), sin(angle));
                float x = dot(dir, wavePos) * frequency + modTime * wSpeed;
                float wave = exp(sin(x) - 1.0f);
                float result = wave * cos(x);
                float2 force = result * weight * dir;

                dx += force;
                wavePos -= force * 0.03f;
                angle += GOLDEN_ANGLE;
                waveSum += weight;
                weight *= 0.62f;
                frequency *= 1.55f;
                wSpeed *= 1.12f;
              }

              float2 waveSlope = -dx / max(waveSum, 0.001f);
              float normalMult = 0.06f * strength;
              return normalize(float3(waveSlope.x * normalMult, 1.0f, waveSlope.y * normalMult));
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

kernel void prisma_deferred_cs(
              uint2 gid [[thread_position_in_grid]],
              texture2d<float, access::write> outTexture [[texture(10)]],
              texture2d<float> albedoTex [[texture(0)]],
              texture2d<float> normalTex [[texture(1)]],
              texture2d<float> lightDataTex [[texture(2)]],
              depth2d<float> worldDepthTex [[texture(3)]],
              depth2d<float> handDepthTex [[texture(4)]],
              texture2d<float> blockAtlasTex [[texture(5)]],
              texture2d<float> playerSkinTex [[texture(6)]],
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

              float3 noonSunColor = float3(1.10f, 1.05f, 1.00f);
              float3 sunsetSunColor = float3(1.50f, 0.70f, 0.25f);
              float3 currentSunColor = mix(noonSunColor, sunsetSunColor, max(sunsetFactor, clampedSunrise));
              float3 currentMoonColor = float3(0.24f, 0.34f, 0.54f);
              float3 celestialDir = sunWeight > 0.5f ? sunDir : moonDir;

              float3 actualSky = float3(u.skyR, u.skyG, u.skyB);
              float3 sunriseTint = float3(u.sunriseR, u.sunriseG, u.sunriseB);
              float3 daySkyLight = mix(float3(1.00f, 0.98f, 0.95f), sunriseTint * 1.25f, clampedSunrise);
              float3 nightSkyLight = float3(0.08f, 0.15f, 0.35f);
              float3 activeSkyLight = mix(nightSkyLight, daySkyLight, sunWeight);

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

              float3 pLocal = pWorld - floor(pWorld);
              float3 distMin = pLocal;
              float3 distMax = 1.0f - pLocal;
              float3 blockNormal = float3(0.0f, 1.0f, 0.0f);
              float minDist = 1000.0f;
              if (distMin.x < minDist) { minDist = distMin.x; blockNormal = float3(-1.0f, 0.0f, 0.0f); }
              if (distMax.x < minDist) { minDist = distMax.x; blockNormal = float3(1.0f, 0.0f, 0.0f); }
              if (distMin.y < minDist) { minDist = distMin.y; blockNormal = float3(0.0f, -1.0f, 0.0f); }
              if (distMax.y < minDist) { minDist = distMax.y; blockNormal = float3(0.0f, 1.0f, 0.0f); }
              if (distMin.z < minDist) { minDist = distMin.z; blockNormal = float3(0.0f, 0.0f, -1.0f); }
              if (distMax.z < minDist) { minDist = distMax.z; blockNormal = float3(0.0f, 0.0f, 1.0f); }

              float2 ndcTrue = float2(uv.x * 2.0f - 1.0f, uv.y * 2.0f - 1.0f);
              float4 nearP = uVoxel.invViewProj * float4(ndcTrue, 1.0f, 1.0f);
              float4 farP = uVoxel.invViewProj * float4(ndcTrue, 0.0f, 1.0f);
              float3 trueRayDir = normalize(farP.xyz / max(farP.w, 1e-5f) - nearP.xyz / max(nearP.w, 1e-5f));
              float3 viewDirCam = -trueRayDir;

              bool badDerivative = dot(dX, dX) > 0.25f || dot(dY, dY) > 0.25f;
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

              bool isCurrBlock = ((voxCurr.x & 1) != 0) || ((voxCurr.x & 4) != 0);
              bool isIn1Block  = ((voxIn1.x & 1) != 0)  || ((voxIn1.x & 4) != 0);
              bool isIn2Block  = ((voxIn2.x & 1) != 0)  || ((voxIn2.x & 4) != 0);

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
                // Check if this surface block is water
                if (((insideVox.x & 4) != 0 && (insideVox.x & 8) == 0) || ((currVox.x & 4) != 0 && (currVox.x & 8) == 0)) {
                  isWater = true;
                }
                uint reflectType = (insideVox.x >> 12) & 0x0F;
                if (reflectType == 2u) {
                  isMetal = true;
                }
                if (reflectType == 1u) {
                  isGlass = true;
                }
              }

              float3 surfNormal = isEntity ? geomNormal : normalize(mix(geomNormal, nWorld, 0.70f));
              if (isWater) {
                float baseWaterY = (uVoxel.camPos.y >= pWorld.y) ? 1.0f : -1.0f;
                float3 waveNorm = computeEclipseWaterWaves(pWorld.xz, u.gameTime, u.waterWaveStrength, u.waterWaveSpeed);
                surfNormal = normalize(float3(waveNorm.x, baseWaterY * waveNorm.y, waveNorm.z));
                nWorld = float3(0.0f, baseWaterY, 0.0f);
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

              float vxaoStrength = uVoxel.camPos.w;
              float vxao = (!isEntity && gridWeight > 0.05f && vxaoStrength > 0.01f) ? computeVXAO(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, pWorld, nWorld, uVoxel.camPos.xyz, uVoxel.gridOrigin.w, (float2(gid) + 0.5f)) * vxaoStrength * gridWeight : 0.0f;
              float ssao = (!isEntity && vxaoStrength > 0.01f && vxao < 0.92f) ? computeSSAO(worldDepthTex, smp, uv, rawDepth, pWorld, surfNormal, uVoxel.camPos.xyz, uVoxel.viewProj, (float2(gid) + 0.5f)) * vxaoStrength : 0.0f;
              
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

              float combinedAo = saturate(max(vxao, ssao) * 0.80f) * saturate(1.0f - blockLevel * blockLevel);
              if (isFoliage) {
                combinedAo *= 0.40f; // Soften AO for tree leaves and vegetation
              }
              float ao = saturate(1.0f - combinedAo);
              float volumetricAo = mix(0.22f, 1.0f, pow(ao, 1.25f));



              bool isFirstPerson = (length(uVoxel.camPos.xyz - (uVoxel.playerPos.xyz + float3(0.0f, 1.5f, 0.0f))) < 0.60f);
              bool isGlassSurface = ((insideVox.x & 1) != 0) && (((insideVox.x >> 12) & 0x0F) == 1u);
              PointLightResult ptRes = uVoxel.camRight.w > 0.5f ? computePointLights(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, pWorld, surfNormal, uVoxel.gridSize.w, uVoxel.lights, uVoxel.playerPos, uVoxel.shadowParams, uVoxel.playerAnim, uVoxel.playerHead, uVoxel.mobCounts.x, uVoxel.mobs, (float2(gid) + 0.5f), blockAtlasTex, smp, blockUvTable, bitmaskTable, isGlassSurface, isFirstPerson) : PointLightResult{float3(0.0f), 0.0f, 0.0f};
              float3 pointLights = ptRes.color;

              float dayDampen = mix(1.0f, 0.22f, sunWeight * skyLevel);
              float3 scaledPtLight = pointLights * dayDampen;
              float3 smoothPointLights = scaledPtLight / (1.0f + scaledPtLight * 0.35f);
              float3 totalBlockLight = smoothPointLights;

              float minAmbient = mix(0.055f, 0.10f, sunWeight);
              if (isNether) {
                minAmbient = max(minAmbient, 0.11f);
              }
              minAmbient = max(minAmbient, uVoxel.playerHead.z);
              float3 ambientSky = max(activeSkyLight * (skyLevel * 0.68f), float3(0.03f, 0.025f, 0.02f));
              float celestialNdotL = saturate(dot(surfNormal, celestialDir));
              if (isFoliage) {
                // Two-sided transmission / wrap lighting for leaves
                celestialNdotL = max(celestialNdotL, saturate(-dot(surfNormal, celestialDir)) * 0.65f);
              }
              float3 celestialDirectCol = (sunWeight > 0.5f) ? (currentSunColor * 1.30f) : (currentMoonColor * 0.80f);

              float outsideShadow = 1.0f;
              float computedShadow = (u.sunShadowsEnabled > 0.5f && gridWeight > 0.02f && !isEntity) ? 0.0f : 1.0f;
              float3 computedTint = float3(1.0f);

              if (u.sunShadowsEnabled > 0.5f && gridWeight > 0.02f && !isEntity) {
                if (celestialNdotL > 0.01f && celestialDir.y > 0.001f && rawSky > 0.05f) {
                  float celestialSlopeBias = max(0.04f, 0.06f * (1.0f - celestialNdotL));
                  float3 rayStart = pWorld + nWorld * celestialSlopeBias;

                  float ign = fract(52.9829189f * fract(dot(float2(gid), float2(0.06711056f, 0.00583715f))));
                  float dAngle = ign * 6.2831853f;
                  float ditherX = cos(dAngle);
                  float ditherZ = sin(dAngle);
                  
                  float sdaaMode = uVoxel.shadowParams.z;
                  float radius = abs(sdaaMode) * 0.8f;
                  float3 j1 = float3(ditherX, 0.0f, ditherZ) * radius;
                float3 rDir1 = normalize(celestialDir * 40.0f + j1);
                if (dot(rDir1, nWorld) < 0.02f) {
                    rDir1 = normalize(rDir1 + nWorld * (0.02f - dot(rDir1, nWorld)));
                }
                float3 t1 = rayStart + rDir1 * 40.0f;
                ShadowRayResult cRes1 = traceDdaShadowRay(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, rayStart, t1, blockAtlasTex, smp, blockUvTable, bitmaskTable);
                
                bool canCastPlayerShadow = (uVoxel.shadowParams.w > 0.5f && length(rayStart.xz - uVoxel.playerPos.xz) < 12.0f);
                if (isFirstPerson) {
                    canCastPlayerShadow = canCastPlayerShadow && (nWorld.y > 0.55f && rayStart.y <= uVoxel.playerPos.y + 0.6f);
                }
                if (sdaaMode > 0.0f) {
                    float3 j2 = float3(-ditherZ, 0.0f, ditherX) * radius;
                    float3 j3 = float3(-ditherX, 0.0f, -ditherZ) * radius;
                    float3 rDir2 = normalize(celestialDir * 40.0f + j2);
                    if (dot(rDir2, nWorld) < 0.02f) {
                        rDir2 = normalize(rDir2 + nWorld * (0.02f - dot(rDir2, nWorld)));
                    }
                    float3 rDir3 = normalize(celestialDir * 40.0f + j3);
                    if (dot(rDir3, nWorld) < 0.02f) {
                        rDir3 = normalize(rDir3 + nWorld * (0.02f - dot(rDir3, nWorld)));
                    }
                    float3 t2 = rayStart + rDir2 * 40.0f;
                    float3 t3 = rayStart + rDir3 * 40.0f;
                    ShadowRayResult cRes2 = traceDdaShadowRay(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, rayStart, t2, blockAtlasTex, smp, blockUvTable, bitmaskTable);
                    ShadowRayResult cRes3 = traceDdaShadowRay(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, rayStart, t3, blockAtlasTex, smp, blockUvTable, bitmaskTable);
                    
                    if (canCastPlayerShadow) {
                        PlayerHit hit; hit.hitDist = 1e6f;
                        tracePlayerOBB(rayStart, rDir1, uVoxel.playerPos.xyz, uVoxel.shadowParams.x, uVoxel.playerHead.x, uVoxel.playerHead.y, uVoxel.playerAnim.x, uVoxel.playerAnim.y, uVoxel.playerAnim.z, uVoxel.playerAnim.w, playerSkinTex, smp, hit);
                        if (hit.hitDist > 0.15f && hit.hitDist < 40.0f) cRes1.vis = 0.0f;
                        
                        hit.hitDist = 1e6f;
                        tracePlayerOBB(rayStart, rDir2, uVoxel.playerPos.xyz, uVoxel.shadowParams.x, uVoxel.playerHead.x, uVoxel.playerHead.y, uVoxel.playerAnim.x, uVoxel.playerAnim.y, uVoxel.playerAnim.z, uVoxel.playerAnim.w, playerSkinTex, smp, hit);
                        if (hit.hitDist > 0.15f && hit.hitDist < 40.0f) cRes2.vis = 0.0f;
                        
                        hit.hitDist = 1e6f;
                        tracePlayerOBB(rayStart, rDir3, uVoxel.playerPos.xyz, uVoxel.shadowParams.x, uVoxel.playerHead.x, uVoxel.playerHead.y, uVoxel.playerAnim.x, uVoxel.playerAnim.y, uVoxel.playerAnim.z, uVoxel.playerAnim.w, playerSkinTex, smp, hit);
                        if (hit.hitDist > 0.15f && hit.hitDist < 40.0f) cRes3.vis = 0.0f;
                    }
                    computedShadow = (cRes1.vis + cRes2.vis + cRes3.vis) * 0.333f;
                    computedTint = (cRes1.tint + cRes2.tint + cRes3.tint) * 0.333f;
                } else {
                    if (canCastPlayerShadow) {
                        PlayerHit hit; hit.hitDist = 1e6f;
                        tracePlayerOBB(rayStart, rDir1, uVoxel.playerPos.xyz, uVoxel.shadowParams.x, uVoxel.playerHead.x, uVoxel.playerHead.y, uVoxel.playerAnim.x, uVoxel.playerAnim.y, uVoxel.playerAnim.z, uVoxel.playerAnim.w, playerSkinTex, smp, hit);
                        if (hit.hitDist > 0.15f && hit.hitDist < 40.0f) cRes1.vis = 0.0f;
                    }
                    computedShadow = cRes1.vis;
                    computedTint = cRes1.tint;
                }
                computedShadow = mix(computedShadow, 1.0f, u.rainStrength * 0.85f);
                }
              }

              float celestialShadow = mix(outsideShadow, computedShadow, gridWeight);
              float3 celestialTint = mix(float3(1.0f), computedTint, gridWeight);

              float3 directCelestial = celestialDirectCol * (celestialNdotL * skyLevel * 0.80f * celestialShadow) * celestialTint;
              float shadowAmbientFactor = mix(mix(1.0f, 0.50f, skyLevel), 1.0f, celestialShadow);
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

              if (isWater) {
                float waveSlopeSun = (surfNormal.x * celestialDir.x + surfNormal.z * celestialDir.z) * 0.45f * sunWeight;
                float waveSlopeSky = (surfNormal.y - 1.0f) * 0.50f;
                baseLighting *= clamp(1.0f + waveSlopeSun + waveSlopeSky, 0.65f, 1.35f);
              }

              bool isPuddle = false;
              if (u.rainStrength > 0.01f && !isWater && !isEntity && !isCameraInFluid && surfNormal.y > 0.82f && rawSky > 0.95f) {
                float puddleNoise = smoothNoise3D(float3(pWorld.xz * 0.35f, 0.0f));
                float puddleMask = smoothstep(0.40f, 0.65f, puddleNoise) * saturate(u.rainStrength * 1.5f);
                if (puddleMask > 0.01f) {
                  isPuddle = (puddleMask > 0.15f);
                  albedo.rgb *= mix(1.0f, 0.60f, puddleMask);
                  float2 rippleUv = pWorld.xz * 1.5f;
                  float rip1 = sin(length(fract(rippleUv) - 0.5f) * 24.0f - u.gameTime * 18.0f);
                  float rip2 = sin(length(fract(rippleUv + 0.43f) - 0.5f) * 20.0f - u.gameTime * 14.0f);
                  float rippleMask = smoothstep(0.3f, 0.7f, smoothNoise3D(float3(pWorld.xz * 1.2f, 0.0f))); float2 rippleOffset = float2(rip1 + rip2) * 0.045f * puddleMask * u.rainStrength * rippleMask;
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
                    int steps = (b == 0) ? 80 : 30;
                    float cloudsRefl = (b == 0) ? u.cloudsInReflections : 0.0f;
                    float3 skyReflection = evaluateSkyAndReflections(currentRayOrigin, currentRayDir, u.gameTime, actualSky, sunriseTint, clampedSunrise, currentSunColor, currentMoonColor, sunWeight, sunDir, moonDir, u.starBrightness, cloudsRefl, u.cloudSteps, u.rainStrength, 1e6f) * skyLevel;
                    
                                        VoxelReflResult vxr = traceVoxelReflections(voxelGrid, uVoxel.gridOrigin, uVoxel.gridSize, currentRayOrigin, currentRayDir, activeSkyLight, currentSunColor, currentMoonColor, celestialDir, sunWeight, blockAtlasTex, playerSkinTex, smp, blockUvTable, bitmaskTable, uVoxel.gridSize.w, uVoxel.lights, steps, u.maxPointLights, u.reflectionPtShadows, u.reflectionDirShadows, u.vxaoInReflections, 0.0f, u.rainStrength, u.gameTime, uVoxel);
                    
                    float reflDist = vxr.hitDist;
                    float rawReflFog = saturate(1.0f - exp(-pow(reflDist * 0.003f, 3.5f)));
                    float3 reflFogColor = evaluateSkyAndReflections(currentRayOrigin, currentRayDir, u.gameTime, actualSky, sunriseTint, clampedSunrise, float3(0.0f), float3(0.0f), sunWeight, sunDir, moonDir, u.starBrightness, 0.0f, u.cloudSteps, u.rainStrength, 1e6f);
                    
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

                float3 underWaterColor = albedo.rgb;
                float3 shallowColor = float3(0.08f, 0.45f, 0.65f);
                float3 deepColor = float3(0.01f, 0.15f, 0.35f);
                float3 toSurf = uVoxel.camPos.xyz - pWorld;
                float vertDist = max(abs(toSurf.y), 0.5f);
                float horizDist = length(toSurf.xz);
                float depthAngle = saturate(vertDist / (vertDist + horizDist * 0.5f));
                float3 waterVol = mix(deepColor, shallowColor, pow(depthAngle, 0.6f));
                float3 cleanWaterTint = isWater ? mix(underWaterColor, waterVol, saturate(0.35f * u.waterAbsorption)) : underWaterColor;
                albedo.rgb = cleanWaterTint;
                
                if (isMetal) {
                    accumulatedScene = min(accumulatedScene, 1.3f);
                    accumulatedScene *= mix(float3(1.0f), albedo.rgb, 0.90f);
                    fresnel = saturate(fresnel * 0.35f);
                }
                
                reflectionCol = accumulatedScene + specPoints * (isMetal ? 0.3f : 0.8f);
                reflectFactor = isWater ? saturate(fresnel * 0.85f + 0.08f) : (isPuddle ? saturate(fresnel * 0.90f + 0.05f) : saturate(fresnel));
              }

              bool isEmissiveBlock = (insideVox.x & 8) != 0;
              bool isOutsideGridEmissive = !isEntity && blockLevel > 0.92f;
              if (isEmissiveBlock || isOutsideGridEmissive) {
                float emStr = isMetal ? 1.15f : mix(1.0f, 1.6f, blockLevel);
                baseLighting = max(baseLighting, float3(emStr));
              }

              float3 baseLit = albedo.rgb * baseLighting;
              float3 litRgb = baseLit;
              if ((isWater || isMetal || isGlass || isPuddle) && u.reflectionsEnabled > 0.5f) {
                litRgb = mix(baseLit, reflectionCol, reflectFactor);
              }

              if (isCameraUnderwater) {
                float distToCam = length(pWorld - uVoxel.camPos.xyz);
                float fogFactor = saturate(distToCam * 0.04f);
                float3 rtxWater = mix(float3(0.005f, 0.03f, 0.15f), float3(0.02f, 0.55f, 0.75f), saturate(1.0f - distToCam / 32.0f));
                litRgb = mix(litRgb, rtxWater * max(activeSkyLight, float3(0.3f)), fogFactor);
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
                
                // Exponential distance fog (thicker to hide chunks better)
                                float distFog = 1.0f - exp(-pow(distToCam * 0.003f, 3.5f));
                float heightFog = exp(-(pWorld.y - 40.0f) * 0.03f) * saturate(distToCam * 0.015f);
                
                float fogFactor = saturate(distFog + heightFog);
                float3 fogColor = evaluateSkyAndReflections(uVoxel.camPos.xyz, rayDir, u.gameTime, actualSky, sunriseTint, clampedSunrise, float3(0.0f), float3(0.0f), sunWeight, sunDir, moonDir, u.starBrightness, 0.0f, u.cloudSteps, u.rainStrength, 1e6f);

                                                // Darken fog in caves (but keep it bright under trees)
                float surfaceBoost = saturate((pWorld.y - 50.0f) * 0.05f); 
                float caveDarkness = saturate(skyLevel * 4.0f + surfaceBoost);
                fogColor *= max(caveDarkness, 0.0f);

                float cosSunTheta = dot(rayDir, sunDir);
                float miePhase = (1.0f - 0.78f*0.78f) / pow(max(1.0f + 0.78f*0.78f - 2.0f*0.78f*cosSunTheta, 0.01f), 1.5f);
                float3 mieGlare = currentSunColor * (miePhase * 0.035f * sunWeight * (1.0f - u.rainStrength * 0.80f)) * skyLevel * celestialShadow;
                fogColor += mieGlare;
                
                fogFactor = saturate(fogFactor + u.rainStrength * 0.85f * (1.0f - exp(-distToCam * 0.04f)));

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
              
              outTexture.write(float4(litRgb, albedo.a), gid);
              return;
            }
