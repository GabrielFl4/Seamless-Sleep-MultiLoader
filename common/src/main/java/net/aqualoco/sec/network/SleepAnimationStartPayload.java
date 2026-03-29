package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Start packet with world id and timing window for client interpolation.
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

    public static final StreamCodec<RegistryFriendlyByteBuf, SleepAnimationStartPayload> CODEC =
            CustomPacketPayload.codec(SleepAnimationStartPayload::write, SleepAnimationStartPayload::read);

    private static void write(SleepAnimationStartPayload payload, RegistryFriendlyByteBuf buf) {
        buf.writeIdentifier(payload.worldId());
        buf.writeLong(payload.startTimeOfDay());
        buf.writeLong(payload.endTimeOfDay());
        buf.writeInt(payload.durationTicks());
        buf.writeLong(payload.startMillis());
    }

    private static SleepAnimationStartPayload read(RegistryFriendlyByteBuf buf) {
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
