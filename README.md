# Prisma

> **Notice:** Prisma is fully open-source under the MIT license. However, pre-compiled binaries and official releases are exclusively distributed via our [Discord Server](https://discord.gg/X8u3yJZQbm). 

Prisma is a native Apple Silicon Metal voxel shader engine for Minecraft and Sodium. It renders real-time lighting, analytical voxel shadows, and reflections directly through Apple Metal (MSL), bypassing OpenGL compatibility layers for maximum performance on macOS.

---

## Performance

Tested on a Base Apple M1 (8GB RAM) at 1650 x 1050 resolution.

**Common Workload (10 Chunks, Double AO, Dynamic Shadows)**
- Open World: **40-50 FPS**

**Ultra Workload (12 Render Chunks, 8 Voxel Chunks, Full Reflections, Volumetric Clouds)**
- Native Resolution: **10-19 FPS**
- With TAAU (50%) + ASFW: **45-60 FPS**

---

## Features

- **ASFW (Async Space Frame Warp):** Native Frame Generation interpolation that artificially multiplies framerates by projecting previous frames based on camera velocity.
- **TAAU (Temporal Anti-Aliasing Upscaling):** Custom spatial and temporal upscaler running natively in MSL, powered by **CAS (Contrast Adaptive Sharpening)** for incredibly crisp upscaled details.
- **VXR (Voxel Reflections):** Real-time 3D voxel ray-traced reflections on water and glossy surfaces.
- **Volumetric Clouds:** Raymarched clouds with dynamic lighting and self-shadowing.
- **Dynamic Weather System:** Includes fog, rain puddles, and ripples on the ground.
- **Double AO:** Unified Voxel Ambient Occlusion (VXAO) and Screen-Space Ambient Occlusion (SSAO).
- **VPLS (Voxel Point Light Shadows):** Dynamic shadows for held and placed light sources.
- **Ray-Traced Foliage:** Alpha cutout sampling for precise foliage silhouettes and shadows.
- **Post-Processing Pipeline:** Native Bloom, ACES Filmic Tonemapping, and Vignette.

---

## Requirements

- **OS:** macOS 13 or newer
- **Hardware:** Apple Silicon (M-Series)
- **Minecraft:** 26.2 (Java 25+)
- **Dependencies:** Fabric Loader 0.19.2+, Sodium 0.9.1+
- **Incompatible:** Iris, OptiFine, or any other rendering mods (Prisma entirely replaces the rendering pipeline).

---

## Installation

1. Install Fabric Loader and Sodium.
2. Download the latest Prisma `.jar` from Discord.
3. Drop the `.jar` into your `.minecraft/mods` folder.
4. Configure options under **Video Settings -> Prisma**.

---

## Known Limitations

- **Voxel Grid Radius:** Terrain beyond the active voxel chunk radius reflects sky and ambient light rather than discrete geometry.
- **ASFW Hardware Compatibility:** FrameWarp (ASFW) currently ONLY works on M1 and M2 chips. On M3 and newer architectures, it produces a black screen flicker due to Dynamic Caching memory barriers. We are working on a fix.
- **ASFW Artifacts:** On supported chips, very fast camera sweeps may produce minor edge ghosting. Using TAAU alongside ASFW is highly recommended.
- **Light Transmission:** Currently only supports solid translucent blocks (like stained glass). Light transmission through water is a work in progress.

---

## Credits

Built on top of Metallum by kokodio. Powered by Fabric and Sodium.
