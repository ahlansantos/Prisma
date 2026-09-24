package com.prisma.config;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class PrismaSodiumConfig implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(final ConfigBuilder builder) {
        ModOptionsBuilder modOptions = builder.registerModOptions("prisma");
        modOptions.setName("Prisma");
        modOptions.setVersion(net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("prisma").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("20092026-0.2.1-nightly"));
        modOptions.setIcon(Identifier.fromNamespaceAndPath("prisma", "icon-mono.png"));

        OptionPageBuilder lightingPage = builder.createOptionPage();
        lightingPage.setName(Component.literal("⚡ Lighting & Shadows"));

        OptionGroupBuilder archGroup = builder.createOptionGroup();
        archGroup.setName(Component.literal("Voxel Engine"));

        IntegerOptionBuilder voxelRadiusOption = builder.createIntegerOption(
                Identifier.fromNamespaceAndPath("prisma", "voxel_radius")
        );
        voxelRadiusOption.setName(Component.literal("Voxelization Radius"));
        voxelRadiusOption.setTooltip(Component.literal("Radius of chunks surrounding the camera voxelized into Apple Silicon Unified Memory (2 to 16 chunks). Powers real-time Double AO and DDA point light shadows."));
        voxelRadiusOption.setRange(2, 16, 1);
        voxelRadiusOption.setDefaultValue(6);
        voxelRadiusOption.setValueFormatter(val -> Component.literal(val + " Chunks (" + (val * 32) + "m diameter)"));
        voxelRadiusOption.setBinding(
                val -> PrismaConfig.INSTANCE.voxelRadius = val,
                () -> PrismaConfig.INSTANCE.voxelRadius
        );
        voxelRadiusOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        archGroup.addOption(voxelRadiusOption);

        OptionGroupBuilder vxaoGroup = builder.createOptionGroup();
        vxaoGroup.setName(Component.literal("Double AO (VXAO + SSAO)"));

        BooleanOptionBuilder vxaoOption = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "vxao")
        );
        vxaoOption.setName(Component.literal("Enable Double AO"));
        vxaoOption.setTooltip(Component.literal("Combines 3D Voxel Ambient Occlusion for macro spaces with Screen-Space Ambient Occlusion for fine geometry contact shadows."));
        vxaoOption.setDefaultValue(true);
        vxaoOption.setBinding(
                val -> PrismaConfig.INSTANCE.vxaoEnabled = val,
                () -> PrismaConfig.INSTANCE.vxaoEnabled
        );
        vxaoOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        vxaoGroup.addOption(vxaoOption);

        IntegerOptionBuilder vxaoStrengthOption = builder.createIntegerOption(
                Identifier.fromNamespaceAndPath("prisma", "vxao_strength")
        );
        vxaoStrengthOption.setName(Component.literal("AO Occlusion Intensity"));
        vxaoStrengthOption.setTooltip(Component.literal("Scales the darkness and contrast of ambient occlusion shadows across surfaces and crevices."));
        vxaoStrengthOption.setRange(50, 200, 10);
        vxaoStrengthOption.setDefaultValue(100);
        vxaoStrengthOption.setValueFormatter(val -> Component.literal(val + "%"));
        vxaoStrengthOption.setBinding(
                val -> PrismaConfig.INSTANCE.vxaoStrength = val / 100.0f,
                () -> Math.round(PrismaConfig.INSTANCE.vxaoStrength * 100.0f)
        );
        vxaoStrengthOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        vxaoGroup.addOption(vxaoStrengthOption);

        OptionGroupBuilder vplsGroup = builder.createOptionGroup();
        vplsGroup.setName(Component.literal("Point Light Shadows (VPLS)"));

        BooleanOptionBuilder pointLightsOption = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "point_lights")
        );
        pointLightsOption.setName(Component.literal("Point Light DDA Shadows"));
        pointLightsOption.setTooltip(Component.literal("Real-time line-of-sight raymarched geometric shadows for torches, lanterns, and light sources using 3D DDA traversal."));
        pointLightsOption.setDefaultValue(true);
        pointLightsOption.setBinding(
                val -> PrismaConfig.INSTANCE.pointLightsEnabled = val,
                () -> PrismaConfig.INSTANCE.pointLightsEnabled
        );
        pointLightsOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        vplsGroup.addOption(pointLightsOption);

        IntegerOptionBuilder maxPtLightsOption = builder.createIntegerOption(
                Identifier.fromNamespaceAndPath("prisma", "max_pt_lights")
        );
        maxPtLightsOption.setName(Component.literal("Max Point Lights"));
        maxPtLightsOption.setTooltip(Component.literal("Maximum number of point lights processed per pixel."));
        maxPtLightsOption.setRange(0, 1024, 16);
        maxPtLightsOption.setDefaultValue(32);
        maxPtLightsOption.setValueFormatter(val -> Component.literal(val + " lights"));
        maxPtLightsOption.setBinding(
                val -> PrismaConfig.INSTANCE.maxPointLights = val,
                () -> PrismaConfig.INSTANCE.maxPointLights
        );
        maxPtLightsOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        vplsGroup.addOption(maxPtLightsOption);

        lightingPage.addOptionGroup(archGroup);
        lightingPage.addOptionGroup(vxaoGroup);
        lightingPage.addOptionGroup(vplsGroup);

        

        
        



        OptionPageBuilder waterPage = builder.createOptionPage();
        waterPage.setName(Component.literal("💧 Water & Fluids"));

        OptionGroupBuilder waveGroup = builder.createOptionGroup();
        waveGroup.setName(Component.literal("Trochoidal Ocean Waves (Eclipse/Bliss)"));

        BooleanOptionBuilder waterWavesOption = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "water_waves")
        );
        waterWavesOption.setName(Component.literal("Trochoidal Wave Simulation"));
        waterWavesOption.setTooltip(Component.literal("Simulates realistic pinched trochoidal ocean waves with analytical horizontal drag displacement based on the Eclipse / Bliss shader model."));
        waterWavesOption.setDefaultValue(true);
        waterWavesOption.setBinding(
                val -> PrismaConfig.INSTANCE.waterWavesEnabled = val,
                () -> PrismaConfig.INSTANCE.waterWavesEnabled
        );
        waterWavesOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        waveGroup.addOption(waterWavesOption);

        IntegerOptionBuilder waveStrengthOption = builder.createIntegerOption(
                Identifier.fromNamespaceAndPath("prisma", "water_wave_strength")
        );
        waveStrengthOption.setName(Component.literal("Wave Amplitude"));
        waveStrengthOption.setTooltip(Component.literal("Controls the height and slope perturbation of water surface ripples."));
        waveStrengthOption.setRange(25, 200, 10);
        waveStrengthOption.setDefaultValue(100);
        waveStrengthOption.setValueFormatter(val -> Component.literal(val + "%"));
        waveStrengthOption.setBinding(
                val -> PrismaConfig.INSTANCE.waterWaveStrength = val / 100.0f,
                () -> Math.round(PrismaConfig.INSTANCE.waterWaveStrength * 100.0f)
        );
        waveStrengthOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        waveGroup.addOption(waveStrengthOption);

        IntegerOptionBuilder waveSpeedOption = builder.createIntegerOption(
                Identifier.fromNamespaceAndPath("prisma", "water_wave_speed")
        );
        waveSpeedOption.setName(Component.literal("Wave Flow Speed"));
        waveSpeedOption.setTooltip(Component.literal("Controls the speed of flowing ripples and wave crest progression."));
        waveSpeedOption.setRange(25, 200, 10);
        waveSpeedOption.setDefaultValue(100);
        waveSpeedOption.setValueFormatter(val -> Component.literal(val + "%"));
        waveSpeedOption.setBinding(
                val -> PrismaConfig.INSTANCE.waterWaveSpeed = val / 100.0f,
                () -> Math.round(PrismaConfig.INSTANCE.waterWaveSpeed * 100.0f)
        );
        waveSpeedOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        waveGroup.addOption(waveSpeedOption);

        OptionGroupBuilder opticalGroup = builder.createOptionGroup();
        opticalGroup.setName(Component.literal("Optical & Absorption Properties"));

        IntegerOptionBuilder absorptionOption = builder.createIntegerOption(
                Identifier.fromNamespaceAndPath("prisma", "water_absorption")
        );
        absorptionOption.setName(Component.literal("Water Absorption & Depth Tint"));
        absorptionOption.setTooltip(Component.literal("Physical Beer-Lambert spectral absorption (red absorbs 6x faster than blue) producing tropical turquoise to deep ocean navy color gradients."));
        absorptionOption.setRange(25, 200, 10);
        absorptionOption.setDefaultValue(100);
        absorptionOption.setValueFormatter(val -> Component.literal(val + "%"));
        absorptionOption.setBinding(
                val -> PrismaConfig.INSTANCE.waterAbsorptionStrength = val / 100.0f,
                () -> Math.round(PrismaConfig.INSTANCE.waterAbsorptionStrength * 100.0f)
        );
        absorptionOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        opticalGroup.addOption(absorptionOption);

        waterPage.addOptionGroup(waveGroup);
        waterPage.addOptionGroup(opticalGroup);

        

        

        OptionPageBuilder reflectionsPage = builder.createOptionPage();
        reflectionsPage.setName(Component.literal("✨ Reflections"));

        OptionGroupBuilder reflGroup = builder.createOptionGroup();
        BooleanOptionBuilder reflEnableOpt = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "reflections_enabled")
        );
        reflEnableOpt.setName(Component.literal("Enable Reflections"));
        reflEnableOpt.setTooltip(Component.literal("Toggles real-time raytraced reflections on water and metals."));
        reflEnableOpt.setDefaultValue(true);
        reflEnableOpt.setBinding(
                val -> PrismaConfig.INSTANCE.reflectionsEnabled = val,
                () -> PrismaConfig.INSTANCE.reflectionsEnabled
        );
        reflEnableOpt.setStorageHandler(PrismaConfig.INSTANCE::save);
        reflGroup.addOption(reflEnableOpt);

        reflGroup.setName(Component.literal("Reflection Shadows & Lighting"));

        BooleanOptionBuilder reflPtShadowsOption = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "reflection_pt_shadows")
        );
        reflPtShadowsOption.setName(Component.literal("Point Light Shadows"));
        reflPtShadowsOption.setTooltip(Component.literal("Enables raymarched point light shadows inside reflections."));
        reflPtShadowsOption.setDefaultValue(true);
        reflPtShadowsOption.setBinding(
                val -> PrismaConfig.INSTANCE.reflectionPointLightShadows = val,
                () -> PrismaConfig.INSTANCE.reflectionPointLightShadows
        );
        reflPtShadowsOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        reflGroup.addOption(reflPtShadowsOption);

        BooleanOptionBuilder reflDirShadowsOption = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "reflection_dir_shadows")
        );
        reflDirShadowsOption.setName(Component.literal("Directional Shadows"));
        reflDirShadowsOption.setTooltip(Component.literal("Enables sun/moon shadows inside reflections."));
        reflDirShadowsOption.setDefaultValue(true);
        reflDirShadowsOption.setBinding(
                val -> PrismaConfig.INSTANCE.reflectionDirectionalShadows = val,
                () -> PrismaConfig.INSTANCE.reflectionDirectionalShadows
        );
        reflDirShadowsOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        reflGroup.addOption(reflDirShadowsOption);

        BooleanOptionBuilder vxaoReflOption = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "vxao_refl")
        );
        vxaoReflOption.setName(Component.literal("VXAO in Reflections"));
        vxaoReflOption.setTooltip(Component.literal("Applies Ambient Occlusion logic inside voxel reflections."));
        vxaoReflOption.setDefaultValue(true);
        vxaoReflOption.setBinding(
                val -> PrismaConfig.INSTANCE.vxaoInReflections = val,
                () -> PrismaConfig.INSTANCE.vxaoInReflections
        );
        vxaoReflOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        reflGroup.addOption(vxaoReflOption);
        BooleanOptionBuilder cloudsReflOption = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "clouds_refl")
        );
        cloudsReflOption.setName(Component.literal("Reflect Clouds"));
        cloudsReflOption.setTooltip(Component.literal("Renders volumetric clouds inside water reflections. Can impact performance."));
        cloudsReflOption.setDefaultValue(true);
        cloudsReflOption.setBinding(
                val -> PrismaConfig.INSTANCE.cloudsInReflections = val,
                () -> PrismaConfig.INSTANCE.cloudsInReflections
        );
        cloudsReflOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        reflGroup.addOption(cloudsReflOption);
        
        

        
        reflectionsPage.addOptionGroup(reflGroup);
        

            
        
        OptionPageBuilder cloudsPage = builder.createOptionPage();
        cloudsPage.setName(Component.literal("☁️ Atmosphere & Clouds"));

        OptionGroupBuilder cloudGroup = builder.createOptionGroup();
        cloudGroup.setName(Component.literal("Volumetric Raymarching"));

        BooleanOptionBuilder cloudsEnabledOption = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "volumetric_clouds")
        );
        cloudsEnabledOption.setName(Component.literal("Volumetric Clouds"));
        cloudsEnabledOption.setTooltip(Component.literal("Renders physically based 3D clouds using fractal raymarching. High performance impact."));
        cloudsEnabledOption.setDefaultValue(true);
        cloudsEnabledOption.setBinding(
                val -> PrismaConfig.INSTANCE.volumetricCloudsEnabled = val,
                () -> PrismaConfig.INSTANCE.volumetricCloudsEnabled
        );
        cloudsEnabledOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        cloudGroup.addOption(cloudsEnabledOption);

        IntegerOptionBuilder cloudStepsOption = builder.createIntegerOption(
                Identifier.fromNamespaceAndPath("prisma", "cloud_quality_steps")
        );
        cloudStepsOption.setName(Component.literal("Cloud Raymarch Steps"));
        cloudStepsOption.setTooltip(Component.literal("Higher steps equal better cloud details and denser shadows, but heavily drops FPS."));
        cloudStepsOption.setRange(10, 80, 5);
        cloudStepsOption.setDefaultValue(30);
        cloudStepsOption.setValueFormatter(val -> Component.literal(String.valueOf(val)));
        cloudStepsOption.setBinding(
                val -> PrismaConfig.INSTANCE.cloudQualitySteps = val,
                () -> PrismaConfig.INSTANCE.cloudQualitySteps
        );
        cloudStepsOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        cloudGroup.addOption(cloudStepsOption);

        cloudsPage.addOptionGroup(cloudGroup);

        modOptions.addPage(cloudsPage);
        modOptions.addPage(lightingPage);
        modOptions.addPage(waterPage);
        modOptions.addPage(reflectionsPage);
        

        OptionPageBuilder perfPage = builder.createOptionPage();
        perfPage.setName(Component.literal("🚀 Performance & Upscaling"));

        OptionGroupBuilder frameGroup = builder.createOptionGroup();
        frameGroup.setName(Component.literal("⚡ Rendering Optimizations"));

        BooleanOptionBuilder upscalingOption = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "upscaling_mode")
        );
        upscalingOption.setName(net.minecraft.network.chat.Component.literal("TAA Upscaling (Half-Res)"));
        upscalingOption.setTooltip(net.minecraft.network.chat.Component.literal("Renders heavy effects at half resolution and temporally upscales them."));
        upscalingOption.setDefaultValue(false);
        upscalingOption.setBinding(
                val -> PrismaConfig.INSTANCE.upscalingMode = val ? 2 : 1,
                () -> PrismaConfig.INSTANCE.upscalingMode >= 2
        );
        upscalingOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        frameGroup.addOption(upscalingOption);

        
        BooleanOptionBuilder mbOption = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "motion_blur")
        );
        mbOption.setName(Component.literal("Camera Motion Blur"));
        mbOption.setTooltip(Component.literal("Blurs the screen when the camera moves quickly."));
        mbOption.setDefaultValue(true);
        mbOption.setBinding(
                val -> PrismaConfig.INSTANCE.motionBlurEnabled = val,
                () -> PrismaConfig.INSTANCE.motionBlurEnabled
        );
        mbOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        frameGroup.addOption(mbOption);

        BooleanOptionBuilder spaceWarpOption = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "space_warp")
        );
        spaceWarpOption.setName(Component.literal("Async Space Frame Warp (FrameGen)"));
        spaceWarpOption.setTooltip(Component.literal("Asynchronous Reprojection. Doubles the framerate of camera movement by reprojecting the previous frame."));
        spaceWarpOption.setDefaultValue(false);
        spaceWarpOption.setBinding(
                val -> PrismaConfig.INSTANCE.spaceWarpEnabled = val,
                () -> PrismaConfig.INSTANCE.spaceWarpEnabled
        );
        spaceWarpOption.setStorageHandler(PrismaConfig.INSTANCE::save);
        frameGroup.addOption(spaceWarpOption);

        perfPage.addOptionGroup(frameGroup);
        modOptions.addPage(perfPage);

        OptionPageBuilder expPage = builder.createOptionPage();
        expPage.setName(Component.literal("🧪 Experimental"));

        OptionGroupBuilder expGroup = builder.createOptionGroup();
        expGroup.setName(Component.literal("Player Rendering"));

        BooleanOptionBuilder pShadowOpt = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "player_shadows")
        );
        pShadowOpt.setName(Component.literal("Player Shadows"));
        pShadowOpt.setTooltip(Component.literal("Renders the player's shadow from sunlight and point lights."));
        pShadowOpt.setDefaultValue(true);
        pShadowOpt.setBinding(
                val -> PrismaConfig.INSTANCE.playerShadowEnabled = val,
                () -> PrismaConfig.INSTANCE.playerShadowEnabled
        );
        pShadowOpt.setStorageHandler(PrismaConfig.INSTANCE::save);
        expGroup.addOption(pShadowOpt);

        BooleanOptionBuilder pReflOpt = builder.createBooleanOption(
                Identifier.fromNamespaceAndPath("prisma", "player_reflections")
        );
        pReflOpt.setName(Component.literal("Player Reflections"));
        pReflOpt.setTooltip(Component.literal("Renders the player model in raytraced reflections."));
        pReflOpt.setDefaultValue(true);
        pReflOpt.setBinding(
                val -> PrismaConfig.INSTANCE.playerReflectionEnabled = val,
                () -> PrismaConfig.INSTANCE.playerReflectionEnabled
        );
        pReflOpt.setStorageHandler(PrismaConfig.INSTANCE::save);
        expGroup.addOption(pReflOpt);

        expPage.addOptionGroup(expGroup);

        modOptions.addPage(expPage);
        


        


        

    }
}