package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Packet used to mirror server config values on connected clients.
public record ServerConfigSyncPayload(int sleepWeatherClearChancePercent,
                                      double sleepAnimationDurationMultiplier) implements CustomPacketPayload {

    public static final Type<ServerConfigSyncPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "server_config_sync"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigSyncPayload> CODEC =
            CustomPacketPayload.codec(ServerConfigSyncPayload::write, ServerConfigSyncPayload::read);

    private static void write(ServerConfigSyncPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.sleepWeatherClearChancePercent());
        buf.writeDouble(payload.sleepAnimationDurationMultiplier());
    }

    private static ServerConfigSyncPayload read(FriendlyByteBuf buf) {
        int weatherClearChancePercent = buf.readVarInt();
        double durationMultiplier = buf.readDouble();
        return new ServerConfigSyncPayload(weatherClearChancePercent, durationMultiplier);
    }

    @Override
    public Type<ServerConfigSyncPayload> type() {
        return ID;
    }
}
