package net.aqualoco.sec;

import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.sleep.SleepAnimationState;

// Common bootstrap that wires configs, networking, and shared registries.
public final class SeamlessSleepCommon {

    public static final SleepAnimationState OVERWORLD_SLEEP_ANIMATION = new SleepAnimationState();

    private SeamlessSleepCommon() {
    }

    public static void init() {
        SeamlessSleepClientConfigManager.init();
        SeamlessSleepServerConfigManager.init();
        SleepAnimationNetworking.initCommon();
        Constants.info("Initialized. Sleep animation networking is registered.");
    }
}
