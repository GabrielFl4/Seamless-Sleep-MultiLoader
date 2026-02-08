package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet used to mirror server config values on connected clients.
public record ServerConfigSyncPayload(boolean sleepClearsWeather,
                                      double sleepAnimationDurationMultiplier) implements CustomPacketPayload {

    public static final Type<ServerConfigSyncPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "server_config_sync"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigSyncPayload> CODEC =
            CustomPacketPayload.codec(ServerConfigSyncPayload::write, ServerConfigSyncPayload::read);

    private static void write(ServerConfigSyncPayload payload, FriendlyByteBuf buf) {
        buf.writeBoolean(payload.sleepClearsWeather());
        buf.writeDouble(payload.sleepAnimationDurationMultiplier());
    }

    private static ServerConfigSyncPayload read(FriendlyByteBuf buf) {
        boolean clearsWeather = buf.readBoolean();
        double durationMultiplier = buf.readDouble();
        return new ServerConfigSyncPayload(clearsWeather, durationMultiplier);
    }

    @Override
    public Type<ServerConfigSyncPayload> type() {
        return ID;
    }
}
