package com.prisma.mixin.sodium;

import com.prisma.render.MetalDrawContext;
import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.client.gpu.device.context.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DrawContext.class)
public class DrawContextMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true, remap = false)
    private static void prisma$createMetalDrawContext(CallbackInfoReturnable<DrawContext> cir) {
        if (RenderSystem.getDevice().getDeviceInfo().backendName().equals("Metal")) {
            cir.setReturnValue(new MetalDrawContext());
        }
    }
}
