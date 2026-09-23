package com.prisma.mixin.render;

import com.prisma.render.MetalBackend;
import com.mojang.blaze3d.opengl.GlBackend;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PreferredGraphicsApi.class)
abstract class PreferredGraphicsApiMixin {
    @Inject(method = "getBackendsToTry", at = @At("HEAD"), cancellable = true)
    private void prisma$injectMetalBackend(final CallbackInfoReturnable<GpuBackend[]> cir) {
        cir.setReturnValue(new GpuBackend[]{new MetalBackend(), new VulkanBackend(), new GlBackend()});
    }

    @Inject(method = "caption", at = @At("HEAD"), cancellable = true)
    private void prisma$renameDefaultApiToMetal(final CallbackInfoReturnable<Component> cir) {
        PreferredGraphicsApi self = (PreferredGraphicsApi) (Object) this;
        if (self == PreferredGraphicsApi.DEFAULT) {
            cir.setReturnValue(Component.literal("Prefer Metal (Prisma)"));
        }
    }
}
