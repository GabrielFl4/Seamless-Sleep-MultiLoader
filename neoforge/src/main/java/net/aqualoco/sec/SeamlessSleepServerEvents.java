package net.aqualoco.sec;

import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

final class SeamlessSleepServerEvents {

    private SeamlessSleepServerEvents() {
    }

    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        SeamlessSleepServerConfig cfg = SeamlessSleepServerConfigManager.get();
        Services.NETWORK.sendToPlayer(
                player,
                new ServerConfigSyncPayload(
                        cfg.sleepWeatherClearChancePercent,
                        cfg.sleepAnimationDurationMultiplier
                )
        );
    }
}
