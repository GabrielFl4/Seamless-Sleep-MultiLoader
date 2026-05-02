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
        config.clamp();
        ServerConfigSyncPayload payload = new ServerConfigSyncPayload(
                config.sleepWeatherClearChancePercent,
                config.sleepAnimationDurationMultiplier,
                config.fallAsleepDelayTicks,
                config.overrideOverlayText,
                config.overlayCustomText,
                config.sleepEligibility,
                config.madeInHeavenChancePercent,
                Math.max(1, server.getPlayerList().getSimulationDistance()),
                config.worldSleepAcceleration.mode,
                config.worldSleepAcceleration.automaticMode,
                config.worldSleepAcceleration.playersAffected,
                config.worldSleepAcceleration.manualAccelerationRadiusChunks,
                config.worldSleepAcceleration.manualAccelerationSpeedPercent,
                config.worldSleepAcceleration.grassAndFoliageAccelerationEnabled,
                config.worldSleepAcceleration.cropsAndSaplingsAccelerationEnabled,
                config.worldSleepAcceleration.kelpAccelerationEnabled,
                config.worldSleepAcceleration.vanillaOnlyAcceleration,
                config.worldSleepAcceleration.processesAccelerationEnabled,
                config.worldSleepAcceleration.processesSpeedPercent
        );
        for (ServerLevel level : server.getAllLevels()) {
            Services.NETWORK.sendToPlayers(level, payload);
        }
    }
}
