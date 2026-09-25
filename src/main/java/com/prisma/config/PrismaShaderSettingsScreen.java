package com.prisma.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

import java.util.List;
import java.util.function.Consumer;

public class PrismaShaderSettingsScreen extends Screen {
    private final Screen parent;
    private SettingsListWidget listWidget;
    private final Tab currentTab;

    public enum Tab {
        LIGHTING("Lighting"),
        WATER_CLOUDS("Water & Clouds"),
        PERFORMANCE("Performance");
        
        public final String name;
        Tab(String name) { this.name = name; }
    }
    
    public PrismaShaderSettingsScreen(Screen parent, Tab tab) {
        super(Component.literal("Shader Pack Settings"));
        this.parent = parent;
        this.currentTab = tab;
    }

    public PrismaShaderSettingsScreen(Screen parent) {
        this(parent, Tab.LIGHTING);
    }

    @Override
    protected void init() {
        this.listWidget = new SettingsListWidget(this.minecraft, this.width, this.height, 60, 24);
        
        int w = 150;
        int h = 20;

        // Tabs at the top
        int tabW = 100;
        int startX = this.width / 2 - (Tab.values().length * tabW) / 2;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab t = Tab.values()[i];
            Button btn = Button.builder(Component.literal(t.name), (b) -> {
                PrismaConfig.INSTANCE.save();
                this.minecraft.setScreenAndShow(new PrismaShaderSettingsScreen(this.parent, t));
            }).bounds(startX + i * tabW, 30, tabW, 20).build();
            if (t == this.currentTab) btn.active = false;
            this.addRenderableWidget(btn);
        }

        if (!"VXR Default".equals(PrismaConfig.INSTANCE.shaderPack)) {
            this.listWidget.add(new SettingsEntry(Component.literal("Shader Settings Unavailable").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD)));
            this.listWidget.add(new SettingsEntry(Component.literal("Custom MSL Shaders currently do not support").withStyle(net.minecraft.ChatFormatting.GRAY)));
            this.listWidget.add(new SettingsEntry(Component.literal("in-game UI configuration menus.").withStyle(net.minecraft.ChatFormatting.GRAY)));
        } else if (this.currentTab == Tab.LIGHTING) {
            this.listWidget.add(new SettingsEntry(Component.literal("Global Illumination").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            Button b1 = createToggle("Double AO", PrismaConfig.INSTANCE.vxaoEnabled, v -> PrismaConfig.INSTANCE.vxaoEnabled = v);
            ConfigSlider b2 = new ConfigSlider("AO Strength", 0.0, 2.0, PrismaConfig.INSTANCE.vxaoStrength, true, v -> PrismaConfig.INSTANCE.vxaoStrength = v.floatValue());
            this.listWidget.add(new SettingsEntry(b1, b2));

            this.listWidget.add(new SettingsEntry(Component.literal("Dynamic Lights").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            Button b3 = createToggle("Point Lights", PrismaConfig.INSTANCE.pointLightsEnabled, v -> PrismaConfig.INSTANCE.pointLightsEnabled = v);
            ConfigSlider b4 = new ConfigSlider("Max Lights", 0.0, 128.0, PrismaConfig.INSTANCE.maxPointLights, false, v -> PrismaConfig.INSTANCE.maxPointLights = v.intValue());
            this.listWidget.add(new SettingsEntry(b3, b4));

            this.listWidget.add(new SettingsEntry(Component.literal("Shadows").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            Button b5 = createToggle("Sun Shadows", PrismaConfig.INSTANCE.sunShadowsEnabled, v -> PrismaConfig.INSTANCE.sunShadowsEnabled = v);
            Button b7 = createToggle("Player Shadows", PrismaConfig.INSTANCE.playerShadowEnabled, v -> PrismaConfig.INSTANCE.playerShadowEnabled = v);
            this.listWidget.add(new SettingsEntry(b5, b7));

            this.listWidget.add(new SettingsEntry(Component.literal("Ray Traced Reflections").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            Button b9 = createToggle("Reflections", PrismaConfig.INSTANCE.reflectionsEnabled, v -> PrismaConfig.INSTANCE.reflectionsEnabled = v);
            Button b8 = createToggle("Player Reflections", PrismaConfig.INSTANCE.playerReflectionEnabled, v -> PrismaConfig.INSTANCE.playerReflectionEnabled = v);
            this.listWidget.add(new SettingsEntry(b9, b8));

            Button b11 = createToggle("Reflect Point Shadows", PrismaConfig.INSTANCE.reflectionPointLightShadows, v -> PrismaConfig.INSTANCE.reflectionPointLightShadows = v);
            Button b12 = createToggle("Reflect Sun Shadows", PrismaConfig.INSTANCE.reflectionDirectionalShadows, v -> PrismaConfig.INSTANCE.reflectionDirectionalShadows = v);
            this.listWidget.add(new SettingsEntry(b11, b12));

            Button b13 = createToggle("Reflect VXAO", PrismaConfig.INSTANCE.vxaoInReflections, v -> PrismaConfig.INSTANCE.vxaoInReflections = v);
            Button b14 = createToggle("Reflect Clouds", PrismaConfig.INSTANCE.cloudsInReflections, v -> PrismaConfig.INSTANCE.cloudsInReflections = v);
            this.listWidget.add(new SettingsEntry(b13, b14));
        }
        else if (this.currentTab == Tab.WATER_CLOUDS) {
            this.listWidget.add(new SettingsEntry(Component.literal("Water Settings").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            Button b1 = createToggle("Water Waves", PrismaConfig.INSTANCE.waterWavesEnabled, v -> PrismaConfig.INSTANCE.waterWavesEnabled = v);
            ConfigSlider b2 = new ConfigSlider("Wave Strength", 0.0, 2.0, PrismaConfig.INSTANCE.waterWaveStrength, true, v -> PrismaConfig.INSTANCE.waterWaveStrength = v.floatValue());
            this.listWidget.add(new SettingsEntry(b1, b2));

            ConfigSlider b3 = new ConfigSlider("Wave Speed", 0.0, 3.0, PrismaConfig.INSTANCE.waterWaveSpeed, true, v -> PrismaConfig.INSTANCE.waterWaveSpeed = v.floatValue());
            ConfigSlider b4 = new ConfigSlider("Water Absorption", 0.0, 5.0, PrismaConfig.INSTANCE.waterAbsorptionStrength, true, v -> PrismaConfig.INSTANCE.waterAbsorptionStrength = v.floatValue());
            this.listWidget.add(new SettingsEntry(b3, b4));

            this.listWidget.add(new SettingsEntry(Component.literal("Cloud Settings").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            Button b5 = createToggle("Volumetric Clouds", PrismaConfig.INSTANCE.volumetricCloudsEnabled, v -> PrismaConfig.INSTANCE.volumetricCloudsEnabled = v);
            ConfigSlider b6 = new ConfigSlider("Cloud Quality", 10.0, 100.0, PrismaConfig.INSTANCE.cloudQualitySteps, false, v -> PrismaConfig.INSTANCE.cloudQualitySteps = v.intValue());
            this.listWidget.add(new SettingsEntry(b5, b6));
        }
        else if (this.currentTab == Tab.PERFORMANCE) {
                        this.listWidget.add(new SettingsEntry(Component.literal("Performance").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            ConfigSlider vrad = new ConfigSlider("Voxel Grid Radius", 2.0, 16.0, PrismaConfig.INSTANCE.voxelRadius, false, v -> PrismaConfig.INSTANCE.voxelRadius = v.intValue());
            this.listWidget.add(new SettingsEntry(vrad, null));
            this.listWidget.add(new SettingsEntry(Component.literal("Spatial Denoiser & Anti Aliaser (SDAA)").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            Button bSdaa = createToggle("SDAA Enabled", PrismaConfig.INSTANCE.sdaaEnabled, v -> PrismaConfig.INSTANCE.sdaaEnabled = v);
            this.listWidget.add(new SettingsEntry(bSdaa, null));
            this.listWidget.add(new SettingsEntry(Component.literal("Post-Processing").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            Button b1 = createToggle("Motion Blur", PrismaConfig.INSTANCE.motionBlurEnabled, v -> PrismaConfig.INSTANCE.motionBlurEnabled = v);
            Button b2 = createToggle("SpaceWarp", PrismaConfig.INSTANCE.spaceWarpEnabled, v -> PrismaConfig.INSTANCE.spaceWarpEnabled = v);
            this.listWidget.add(new SettingsEntry(b1, b2));

            this.listWidget.add(new SettingsEntry(Component.literal("Upscaling").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            ConfigSlider b3 = new ConfigSlider("PEU Render Scale", 0.1, 1.5, PrismaConfig.INSTANCE.upscalingRatio, true, v -> PrismaConfig.INSTANCE.upscalingRatio = v.floatValue());
            this.listWidget.add(new SettingsEntry(b3, null));
        }
        this.addRenderableWidget(this.listWidget);
    }

    private Button createToggle(String name, boolean current, java.util.function.Consumer<Boolean> action) {
        return Button.builder(Component.literal(name + ": " + (current ? "ON" : "OFF")), (b) -> {
            boolean next = !b.getMessage().getString().contains("ON");
            b.setMessage(Component.literal(name + ": " + (next ? "ON" : "OFF")));
            action.accept(next);
            PrismaConfig.INSTANCE.save();
        }).bounds(0, 0, 150, 20).build();
    }

    class SettingsEntry extends ContainerObjectSelectionList.Entry<SettingsEntry> {
        private final Component headerText;
        private final net.minecraft.client.gui.components.AbstractWidget btn1;
        private final net.minecraft.client.gui.components.AbstractWidget btn2;

        public SettingsEntry(Component headerText) {
            this.headerText = headerText;
            this.btn1 = null;
            this.btn2 = null;
        }

        public SettingsEntry(net.minecraft.client.gui.components.AbstractWidget btn1, net.minecraft.client.gui.components.AbstractWidget btn2) {
            this.headerText = null;
            this.btn1 = btn1;
            this.btn2 = btn2;
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            if (headerText != null) return java.util.List.of();
            return btn2 == null ? java.util.List.of(btn1) : java.util.List.of(btn1, btn2);
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            if (headerText != null) return java.util.List.of();
            return btn2 == null ? java.util.List.of(btn1) : java.util.List.of(btn1, btn2);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
            if (headerText != null) {
                graphics.centeredText(Minecraft.getInstance().font, headerText, listWidget.getX() + listWidget.getWidth() / 2, this.getY() + 10, -1);
                return;
            }
            if (btn1 != null) {
                btn1.setX(listWidget.getX() + listWidget.getWidth() / 2 - 155);
                btn1.setY(this.getY());
                btn1.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
            if (btn2 != null) {
                btn2.setX(listWidget.getX() + listWidget.getWidth() / 2 + 5);
                btn2.setY(this.getY());
                btn2.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    class ConfigSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private final String prefix;
        private final double min, max;
        private final boolean isFloat;
        private final java.util.function.Consumer<Double> onUpdate;

        public ConfigSlider(String prefix, double min, double max, double current, boolean isFloat, java.util.function.Consumer<Double> onUpdate) {
            super(0, 0, 150, 20, net.minecraft.network.chat.Component.empty(), 0.0);
            this.prefix = prefix;
            this.min = min;
            this.max = max;
            this.isFloat = isFloat;
            this.onUpdate = onUpdate;
            this.value = (current - min) / (max - min);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            double val = min + value * (max - min);
            if (isFloat) {
                this.setMessage(Component.literal(prefix + ": " + String.format("%.2f", val)));
            } else {
                this.setMessage(Component.literal(prefix + ": " + (int)Math.round(val)));
            }
        }

        @Override
        protected void applyValue() {
            onUpdate.accept(min + value * (max - min));
            PrismaConfig.INSTANCE.save();
        }
    }

        class SettingsListWidget extends ContainerObjectSelectionList<SettingsEntry> {
        public SettingsListWidget(Minecraft minecraft, int width, int height, int y0, int itemHeight) {
            super(minecraft, width, height - 110, y0, itemHeight);
        }
        public void add(SettingsEntry entry) {
            this.addEntry(entry);
        }
        @Override
        public int getRowWidth() {
            return 340;
        }
        @Override
        protected int scrollBarX() {
            return this.width / 2 + 180;
        }
    }
}

