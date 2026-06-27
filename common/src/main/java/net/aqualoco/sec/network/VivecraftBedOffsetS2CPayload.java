package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

// Mirrors a VR player's effective bed room Y offset to clients rendering that player's Vivecraft avatar.
public record VivecraftBedOffsetS2CPayload(UUID playerId, boolean active, double yOffset) implements CustomPacketPayload {

    public static final Type<VivecraftBedOffsetS2CPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "vivecraft_bed_offset_s2c"));

    public static final StreamCodec<FriendlyByteBuf, VivecraftBedOffsetS2CPayload> CODEC =
            CustomPacketPayload.codec(VivecraftBedOffsetS2CPayload::write, VivecraftBedOffsetS2CPayload::read);

    private static void write(VivecraftBedOffsetS2CPayload payload, FriendlyByteBuf buf) {
        buf.writeUUID(payload.playerId());
        buf.writeBoolean(payload.active());
        buf.writeDouble(payload.yOffset());
    }

    private static VivecraftBedOffsetS2CPayload read(FriendlyByteBuf buf) {
        return new VivecraftBedOffsetS2CPayload(buf.readUUID(), buf.readBoolean(), buf.readDouble());
    }

    @Override
    public Type<VivecraftBedOffsetS2CPayload> type() {
        return ID;
    }
}
