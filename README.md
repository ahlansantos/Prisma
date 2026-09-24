# Prisma

> **Notice:** Prisma is fully open-source under the MIT license. However, pre-compiled binaries and official releases are exclusively distributed via our [Discord Server](https://discord.gg/X8u3yJZQbm). 

Prisma is a **Native Apple Silicon Metal Shader Loader** for Minecraft and Sodium (Starting in v0.2.4). 

By completely bypassing OpenGL, MoltenVK, and translation layers, Prisma loads and compiles `.metal` (MSL) shaderpacks directly to the GPU. This provides unprecedented performance for shaders on Mac. 

Prisma comes with a built-in flagship shaderpack: **Prisma's VXR Default**, which features real-time lighting, analytical voxel ray-traced shadows, and reflections.

---

## Performance

Tested on a Base Apple M1 (8GB RAM) at 1650 x 1050 resolution.

**Common Workload (10 Chunks, Double AO, Dynamic Shadows)**
- Open World: **40-50 FPS**

**Ultra Workload (12 Render Chunks, 8 Voxel Chunks, Full Reflections, Volumetric Clouds)**
- Native Resolution: **10-19 FPS**
- With TAAU (50%) + ASFW: **45-60 FPS**

---

## Built-in Shader: Prisma's VXR Default

The engine comes with a flagship built-in shaderpack out of the box. Here are its features:

- **ASFW (Async Space Frame Warp):** Native Frame Generation interpolation that artificially multiplies framerates by projecting previous frames based on camera velocity.
- **TAAU (Temporal Anti-Aliasing Upscaling):** Custom spatial and temporal upscaler running natively in MSL. Now utilizes **Catmull-Rom Bicubic spatial upscaling** and **CAUM (Contrast Adaptive Unsharp Mask)** for incredibly crisp details.
- **VXR (Voxel Reflections):** Real-time 3D voxel ray-traced reflections on water and glossy surfaces.
- **Volumetric Clouds:** Raymarched clouds with dynamic lighting and self-shadowing.
- **Dynamic Weather System:** Includes fog, rain puddles, and ripples on the ground.
- **Double AO:** Unified Voxel Ambient Occlusion (VXAO) and Screen-Space Ambient Occlusion (SSAO).
- **VPLS (Voxel Point Light Shadows):** Dynamic shadows for held and placed light sources.
- **Ray-Traced Foliage:** Alpha cutout sampling for precise foliage silhouettes and shadows.
- **Post-Processing Pipeline:** Velocity-Based Motion Blur, Native Bloom, ACES Filmic Tonemapping, and Vignette.

---


---

## For Developers (Custom Shaderpacks)

Starting from v0.2.4, Prisma is designed as a Shader Loader. It is entirely possible to port existing GLSL shaders to MSL (Metal Shading Language) or write your own from scratch.

- **No Official Tutorial Yet:** While the API is fully functional, comprehensive documentation for writing Prisma Shaderpacks is still a work in progress.
- **Optional Voxel APIs:** Shaders can optionally tap into Prisma's 3D Voxel Grid API. You can use it to create Ray Traced Point Light Shadows, Voxel-based Reflections, or ignore it entirely and write a traditional Screen-Space shader.
- **MakeUp Ultra Fast - Metal Port:** As a proof of concept, we are currently working on an official Native Metal port of the beloved [MakeUp Ultra Fast](https://modrinth.com/shader/makeup-ultra-fast) shaderpack!

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
