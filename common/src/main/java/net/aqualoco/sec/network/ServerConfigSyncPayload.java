package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SleepEligibilityMode;
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
                                      int fallAsleepDelayTicks,
                                      boolean overrideOverlayText,
                                      String overlayCustomText,
                                      SleepEligibilityMode sleepEligibility,
                                      int madeInHeavenChancePercent,
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
                                      int processesSpeedPercent,
                                      boolean betterDaysCompatibilityEnabled) implements CustomPacketPayload {

    public static final Type<ServerConfigSyncPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "server_config_sync"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigSyncPayload> CODEC =
            CustomPacketPayload.codec(ServerConfigSyncPayload::write, ServerConfigSyncPayload::read);

    private static void write(ServerConfigSyncPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.sleepWeatherClearChancePercent());
        buf.writeDouble(payload.sleepAnimationDurationMultiplier());
        buf.writeVarInt(payload.fallAsleepDelayTicks());
        buf.writeBoolean(payload.overrideOverlayText());
        buf.writeUtf(SeamlessSleepServerConfig.sanitizeOverlayText(payload.overlayCustomText()), 128);
        SleepEligibilityMode eligibility = payload.sleepEligibility() == null
                ? SleepEligibilityMode.VANILLA
                : payload.sleepEligibility();
        buf.writeUtf(eligibility.name());
        buf.writeVarInt(payload.madeInHeavenChancePercent());
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
        buf.writeBoolean(payload.betterDaysCompatibilityEnabled());
    }

    private static ServerConfigSyncPayload read(FriendlyByteBuf buf) {
        int weatherClearChancePercent = buf.readVarInt();
        double durationMultiplier = buf.readDouble();
        int bodyStartIndex = buf.readerIndex();
        try {
            if (looksLikeLegacyBody(buf)) {
                return readLegacyBody(buf, weatherClearChancePercent, durationMultiplier);
            }
            return readCurrentBody(buf, weatherClearChancePercent, durationMultiplier);
        } catch (RuntimeException currentFailure) {
            buf.readerIndex(bodyStartIndex);
            return readLegacyBody(buf, weatherClearChancePercent, durationMultiplier);
        }
    }

    private static ServerConfigSyncPayload readCurrentBody(FriendlyByteBuf buf,
                                                           int weatherClearChancePercent,
                                                           double durationMultiplier) {
        int fallAsleepDelayTicks = buf.readVarInt();
        boolean overrideOverlayText = buf.readBoolean();
        String overlayCustomText = buf.readUtf(128);
        SleepEligibilityMode sleepEligibility = readEnum(buf, SleepEligibilityMode.class, SleepEligibilityMode.VANILLA);
        int madeInHeavenChancePercent = buf.readVarInt();
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
        boolean betterDaysCompatibilityEnabled = buf.readableBytes() > 0
                ? buf.readBoolean()
                : SeamlessSleepServerConfig.DEFAULT_BETTER_DAYS_COMPATIBILITY_ENABLED;
        return new ServerConfigSyncPayload(
                weatherClearChancePercent,
                durationMultiplier,
                fallAsleepDelayTicks,
                overrideOverlayText,
                overlayCustomText,
                sleepEligibility,
                madeInHeavenChancePercent,
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
                processesSpeedPercent,
                betterDaysCompatibilityEnabled
        );
    }

    private static ServerConfigSyncPayload readLegacyBody(FriendlyByteBuf buf,
                                                          int weatherClearChancePercent,
                                                          double durationMultiplier) {
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
                SeamlessSleepServerConfig.DEFAULT_FALL_ASLEEP_DELAY_TICKS,
                false,
                SeamlessSleepServerConfig.DEFAULT_OVERLAY_CUSTOM_TEXT,
                SleepEligibilityMode.VANILLA,
                0,
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
                processesSpeedPercent,
                SeamlessSleepServerConfig.DEFAULT_BETTER_DAYS_COMPATIBILITY_ENABLED
        );
    }

    private static boolean looksLikeLegacyBody(FriendlyByteBuf buf) {
        int bodyStartIndex = buf.readerIndex();
        try {
            buf.readVarInt();
            if (buf.readableBytes() <= 0) {
                return false;
            }

            int nextByte = buf.readByte() & 0xFF;
            return nextByte != 0 && nextByte != 1;
        } finally {
            buf.readerIndex(bodyStartIndex);
        }
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
