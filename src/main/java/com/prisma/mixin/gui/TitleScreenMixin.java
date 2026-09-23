package com.prisma.mixin.gui;

import com.prisma.Prisma;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        if (Prisma.hasMSLCompileError) {
            SystemToast.add(
                Minecraft.getInstance().gui.toastManager(),
                SystemToast.SystemToastId.PACK_LOAD_FAILURE,
                Component.literal("Prisma MSL Error"),
                Component.literal("Failed to compile MSL. Check Logs.")
            );
            Prisma.hasMSLCompileError = false;
        }
    }
}
