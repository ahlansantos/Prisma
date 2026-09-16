# Prisma RT

> [!IMPORTANT]
> **The Legacy VRTE (Voxel Ray Tracing Engine) is DEAD.** 
> Starting with 0.2.0RT-Alpha (codename Yeezus), Prisma has been completely rewritten from the ground up to use **Pure Native Hardware Ray Tracing** via Apple Metal's `MTLAccelerationStructure`.

Native Apple Silicon Metal Ray Traced engine for Minecraft and Sodium. Prisma injects real-time lighting, analytical shadows, and ray-marched reflections directly into the Metal pipelines using MSL, completely bypassing OpenGL compatibility layers.

By utilizing hardware Ray Tracing Cores on M3+ (and highly optimized simulated RT compute pipelines on M1/M2), Prisma delivers real-time BVH tracing for gorgeous lighting and shadows tailored exactly for Apple Silicon.

---

## Features

- **Real-Time Hardware Ray-Traced Shadows**
  - Accurate, real-time shadows cast across terrain and geometry using native Metal BVH structures.
  - **Dynamic Penumbra:** Physically-based soft shadows that organically blur as the distance from the caster increases.
  - Seamless, mathematical celestial crossfading between the Sun and Moon.
- **Analytical Lighting Overhaul**
  - Completely replaces vanilla Minecraft's blocky lightmaps with a smooth, hemispherical ambient sky light system. 
  - Dynamic shading that respects surface normals and perfectly blends with the environment.
- **Water Physics & Sky Reflections**
  - Fully functional Trochoidal water waves reacting to real-time specular lighting.
  - Pure sky and celestial body reflections on water surfaces.
- **Sodium 1.20+ Integration**
  - Tightly coupled with Sodium's chunk meshing and render passes to extract raw vertex data and build dynamic TLAS/BLAS without crushing the CPU.

---

## Performance Benchmark (M1)

Right now, Prisma builds its Ray Tracing BVH strictly from the visible chunks (piggybacking on Sodium's aggressive Frustum and Occlusion culling). While this saves memory, tracing rays natively per-pixel is computationally intensive.

- **Platform**: Apple Silicon Mac (Base Apple M1, 8GB)
- **Resolution**: 1650 x 1050
- **Settings**: 2 Render Distance chunks, Ray Traced Directional Shadows, Water Waves.
- **Framerate**: **Playable (35-55 FPS)**

> [!TIP]
> **Roadmap:** The upcoming integration of **MetalFX Spatial Upscaling** is expected to effectively double these framerates on base M-series Macs.

---

## Current Technical Limitations (Alpha)

As Prisma is transitioning into its new Hardware RT architecture, please keep the following limitations in mind:
1. **Frustum Culling Clipping:** The BVH currently relies on Sodium's visible chunk graph. Chunks immediately behind the player are culled by Sodium and are thus missing from the BVH, which can cause shadows cast from behind the camera to clip or disappear. (A global 360-degree BVH is planned for the future).
2. **BVH Texturing (Alpha Testing):** Currently, transparent cutout blocks (like tall grass, flowers, saplings) cast solid rectangular shadows. Full intersection function mapping for BVH texture alpha-testing is actively in development.
3. **Hardware Scope:** Specifically optimized for macOS with Apple Silicon (M1, M2, M3, M4). Intel Macs and non-Apple platforms are structurally incompatible and unsupported.

---

## Requirements

- **OS**: macOS 13 (Ventura) or newer
- **Hardware**: Apple Silicon Mac (M1, M2, M3, M4)
- **Minecraft**: 26.2
- **Dependencies**: Fabric Loader 0.19.2+, Fabric API, Sodium 0.9.1+

---

## Installation

1. Install Fabric Loader, Fabric API, and Sodium.
2. Drop `prisma-0.2.0RT-Alpha.jar` into your `.minecraft/mods` folder.
3. Launch Minecraft and adjust options under **Video Settings -> Prisma**.

---

## Availability

Prisma is currently in Alpha and closed source. Pre-compiled binaries are published on Modrinth and GitHub Releases. The project will transition to an open-source license upon reaching version 1.0.0.

---

## Credits

Built on top of Metallum by kokodio. Powered by Fabric and Sodium.

