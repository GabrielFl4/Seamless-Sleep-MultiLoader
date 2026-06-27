package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Mirrors the client's active VR state to a Seamless server without requiring Vivecraft server-side.
public record VivecraftVrStatePayload(boolean active) implements CustomPacketPayload {

    public static final Type<VivecraftVrStatePayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "vivecraft_vr_state"));

    public static final StreamCodec<FriendlyByteBuf, VivecraftVrStatePayload> CODEC =
            CustomPacketPayload.codec(VivecraftVrStatePayload::write, VivecraftVrStatePayload::read);

    private static void write(VivecraftVrStatePayload payload, FriendlyByteBuf buf) {
        buf.writeBoolean(payload.active());
    }

    private static VivecraftVrStatePayload read(FriendlyByteBuf buf) {
        return new VivecraftVrStatePayload(buf.readBoolean());
    }

    @Override
    public Type<VivecraftVrStatePayload> type() {
        return ID;
    }
}
