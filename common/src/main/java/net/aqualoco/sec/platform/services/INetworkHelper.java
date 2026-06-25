package net.aqualoco.sec.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// Loader-agnostic hooks for payload registration and server-to-client sends.
public interface INetworkHelper {

    void registerPayloads();

    void registerClientHandlers();

    void sendToPlayers(ServerLevel world, CustomPacketPayload payload);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToServer(CustomPacketPayload payload);

    boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type);

    boolean canSendToServer(CustomPacketPayload.Type<?> type);

    default boolean sendToPlayerIfSupported(ServerPlayer player, CustomPacketPayload payload) {
        if (!canSendToPlayer(player, payload.type())) {
            return false;
        }
        sendToPlayer(player, payload);
        return true;
    }

    default int sendToPlayersIfSupported(ServerLevel world, CustomPacketPayload payload) {
        int sent = 0;
        for (ServerPlayer player : world.players()) {
            if (sendToPlayerIfSupported(player, payload)) {
                sent++;
            }
        }
        return sent;
    }
}
