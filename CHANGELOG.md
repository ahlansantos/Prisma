# Prisma v0.2.5 Revision 1

## Main Graphics
- **SDAA (Spatial Denoiser & Anti-Aliaser):** New custom Anti-Aliasing & Denoiser system. Replaces the old FXAA with an edge-adaptive filter that smooths jagged edges without blurring block textures. Added a toggle in the Performance tab.
- **Ray Traced Contact Hardening Shadows (Penumbra):** 
  - Shadows are now physically accurate: perfectly sharp at the base and smoothly diffused over distance. Works beautifully with SDAA.
  - **Point Lights Support:** Torches, lanterns, and other dynamic point lights now feature full Penumbra shadows. When SDAA is enabled, it fires 3 simultaneous multi-tap rays per point light to create a perfectly smooth, realistic fade!
- **Cave Lighting Slider:** Added a new slider under the Global Illumination tab. You can now freely adjust the minimum ambient light floor (from pitch black to softly illuminated) to improve visibility deep inside caves without raising monitor brightness.

## Misc & Bug Fixes
- **Player Shadows & Reflections:** Fixed the v0.2.4 bug where these toggles wouldn't work in the UI.
- **Green Water Artifact:** Fixed a GPU memory uninitialized variable bug that caused water reflections to turn green and corrupted.
- **Floating Player Shadows:** Fixed OBB intersection precision, solving shadows clipping out near the player's feet.
- **DDA Distance Limit:** Increased the Raytracing step limit from 24 to 120. Long mountain shadows at sunset no longer abruptly disappear.
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
