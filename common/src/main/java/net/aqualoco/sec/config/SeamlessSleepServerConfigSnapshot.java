package net.aqualoco.sec.config;

// Client-side snapshot of the latest server config packet.
public final class SeamlessSleepServerConfigSnapshot {
    private static final int DEFAULT_SLEEP_WEATHER_CLEAR_CHANCE_PERCENT = 100;
    private static final double DEFAULT_SLEEP_ANIMATION_DURATION_MULTIPLIER = 1.0D;

    private static int sleepWeatherClearChancePercent = 100;
    private static double sleepAnimationDurationMultiplier = 1.0D;
    private static boolean initialized;

    private SeamlessSleepServerConfigSnapshot() {
    }

    public static void update(int weatherClearChancePercent, double durationMultiplier) {
        sleepWeatherClearChancePercent = weatherClearChancePercent;
        sleepAnimationDurationMultiplier = durationMultiplier;
        initialized = true;
    }

    public static void reset() {
        sleepWeatherClearChancePercent = DEFAULT_SLEEP_WEATHER_CLEAR_CHANCE_PERCENT;
        sleepAnimationDurationMultiplier = DEFAULT_SLEEP_ANIMATION_DURATION_MULTIPLIER;
        initialized = false;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static int getSleepWeatherClearChancePercent() {
        return sleepWeatherClearChancePercent;
    }

    public static double getSleepAnimationDurationMultiplier() {
        return sleepAnimationDurationMultiplier;
    }
}
