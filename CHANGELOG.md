# Prisma v0.3.0 (The Compute Rendering Update)

## Rendering Architecture (The Big Changes!)
- **Deferred Compute Pipelines:** Lighting, shadows, and post-processing have been entirely migrated to a new and powerful Deferred Compute Shaders architecture, allowing for much more precise calculations and preparing the engine for massive future scalability and performance.
- **SDAA & Halo Fixes:** Say goodbye to blinding flashes and screen tearing! Replaced the broken PEU filter with a mathematically stable Spatial Anti-Aliasing (SDAA) integration. The Bloom threshold and radius were strictly adjusted so that normal blocks no longer bleed brightness into the dark night sky.

## Main Graphics & Lighting
- **Flawless Geometry Normals:** Block edges will never flash white or yellow again! Implemented an **Analytic Voxel Normal Fallback**. When the shader detects that the screen-space depth reading failed because it's exactly on the edge of a block (or on thin blocks like glass and iron bars), it smartly abandons the screen-space derivative and calculates the perfect normal based on the mathematical 3D Voxel Grid instead.
- **Enhanced Shadow Contrast:** Shadows finally look like shadows! Fixed a critical bug where block faces pointing away from the sun skipped the shadow calculation but continued to receive 100% solar light. Unlit faces now properly drop to 0% sun exposure, drastically improving directional contrast and visual depth.
- **Crisp Shadow Penumbras:** Stochastic noise (Interleaved Gradient Noise) has been completely removed from the shadow system. Grass, foliage, and blocks now cast perfectly crisp and clean shadows, with no visible graininess even at lower resolutions.

## Water, Reflections & Clouds
- **Headbob Distortion Fixed:** Reflections no longer "slide" when you walk! The reflection vector now extracts the exact headbobbed camera position directly from the Inverse View Projection matrix, perfectly anchoring all reflections to your monitor's perspective.
- **Stable Water Physics:** Water normals no longer depend on fragile screen-space depth derivatives. They now use mathematical waves based purely on World-Space, completely eliminating the distortion and bizarre lines that occurred when moving the camera.
- **Immersive Volumetric Clouds:** You can now fly *inside* the clouds! Fixed a mathematical rendering error that caused the entire cloud layer to disappear when entering it and looking down or at the horizon. The sky atmosphere is now fully continuous, regardless of your altitude.
- **Player Reflections Restored:** The player no longer disappears from the mirror when getting close! The Ray Tracing collision threshold was adjusted, allowing you to perfectly see your own reflection in water and metals, even when up close.

## Misc & Bug Fixes
- **The "Square Cutout" Wall Bug:** The entity detection system (`isEntity`) was rewritten from scratch. The engine now uses 3 robust depth tests (including a precise view-ray projection) to verify blocks. Getting closer than 0.5 blocks to a wall will no longer cause the game to think the wall is a glowing entity, preventing lighting and shadows from disappearing in the center of the screen.
- **Ghost Head Shadows:** Fixed a bizarre issue where the player's head cast a floating shadow on vertical walls right in front of them due to a shader axis calculation failure.

## Known Limitations
- **ASFW Hardware Compatibility:** FrameWarp (ASFW) currently ONLY works on M1 and M2 architectures. It **does NOT support M3 chips and above** due to Apple's Dynamic Caching memory barrier changes. (Also, ASFW may flicker black / grey colors on supported hardware).
- **Lighting Perfection:** Voxel ray-traced shadows may show bleeding inside caves and cave fog bloom artifacts.
- **Settings:** Sun Shadows, Player Shadows and Player Reflections just won't work perfectly in the UI yet, idk why.

## Plans
- Expand the Deferred Compute pipeline to support more advanced GI (Global Illumination) techniques.
- Write comprehensive documentation for the new MSL Shader API so developers can start creating native Mac shaders.
- Expand the Shader Loader capabilities based on community feedback.

# Prisma v0.2.5 Revision 2

## Main Graphics
- **SDAA (Spatial Denoiser & Anti-Aliaser):** New custom Anti-Aliasing & Denoiser system. Replaces the old FXAA with an edge-adaptive filter that smooths jagged edges without blurring block textures. Added a toggle in the Performance tab.
- **Ray Traced Contact Hardening Shadows (Penumbra):** 
  - Shadows are now physically accurate: perfectly sharp at the base and smoothly diffused over distance. Works beautifully with SDAA.
  - **Point Lights Support:** Torches, lanterns, and other dynamic point lights now feature sharp penumbra shadows. *(Note: The 3-tap multi-ray penumbra was reverted to a single sharp ray due to severe thermal/performance throttling on Apple Silicon when computing 30+ rays per pixel in dense environments).*
- **Cave Lighting Slider:** Added a new slider under the Global Illumination tab. You can now freely adjust the minimum ambient light floor (from pitch black to softly illuminated) to improve visibility deep inside caves without raising monitor brightness.
- **Dynamic Fog:** Fog is now completely removed on clear days to showcase the vast raytraced draw distances. Dense fog will now dynamically roll in *only* during rain.

## Misc & Bug Fixes
- **Player Shadows & Reflections:** Fixed the v0.2.4 bug where these toggles wouldn't work in the UI.
- **Green Water Artifact:** Fixed a GPU memory uninitialized variable bug that caused water reflections to turn green and corrupted.
- **Floating Player Shadows:** Fixed OBB intersection precision, solving shadows clipping out near the player's feet.
- **DDA Distance Limit:** Increased the Raytracing step limit from 24 to 120. Long mountain shadows at sunset no longer abruptly disappear.
- **Voxel Reflection Lighting:** Fixed an ambient occlusion bug where cave and block lighting in the voxel grid reflection appeared much brighter than the actual real-world environment.
- **PEU Slider:** Resolution scaling is now freely adjustable between 10% and 150%.

## Plans
- **Next Update Focus:** Starting the massive port to **Minecraft 26.3**. We will migrate to the new `RenderPearl` API to unlock true Frame Generation (fixing the SpaceWarp ghosting) and fix hardware compatibility for M3+ chips.

---

# Prisma v0.2.4 (The Shader Loader Update)

## UI & Configuration (The Big Changes!)
- **Completely Rebuilt Custom UI:** Say goodbye to the messy Sodium tabs! Prisma now features a beautiful, standalone Shaderpack screen (highly inspired by Iris).
- **Sodium Clean-Up:** All Prisma configs were wiped from the Sodium menu. You will now only see a single, clean "Shader Packs..." button in your Video Settings. (Similar to Iris!)
- **New Settings Screen:** The shader settings are now properly organized into **Tabs** (*Lighting*, *Water & Clouds*, and *Performance*).
- **Native Sliders:** Removed the old, clunky ON/OFF toggles for numeric values. We now have smooth, native vanilla Sliders for configuring intensities!
- **Instant Application:** Adjusting settings or switching shaders and clicking "Apply/Done" now instantly recompiles and reloads the GPU pipelines without needing to restart the game.
- **Open Folder Button:** Added a native button to instantly open your `shaderpacks` folder.

## Main Graphics
- **New Shaderpack System:**
  - `VXR Default`: The classic Voxel Raytracing shader you know and love is now fully packaged as the default built-in shaderpack, running flawlessly on the new loader system!
- **Cloud System Overhaul:** Clouds are now volumetric entities that can be flown above or flown through naturally, eliminating the sky cutoff bug.

## Misc & Bug Fixes
- `MTLBuiltinPipelines` architecture completely reworked (No longer static hardcoded strings).
- **Prisma is now officially a Native Apple Silicon Shader Loader!** It dynamically reads `.metal` files directly from the filesystem.
- Complete removal of legacy Debug features and Views.
- Fixed MSL Entry Point Compilation logic (Fixed the `expected unqualified-id` crash!).
- Removed redundant settings like `Reflection Bounces` and `Shadow Quality` since the engine now handles them dynamically and optimally.

## Known Limitations
- **ASFW Hardware Compatibility:** FrameWarp (ASFW) currently ONLY works on M1 and M2 architectures. It **does NOT support M3 chips and above** due to Apple's Dynamic Caching memory barrier changes. (Also, ASFW may flicker black / grey colors on supported hardware)
- **Lighting Perfection:** Voxel ray-traced shadow may show bleeding inside caves and cave fog bloom artifacts.

## Plans
- Write comprehensive documentation for the new MSL Shader API so developers can start creating native Mac shaders.
- Expand the Shader Loader capabilities based on community feedback.
