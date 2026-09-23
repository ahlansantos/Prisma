package com.prisma.mixin.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatMixin {
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
        }
    }
}
