package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.sleep.SleepAnimationMode;
import net.aqualoco.sec.sleep.SleepAnimationPhase;
import net.aqualoco.sec.sleep.SleepAnimationSoundMode;
import net.aqualoco.sec.sleep.SleepAnimationVisualContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Start/snapshot packet with the authoritative server gameTime timeline.
public record SleepAnimationStartPayload(
        ResourceLocation worldId,
        long sessionId,
        long sequenceId,
        SleepAnimationMode mode,
        SleepAnimationPhase phase,
        SleepAnimationVisualContext visualContext,
        SleepAnimationSoundMode soundMode,
        long startTimeOfDay,
        long endTimeOfDay,
        int durationTicks,
        long serverStartGameTime,
        long serverGameTimeAtSend,
        long currentDayTime
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SleepAnimationStartPayload> ID =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sleep_animation_start")
            );

    public static final StreamCodec<FriendlyByteBuf, SleepAnimationStartPayload> CODEC =
            CustomPacketPayload.codec(SleepAnimationStartPayload::write, SleepAnimationStartPayload::read);
    private static final int LEGACY_REMAINING_BYTES_AFTER_PHASE = Long.BYTES * 5 + Integer.BYTES;

    private static void write(SleepAnimationStartPayload payload, FriendlyByteBuf buf) {
        buf.writeResourceLocation(payload.worldId());
        buf.writeLong(payload.sessionId());
        buf.writeLong(payload.sequenceId());
        buf.writeUtf(payload.mode().name());
        buf.writeUtf(payload.phase().name());
        buf.writeUtf(payload.visualContext().name());
        buf.writeUtf(payload.soundMode().name());
        buf.writeLong(payload.startTimeOfDay());
        buf.writeLong(payload.endTimeOfDay());
        buf.writeInt(payload.durationTicks());
        buf.writeLong(payload.serverStartGameTime());
        buf.writeLong(payload.serverGameTimeAtSend());
        buf.writeLong(payload.currentDayTime());
    }

    private static SleepAnimationStartPayload read(FriendlyByteBuf buf) {
        ResourceLocation worldId = buf.readResourceLocation();
        long sessionId = buf.readLong();
        long sequenceId = buf.readLong();
        SleepAnimationMode mode = readEnum(buf, SleepAnimationMode.class, SleepAnimationMode.NORMAL_SLEEP);
        SleepAnimationPhase phase = readEnum(buf, SleepAnimationPhase.class, SleepAnimationPhase.RUNNING);
        if (buf.readableBytes() == LEGACY_REMAINING_BYTES_AFTER_PHASE) {
            return readLegacyBody(buf, worldId, sessionId, sequenceId, mode, phase);
        }

        SleepAnimationVisualContext visualContext = readEnum(
                buf,
                SleepAnimationVisualContext.class,
                SleepAnimationVisualContext.NIGHT
        );
        SleepAnimationSoundMode soundMode = buf.readableBytes() == LEGACY_REMAINING_BYTES_AFTER_PHASE
                ? SleepAnimationSoundMode.MUTED
                : SleepAnimationSoundMode.canonical(readEnum(buf, SleepAnimationSoundMode.class, SleepAnimationSoundMode.MUTED));
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
                visualContext,
                soundMode,
                startTime,
                endTime,
                duration,
                serverStartGameTime,
                serverGameTimeAtSend,
                currentDayTime
        );
    }

    private static SleepAnimationStartPayload readLegacyBody(FriendlyByteBuf buf,
                                                             ResourceLocation worldId,
                                                             long sessionId,
                                                             long sequenceId,
                                                             SleepAnimationMode mode,
                                                             SleepAnimationPhase phase) {
        long startTime = buf.readLong();
        long endTime = buf.readLong();
        int duration = buf.readInt();
        long serverStartGameTime = buf.readLong();
        long serverGameTimeAtSend = buf.readLong();
        long currentDayTime = buf.readLong();
        SleepAnimationVisualContext visualContext = mode == SleepAnimationMode.MADE_IN_HEAVEN_BED
                || mode == SleepAnimationMode.COMMAND_TIMELAPSE
                ? SleepAnimationVisualContext.MADE_IN_HEAVEN
                : SleepAnimationVisualContext.NIGHT;
        return new SleepAnimationStartPayload(
                worldId,
                sessionId,
                sequenceId,
                mode,
                phase,
                visualContext,
                SleepAnimationSoundMode.MUTED,
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
