package net.aqualoco.sec.config;

// Server-authoritative sleep settings that are synced to clients.
public final class SeamlessSleepServerConfig {
    public static final int DEFAULT_FALL_ASLEEP_DELAY_TICKS = 100;
    public static final int MIN_FALL_ASLEEP_DELAY_TICKS = 0;
    public static final int MAX_FALL_ASLEEP_DELAY_TICKS = 200;
    public static final int MAX_OVERLAY_CUSTOM_TEXT_LENGTH = 128;
    public static final String DEFAULT_OVERLAY_CUSTOM_TEXT = "Sleeping...";

    public int sleepWeatherClearChancePercent = 100;
    public double sleepAnimationDurationMultiplier = 1.0D;
    public int fallAsleepDelayTicks = DEFAULT_FALL_ASLEEP_DELAY_TICKS;
    public boolean overrideOverlayText = false;
    public String overlayCustomText = DEFAULT_OVERLAY_CUSTOM_TEXT;
    public SleepEligibilityMode sleepEligibility = SleepEligibilityMode.VANILLA;
    public int madeInHeavenChancePercent = 0;
    public final WorldSleepAccelerationConfig worldSleepAcceleration = new WorldSleepAccelerationConfig();

    public void clamp() {
        sleepWeatherClearChancePercent = clampInt(sleepWeatherClearChancePercent, 0, 100);
        sleepAnimationDurationMultiplier = clampRange(
                sleepAnimationDurationMultiplier,
                0.25D,
                8.0D,
                1.0D
        );
        fallAsleepDelayTicks = clampInt(
                fallAsleepDelayTicks,
                MIN_FALL_ASLEEP_DELAY_TICKS,
                MAX_FALL_ASLEEP_DELAY_TICKS
        );
        overlayCustomText = sanitizeOverlayText(overlayCustomText);
        sleepEligibility = sleepEligibility == null ? SleepEligibilityMode.VANILLA : sleepEligibility;
        madeInHeavenChancePercent = clampInt(madeInHeavenChancePercent, 0, 100);
        worldSleepAcceleration.clamp();
    }

    public static String sanitizeOverlayText(String value) {
        if (value == null) {
            return DEFAULT_OVERLAY_CUSTOM_TEXT;
        }

        String sanitized = value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replace("\u00A7", "");
        if (sanitized.isBlank()) {
            return DEFAULT_OVERLAY_CUSTOM_TEXT;
        }
        if (sanitized.length() > MAX_OVERLAY_CUSTOM_TEXT_LENGTH) {
            return sanitized.substring(0, MAX_OVERLAY_CUSTOM_TEXT_LENGTH);
        }
        return sanitized;
    }

    public static int clampInt(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    static double clampRange(double value, double min, double max, double fallback) {
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
}
