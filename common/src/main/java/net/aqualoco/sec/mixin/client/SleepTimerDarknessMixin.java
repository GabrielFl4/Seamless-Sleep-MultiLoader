package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Scales vanilla sleep timer on 1.20 to control darkness intensity.
@Mixin(Player.class)
public abstract class SleepTimerDarknessMixin {

    @Inject(method = "getSleepTimer", at = @At("RETURN"), cancellable = true)
    private void seamlesssleep$scaleSleepTimer(CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValue();
        SeamlessSleepClientConfig cfg = SeamlessSleepClientConfigManager.get();
        double factor = cfg.sleepOverlayDarknessMultiplier;
        cir.setReturnValue((int) (vanilla * factor));
    }
}
