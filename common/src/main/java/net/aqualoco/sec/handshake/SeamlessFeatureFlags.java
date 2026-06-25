package net.aqualoco.sec.handshake;

public final class SeamlessFeatureFlags {
    public static final int FEATURE_SLEEP_ANIMATION = 1;
    public static final int FEATURE_REMOTE_CONFIG = 1 << 1;
    public static final int FEATURE_BED_HUD = 1 << 2;
    public static final int FEATURE_BED_LOOK_SYNC = 1 << 3;
    public static final int FEATURE_SOUND_PROFILES = 1 << 4;
    public static final int REQUIRED_CLIENT_FEATURES = FEATURE_SLEEP_ANIMATION
            | FEATURE_REMOTE_CONFIG
            | FEATURE_BED_HUD
            | FEATURE_BED_LOOK_SYNC;

    public static final int CURRENT = REQUIRED_CLIENT_FEATURES | FEATURE_SOUND_PROFILES;

    private SeamlessFeatureFlags() {
    }

    public static boolean hasRequiredFeatures(int flags) {
        return (flags & REQUIRED_CLIENT_FEATURES) == REQUIRED_CLIENT_FEATURES;
    }
}
