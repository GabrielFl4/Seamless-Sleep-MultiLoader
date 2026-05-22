package net.aqualoco.sec.client.sound;

import net.aqualoco.sec.sleep.SleepAnimationSoundMode;

public enum SleepSoundProfile {
    MUTED,
    DEFAULT,
    EPIC;

    public static SleepSoundProfile from(SleepAnimationSoundMode mode) {
        return switch (SleepAnimationSoundMode.canonical(mode)) {
            case DEFAULT -> DEFAULT;
            case EPIC -> EPIC;
            default -> MUTED;
        };
    }
}
