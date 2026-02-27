package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Packet that starts the client sleep transition with timing and world data.
public record SleepAnimationStartPayload(
        ResourceLocation worldId,
        long startTimeOfDay,
        long endTimeOfDay,
        int durationTicks,
        long startMillis
) implements SeamlessSleepPacket {

    public static final ResourceLocation ID =
            new ResourceLocation(Constants.MOD_ID, "sleep_animation_start");

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(worldId());
        buf.writeLong(startTimeOfDay());
        buf.writeLong(endTimeOfDay());
        buf.writeInt(durationTicks());
        buf.writeLong(startMillis());
    }

    public static SleepAnimationStartPayload read(FriendlyByteBuf buf) {
        ResourceLocation worldId = buf.readResourceLocation();
        long startTime = buf.readLong();
        long endTime = buf.readLong();
        int duration = buf.readInt();
        long startMillis = buf.readLong();
        return new SleepAnimationStartPayload(worldId, startTime, endTime, duration, startMillis);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
