package net.aqualoco.sec.network;

import net.aqualoco.sec.AquaSec;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Payload S2C para iniciar a animação de sono no client.
 */
public record SleepAnimationStartPayload(
        Identifier worldId,
        long startTimeOfDay,
        long endTimeOfDay,
        int durationTicks,
        long startMillis
) implements CustomPayload {

    public static final CustomPayload.Id<SleepAnimationStartPayload> ID =
            new CustomPayload.Id<>(Identifier.of(AquaSec.MOD_ID, "sleep_animation_start"));

    // Codec baseado em PacketByteBuf, usando a fábrica do CustomPayload
    public static final PacketCodec<PacketByteBuf, SleepAnimationStartPayload> CODEC =
            CustomPayload.codecOf(SleepAnimationStartPayload::write, SleepAnimationStartPayload::read);

    // ENCODER: value first. Assinatura precisa ser
    // (SleepAnimationStartPayload value, PacketByteBuf buf)
    // para casar com ValueFirstEncoder<B, T>.
    private static void write(SleepAnimationStartPayload payload, PacketByteBuf buf) {
        buf.writeIdentifier(payload.worldId());
        buf.writeLong(payload.startTimeOfDay());
        buf.writeLong(payload.endTimeOfDay());
        buf.writeInt(payload.durationTicks());
        buf.writeLong(payload.startMillis());
    }

    // DECODER: T read(B buf).
    private static SleepAnimationStartPayload read(PacketByteBuf buf) {
        Identifier worldId = buf.readIdentifier();
        long startTime = buf.readLong();
        long endTime = buf.readLong();
        int duration = buf.readInt();
        long startMillis = buf.readLong();
        return new SleepAnimationStartPayload(worldId, startTime, endTime, duration, startMillis);
    }

    @Override
    public Id<SleepAnimationStartPayload> getId() {
        return ID;
    }
}
