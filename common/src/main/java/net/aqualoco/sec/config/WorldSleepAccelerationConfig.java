package net.aqualoco.sec.config;

public final class WorldSleepAccelerationConfig {
    public WorldSleepAccelerationMode mode = WorldSleepAccelerationMode.AUTO;
    public WorldSleepAccelerationPreset preset = WorldSleepAccelerationPreset.BALANCED;
    public boolean randomTickAccelerationEnabled = true;
    public boolean processAccelerationEnabled = true;
    public WorldSleepAccelerationGovernorAggressiveness governorAggressiveness =
            WorldSleepAccelerationGovernorAggressiveness.BALANCED;
    public WorldSleepNatureFilterProfile natureFilterProfile = WorldSleepNatureFilterProfile.ALL;
    public final WorldSleepAccelerationModuleConfig nature = new WorldSleepAccelerationModuleConfig();
    public final WorldSleepAccelerationModuleConfig process = new WorldSleepAccelerationModuleConfig();

    public WorldSleepAccelerationConfig() {
        applyPreset(WorldSleepAccelerationPreset.BALANCED);
    }

    public void applyPreset(WorldSleepAccelerationPreset preset) {
        WorldSleepAccelerationPreset resolvedPreset = preset == null
                ? WorldSleepAccelerationPreset.BALANCED
                : preset;
        if (resolvedPreset == WorldSleepAccelerationPreset.CUSTOM) {
            this.preset = WorldSleepAccelerationPreset.CUSTOM;
            return;
        }

        WorldSleepAccelerationPresetValues values = WorldSleepAccelerationPresetValues.of(resolvedPreset);
        this.preset = resolvedPreset;
        this.governorAggressiveness = values.governorAggressiveness;
        this.natureFilterProfile = values.natureFilterProfile;
        copyInto(values.nature, this.nature);
        copyInto(values.process, this.process);
    }

    public void markPresetCustom() {
        this.preset = WorldSleepAccelerationPreset.CUSTOM;
    }

    public void clamp() {
        mode = mode == null ? WorldSleepAccelerationMode.AUTO : mode;
        preset = preset == null ? WorldSleepAccelerationPreset.BALANCED : preset;
        governorAggressiveness = governorAggressiveness == null
                ? WorldSleepAccelerationGovernorAggressiveness.BALANCED
                : governorAggressiveness;
        natureFilterProfile = natureFilterProfile == null ? WorldSleepNatureFilterProfile.ALL : natureFilterProfile;
        nature.clamp();
        process.clamp();
        if (preset != WorldSleepAccelerationPreset.CUSTOM && !matchesPresetValues(preset)) {
            preset = WorldSleepAccelerationPreset.CUSTOM;
        }
    }

    public boolean matchesPresetValues(WorldSleepAccelerationPreset preset) {
        if (preset == null || preset == WorldSleepAccelerationPreset.CUSTOM) {
            return false;
        }
        WorldSleepAccelerationPresetValues values = WorldSleepAccelerationPresetValues.of(preset);
        return governorAggressiveness == values.governorAggressiveness
                && natureFilterProfile == values.natureFilterProfile
                && nature.matches(values.nature)
                && process.matches(values.process);
    }

    private static void copyInto(WorldSleepAccelerationModuleConfig from, WorldSleepAccelerationModuleConfig to) {
        to.baseRadiusChunks = from.baseRadiusChunks;
        to.autoMinRadiusChunks = from.autoMinRadiusChunks;
        to.baseRateFraction = from.baseRateFraction;
        to.autoMinRateFraction = from.autoMinRateFraction;
        to.clamp();
    }
}
