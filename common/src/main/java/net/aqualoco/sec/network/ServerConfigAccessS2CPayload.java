package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerConfigAccessS2CPayload(boolean canEditServerConfig,
                                           int requiredPermissionLevel,
                                           int serverConfigRevision) implements CustomPacketPayload {
    public static final Type<ServerConfigAccessS2CPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "server_config_access"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigAccessS2CPayload> CODEC =
            CustomPacketPayload.codec(ServerConfigAccessS2CPayload::write, ServerConfigAccessS2CPayload::read);

    private static void write(ServerConfigAccessS2CPayload payload, FriendlyByteBuf buf) {
        buf.writeBoolean(payload.canEditServerConfig());
        buf.writeVarInt(payload.requiredPermissionLevel());
        buf.writeVarInt(payload.serverConfigRevision());
    }

    private static ServerConfigAccessS2CPayload read(FriendlyByteBuf buf) {
        return new ServerConfigAccessS2CPayload(
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    @Override
    public Type<ServerConfigAccessS2CPayload> type() {
        return ID;
    }
}
