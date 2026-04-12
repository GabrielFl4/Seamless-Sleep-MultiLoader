package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Sends the local managed-bed look to the server so sleeping orientation becomes authoritative.
public record BedLookSyncPayload(float yaw, float pitch) implements CustomPacketPayload {

    public static final Type<BedLookSyncPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "bed_look_sync"));

    public static final StreamCodec<FriendlyByteBuf, BedLookSyncPayload> CODEC =
            CustomPacketPayload.codec(BedLookSyncPayload::write, BedLookSyncPayload::read);

    private static void write(BedLookSyncPayload payload, FriendlyByteBuf buf) {
        buf.writeFloat(payload.yaw());
        buf.writeFloat(payload.pitch());
    }

    private static BedLookSyncPayload read(FriendlyByteBuf buf) {
        return new BedLookSyncPayload(buf.readFloat(), buf.readFloat());
    }

    @Override
    public Type<BedLookSyncPayload> type() {
        return ID;
    }
}
