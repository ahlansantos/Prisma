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
        PERFORMANCE("Performance"),
        PRESETS("Presets");
        
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
        int tabW = 85;
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
            Button bVxao = createToggle("Double AO", PrismaConfig.INSTANCE.doubleAoEnabled, v -> PrismaConfig.INSTANCE.doubleAoEnabled = v);
            ConfigSlider bVxaoStrength = new ConfigSlider("Double AO Strength", 0.5, 2.5, PrismaConfig.INSTANCE.doubleAoStrength, true, v -> { PrismaConfig.INSTANCE.doubleAoStrength = v.floatValue(); PrismaConfig.INSTANCE.save(); });
            this.listWidget.add(new SettingsEntry(bVxao, bVxaoStrength));
            
            Button b4 = createToggle("Point Lights", PrismaConfig.INSTANCE.pointLightsEnabled, v -> PrismaConfig.INSTANCE.pointLightsEnabled = v);
            ConfigSlider bMaxLights = new ConfigSlider("Max Point Lights", 16.0, 256.0, PrismaConfig.INSTANCE.maxPointLights, false, v -> {
                PrismaConfig.INSTANCE.maxPointLights = v.intValue();
                PrismaConfig.INSTANCE.save();
            });
            this.listWidget.add(new SettingsEntry(b4, bMaxLights));

            this.listWidget.add(new SettingsEntry(Component.literal("Volumetric Fog").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            Button bFog = createToggle("Ray Traced Fog", PrismaConfig.INSTANCE.rayMarchedFogEnabled, v -> PrismaConfig.INSTANCE.rayMarchedFogEnabled = v);
            ConfigSlider bFogSamples = new ConfigSlider("Fog Ray Steps", 4.0, 64.0, PrismaConfig.INSTANCE.rayMarchedFogSamples, false, v -> {
                PrismaConfig.INSTANCE.rayMarchedFogSamples = v.intValue();
                PrismaConfig.INSTANCE.save();
            });
            this.listWidget.add(new SettingsEntry(bFog, bFogSamples));
            ConfigSlider bFogIntensity = new ConfigSlider("Fog Intensity", 0.1, 3.0, PrismaConfig.INSTANCE.rayMarchedFogIntensity, true, v -> {
                PrismaConfig.INSTANCE.rayMarchedFogIntensity = v.floatValue();
                PrismaConfig.INSTANCE.save();
            });
            this.listWidget.add(new SettingsEntry(bFogIntensity, null));

            this.listWidget.add(new SettingsEntry(Component.literal("Shadows & Quality").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            Button b5 = createToggle("Sun Shadows", PrismaConfig.INSTANCE.sunShadowsEnabled, v -> PrismaConfig.INSTANCE.sunShadowsEnabled = v);
            Button b7 = createToggle("Player Shadows", PrismaConfig.INSTANCE.playerShadowEnabled, v -> PrismaConfig.INSTANCE.playerShadowEnabled = v);
            this.listWidget.add(new SettingsEntry(b5, b7));
            
            ConfigSlider bRays = new ConfigSlider("Penumbra Ray Count", 0.0, 32.0, PrismaConfig.INSTANCE.shadowRayCount, false, v -> {
                PrismaConfig.INSTANCE.shadowRayCount = v.intValue();
                PrismaConfig.INSTANCE.save();
            });
            bRays.setWidth(310); // Make it take two slots if possible
            this.listWidget.add(new SettingsEntry(bRays, null));
            
            this.listWidget.add(new SettingsEntry(Component.literal("Ray Traced Reflections").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            Button b9 = createToggle("Reflections", PrismaConfig.INSTANCE.reflectionsEnabled, v -> PrismaConfig.INSTANCE.reflectionsEnabled = v);
                        this.listWidget.add(new SettingsEntry(b9, null));

                                    
            Button b13 = createToggle("Reflect Double AO", PrismaConfig.INSTANCE.doubleAoInReflections, v -> PrismaConfig.INSTANCE.doubleAoInReflections = v);
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

            this.listWidget.add(new SettingsEntry(Component.literal("Post-Processing").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            Button b1 = createToggle("Motion Blur", PrismaConfig.INSTANCE.motionBlurEnabled, v -> PrismaConfig.INSTANCE.motionBlurEnabled = v);
            this.listWidget.add(new SettingsEntry(b1, null));

            this.listWidget.add(new SettingsEntry(Component.literal("Cascaded Upscaling (MFX + EASU)").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            
            ConfigSlider bMfxQ = new ConfigSlider("Base Render Scale (MFX Input)", 0.25, 1.0, PrismaConfig.INSTANCE.metalFxResolutionScale, true, v -> {
                PrismaConfig.INSTANCE.metalFxResolutionScale = v.floatValue();
                PrismaConfig.INSTANCE.hasCustomMetalFxScale = true;
                PrismaConfig.INSTANCE.save();
            });
            ConfigSlider bEasuQ = new ConfigSlider("Intermediate Scale (EASU Input)", 0.25, 1.0, PrismaConfig.INSTANCE.easuResolutionScale, true, v -> {
                PrismaConfig.INSTANCE.easuResolutionScale = v.floatValue();
                PrismaConfig.INSTANCE.save();
            });
            this.listWidget.add(new SettingsEntry(bMfxQ, bEasuQ));
            ConfigSlider bUnsharp = new ConfigSlider("Sharpening / Reverse Blur", 0.0, 1.0, PrismaConfig.INSTANCE.unsharpMaskStrength, true, v -> {
                PrismaConfig.INSTANCE.unsharpMaskStrength = v.floatValue();
                PrismaConfig.INSTANCE.save();
            });
            this.listWidget.add(new SettingsEntry(bUnsharp, null));         

        }
        else if (this.currentTab == Tab.PRESETS) {
            this.listWidget.add(new SettingsEntry(Component.literal("Global Presets").withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD)));
            this.listWidget.add(new SettingsEntry(Component.literal("Recomendado: rode o Minecraft em 1152x720").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC)));
            
            Button presetM1 = Button.builder(Component.literal("Preset: M1 Air Low (45-60 FPS)"), (b) -> {
                PrismaConfig.INSTANCE.shadowRayCount = 2;
                PrismaConfig.INSTANCE.sunShadowsEnabled = true;
                PrismaConfig.INSTANCE.pointLightsEnabled = true;
                PrismaConfig.INSTANCE.maxPointLights = 48;
                PrismaConfig.INSTANCE.voxelRadius = 4;
                PrismaConfig.INSTANCE.doubleAoEnabled = true;
                PrismaConfig.INSTANCE.cloudQualitySteps = 15;
                PrismaConfig.INSTANCE.metalFxResolutionScale = 0.65f;
                PrismaConfig.INSTANCE.easuResolutionScale = 0.65f;
                PrismaConfig.INSTANCE.unsharpMaskStrength = 0.5f;
                
                // Emulate 1152x720 by using a base scale of ~0.45 if running on full 2560x1600 Retina display
                // If running at 1920x1080, scale would be ~0.60
                // We'll set the resolution scale to 0.65 explicitly as requested.
                
                PrismaConfig.INSTANCE.save();
                this.minecraft.setScreenAndShow(new PrismaShaderSettingsScreen(this.parent, Tab.PRESETS));
            }).bounds(0, 0, 310, 20).build();
            this.listWidget.add(new SettingsEntry(presetM1, null));
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

