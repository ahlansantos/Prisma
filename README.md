# Prisma

> [!IMPORTANT]
> Nightly builds (the best ones rn) are being released on discord first - this repo got only the older versions. (https://discord.gg/X8u3yJZQbm)

Native Apple Silicon Metal voxel shader engine for Minecraft and Sodium.

Prisma renders real-time lighting, analytical voxel shadows, and reflections directly through native Apple Metal pipelines (MSL), without the overhead of OpenGL compatibility layers.

## Performance Benchmark

### Common Test

- **Platform**: Apple Silicon Mac (Base Apple M1, 8GB)
- **Resolution**: 1650 x 1050
- **Settings**: 10 Render Distance chunks, 10 Voxelized Chunks, Double AO enabled, dynamic shadows active
- **Open world**: ~**~40-50 FPS**
- **Heavy scenes** (lots of reflective surfaces and shadow casters): ~**~25-30 FPS**

### Ultra Test

- **Settings**: 4 Render Distance chunks, 4 Voxelized Chunks, Double AO enabled, dynamic shadows active, all reflections enabled, Volumetric Clouds enabled
- **Native Resolution**: ~**10-20 FPS**
- **With TAAU (50%)**: ~**30-40 FPS**
- **With TAAU (50%) + FrameWarp (1.5x)**: ~**60-70 FPS**

---

## Features

- **Ray-Traced Foliage & Cutout Shadows**:
  - Analytical and voxel ray tracing for cross-shaped plants and foliage (grass, tall flowers, fern, bamboo, sweet berry bushes, saplings).
  - Alpha cutout sampling directly from the block texture atlas during ray marching, preserving exact foliage silhouettes and leaf patterns without rendering solid rectangular blocks.
- **VXR (Voxel Reflections)**:
  - Real-time 3D voxel ray-traced reflections on water and glossy surfaces sampling real block textures directly from the Minecraft texture atlas (pure voxel ray marching, no screen-space planar artifacts).
  - Extended vertical grid tracking (up to 256 blocks tall) and ultra-long raymarching allows distant mountains and ravines to be fully reflected.
- **Volumetric Clouds**:
  - Real-time raymarched volumetric clouds with dynamic lighting and self-shadowing.
  - Fully responds to time of day, sunrise/sunset colors, and weather conditions.
- **Weather System**:
  - Dynamic weather with fog, rain puddles, and ripples on the ground.
  - Clouds automatically darken based on rain strength.
  - Directional shadows become softer and fade out smoothly during rain.
- **TAAU (Temporal Anti-Aliasing Upscaling)**:
  - Custom-built spatial and temporal upscaler running natively in MSL. Dramatically boosts performance by rendering internally at a lower resolution (e.g. 50%) and accumulating historical frame data to reconstruct a sharp native-resolution image.
- **FrameWarp (Asynchronous Space Warp)**:
  - Experimental native Frame Generation technique. Projects previous frames using camera velocity (translation and rotation) and depth buffers to artificially multiply frame rates without putting extra load on the CPU or rendering pipeline.
- **Double AO (Unified VXAO + SSAO)**:
  - **VXAO (Voxel Ambient Occlusion)**: 3D volumetric occlusion computed for broad contact shadows.
  - **SSAO (Screen-Space Ambient Occlusion)**: High-frequency sub-block occlusion.
  - Both AO passes are completely unified on the same plane and apply dynamically across all geometry (solid blocks and foliage alike), smoothly blending together using organic mathematical overlapping.
- **Post-Processing Pipeline (basic)**:
  - A basic post-processing pipeline is already in place and running natively in MSL.
  - **ACES Filmic Tonemapping**: Standard dynamic range, smooth highlight roll-offs, and true-to-life contrast curves.
  - **Vignette**: Subtle screen-edge vignetting.
  - **Dithering / Film Grain**: Very light spatial noise that breaks shadow color-banding and adds a hint of film feel.
- **Light Transmission (translucent blocks)**:
  - Correct light transmission through translucent blocks (such as stained glass). Water is planned to come next.
- **VPLS (Voxel Point Light Shadows)**:
  - Dynamic point light shadows with soft contact penumbra for held and placed light sources (torches, lanterns, campfires, soul variants).
  - Dynamic handheld light tracking with automatic self-shadow suppression.
- **WaterWaves**:
  - Trochoidal wave animation with physical slope lighting, specular highlights from both point lights and celestial bodies, and Fresnel reflection blending.
- **Settings Rework**:
  - Cleanly integrated into Sodium Video Settings (`Video Settings -> Prisma`).
  - Categorized into intuitive tabs: *Lighting & Shadows*, *Water & Fluids*, *Diagnostics & Debug*.

---

## Technical Limitations (Beta)

As Prisma is currently in Stable-Beta (v0.2.2-revision4), please keep the following limitations in mind:

1. **Voxel Grid Scope**:
- Voxelization operates within an active radius around the camera (configurable, default 2 chunks / 64 voxels). Terrain beyond the voxel radius reflects sky and ambient light rather than discrete voxel geometry - and water only shows correct inside the Voxel Radius.
2. **Hardware Scope**:
- Specifically optimized for macOS with Apple Silicon (M1, M2, M3, M4). Non-Apple platforms are not supported, I don't know about Intel Macs!
3. **Mod Compatibility**:
- **Incompatible with OptiFine, Iris, or other rendering overhaul mods.** Prisma completely replaces the rendering pipeline via native Apple Metal APIs; attempting to run alongside other shader loaders or massive rendering patches will result in crashes or visual corruption.
4. **Light Transmission**:
- Correct light transmission only works through translucent blocks for now. Water does not transmit light correctly yet (planned).
5. **FrameWarp Artifacts**:
- FrameWarp is in its initial version. You may experience slightly floating input latency (camera lag) and minor visual ghosting/tearing off-screen when doing very fast camera sweeps. TAAU is heavily recommended to be used alongside it to mitigate the latency.

---

## Future Plans

- **FrameWarp Refinements**: Further optimization of our native Frame Generation to reduce ghosting and input lag, possibly utilizing Metal motion vectors.
- **PBR Support (Physically Based Rendering)**: Normal maps, specular maps, and emission rendering utilizing LabPBR standard resource packs.
- **Water Light Transmission**: Correct light transmission through water.
- **Advanced Post-Processing**: Depth of field, bloom, motion blur, and color grading on top of the current basic pipeline, natively written in MSL.

---

## Requirements

- **OS**: macOS 13 (Ventura) or newer
- **Hardware**: Apple Silicon Mac (M1, M2, M3, M4)
- **Minecraft**: 26.2
- **Java**: 25 or newer
- **Dependencies**: Fabric Loader 0.19.2+, Sodium 0.9.1+ (required)
- **Incompatible**: Iris and other shader/rendering mods (Prisma refuses to load alongside Iris)

---

## Installation

1. Install Fabric Loader and Sodium.
2. Drop `23092026-0.2.2-revision4` into your `.minecraft/mods` folder.
3. Launch Minecraft and adjust options under **Video Settings -> Prisma**.

---

## Availability

Prisma is now **100% open-source** and licensed under MIT! Nightly and stable builds are distributed on our Discord first, and source code is fully available right here.

---

## Credits

Built on top of Metallum by kokodio. Powered by Fabric and Sodium.