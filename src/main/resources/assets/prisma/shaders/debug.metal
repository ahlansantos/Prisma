            struct DebugVertexOut {
              float4 position [[position]];
              float2 uv;
            };
            struct DebugUniforms {
              int debugMode;
              int dummy;
              float aspect;
              float fovScale;
            };
            vertex DebugVertexOut prisma_debug_vs(uint vertexId [[vertex_id]]) {
              const float2 positions[3] = { float2(-1.0, 1.0), float2(3.0, 1.0), float2(-1.0, -3.0) };
              const float2 uvs[3] = { float2(0.0, 0.0), float2(2.0, 0.0), float2(0.0, 2.0) };
              DebugVertexOut out;
              out.position = float4(positions[vertexId], 0.0, 1.0);
              out.uv = uvs[vertexId];
              return out;
            }
            fragment float4 prisma_debug_fs(
              DebugVertexOut in [[stage_in]],
              texture2d<float> albedoTex [[texture(0)]],
              texture2d<float> normalTex [[texture(1)]],
              texture2d<float> lightDataTex [[texture(2)]],
              depth2d<float> worldDepthTex [[texture(3)]],
              depth2d<float> handDepthTex [[texture(4)]],
              texture2d<float> blockAtlasTex [[texture(5)]],
              texture2d<float> playerSkinTex [[texture(6)]],
              sampler smp [[sampler(0)]],
              constant DebugUniforms& u [[buffer(0)]],
              device const uint2* voxelGrid [[buffer(1)]],
              constant VoxelUniforms& uVoxel [[buffer(2)]],
              constant float4* blockUvTable [[buffer(3)]],
              constant ulong* bitmaskTable [[buffer(4)]]
            ) {
              float4 albedo = albedoTex.sample(smp, in.uv);
              float4 nSample = normalTex.sample(smp, in.uv);
              float4 lightData = lightDataTex.sample(smp, in.uv);
              float wDepth = worldDepthTex.sample(smp, in.uv);
              float hDepth = handDepthTex.sample(smp, in.uv);
              float effectiveDepth = (wDepth <= 0.00005f && lightData.z > 0.5f && lightData.w > 0.00005f) ? lightData.w : wDepth;
              if (effectiveDepth <= 0.00005f && hDepth <= 0.0001f) return float4(0.0);
              if (hDepth > 0.0001f) return float4(albedo.rgb, 1.0);
              float3 pWorld = reconstructWorldPos(in.uv, effectiveDepth, uVoxel.camPos.xyz, uVoxel.invViewProj);
              bool isEntity = lightData.z < 0.5f;
              float3 nWorld;
              if (isEntity) {
                  float3 dFdxPos = dfdx(pWorld);
                  float3 dFdyPos = dfdy(pWorld);
                  float3 crossNorm = cross(dFdxPos, dFdyPos);
                  nWorld = length(crossNorm) < 0.0001f ? normalize(uVoxel.camPos.xyz - pWorld) : normalize(crossNorm);
              } else {
                  nWorld = normalize(nSample.xyz * 2.0f - 1.0f);
              }
              if (u.debugMode == 1) {
                float dist = length(pWorld - uVoxel.camPos.xyz);
                return float4(float3(fract(dist / 16.0f)), 1.0f);
              } else if (u.debugMode == 2) {
                return float4(nWorld * 0.5f + 0.5f, 1.0f);
              } else if (u.debugMode == 3 || u.debugMode == 6 || u.debugMode == 7) {
                float blockLevel = lightData.x;
                float vxao = computeVXAO(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, pWorld, nWorld, uVoxel.camPos.xyz, uVoxel.gridOrigin.w, in.position.xy);
                float ssao = computeSSAO(worldDepthTex, smp, in.uv, wDepth, pWorld, nWorld, uVoxel.camPos.xyz, uVoxel.viewProj, in.position.xy);
                float combinedAo = saturate(max(vxao, ssao) * 0.80f) * saturate(1.0f - blockLevel * blockLevel);
                if (u.debugMode == 6) return float4(float3(1.0f - vxao), 1.0f);
                if (u.debugMode == 7) return float4(float3(1.0f - ssao), 1.0f);
                return float4(float3(1.0f - combinedAo), 1.0f);
              } else if (u.debugMode == 4) {
                PointLightResult pt = computePointLights(voxelGrid, uVoxel.gridOrigin.xyz, uVoxel.gridSize.xyz, pWorld, nWorld, uVoxel.gridSize.w, uVoxel.lights, uVoxel.playerPos, uVoxel.shadowParams, uVoxel.playerAnim, uVoxel.playerHead, uVoxel.mobCounts.x, uVoxel.mobs, in.position.xy, blockAtlasTex, smp, blockUvTable, bitmaskTable, false);
                return float4(pt.color, 1.0f);
              }
              return float4(albedo.rgb, 1.0f);
            }