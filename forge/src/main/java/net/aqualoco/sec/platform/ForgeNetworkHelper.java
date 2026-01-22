package net.aqualoco.sec.platform;

import net.aqualoco.sec.platform.services.INetworkHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;

public class ForgeNetworkHelper implements INetworkHelper {

    @Override
    public void registerPayloads() {
    }

    @Override
    public void registerClientHandlers() {
    }

    @Override
    public void sendToPlayers(ServerLevel world, CustomPacketPayload payload) {
    }
}
