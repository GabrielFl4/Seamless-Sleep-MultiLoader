package net.aqualoco.sec.network;

import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.handshake.ServerSeamlessClientPresenceManager;
import net.aqualoco.sec.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// Broadcasts the current server config snapshot to confirmed Seamless Sleep clients.
public final class ServerConfigSync {

    private ServerConfigSync() {
    }

    public static void sendToAll(MinecraftServer server, SeamlessSleepServerConfig config) {
        config.clamp();
        ServerConfigSyncPayload payload = createPayload(server, config);
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (ServerSeamlessClientPresenceManager.isConfirmed(player)) {
                    Services.NETWORK.sendToPlayerIfSupported(player, payload);
                }
            }
        }
    }

    public static void sendToPlayer(ServerPlayer player, SeamlessSleepServerConfig config) {
        if (!ServerSeamlessClientPresenceManager.isConfirmed(player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        config.clamp();
        Services.NETWORK.sendToPlayerIfSupported(player, createPayload(server, config));
    }

    private static ServerConfigSyncPayload createPayload(MinecraftServer server, SeamlessSleepServerConfig config) {
        return new ServerConfigSyncPayload(
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
                config.worldSleepAcceleration.processesSpeedPercent,
                config.betterDaysCompatibilityEnabled,
                config.worldSleepAcceleration.recheckIrrelevantNatureSectionsDuringAcceleration,
                config.worldSleepAcceleration.accelerationTelemetryEnabled,
                config.worldSleepAcceleration.vinesAndBambooAccelerationEnabled
        );
    }
}
