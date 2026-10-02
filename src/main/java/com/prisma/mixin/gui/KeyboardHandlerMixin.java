package com.prisma.mixin.gui;

import com.prisma.config.ui.PrismaOverlayScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    private static boolean isOverlayOpen = false;

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (event.key() == 298 && action == 1) { // 298 = F9, 1 = PRESS
            Minecraft client = Minecraft.getInstance();
            if (!isOverlayOpen) {
                isOverlayOpen = true;
                client.setScreenAndShow(new PrismaOverlayScreen() {
                    @Override
                    public void onClose() {
                        super.onClose();
                        isOverlayOpen = false;
                    }
                    @Override
                    public void removed() {
                        super.removed();
                        isOverlayOpen = false;
                    }
                });
            } else {
                isOverlayOpen = false;
                client.setScreenAndShow(null);
            }
        }
    }
}
