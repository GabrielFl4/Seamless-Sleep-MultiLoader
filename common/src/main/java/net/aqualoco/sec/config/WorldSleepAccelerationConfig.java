package net.aqualoco.sec.config;

import net.aqualoco.sec.acceleration.WorldSleepAccelerationFilterPolicy;

public final class WorldSleepAccelerationConfig {
    public static final int DEFAULT_MANUAL_RADIUS_CHUNKS = 12;
    private static final int MAX_CONFIG_RADIUS_CHUNKS = 32;
    public int manualAccelerationRadiusChunks = DEFAULT_MANUAL_RADIUS_CHUNKS;

    public WorldSleepAccelerationMode mode = WorldSleepAccelerationMode.AUTOMATIC;
    public WorldSleepAutomaticMode automaticMode = WorldSleepAutomaticMode.AGGRESSIVE;
    public WorldSleepAccelerationPlayersAffected playersAffected = WorldSleepAccelerationPlayersAffected.ALL_PLAYERS;
    public int manualAccelerationSpeedPercent = 100;
    public boolean grassAndFoliageAccelerationEnabled = true;
    public boolean cropsAndSaplingsAccelerationEnabled = true;
    public boolean kelpAccelerationEnabled = false;
    public boolean vanillaOnlyAcceleration = true;
    public boolean processesAccelerationEnabled = true;
    public int processesSpeedPercent = 100;

    public void clamp() {
        mode = mode == null ? WorldSleepAccelerationMode.AUTOMATIC : mode;
        automaticMode = automaticMode == null ? WorldSleepAutomaticMode.AGGRESSIVE : automaticMode;
        playersAffected = playersAffected == null
                ? WorldSleepAccelerationPlayersAffected.ALL_PLAYERS
                : playersAffected;
        manualAccelerationRadiusChunks = clampConfiguredRadius(manualAccelerationRadiusChunks);
        manualAccelerationSpeedPercent = SeamlessSleepServerConfig.clampInt(
                manualAccelerationSpeedPercent,
                0,
                100
        );
        processesSpeedPercent = SeamlessSleepServerConfig.clampInt(
                processesSpeedPercent,
                0,
                100
        );
    }

    public AutomaticCeiling getAutomaticCeiling(int simulationDistance) {
        int clampedSimulationDistance = clampSimulationDistance(simulationDistance);
        return switch (automaticMode) {
            case PERFORMANCE -> new AutomaticCeiling(
                    Math.max(1, (int) Math.floor(clampedSimulationDistance * 0.40D)),
                    40
            );
            case BALANCED -> new AutomaticCeiling(
                    Math.max(1, (int) Math.floor(clampedSimulationDistance * 0.75D)),
                    75
            );
            case AGGRESSIVE -> new AutomaticCeiling(clampedSimulationDistance, 100);
        };
    }

    public WorldSleepAccelerationPlayersAffected resolveAutomaticPlayersAffected() {
        return automaticMode == WorldSleepAutomaticMode.PERFORMANCE
                ? WorldSleepAccelerationPlayersAffected.SLEEPERS
                : WorldSleepAccelerationPlayersAffected.ALL_PLAYERS;
    }

    public WorldSleepAccelerationPlayersAffected resolveEffectivePlayersAffected() {
        return mode == WorldSleepAccelerationMode.AUTOMATIC
                ? resolveAutomaticPlayersAffected()
                : playersAffected;
    }

    public int resolveManualRadiusChunks(int simulationDistance) {
        int clampedSimulationDistance = clampSimulationDistance(simulationDistance);
        int configuredRadius = clampConfiguredRadius(manualAccelerationRadiusChunks);
        return SeamlessSleepServerConfig.clampInt(configuredRadius, 1, clampedSimulationDistance);
    }

    public int resolveManualAccelerationSpeedPercent() {
        return SeamlessSleepServerConfig.clampInt(manualAccelerationSpeedPercent, 0, 100);
    }

    public int resolveProcessesSpeedPercent() {
        return SeamlessSleepServerConfig.clampInt(processesSpeedPercent, 0, 100);
    }

    public boolean hasAnyNatureAccelerationEnabled() {
        return grassAndFoliageAccelerationEnabled
                || cropsAndSaplingsAccelerationEnabled
                || kelpAccelerationEnabled;
    }

    public WorldSleepAccelerationFilterPolicy createFilterPolicy() {
        return new WorldSleepAccelerationFilterPolicy(
                grassAndFoliageAccelerationEnabled,
                cropsAndSaplingsAccelerationEnabled,
                kelpAccelerationEnabled,
                vanillaOnlyAcceleration
        );
    }

    public static int clampSimulationDistance(int simulationDistance) {
        return Math.max(1, simulationDistance);
    }

    private static int clampConfiguredRadius(int value) {
        if (value < 1) {
            return DEFAULT_MANUAL_RADIUS_CHUNKS;
        }
        return Math.min(value, MAX_CONFIG_RADIUS_CHUNKS);
    }

    public record AutomaticCeiling(int radiusChunks, int speedPercent) {
    }
}
