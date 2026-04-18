package net.aqualoco.sec.acceleration;

import net.aqualoco.sec.config.WorldSleepAccelerationMode;
import net.aqualoco.sec.config.WorldSleepAccelerationPreset;
import net.aqualoco.sec.config.WorldSleepNatureFilterProfile;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class WorldSleepAccelerationStatus {
    public static final WorldSleepAccelerationStatus INACTIVE = new WorldSleepAccelerationStatus(
            false,
            null,
            0,
            1.0D,
            50.0D,
            50.0D,
            0.0D,
            WorldSleepAccelerationMode.OFF,
            WorldSleepAccelerationPreset.BALANCED,
            WorldSleepNatureFilterProfile.ALL,
            WorldSleepAccelerationGovernorAction.NONE,
            WorldSleepAccelerationModuleStatus.INACTIVE,
            WorldSleepAccelerationModuleStatus.INACTIVE
    );

    private final boolean active;
    private final ResourceKey<Level> dimension;
    private final int activePlayerCount;
    private final double worldSleepRate;
    private final double averageMspt;
    private final double p95Mspt;
    private final double governorPressure;
    private final WorldSleepAccelerationMode mode;
    private final WorldSleepAccelerationPreset preset;
    private final WorldSleepNatureFilterProfile natureFilterProfile;
    private final WorldSleepAccelerationGovernorAction governorAction;
    private final WorldSleepAccelerationModuleStatus nature;
    private final WorldSleepAccelerationModuleStatus process;

    public WorldSleepAccelerationStatus(boolean active,
                                        ResourceKey<Level> dimension,
                                        int activePlayerCount,
                                        double worldSleepRate,
                                        double averageMspt,
                                        double p95Mspt,
                                        double governorPressure,
                                        WorldSleepAccelerationMode mode,
                                        WorldSleepAccelerationPreset preset,
                                        WorldSleepNatureFilterProfile natureFilterProfile,
                                        WorldSleepAccelerationGovernorAction governorAction,
                                        WorldSleepAccelerationModuleStatus nature,
                                        WorldSleepAccelerationModuleStatus process) {
        this.active = active;
        this.dimension = dimension;
        this.activePlayerCount = activePlayerCount;
        this.worldSleepRate = worldSleepRate;
        this.averageMspt = averageMspt;
        this.p95Mspt = p95Mspt;
        this.governorPressure = governorPressure;
        this.mode = mode;
        this.preset = preset;
        this.natureFilterProfile = natureFilterProfile;
        this.governorAction = governorAction;
        this.nature = nature;
        this.process = process;
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

    public double getWorldSleepRate() {
        return worldSleepRate;
    }

    public double getAverageMspt() {
        return averageMspt;
    }

    public double getP95Mspt() {
        return p95Mspt;
    }

    public double getGovernorPressure() {
        return governorPressure;
    }

    public WorldSleepAccelerationMode getMode() {
        return mode;
    }

    public WorldSleepAccelerationPreset getPreset() {
        return preset;
    }

    public WorldSleepNatureFilterProfile getNatureFilterProfile() {
        return natureFilterProfile;
    }

    public WorldSleepAccelerationGovernorAction getGovernorAction() {
        return governorAction;
    }

    public WorldSleepAccelerationModuleStatus getNature() {
        return nature;
    }

    public WorldSleepAccelerationModuleStatus getProcess() {
        return process;
    }
}
