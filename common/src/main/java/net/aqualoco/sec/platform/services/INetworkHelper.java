package net.aqualoco.sec.platform.services;

import net.aqualoco.sec.network.SeamlessSleepPacket;
import net.minecraft.server.level.ServerLevel;

// Loader-agnostic hooks for payload registration and server-to-client sends.
public interface INetworkHelper {

    void registerPayloads();

    void registerClientHandlers();

    void sendToPlayers(ServerLevel world, SeamlessSleepPacket payload);
}
