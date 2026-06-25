package net.aqualoco.sec.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class SleepFadingSoundInstance extends AbstractTickableSoundInstance {
    private static final float STOP_VOLUME_EPSILON = 0.0025F;

    private float targetVolume;
    private int fadeTicks;
    private boolean fadingOut;
    private int fadeOutTicksRemaining;
    private int fadeOutTotalTicks;
    private float fadeOutStartVolume;

    public SleepFadingSoundInstance(Identifier soundId, SoundSource source, float targetVolume, int fadeTicks) {
        super(SoundEvent.createVariableRangeEvent(soundId), source, SoundInstance.createUnseededRandom());
        this.looping = false;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.pitch = 1.0F;
        this.targetVolume = clamp01(targetVolume);
        this.fadeTicks = Math.max(0, fadeTicks);
        this.volume = this.fadeTicks <= 0 ? this.targetVolume : 0.0F;
    }

    @Override
    public void tick() {
        if (this.fadingOut) {
            this.fadeOutTicksRemaining--;
            float t = this.fadeOutTotalTicks <= 0
                    ? 0.0F
                    : this.fadeOutTicksRemaining / (float) this.fadeOutTotalTicks;
            this.volume = this.fadeOutStartVolume * clamp01(t);
            if (this.fadeOutTicksRemaining <= 0 || this.volume <= STOP_VOLUME_EPSILON) {
                this.volume = 0.0F;
                this.stop();
            }
            return;
        }

        float step = this.fadeTicks <= 0
                ? 1.0F
                : this.targetVolume / Math.max(1, this.fadeTicks);
        if (this.volume < this.targetVolume) {
            this.volume = Math.min(this.targetVolume, this.volume + step);
        } else if (this.volume > this.targetVolume) {
            this.volume = Math.max(this.targetVolume, this.volume - step);
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    public void setTargetVolume(float targetVolume) {
        this.targetVolume = clamp01(targetVolume);
        if (this.targetVolume > STOP_VOLUME_EPSILON) {
            this.fadingOut = false;
        }
    }

    public void stopWithFade(int fadeTicks) {
        this.targetVolume = 0.0F;
        if (fadeTicks <= 0 || this.volume <= STOP_VOLUME_EPSILON) {
            this.volume = 0.0F;
            this.stop();
            return;
        }

        this.fadingOut = true;
        this.fadeOutTicksRemaining = fadeTicks;
        this.fadeOutTotalTicks = fadeTicks;
        this.fadeOutStartVolume = this.volume;
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0F;
        }
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }
}
