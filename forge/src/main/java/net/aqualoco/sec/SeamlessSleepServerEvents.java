package net.aqualoco.sec;

import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

// Forge-side server event hooks used to sync config when players log in.
final class SeamlessSleepServerEvents {

    private SeamlessSleepServerEvents() {
    }

    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        SeamlessSleepServerConfig cfg = SeamlessSleepServerConfigManager.get();
        Services.NETWORK.sendToPlayers(
                player.level(),
                new ServerConfigSyncPayload(
                        cfg.sleepWeatherClearChancePercent,
                        cfg.sleepAnimationDurationMultiplier,
                        cfg.worldSleepAcceleration.mode,
                        cfg.worldSleepAcceleration.preset,
                        cfg.worldSleepAcceleration.randomTickAccelerationEnabled,
                        cfg.worldSleepAcceleration.processAccelerationEnabled,
                        cfg.worldSleepAcceleration.governorAggressiveness,
                        cfg.worldSleepAcceleration.natureFilterProfile,
                        cfg.worldSleepAcceleration.nature.baseRadiusChunks,
                        cfg.worldSleepAcceleration.nature.autoMinRadiusChunks,
                        cfg.worldSleepAcceleration.nature.baseRateFraction,
                        cfg.worldSleepAcceleration.nature.autoMinRateFraction,
                        cfg.worldSleepAcceleration.process.baseRadiusChunks,
                        cfg.worldSleepAcceleration.process.autoMinRadiusChunks,
                        cfg.worldSleepAcceleration.process.baseRateFraction,
                        cfg.worldSleepAcceleration.process.autoMinRateFraction
                )
        );
    }
}
