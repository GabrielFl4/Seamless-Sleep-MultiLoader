package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerConfigUpdateResultS2CPayload(boolean success,
                                                 int serverConfigRevision,
                                                 ServerConfigUpdateStatus status,
                                                 String message) implements CustomPacketPayload {
    private static final int MAX_MESSAGE_LENGTH = 256;

    public static final Type<ServerConfigUpdateResultS2CPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "server_config_update_result"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigUpdateResultS2CPayload> CODEC =
            CustomPacketPayload.codec(ServerConfigUpdateResultS2CPayload::write, ServerConfigUpdateResultS2CPayload::read);

    private static void write(ServerConfigUpdateResultS2CPayload payload, FriendlyByteBuf buf) {
        buf.writeBoolean(payload.success());
        buf.writeVarInt(payload.serverConfigRevision());
        buf.writeUtf(payload.status().name());
        buf.writeUtf(payload.message() == null ? "" : payload.message(), MAX_MESSAGE_LENGTH);
    }

    private static ServerConfigUpdateResultS2CPayload read(FriendlyByteBuf buf) {
        boolean success = buf.readBoolean();
        int revision = buf.readVarInt();
        ServerConfigUpdateStatus status = readStatus(buf);
        String message = buf.readUtf(MAX_MESSAGE_LENGTH);
        return new ServerConfigUpdateResultS2CPayload(success, revision, status, message);
    }

    private static ServerConfigUpdateStatus readStatus(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        try {
            return ServerConfigUpdateStatus.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return ServerConfigUpdateStatus.INVALID_VALUE;
        }
    }

    @Override
    public Type<ServerConfigUpdateResultS2CPayload> type() {
        return ID;
    }
}
