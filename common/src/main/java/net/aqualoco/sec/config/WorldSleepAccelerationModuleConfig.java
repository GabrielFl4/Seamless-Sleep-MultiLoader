package net.aqualoco.sec.config;

public final class WorldSleepAccelerationModuleConfig {
    public int baseRadiusChunks;
    public int autoMinRadiusChunks;
    public double baseRateFraction;
    public double autoMinRateFraction;

    public WorldSleepAccelerationModuleConfig copy() {
        WorldSleepAccelerationModuleConfig copy = new WorldSleepAccelerationModuleConfig();
        copy.baseRadiusChunks = this.baseRadiusChunks;
        copy.autoMinRadiusChunks = this.autoMinRadiusChunks;
        copy.baseRateFraction = this.baseRateFraction;
        copy.autoMinRateFraction = this.autoMinRateFraction;
        return copy;
    }

    public void clamp() {
        baseRadiusChunks = SeamlessSleepServerConfig.clampInt(baseRadiusChunks, 0, 32);
        autoMinRadiusChunks = SeamlessSleepServerConfig.clampInt(autoMinRadiusChunks, 0, 32);
        baseRateFraction = SeamlessSleepServerConfig.clampRange(baseRateFraction, 0.0D, 1.0D, 1.0D);
        autoMinRateFraction = SeamlessSleepServerConfig.clampRange(autoMinRateFraction, 0.0D, 1.0D, 0.25D);
        if (baseRadiusChunks > 0 && autoMinRadiusChunks > baseRadiusChunks) {
            autoMinRadiusChunks = baseRadiusChunks;
        }
        if (autoMinRateFraction > baseRateFraction) {
            autoMinRateFraction = baseRateFraction;
        }
    }

    boolean matches(WorldSleepAccelerationModuleConfig other) {
        if (other == null) {
            return false;
        }
        return baseRadiusChunks == other.baseRadiusChunks
                && autoMinRadiusChunks == other.autoMinRadiusChunks
                && sameDouble(baseRateFraction, other.baseRateFraction)
                && sameDouble(autoMinRateFraction, other.autoMinRateFraction);
    }

    private static boolean sameDouble(double left, double right) {
        return Math.abs(left - right) <= 0.0001D;
    }
}
