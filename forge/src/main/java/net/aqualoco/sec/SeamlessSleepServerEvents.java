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
                        cfg.sleepClearsWeather,
                        cfg.sleepAnimationDurationMultiplier
                )
        );
    }
}
