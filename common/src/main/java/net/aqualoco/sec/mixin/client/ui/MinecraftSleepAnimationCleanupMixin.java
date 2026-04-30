package net.aqualoco.sec.mixin.client.ui;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
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
        ClientLevel world = client.level;
        if (world == null) {
            SeamlessSleepClientState.SLEEP_ANIMATION.resetForWorldExit("world_null");
            return;
        }

        if (!world.dimension().equals(Level.OVERWORLD)) {
            SeamlessSleepClientState.SLEEP_ANIMATION.resetForWorldExit("non_overworld");
            return;
        }

        SeamlessSleepClientState.SLEEP_ANIMATION.resetIfWorldMismatch(world, "world_changed");
    }
}
