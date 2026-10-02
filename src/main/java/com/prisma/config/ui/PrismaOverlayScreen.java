package com.prisma.config.ui;

import com.prisma.config.PrismaShaderSettingsScreen;

public class PrismaOverlayScreen extends PrismaShaderSettingsScreen {
    public PrismaOverlayScreen() {
        super(null, Tab.LIGHTING);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
