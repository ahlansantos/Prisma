# Changelog

## [26.3-Preview.4]

### Features & Improvements
- **True HDR Output Unlocked:** Removed an artificial SDR limit in the post-processing pipeline that was restricting brightness. True linear EDR values now flow directly to macOS, allowing XDR displays to output brilliant, unbound lighting and realistic highlights.
- **MetalFX Crash Fix:** Resolved an Apple Neural Engine (`ANE inference operation failed`) crash and black screen artifact caused by division-by-zero NaNs in point light soft shadow jittering.
- **Ray Traced Fog & Volumetrics Overhaul:** Replaced basic Bayer jitter with Interleaved Gradient Noise to eliminate banding and scanlines. Added a distance cap and decoupled local point light accumulations to prevent them from blowing up to 20x brightness on distant fog rays.
- **Emissive Blocks Fix:** Sea Lanterns, Glowstone, and other emissives now correctly bypass overlapping point-light summation on their own surfaces, maintaining their rich texture colors instead of blooming out to pure white.
- **RTAO for Flat Blocks:** Ray Traced Ambient Occlusion now correctly identifies Snow Layers and Carpets (1/8th block height), preventing them from casting massive full-block shadows.
- **Lightweight Floodfill Option:** When Ray Traced Point Light Shadows are toggled off, the engine now uses an aggressively optimized, soft-capped additive floodfill mode that replicates SEUS PTGI's shadowless performance without blowing out indoor areas.
- **Scrollable Single-Page Settings UI:** Refactored the settings screen into a clean, dynamically resizing single-page scrolling list to fit any resolution or GUI scale.
- **Buffed Sunlight Intensity:** Increased celestial direct light intensity by nearly 2x to compete properly with artificial lights and achieve a natural golden hour rim light.
- **VXGI Temporal & Spatial Denoiser:** Substituted oscillating temporal sampling with complementary 180° non-rotating hemisphere rays, 5x5 cross-bilateral filter, and world-space temporal reprojection. Eliminates the spinning/strobing effect ("rave") and stabilizes bounce lighting.
- **Wide Hemispherical GI Spread:** Wide cosine distribution allows indirect light to spread naturally across ceilings, walls, and crevices rather than concentrating only on adjacent walls.
- **Bilinear GI Sampling:** Hardware bilinear interpolation eliminates distant moiré stripes and perspective banding on surfaces.
- **Contact RTAO & 4x4x4 Bitmask:** Refined RTAO radius (0.45–1.15 blocks) and added bitmask testing for non-full blocks (stairs, slabs, trapdoors, fences, lanterns).
- **Point Lights Overhaul:** Floor torches and wall torches unified at 4x intensity. Reach increased to 14–20 blocks with up to 128 active lights. Disabling point lights in settings now completely skips ray/lightmap evaluation with zero performance cost.
- **Volumetric Fog Balance:** Torch flare smoothed to a warm atmospheric halo; base fog extinction boosted.
- **Glass Backlit Lighting:** Prevented transmitted light through glass from turning pitch black when illuminated from behind.
- **Reflection Specular Occlusion:** Point light specular highlights in reflections now check voxel occlusion through solid walls.

## [26.3-Preview.3 Hotfix]

### Features
- **In-Game Overlay:** Moved the Prisma configuration menu to a left-aligned overlay panel.
- **Chat Command Gateway:** Type `/prisma` in the chat to seamlessly open the configuration menu in-game.
- **Native True HDR Support:** Toggle "Real HDR" to pipe true linear EDR output directly to macOS, allowing blindingly bright skies and physically correct blooming on compatible XDR displays.

### Improvements
- **Extreme FPS Volumetric Clouds Optimization:** Slashed volumetric cloud raymarching costs by up to 75% for sky pixels by implementing dynamic noise iteration Level-Of-Detail (LOD).


## [26.3-Preview.3]

### Features & Improvements
- **MetalFX Temporal Scaler:** Upgraded the MetalFX Spatial upscaler to **MetalFX Temporal** using native engine motion vectors, significantly reducing path-tracing noise and stabilizing the image (eliminates a large portion of the noise, but not all of it).
- **VX RTAO (Ray-Traced Ambient Occlusion):** Replaced the old static Double AO with true Voxel Ray-Traced Ambient Occlusion. Features soft occlusion that properly passes through slabs, leaves, and tall grass.
- **VX RTGI (Voxel Ray-Traced Global Illumination):** Restored 1-bounce Global Illumination! Fully separated into its own UI toggle. It now interacts perfectly with MetalFX Temporal for colored bounces.
- **Player Voxelization & Shadow (3D Layers & Slim Models):** The raytracer now maps all 3D skin layers (Hat, Jacket, etc.) into the grid, and fully supports Slim (Alex) vs Classic (Steve) skin shadows and reflections.
- **Post-Processing Control:** Added a dedicated UI tab with sliders to precisely adjust Lens Flare, Vignette, and Chromatic Aberration intensity.
- **Physical Water Raytracing & Depth:** Water and fluids are now correctly inserted into the 3D Voxel Grid, allowing them to cast volumetric shadows, reflect in mirrors, and block ambient light. Shallow water is clear, deep water fades to navy.
- **Glossy / Blurry Reflections:** Quartz, Prismarine, and Sea Lanterns now feature beautiful frosted/glossy ray-traced reflections.
- **Luma-Preserving Filmic Tone Mapping:** Luminance-only curve that preserves 100% of real RGB chromaticity. Eliminates the grey, washed-out veil while keeping lush green grass and rich golden sunsets.
- **Retina MetalFX Auto-Detect:** Prisma automatically selects the best MetalFX render scale for your display on first launch: 0.40 for M4 Max native 3.4K, 0.50 for standard Retina, 0.75 for 1080p.

### Fixes
- **RTGI Point Light Injection:** Point lights (torches, lanterns) now accurately bounce and contribute fully to the Global Illumination raymarcher, curing dark rooms with up to 32 concurrent lights per bounce.
- **Strict Profile Presets:** M1 Air Low strictly disables VXGI and enforces a baseline 35-60 FPS performance without rogue resolution overrides.
- **GI Light Leaks & Motion Vectors:** Pitch-black rooms no longer leak phantom Global Illumination from dark walls. Also inverted and correctly mapped motion vectors, fixing all edge smearing and ghosting.

> **Known Limitations:**
> - Very noisy at lower internal rendering resolutions.
> - Noticeable blur and ghosting when rotating the camera quickly or observing fast-moving objects under low MFX resolutions.
> - RTAO may occasionally treat partial blocks (like leaves or slabs) as full solid blocks.
> - RTGI currently suffers from significant noise and ghosting, especially during movement.

---

## [26.3-Preview.2 - Hotfix]

### Features & Improvements
- **Dual-Pass Translucent Deferred Shading:** Split deferred lighting into a pre-translucent solid pass and a post-translucent pass. Looking through stained glass and water now reveals fully deferred lighting (sun shadows, point light shadows, VXAO, indirect lighting) instead of vanilla illumination!
- **Translucent Glass & Water Reflections:** Restored and polished specular reflections for stained glass and water across the dual-pass pipeline.
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
- **3D HUD Depth-Only Render Pass Crash Fix:** Handled empty color attachment descriptors in Metal render pass creation, resolving a `NoSuchElementException` crash during `GameRenderer.integrate3DHudDepth` / `render3dHud`.
- **Enhanced Crepuscular God Rays:** Increased god ray scattering and atmospheric sunlight beam presence through gaps, windows, and foliage.
- **Block Edge & Corner Normal Reconstruction Fix:** Eliminated white lines and abnormal highlights along block corners and edges by weighting axis-snapped normals with camera view alignment and filtering fallback normals to visible front-faces.
- **Glass Reflection & Dual-Pass Z-Fighting Fix:** Isolated glass surface classification strictly to the underlying block voxel (`insideVox`), preventing adjacent solid walls or faces pointed towards glass from triggering duplicate reflection passes and z-fighting.
- **Underwater Volumetric Fog Clean-up:** Volumetric atmospheric fog now halts cleanly at water boundaries and is disabled underwater, eliminating scanline banding around sea lanterns and underwater artifacts.
- **Translucent AO Exclusion:** Excluded transparent glass and water from generating and receiving ambient occlusion, ensuring clean glass without dark smudges.
- **Water Boundary & Z-Fighting Fix:** Constrained water surface detection strictly to upward faces (`> 0.85`), eliminating flickering and z-fighting on adjacent submerged/exposed block walls.
- **Water Fog Stability:** Stabilized ray-marched atmospheric fog around water boundaries to prevent jumpy or erratic shifts when moving near or submerged in water.
- **Multiplayer Connection Crash Fix:** Guarded deferred lighting passes against uninitialized projection matrices during `ClientboundLoginPacket`, resolving `Network Protocol Error` disconnects.
- **Cloud & Horizon Fog Dither Fix:** Bounded volumetric fog raymarching to the local voxel radius (56 blocks), eliminating harsh checkerboard/stippled noise patterns on clouds and distant water while improving performance.
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
