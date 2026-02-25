package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Packet that starts the client sleep transition with timing and world data.
public record SleepAnimationStartPayload(
        Identifier worldId,
        long startTimeOfDay,
        long endTimeOfDay,
        int durationTicks,
        long startMillis
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SleepAnimationStartPayload> ID =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sleep_animation_start")
            );

    public static final StreamCodec<FriendlyByteBuf, SleepAnimationStartPayload> CODEC =
            CustomPacketPayload.codec(SleepAnimationStartPayload::write, SleepAnimationStartPayload::read);

    private static void write(SleepAnimationStartPayload payload, FriendlyByteBuf buf) {
        buf.writeIdentifier(payload.worldId());
        buf.writeLong(payload.startTimeOfDay());
        buf.writeLong(payload.endTimeOfDay());
        buf.writeInt(payload.durationTicks());
        buf.writeLong(payload.startMillis());
    }

    private static SleepAnimationStartPayload read(FriendlyByteBuf buf) {
        Identifier worldId = buf.readIdentifier();
        long startTime = buf.readLong();
        long endTime = buf.readLong();
        int duration = buf.readInt();
        long startMillis = buf.readLong();
        return new SleepAnimationStartPayload(worldId, startTime, endTime, duration, startMillis);
    }

    @Override
    public Type<SleepAnimationStartPayload> type() {
        return ID;
    }
}
