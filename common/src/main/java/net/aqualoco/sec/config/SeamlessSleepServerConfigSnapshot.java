package net.aqualoco.sec.config;

public final class SeamlessSleepServerConfigSnapshot {
    private static boolean sleepClearsWeather = true;
    private static boolean initialized;

    private SeamlessSleepServerConfigSnapshot() {
    }

    public static void update(boolean sleepClearsWeatherValue) {
        sleepClearsWeather = sleepClearsWeatherValue;
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean getSleepClearsWeather() {
        return sleepClearsWeather;
    }
}
