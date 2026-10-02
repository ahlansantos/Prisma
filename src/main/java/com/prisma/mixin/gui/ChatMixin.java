package com.prisma.mixin.gui;

import com.prisma.config.ui.PrismaOverlayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.gui.components.EditBox;

@Mixin(ChatScreen.class)
public class ChatMixin {
    @Shadow protected EditBox input;

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void onChat(String message, boolean addToHistory, CallbackInfo ci) {
        if (message.equals("/prismatoast")) {
            SystemToast.add(
                Minecraft.getInstance().gui.toastManager(),
                SystemToast.SystemToastId.PACK_LOAD_FAILURE,
                Component.literal("Prisma MSL Error"),
                Component.literal("Failed to compile MSL. Check Logs.")
            );
            ci.cancel();
        } else if (message.equals("/prisma") || message.equals("/reshade")) {
            // Handled in keyPressed now
            ci.cancel();
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() == 257 || event.key() == 335) { // Enter or Numpad Enter
            String message = this.input.getValue().trim();
            if (message.equals("/prisma") || message.equals("/reshade")) {
                Minecraft.getInstance().setScreenAndShow(new PrismaOverlayScreen());
                cir.setReturnValue(true);
            }
        }
    }
}
