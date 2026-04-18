package net.aqualoco.sec.acceleration;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public final class WorldSleepAccelerationModuleStatus {
    public static final WorldSleepAccelerationModuleStatus INACTIVE =
            new WorldSleepAccelerationModuleStatus(false, 0, 0, 0, 0.0D, 0.0D, 1.0D, 0.0D, 0, 0.0D, 0, new LongOpenHashSet(), WorldSleepAccelerationGovernorAction.NONE);

    private final boolean active;
    private final int baseRadiusChunks;
    private final int minRadiusChunks;
    private final int effectiveRadiusChunks;
    private final double baseRateFraction;
    private final double effectiveRateFraction;
    private final double effectiveTickMultiplier;
    private final double extraRandomTickAttemptsPerSection;
    private final int extraRandomTickWholeAttemptsPerSection;
    private final double extraRandomTickFractionalAttemptsPerSection;
    private final int coveredChunkCount;
    private final LongOpenHashSet coveredChunks;
    private final WorldSleepAccelerationGovernorAction governorAction;

    public WorldSleepAccelerationModuleStatus(boolean active,
                                              int baseRadiusChunks,
                                              int minRadiusChunks,
                                              int effectiveRadiusChunks,
                                              double baseRateFraction,
                                              double effectiveRateFraction,
                                              double effectiveTickMultiplier,
                                              double extraRandomTickAttemptsPerSection,
                                              int extraRandomTickWholeAttemptsPerSection,
                                              double extraRandomTickFractionalAttemptsPerSection,
                                              int coveredChunkCount,
                                              LongOpenHashSet coveredChunks,
                                              WorldSleepAccelerationGovernorAction governorAction) {
        this.active = active;
        this.baseRadiusChunks = baseRadiusChunks;
        this.minRadiusChunks = minRadiusChunks;
        this.effectiveRadiusChunks = effectiveRadiusChunks;
        this.baseRateFraction = baseRateFraction;
        this.effectiveRateFraction = effectiveRateFraction;
        this.effectiveTickMultiplier = effectiveTickMultiplier;
        this.extraRandomTickAttemptsPerSection = extraRandomTickAttemptsPerSection;
        this.extraRandomTickWholeAttemptsPerSection = extraRandomTickWholeAttemptsPerSection;
        this.extraRandomTickFractionalAttemptsPerSection = extraRandomTickFractionalAttemptsPerSection;
        this.coveredChunkCount = coveredChunkCount;
        this.coveredChunks = coveredChunks;
        this.governorAction = governorAction;
    }

    public boolean isActive() {
        return active;
    }

    public int getBaseRadiusChunks() {
        return baseRadiusChunks;
    }

    public int getMinRadiusChunks() {
        return minRadiusChunks;
    }

    public int getEffectiveRadiusChunks() {
        return effectiveRadiusChunks;
    }

    public double getBaseRateFraction() {
        return baseRateFraction;
    }

    public double getEffectiveRateFraction() {
        return effectiveRateFraction;
    }

    public double getEffectiveTickMultiplier() {
        return effectiveTickMultiplier;
    }

    public double getExtraRandomTickAttemptsPerSection() {
        return extraRandomTickAttemptsPerSection;
    }

    public int getExtraRandomTickWholeAttemptsPerSection() {
        return extraRandomTickWholeAttemptsPerSection;
    }

    public double getExtraRandomTickFractionalAttemptsPerSection() {
        return extraRandomTickFractionalAttemptsPerSection;
    }

    public int getCoveredChunkCount() {
        return coveredChunkCount;
    }

    public WorldSleepAccelerationGovernorAction getGovernorAction() {
        return governorAction;
    }

    public boolean coversChunk(long chunkPos) {
        return active && coveredChunks.contains(chunkPos);
    }
}
