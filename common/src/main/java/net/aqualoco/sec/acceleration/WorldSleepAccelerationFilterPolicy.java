package net.aqualoco.sec.acceleration;

import net.minecraft.world.level.block.state.BlockState;

public final class WorldSleepAccelerationFilterPolicy {
    public static final WorldSleepAccelerationFilterPolicy DISABLED =
            new WorldSleepAccelerationFilterPolicy(false, false, false, false, true);

    private final boolean vanillaOnly;
    private final int allowedCategoryMask;
    private final int cacheKey;

    public WorldSleepAccelerationFilterPolicy(boolean grassAndFoliageEnabled,
                                              boolean cropsAndSaplingsEnabled,
                                              boolean vinesAndBambooEnabled,
                                              boolean kelpEnabled,
                                              boolean vanillaOnly) {
        this.vanillaOnly = vanillaOnly;
        int compiledMask = 0;
        if (grassAndFoliageEnabled) {
            compiledMask |= WorldSleepRandomTickFilters.FLAG_GRASS_AND_FOLIAGE;
        }
        if (cropsAndSaplingsEnabled) {
            compiledMask |= WorldSleepRandomTickFilters.FLAG_CROPS_AND_SAPLINGS;
        }
        if (vinesAndBambooEnabled) {
            compiledMask |= WorldSleepRandomTickFilters.FLAG_VINES_AND_BAMBOO;
        }
        if (kelpEnabled) {
            compiledMask |= WorldSleepRandomTickFilters.FLAG_KELP;
        }
        this.allowedCategoryMask = compiledMask;
        this.cacheKey = compiledMask | (vanillaOnly ? 1 << 8 : 0);
    }

    public boolean isAnyEnabled() {
        return allowedCategoryMask != 0;
    }

    public boolean allows(BlockState state) {
        return WorldSleepRandomTickFilters.isEligible(allowedCategoryMask, vanillaOnly, state);
    }

    public boolean mayContainRelevantState(BlockState state) {
        return WorldSleepRandomTickFilters.mayContainRelevantState(
                allowedCategoryMask,
                vanillaOnly,
                state
        );
    }

    public int getCacheKey() {
        return cacheKey;
    }
}
