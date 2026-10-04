package com.prisma.render;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.lang.ref.WeakReference;

/**
 * Shows the performance warning once every time the player enters a world.
 * Triggered from the render mixin ({@code PrismaLevelRendererMixin}) so it runs as soon as
 * the first frame of a new level is rendered.
 */
public final class PrismaJoinWarning {
    private static final String WARNING =
            "Prisma Preview 4 is VERY RESOURCE-HEAVY - even if you are on a Max Chip, PLEASE use lower "
                    + "resolutions at fullscreen. Retina kills performance, and anything above 1080p / 1200p "
                    + "may KILL performance without upscaling.";

    private static WeakReference<Object> lastLevel = new WeakReference<>(null);
    private static int framesSinceJoin = 0;
    private static boolean pending = false;

    private PrismaJoinWarning() {
    }

    public static void onFrame(final Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null) {
            return;
        }
        if (lastLevel.get() != mc.level) {
            lastLevel = new WeakReference<>(mc.level);
            framesSinceJoin = 0;
            pending = true;
        }
        if (!pending) {
            return;
        }
        // Wait a short moment so the message is not lost during the join/loading frames.
        if (++framesSinceJoin < 120) {
            return;
        }
        pending = false;
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(WARNING).withStyle(ChatFormatting.GOLD));
            }
            SystemToast.add(
                    mc.gui.toastManager(),
                    SystemToast.SystemToastId.PACK_LOAD_FAILURE,
                    Component.literal("Prisma Preview 4 is VERY heavy"),
                    Component.literal("Use lower resolutions at fullscreen. Check chat.")
            );
        });
    }
}
