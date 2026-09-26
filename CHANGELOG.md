# Prisma 26.3 Revision 2

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
- **Lighting Perfection:** Voxel ray-traced shadows may still show minor bleeding inside caves and cave fog bloom artifacts.
- **Next Update Focus:** Starting the massive port to **Minecraft 26.3**. We will migrate to the new `RenderPearl` API to unlock true Frame Generation and fix hardware compatibility for M3+ chips.
