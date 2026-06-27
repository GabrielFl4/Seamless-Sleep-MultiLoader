package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Mirrors the vanilla sleep progress numbers to the custom bed HUD without relying on the action bar.
public record BedHudSleepProgressPayload(
        ResourceLocation worldId,
        int sleepingPlayers,
        int sleepersNeeded,
        boolean active
) implements CustomPacketPayload {

    public static final Type<BedHudSleepProgressPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "bed_hud_sleep_progress"));

    public static final StreamCodec<FriendlyByteBuf, BedHudSleepProgressPayload> CODEC =
            CustomPacketPayload.codec(BedHudSleepProgressPayload::write, BedHudSleepProgressPayload::read);

    private static void write(BedHudSleepProgressPayload payload, FriendlyByteBuf buf) {
        buf.writeResourceLocation(payload.worldId());
        buf.writeVarInt(payload.sleepingPlayers());
        buf.writeVarInt(payload.sleepersNeeded());
        buf.writeBoolean(payload.active());
    }

    private static BedHudSleepProgressPayload read(FriendlyByteBuf buf) {
        return new BedHudSleepProgressPayload(
                buf.readResourceLocation(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean()
        );
    }

    @Override
    public Type<BedHudSleepProgressPayload> type() {
        return ID;
    }
}
