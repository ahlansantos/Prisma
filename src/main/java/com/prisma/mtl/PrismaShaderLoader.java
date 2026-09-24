package com.prisma.mtl;

import com.prisma.config.PrismaConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PrismaShaderLoader {
    
    public static String readShaderSource(String name) {
        String packName = PrismaConfig.INSTANCE.shaderPack;
        
        // 1. Try to load from shaderpacks folder
        if (packName != null && !packName.equals("VXR Default") && !packName.isEmpty()) {
            Path packPath = FabricLoader.getInstance().getGameDir().resolve("shaderpacks").resolve(packName).resolve("shaders").resolve(name);
            if (Files.exists(packPath)) {
                try {
                    return Files.readString(packPath, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    System.err.println("[Prisma] Failed to read shader from pack: " + packPath);
                }
            }
        }
        
        // 2. Fallback to built-in resources
        try (InputStream is = PrismaShaderLoader.class.getResourceAsStream("/assets/prisma/shaders/" + name)) {
            if (is == null) throw new RuntimeException("Built-in shader not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read built-in shader: " + name, e);
        }
    }
}
