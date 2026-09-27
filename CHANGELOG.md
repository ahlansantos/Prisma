# Changelog

## [26.3-Preview.2]

### Features & Improvements
- **Ray Traced Volumetric Fog Scattering:** Complete ground-up GPU ray march implementation with forward Henyey-Greenstein scattering and Beer-Lambert attenuation.
- **Volumetric Sunlight & Moon God Rays:** Atmospheric crepuscular beams cast from the sun and moon directly through open sky and windows.
- **Colored Glass Volumetric Light Staining:** Sunlight and point light beams passing through stained glass accurately take on the color of the glass media.
- **Linked Reflection Penumbra:** Soft reflection shadows are now dynamically tied to the world shadow penumbra slider (0 = razor sharp, >0 = realistic soft penumbra).
- **Physical Point Light Shadows in Reflections:** Replaced fast voxel approximation with full DDA raytracing for point lights inside reflections.
- **Pixel-Art Crisp Reflections:** Removed blurry bilinear filtering from all voxel reflection texture samples (`filter::nearest`), restoring authentic Minecraft sharpness.
- **Glass Transparency in Reflections:** Reflection rays now traverse transparent glass panes instead of stopping on opaque surfaces.
- **3rd-Person Player Shadow:** Player model shadows now cast accurately in 3rd-person perspective.
- **Balanced Light Multipliers:** Torch fog softened to a gentle warm haze (`0.18`), sea lanterns and normal lanterns properly tuned, and handheld fog confined to player hand.
- **Extended Handheld Lights:** Added redstone items and blocks to handheld dynamic emission.

### Fixes & Cleanups
- Bamboo and iron bar collision/alpha bounds corrected to match true geometry.
- Cleaned up obsolete settings toggles and unified Double AO settings.
- Optimized volumetric step limits (24 max steps) to prevent macOS GPU Metal submit timeouts.

> **Performance Note (M1 Air / Base Apple Silicon):** Prisma is highly demanding. On base models like the M1 MacBook Air, performance is heavily impacted unless render resolution scaling is set around 25%–45% with quality settings set to Medium.

---

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
