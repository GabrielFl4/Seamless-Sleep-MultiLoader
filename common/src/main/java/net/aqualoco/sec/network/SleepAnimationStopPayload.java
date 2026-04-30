package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.sleep.SleepAnimationStopReason;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Packet that tells clients to finish or stop the active sleep transition.
public record SleepAnimationStopPayload(
        Identifier worldId,
        long sessionId,
        long finalDayTime,
        SleepAnimationStopReason reason
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SleepAnimationStopPayload> ID =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sleep_animation_stop")
            );

    public static final StreamCodec<FriendlyByteBuf, SleepAnimationStopPayload> CODEC =
            CustomPacketPayload.codec(SleepAnimationStopPayload::write, SleepAnimationStopPayload::read);

    private static void write(SleepAnimationStopPayload payload, FriendlyByteBuf buf) {
        buf.writeIdentifier(payload.worldId());
        buf.writeLong(payload.sessionId());
        buf.writeLong(payload.finalDayTime());
        buf.writeUtf(payload.reason().name());
    }

    private static SleepAnimationStopPayload read(FriendlyByteBuf buf) {
        Identifier worldId = buf.readIdentifier();
        long sessionId = buf.readLong();
        long finalDayTime = buf.readLong();
        SleepAnimationStopReason reason = readEnum(
                buf,
                SleepAnimationStopReason.class,
                SleepAnimationStopReason.CANCELLED_UNKNOWN
        );
        return new SleepAnimationStopPayload(worldId, sessionId, finalDayTime, reason);
    }

    @Override
    public Type<SleepAnimationStopPayload> type() {
        return ID;
    }

    private static <E extends Enum<E>> E readEnum(FriendlyByteBuf buf, Class<E> enumType, E fallback) {
        String name = buf.readUtf();
        try {
            return Enum.valueOf(enumType, name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
