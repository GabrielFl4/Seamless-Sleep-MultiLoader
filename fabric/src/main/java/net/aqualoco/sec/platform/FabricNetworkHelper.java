package net.aqualoco.sec.platform;

import net.aqualoco.sec.network.SeamlessSleepPacket;
import net.aqualoco.sec.platform.services.INetworkHelper;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// Fabric networking bridge for payload registration and player sends.
public class FabricNetworkHelper implements INetworkHelper {

    @Override
    public void registerPayloads() {
        // 1.20.1 Fabric networking uses channel identifiers + raw FriendlyByteBuf.
    }

    @Override
    public void registerClientHandlers() {
        FabricClientNetworkHandler.register();
    }

    @Override
    public void sendToPlayers(ServerLevel world, SeamlessSleepPacket payload) {
        for (ServerPlayer player : world.players()) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            payload.write(buf);
            ServerPlayNetworking.send(player, payload.id(), buf);
        }
    }
}
