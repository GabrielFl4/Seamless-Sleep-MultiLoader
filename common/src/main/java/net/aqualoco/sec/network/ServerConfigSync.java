package net.aqualoco.sec.network;

import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

// Broadcasts the current server config snapshot to every online client.
public final class ServerConfigSync {

    private ServerConfigSync() {
    }

    public static void sendToAll(MinecraftServer server, SeamlessSleepServerConfig config) {
        ServerConfigSyncPayload payload = new ServerConfigSyncPayload(
                config.sleepWeatherClearChancePercent,
                config.sleepAnimationDurationMultiplier,
                config.worldSleepAcceleration.mode,
                config.worldSleepAcceleration.preset,
                config.worldSleepAcceleration.randomTickAccelerationEnabled,
                config.worldSleepAcceleration.processAccelerationEnabled,
                config.worldSleepAcceleration.governorAggressiveness,
                config.worldSleepAcceleration.natureFilterProfile,
                config.worldSleepAcceleration.nature.baseRadiusChunks,
                config.worldSleepAcceleration.nature.autoMinRadiusChunks,
                config.worldSleepAcceleration.nature.baseRateFraction,
                config.worldSleepAcceleration.nature.autoMinRateFraction,
                config.worldSleepAcceleration.process.baseRadiusChunks,
                config.worldSleepAcceleration.process.autoMinRadiusChunks,
                config.worldSleepAcceleration.process.baseRateFraction,
                config.worldSleepAcceleration.process.autoMinRateFraction
        );
        for (ServerLevel level : server.getAllLevels()) {
            Services.NETWORK.sendToPlayers(level, payload);
        }
    }
}
