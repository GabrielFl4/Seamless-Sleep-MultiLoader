package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Packet that tells clients to stop the active sleep transition.
public record SleepAnimationStopPayload(ResourceLocation worldId) implements SeamlessSleepPacket {

    public static final ResourceLocation ID =
            new ResourceLocation(Constants.MOD_ID, "sleep_animation_stop");

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(worldId());
    }

    public static SleepAnimationStopPayload read(FriendlyByteBuf buf) {
        ResourceLocation worldId = buf.readResourceLocation();
        return new SleepAnimationStopPayload(worldId);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
