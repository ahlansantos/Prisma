# VXR Default - Prisma Shaderpack

This is the standard VXR rendering pipeline extracted into a standalone shaderpack format for Prisma.

## Folder Structure
```
VXR-Default/
├── pack.mcmeta
└── shaders/
    ├── deferred.metal       (Main lighting and volumetric pass)
    ├── postprocess.metal    (MetalFX, Sharpening, and Tonemapping)
    └── voxel_common.metal   (Core DDA Raytracing and utility functions)
```

## How to use
Simply zip this folder or place it directly into your `shaderpacks` directory (or wherever Prisma loads its metal shaders from). You can freely modify the `.metal` files to create your own custom ray tracing pipeline.
