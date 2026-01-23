package net.aqualoco.sec.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;

public interface INetworkHelper {

    void registerPayloads();

    void registerClientHandlers();

    void sendToPlayers(ServerLevel world, CustomPacketPayload payload);
}
