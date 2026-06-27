package net.aqualoco.sec.mixin.client.sound;

import net.aqualoco.sec.client.sound.MadeInHeavenMusicSuppression;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMadeInHeavenMusicDuckMixin {
    @Unique
    private float seamlesssleep$lastMusicDuckFactor = 1.0F;

    @Inject(
            method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
            at = @At("RETURN"),
            cancellable = true
    )
    private void seamlesssleep$duckMusicCategory(float volume,
                                                 SoundSource source,
                                                 CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(MadeInHeavenMusicSuppression.duckSoundEngineVolume(cir.getReturnValueF(), source));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void seamlesssleep$refreshMusicDuck(boolean paused, CallbackInfo ci) {
        float factor = MadeInHeavenMusicSuppression.musicDuckFactor();
        if (Math.abs(factor - this.seamlesssleep$lastMusicDuckFactor) <= 1.0E-4F) {
            return;
        }

        this.seamlesssleep$lastMusicDuckFactor = factor;
        ((SoundEngine) (Object) this).updateCategoryVolume(SoundSource.MUSIC);
    }
}
