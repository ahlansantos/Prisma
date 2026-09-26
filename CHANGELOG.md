# Prisma 26.3 Revision 1

## Graphics & Lighting
- **Foliage Contrast Fix:** Removed the broken two-sided transmission on leaves. Trees are now correctly shadowed on the side facing away from the sun.
- **Deep Shadows:** Fixed an ambient light stacking bug. Unlit block faces now correctly drop in brightness, creating realistic deep shadows and directional contrast.
- **Analytic Geometry Normals:** Block edges (like glass panes and iron bars) no longer flash white or yellow. The shader dynamically calculates flawless voxel-based normals on geometry edges.
- **Entity Detection Overhaul:** Getting within 0.5 blocks of a wall no longer disables lighting and shadows (the "Square Cutout" bug).
- **Soft Shadows in Reflections:** Voxel grid reflections on water and specular surfaces now support Stochastic Penumbra (soft shadows)! Water reflections now mirror the same soft shadow edges as the main world view instead of hard edges.
- **Dynamic Distance Fog:** The World Fog has been heavily reduced on sunny/clear days to increase visibility, and now gets dynamically thicker only during rain to create a heavy atmosphere.

## Water, Sky & Post-Processing
- **Stable Headbob Reflections:** Water reflections are now 100% anchored to the screen. Extracted coordinates from the Inverse View matrix to prevent reflections from sliding while walking.
- **Immersive Volumetric Clouds:** Flying directly inside clouds no longer causes them to disappear. Engine bounds-checking was fixed for continuous sky atmosphere at any altitude.
- **SDAA & Halo Fixes:** Replaced the broken PEU filter with mathematically stable Spatial Anti-Aliasing (SDAA), eliminating screen-tearing and bright halos around blocks against the night sky.
- **Player Reflections Restored:** The ray-tracing threshold was lowered, allowing the player model's reflection to remain visible when standing close to water or metal blocks.

## Settings & UI
- **Menu Reordering:** Reordered the UI settings under appropriate headers to prevent confusion (`PEU Render Scale` correctly moved to Upscaling, `SDAA` correctly moved to its own header).
- **Settings Save Fix:** Fixed a parsing bug in `prisma.json` that caused several graphics settings to reset to default when restarting the game.

## Known Limitations
- **ASFW Hardware Compatibility:** FrameWarp (ASFW) currently ONLY works on M1 and M2 architectures. It **does NOT support M3 chips and above** due to Apple's Dynamic Caching memory barrier changes.
- **Lighting Perfection:** Voxel ray-traced shadows may still show minor bleeding inside caves and cave fog bloom artifacts.
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
