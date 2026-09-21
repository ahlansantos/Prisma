# Prisma

> [!IMPORTANT]
> Nightly builds (the best ones rn) are being released on discord first - this repo got only the older versions. (https://discord.gg/X8u3yJZQbm)

Native Apple Silicon Metal voxel shader engine for Minecraft and Sodium.

Prisma renders real-time lighting, analytical voxel shadows, and reflections directly through native Apple Metal pipelines (MSL), delivering 60+ FPS on base M-series Macs without the overhead of OpenGL compatibility layers.

## Performance Benchmark

- **Platform**: Apple Silicon Mac (Base Apple M1, 8GB)
- **Resolution**: 1650 x 1050
- **Settings**: 10 Render Distance chunks, 10 Voxelized Chunks, Double AO enabled, dynamic shadows active
- **Framerate**: **30 FPS stable**

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
- **Initial Cinematic Pipeline (without a renderpass)**:
  - **ACES Filmic Tonemapping**: High dynamic range, smooth highlight roll-offs, and true-to-life contrast curves.
  - **Cinematic Post-Processing**: Includes subtle screen-edge vignetting and spatial noise dithering (Film Grain) to entirely break shadow color-banding and add a 35mm film feel.
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

As Prisma is currently in Stable-Beta (v0.2.0-hotfix), please keep the following limitations in mind:

1. **Voxel Grid Scope**:
  - Voxelization operates within an active radius around the camera (configurable, default 2 chunks / 64 voxels). Terrain beyond the voxel radius reflects sky and ambient light rather than discrete voxel geometry - and water only shows correct inside the Voxel Radius.
2. **Hardware Scope**:
  - Specifically optimized for macOS with Apple Silicon (M1, M2, M3, M4). Non-Apple platforms are not supported, I don't know about Intel Macs!
3. **Mod Compatibility**:
  - **Incompatible with OptiFine, Iris, or other rendering overhaul mods.** Prisma completely replaces the rendering pipeline via native Apple Metal APIs; attempting to run alongside other shader loaders or massive rendering patches will result in crashes or visual corruption.
4. **Shadow Transmission**:
  - Shadows currently do not support color transmission or soft fading through transparent surfaces (such as stained glass or deep water). They will cast solid or completely transparent shadows depending on the block type.

---

## Future Plans

- **MetalFX Upscaling**: High-performance spatial and temporal upscaling for higher base resolutions.
- **AMD FSR Support**: Integration of FidelityFX Super Resolution for improved visuals and framerates.
- **FSR FrameGen**: Frame generation capabilities to dramatically push beyond 60 FPS.
- **PBR Support (Physically Based Rendering)**: Normal maps, specular maps, and emission rendering utilizing LabPBR standard resource packs.
- **Post-Processing Pipeline**: Advanced depth of field, bloom, motion blur, and color grading capabilities natively written in MSL.

---

## Requirements

- **OS**: macOS 13 (Ventura) or newer
- **Hardware**: Apple Silicon Mac (M1, M2, M3, M4)
- **Minecraft**: 1.21.x
- **Dependencies**: Fabric Loader 0.19+, Fabric API, Sodium

---

## Installation

1. Install Fabric Loader, Fabric API, and Sodium.
2. Drop `20092026-0.2.1-nightly` into your `.minecraft/mods` folder.
3. Launch Minecraft and adjust options under **Video Settings -> Prisma**.

---

## Availability

Prisma will be officially **open-sourced starting from v1.0.0**!

---

## Credits

Built on top of Metallum by kokodio. Powered by Fabric and Sodium.
