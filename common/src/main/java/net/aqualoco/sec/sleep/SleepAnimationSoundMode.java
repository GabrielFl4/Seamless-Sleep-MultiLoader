package net.aqualoco.sec.sleep;

public enum SleepAnimationSoundMode {
    MUTED,
    DEFAULT,
    EPIC,
    NONE,
    SFX,
    MUSIC;

    public static SleepAnimationSoundMode canonical(SleepAnimationSoundMode mode) {
        if (mode == null) {
            return MUTED;
        }
        return switch (mode) {
            case NONE -> MUTED;
            case SFX -> DEFAULT;
            case MUSIC -> EPIC;
            default -> mode;
        };
    }

    public boolean isMuted() {
        return canonical(this) == MUTED;
    }
}
