package net.aqualoco.sec;

import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// NeoForge-side server event hooks used to sync config when players log in.
final class SeamlessSleepServerEvents {

    private SeamlessSleepServerEvents() {
    }

    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().getServer() == null) {
            return;
        }

        SeamlessSleepServerConfig cfg = SeamlessSleepServerConfigManager.get();
        Services.NETWORK.sendToPlayers(
                player.level(),
                new ServerConfigSyncPayload(
                        cfg.sleepWeatherClearChancePercent,
                        cfg.sleepAnimationDurationMultiplier,
                        cfg.fallAsleepDelayTicks,
                        cfg.overrideOverlayText,
                        cfg.overlayCustomText,
                        cfg.sleepEligibility,
                        cfg.madeInHeavenChancePercent,
                        Math.max(1, player.level().getServer().getPlayerList().getSimulationDistance()),
                        cfg.worldSleepAcceleration.mode,
                        cfg.worldSleepAcceleration.automaticMode,
                        cfg.worldSleepAcceleration.playersAffected,
                        cfg.worldSleepAcceleration.manualAccelerationRadiusChunks,
                        cfg.worldSleepAcceleration.manualAccelerationSpeedPercent,
                        cfg.worldSleepAcceleration.grassAndFoliageAccelerationEnabled,
                        cfg.worldSleepAcceleration.cropsAndSaplingsAccelerationEnabled,
                        cfg.worldSleepAcceleration.kelpAccelerationEnabled,
                        cfg.worldSleepAcceleration.vanillaOnlyAcceleration,
                        cfg.worldSleepAcceleration.processesAccelerationEnabled,
                        cfg.worldSleepAcceleration.processesSpeedPercent
                )
        );
        SleepAnimationNetworking.sendActiveSnapshotToPlayer(player);
    }

    static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SleepAnimationNetworking.sendActiveSnapshotToPlayer(player);
        }
    }

    static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SleepAnimationNetworking.sendActiveSnapshotToPlayer(player);
        }
    }
}
