runc# Prisma

> **Notice:** Prisma is fully open-source under the MIT license. However, pre-compiled binaries and official releases are exclusively distributed via our [Discord Server](https://discord.gg/X8u3yJZQbm). 

Prisma is a **Native Apple Silicon Metal Shader Loader** for Minecraft and Sodium (Starting in v0.2.4). 

By completely bypassing OpenGL, MoltenVK, and translation layers, Prisma loads and compiles `.metal` (MSL) shaderpacks directly to the GPU. This provides unprecedented performance for shaders on Mac. 

Prisma comes with a built-in flagship shaderpack: **Prisma's VXR Default**, which features real-time lighting, analytical voxel ray-traced shadows, and reflections.

---

## Built-in Shader: Prisma's VXR Default

The engine comes with a flagship built-in shaderpack out of the box. Here are its features:

- **ASFW (Async Space Frame Warp):** Native Frame Generation interpolation that artificially multiplies framerates by projecting previous frames based on camera velocity.
- **PEU (Prisma Experimental Upscaling):** Edge-adaptive spatial upscaling running natively in MSL. Inspired by EASU (FSR 1.0), it dynamically detects edge contrast and reconstructs crisp details for practically lossless visuals at 50% native resolution, completely eliminating blur.
- **VXR (Voxel Reflections):** Real-time 3D voxel ray-traced reflections on water and glossy surfaces.
- **Volumetric Clouds:** Raymarched clouds with dynamic lighting and self-shadowing.
- **Dynamic Weather System:** Includes fog, rain puddles, and ripples on the ground.
- **Double AO:** Unified Voxel Ambient Occlusion (VXAO) and Screen-Space Ambient Occlusion (SSAO).
- **VPLS (Voxel Point Light Shadows):** Dynamic shadows for held and placed light sources.
- **Ray-Traced Foliage:** Alpha cutout sampling for precise foliage silhouettes and shadows.
- **Post-Processing Pipeline:** Velocity-Based Motion Blur, Native Bloom, ACES Filmic Tonemapping, and Vignette.

---

<details>
<summary><b>For Shader Developers</b></summary>

Starting in version 0.2.4, Prisma acts as an open standard MSL (Metal Shading Language) Shader Loader.

- **Dynamic Loading:** Prisma will automatically scan the `shaderpacks/` directory for any folders containing a `shaders/` sub-directory with `.metal` files.
- **VXR Isolation:** Advanced built-in features like PEU (Prisma Experimental Upscaler) and ASFW (Frame Generation) are strictly isolated to the built-in VXR Default shader. Custom shaders render perfectly at native resolution with no G-Buffer bleeding or distortion.
- **Ray Traced Shadows Template:** Looking to build advanced lighting? Join our [Discord Server](https://discord.gg/X8u3yJZQbm) to download an extended Metal shader template that includes built-in voxel ray-traced shadows!

</details>

## Installation

1. Install Fabric Loader and Sodium.
2. Download the latest Prisma `.jar` from Discord.
3. Drop the `.jar` into your `.minecraft/mods` folder.
4. Configure options under **Video Settings -> Shader Packs**.

---

<details>
<summary><b>Known Limitations (Prisma's VXR Default)</b></summary>

- **Voxel Grid Radius:** Terrain beyond the active voxel chunk radius reflects sky and ambient light rather than discrete geometry.
- **ASFW Hardware Compatibility:** FrameWarp (ASFW) currently ONLY works on M1 and M2 chips. On M3 and newer architectures, it produces a black screen flicker due to Dynamic Caching memory barriers. We are working on a fix.
- **ASFW Artifacts:** On supported chips, very fast camera sweeps may produce minor edge ghosting. Using PEU alongside ASFW is highly recommended.
- **Light Transmission:** Currently only supports solid translucent blocks (like stained glass). Light transmission through water is a work in progress.

</details>

---

## Credits

Built on top of Metallum by kokodio. Powered by Fabric and Sodium.
