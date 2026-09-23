package com.prisma.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin {
    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void prisma$cancelVignette(GuiGraphicsExtractor guiGraphicsExtractor, Entity entity, CallbackInfo ci) {
        ci.cancel();
    }
}
