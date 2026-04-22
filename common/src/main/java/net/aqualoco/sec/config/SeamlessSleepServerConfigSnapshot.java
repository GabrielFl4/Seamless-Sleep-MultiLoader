package net.aqualoco.sec.config;

// Client-side snapshot of the latest server config packet.
public final class SeamlessSleepServerConfigSnapshot {
    private static int sleepWeatherClearChancePercent = 100;
    private static double sleepAnimationDurationMultiplier = 1.0D;
    private static int serverSimulationDistance = 12;
    private static WorldSleepAccelerationMode worldSleepAccelerationMode = WorldSleepAccelerationMode.AUTOMATIC;
    private static WorldSleepAutomaticMode worldSleepAutomaticMode = WorldSleepAutomaticMode.AGGRESSIVE;
    private static WorldSleepAccelerationPlayersAffected worldSleepAccelerationPlayersAffected = WorldSleepAccelerationPlayersAffected.ALL_PLAYERS;
    private static int manualAccelerationRadiusChunks = WorldSleepAccelerationConfig.DEFAULT_MANUAL_RADIUS_CHUNKS;
    private static int manualAccelerationSpeedPercent = WorldSleepAccelerationConfig.DEFAULT_MANUAL_SPEED_PERCENT;
    private static boolean grassAndFoliageAccelerationEnabled = true;
    private static boolean cropsAndSaplingsAccelerationEnabled = true;
    private static boolean kelpAccelerationEnabled = false;
    private static boolean vanillaOnlyAcceleration = WorldSleepAccelerationConfig.DEFAULT_VANILLA_ONLY_ACCELERATION;
    private static boolean processesAccelerationEnabled = true;
    private static int processesSpeedPercent = 100;
    private static boolean initialized;

    private SeamlessSleepServerConfigSnapshot() {
    }

    public static void update(int weatherClearChancePercent,
                              double durationMultiplier,
                              int simulationDistance,
                              WorldSleepAccelerationMode accelerationMode,
                              WorldSleepAutomaticMode automaticMode,
                              WorldSleepAccelerationPlayersAffected playersAffected,
                              int manualRadiusChunks,
                              int manualSpeedPercent,
                              boolean grassAndFoliageEnabled,
                              boolean cropsAndSaplingsEnabled,
                              boolean kelpEnabled,
                              boolean vanillaOnly,
                              boolean processesEnabled,
                              int processesSpeedPercentValue) {
        sleepWeatherClearChancePercent = weatherClearChancePercent;
        sleepAnimationDurationMultiplier = durationMultiplier;
        serverSimulationDistance = Math.max(1, simulationDistance);
        worldSleepAccelerationMode = accelerationMode;
        worldSleepAutomaticMode = automaticMode;
        worldSleepAccelerationPlayersAffected = playersAffected;
        manualAccelerationRadiusChunks = manualRadiusChunks;
        manualAccelerationSpeedPercent = manualSpeedPercent;
        grassAndFoliageAccelerationEnabled = grassAndFoliageEnabled;
        cropsAndSaplingsAccelerationEnabled = cropsAndSaplingsEnabled;
        kelpAccelerationEnabled = kelpEnabled;
        vanillaOnlyAcceleration = vanillaOnly;
        processesAccelerationEnabled = processesEnabled;
        processesSpeedPercent = processesSpeedPercentValue;
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

    public static int getServerSimulationDistance() {
        return serverSimulationDistance;
    }

    public static WorldSleepAccelerationMode getWorldSleepAccelerationMode() {
        return worldSleepAccelerationMode;
    }

    public static WorldSleepAutomaticMode getWorldSleepAutomaticMode() {
        return worldSleepAutomaticMode;
    }

    public static WorldSleepAccelerationPlayersAffected getWorldSleepAccelerationPlayersAffected() {
        return worldSleepAccelerationPlayersAffected;
    }

    public static int getManualAccelerationRadiusChunks() {
        return manualAccelerationRadiusChunks;
    }

    public static int getManualAccelerationSpeedPercent() {
        return manualAccelerationSpeedPercent;
    }

    public static boolean isGrassAndFoliageAccelerationEnabled() {
        return grassAndFoliageAccelerationEnabled;
    }

    public static boolean isCropsAndSaplingsAccelerationEnabled() {
        return cropsAndSaplingsAccelerationEnabled;
    }

    public static boolean isKelpAccelerationEnabled() {
        return kelpAccelerationEnabled;
    }

    public static boolean isVanillaOnlyAcceleration() {
        return vanillaOnlyAcceleration;
    }

    public static boolean isProcessesAccelerationEnabled() {
        return processesAccelerationEnabled;
    }

    public static int getProcessesSpeedPercent() {
        return processesSpeedPercent;
    }
}
