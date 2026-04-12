package net.aqualoco.sec.config;

// Client-side visual settings with validation bounds for safe values.
public final class SeamlessSleepClientConfig {
    private static final double DEFAULT_CHAT_TEXT_BASE = 0.5D;
    private static final double DEFAULT_CHAT_BG_BASE = 0.4D;

    private static final double DEFAULT_OVERLAY_DARKNESS = 0.35D;
    private static final double DEFAULT_CHAT_GROUP_MULTIPLIER = 1.0D;
    private static final int DEFAULT_CHAT_MAX_LINES = 4;
    private static final double DEFAULT_TILT_DEGREES = 10.0D;
    private static final double MIN_TILT_DEGREES = 0.0D;
    private static final double MIN_NON_ZERO_TILT_DEGREES = 0.1D;
    private static final double MAX_TILT_DEGREES = 90.0D;
    private static final boolean DEFAULT_REPLAY_COMPATIBILITY_ENABLED = true;
    private static final boolean DEFAULT_DEBUG_LOGS_ENABLED = false;

    public boolean sleepOverlayEnabled = true;
    public double sleepOverlayDarknessMultiplier = DEFAULT_OVERLAY_DARKNESS;

    public double sleepChatTextOpacityMultiplier = DEFAULT_CHAT_TEXT_BASE;
    public double sleepChatBackgroundOpacityMultiplier = DEFAULT_CHAT_BG_BASE;
    public double sleepChatOpacityMultiplier = DEFAULT_CHAT_GROUP_MULTIPLIER;
    public int sleepChatMaxLines = DEFAULT_CHAT_MAX_LINES;

    public double sleepCameraTiltDegrees = DEFAULT_TILT_DEGREES;
    public boolean replayCompatibilityEnabled = DEFAULT_REPLAY_COMPATIBILITY_ENABLED;
    public boolean debugLogsEnabled = DEFAULT_DEBUG_LOGS_ENABLED;

    public void clamp() {
        sleepChatTextOpacityMultiplier = clamp01(sleepChatTextOpacityMultiplier, DEFAULT_CHAT_TEXT_BASE);
        sleepChatBackgroundOpacityMultiplier = clamp01(sleepChatBackgroundOpacityMultiplier, DEFAULT_CHAT_BG_BASE);

        sleepOverlayDarknessMultiplier = clampRange(
                sleepOverlayDarknessMultiplier,
                0.0D,
                1.0D,
                DEFAULT_OVERLAY_DARKNESS
        );
        sleepChatOpacityMultiplier = clampRange(
                sleepChatOpacityMultiplier,
                0.1D,
                2.0D,
                DEFAULT_CHAT_GROUP_MULTIPLIER
        );
        sleepChatMaxLines = clampInt(sleepChatMaxLines, 0, 12, DEFAULT_CHAT_MAX_LINES);
        sleepCameraTiltDegrees = clampTiltDegrees(sleepCameraTiltDegrees, DEFAULT_TILT_DEGREES);
    }

    private static double clamp01(double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }

    private static double clampRange(double value, double min, double max, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static double clampTiltDegrees(double value, double fallback) {
        double clamped = clampRange(value, MIN_TILT_DEGREES, MAX_TILT_DEGREES, fallback);
        if (clamped == 0.0D) {
            return MIN_NON_ZERO_TILT_DEGREES;
        }
        return clamped;
    }

    private static int clampInt(int value, int min, int max, int fallback) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
