package com.prisma.config;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PrismaConfig {
    public static final PrismaConfig INSTANCE = new PrismaConfig();

        public volatile int voxelRadius = 6;
    public volatile float caveLighting = 0.05f;
    public volatile boolean doubleAoEnabled = false;
    public volatile float doubleAoStrength = 1.0f;
    public volatile String shaderPack = "VXR Default";

    public volatile boolean pointLightsEnabled = false;
    public volatile boolean reflectionsEnabled = false;
    public volatile boolean cloudsInReflections = true;

    public volatile boolean doubleAoInReflections = true;
    public volatile boolean sunShadowsEnabled = false;

    public volatile boolean playerShadowEnabled = false;
                

    public volatile int csmResolution = 2048;
    public volatile int csmCascades = 3;
    public volatile boolean waterWavesEnabled = false;
    public volatile float waterWaveStrength = 1.0f;
    public volatile float waterWaveSpeed = 1.0f;
    public volatile float waterAbsorptionStrength = 1.0f;
    public volatile boolean volumetricCloudsEnabled = false;
    public volatile int cloudQualitySteps = 30;
    public volatile float upscalingRatio = 1.0f;
    public volatile boolean motionBlurEnabled = true;

    // ReSTIR
     // PCF soft shadow for point lights
    public volatile float metalFxResolutionScale = 0.5f;
    public volatile float easuResolutionScale = 1.0f;
    public volatile float unsharpMaskStrength = 0.0f;
    public volatile int shadowRayCount = 3;
    // Ray Traced Volumetric Fog Scattering
    public volatile boolean rayMarchedFogEnabled = false;
    public volatile int rayMarchedFogSamples = 12;
    public volatile float rayMarchedFogIntensity = 1.0f;

    // Point Light Limit Slider (16, 32, 64, 128, 256)
    public volatile int maxPointLights = 64;

    
    private PrismaConfig() {
    }

    public void save() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Files.createDirectories(configDir);
            Path configFile = configDir.resolve("prisma.json");
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"shaderPack\":\"").append(shaderPack != null ? shaderPack : "").append("\",");
            sb.append("\"voxelRadius\":").append(voxelRadius).append(",");
            sb.append("\"doubleAoEnabled\":").append(doubleAoEnabled).append(",");
            sb.append(String.format(java.util.Locale.ROOT, "\"doubleAoStrength\":%.2f,", doubleAoStrength));
            sb.append("\"pointLightsEnabled\":").append(pointLightsEnabled).append(",");
            sb.append("\"reflectionsEnabled\":").append(reflectionsEnabled).append(",");
            sb.append("\"cloudsInReflections\":").append(cloudsInReflections).append(",");
            sb.append("\"doubleAoInReflections\":").append(doubleAoInReflections).append(",");
            sb.append("\"sunShadowsEnabled\":").append(sunShadowsEnabled).append(",");
            sb.append("\"csmResolution\":").append(csmResolution).append(",");
            sb.append("\"csmCascades\":").append(csmCascades).append(",");
            sb.append("\"waterWavesEnabled\":").append(waterWavesEnabled).append(",");
            sb.append(String.format(java.util.Locale.ROOT, "\"waterWaveStrength\":%.2f,", waterWaveStrength));
            sb.append(String.format(java.util.Locale.ROOT, "\"waterWaveSpeed\":%.2f,", waterWaveSpeed));
            sb.append(String.format(java.util.Locale.ROOT, "\"waterAbsorptionStrength\":%.2f,", waterAbsorptionStrength));
            sb.append("\"volumetricCloudsEnabled\":").append(volumetricCloudsEnabled).append(",");
            sb.append("\"cloudQualitySteps\":").append(cloudQualitySteps).append(",");
            sb.append(String.format(java.util.Locale.ROOT, "\"upscalingRatio\":%.2f,", upscalingRatio));
            sb.append("\"motionBlurEnabled\":").append(motionBlurEnabled).append(",");
            sb.append("\"playerShadowEnabled\":").append(playerShadowEnabled).append(",");
                        sb.append("\"metalFxResolutionScale\":").append(metalFxResolutionScale).append(",");
            sb.append("\"easuResolutionScale\":").append(easuResolutionScale).append(",");
            sb.append("\"unsharpMaskStrength\":").append(unsharpMaskStrength).append(",");
            sb.append("\"shadowRayCount\":").append(shadowRayCount).append(",");
            sb.append(String.format(java.util.Locale.ROOT, "\"caveLighting\":%.2f,", caveLighting));
            sb.append("\"rayMarchedFogEnabled\":").append(rayMarchedFogEnabled).append(",");
            sb.append("\"rayMarchedFogSamples\":").append(rayMarchedFogSamples).append(",");
            sb.append(String.format(java.util.Locale.ROOT, "\"rayMarchedFogIntensity\":%.2f,", rayMarchedFogIntensity));
            sb.append("\"maxPointLights\":").append(maxPointLights);
            sb.append("}");
            Files.writeString(configFile, sb.toString());
        } catch (Throwable ignored) {
        }
    }

    public void load() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Path configFile = configDir.resolve("prisma.json");
            if (Files.exists(configFile)) {
                String content = Files.readString(configFile);
                if (content.contains("\"shaderPack\":")) {
                    try {
                        int idx = content.indexOf("\"shaderPack\":") + 13;
                        int firstQuote = content.indexOf("\"", idx);
                        if (firstQuote >= 0) {
                            int secondQuote = content.indexOf("\"", firstQuote + 1);
                            if (secondQuote >= 0) {
                                shaderPack = content.substring(firstQuote + 1, secondQuote);
                            }
                        }
                    } catch (Exception ignored) {}
                }

                String val;
                if ((val = getJsonValue(content, "voxelRadius")) != null) {
                    try { voxelRadius = Math.clamp(Integer.parseInt(val), 2, 16); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "doubleAoEnabled")) != null) {
                    try { doubleAoEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "doubleAoStrength")) != null) {
                    try { doubleAoStrength = Math.clamp(Float.parseFloat(val), 0.5f, 2.5f); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "doubleAoInReflections")) != null) {
                    try { doubleAoInReflections = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }

                if ((val = getJsonValue(content, "reflectionsEnabled")) != null) {
                    try { reflectionsEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "cloudsInReflections")) != null) {
                    try { cloudsInReflections = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "pointLightsEnabled")) != null) {
                    try { pointLightsEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "sunShadowsEnabled")) != null) {
                    try { sunShadowsEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "waterWavesEnabled")) != null) {
                    try { waterWavesEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "waterWaveStrength")) != null) {
                    try { waterWaveStrength = Math.clamp(Float.parseFloat(val), 0.5f, 2.5f); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "waterWaveSpeed")) != null) {
                    try { waterWaveSpeed = Math.clamp(Float.parseFloat(val), 0.5f, 2.5f); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "waterAbsorptionStrength")) != null) {
                    try { waterAbsorptionStrength = Math.clamp(Float.parseFloat(val), 0.5f, 2.5f); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "volumetricCloudsEnabled")) != null) {
                    try { volumetricCloudsEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "cloudQualitySteps")) != null) {
                    try { cloudQualitySteps = Math.clamp(Integer.parseInt(val), 10, 80); } catch (Throwable ignored) {}
                }
                
                if ((val = getJsonValue(content, "playerShadowEnabled")) != null) {
                    try { playerShadowEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                
                if ((val = getJsonValue(content, "metalFxResolutionScale")) != null) {
                    try { metalFxResolutionScale = Float.parseFloat(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "easuResolutionScale")) != null) {
                    try { easuResolutionScale = Float.parseFloat(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "unsharpMaskStrength")) != null) {
                    try { unsharpMaskStrength = Float.parseFloat(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "shadowRayCount")) != null) {
                    try { shadowRayCount = Integer.parseInt(val); } catch (Throwable ignored) {}
                }

                if ((val = getJsonValue(content, "caveLighting")) != null) {
                    try { caveLighting = Float.parseFloat(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "motionBlurEnabled")) != null) {
                    try { motionBlurEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "rayMarchedFogEnabled")) != null) {
                    try { rayMarchedFogEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "rayMarchedFogSamples")) != null) {
                    try { rayMarchedFogSamples = Math.clamp(Integer.parseInt(val), 4, 64); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "rayMarchedFogIntensity")) != null) {
                    try { rayMarchedFogIntensity = Math.clamp(Float.parseFloat(val), 0.1f, 4.0f); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "maxPointLights")) != null) {
                    // Allowed values: 16, 32, 64, 128, 256. Clamp to [16, 256].
                    try { maxPointLights = Math.clamp(Integer.parseInt(val), 16, 256); } catch (Throwable ignored) {}
                }

            }
        } catch (Throwable ignored) {
        }
    }

    private static String getJsonValue(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        int end = findJsonEnd(json, start);
        return json.substring(start, end).trim();
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
