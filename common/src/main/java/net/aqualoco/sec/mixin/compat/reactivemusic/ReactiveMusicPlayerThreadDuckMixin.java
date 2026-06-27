package net.aqualoco.sec.mixin.compat.reactivemusic;

import net.aqualoco.sec.client.sound.MadeInHeavenMusicSuppression;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "circuitlord.reactivemusic.PlayerThread", remap = false)
public abstract class ReactiveMusicPlayerThreadDuckMixin {
    @Shadow(remap = false)
    public static volatile float musicDiscDuckPercentage;

    @Inject(
            method = "processRealGain",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void seamlesssleep$applyMadeInHeavenMusicDuck(CallbackInfo ci) {
        musicDiscDuckPercentage = Math.min(musicDiscDuckPercentage, MadeInHeavenMusicSuppression.musicDuckFactor());
    }
}
