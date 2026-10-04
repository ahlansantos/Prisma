package com.prisma.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.prisma.render.PrismaDeferredRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.OptionalDouble;

@Mixin(LevelRenderer.class)
public class PrismaLevelRendererMixin {
    @WrapOperation(
            method = "lambda$addMainPass$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;executeClassicTransparency(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"
            )
    )
    private void prisma$splitTranslucentPass(
            LevelRenderer instance,
            ChunkSectionsToRender chunkSectionsToRender,
            FeatureRenderDispatcher.PreparedFrame featureFrame,
            RenderPass renderPass,
            Operation<Void> original
    ) {
        Minecraft mc = Minecraft.getInstance();
        com.prisma.render.PrismaJoinWarning.onFrame(mc);
        RenderTarget mainTarget = mc.gameRenderer != null ? mc.gameRenderer.mainRenderTarget() : null;

        if (mainTarget != null) {
            // 1. Fecha o renderPass do Solid para liberar o CommandEncoder
            renderPass.close();

            // 2. Executa a iluminação deferred no mundo sólido
            PrismaDeferredRenderer.performDeferredLighting(mc, mainTarget);

            // 3. Abre novo RenderPass para a transparência desenhar por cima do mundo iluminado
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass translucentPass = encoder.createRenderPass(
                    () -> "Translucent",
                    mainTarget.getColorTextureView(),
                    Optional.empty(),
                    mainTarget.getDepthTextureView(),
                    OptionalDouble.empty()
            )) {
                RenderSystem.bindDefaultUniforms(translucentPass);
                original.call(instance, chunkSectionsToRender, featureFrame, translucentPass);
            }

            // 4. Executa o pass de água com waves, Fresnel e reflexões na superfície da água recém-desenhada!
            PrismaDeferredRenderer.performDeferredLighting(mc, mainTarget, true);
            

        } else {
            original.call(instance, chunkSectionsToRender, featureFrame, renderPass);
        }
    }
}
