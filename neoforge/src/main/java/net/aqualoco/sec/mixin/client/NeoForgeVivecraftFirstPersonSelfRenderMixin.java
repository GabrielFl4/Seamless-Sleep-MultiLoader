package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.aqualoco.sec.client.VivecraftClientCompat;
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

@Mixin(LevelRenderer.class)
public abstract class NeoForgeVivecraftFirstPersonSelfRenderMixin {

    @Inject(
            method = "collectVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;)Z",
            at = @At("TAIL")
    )
    private void seamlesssleep$removeVivecraftFirstPersonSleepingSelf(Camera camera,
                                                                      Frustum frustum,
                                                                      List<Entity> visibleEntities,
                                                                      CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null
                || camera.isDetached()
                || camera.getEntity() != player
                || !client.options.getCameraType().isFirstPerson()
                || !VivecraftClientCompat.shouldUseVrBedPolicy(player)
                || !ClientBedWorkflow.isManagedBedState(player)) {
            return;
        }

        visibleEntities.removeIf(entity -> entity == player);
    }
}
