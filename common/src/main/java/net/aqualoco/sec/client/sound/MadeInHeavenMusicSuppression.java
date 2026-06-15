package net.aqualoco.sec.client.sound;

import net.minecraft.sounds.SoundSource;

public final class MadeInHeavenMusicSuppression {
    private static final int MUSIC_FADE_OUT_TICKS = 30;
    private static final int RELEASE_HOLD_TICKS = 100;
    private static final int MUSIC_FADE_IN_TICKS = 200;
    private static final float FULL_VOLUME = 1.0F;
    private static final float SILENT_VOLUME = 0.0F;

    private static float musicDuckFactor = FULL_VOLUME;
    private static int releaseHoldTicks;
    private static boolean wasSuppressing;

    private MadeInHeavenMusicSuppression() {
    }

    static void update(boolean shouldSuppress) {
        if (shouldSuppress) {
            wasSuppressing = true;
            releaseHoldTicks = 0;
            musicDuckFactor = approach(musicDuckFactor, SILENT_VOLUME, 1.0F / MUSIC_FADE_OUT_TICKS);
            return;
        }

        if (wasSuppressing) {
            wasSuppressing = false;
            releaseHoldTicks = RELEASE_HOLD_TICKS;
        }

        if (releaseHoldTicks > 0) {
            releaseHoldTicks--;
            return;
        }

        musicDuckFactor = approach(musicDuckFactor, FULL_VOLUME, 1.0F / MUSIC_FADE_IN_TICKS);
    }

    public static float duckSoundEngineVolume(float volume, SoundSource source) {
        return source == SoundSource.MUSIC ? volume * musicDuckFactor : volume;
    }

    public static float musicDuckFactor() {
        return musicDuckFactor;
    }

    private static float approach(float value, float target, float step) {
        if (value < target) {
            return Math.min(target, value + step);
        }
        if (value > target) {
            return Math.max(target, value - step);
        }
        return value;
    }
}
