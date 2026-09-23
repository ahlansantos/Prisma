package com.prisma.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.caffeinemc.mods.sodium.client.gpu.device.context.VKIndirectContext;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;

import java.nio.ByteBuffer;

public final class MetalDrawContext extends VKIndirectContext {
    private MetalRenderPass metalPass;

    @Override
    public void setContext(RenderPass pass, RenderPipeline pipeline) {
        this.pass = pass;
        this.metalPass = (MetalRenderPass) ((net.caffeinemc.mods.sodium.mixin.core.RenderPassAccessor) pass).getBackend();
    }

    @Override
    public void updateData(RenderRegion region, CameraTransform camera) {
        float x = getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
        float y = getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
        float z = getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);

        GpuBufferSlice pushConstantsBufferSlice;
        try (GpuBufferSlice.MappedView mapped = metalPass.allocateTransient(20, 4, GpuBuffer.USAGE_UNIFORM)) {
            ByteBuffer data = mapped.data();
            data.putFloat(0, x);
            data.putFloat(4, y);
            data.putFloat(8, z);
            data.putInt(12, Math.toIntExact(System.currentTimeMillis() - region.getCreationTime()));
            data.putInt(16, region.getId());
            pushConstantsBufferSlice = mapped.slice();
        }

        this.metalPass.setUniform("push_constants", pushConstantsBufferSlice);
    }
}
