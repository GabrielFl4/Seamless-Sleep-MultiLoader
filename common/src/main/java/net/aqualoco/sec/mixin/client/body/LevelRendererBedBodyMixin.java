package net.aqualoco.sec.mixin.client.body;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// Injects the local player into visible entities so a first-person bed body can be rendered when needed.
@Mixin(LevelRenderer.class)
public abstract class LevelRendererBedBodyMixin {

    @Inject(
            method = "collectVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;)Z",
            at = @At("TAIL")
    )
    private void seamlesssleep$addCameraPlayerBody(Camera camera,
                                                   Frustum frustum,
                                                   List<Entity> visibleEntities,
                                                   CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || camera.isDetached() || !ClientBedWorkflow.shouldRenderFirstPersonBody(player)) {
            return;
        }

        Entity cameraEntity = camera.getEntity();
        if (cameraEntity != player) {
            return;
        }

        if (!visibleEntities.contains(player)) {
            visibleEntities.add(player);
        }
    }
}
