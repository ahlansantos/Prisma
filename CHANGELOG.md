# Prisma Changelog

## [26.3-Preview.1] - The Engine Overhaul
*The biggest architectural leap for Prisma, bringing native Compute Shaders, unbounded dynamic shadows, and API access.*

### ✨ New Features & Enhancements
- **Compute Shaders Upgrade**: Replaced legacy fragment shaders with pure Metal Compute Shaders for exponentially faster dispatch and execution.
- **Analytical Volumetric Scattering**: Added a new physically-based volumetric fog halo around light sources computed analytically along the ray without relying on world fog.
- **MetalFX Cascaded Spatial Upscaling**: Forced cascaded mode (Apple MFX + AMD EASU) with unified Base Render and Intermediate Scale sliders.
- **Laplacian Sharpening**: Replaced the old blurry unsharp mask with a true contrast-adaptive reverse blur (Laplacian filter) to combat MFX softness.
- **Open PrismaShaderAPI**: Introduced a native Java API (`PrismaShaderAPI`) allowing other developers to seamlessly register MSL passes, bind uniforms, and interact with the Voxel Grid.

### 🌓 Lighting & Shadows
- **Unconstrained Shadow Casting**: Removed arbitrary analytical light limits (ReSTIR). All point lights now cast fully accurate physical shadows simultaneously.
- **Unified Penumbra Scaling**: Giant 0-32 scalable ray slider now accurately governs both Sun and Point Light soft shadows.
- **Dynamic Torch Volumetrics**: Specifically tuned the volumetric scattering intensity for torches (0.005x) vs other light sources like Lava/Glowstone (0.12x) for better immersion.

### 🐛 Bug Fixes
- **Vanilla Bleed Fix**: Removed hacky vanilla lightmap blending that caused glowing surfaces and light bleeding near torches.
- **Light Leakage & Collision**: Fixed precision errors causing shadows to clip through walls.
- **Carpet Shadows**: Carpets now correctly bypass the voxel grid shadow casters, preventing black artifacts on the ground.
- **Foliage Reflection Fix**: Eliminated the neon green artifacting on transparent cross-blocks (grass/flowers) near water reflections.

### ⚙️ UI & Under The Hood
- **Ported to Minecraft 26.3**: Fully upgraded the entire engine architecture to run seamlessly on MC 26.3.
- **Settings UI Overhaul**: Cleaned up dead settings, removed broken SDAA anti-aliasing, and intelligently grouped Shadow Quality sliders.

---

## [26.3-Rev.2]
- **Procedural Vogel Disk**: Implemented procedural vogel disk sampling for soft shadows, drastically reducing noise.
- **Analytical Point Lights**: Initial introduction of analytical shadow casting for point lights overriding block maps.
- **MSL Shader Rewrite**: Massive backend rewrite for deferred and post-process passes natively in Metal Shading Language.
- **Compilation Errors**: Resolved multiple strict compiler warnings, unused variables, and scope leaks in MSL.
