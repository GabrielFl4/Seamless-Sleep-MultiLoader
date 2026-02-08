package net.aqualoco.sec;

import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
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

        SeamlessSleepServerConfig cfg = SeamlessSleepServerConfigManager.get();
        Services.NETWORK.sendToPlayers(
                player.serverLevel(),
                new ServerConfigSyncPayload(
                        cfg.sleepClearsWeather,
                        cfg.sleepAnimationDurationMultiplier
                )
        );
    }
}
