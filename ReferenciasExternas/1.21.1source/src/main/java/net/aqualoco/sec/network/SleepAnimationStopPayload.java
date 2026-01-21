package net.aqualoco.sec.network;

import net.aqualoco.sec.AquaSec;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SleepAnimationStopPayload(Identifier worldId) implements CustomPayload {

    public static final CustomPayload.Id<SleepAnimationStopPayload> ID =
            new CustomPayload.Id<>(Identifier.of(AquaSec.MOD_ID, "sleep_animation_stop"));

    public static final PacketCodec<PacketByteBuf, SleepAnimationStopPayload> CODEC =
            CustomPayload.codecOf(SleepAnimationStopPayload::write, SleepAnimationStopPayload::read);

    private static void write(SleepAnimationStopPayload payload, PacketByteBuf buf) {
        buf.writeIdentifier(payload.worldId());
    }

    private static SleepAnimationStopPayload read(PacketByteBuf buf) {
        Identifier worldId = buf.readIdentifier();
        return new SleepAnimationStopPayload(worldId);
    }

    @Override
    public Id<SleepAnimationStopPayload> getId() {
        return ID;
    }
}

