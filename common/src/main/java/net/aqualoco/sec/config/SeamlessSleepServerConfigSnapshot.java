package net.aqualoco.sec.config;

// Client-side snapshot of the latest server config packet.
public final class SeamlessSleepServerConfigSnapshot {
    private static int sleepWeatherClearChancePercent = 100;
    private static double sleepAnimationDurationMultiplier = 1.0D;
    private static WorldSleepAccelerationMode worldSleepAccelerationMode = WorldSleepAccelerationMode.AUTO;
    private static WorldSleepAccelerationPreset worldSleepAccelerationPreset = WorldSleepAccelerationPreset.BALANCED;
    private static boolean randomTickAccelerationEnabled = true;
    private static boolean processAccelerationEnabled = true;
    private static WorldSleepAccelerationGovernorAggressiveness governorAggressiveness =
            WorldSleepAccelerationGovernorAggressiveness.BALANCED;
    private static WorldSleepNatureFilterProfile natureFilterProfile = WorldSleepNatureFilterProfile.ALL;
    private static int natureBaseRadiusChunks = 6;
    private static int natureAutoMinRadiusChunks = 3;
    private static double natureBaseRateFraction = 0.45D;
    private static double natureAutoMinRateFraction = 0.20D;
    private static int processBaseRadiusChunks = 6;
    private static int processAutoMinRadiusChunks = 3;
    private static double processBaseRateFraction = 0.75D;
    private static double processAutoMinRateFraction = 0.40D;
    private static boolean initialized;

    private SeamlessSleepServerConfigSnapshot() {
    }

    public static void update(int weatherClearChancePercent,
                              double durationMultiplier,
                              WorldSleepAccelerationMode accelerationMode,
                              WorldSleepAccelerationPreset accelerationPreset,
                              boolean randomTickEnabled,
                              boolean processEnabled,
                              WorldSleepAccelerationGovernorAggressiveness accelerationGovernorAggressiveness,
                              WorldSleepNatureFilterProfile accelerationNatureFilterProfile,
                              int accelerationNatureBaseRadiusChunks,
                              int accelerationNatureAutoMinRadiusChunks,
                              double accelerationNatureBaseRateFraction,
                              double accelerationNatureAutoMinRateFraction,
                              int accelerationProcessBaseRadiusChunks,
                              int accelerationProcessAutoMinRadiusChunks,
                              double accelerationProcessBaseRateFraction,
                              double accelerationProcessAutoMinRateFraction) {
        sleepWeatherClearChancePercent = weatherClearChancePercent;
        sleepAnimationDurationMultiplier = durationMultiplier;
        worldSleepAccelerationMode = accelerationMode;
        worldSleepAccelerationPreset = accelerationPreset;
        randomTickAccelerationEnabled = randomTickEnabled;
        processAccelerationEnabled = processEnabled;
        governorAggressiveness = accelerationGovernorAggressiveness;
        natureFilterProfile = accelerationNatureFilterProfile;
        natureBaseRadiusChunks = accelerationNatureBaseRadiusChunks;
        natureAutoMinRadiusChunks = accelerationNatureAutoMinRadiusChunks;
        natureBaseRateFraction = accelerationNatureBaseRateFraction;
        natureAutoMinRateFraction = accelerationNatureAutoMinRateFraction;
        processBaseRadiusChunks = accelerationProcessBaseRadiusChunks;
        processAutoMinRadiusChunks = accelerationProcessAutoMinRadiusChunks;
        processBaseRateFraction = accelerationProcessBaseRateFraction;
        processAutoMinRateFraction = accelerationProcessAutoMinRateFraction;
        initialized = true;
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

    public static WorldSleepAccelerationMode getWorldSleepAccelerationMode() {
        return worldSleepAccelerationMode;
    }

    public static WorldSleepAccelerationPreset getWorldSleepAccelerationPreset() {
        return worldSleepAccelerationPreset;
    }

    public static boolean isRandomTickAccelerationEnabled() {
        return randomTickAccelerationEnabled;
    }

    public static boolean isProcessAccelerationEnabled() {
        return processAccelerationEnabled;
    }

    public static WorldSleepAccelerationGovernorAggressiveness getGovernorAggressiveness() {
        return governorAggressiveness;
    }

    public static WorldSleepNatureFilterProfile getNatureFilterProfile() {
        return natureFilterProfile;
    }

    public static int getNatureBaseRadiusChunks() {
        return natureBaseRadiusChunks;
    }

    public static int getNatureAutoMinRadiusChunks() {
        return natureAutoMinRadiusChunks;
    }

    public static double getNatureBaseRateFraction() {
        return natureBaseRateFraction;
    }

    public static double getNatureAutoMinRateFraction() {
        return natureAutoMinRateFraction;
    }

    public static int getProcessBaseRadiusChunks() {
        return processBaseRadiusChunks;
    }

    public static int getProcessAutoMinRadiusChunks() {
        return processAutoMinRadiusChunks;
    }

    public static double getProcessBaseRateFraction() {
        return processBaseRateFraction;
    }

    public static double getProcessAutoMinRateFraction() {
        return processAutoMinRateFraction;
    }
}
