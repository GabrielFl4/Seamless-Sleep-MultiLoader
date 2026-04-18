package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.config.WorldSleepAccelerationGovernorAggressiveness;
import net.aqualoco.sec.config.WorldSleepAccelerationMode;
import net.aqualoco.sec.config.WorldSleepAccelerationPreset;
import net.aqualoco.sec.config.WorldSleepNatureFilterProfile;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Packet used to mirror server config values on connected clients.
public record ServerConfigSyncPayload(int sleepWeatherClearChancePercent,
                                      double sleepAnimationDurationMultiplier,
                                      WorldSleepAccelerationMode worldSleepAccelerationMode,
                                      WorldSleepAccelerationPreset worldSleepAccelerationPreset,
                                      boolean randomTickAccelerationEnabled,
                                      boolean processAccelerationEnabled,
                                      WorldSleepAccelerationGovernorAggressiveness governorAggressiveness,
                                      WorldSleepNatureFilterProfile natureFilterProfile,
                                      int natureBaseRadiusChunks,
                                      int natureAutoMinRadiusChunks,
                                      double natureBaseRateFraction,
                                      double natureAutoMinRateFraction,
                                      int processBaseRadiusChunks,
                                      int processAutoMinRadiusChunks,
                                      double processBaseRateFraction,
                                      double processAutoMinRateFraction) implements CustomPacketPayload {

    public static final Type<ServerConfigSyncPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "server_config_sync"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigSyncPayload> CODEC =
            CustomPacketPayload.codec(ServerConfigSyncPayload::write, ServerConfigSyncPayload::read);

    private static void write(ServerConfigSyncPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.sleepWeatherClearChancePercent());
        buf.writeDouble(payload.sleepAnimationDurationMultiplier());
        buf.writeUtf(payload.worldSleepAccelerationMode().name());
        buf.writeUtf(payload.worldSleepAccelerationPreset().name());
        buf.writeBoolean(payload.randomTickAccelerationEnabled());
        buf.writeBoolean(payload.processAccelerationEnabled());
        buf.writeUtf(payload.governorAggressiveness().name());
        buf.writeUtf(payload.natureFilterProfile().name());
        buf.writeVarInt(payload.natureBaseRadiusChunks());
        buf.writeVarInt(payload.natureAutoMinRadiusChunks());
        buf.writeDouble(payload.natureBaseRateFraction());
        buf.writeDouble(payload.natureAutoMinRateFraction());
        buf.writeVarInt(payload.processBaseRadiusChunks());
        buf.writeVarInt(payload.processAutoMinRadiusChunks());
        buf.writeDouble(payload.processBaseRateFraction());
        buf.writeDouble(payload.processAutoMinRateFraction());
    }

    private static ServerConfigSyncPayload read(FriendlyByteBuf buf) {
        int weatherClearChancePercent = buf.readVarInt();
        double durationMultiplier = buf.readDouble();
        WorldSleepAccelerationMode mode = readEnum(buf, WorldSleepAccelerationMode.class, WorldSleepAccelerationMode.AUTO);
        WorldSleepAccelerationPreset preset = readEnum(buf, WorldSleepAccelerationPreset.class, WorldSleepAccelerationPreset.BALANCED);
        boolean randomTickEnabled = buf.readBoolean();
        boolean processEnabled = buf.readBoolean();
        WorldSleepAccelerationGovernorAggressiveness aggressiveness = readEnum(
                buf,
                WorldSleepAccelerationGovernorAggressiveness.class,
                WorldSleepAccelerationGovernorAggressiveness.BALANCED
        );
        WorldSleepNatureFilterProfile filterProfile = readEnum(
                buf,
                WorldSleepNatureFilterProfile.class,
                WorldSleepNatureFilterProfile.ALL
        );
        int natureBaseRadius = buf.readVarInt();
        int natureMinRadius = buf.readVarInt();
        double natureBaseRate = buf.readDouble();
        double natureMinRate = buf.readDouble();
        int processBaseRadius = buf.readVarInt();
        int processMinRadius = buf.readVarInt();
        double processBaseRate = buf.readDouble();
        double processMinRate = buf.readDouble();
        return new ServerConfigSyncPayload(
                weatherClearChancePercent,
                durationMultiplier,
                mode,
                preset,
                randomTickEnabled,
                processEnabled,
                aggressiveness,
                filterProfile,
                natureBaseRadius,
                natureMinRadius,
                natureBaseRate,
                natureMinRate,
                processBaseRadius,
                processMinRadius,
                processBaseRate,
                processMinRate
        );
    }

    @Override
    public Type<ServerConfigSyncPayload> type() {
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
