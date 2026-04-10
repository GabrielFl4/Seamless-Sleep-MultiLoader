package net.aqualoco.sec.network;

import net.aqualoco.sec.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;

// Sends bed HUD sleep-progress state to clients so bed users do not depend on the vanilla action bar timing.
public final class BedHudNetworking {

    private BedHudNetworking() {
    }

    public static void sendSleepProgress(ServerLevel world, int sleepingPlayers, int sleepersNeeded) {
        Identifier worldId = world.dimension().identifier();
        Services.NETWORK.sendToPlayers(world, new BedHudSleepProgressPayload(worldId, sleepingPlayers, sleepersNeeded, true));
    }

    public static void clearSleepProgress(ServerLevel world) {
        Identifier worldId = world.dimension().identifier();
        Services.NETWORK.sendToPlayers(world, new BedHudSleepProgressPayload(worldId, 0, 0, false));
    }

    public static void syncSleepProgress(ServerLevel world) {
        if (!world.canSleepThroughNights()) {
            clearSleepProgress(world);
            return;
        }

        int percentage = world.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        if (percentage <= 0) {
            clearSleepProgress(world);
            return;
        }

        int activePlayers = 0;
        int sleepingPlayers = 0;
        for (ServerPlayer player : world.players()) {
            if (player.isSpectator()) {
                continue;
            }
            activePlayers++;
            if (player.isSleeping()) {
                sleepingPlayers++;
            }
        }

        if (activePlayers <= 0 || sleepingPlayers <= 0) {
            clearSleepProgress(world);
            return;
        }

        int sleepersNeeded = Math.max(1, Mth.ceil(activePlayers * percentage / 100.0F));
        if (sleepingPlayers >= sleepersNeeded) {
            clearSleepProgress(world);
            return;
        }

        sendSleepProgress(world, sleepingPlayers, sleepersNeeded);
    }
}
