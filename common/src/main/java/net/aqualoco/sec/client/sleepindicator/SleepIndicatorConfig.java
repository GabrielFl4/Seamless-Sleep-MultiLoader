package net.aqualoco.sec.client.sleepindicator;

import net.aqualoco.sec.config.SeamlessSleepClientConfig;

// Normalizes client config values before the sleep indicator system consumes them.
public record SleepIndicatorConfig(
        SleepIndicatorMode mode,
        SleepIndicatorAnchor anchor,
        SleepIndicatorVisibility visibility,
        float scale
) {
    public static SleepIndicatorConfig from(SeamlessSleepClientConfig config) {
        SleepIndicatorMode mode = config.sleepIndicatorMode == null
                ? SleepIndicatorMode.BIOME_CLOCK
                : config.sleepIndicatorMode;
        SleepIndicatorAnchor anchor = config.sleepIndicatorAnchor == null
                ? SleepIndicatorAnchor.CENTER
                : config.sleepIndicatorAnchor;
        SleepIndicatorVisibility visibility = config.sleepIndicatorVisibility == null
                ? SleepIndicatorVisibility.ALWAYS
                : config.sleepIndicatorVisibility;

        float scale = (float) config.sleepIndicatorScale;
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            scale = 1.0F;
        }

        return new SleepIndicatorConfig(mode, anchor, visibility, scale);
    }
}
