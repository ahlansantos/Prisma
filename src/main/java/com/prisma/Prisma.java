package com.prisma;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Prisma implements ModInitializer {
    public static final String MOD_ID = "prisma";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static boolean hasMSLCompileError = false;
    public static String lastMSLError = null;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Prisma - Native macOS Voxel Shader Engine");
        com.prisma.config.PrismaConfig.INSTANCE.load();
    }
}