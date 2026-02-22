package net.aqualoco.sec.config;

// Server-authoritative sleep settings that are synced to clients.
public final class SeamlessSleepServerConfig {
    public Boolean sleepClearsWeather = true;
    public double sleepAnimationDurationMultiplier = 1.0D;

    public void clamp() {
        if (sleepClearsWeather == null) {
            sleepClearsWeather = true;
        }
        sleepAnimationDurationMultiplier = clampRange(
                sleepAnimationDurationMultiplier,
                0.25D,
                8.0D,
                1.0D
        );
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
