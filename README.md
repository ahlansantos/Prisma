# Prisma

Native Apple Silicon Metal voxel shader engine for Minecraft and Sodium.

Prisma renders real-time lighting, analytical voxel shadows, and reflections directly through native Apple Metal pipelines (MSL), delivering 60+ FPS on base M-series Macs without the overhead of OpenGL compatibility layers.

## Performance Benchmark

- **Platform**: Apple Silicon Mac (Base Apple M1, 8GB / 16GB)
- **Resolution**: 1650 x 1050
- **Settings**: 16 Render Distance chunks, 2 Voxelized Chunks (64x64 horizontal radius), Double AO enabled, dynamic shadows active
- **Framerate**: **60 FPS stable**

---

## Features

- **Ray-OBB Entity Shadows (Beta)**:
  - Real-time analytical 3D ray-traced shadows for the player model (head, torso, arms, legs) with realistic proportions and animations (walking limb swing, crouch forward pitch, head yaw/pitch).
  - Analytical Ray-OBB shadows for key world mobs (Cows, Pigs, Chickens, Skeletons, Zombies, Drowned with swimming poses, Witches, Villagers, Cats, Wolves, and Spiders) with synchronized limb movements and head tracking.
  - Seamless ground contact: eliminates artificial exclusion holes under feet and paws.
- **Ray-Traced Foliage & Cutout Shadows**:
  - Analytical and voxel ray tracing for cross-shaped plants and foliage (grass, tall flowers, fern, bamboo, sweet berry bushes, saplings).
  - Alpha cutout sampling directly from the block texture atlas during ray marching, preserving exact foliage silhouettes and leaf patterns without rendering solid rectangular blocks.
- **VXR (Voxel Reflections)**:
  - Real-time 3D voxel ray-traced reflections on water and glossy surfaces sampling real block textures directly from the Minecraft texture atlas (pure voxel ray marching, no screen-space planar artifacts).
  - Includes player and mob reflections with animated limbs and directional lighting.
- **Double AO (Hybrid VXAO + SSAO)**:
  - **VXAO (Voxel Ambient Occlusion)**: 3D volumetric occlusion computed against solid world geometry for deep, natural corner contact shadows.
  - **SSAO (Screen-Space Ambient Occlusion)**: High-frequency sub-block occlusion specifically targeting non-solid blocks (stairs, slabs, trapdoors, fences, foliage) and dynamic entities, preventing flat shading.
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

As Prisma is currently in Beta (v0.1.3-B), please keep the following limitations in mind:

1. **Entity Whitelist for Ray-OBB**:
   - Analytical multi-box ray tracing is currently implemented for the Player and 11 primary terrestrial/aquatic mobs (Zombies, Skeletons, Drowned, Witches, Villagers, Cows, Pigs, Chickens, Cats, Wolves, Spiders).
   - Other mobs (such as horses, iron golems, or endermen) are rendered with approximate bounding shapes until their specific bone hierarchies are added.
2. **Entity Reflection Textures**:
   - Terrain blocks in reflections sample the full per-pixel texture atlas.
   - Dynamic entities in water reflections currently use procedural, model-matched palette shading rather than per-entity skin texture maps.
3. **Voxel Grid Scope**:
   - Voxelization operates within an active radius around the camera (configurable, default 2 chunks / 64 voxels). Terrain beyond the voxel radius reflects sky and ambient light rather than discrete voxel geometry.
4. **Hardware Scope**:
   - Specifically optimized for macOS with Apple Silicon (M1, M2, M3, M4). Intel Macs and non-Apple platforms are not supported.

---

## Requirements

- **OS**: macOS 13 (Ventura) or newer
- **Hardware**: Apple Silicon Mac (M1, M2, M3, M4)
- **Minecraft**: 26.2
- **Dependencies**: Fabric Loader 0.19+, Fabric API, Sodium

---

## Installation

1. Install Fabric Loader, Fabric API, and Sodium.
2. Drop `prisma-0.1.3-B.jar` into your `.minecraft/mods` folder.
3. Launch Minecraft and adjust options under **Video Settings -> Prisma**.

---

## Availability

Prisma is currently in Beta and closed source until version 1.0.0. Pre-compiled binaries are published on Modrinth and GitHub Releases. The project will transition to an open-source license upon reaching version 1.0.0.

---

## Credits

Built on top of Metallum by kokodio. Powered by Fabric and Sodium.

