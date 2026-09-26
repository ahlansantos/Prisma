package com.prisma.config;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PrismaConfig {
    public static final PrismaConfig INSTANCE = new PrismaConfig();

        public volatile int voxelRadius = 6;
    public volatile float caveLighting = 0.05f;
    public volatile boolean vxaoEnabled = true;
    public volatile float vxaoStrength = 1.0f;
    public volatile String shaderPack = "VXR Default";

    public volatile boolean pointLightsEnabled = true;
    public volatile boolean reflectionsEnabled = true;
    public volatile boolean cloudsInReflections = true;
    public volatile int maxPointLights = 32;
    public volatile boolean reflectionPointLightShadows = true;
    public volatile boolean reflectionDirectionalShadows = true;
    public volatile boolean vxaoInReflections = true;
    public volatile boolean sunShadowsEnabled = true;



    public volatile boolean playerShadowEnabled = true;
    public volatile boolean playerReflectionEnabled = true;
                

    public volatile int csmResolution = 2048;
    public volatile int csmCascades = 3;
    public volatile boolean waterWavesEnabled = true;
    public volatile float waterWaveStrength = 1.0f;
    public volatile float waterWaveSpeed = 1.0f;
    public volatile float waterAbsorptionStrength = 1.0f;
    public volatile boolean volumetricCloudsEnabled = true;
    public volatile int cloudQualitySteps = 30;
    public volatile float upscalingRatio = 1.0f;
    public volatile boolean motionBlurEnabled = true;
    public volatile boolean sdaaEnabled = true;
    public volatile boolean restirShadowsEnabled = true;
    public volatile boolean metalFxUpscalingEnabled = false;
    public volatile int metalFxQuality = 1; // 0=Performance, 1=Balanced, 2=Quality
    public volatile boolean volPointLightsEnabled = false;
    public volatile float volPointLightIntensity = 1.0f;
    public volatile int volPointLightQuality = 15;

    
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
            sb.append("\"vxaoEnabled\":").append(vxaoEnabled).append(",");
            sb.append(String.format(java.util.Locale.ROOT, "\"vxaoStrength\":%.2f,", vxaoStrength));
            sb.append("\"pointLightsEnabled\":").append(pointLightsEnabled).append(",");
            sb.append("\"reflectionsEnabled\":").append(reflectionsEnabled).append(",");
            sb.append("\"cloudsInReflections\":").append(cloudsInReflections).append(",");
            sb.append("\"maxPointLights\":").append(maxPointLights).append(",");
            sb.append("\"reflectionPointLightShadows\":").append(reflectionPointLightShadows).append(",");
            sb.append("\"reflectionDirectionalShadows\":").append(reflectionDirectionalShadows).append(",");
            sb.append("\"vxaoInReflections\":").append(vxaoInReflections).append(",");
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
            sb.append("\"playerReflectionEnabled\":").append(playerReflectionEnabled).append(",");
            sb.append("\"sdaaEnabled\":").append(sdaaEnabled).append(",");
            sb.append("\"restirShadowsEnabled\":").append(restirShadowsEnabled).append(",");
            sb.append("\"metalFxUpscalingEnabled\":").append(metalFxUpscalingEnabled).append(",");
            sb.append("\"metalFxQuality\":").append(metalFxQuality).append(",");
            sb.append(String.format(java.util.Locale.ROOT, "\"caveLighting\":%.2f", caveLighting));
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
                if ((val = getJsonValue(content, "vxaoEnabled")) != null) {
                    try { vxaoEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "vxaoStrength")) != null) {
                    try { vxaoStrength = Math.clamp(Float.parseFloat(val), 0.5f, 2.5f); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "vxaoInReflections")) != null) {
                    try { vxaoInReflections = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "maxPointLights")) != null) {
                    try { maxPointLights = Math.clamp(Integer.parseInt(val), 0, 1024); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "reflectionPointLightShadows")) != null) {
                    try { reflectionPointLightShadows = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "reflectionDirectionalShadows")) != null) {
                    try { reflectionDirectionalShadows = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
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
                if ((val = getJsonValue(content, "upscalingRatio")) != null) {
                    try { upscalingRatio = Math.clamp(Float.parseFloat(val), 0.1f, 1.5f); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "playerShadowEnabled")) != null) {
                    try { playerShadowEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "playerReflectionEnabled")) != null) {
                    try { playerReflectionEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "sdaaEnabled")) != null) {
                    try { sdaaEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "restirShadowsEnabled")) != null) {
                    try { restirShadowsEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "metalFxUpscalingEnabled")) != null) {
                    try { metalFxUpscalingEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "metalFxQuality")) != null) {
                    try { metalFxQuality = Integer.parseInt(val); } catch (Throwable ignored) {}
                }

                if ((val = getJsonValue(content, "caveLighting")) != null) {
                    try { caveLighting = Float.parseFloat(val); } catch (Throwable ignored) {}
                }
                if ((val = getJsonValue(content, "motionBlurEnabled")) != null) {
                    try { motionBlurEnabled = Boolean.parseBoolean(val); } catch (Throwable ignored) {}
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
