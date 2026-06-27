package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.aqualoco.sec.client.VivecraftClientCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class NeoForgeVivecraftFirstPersonSelfRenderMixin {

    @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
    private void seamlesssleep$removeVivecraftFirstPersonSleepingSelf(Camera camera,
                                                                      Frustum frustum,
                                                                      DeltaTracker deltaTracker,
                                                                      LevelRenderState levelRenderState,
                                                                      CallbackInfo ci) {
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

        int playerId = player.getId();
        levelRenderState.entityRenderStates.removeIf(renderState ->
                renderState instanceof AvatarRenderState avatarRenderState && avatarRenderState.id == playerId);
    }
}
