package com.prisma.voxel;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.Arrays;

@Environment(EnvType.CLIENT)
public final class BlockColorPalette {
    private static final int[] BASE_COLORS = new int[32768];
    private static Field originalImageField;
    private static boolean fieldLookupAttempted = false;

    static {
        Arrays.fill(BASE_COLORS, -1);
    }

    private BlockColorPalette() {}

    public static void clearCache() {
        Arrays.fill(BASE_COLORS, -1);
    }

    public static int getColor(final BlockState state, final BlockAndTintGetter level, final BlockPos pos) {
        int id = Block.getId(state);
        int baseRgb = -1;
        if (id >= 0 && id < BASE_COLORS.length) {
            baseRgb = BASE_COLORS[id];
        }

        if (baseRgb == -1) {
            baseRgb = extractBaseColor(state, level, pos);
            if (id >= 0 && id < BASE_COLORS.length) {
                BASE_COLORS[id] = baseRgb;
            }
        }

        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
            return 0x8C5624; 
        }
        if (state.getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock) {
            return 0x1A2E2E; 
        }

        if (state.is(net.minecraft.world.level.block.Blocks.GLASS)
                || (state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock && !state.is(net.minecraft.world.level.block.Blocks.IRON_BARS) && !(state.getBlock() instanceof net.minecraft.world.level.block.BeaconBeamBlock))) {
            return 0xFFFFFF; 
        }

        if (state.getBlock() instanceof net.minecraft.world.level.block.BeaconBeamBlock beamBlock) {
            net.minecraft.world.item.DyeColor dye = beamBlock.getColor();
            if (dye != null) {
                return dye.getTextureDiffuseColor() & 0xFFFFFF;
            }
        }

        Minecraft mc = Minecraft.getInstance();
        BlockColors blockColors = mc != null ? mc.getBlockColors() : null;
        if (blockColors != null) {
            try {
                BlockTintSource tintSource = blockColors.getTintSource(state, 0);
                if (tintSource != null) {
                    int tint = tintSource.colorInWorld(state, level, pos);
                    if (tint != 0 && tint != -1) {
                        if (state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                                || BlockBitmaskPalette.isVegetation(state)
                                || state.getBlock() instanceof net.minecraft.world.level.block.VineBlock
                                || state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) {
                            return tint;
                        }
                        int tr = (tint >> 16) & 0xFF;
                        int tg = (tint >> 8) & 0xFF;
                        int tb = tint & 0xFF;
                        int br = (baseRgb >> 16) & 0xFF;
                        int bg = (baseRgb >> 8) & 0xFF;
                        int bb = baseRgb & 0xFF;
                        int r = (br * tr) / 255;
                        int g = (bg * tg) / 255;
                        int b = (bb * tb) / 255;
                        return (r << 16) | (g << 8) | b;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                || BlockBitmaskPalette.isVegetation(state)) {
            return 0x79C05A;
        }
        if (state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) {
            return 0x59AE30;
        }

        return baseRgb;
    }

    private static int extractBaseColor(final BlockState state, final BlockAndTintGetter level, final BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            ModelManager modelManager = mc.getModelManager();
            if (modelManager != null) {
                BlockStateModelSet modelSet = modelManager.getBlockStateModelSet();
                if (modelSet != null) {
                    try {
                        Material.Baked mat = modelSet.getParticleMaterial(state);
                        if (mat != null) {
                            TextureAtlasSprite sprite = mat.sprite();
                            if (sprite != null) {
                                int spriteRgb = extractSpriteAverageColor(sprite);
                                if (spriteRgb != -1) {
                                    return spriteRgb;
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        try {
            if (level instanceof net.minecraft.world.level.LevelReader reader) {
                return state.getMapColor(reader, pos).col;
            }
            return 0x888888;
        } catch (Throwable ignored) {
            return 0x888888;
        }
    }

    private static int extractSpriteAverageColor(final TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        if (contents == null) return -1;

        NativeImage image = getOriginalImage(contents);
        if (image == null) return -1;

        int w = image.getWidth();
        int h = image.getHeight();
        if (w <= 0 || h <= 0) return -1;

        long sumR = 0, sumG = 0, sumB = 0;
        int count = 0;

        int stepX = Math.max(1, w / 16);
        int stepY = Math.max(1, h / 16);

        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int argb = image.getPixel(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a > 15) {
                    sumR += (argb >> 16) & 0xFF;
                    sumG += (argb >> 8) & 0xFF;
                    sumB += argb & 0xFF;
                    count++;
                }
            }
        }

        if (count == 0) return -1;
        int r = (int) (sumR / count);
        int g = (int) (sumG / count);
        int b = (int) (sumB / count);
        return (r << 16) | (g << 8) | b;
    }

    private static NativeImage getOriginalImage(final SpriteContents contents) {
        if (!fieldLookupAttempted) {
            fieldLookupAttempted = true;
            for (Field f : SpriteContents.class.getDeclaredFields()) {
                if (f.getType() == NativeImage.class) {
                    f.setAccessible(true);
                    originalImageField = f;
                    break;
                }
            }
        }

        if (originalImageField != null) {
            try {
                NativeImage img = (NativeImage) originalImageField.get(contents);
                if (img != null && !img.isClosed()) {
                    return img;
                }
            } catch (Throwable ignored) {
            }
        }

        for (Field f : SpriteContents.class.getDeclaredFields()) {
            if (f.getType() == NativeImage[].class) {
                f.setAccessible(true);
                try {
                    NativeImage[] arr = (NativeImage[]) f.get(contents);
                    if (arr != null && arr.length > 0 && arr[0] != null && !arr[0].isClosed()) {
                        return arr[0];
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return null;
    }
}
