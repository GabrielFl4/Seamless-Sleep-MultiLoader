package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientHelloC2SPayload(int protocolVersion,
                                    String modVersion,
                                    int featureFlags) implements CustomPacketPayload {
    public static final Type<ClientHelloC2SPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "client_hello"));

    public static final StreamCodec<FriendlyByteBuf, ClientHelloC2SPayload> CODEC =
            CustomPacketPayload.codec(ClientHelloC2SPayload::write, ClientHelloC2SPayload::read);

    private static void write(ClientHelloC2SPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.protocolVersion());
        buf.writeUtf(payload.modVersion());
        buf.writeVarInt(payload.featureFlags());
    }

    private static ClientHelloC2SPayload read(FriendlyByteBuf buf) {
        return new ClientHelloC2SPayload(
                buf.readVarInt(),
                buf.readUtf(128),
                buf.readVarInt()
        );
    }

    @Override
    public Type<ClientHelloC2SPayload> type() {
        return ID;
    }
}
