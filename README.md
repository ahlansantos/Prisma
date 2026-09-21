# Prisma

> [!IMPORTANT]
> Nightly builds (the best ones rn) are being released on discord first - this repo got only the older versions. (https://discord.gg/X8u3yJZQbm)

Native Apple Silicon Metal voxel shader engine for Minecraft and Sodium.

Prisma renders real-time lighting, analytical voxel shadows, and reflections directly through native Apple Metal pipelines (MSL), without the overhead of OpenGL compatibility layers.

## Performance Benchmark

- **Platform**: Apple Silicon Mac (Base Apple M1, 8GB)
- **Resolution**: 1650 x 1050
- **Settings**: 10 Render Distance chunks, 10 Voxelized Chunks, Double AO enabled, dynamic shadows active
- **Open world**: ~**60 FPS**
- **Heavy scenes** (lots of reflective surfaces and shadow casters): ~**30 FPS**

---

## Features

- **Ray-Traced Foliage & Cutout Shadows**:
  - Analytical and voxel ray tracing for cross-shaped plants and foliage (grass, tall flowers, fern, bamboo, sweet berry bushes, saplings).
  - Alpha cutout sampling directly from the block texture atlas during ray marching, preserving exact foliage silhouettes and leaf patterns without rendering solid rectangular blocks.
- **VXR (Voxel Reflections)**:
  - Real-time 3D voxel ray-traced reflections on water and glossy surfaces sampling real block textures directly from the Minecraft texture atlas (pure voxel ray marching, no screen-space planar artifacts).
  - Extended vertical grid tracking (up to 256 blocks tall) and ultra-long raymarching allows distant mountains and ravines to be fully reflected.
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

As Prisma is currently in Stable-Beta (v0.2.1-nightly), please keep the following limitations in mind:

1. **Voxel Grid Scope**:
- Voxelization operates within an active radius around the camera (configurable, default 2 chunks / 64 voxels). Terrain beyond the voxel radius reflects sky and ambient light rather than discrete voxel geometry - and water only shows correct inside the Voxel Radius.
2. **Hardware Scope**:
- Specifically optimized for macOS with Apple Silicon (M1, M2, M3, M4). Non-Apple platforms are not supported, I don't know about Intel Macs!
3. **Mod Compatibility**:
- **Incompatible with OptiFine, Iris, or other rendering overhaul mods.** Prisma completely replaces the rendering pipeline via native Apple Metal APIs; attempting to run alongside other shader loaders or massive rendering patches will result in crashes or visual corruption.
4. **Light Transmission**:
- Correct light transmission only works through translucent blocks for now. Water does not transmit light correctly yet (planned).
5. **Mob Lighting**:
- Only sunlight illuminates mobs right now. Moonlight, point lights, handheld lights and other light sources do not light them yet. This will be fixed.

---

## Future Plans

- **MetalFX Upscaling**: High-performance spatial and temporal upscaling for higher base resolutions.
- **AMD FSR Support**: Integration of FidelityFX Super Resolution for improved visuals and framerates.
- **FSR FrameGen**: Frame generation capabilities to dramatically push beyond 60 FPS.
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
2. Drop `20092026-0.2.1-nightly` into your `.minecraft/mods` folder.
3. Launch Minecraft and adjust options under **Video Settings -> Prisma**.

---

## Availability

Nightly and stable builds are distributed on Discord first; this repository is licensed under MIT. The full open-source release is planned for **v1.0.0**.

---

## Credits

Built on top of Metallum by kokodio. Powered by Fabric and Sodium.
