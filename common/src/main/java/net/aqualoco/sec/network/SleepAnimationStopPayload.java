package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Packet that tells clients to stop the active sleep transition.
public record SleepAnimationStopPayload(Identifier worldId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SleepAnimationStopPayload> ID =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sleep_animation_stop")
            );

    public static final StreamCodec<FriendlyByteBuf, SleepAnimationStopPayload> CODEC =
            CustomPacketPayload.codec(SleepAnimationStopPayload::write, SleepAnimationStopPayload::read);

    private static void write(SleepAnimationStopPayload payload, FriendlyByteBuf buf) {
        buf.writeIdentifier(payload.worldId());
    }

    private static SleepAnimationStopPayload read(FriendlyByteBuf buf) {
        Identifier worldId = buf.readIdentifier();
        return new SleepAnimationStopPayload(worldId);
    }

    @Override
    public Type<SleepAnimationStopPayload> type() {
        return ID;
    }
}
