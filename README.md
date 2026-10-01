# Prisma

> **Notice:** Prisma is fully open-source under the MIT license. However, pre-compiled binaries and official releases are exclusively distributed via our [Discord Server](https://discord.gg/EtdRVPtxMA). 

Prisma is a **Native Apple Silicon Metal Shader Loader** for Minecraft and Sodium. 

By completely bypassing OpenGL, MoltenVK, and translation layers, Prisma loads and compiles `.metal` (MSL) shaderpacks directly to the GPU. This provides unprecedented performance for shaders on Mac. 

Prisma comes with a built-in flagship shaderpack: **Prisma's VXR Default**, which features real-time lighting, analytical voxel ray-traced shadows, and volumetric effects.

---

## Built-in Shader: Prisma's VXR Default

The engine comes with a flagship built-in shaderpack out of the box. Here are its features:

- **RTGI & VX RTAO:** Real-time Voxel Ray-Traced Global Illumination and Ambient Occlusion.
- **VXR (Voxel Reflections):** Real-time 3D voxel ray-traced reflections on water and glossy surfaces with authentic pixel-art sharpness, dynamic penumbra scaling, and point light shadow casting.
- **Unconstrained Penumbra Shadows:** Every light source casts physically accurate dynamic shadows. Leveraging procedural Vogel disk sampling, penumbra shadows scale flawlessly up to 32 simultaneous rays.
- **Ray Traced Volumetric Fog & God Rays:** Ground-up GPU ray march implementation with forward Henyey-Greenstein scattering, Beer-Lambert attenuation, atmospheric sun/moon crepuscular god rays, and colored stained-glass beam staining.
- **MetalFX & EASU Cascaded Upscaling:** Runs native resolutions seamlessly by enforcing cascaded spatial upscaling using Apple MetalFX and AMD EASU, coupled with contrast-adaptive Laplacian Sharpening.
- **Volumetric Clouds:** Raymarched clouds with dynamic lighting and self-shadowing.
- **Dynamic Weather System:** Includes rain puddles, wet surface ripples, and dynamic atmospheric haze.
- **Post-Processing Pipeline:** ACES Filmic Tonemapping, Vignette, and Laplacian reverse blur.

---

<details>
<summary><b>For Shader Developers</b></summary>

Prisma acts as an open standard MSL (Metal Shading Language) Shader Loader.

- **Dynamic Loading:** Prisma automatically scans the `shaderpacks/` directory for any folders containing a `shaders/` sub-directory with `.metal` files.
- **PrismaShaderAPI:** Built natively into Java, it provides an elegant hook for third-party modders to register custom MSL passes, inject custom uniforms, and control the underlying Metal pipeline directly.

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
- **RTAO Solid Blocks:** Ray-Traced Ambient Occlusion may occasionally treat partial blocks as full solid blocks (like leaves or slabs).
- **RTGI Noise:** Ray-Traced Global Illumination currently suffers from significant noise and ghosting, especially during rapid movement.
- **Hardware Demands:** Pushes hardware to its limits. On base models (like the M1 MacBook Air), performance will be heavily impacted unless you use the built-in 'M1 Air Low' preset.
</details>

---

## Credits

Built on top of Metallum by kokodio. Powered by Fabric and Sodium.
