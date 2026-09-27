# Prisma

> **Notice:** Prisma is fully open-source under the MIT license. However, pre-compiled binaries and official releases are exclusively distributed via our [Discord Server](https://discord.gg/EtdRVPtxMA). 

Prisma is a **Native Apple Silicon Metal Shader Loader** for Minecraft and Sodium. 

By completely bypassing OpenGL, MoltenVK, and translation layers, Prisma loads and compiles `.metal` (MSL) shaderpacks directly to the GPU. This provides unprecedented performance for shaders on Mac. 

Prisma comes with a built-in flagship shaderpack: **Prisma's VXR Default**, which features real-time lighting, analytical voxel ray-traced shadows, and volumetric scattering.

---

## Built-in Shader: Prisma's VXR Default

The engine comes with a flagship built-in shaderpack out of the box. Here are its features:

- **Native Voxel Ray Tracing:** Real-time access to a 3D Voxel Grid stored directly on the GPU, powered by heavily optimized DDA ray marching for accurate physical collision and reflections.
- **MetalFX & EASU Cascaded Upscaling:** Runs native resolutions seamlessly by enforcing cascaded spatial upscaling using Apple MetalFX and AMD EASU, coupled with contrast-adaptive Laplacian Sharpening.
- **Unconstrained Penumbra Shadows:** Every light source casts physically accurate dynamic shadows. Leveraging procedural Vogel disk sampling, penumbra shadows scale flawlessly up to 32 simultaneous rays per light source.
- **Analytical Volumetric Scattering:** Features a physically-based volumetric fog halo around light sources computed analytically along the ray, independent of world fog.
- **Pure Compute Shaders:** Replaces legacy fragment shaders with pure Metal Compute Shaders for exponentially faster dispatch.

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
- **Hardware Demands:** Prisma pushes hardware to its limits. Running the engine at native full-resolution with all settings maxed out may cause performance drops on base models (like the M1 MacBook Air).
- **Light Transmission:** Currently only supports solid translucent blocks (like stained glass). Light transmission through water is a work in progress.

</details>

---

## Credits

Built on top of Metallum by kokodio. Powered by Fabric and Sodium.
