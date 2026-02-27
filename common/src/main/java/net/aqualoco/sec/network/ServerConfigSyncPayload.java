package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Packet used to mirror server config values on connected clients.
public record ServerConfigSyncPayload(boolean sleepClearsWeather,
                                      double sleepAnimationDurationMultiplier) implements SeamlessSleepPacket {

    public static final ResourceLocation ID =
            new ResourceLocation(Constants.MOD_ID, "server_config_sync");

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(sleepClearsWeather());
        buf.writeDouble(sleepAnimationDurationMultiplier());
    }

    public static ServerConfigSyncPayload read(FriendlyByteBuf buf) {
        boolean clearsWeather = buf.readBoolean();
        double durationMultiplier = buf.readDouble();
        return new ServerConfigSyncPayload(clearsWeather, durationMultiplier);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
