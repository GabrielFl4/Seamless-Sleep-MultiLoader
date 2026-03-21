package net.aqualoco.sec.config;

// Client-side snapshot of the latest server config packet.
public final class SeamlessSleepServerConfigSnapshot {
    private static int sleepWeatherClearChancePercent = 100;
    private static double sleepAnimationDurationMultiplier = 1.0D;
    private static boolean initialized;

    private SeamlessSleepServerConfigSnapshot() {
    }

    public static void update(int sleepWeatherClearChancePercentValue, double durationMultiplier) {
        sleepWeatherClearChancePercent = sleepWeatherClearChancePercentValue;
        sleepAnimationDurationMultiplier = durationMultiplier;
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean getSleepClearsWeather() {
        return sleepWeatherClearChancePercent > 0;
    }

    public static int getSleepWeatherClearChancePercent() {
        return sleepWeatherClearChancePercent;
    }

    public static double getSleepAnimationDurationMultiplier() {
        return sleepAnimationDurationMultiplier;
    }
}
