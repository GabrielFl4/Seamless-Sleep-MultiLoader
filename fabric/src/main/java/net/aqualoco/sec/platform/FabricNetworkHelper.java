package net.aqualoco.sec.platform;

import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.aqualoco.sec.platform.services.INetworkHelper;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class FabricNetworkHelper implements INetworkHelper {

    @Override
    public void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(
                SleepAnimationStartPayload.ID,
                SleepAnimationStartPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                SleepAnimationStopPayload.ID,
                SleepAnimationStopPayload.CODEC
        );
    }

    @Override
    public void registerClientHandlers() {
        FabricClientNetworkHandler.register();
    }

    @Override
    public void sendToPlayers(ServerLevel world, CustomPacketPayload payload) {
        for (ServerPlayer player : world.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
