package com.prisma.config;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ExternalPageBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class PrismaSodiumConfig implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(final ConfigBuilder builder) {
        ModOptionsBuilder modOptions = builder.registerModOptions("prisma");
        modOptions.setName("Prisma");
        modOptions.setVersion(net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("prisma").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0.2.4-rev1"));
        modOptions.setIcon(Identifier.fromNamespaceAndPath("prisma", "icon-mono.png"));

        ExternalPageBuilder shaderPage = builder.createExternalPage();
        shaderPage.setName(Component.literal("Shader Packs..."));
        shaderPage.setScreenConsumer(parent -> Minecraft.getInstance().setScreenAndShow(new PrismaShaderScreen(parent)));

        modOptions.addPage(shaderPage);
    }
}
