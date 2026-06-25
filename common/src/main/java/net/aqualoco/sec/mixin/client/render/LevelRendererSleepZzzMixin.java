package net.aqualoco.sec.mixin.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.aqualoco.sec.client.VivecraftSleepWristPanel;
import net.aqualoco.sec.client.sleepvisual.SleepZzzVisualSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Adds the lightweight sleep Z quads to the same world render queue used by entities.
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSleepZzzMixin {

    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void seamlesssleep$submitSleepZzz(PoseStack poseStack,
                                               LevelRenderState levelRenderState,
                                               SubmitNodeCollector submitNodeCollector,
                                               CallbackInfo ci) {
        SleepZzzVisualSystem.tickReplay(Minecraft.getInstance());
        SleepZzzVisualSystem.submitRender(
                poseStack,
                levelRenderState.cameraRenderState,
                submitNodeCollector
        );
        VivecraftSleepWristPanel.submitRender(
                poseStack,
                levelRenderState.cameraRenderState,
                submitNodeCollector
        );
    }
}
