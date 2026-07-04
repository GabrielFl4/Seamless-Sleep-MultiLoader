package net.aqualoco.sec.mixin.compat.subtleeffects;

import net.aqualoco.sec.client.sleepvisual.SleepZzzConfigBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Lets Seamless Sleep own player Z visuals while enabled without mutating Subtle Effects' config.
@Pseudo
@Mixin(targets = "einstein.subtle_effects.ticking.tickers.entity.sleeping.PlayerSleepingTicker", remap = false)
public abstract class SubtleEffectsPlayerSleepingTickerMixin {

    @Inject(
            method = "particleConfigEnabled",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void seamlesssleep$suppressDuplicatePlayerSleepingZs(CallbackInfoReturnable<Boolean> cir) {
        if (SleepZzzConfigBridge.isEnabled()) {
            cir.setReturnValue(false);
        }
    }
}
