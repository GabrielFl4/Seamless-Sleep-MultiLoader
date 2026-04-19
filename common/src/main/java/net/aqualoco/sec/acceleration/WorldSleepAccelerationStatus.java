package net.aqualoco.sec.acceleration;

import net.aqualoco.sec.config.WorldSleepAccelerationMode;
import net.aqualoco.sec.config.WorldSleepAccelerationPlayersAffected;
import net.aqualoco.sec.config.WorldSleepAutomaticMode;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class WorldSleepAccelerationStatus {
    public static final WorldSleepAccelerationStatus INACTIVE = new WorldSleepAccelerationStatus(
            false,
            null,
            0,
            1,
            1.0D,
            50.0D,
            50.0D,
            WorldSleepAccelerationMode.OFF,
            WorldSleepAutomaticMode.AGGRESSIVE,
            WorldSleepAccelerationPlayersAffected.ALL_PLAYERS,
            WorldSleepAccelerationFilterPolicy.DISABLED,
            WorldSleepAccelerationGovernorAction.NONE,
            WorldSleepAccelerationGovernorSnapshot.INACTIVE,
            WorldSleepAccelerationModuleStatus.INACTIVE,
            WorldSleepAccelerationModuleStatus.INACTIVE,
            false
    );

    private final boolean active;
    private final ResourceKey<Level> dimension;
    private final int activePlayerCount;
    private final int simulationDistance;
    private final double worldSleepRate;
    private final double averageMspt;
    private final double p95Mspt;
    private final WorldSleepAccelerationMode mode;
    private final WorldSleepAutomaticMode automaticMode;
    private final WorldSleepAccelerationPlayersAffected playersAffected;
    private final WorldSleepAccelerationFilterPolicy filterPolicy;
    private final WorldSleepAccelerationGovernorAction governorAction;
    private final WorldSleepAccelerationGovernorSnapshot governorSnapshot;
    private final WorldSleepAccelerationModuleStatus nature;
    private final WorldSleepAccelerationModuleStatus process;
    private final boolean processesTemporarilySuppressed;

    public WorldSleepAccelerationStatus(boolean active,
                                        ResourceKey<Level> dimension,
                                        int activePlayerCount,
                                        int simulationDistance,
                                        double worldSleepRate,
                                        double averageMspt,
                                        double p95Mspt,
                                        WorldSleepAccelerationMode mode,
                                        WorldSleepAutomaticMode automaticMode,
                                        WorldSleepAccelerationPlayersAffected playersAffected,
                                        WorldSleepAccelerationFilterPolicy filterPolicy,
                                        WorldSleepAccelerationGovernorAction governorAction,
                                        WorldSleepAccelerationGovernorSnapshot governorSnapshot,
                                        WorldSleepAccelerationModuleStatus nature,
                                        WorldSleepAccelerationModuleStatus process,
                                        boolean processesTemporarilySuppressed) {
        this.active = active;
        this.dimension = dimension;
        this.activePlayerCount = activePlayerCount;
        this.simulationDistance = simulationDistance;
        this.worldSleepRate = worldSleepRate;
        this.averageMspt = averageMspt;
        this.p95Mspt = p95Mspt;
        this.mode = mode;
        this.automaticMode = automaticMode;
        this.playersAffected = playersAffected;
        this.filterPolicy = filterPolicy;
        this.governorAction = governorAction;
        this.governorSnapshot = governorSnapshot;
        this.nature = nature;
        this.process = process;
        this.processesTemporarilySuppressed = processesTemporarilySuppressed;
    }

    public boolean isActive() {
        return active;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public int getActivePlayerCount() {
        return activePlayerCount;
    }

    public int getSimulationDistance() {
        return simulationDistance;
    }

    public double getWorldSleepRate() {
        return worldSleepRate;
    }

    public double getAverageMspt() {
        return averageMspt;
    }

    public double getP95Mspt() {
        return p95Mspt;
    }

    public WorldSleepAccelerationMode getMode() {
        return mode;
    }

    public WorldSleepAutomaticMode getAutomaticMode() {
        return automaticMode;
    }

    public WorldSleepAccelerationPlayersAffected getPlayersAffected() {
        return playersAffected;
    }

    public WorldSleepAccelerationFilterPolicy getFilterPolicy() {
        return filterPolicy;
    }

    public WorldSleepAccelerationGovernorAction getGovernorAction() {
        return governorAction;
    }

    public WorldSleepAccelerationGovernorSnapshot getGovernorSnapshot() {
        return governorSnapshot;
    }

    public WorldSleepAccelerationModuleStatus getNature() {
        return nature;
    }

    public WorldSleepAccelerationModuleStatus getProcess() {
        return process;
    }

    public boolean isProcessesTemporarilySuppressed() {
        return processesTemporarilySuppressed;
    }
}
