# Prisma 26.3 Revision 1

## True Shadows & Contrast (The Big Fix)
- **Deep Directional Contrast:** The core lighting engine was completely overhauled! Previously, multiple layers of ambient and sky light were "stacking" on the unlit sides of blocks, causing faces pointing away from the sun to still look bright and washed out. This stacking has been aggressively reduced. Shadowed faces and areas facing away from the light source will now look authentically dark, granting the world a massive boost in depth and realistic contrast.
- **Analytic Geometry Normals:** Say goodbye to those bizarre white and yellow flashing pixels on the edges of blocks! The shader now dynamically falls back to perfect grid-based voxel normals whenever it detects you are looking at the razor-thin edge of a block, glass pane, or iron bar.
- **The "Square Cutout" Bug Destroyed:** We completely rebuilt the `isEntity` depth-testing logic. Getting right up into the face of a wall (less than 0.5 blocks away) will no longer cause the engine to panic, strip away all shadows, and force fullbright vanilla lighting in the shape of a square!

## Water, Sky & Post-Processing
- **Stable Headbob Reflections:** Water reflections are now 100% anchored to the screen! We fixed the math that caused reflections to slide and distort every time you walked. The shader now properly extracts the headbobbed coordinates straight from the Inverse View matrix.
- **Immersive Volumetric Clouds:** You can finally fly directly inside the clouds without them disappearing! We fixed the engine's early-exit bound checks, meaning the sky atmosphere remains cohesive no matter your altitude or camera angle.
- **SDAA & Halo Fixes:** The devastating screen-tearing and white halo artifacts are gone. We replaced the broken PEU filter with a mathematically stable Spatial Anti-Aliasing (SDAA) integration, clamping the bloom thresholds so brightly lit blocks no longer bleed into the dark sky.
- **Player Reflections Restored:** The ray-tracing threshold was lowered, allowing your player model's reflection to remain perfectly visible even when you stand extremely close to water or metal blocks.

## Known Limitations
- **ASFW Hardware Compatibility:** FrameWarp (ASFW) currently ONLY works on M1 and M2 architectures. It **does NOT support M3 chips and above** due to Apple's Dynamic Caching memory barrier changes. (Also, ASFW may flicker black / grey colors on supported hardware).
- **Lighting Perfection:** Voxel ray-traced shadows may still show minor bleeding inside caves and cave fog bloom artifacts.
- **Settings:** Sun Shadows, Player Shadows and Player Reflections just won't work perfectly in the UI yet.

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
