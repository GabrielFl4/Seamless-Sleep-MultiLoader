package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerConfigAccessRequestC2SPayload() implements CustomPacketPayload {
    public static final Type<ServerConfigAccessRequestC2SPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "server_config_access_request"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigAccessRequestC2SPayload> CODEC =
            CustomPacketPayload.codec(ServerConfigAccessRequestC2SPayload::write, ServerConfigAccessRequestC2SPayload::read);

    private static void write(ServerConfigAccessRequestC2SPayload payload, FriendlyByteBuf buf) {
    }

    private static ServerConfigAccessRequestC2SPayload read(FriendlyByteBuf buf) {
        return new ServerConfigAccessRequestC2SPayload();
    }

    @Override
    public Type<ServerConfigAccessRequestC2SPayload> type() {
        return ID;
    }
}
