# Prisma v0.2.4 (The Shader Loader Update)

## UI & Configuration (The Big Changes!)
- **Completely Rebuilt Custom UI:** Say goodbye to the messy Sodium tabs! Prisma now features a beautiful, standalone Shaderpack screen (highly inspired by Iris).
- **Sodium Clean-Up:** All Prisma configs were wiped from the Sodium menu. You will now only see a single, clean "Shader Packs..." button in your Video Settings.
- **Next-Gen Settings Screen:** The shader settings are now properly organized into **Tabs** (*Lighting*, *Water & Clouds*, and *Performance*). 
- **Native Sliders:** Removed the old, clunky ON/OFF toggles for numeric values. We now have smooth, native vanilla Sliders for configuring intensities!
- **Instant Application:** Adjusting settings or switching shaders and clicking "Apply/Done" now instantly recompiles and reloads the GPU pipelines without needing to restart the game.
- **Open Folder Button:** Added a native button to instantly open your `shaderpacks` folder.

## Main Graphics
- **New Shaderpack System:**
  - `VXR Default`: The classic Voxel Raytracing shader you know and love is now fully packaged as the default built-in shaderpack, running flawlessly on the new loader system!
- **Lighting Perfection:** Fixed voxel ray-traced shadow bleeding inside caves, removed cave fog bloom artifacts, and fixed pitch-black glass shadows.
- **Cloud System Overhaul:** Clouds are now volumetric entities that can be flown above or flown through naturally, eliminating the sky cutoff bug.

## Misc & Bug Fixes
- `MTLBuiltinPipelines` architecture completely reworked (No longer static hardcoded strings).
- **Prisma is now officially a Native Apple Silicon Shader Loader!** It dynamically reads `.metal` files directly from the filesystem.
- Complete removal of legacy Debug features and Views.
- Fixed MSL Entry Point Compilation logic (Fixed the `expected unqualified-id` crash!).
- Removed redundant settings like `Reflection Bounces` and `Shadow Quality` since the engine now handles them dynamically and optimally.

## Known Limitations
- **ASFW Hardware Compatibility:** FrameWarp (ASFW) currently ONLY works on M1 and M2 architectures. It **does NOT support M3 chips and above** due to Apple's Dynamic Caching memory barrier changes.

## Plans
- Write comprehensive documentation for the new MSL Shader API so developers can start creating native Mac shaders.
- Expand the Shader Loader capabilities based on community feedback.
