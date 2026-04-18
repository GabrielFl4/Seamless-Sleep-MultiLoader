package net.aqualoco.sec.config;

final class WorldSleepAccelerationPresetValues {
    final WorldSleepAccelerationGovernorAggressiveness governorAggressiveness;
    final WorldSleepNatureFilterProfile natureFilterProfile;
    final WorldSleepAccelerationModuleConfig nature;
    final WorldSleepAccelerationModuleConfig process;

    private WorldSleepAccelerationPresetValues(WorldSleepAccelerationGovernorAggressiveness governorAggressiveness,
                                               WorldSleepNatureFilterProfile natureFilterProfile,
                                               WorldSleepAccelerationModuleConfig nature,
                                               WorldSleepAccelerationModuleConfig process) {
        this.governorAggressiveness = governorAggressiveness;
        this.natureFilterProfile = natureFilterProfile;
        this.nature = nature;
        this.process = process;
    }

    static WorldSleepAccelerationPresetValues of(WorldSleepAccelerationPreset preset) {
        return switch (preset) {
            case ECO -> new WorldSleepAccelerationPresetValues(
                    WorldSleepAccelerationGovernorAggressiveness.CONSERVATIVE,
                    WorldSleepNatureFilterProfile.FARM_ONLY,
                    module(4, 2, 0.25D, 0.12D),
                    module(4, 2, 0.50D, 0.25D)
            );
            case AGGRESSIVE -> new WorldSleepAccelerationPresetValues(
                    WorldSleepAccelerationGovernorAggressiveness.AGGRESSIVE,
                    WorldSleepNatureFilterProfile.ALL,
                    module(0, 4, 0.75D, 0.35D),
                    module(0, 4, 1.0D, 0.60D)
            );
            case BALANCED, CUSTOM -> new WorldSleepAccelerationPresetValues(
                    WorldSleepAccelerationGovernorAggressiveness.BALANCED,
                    WorldSleepNatureFilterProfile.ALL,
                    module(6, 3, 0.45D, 0.20D),
                    module(6, 3, 0.75D, 0.40D)
            );
        };
    }

    private static WorldSleepAccelerationModuleConfig module(int baseRadius,
                                                             int minRadius,
                                                             double baseFraction,
                                                             double minFraction) {
        WorldSleepAccelerationModuleConfig config = new WorldSleepAccelerationModuleConfig();
        config.baseRadiusChunks = baseRadius;
        config.autoMinRadiusChunks = minRadius;
        config.baseRateFraction = baseFraction;
        config.autoMinRateFraction = minFraction;
        config.clamp();
        return config;
    }
}
