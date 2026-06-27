package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Sends the local effective Vivecraft bed room Y offset to a Seamless server for lightweight render sync.
public record VivecraftBedOffsetC2SPayload(boolean active, double yOffset) implements CustomPacketPayload {

    public static final Type<VivecraftBedOffsetC2SPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "vivecraft_bed_offset_c2s"));

    public static final StreamCodec<FriendlyByteBuf, VivecraftBedOffsetC2SPayload> CODEC =
            CustomPacketPayload.codec(VivecraftBedOffsetC2SPayload::write, VivecraftBedOffsetC2SPayload::read);

    private static void write(VivecraftBedOffsetC2SPayload payload, FriendlyByteBuf buf) {
        buf.writeBoolean(payload.active());
        buf.writeDouble(payload.yOffset());
    }

    private static VivecraftBedOffsetC2SPayload read(FriendlyByteBuf buf) {
        return new VivecraftBedOffsetC2SPayload(buf.readBoolean(), buf.readDouble());
    }

    @Override
    public Type<VivecraftBedOffsetC2SPayload> type() {
        return ID;
    }
}
