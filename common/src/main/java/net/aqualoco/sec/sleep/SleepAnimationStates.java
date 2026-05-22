package net.aqualoco.sec.sleep;

import net.aqualoco.sec.SeamlessSleepCommon;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

// Server-side sleep animation states, keyed by dimension while preserving the Overworld singleton.
public final class SleepAnimationStates {
    private static final Map<ResourceKey<Level>, SleepAnimationState> STATES = new HashMap<>();

    private SleepAnimationStates() {
    }

    public static SleepAnimationState getOrCreate(ServerLevel level) {
        return getOrCreate(level.dimension());
    }

    public static SleepAnimationState getOrCreate(ResourceKey<Level> dimension) {
        if (Level.OVERWORLD.equals(dimension)) {
            return SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
        }
        return STATES.computeIfAbsent(dimension, ignored -> new SleepAnimationState());
    }

    public static SleepAnimationState getIfPresent(ServerLevel level) {
        return getIfPresent(level.dimension());
    }

    public static SleepAnimationState getIfPresent(ResourceKey<Level> dimension) {
        if (Level.OVERWORLD.equals(dimension)) {
            return SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
        }
        return STATES.get(dimension);
    }
}
