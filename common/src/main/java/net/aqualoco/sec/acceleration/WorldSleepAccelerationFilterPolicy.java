package net.aqualoco.sec.acceleration;

import net.minecraft.world.level.block.state.BlockState;

public final class WorldSleepAccelerationFilterPolicy {
    public static final WorldSleepAccelerationFilterPolicy DISABLED =
            new WorldSleepAccelerationFilterPolicy(false, false, false, true);

    private final boolean grassAndFoliageEnabled;
    private final boolean cropsAndSaplingsEnabled;
    private final boolean kelpEnabled;
    private final boolean vanillaOnly;

    public WorldSleepAccelerationFilterPolicy(boolean grassAndFoliageEnabled,
                                              boolean cropsAndSaplingsEnabled,
                                              boolean kelpEnabled,
                                              boolean vanillaOnly) {
        this.grassAndFoliageEnabled = grassAndFoliageEnabled;
        this.cropsAndSaplingsEnabled = cropsAndSaplingsEnabled;
        this.kelpEnabled = kelpEnabled;
        this.vanillaOnly = vanillaOnly;
    }

    public boolean isGrassAndFoliageEnabled() {
        return grassAndFoliageEnabled;
    }

    public boolean isCropsAndSaplingsEnabled() {
        return cropsAndSaplingsEnabled;
    }

    public boolean isKelpEnabled() {
        return kelpEnabled;
    }

    public boolean isVanillaOnly() {
        return vanillaOnly;
    }

    public boolean isAnyEnabled() {
        return grassAndFoliageEnabled || cropsAndSaplingsEnabled || kelpEnabled;
    }

    public boolean allows(BlockState state) {
        return isAnyEnabled() && WorldSleepRandomTickFilters.isEligible(this, state);
    }
}
