package net.aqualoco.sec.platform;

import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.aqualoco.sec.platform.services.INetworkHelper;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// Fabric networking bridge for payload registration and player sends.
public class FabricNetworkHelper implements INetworkHelper {

    @Override
    public void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(
                SleepAnimationStartPayload.ID,
                SleepAnimationStartPayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
                SleepAnimationStopPayload.ID,
                SleepAnimationStopPayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
                ServerConfigSyncPayload.ID,
                ServerConfigSyncPayload.CODEC
        );
    }

    @Override
    public void registerClientHandlers() {
        FabricClientNetworkHandler.register();
    }

    @Override
    public void sendToPlayers(ServerLevel world, CustomPacketPayload payload) {
        for (ServerPlayer player : world.players()) {
            this.sendToPlayer(player, payload);
        }
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
