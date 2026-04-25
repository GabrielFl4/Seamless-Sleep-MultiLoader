package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.config.WorldSleepAccelerationMode;
import net.aqualoco.sec.config.WorldSleepAccelerationPlayersAffected;
import net.aqualoco.sec.config.WorldSleepAutomaticMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Packet used to mirror server config values on connected clients.
public record ServerConfigSyncPayload(int sleepWeatherClearChancePercent,
                                      double sleepAnimationDurationMultiplier,
                                      int serverSimulationDistance,
                                      WorldSleepAccelerationMode worldSleepAccelerationMode,
                                      WorldSleepAutomaticMode worldSleepAutomaticMode,
                                      WorldSleepAccelerationPlayersAffected worldSleepAccelerationPlayersAffected,
                                      int manualAccelerationRadiusChunks,
                                      int manualAccelerationSpeedPercent,
                                      boolean grassAndFoliageAccelerationEnabled,
                                      boolean cropsAndSaplingsAccelerationEnabled,
                                      boolean kelpAccelerationEnabled,
                                      boolean vanillaOnlyAcceleration,
                                      boolean processesAccelerationEnabled,
                                      int processesSpeedPercent) implements CustomPacketPayload {

    public static final Type<ServerConfigSyncPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "server_config_sync"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigSyncPayload> CODEC =
            CustomPacketPayload.codec(ServerConfigSyncPayload::write, ServerConfigSyncPayload::read);

    private static void write(ServerConfigSyncPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.sleepWeatherClearChancePercent());
        buf.writeDouble(payload.sleepAnimationDurationMultiplier());
        buf.writeVarInt(payload.serverSimulationDistance());
        buf.writeUtf(payload.worldSleepAccelerationMode().name());
        buf.writeUtf(payload.worldSleepAutomaticMode().name());
        buf.writeUtf(payload.worldSleepAccelerationPlayersAffected().name());
        buf.writeVarInt(payload.manualAccelerationRadiusChunks());
        buf.writeVarInt(payload.manualAccelerationSpeedPercent());
        buf.writeBoolean(payload.grassAndFoliageAccelerationEnabled());
        buf.writeBoolean(payload.cropsAndSaplingsAccelerationEnabled());
        buf.writeBoolean(payload.kelpAccelerationEnabled());
        buf.writeBoolean(payload.vanillaOnlyAcceleration());
        buf.writeBoolean(payload.processesAccelerationEnabled());
        buf.writeVarInt(payload.processesSpeedPercent());
    }

    private static ServerConfigSyncPayload read(FriendlyByteBuf buf) {
        int weatherClearChancePercent = buf.readVarInt();
        double durationMultiplier = buf.readDouble();
        int serverSimulationDistance = buf.readVarInt();
        WorldSleepAccelerationMode mode = readEnum(
                buf,
                WorldSleepAccelerationMode.class,
                WorldSleepAccelerationMode.AUTOMATIC
        );
        WorldSleepAutomaticMode automaticMode = readEnum(
                buf,
                WorldSleepAutomaticMode.class,
                WorldSleepAutomaticMode.AGGRESSIVE
        );
        WorldSleepAccelerationPlayersAffected playersAffected = readEnum(
                buf,
                WorldSleepAccelerationPlayersAffected.class,
                WorldSleepAccelerationPlayersAffected.ALL_PLAYERS
        );
        int manualRadius = buf.readVarInt();
        int manualSpeed = buf.readVarInt();
        boolean grassAndFoliageEnabled = buf.readBoolean();
        boolean cropsAndSaplingsEnabled = buf.readBoolean();
        boolean kelpEnabled = buf.readBoolean();
        boolean vanillaOnlyAcceleration = buf.readBoolean();
        boolean processesEnabled = buf.readBoolean();
        int processesSpeedPercent = buf.readVarInt();
        return new ServerConfigSyncPayload(
                weatherClearChancePercent,
                durationMultiplier,
                serverSimulationDistance,
                mode,
                automaticMode,
                playersAffected,
                manualRadius,
                manualSpeed,
                grassAndFoliageEnabled,
                cropsAndSaplingsEnabled,
                kelpEnabled,
                vanillaOnlyAcceleration,
                processesEnabled,
                processesSpeedPercent
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
