# Prisma 💎

> [!IMPORTANT]
> **Prisma is now 100% open-source!** 
> Join our [Discord](https://discord.gg/X8u3yJZQbm) for nightly builds and updates.

Native Apple Silicon Metal voxel shader engine for Minecraft and Sodium. Prisma renders real-time lighting, analytical voxel shadows, and reflections directly through Apple Metal (MSL), bypassing OpenGL layers.

---

## 🚀 Performance (Base Apple M1)

**Common Test (10 Chunks, Double AO, Dynamic Shadows)**
- **~40-50 FPS** (Open world)

**Ultra Test (4 Chunks, Full Reflections, Volumetric Clouds)**
- **Native Resolution**: 10-20 FPS
- **With TAAU (50%)**: 30-40 FPS
- **With TAAU (50%) + FrameWarp**: 60-70 FPS

---

## ✨ Key Features

- **Native FrameWarp (FrameGen):** Asynchronous Space Warp interpolation for massive FPS boosts.
- **TAAU (Temporal Upscaling):** Custom spatial/temporal upscaler running natively in MSL.
- **VXR (Voxel Reflections):** Real-time 3D voxel ray-traced reflections on water and glossy surfaces.
- **Volumetric Clouds & Weather:** Raymarched clouds with dynamic lighting, rain puddles, and ripples.
- **Double AO:** Unified Voxel Ambient Occlusion (VXAO) + Screen-Space (SSAO).
- **VPLS (Voxel Point Light Shadows):** Dynamic shadows for held/placed light sources (torches, lanterns).
- **Foliage Ray Tracing:** Alpha cutout sampling for precise foliage shadows.
- **Native Post-Processing:** Bloom, ACES Filmic Tonemapping, Vignette, and Film Grain.

---

## ⚙️ Requirements & Compatibility

- **Hardware**: Apple Silicon (M1/M2/M3/M4) running macOS 13+
- **Minecraft**: 26.2 (Java 25+)
- **Dependencies**: Fabric Loader 0.19.2+, Sodium 0.9.1+
- ⚠️ **Incompatible with Iris or OptiFine** (Prisma entirely replaces the rendering pipeline).

---

## 🛠️ Installation

1. Install Fabric and Sodium.
2. Drop the `.jar` into your `.minecraft/mods` folder.
3. Configure settings in **Video Settings -> Prisma**.

---

## 📝 Known Limitations (Beta)
- **Voxel Grid Radius**: Terrain reflects sky/ambient light beyond the active voxel chunk radius.
- **FrameWarp Ghosting**: Minor edge ghosting on very fast camera swipes (TAAU highly recommended).
- **Light Transmission**: Currently only works through solid translucent blocks (glass), water transmission is WIP.

---
**Credits:** Built on top of Metallum by kokodio. Powered by Fabric and Sodium. Licensed under MIT.
