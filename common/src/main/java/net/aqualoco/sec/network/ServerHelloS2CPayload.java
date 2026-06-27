package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerHelloS2CPayload(int protocolVersion,
                                    String modVersion,
                                    int featureFlags,
                                    int serverConfigRevision) implements CustomPacketPayload {
    public static final Type<ServerHelloS2CPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "server_hello"));

    public static final StreamCodec<FriendlyByteBuf, ServerHelloS2CPayload> CODEC =
            CustomPacketPayload.codec(ServerHelloS2CPayload::write, ServerHelloS2CPayload::read);

    private static void write(ServerHelloS2CPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.protocolVersion());
        buf.writeUtf(payload.modVersion());
        buf.writeVarInt(payload.featureFlags());
        buf.writeVarInt(payload.serverConfigRevision());
    }

    private static ServerHelloS2CPayload read(FriendlyByteBuf buf) {
        return new ServerHelloS2CPayload(
                buf.readVarInt(),
                buf.readUtf(128),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    @Override
    public Type<ServerHelloS2CPayload> type() {
        return ID;
    }
}
