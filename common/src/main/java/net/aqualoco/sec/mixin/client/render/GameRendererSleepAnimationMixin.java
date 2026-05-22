package net.aqualoco.sec.mixin.client.render;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.client.sound.SleepSoundManager;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.sleep.SleepDimensionSupport;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Advances client sleep timing once per rendered frame.
@Mixin(GameRenderer.class)
public abstract class GameRendererSleepAnimationMixin {

    @Unique
    private static boolean seamlesssleep$clientInit;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void seamlesssleep$renderSleepAnimation(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!seamlesssleep$clientInit) {
            seamlesssleep$clientInit = true;
            SleepAnimationNetworking.initClient();
        }

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) {
            SeamlessSleepClientState.SLEEP_ANIMATION.resetForWorldExit("render_world_null");
            SleepSoundManager.reset("render_world_null");
            return;
        }

        if (!SleepDimensionSupport.supportsClientSleepAnimation(world)) {
            SeamlessSleepClientState.SLEEP_ANIMATION.resetForWorldExit("render_unsupported_dimension");
            SleepSoundManager.reset("render_unsupported_dimension");
            return;
        }

        SeamlessSleepClientState.SLEEP_ANIMATION.resetIfWorldMismatch(world, "render_world_changed");
        if (SeamlessSleepClientState.SLEEP_ANIMATION.isActive()) {
            SeamlessSleepClientState.SLEEP_ANIMATION.tick(world, deltaTracker);
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$hideVanillaHandsWhileBedBound(float tickDelta, boolean renderBlockOutline, Matrix4f projectionMatrix, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ClientBedWorkflow.shouldHideVanillaHands(player)) {
            ci.cancel();
        }
    }
}
