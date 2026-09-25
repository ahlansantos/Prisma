package com.prisma.config;

import com.prisma.mtl.MTLBuiltinPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.File;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public class PrismaShaderScreen extends Screen {
    private final Screen parent;
    private ShaderListWidget listWidget;

    public PrismaShaderScreen(Screen parent) {
        super(Component.literal("Prisma Shaderpacks"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.listWidget = new ShaderListWidget(this.minecraft, this.width, this.height, 40, 48);
                this.listWidget.add(new ShaderEntry("VXR Default", "The default hybrid ray-traced shader for Prisma."));
        
        // Scan shaderpacks folder
        File[] packs = FabricLoader.getInstance().getGameDir().resolve("shaderpacks").toFile().listFiles();
        if (packs != null) {
            for (File p : packs) {
                if (p.isDirectory() && new File(p, "shaders").exists()) {
                    this.listWidget.add(new ShaderEntry(p.getName(), "Custom MSL Shaderpack"));
                }
            }
        }
        
        this.addRenderableWidget(this.listWidget);

        int bottomY = this.height - 35;
        int btnWidth = 120;
        int spacing = 10;
        
        // Folder Button
        this.addRenderableWidget(Button.builder(Component.literal("Open Folder"), (button) -> {
            File packDir = new File(this.minecraft.gameDirectory, "shaderpacks");
            packDir.mkdirs();
            Util.getPlatform().openUri(packDir.toURI());
        }).bounds(this.width / 2 - btnWidth - spacing/2, bottomY - 30, btnWidth, 20).build());

        // Shader Settings Button
        this.addRenderableWidget(Button.builder(Component.literal("Shader Settings..."), (button) -> {
            this.minecraft.setScreenAndShow(new PrismaShaderSettingsScreen(this));
        }).bounds(this.width / 2 + spacing/2, bottomY - 30, btnWidth, 20).build());

        // Apply Button
        this.addRenderableWidget(Button.builder(Component.literal("Apply"), (button) -> {
            ShaderEntry selected = this.listWidget.getSelected();
            if (selected != null) {
                PrismaConfig.INSTANCE.shaderPack = selected.packName;
                PrismaConfig.INSTANCE.save();
                MTLBuiltinPipelines.reloadShaders();
            }
        }).bounds(this.width / 2 - btnWidth - spacing/2, bottomY, btnWidth, 20).build());

        // Done Button
        this.addRenderableWidget(Button.builder(Component.literal("Done"), (button) -> {
            this.minecraft.setScreenAndShow(this.parent);
        }).bounds(this.width / 2 + spacing/2, bottomY, btnWidth, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.extractBackground(graphics, mouseX, mouseY, delta);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 20, -1);
    }

    class ShaderListWidget extends ObjectSelectionList<ShaderEntry> {
        public ShaderListWidget(Minecraft minecraft, int width, int height, int y0, int itemHeight) {
            super(minecraft, width, height - 110, y0, itemHeight);
        }
        @Override
        public int getRowWidth() {
            return 300;
        }
        @Override
        protected int scrollBarX() {
            return this.width / 2 + 160;
        }

        public void add(ShaderEntry e) {
            this.addEntry(e);
            if (e.packName.equals(PrismaConfig.INSTANCE.shaderPack)) {
                this.setSelected(e);
            }
        }
    }

        class ShaderEntry extends ObjectSelectionList.Entry<ShaderEntry> {
        public final String packName;
        public final String description;

        public ShaderEntry(String packName, String description) {
            this.packName = packName;
            this.description = description;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
            int color = listWidget.getSelected() == this ? -171 : -1;
            int x = this.getX();
            int y = this.getY();
            graphics.text(Minecraft.getInstance().font, this.packName, x + 15, y + 5, color);
            graphics.text(Minecraft.getInstance().font, this.description, x + 15, y + 20, -8355712); // Gray text
            if (this.packName.equals(PrismaConfig.INSTANCE.shaderPack)) {
                graphics.text(Minecraft.getInstance().font, "(Active)", x + 250, y + 5, 0xFF00FF00);
            }
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            listWidget.setSelected(this);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(packName);
        }
    }
}
