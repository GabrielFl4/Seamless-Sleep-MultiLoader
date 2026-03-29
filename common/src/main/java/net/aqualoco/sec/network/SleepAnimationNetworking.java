package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// Small helper around sleep animation packet flow.
public final class SleepAnimationNetworking {

    private SleepAnimationNetworking() {
    }

    public static void initCommon() {
        Services.NETWORK.registerPayloads();
        Constants.info("Registered sleep animation payload types (S2C).");
    }

    public static void initClient() {
        Services.NETWORK.registerClientHandlers();
        Constants.info("Registered client handlers for sleep animation.");
    }

    public static void sendStart(ServerLevel world, SleepAnimationState state) {
        Identifier worldId = world.dimension().identifier();
        long startTime = state.getStartTimeOfDay();
        long endTime = state.getEndTimeOfDay();
        int duration = state.getDurationTicks();
        long startMillis = state.getStartMillis();

        SleepAnimationStartPayload payload = new SleepAnimationStartPayload(
                worldId, startTime, endTime, duration, startMillis
        );

        Services.NETWORK.sendToPlayers(world, payload);

        Constants.debug(
                "Sent sleep animation payload (start) to {} players ({} -> {}, {} ticks)",
                world.players().size(), startTime, endTime, duration
        );
    }

    public static void sendStop(ServerLevel world) {
        Identifier worldId = world.dimension().identifier();
        SleepAnimationStopPayload payload = new SleepAnimationStopPayload(worldId);

        int playerCount = 0;
        for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            Services.NETWORK.sendToPlayer(player, payload);
            playerCount++;
        }

        Constants.debug(
                "Sent sleep animation payload (stop) to {} players",
                playerCount
        );
    }
}
