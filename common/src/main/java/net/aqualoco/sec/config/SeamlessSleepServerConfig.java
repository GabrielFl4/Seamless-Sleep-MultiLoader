package net.aqualoco.sec.config;

// Server-authoritative sleep settings that are synced to clients.
public final class SeamlessSleepServerConfig {
    public int sleepWeatherClearChancePercent = 100;
    public double sleepAnimationDurationMultiplier = 1.0D;

    public void clamp() {
        sleepWeatherClearChancePercent = clampInt(sleepWeatherClearChancePercent, 0, 100);
        sleepAnimationDurationMultiplier = clampRange(
                sleepAnimationDurationMultiplier,
                0.25D,
                8.0D,
                1.0D
        );
    }

    private static int clampInt(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
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
}
