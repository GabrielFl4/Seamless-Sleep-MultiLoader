package net.aqualoco.sec;

import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.registry.ModBlocks;
import net.aqualoco.sec.sleep.SleepAnimationState;

public final class SeamlessSleepCommon {

    public static final SleepAnimationState OVERWORLD_SLEEP_ANIMATION = new SleepAnimationState();

    private SeamlessSleepCommon() {
    }

    public static void init() {
        SeamlessSleepClientConfigManager.init();
        SeamlessSleepServerConfigManager.init();
        SleepAnimationNetworking.initCommon();
        ModBlocks.registerModBlocks();
        Constants.LOG.info("[Seamless Sleep] inicializado. Animacao de sono e bloco sleep_barrier registrados.");
    }
}
