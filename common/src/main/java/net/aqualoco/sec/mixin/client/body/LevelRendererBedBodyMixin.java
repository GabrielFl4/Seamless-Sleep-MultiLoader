package net.aqualoco.sec.mixin.client.body;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Injects the local player into visible entities so a first-person bed body can be rendered when needed.
@Mixin(LevelRenderer.class)
public abstract class LevelRendererBedBodyMixin {

    @Shadow
    protected abstract EntityRenderState extractEntity(Entity entity, float tickDelta);

    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void seamlesssleep$addCameraPlayerBody(Camera camera,
                                                   Frustum frustum,
                                                   DeltaTracker deltaTracker,
                                                   LevelRenderState levelRenderState,
                                                   CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || camera.isDetached() || !ClientBedWorkflow.shouldRenderFirstPersonBody(player)) {
            return;
        }

        Entity cameraEntity = camera.entity();
        if (cameraEntity != player) {
            return;
        }

        levelRenderState.entityRenderStates.add(this.extractEntity(cameraEntity, deltaTracker.getGameTimeDeltaPartialTick(true)));
    }
}
