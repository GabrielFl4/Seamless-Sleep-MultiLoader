package net.aqualoco.sec.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;

// Loader-agnostic hooks for payload registration and server-to-client sends.
public interface INetworkHelper {

    void registerPayloads();

    void registerClientHandlers();

    void sendToPlayers(ServerLevel world, CustomPacketPayload payload);

    void sendToServer(CustomPacketPayload payload);
}
