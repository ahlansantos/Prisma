package com.prisma.mtl;

import com.prisma.config.PrismaConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PrismaShaderLoader {
    
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$", Pattern.MULTILINE);
    
    public static String readShaderSource(String name) {
        return loadAndPreprocess(name, new HashSet<>());
    }

    private static String loadAndPreprocess(String name, Set<String> includedFiles) {
        if (!includedFiles.add(name)) {
            return "// [Prisma] Skipped duplicate include: " + name + "\n";
        }

        String source = rawReadShaderSource(name);
        if (source == null) return "";
        
        StringBuffer sb = new StringBuffer();
        Matcher matcher = INCLUDE_PATTERN.matcher(source);
        
        while (matcher.find()) {
            String includeName = matcher.group(1);
            String includedSource = loadAndPreprocess(includeName, includedFiles);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(
                "\n// --- Begin include: " + includeName + " ---\n" +
                includedSource +
                "\n// --- End include: " + includeName + " ---\n"
            ));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    private static String rawReadShaderSource(String name) {
        String packName = PrismaConfig.INSTANCE.shaderPack;
        
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
        
        try (InputStream is = PrismaShaderLoader.class.getResourceAsStream("/assets/prisma/shaders/" + name)) {
            if (is == null) throw new RuntimeException("Built-in shader not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read built-in shader: " + name, e);
        }
    }
}
