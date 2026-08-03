package net.aqualoco.sec.mixin.client.ui;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.client.sound.SleepSoundManager;
import net.aqualoco.sec.handshake.ClientHandshakeState;
import net.aqualoco.sec.sleep.SleepDimensionSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Clears live sleep animation state when the client leaves or changes worlds.
@Mixin(Minecraft.class)
public abstract class MinecraftSleepAnimationCleanupMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void seamlesssleep$cleanupSleepAnimationWorldState(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        ClientHandshakeState.tick(client);
        ClientLevel world = client.level;
        if (world == null) {
            SeamlessSleepClientState.SLEEP_ANIMATION.resetForWorldExit("world_null");
            SleepSoundManager.reset("world_null");
            return;
        }

        if (!SleepDimensionSupport.supportsClientSleepAnimation(world)) {
            SeamlessSleepClientState.SLEEP_ANIMATION.resetForWorldExit("unsupported_dimension");
            SleepSoundManager.reset("unsupported_dimension");
            return;
        }

        SeamlessSleepClientState.SLEEP_ANIMATION.resetIfWorldMismatch(world, "world_changed");
        SleepSoundManager.tick(client);
    }
}
