package net.aqualoco.sec;

import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

// Fabric-side server event hooks used to sync config to players when they join.
final class SeamlessSleepServerEvents {

    private SeamlessSleepServerEvents() {
    }

    static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SeamlessSleepServerConfig cfg = SeamlessSleepServerConfigManager.get();
            ServerPlayNetworking.send(handler.getPlayer(),
                    new ServerConfigSyncPayload(
                            cfg.sleepClearsWeather,
                            cfg.sleepAnimationDurationMultiplier
                    ));
        });
    }
}
