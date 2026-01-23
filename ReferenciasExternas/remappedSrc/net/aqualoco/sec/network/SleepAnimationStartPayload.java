package net.aqualoco.sec.network;

import net.aqualoco.sec.AquaSec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload S2C para iniciar a animação de sono no client.
 */
public record SleepAnimationStartPayload(
        ResourceLocation worldId,
        long startTimeOfDay,
        long endTimeOfDay,
        int durationTicks,
        long startMillis
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SleepAnimationStartPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AquaSec.MOD_ID, "sleep_animation_start"));

    // Codec baseado em PacketByteBuf, usando a fábrica do CustomPayload
    public static final StreamCodec<FriendlyByteBuf, SleepAnimationStartPayload> CODEC =
            CustomPacketPayload.codec(SleepAnimationStartPayload::write, SleepAnimationStartPayload::read);

    // ENCODER: value first. Assinatura precisa ser
    // (SleepAnimationStartPayload value, PacketByteBuf buf)
    // para casar com ValueFirstEncoder<B, T>.
    private static void write(SleepAnimationStartPayload payload, FriendlyByteBuf buf) {
        buf.writeResourceLocation(payload.worldId());
        buf.writeLong(payload.startTimeOfDay());
        buf.writeLong(payload.endTimeOfDay());
        buf.writeInt(payload.durationTicks());
        buf.writeLong(payload.startMillis());
    }

    // DECODER: T read(B buf).
    private static SleepAnimationStartPayload read(FriendlyByteBuf buf) {
        ResourceLocation worldId = buf.readResourceLocation();
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
