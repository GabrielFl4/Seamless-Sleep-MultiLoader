package net.aqualoco.sec.mixin.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.aqualoco.sec.client.VivecraftSleepWristPanel;
import net.aqualoco.sec.client.sleepvisual.SleepZzzVisualSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Adds the lightweight sleep Z quads to the same world render queue used by entities.
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSleepZzzMixin {

    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void seamlesssleep$invalidateWristIndicatorResources(ResourceManager resourceManager, CallbackInfo ci) {
        net.aqualoco.sec.client.sleepindicator.VivecraftSleepWristIndicatorRenderer.onResourceReload();
    }

    @Inject(
            method = "renderEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/Camera;Lnet/minecraft/client/DeltaTracker;Ljava/util/List;)V",
            at = @At("TAIL")
    )
    private void seamlesssleep$submitSleepZzz(PoseStack poseStack,
                                               MultiBufferSource.BufferSource bufferSource,
                                               Camera camera,
                                               DeltaTracker deltaTracker,
                                               List<Entity> visibleEntities,
                                               CallbackInfo ci) {
        SleepZzzVisualSystem.tickReplay(Minecraft.getInstance());
        SleepZzzVisualSystem.submitRender(
                poseStack,
                camera,
                bufferSource
        );
        VivecraftSleepWristPanel.submitRender(
                poseStack,
                camera,
                bufferSource
        );
    }
}
