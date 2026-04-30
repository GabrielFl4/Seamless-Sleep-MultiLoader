package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.SeamlessSleepCommon;
import net.aqualoco.sec.platform.Services;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.aqualoco.sec.sleep.SleepAnimationStopReason;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

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
        SleepAnimationStartPayload payload = createStartPayload(world, state);
        Services.NETWORK.sendToPlayers(world, payload);

        Constants.debug(
                "Sent sleep animation payload (start session {}) to {} players ({} -> {}, {} ticks)",
                state.getSessionId(),
                world.players().size(),
                state.getStartTimeOfDay(),
                state.getEndTimeOfDay(),
                state.getDurationTicks()
        );
    }

    public static void sendSnapshotToPlayer(ServerPlayer player, ServerLevel world, SleepAnimationState state) {
        if (!state.isActive() || !player.level().dimension().equals(world.dimension())) {
            return;
        }

        SleepAnimationStartPayload payload = createStartPayload(world, state);
        Services.NETWORK.sendToPlayer(player, payload);

        Constants.debug(
                "Sent sleep animation snapshot (session {}) to {}",
                state.getSessionId(),
                player.getGameProfile().name()
        );
    }

    public static void sendActiveSnapshotToPlayer(ServerPlayer player) {
        if (!player.level().dimension().equals(Level.OVERWORLD)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        sendSnapshotToPlayer(player, overworld, SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION);
    }

    private static SleepAnimationStartPayload createStartPayload(ServerLevel world, SleepAnimationState state) {
        Identifier worldId = world.dimension().identifier();
        return new SleepAnimationStartPayload(
                worldId,
                state.getSessionId(),
                state.getSequenceId(),
                state.getMode(),
                state.getPhase(),
                state.getStartTimeOfDay(),
                state.getEndTimeOfDay(),
                state.getDurationTicks(),
                state.getServerStartGameTime(),
                world.getGameTime(),
                state.getLastAppliedDayTime()
        );
    }

    public static void sendStop(ServerLevel world, SleepAnimationState state, SleepAnimationStopReason reason) {
        sendStop(world, state.getSessionId(), state.getLastAppliedDayTime(), reason);
    }

    public static void sendFinish(ServerLevel world, SleepAnimationState state) {
        sendStop(world, state.getSessionId(), state.getEndTimeOfDay(), SleepAnimationStopReason.FINISHED);
    }

    public static void sendStop(ServerLevel world, long sessionId, long finalDayTime, SleepAnimationStopReason reason) {
        Identifier worldId = world.dimension().identifier();
        SleepAnimationStopPayload payload = new SleepAnimationStopPayload(worldId, sessionId, finalDayTime, reason);

        Services.NETWORK.sendToPlayers(world, payload);

        Constants.debug(
                "Sent sleep animation payload ({} session {}) to {} players",
                reason,
                sessionId,
                world.players().size()
        );
    }
}
