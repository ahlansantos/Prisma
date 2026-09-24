package com.prisma.config;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PrismaConfig {
    public static final PrismaConfig INSTANCE = new PrismaConfig();

        public volatile int voxelRadius = 6;
    public volatile boolean vxaoEnabled = true;
    public volatile float vxaoStrength = 1.0f;
    public volatile boolean pointLightsEnabled = true;
    public volatile boolean reflectionsEnabled = true;
    public volatile boolean cloudsInReflections = true;
    public volatile int maxPointLights = 32;
    public volatile boolean reflectionPointLightShadows = true;
    public volatile boolean reflectionDirectionalShadows = true;
    public volatile boolean vxaoInReflections = true;
    public volatile boolean sunShadowsEnabled = false;



    public volatile boolean playerShadowEnabled = true;
    public volatile boolean playerReflectionEnabled = true;
    public volatile int shadowQuality = 2;
        public volatile int reflectionBounces = 2;
    

    public volatile int csmResolution = 2048;
    public volatile int csmCascades = 3;
    public volatile boolean waterWavesEnabled = true;
    public volatile float waterWaveStrength = 1.0f;
    public volatile float waterWaveSpeed = 1.0f;
    public volatile float waterAbsorptionStrength = 1.0f;
    public volatile boolean volumetricCloudsEnabled = true;
    public volatile int cloudQualitySteps = 30;
    public volatile int upscalingMode = 0; // 0=Off, 1=Half, 2=Quarter
    public volatile boolean spaceWarpEnabled = false;
    public volatile boolean motionBlurEnabled = true;

    
    private PrismaConfig() {
    }

    public void save() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Files.createDirectories(configDir);
            Path configFile = configDir.resolve("prisma.json");
            Files.writeString(configFile, String.format(java.util.Locale.ROOT,
                    "{\"debugView\":\"%s\",\"voxelRadius\":%d,\"vxaoEnabled\":%b,\"vxaoStrength\":%.2f,\"pointLightsEnabled\":%b,\"reflectionsEnabled\":%b,\"cloudsInReflections\":%b,\"maxPointLights\":%d,\"reflectionPointLightShadows\":%b,\"reflectionDirectionalShadows\":%b,\"vxaoInReflections\":%b,\"sunShadowsEnabled\":%b,\"csmResolution\":%d,\"csmCascades\":%d,\"waterWavesEnabled\":%b,\"waterWaveStrength\":%.2f,\"waterWaveSpeed\":%.2f,\"waterAbsorptionStrength\":%.2f,\"volumetricCloudsEnabled\":%b,\"cloudQualitySteps\":%d,\"shadowQuality\":%d,\"reflectionBounces\":%d,\"upscalingMode\":%d,\"spaceWarpEnabled\":%b}",
                    voxelRadius, vxaoEnabled, vxaoStrength, pointLightsEnabled, reflectionsEnabled, cloudsInReflections, maxPointLights, reflectionPointLightShadows, reflectionDirectionalShadows, vxaoInReflections, sunShadowsEnabled, csmResolution, csmCascades, waterWavesEnabled, waterWaveStrength, waterWaveSpeed, waterAbsorptionStrength, volumetricCloudsEnabled, cloudQualitySteps, shadowQuality, reflectionBounces, upscalingMode, spaceWarpEnabled, motionBlurEnabled));
        } catch (Throwable ignored) {
        }
    }

    public void load() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Path configFile = configDir.resolve("prisma.json");
            if (Files.exists(configFile)) {
                String content = Files.readString(configFile);
                if (content.contains("\"voxelRadius\":")) {
                    try {
                        int idx = content.indexOf("\"voxelRadius\":") + 14;
                        int end = findJsonEnd(content, idx);
                        voxelRadius = Math.clamp(Integer.parseInt(content.substring(idx, end).trim()), 2, 16);
                    } catch (Throwable ignored) {
                    }
                }

                if (content.contains("\"vxaoEnabled\":")) {
                    try {
                        int idx = content.indexOf("\"vxaoEnabled\":") + 14;
                        int end = findJsonEnd(content, idx);
                        vxaoEnabled = Boolean.parseBoolean(content.substring(idx, end).trim());
                    } catch (Throwable ignored) {
                    }
                }

                if (content.contains("\"vxaoStrength\":")) {
                    try {
                        int idx = content.indexOf("\"vxaoStrength\":") + 15;
                        int end = findJsonEnd(content, idx);
                        vxaoStrength = Math.clamp(Float.parseFloat(content.substring(idx, end).trim()), 0.5f, 2.5f);
                    } catch (Throwable ignored) {
                    }
                }

                
                if (content.contains("\"vxaoInReflections\":")) {
                    try {
                        int idx = content.indexOf("\"vxaoInReflections\":") + 20;
                        int end = findJsonEnd(content, idx);
                        vxaoInReflections = Boolean.parseBoolean(content.substring(idx, end).trim());
                    } catch (Throwable ignored) { }
                }
                
                if (content.contains("\"maxPointLights\":")) {
                    try {
                        int idx = content.indexOf("\"maxPointLights\":") + 17;
                        int end = findJsonEnd(content, idx);
                        maxPointLights = Math.clamp(Integer.parseInt(content.substring(idx, end).trim()), 0, 1024);
                    } catch (Throwable ignored) { }
                }
                
                if (content.contains("\"reflectionPointLightShadows\":")) {
                    try {
                        int idx = content.indexOf("\"reflectionPointLightShadows\":") + 30;
                        int end = findJsonEnd(content, idx);
                        reflectionPointLightShadows = Boolean.parseBoolean(content.substring(idx, end).trim());
                    } catch (Throwable ignored) { }
                }
                
                if (content.contains("\"reflectionDirectionalShadows\":")) {
                    try {
                        int idx = content.indexOf("\"reflectionDirectionalShadows\":") + 31;
                        int end = findJsonEnd(content, idx);
                        reflectionDirectionalShadows = Boolean.parseBoolean(content.substring(idx, end).trim());
                    } catch (Throwable ignored) { }
                }

                if (content.contains("\"reflectionsEnabled\":")) {
                    try {
                        int idx = content.indexOf("\"reflectionsEnabled\":") + 21;
                        int end = findJsonEnd(content, idx);
                        reflectionsEnabled = Boolean.parseBoolean(content.substring(idx, end).trim());
                    } catch (Throwable ignored) {
                    }
                }

                if (content.contains("\"cloudsInReflections\":")) {
                    try {
                        int idx = content.indexOf("\"cloudsInReflections\":") + 22;
                        int end = findJsonEnd(content, idx);
                        cloudsInReflections = Boolean.parseBoolean(content.substring(idx, end).trim());
                    } catch (Throwable ignored) {
                    }
                }

                if (content.contains("\"pointLightsEnabled\":")) {
                    try {
                        int idx = content.indexOf("\"pointLightsEnabled\":") + 21;
                        int end = findJsonEnd(content, idx);
                        pointLightsEnabled = Boolean.parseBoolean(content.substring(idx, end).trim());
                    } catch (Throwable ignored) {
                    }
                }

                if (content.contains("\"sunShadowsEnabled\":")) {
                    try {
                        int idx = content.indexOf("\"sunShadowsEnabled\":") + 20;
                        int end = findJsonEnd(content, idx);
                        sunShadowsEnabled = Boolean.parseBoolean(content.substring(idx, end).trim());
                    } catch (Throwable ignored) {
                    }
                }



                if (content.contains("\"waterWavesEnabled\":")) {
                    try {
                        int idx = content.indexOf("\"waterWavesEnabled\":") + 20;
                        int end = findJsonEnd(content, idx);
                        waterWavesEnabled = Boolean.parseBoolean(content.substring(idx, end).trim());
                    } catch (Throwable ignored) {
                    }
                }

                if (content.contains("\"waterWaveStrength\":")) {
                    try {
                        int idx = content.indexOf("\"waterWaveStrength\":") + 20;
                        int end = findJsonEnd(content, idx);
                        waterWaveStrength = Math.clamp(Float.parseFloat(content.substring(idx, end).trim()), 0.5f, 2.5f);
                    } catch (Throwable ignored) {
                    }
                }

                if (content.contains("\"waterWaveSpeed\":")) {
                    try {
                        int idx = content.indexOf("\"waterWaveSpeed\":") + 17;
                        int end = findJsonEnd(content, idx);
                        waterWaveSpeed = Math.clamp(Float.parseFloat(content.substring(idx, end).trim()), 0.5f, 2.5f);
                    } catch (Throwable ignored) {
                    }
                }

                if (content.contains("\"waterAbsorptionStrength\":")) {
                    try {
                        int idx = content.indexOf("\"waterAbsorptionStrength\":") + 26;
                        int end = findJsonEnd(content, idx);
                        waterAbsorptionStrength = Math.clamp(Float.parseFloat(content.substring(idx, end).trim()), 0.5f, 2.5f);
                    } catch (Throwable ignored) {
                    }
                }

                if (content.contains("\"volumetricCloudsEnabled\":")) {
                    try {
                        int idx = content.indexOf("\"volumetricCloudsEnabled\":") + 26;
                        int end = findJsonEnd(content, idx);
                        volumetricCloudsEnabled = Boolean.parseBoolean(content.substring(idx, end).trim());
                    } catch (Throwable ignored) {
                    }
                }

                if (content.contains("\"cloudQualitySteps\":")) {
                    try {
                        int idx = content.indexOf("\"cloudQualitySteps\":") + 20;
                        int end = findJsonEnd(content, idx);
                        cloudQualitySteps = Math.clamp(Integer.parseInt(content.substring(idx, end).trim()), 10, 80);
                    } catch (Throwable ignored) {
                    }
                }

                if (content.contains("\"shadowQuality\":")) {
                    try {
                        int idx = content.indexOf("\"shadowQuality\":") + 16;
                        int end = findJsonEnd(content, idx);
                        shadowQuality = Math.clamp(Integer.parseInt(content.substring(idx, end).trim()), 1, 4);
                    } catch (Throwable ignored) { }
                }
                if (content.contains("\"reflectionBounces\":")) {
                    try {
                        int idx = content.indexOf("\"reflectionBounces\":") + 20;
                        int end = findJsonEnd(content, idx);
                        reflectionBounces = Math.clamp(Integer.parseInt(content.substring(idx, end).trim()), 1, 3);
                    } catch (Throwable ignored) { }
                }
                if (content.contains("\"upscalingMode\":")) {
                    try {
                        int idx = content.indexOf("\"upscalingMode\":") + 16;
                        int end = findJsonEnd(content, idx);
                        upscalingMode = Math.clamp(Integer.parseInt(content.substring(idx, end).trim()), 0, 2);
                    } catch (Throwable ignored) { }
                }
                if (content.contains("\"motionBlurEnabled\":")) {
                    try {
                        int idx = content.indexOf("\"motionBlurEnabled\":") + 20;
                        int end = findJsonEnd(content, idx);
                        motionBlurEnabled = Boolean.parseBoolean(content.substring(idx, end).trim());
                    } catch (Throwable ignored) { }
                }
                if (content.contains("\"spaceWarpEnabled\":")) {
                    try {
                        int idx = content.indexOf("\"spaceWarpEnabled\":") + 19;
                        int end = findJsonEnd(content, idx);
                        spaceWarpEnabled = Boolean.parseBoolean(content.substring(idx, end).trim());
                    } catch (Throwable ignored) { }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static int findJsonEnd(String text, int start) {
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ',' || c == '}' || c == ' ' || c == '\n' || c == '\r') {
                return i;
            }
        }
        return text.length();
    }
}
