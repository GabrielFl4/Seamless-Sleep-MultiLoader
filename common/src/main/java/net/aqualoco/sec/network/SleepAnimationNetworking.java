package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.handshake.ServerSeamlessClientPresenceManager;
import net.aqualoco.sec.platform.Services;
import net.aqualoco.sec.sleep.SleepAnimationStates;
import net.aqualoco.sec.sleep.SleepDimensionSupport;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.aqualoco.sec.sleep.SleepAnimationStopReason;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// Small helper around sleep animation packet flow.
public final class SleepAnimationNetworking {
    private static boolean commonInitialized;
    private static boolean clientInitialized;

    private SleepAnimationNetworking() {
    }

    public static void initCommon() {
        if (commonInitialized) {
            return;
        }
        commonInitialized = true;
        Services.NETWORK.registerPayloads();
        Constants.info("Registered Seamless Sleep payload types.");
    }

    public static void initClient() {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;
        Services.NETWORK.registerClientHandlers();
        Constants.info("Registered client handlers for sleep animation.");
    }

    public static void sendStart(ServerLevel world, SleepAnimationState state) {
        SleepAnimationStartPayload payload = createStartPayload(world, state);
        int sent = sendToConfirmedPlayers(world, payload);

        Constants.debug(
                "Sent sleep animation payload (start session {}) to {} players ({} -> {}, {} ticks)",
                state.getSessionId(),
                sent,
                state.getStartTimeOfDay(),
                state.getEndTimeOfDay(),
                state.getDurationTicks()
        );
    }

    public static void sendSnapshotToPlayer(ServerPlayer player, ServerLevel world, SleepAnimationState state) {
        if (!ServerSeamlessClientPresenceManager.isConfirmed(player)) {
            return;
        }
        if (!state.isActive()
                || state.hasVisualFinishBeenAnnounced()
                || !player.level().dimension().equals(world.dimension())) {
            return;
        }

        SleepAnimationStartPayload payload = createStartPayload(world, state);
        if (!Services.NETWORK.sendToPlayerIfSupported(player, payload)) {
            return;
        }

        Constants.debug(
                "Sent sleep animation snapshot (session {}) to {}",
                state.getSessionId(),
                player.getGameProfile().name()
        );
    }

    public static void sendActiveSnapshotToPlayer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel world)) {
            return;
        }
        SleepAnimationState state = SleepAnimationStates.getIfPresent(world);
        if (state == null || !state.isActive()) {
            return;
        }
        if (!SleepDimensionSupport.supportsSleepAnimation(world)) {
            return;
        }

        sendSnapshotToPlayer(player, world, state);
    }

    private static SleepAnimationStartPayload createStartPayload(ServerLevel world, SleepAnimationState state) {
        Identifier worldId = world.dimension().identifier();
        return new SleepAnimationStartPayload(
                worldId,
                state.getSessionId(),
                state.getSequenceId(),
                state.getMode(),
                state.getPhase(),
                state.getVisualContext(),
                state.getSoundMode(),
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

        int sent = sendToConfirmedPlayers(world, payload);

        Constants.debug(
                "Sent sleep animation payload ({} session {}) to {} players",
                reason,
                sessionId,
                sent
        );
    }

    private static int sendToConfirmedPlayers(ServerLevel world, CustomPacketPayload payload) {
        int sent = 0;
        for (ServerPlayer player : world.players()) {
            if (ServerSeamlessClientPresenceManager.isConfirmed(player)
                    && Services.NETWORK.sendToPlayerIfSupported(player, payload)) {
                sent++;
            }
        }
        return sent;
    }
}
