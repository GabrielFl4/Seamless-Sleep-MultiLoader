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
                config.sleepClearsWeather,
                config.sleepAnimationDurationMultiplier
        );
        for (ServerLevel level : server.getAllLevels()) {
            Services.NETWORK.sendToPlayers(level, payload);
        }
    }
}
