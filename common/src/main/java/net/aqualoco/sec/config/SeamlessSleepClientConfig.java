package net.aqualoco.sec.config;

import net.aqualoco.sec.client.sleepindicator.SleepIndicatorAnchor;
import net.aqualoco.sec.client.sleepindicator.SleepIndicatorMode;
import net.aqualoco.sec.client.sleepindicator.SleepIndicatorVisibility;
import net.aqualoco.sec.client.sleepindicator.TimestampStyle;
import net.aqualoco.sec.client.sleepvisual.SleepZzzConfigBridge;

// Client-side visual settings with validation bounds for safe values.
public final class SeamlessSleepClientConfig {
    private static final double DEFAULT_CHAT_TEXT_BASE = 0.5D;
    private static final double DEFAULT_CHAT_BG_BASE = 0.4D;
    private static final double DEFAULT_CHAT_PRESET_POSITION = 0.5D;

    private static final double DEFAULT_OVERLAY_DARKNESS = 0.35D;
    private static final double DEFAULT_CHAT_GROUP_MULTIPLIER = DEFAULT_CHAT_PRESET_POSITION;
    private static final int DEFAULT_CHAT_MAX_LINES = 4;
    private static final double DEFAULT_TILT_DEGREES = 10.0D;
    private static final int DEFAULT_MOUSE_SMOOTHNESS_PERCENT = 100;
    private static final double DEFAULT_SLEEP_INDICATOR_SCALE = 1.0D;
    public static final int DEFAULT_TIMESTAMP_COLOR = 0x00FFFFFF;
    private static final double MIN_SLEEP_INDICATOR_SCALE = 0.25D;
    private static final double MAX_SLEEP_INDICATOR_SCALE = 4.0D;
    private static final int MIN_SLEEP_ZZZ_CHANCE = 0;
    private static final int MAX_SLEEP_ZZZ_CHANCE = 100;
    private static final boolean DEFAULT_LEAVE_BED_HINT_ENABLED = true;
    private static final boolean DEFAULT_SLEEP_CONTEXT_ENABLED = true;
    private static final double MIN_TILT_DEGREES = 0.0D;
    private static final double MIN_NON_ZERO_TILT_DEGREES = 0.1D;
    private static final double MAX_TILT_DEGREES = 90.0D;
    private static final boolean DEFAULT_REPLAY_COMPATIBILITY_ENABLED = true;
    private static final boolean DEFAULT_DEBUG_LOGS_ENABLED = false;

    public double sleepOverlayDarknessMultiplier = DEFAULT_OVERLAY_DARKNESS;
    public boolean leaveBedHintEnabled = DEFAULT_LEAVE_BED_HINT_ENABLED;
    public boolean sleepContextEnabled = DEFAULT_SLEEP_CONTEXT_ENABLED;
    public SleepIndicatorMode sleepIndicatorMode = SleepIndicatorMode.BIOME_CLOCK;
    public SleepIndicatorAnchor sleepIndicatorAnchor = SleepIndicatorAnchor.TOP_LEFT;
    public SleepIndicatorVisibility sleepIndicatorVisibility = SleepIndicatorVisibility.BED;
    public double sleepIndicatorScale = DEFAULT_SLEEP_INDICATOR_SCALE;
    public TimestampStyle timestampStyle = TimestampStyle.DAY_FIRST;
    public int timestampColor = DEFAULT_TIMESTAMP_COLOR;
    public int sleepZzzChance = SleepZzzConfigBridge.DEFAULT_CHANCE;
    public String sleepZzzStyle = SleepZzzConfigBridge.DEFAULT_STYLE.name();

    public double sleepChatTextOpacityMultiplier = DEFAULT_CHAT_TEXT_BASE;
    public double sleepChatBackgroundOpacityMultiplier = DEFAULT_CHAT_BG_BASE;
    public double sleepChatOpacityMultiplier = DEFAULT_CHAT_GROUP_MULTIPLIER;
    public int sleepChatMaxLines = DEFAULT_CHAT_MAX_LINES;

    public double sleepCameraTiltDegrees = DEFAULT_TILT_DEGREES;
    public int mouseSmoothnessPercent = DEFAULT_MOUSE_SMOOTHNESS_PERCENT;
    public boolean replayCompatibilityEnabled = DEFAULT_REPLAY_COMPATIBILITY_ENABLED;
    public boolean debugLogsEnabled = DEFAULT_DEBUG_LOGS_ENABLED;

    public void clamp() {
        sleepChatTextOpacityMultiplier = clamp01(sleepChatTextOpacityMultiplier, DEFAULT_CHAT_TEXT_BASE);
        sleepChatBackgroundOpacityMultiplier = clamp01(sleepChatBackgroundOpacityMultiplier, DEFAULT_CHAT_BG_BASE);
        if (sleepIndicatorMode == null) {
            sleepIndicatorMode = SleepIndicatorMode.BIOME_CLOCK;
        }
        if (sleepIndicatorAnchor == null) {
            sleepIndicatorAnchor = SleepIndicatorAnchor.TOP_LEFT;
        }
        if (sleepIndicatorVisibility == null) {
            sleepIndicatorVisibility = SleepIndicatorVisibility.BED;
        }
        if (timestampStyle == null) {
            timestampStyle = TimestampStyle.DAY_FIRST;
        }
        if (sleepIndicatorMode == SleepIndicatorMode.TEXT) {
            sleepIndicatorVisibility = SleepIndicatorVisibility.SLEEP;
        }

        sleepOverlayDarknessMultiplier = clampRange(
                sleepOverlayDarknessMultiplier,
                0.0D,
                1.0D,
                DEFAULT_OVERLAY_DARKNESS
        );
        sleepChatOpacityMultiplier = clampRange(
                sleepChatOpacityMultiplier,
                0.0D,
                1.0D,
                DEFAULT_CHAT_GROUP_MULTIPLIER
        );
        sleepIndicatorScale = clampRange(
                sleepIndicatorScale,
                MIN_SLEEP_INDICATOR_SCALE,
                MAX_SLEEP_INDICATOR_SCALE,
                DEFAULT_SLEEP_INDICATOR_SCALE
        );
        timestampColor &= 0x00FFFFFF;
        sleepZzzChance = clampInt(sleepZzzChance, MIN_SLEEP_ZZZ_CHANCE, MAX_SLEEP_ZZZ_CHANCE, SleepZzzConfigBridge.DEFAULT_CHANCE);
        sleepZzzStyle = SleepZzzConfigBridge.parseStyle(sleepZzzStyle).name();
        sleepChatMaxLines = clampInt(sleepChatMaxLines, 0, 12, DEFAULT_CHAT_MAX_LINES);
        sleepCameraTiltDegrees = clampTiltDegrees(sleepCameraTiltDegrees, DEFAULT_TILT_DEGREES);
        mouseSmoothnessPercent = clampInt(mouseSmoothnessPercent, 0, 100, DEFAULT_MOUSE_SMOOTHNESS_PERCENT);
    }

    public double resolveSleepChatTextOpacityFactor() {
        return resolveSleepChatOpacityFactor(sleepChatTextOpacityMultiplier, DEFAULT_CHAT_TEXT_BASE);
    }

    public double resolveSleepChatBackgroundOpacityFactor() {
        return resolveSleepChatOpacityFactor(sleepChatBackgroundOpacityMultiplier, DEFAULT_CHAT_BG_BASE);
    }

    private double resolveSleepChatOpacityFactor(double presetFactor, double presetFallback) {
        double preset = clamp01(presetFactor, presetFallback);
        double overall = clampRange(
                sleepChatOpacityMultiplier,
                0.0D,
                1.0D,
                DEFAULT_CHAT_GROUP_MULTIPLIER
        );

        if (overall <= DEFAULT_CHAT_PRESET_POSITION) {
            double t = DEFAULT_CHAT_PRESET_POSITION <= 0.0D ? 0.0D : overall / DEFAULT_CHAT_PRESET_POSITION;
            return preset * t;
        }

        double denominator = 1.0D - DEFAULT_CHAT_PRESET_POSITION;
        double t = denominator <= 0.0D ? 1.0D : (overall - DEFAULT_CHAT_PRESET_POSITION) / denominator;
        return preset + (1.0D - preset) * t;
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
