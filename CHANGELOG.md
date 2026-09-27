# Changelog

## [26.3-Preview.1]

### Features
- **Compute Shaders:** Replaced legacy fragment shaders with pure Metal Compute Shaders for faster dispatch.
- **Volumetric Scattering:** Added analytical volumetric bloom around point lights.
- **MetalFX & EASU:** Integrated cascaded spatial upscaling with contrast-adaptive Laplacian sharpening.
- **PrismaShaderAPI:** New Java API for developers to register custom MSL passes and uniforms natively.
- **Unconstrained Point Lights:** Removed ReSTIR limits. All point lights now simultaneously cast accurate physical shadows.
- **Penumbra Scaling:** Unified 0-32 ray sampling slider for both sun and point light soft shadows.
- **Dynamic Torch Volumetrics:** Custom scattering intensity for torches to balance immersion.

### Fixes
- Ported entire engine architecture to Minecraft 26.3.
- Removed vanilla lightmap blending to prevent light bleeding near torches.
- Fixed precision errors causing point light shadows to clip through solid walls.
- Fixed carpet shadow collision, preventing ground artifacts.
- Fixed neon green rendering artifacts on transparent cross-blocks (grass/flowers) in water reflections.
- Overhauled settings UI, grouped shadow sliders, and removed deprecated SDAA.

---

## [26.3-Rev.2]

- Implemented procedural Vogel disk sampling for soft shadows.
- Introduced analytical shadow casting for point lights.
- Rewrote deferred and post-process passes natively in Metal Shading Language.
- Resolved strict compiler warnings and scope leaks in MSL.
