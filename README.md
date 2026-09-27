# Prisma: The Native Ray Traced Voxel Engine for macOS

[![Fabric 0.19.3](https://img.shields.io/badge/Fabric-0.19.3-lightgrey.svg)](https://fabricmc.net/)
[![Minecraft 26.3](https://img.shields.io/badge/Minecraft-26.3-brightgreen.svg)](https://minecraft.net/)
[![Apple Silicon](https://img.shields.io/badge/Apple-Silicon_Optimized-blue.svg)](#)
[![Metal API](https://img.shields.io/badge/Graphics-Apple_Metal-orange.svg)](#)

Prisma is a revolutionary ray tracing engine and shader loader designed natively for Minecraft macOS. Built from the ground up to bypass GLSL translation overhead, Prisma utilizes Apple's **Metal Shading Language (MSL)** and the Unified Memory Architecture of M-series chips to deliver pure path tracing performance.

## 🌟 Core Features

- **Native Voxel Ray Tracing**: Real-time access to a 3D Voxel Grid on the GPU, powered by heavily optimized DDA ray marching for accurate physical collision and reflections.
- **MetalFX & EASU Cascaded Upscaling**: Run natively at high resolutions using cascaded spatial upscaling (Apple MetalFX + AMD EASU), coupled with contrast-adaptive Laplacian Sharpening.
- **Unconstrained Penumbra Shadows**: Every light source casts physically accurate dynamic shadows. Using procedural Vogel disk sampling, penumbra shadows scale flawlessly up to 32 simultaneous rays.
- **Analytical Volumetric Scattering**: Beautiful, mathematically pure volumetric light bloom (fog) around point lights calculated natively along the view ray.
- **Open Shader API**: Built-in `PrismaShaderAPI` gives third-party mods elegant hooks to register custom MSL passes, inject custom uniforms, and control the rendering pipeline directly in Java.

## 🚀 Getting Started

1. Ensure you are running **Minecraft 26.3** with **Fabric Loader 0.19.3**.
2. Install the required dependency: **Sodium mc26.3-0.9.2**.
3. Drop the `prisma-26.3-preview-1.jar` into your `mods` folder.
4. Launch the game and access the Prisma Settings through the video menu.

*(Note: Prisma pushes macOS hardware to its limits. Base M1 Air devices may experience performance drops at full resolution on maximum settings).*

## 🛠 For Developers

Want to write a custom post-processing effect natively in MSL? Use the new `PrismaShaderAPI`.

```java
import com.prisma.api.PrismaShaderAPI;
import net.minecraft.util.Identifier;

public class MyAddon {
    public void init() {
        // Register a custom bloom pass
        PrismaShaderAPI.getInstance().registerCustomMetalPass(
            new Identifier("mymod", "custom_bloom"),
            "mymod:shaders/bloom.metal"
        );
        
        // Push uniforms directly to the GPU
        PrismaShaderAPI.getInstance().bindCustomUniform("u_bloomStrength", new float[]{ 1.5f });
    }
}
```

Check out our [Website & Docs](https://ahlansantos.github.io/prisma/) or join the [Discord](https://discord.gg/EtdRVPtxMA).

---
*Prisma is an independent engine. Not affiliated with Mojang AB or Apple Inc.*
