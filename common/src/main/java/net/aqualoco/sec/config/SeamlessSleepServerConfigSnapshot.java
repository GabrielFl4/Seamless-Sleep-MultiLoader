package net.aqualoco.sec.config;

// Client-side snapshot of the latest server config packet.
public final class SeamlessSleepServerConfigSnapshot {
    private static boolean sleepClearsWeather = true;
    private static double sleepAnimationDurationMultiplier = 1.0D;
    private static boolean initialized;

    private SeamlessSleepServerConfigSnapshot() {
    }

    public static void update(boolean sleepClearsWeatherValue, double durationMultiplier) {
        sleepClearsWeather = sleepClearsWeatherValue;
        sleepAnimationDurationMultiplier = durationMultiplier;
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean getSleepClearsWeather() {
        return sleepClearsWeather;
    }

    public static double getSleepAnimationDurationMultiplier() {
        return sleepAnimationDurationMultiplier;
    }
}
