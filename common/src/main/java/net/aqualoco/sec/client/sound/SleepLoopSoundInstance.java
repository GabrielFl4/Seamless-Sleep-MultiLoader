package net.aqualoco.sec.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class SleepLoopSoundInstance extends AbstractTickableSoundInstance {
    private static final float STOP_VOLUME_EPSILON = 0.0025F;

    private float targetVolume;
    private int fadeTicks;
    private boolean stopping;

    public SleepLoopSoundInstance(ResourceLocation soundId, SoundSource source, int fadeTicks) {
        super(SoundEvent.createVariableRangeEvent(soundId), source, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.0F;
        this.pitch = 1.0F;
        this.fadeTicks = Math.max(1, fadeTicks);
    }

    @Override
    public void tick() {
        float step = 1.0F / Math.max(1, this.fadeTicks);
        if (this.volume < this.targetVolume) {
            this.volume = Math.min(this.targetVolume, this.volume + step);
        } else if (this.volume > this.targetVolume) {
            this.volume = Math.max(this.targetVolume, this.volume - step);
        }

        if (this.stopping && this.volume <= STOP_VOLUME_EPSILON) {
            this.volume = 0.0F;
            this.stop();
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    public void setTargetVolume(float targetVolume) {
        this.targetVolume = clamp01(targetVolume);
        if (this.targetVolume > STOP_VOLUME_EPSILON) {
            this.stopping = false;
        }
    }

    public void setPitch(float pitch) {
        this.pitch = clamp(pitch, 0.5F, 2.0F);
    }

    public void setFadeTicks(int fadeTicks) {
        this.fadeTicks = Math.max(1, fadeTicks);
    }

    public float getCurrentVolume() {
        return this.volume;
    }

    public void stopWithFade(int fadeTicks) {
        this.fadeTicks = Math.max(1, fadeTicks);
        this.targetVolume = 0.0F;
        this.stopping = true;
        if (this.volume <= STOP_VOLUME_EPSILON) {
            this.volume = 0.0F;
            this.stop();
        }
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return min;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
