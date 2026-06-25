package net.aqualoco.sec.mixin.compat;

import net.aqualoco.sec.compat.BetterDaysCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "betterdays.config.ConfigHandler$Common", remap = false)
public abstract class BetterDaysSleepFeatureMixin {

    @Inject(method = "enableSleepFeature", at = @At("HEAD"), cancellable = true, remap = false)
    private static void seamlesssleep$disableBetterDaysSleepFeature(CallbackInfoReturnable<Boolean> cir) {
        if (BetterDaysCompat.shouldDisableBetterDaysSleepFeature()) {
            cir.setReturnValue(false);
        }
    }
}
