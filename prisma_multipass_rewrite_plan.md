# Prisma Phase 4 & 5 - Performance Rewrite & Denoiser

## Objective
Split the monolithic `prisma_deferred_cs` kernel into multiple discrete compute passes to relieve register pressure, increase occupancy, and massively improve frame rates on Apple Silicon (M1/M2/M3). Introduce a specialized GI Denoiser (Phase 5) to allow 1-ray-per-pixel path tracing.

## Passes Architecture
1.  **Voxel GI & Shadows Pass (`prisma_voxel_gi_cs.metal`)**:
    *   **Inputs**: `worldDepthTexture`, `normalTexture`, `voxelGrid`, `VoxelUniforms`.
    *   **Logic**: Raymarch the voxel grid to compute analytical shadows, bounced light (GI), and sky occlusion.
    *   **Output**: `giTexture` (RGBA16Float - RGB: Lighting, A: Shadow Mask/Darkening).
2.  **Volumetrics Pass (`prisma_volumetrics_cs.metal`)**:
    *   **Inputs**: `worldDepthTexture`, `CameraData`, `EnvironmentData`.
    *   **Logic**: Raymarch fog/clouds based on depth buffer.
    *   **Output**: `volumetricsTexture` (RGBA16Float).
    *   *Optimization*: Can run at half-resolution!
3.  **Spatial/Temporal Denoiser Pass (`prisma_denoiser_cs.metal`)**:
    *   **Inputs**: `giTexture` (noisy), `normalTexture`, `worldDepthTexture`, `velocityTexture`, `prevReservoirTex`.
    *   **Logic**: Blur and accumulate the GI texture over time and space, preserving edges via depth/normal variance.
    *   **Output**: `denoisedGiTexture`.
4.  **Composition / Deferred Base (`deferred.metal`)**:
    *   **Inputs**: `albedoTexture`, `denoisedGiTexture`, `volumetricsTexture`, `lightDataTexture` (blocklight/skylight), `normalTexture`.
    *   **Logic**: Material evaluation (PBR), ambient light composition, tone mapping (via `lib_tonemapping`).
    *   **Output**: `outTexture` (HDR Color).

## Java Implementation Steps (`MTLBuiltinPipelines.java` & `PrismaMRTManager.java`)
1.  Add `giTexture`, `volumetricsTexture`, and `denoisedGiTexture` allocation to `PrismaMRTManager`.
2.  Create new pipeline states (`ensureVoxelGIPipeline()`, `ensureVolumetricsPipeline()`, `ensureDenoiserPipeline()`).
3.  Modify `encodeDeferredLightingPass` (or break it up into a `renderFrame` orchestrator) to dispatch all passes sequentially, injecting `MTLBarrier` or `updateFence`/`waitForFence` between them to avoid data races.
