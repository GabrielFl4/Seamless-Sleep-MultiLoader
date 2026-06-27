package net.aqualoco.sec.sleep;

import net.minecraft.util.Mth;

// Mirrors vanilla's minimum of one required sleeper, including playersSleepingPercentage=0.
public final class SleepRequirement {
    private SleepRequirement() {
    }

    public static int sleepersNeeded(int activePlayers, int requiredSleepPercentage) {
        int safePercentage = Math.max(0, requiredSleepPercentage);
        return Math.max(1, Mth.ceil(activePlayers * safePercentage / 100.0F));
    }
}
