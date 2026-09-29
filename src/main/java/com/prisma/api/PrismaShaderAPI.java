package com.prisma.api;

import net.minecraft.resources.Identifier;

/**
 * Public API for accessing and extending the Prisma Shader Engine.
 * Allows third-party mods to register custom Metal shaders, uniforms, and access the Voxel Grid.
 */
public interface PrismaShaderAPI {

    /**
     * Registers a custom Metal Shading Language (.metal) pass to be injected into the render pipeline.
     * @param identifier Unique identifier for your shader pass.
     * @param mslSourcePath Path to your .metal source file in assets.
     */
    void registerCustomMetalPass(Identifier identifier, String mslSourcePath);

    /**
     * Injects a custom uniform variable into the Prisma pipeline to be read by Metal shaders.
     * @param name Name of the uniform variable.
     * @param value Float array representing the data (e.g. float, float2, float3, float4, or matrices).
     */
    void bindCustomUniform(String name, float[] value);

    /**
     * Returns true if the hardware Voxel Grid is currently active and initialized on the GPU.
     */
    boolean isVoxelGridActive();

    /**
     * Requests the engine to force a rebuild of a specific chunk volume in the Voxel Grid.
     * Useful for mods that make custom block updates bypassing vanilla logic.
     */
    void markVolumeDirty(int startX, int startY, int startZ, int endX, int endY, int endZ);

    /**
     * Returns the singleton instance of the Prisma API.
     */
    static PrismaShaderAPI getInstance() {
        return com.prisma.api.internal.PrismaShaderAPIImpl.INSTANCE;
    }
}
