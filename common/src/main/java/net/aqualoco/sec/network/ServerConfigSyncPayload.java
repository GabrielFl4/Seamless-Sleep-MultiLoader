package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerConfigSyncPayload(boolean sleepClearsWeather) implements CustomPacketPayload {

    public static final Type<ServerConfigSyncPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "server_config_sync"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigSyncPayload> CODEC =
            CustomPacketPayload.codec(ServerConfigSyncPayload::write, ServerConfigSyncPayload::read);

    private static void write(ServerConfigSyncPayload payload, FriendlyByteBuf buf) {
        buf.writeBoolean(payload.sleepClearsWeather());
    }

    private static ServerConfigSyncPayload read(FriendlyByteBuf buf) {
        return new ServerConfigSyncPayload(buf.readBoolean());
    }

    @Override
    public Type<ServerConfigSyncPayload> type() {
        return ID;
    }
}
