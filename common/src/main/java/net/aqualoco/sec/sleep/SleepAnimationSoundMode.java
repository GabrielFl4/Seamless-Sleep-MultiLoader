package net.aqualoco.sec.sleep;

public enum SleepAnimationSoundMode {
    MUTED,
    DEFAULT,
    EPIC;

    public static SleepAnimationSoundMode canonical(SleepAnimationSoundMode mode) {
        return mode == null ? MUTED : mode;
    }

    public boolean isMuted() {
        return canonical(this) == MUTED;
    }
}
