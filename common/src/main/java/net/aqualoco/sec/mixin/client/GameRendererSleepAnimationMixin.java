package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Advances client sleep timing once per rendered frame.
@Mixin(GameRenderer.class)
public abstract class GameRendererSleepAnimationMixin {

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void seamlesssleep$renderSleepAnimation(DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) {
            return;
        }

        if (!world.dimension().equals(Level.OVERWORLD)) {
            if (SeamlessSleepClientState.SLEEP_ANIMATION.isActive()) {
                SeamlessSleepClientState.SLEEP_ANIMATION.reset();
            }
            return;
        }

        ClientSleepAnimationState state = SeamlessSleepClientState.SLEEP_ANIMATION;
        if (!state.isActive()) {
            return;
        }

        if (!state.isActiveForWorld(world.dimension().identifier())) {
            state.reset();
            return;
        }

        state.tick(deltaTracker);
    }
}
