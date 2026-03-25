package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
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
            return;
        }

        if (!world.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        if (SeamlessSleepClientState.SLEEP_ANIMATION.isActive()) {
            SeamlessSleepClientState.SLEEP_ANIMATION.tick(world, deltaTracker);
        }
    }
}
