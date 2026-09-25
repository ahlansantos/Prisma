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

              float2 wavePos = pWorldXZ * 0.8f;
              float angle = 0.5f;
              float frequency = 1.0f;
              float wSpeed = 1.5f * speed;
              float weight = 1.0f;
              float waveSum = 0.0f;
              float modTime = time * 0.85f;
              float2 dx = float2(0.0f);

              const float GOLDEN_ANGLE = 2.39996f;

              for (int i = 0; i < 7; i++) {
                float2 dir = float2(cos(angle), sin(angle));
                float x = dot(dir, wavePos) * frequency + modTime * wSpeed;
                x += cos(wavePos.y * 0.5f - modTime * 0.2f) * 0.5f;
                float wave = exp(sin(x) - 1.0f);
                float result = wave * cos(x);
                float2 force = result * weight * dir;

                dx += force;
                wavePos -= force * 0.05f;
                angle += GOLDEN_ANGLE;
                waveSum += weight;
                weight *= 0.55f;
                frequency *= 1.65f;
                wSpeed *= 1.15f;
              }

              float2 waveSlope = -dx / max(waveSum, 0.001f);
              float normalMult = 0.08f * strength;

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

fragment float4 prisma_deferred_fs(
              DeferredVertexOut in [[stage_in]],
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
              float4 albedo = albedoTex.sample(smp, in.uv);
              float wDepth = worldDepthTex.sample(smp, in.uv);
              float hDepth = handDepthTex.sample(smp, in.uv);
              float rawDepth = wDepth;
              float4 lightData = lightDataTex.sample(smp, in.uv);
              float effectiveDepth = rawDepth;
              if (effectiveDepth <= 0.00005f && lightData.z > 0.5f && lightData.w > 0.00005f) {
                effectiveDepth = lightData.w;
              }

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
                    return float4(albedo.rgb, albedo.a); // Let vanilla handle Nether and End skies
                }
                float4 nearPoint = uVoxel.invViewProj * float4(in.uv * 2.0f - 1.0f, 1.0f, 1.0f);
                float4 farPoint = uVoxel.invViewProj * float4(in.uv * 2.0f - 1.0f, 0.001f, 1.0f);
                float3 pNear = nearPoint.xyz / max(nearPoint.w, 0.00001f);
                float3 pFar = farPoint.xyz / max(farPoint.w, 0.00001f);
                float3 rayDir = normalize(pFar - pNear);

                float3 skyCol = evaluateSkyAndReflections(uVoxel.camPos.xyz, rayDir, u.gameTime, actualSky, sunriseTint, clampedSunrise, currentSunColor, currentMoonColor, sunWeight, sunDir, moonDir, u.starBrightness, u.cloudsEnabled, u.cloudSteps, u.rainStrength, 1e6f);
                float luma = dot(albedo.rgb, float3(0.299f, 0.587f, 0.114f)); float isRain = saturate((luma - 0.2f) * 10.0f) * u.rainStrength; return float4(mix(skyCol, albedo.rgb, isRain * 0.6f), 1.0f);
              }


              if (hDepth > 0.0001f) {
                return float4(albedo.rgb, albedo.a);
              }

              float3 pWorld = reconstructWorldPos(in.uv, effectiveDepth, uVoxel.camPos.xyz, uVoxel.invViewProj);

              float4 nSample = normalTex.sample(smp, in.uv);

              bool isEntity = lightData.z < 0.5f;
              float3 nWorld = isEntity ? normalize(nSample.xyz) : normalize(nSample.xyz);

              int3 voxInside = int3(floor(pWorld - nWorld * 0.06f));
              uint2 insideVox = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, voxInside);
              int3 voxAt = int3(floor(pWorld + nWorld * 0.15f));
              uint2 atVox = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, voxAt);

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
                if ((insideVox.x & 4) != 0) {
                  if ((insideVox.x & 8) == 0) {
                    isWater = true;
                  }
                }
                uint reflectType = (insideVox.x >> 12) & 0x0F;
                if (reflectType == 2u) {
                  isMetal = true;
                }
                if (reflectType == 1u) {
                  isGlass = true;
                } else if (nWorld.y > 0.40f && viewDir.y < -1e-4f) {


                  int3 voxDirectlyAbove = int3(floor(pWorld.x), floor(pWorld.y) + 1.0f, floor(pWorld.z));
                  uint2 aboveVox = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, voxDirectlyAbove);
                  if ((aboveVox.x & 4) != 0) {
                    int topY = voxDirectlyAbove.y;
                    for (int step = 0; step < 16; step++) {
                      uint2 testVox = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, int3(voxDirectlyAbove.x, topY + 1, voxDirectlyAbove.z));
                      if ((testVox.x & 4) == 0) break;
                      topY++;
                    }
                    float waterTop = float(topY) + 0.88f;
                    if (uVoxel.camPos.y > waterTop) {
                      float tW = (waterTop - uVoxel.camPos.y) / viewDir.y;
                      if (tW > 0.0f && tW < distToSurface) {
                        float3 pWater = uVoxel.camPos.xyz + viewDir * tW;
                        int3 checkVox = int3(floor(pWater.x), float(topY), floor(pWater.z));
                        uint2 cVox = readVoxel(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, checkVox);
                        if ((cVox.x & 4) != 0) {
                          isWater = true;
                          pWorld = pWater;
                          pSurfaceRel = pWorld - uVoxel.camPos.xyz;
                          nWorld = float3(0.0f, 1.0f, 0.0f);
                        }
                      }
                    }
                  }
                }
              }

              float3 surfNormal = nWorld;
              if (isWater) {
                float3 waveNorm = computeEclipseWaterWaves(pWorld.xz, u.gameTime, u.waterWaveStrength, u.waterWaveSpeed);
                if (abs(nWorld.y) > 0.65f) {
                  surfNormal = normalize(float3(waveNorm.x, nWorld.y > 0.0f ? waveNorm.y : -waveNorm.y, waveNorm.z));
                }
              }



              float vxaoStrength = uVoxel.camPos.w;
              float vxao = (!isEntity && vxaoStrength > 0.01f) ? computeVXAO(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, pWorld, nWorld, uVoxel.camPos.xyz, uVoxel.gridOrigin.w, in.position.xy) * vxaoStrength : 0.0f;
              float ssao = (!isEntity && vxaoStrength > 0.01f && vxao < 0.92f) ? computeSSAO(worldDepthTex, smp, in.uv, rawDepth, pWorld, surfNormal, uVoxel.camPos.xyz, uVoxel.viewProj, in.position.xy) * vxaoStrength : 0.0f;
              float rawSky = (lightData.z > 0.5f) ? lightData.g : (float((atVox.x >> 8) & 0x0F) / 15.0f);
              float rawBlock = (lightData.z > 0.5f) ? lightData.r : (float((atVox.x >> 4) & 0x0F) / 15.0f);
              if (lightData.z <= 0.5f && (atVox.x & 1) == 0 && (insideVox.x & 1) == 0) {
                rawSky = 1.0f;
              }
              float skyLevel = get_vanilla_brightness(rawSky);
              float blockLevel = get_vanilla_brightness(rawBlock);

              float combinedAo = saturate(max(vxao, ssao) * 0.80f) * saturate(1.0f - blockLevel * blockLevel);
              float ao = saturate(1.0f - combinedAo);
              float volumetricAo = mix(0.22f, 1.0f, pow(ao, 1.25f));



              bool isGlassSurface = ((insideVox.x & 1) != 0) && (((insideVox.x >> 12) & 0x0F) == 1u);
              PointLightResult ptRes = uVoxel.camRight.w > 0.5f ? computePointLights(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, pWorld, surfNormal, uVoxel.gridSize.w, uVoxel.lights, uVoxel.playerPos, uVoxel.shadowParams, uVoxel.playerAnim, uVoxel.playerHead, uVoxel.mobCounts.x, uVoxel.mobs, in.position.xy, blockAtlasTex, smp, blockUvTable, bitmaskTable, isGlassSurface) : PointLightResult{float3(0.0f), 0.0f, 0.0f};
              float3 pointLights = ptRes.color;

              float dayDampen = mix(1.0f, 0.22f, sunWeight * skyLevel);
              float3 scaledPtLight = pointLights * dayDampen;
              float3 smoothPointLights = scaledPtLight / (1.0f + scaledPtLight * 0.35f);
              float3 totalBlockLight = smoothPointLights;

              float minAmbient = mix(0.055f, 0.10f, sunWeight);
              if (isNether) {
                minAmbient = max(minAmbient, 0.11f);
              }
              float3 ambientSky = max(activeSkyLight * (skyLevel * 0.68f), float3(0.03f, 0.025f, 0.02f));
              float celestialNdotL = saturate(dot(surfNormal, celestialDir));
              float3 celestialDirectCol = (sunWeight > 0.5f) ? (currentSunColor * 1.30f) : (currentMoonColor * 0.80f);

              float celestialShadow = 1.0f;
              float3 celestialTint = float3(1.0f);

              if (celestialNdotL > 0.0f && celestialDir.y > 0.001f) {
                float celestialSlopeBias = mix(0.045f, 0.012f, celestialNdotL);
                float3 rayStart = pWorld + nWorld * celestialSlopeBias;
                float temporalFrame = fract(u.gameTime * 20.0f) * 64.0f;
                float ditherX = fract(52.9829189f * fract(dot(in.position.xy + float2(temporalFrame, temporalFrame * 1.618f), float2(0.06711056f, 0.00583715f)))) * 2.0f - 1.0f;
                float ditherZ = fract(52.9829189f * fract(dot(in.position.xy + float2(13.0f - temporalFrame, 17.0f + temporalFrame), float2(0.06711056f, 0.00583715f)))) * 2.0f - 1.0f;
                float3 jitter = float3(ditherX, 0.0f, ditherZ) * uVoxel.shadowParams.z * 1.8f;
                float3 shadowTarget = rayStart + normalize(celestialDir * 40.0f + jitter) * 40.0f;

                ShadowRayResult cRes = traceDdaShadowRay(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, rayStart, shadowTarget, blockAtlasTex, smp, blockUvTable, bitmaskTable);
                
                PlayerHit hit;
                hit.hitDist = 1e6f;
                float3 sDir = normalize(shadowTarget - rayStart);
                tracePlayerOBB(rayStart, sDir, uVoxel.playerPos.xyz, uVoxel.shadowParams.x, uVoxel.playerHead.x, uVoxel.playerHead.y, uVoxel.playerAnim.x, uVoxel.playerAnim.y, uVoxel.playerAnim.z, uVoxel.playerAnim.w, playerSkinTex, smp, hit);
                if (hit.hitDist > 0.0f && hit.hitDist < length(shadowTarget - rayStart)) {
                    cRes.vis = 0.0f;
                }

                celestialShadow = cRes.vis;
                celestialTint = cRes.tint;
                celestialShadow = mix(celestialShadow, 1.0f, u.rainStrength * 0.85f);
              }

              float3 directCelestial = celestialDirectCol * (celestialNdotL * skyLevel * 0.80f * celestialShadow) * celestialTint;
              float shadowAmbientFactor = mix(0.50f, 1.0f, celestialShadow);
              float3 baseAmbient = ambientSky * shadowAmbientFactor;



              float3 skyLight = baseAmbient + directCelestial;

              float daylightShadowSuppression = mix(1.0f, 0.30f, sunWeight * skyLevel);
              float shadowOcclusion = saturate(ptRes.shadowDarkening * 0.55f * daylightShadowSuppression);

              float3 shadowedSkyLight = skyLight * (1.0f - shadowOcclusion);

              float3 baseLighting = shadowedSkyLight + totalBlockLight + float3(minAmbient);

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
                float4 nearPoint = uVoxel.invViewProj * float4(in.uv * 2.0f - 1.0f, 1.0f, 1.0f);
                float3 pNear = nearPoint.xyz / max(nearPoint.w, 0.00001f);
                float3 viewDir = normalize((uVoxel.camPos.xyz + pNear) - pWorld);
                float NdotV = saturate(dot(surfNormal, viewDir));

                float3 currentRayOrigin = pWorld;
                float3 currentRayDir = reflect(-viewDir, surfNormal);
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
                                specPoints += uVoxel.lights[i].colorAndIntensity.xyz * (uVoxel.lights[i].colorAndIntensity.w * spec * smoothAtten * 2.2f);
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
                    
                    currentRayOrigin = currentRayOrigin + currentRayDir * vxr.hitDist;
                    currentRayDir = reflect(currentRayDir, vxr.normal);
                    currentAttenuation *= vxr.reflectivity;
                }

                float f0 = isMetal ? 0.85f : (isGlass ? 0.15f : (isPuddle ? 0.15f : 0.02f));
                float fresnel = f0 + (1.0f - f0) * pow(1.0f - NdotV, 5.0f);

                float3 underWaterColor = albedo.rgb;
                float3 shallowColor = float3(0.05f, 0.58f, 0.55f);
                float3 deepColor = float3(0.01f, 0.12f, 0.25f);
                float3 toSurf = uVoxel.camPos.xyz - pWorld;
                float vertDist = max(abs(toSurf.y), 0.5f);
                float horizDist = length(toSurf.xz);
                float depthAngle = saturate(vertDist / (vertDist + horizDist * 0.5f));
                float3 waterVol = mix(deepColor, shallowColor, pow(depthAngle, 0.6f));
                float3 cleanWaterTint = isWater ? mix(underWaterColor, waterVol, saturate(0.75f * u.waterAbsorption)) : underWaterColor;
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
              bool isOutsideGridEmissive = (lightData.z > 0.5f) && !isEntity && blockLevel > 0.92f;
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
                fogColor *= mix(0.01f, 1.0f, caveDarkness);

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
              
              return float4(litRgb, albedo.a);
            }
