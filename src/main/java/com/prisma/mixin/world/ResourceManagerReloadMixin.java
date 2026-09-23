package com.prisma.mixin.world;

import com.prisma.voxel.VoxelGridManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Environment(EnvType.CLIENT)
@Mixin(ReloadableResourceManager.class)
public abstract class ResourceManagerReloadMixin {
    @Inject(method = "createReload", at = @At("RETURN"))
    private void prisma$onCreateReload(
            final Executor prepareExecutor,
            final Executor applyExecutor,
            final CompletableFuture<net.minecraft.util.Unit> alreadyLoaded,
            final List<net.minecraft.server.packs.PackResources> packs,
            final CallbackInfoReturnable<ReloadInstance> cir) {
        ReloadInstance instance = cir.getReturnValue();
        if (instance != null) {
            instance.done().thenRun(() -> {
                VoxelGridManager.INSTANCE.invalidateBlockTextureCache();
                VoxelGridManager.INSTANCE.markDirty();
            });
        }
    }
}
