package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.sleep.SleepAnimationMode;
import net.aqualoco.sec.sleep.SleepAnimationPhase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Start/snapshot packet with the authoritative server gameTime timeline.
public record SleepAnimationStartPayload(
        Identifier worldId,
        long sessionId,
        long sequenceId,
        SleepAnimationMode mode,
        SleepAnimationPhase phase,
        long startTimeOfDay,
        long endTimeOfDay,
        int durationTicks,
        long serverStartGameTime,
        long serverGameTimeAtSend,
        long currentDayTime
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SleepAnimationStartPayload> ID =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sleep_animation_start")
            );

    public static final StreamCodec<FriendlyByteBuf, SleepAnimationStartPayload> CODEC =
            CustomPacketPayload.codec(SleepAnimationStartPayload::write, SleepAnimationStartPayload::read);

    private static void write(SleepAnimationStartPayload payload, FriendlyByteBuf buf) {
        buf.writeIdentifier(payload.worldId());
        buf.writeLong(payload.sessionId());
        buf.writeLong(payload.sequenceId());
        buf.writeUtf(payload.mode().name());
        buf.writeUtf(payload.phase().name());
        buf.writeLong(payload.startTimeOfDay());
        buf.writeLong(payload.endTimeOfDay());
        buf.writeInt(payload.durationTicks());
        buf.writeLong(payload.serverStartGameTime());
        buf.writeLong(payload.serverGameTimeAtSend());
        buf.writeLong(payload.currentDayTime());
    }

    private static SleepAnimationStartPayload read(FriendlyByteBuf buf) {
        Identifier worldId = buf.readIdentifier();
        long sessionId = buf.readLong();
        long sequenceId = buf.readLong();
        SleepAnimationMode mode = readEnum(buf, SleepAnimationMode.class, SleepAnimationMode.NORMAL_SLEEP);
        SleepAnimationPhase phase = readEnum(buf, SleepAnimationPhase.class, SleepAnimationPhase.RUNNING);
        long startTime = buf.readLong();
        long endTime = buf.readLong();
        int duration = buf.readInt();
        long serverStartGameTime = buf.readLong();
        long serverGameTimeAtSend = buf.readLong();
        long currentDayTime = buf.readLong();
        return new SleepAnimationStartPayload(
                worldId,
                sessionId,
                sequenceId,
                mode,
                phase,
                startTime,
                endTime,
                duration,
                serverStartGameTime,
                serverGameTimeAtSend,
                currentDayTime
        );
    }

    @Override
    public Type<SleepAnimationStartPayload> type() {
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
