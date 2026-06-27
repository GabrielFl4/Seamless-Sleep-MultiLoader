package net.aqualoco.sec.config;

import net.aqualoco.sec.network.ServerConfigAccessS2CPayload;
import net.aqualoco.sec.network.ServerConfigField;
import net.aqualoco.sec.network.ServerConfigSync;
import net.aqualoco.sec.network.ServerConfigUpdateC2SPayload;
import net.aqualoco.sec.network.ServerConfigUpdateResultS2CPayload;
import net.aqualoco.sec.network.ServerConfigUpdateStatus;
import net.aqualoco.sec.handshake.ServerSeamlessClientPresenceManager;
import net.aqualoco.sec.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;

import java.util.Map;

public final class ServerConfigMutationService {
    public static final int REQUIRED_PERMISSION_LEVEL = 3;
    private static int serverConfigRevision;

    private ServerConfigMutationService() {
    }

    public static int currentRevision() {
        return serverConfigRevision;
    }

    public static boolean canEditServerConfig(ServerPlayer player) {
        return player != null && player.hasPermissions(REQUIRED_PERMISSION_LEVEL);
    }

    public static void sendAccessToPlayer(ServerPlayer player) {
        if (!ServerSeamlessClientPresenceManager.isConfirmed(player)) {
            return;
        }

        Services.NETWORK.sendToPlayerIfSupported(player, new ServerConfigAccessS2CPayload(
                canEditServerConfig(player),
                REQUIRED_PERMISSION_LEVEL,
                serverConfigRevision
        ));
    }

    public static void sendAccessToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendAccessToPlayer(player);
        }
    }

    public static void sendAccessToOnlineProfile(PlayerList playerList, NameAndId profile) {
        if (playerList == null || profile == null) {
            return;
        }

        ServerPlayer player = playerList.getPlayer(profile.id());
        if (player != null) {
            sendAccessToPlayer(player);
            ServerConfigSync.sendToPlayer(player, SeamlessSleepServerConfigManager.get());
        }
    }

    public static void handleAccessRequest(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!ServerSeamlessClientPresenceManager.requireConfirmed(player, "server_config_access_request")) {
            return;
        }

        ServerConfigSync.sendToPlayer(player, SeamlessSleepServerConfigManager.get());
        sendAccessToPlayer(player);
    }

    public static void handleRemoteUpdate(ServerPlayer player, ServerConfigUpdateC2SPayload payload) {
        if (player == null) {
            return;
        }
        if (!ServerSeamlessClientPresenceManager.requireConfirmed(player, "server_config_update")) {
            return;
        }

        if (!canEditServerConfig(player)) {
            sendUpdateResult(player, false, ServerConfigUpdateStatus.NO_PERMISSION, "No permission to edit server config.");
            handleAccessRequest(player);
            return;
        }
        if (payload.values().isEmpty()) {
            sendUpdateResult(player, false, ServerConfigUpdateStatus.EMPTY_PATCH, "No server config fields changed.");
            return;
        }

        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        try {
            applyPatch(server, payload);
        } catch (IllegalArgumentException exception) {
            sendUpdateResult(player, false, ServerConfigUpdateStatus.INVALID_VALUE, exception.getMessage());
            ServerConfigSync.sendToPlayer(player, SeamlessSleepServerConfigManager.get());
            sendAccessToPlayer(player);
            return;
        }

        sendUpdateResult(player, true, ServerConfigUpdateStatus.SUCCESS, "Server config updated.");
    }

    public static boolean applyTrustedLocalUpdate(MinecraftServer server, ServerConfigUpdateC2SPayload payload) {
        if (server == null || payload == null || payload.values().isEmpty()) {
            return false;
        }

        try {
            applyPatch(server, payload);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static void applyPatch(MinecraftServer server, ServerConfigUpdateC2SPayload payload) {
        SeamlessSleepServerConfig patched = copyOf(SeamlessSleepServerConfigManager.get());
        int simulationDistance = Math.max(1, server.getPlayerList().getSimulationDistance());
        for (Map.Entry<ServerConfigField, String> entry : payload.values().entrySet()) {
            applyField(patched, entry.getKey(), entry.getValue(), simulationDistance);
        }

        copyInto(patched, SeamlessSleepServerConfigManager.get());
        saveAndSync(server, SeamlessSleepServerConfigManager.get());
    }

    public static void saveAndSync(MinecraftServer server, SeamlessSleepServerConfig config) {
        config.clamp();
        SeamlessSleepServerConfigManager.save();
        syncAfterMutation(server, config);
    }

    public static void syncAfterMutation(MinecraftServer server, SeamlessSleepServerConfig config) {
        config.clamp();
        serverConfigRevision++;
        ServerConfigSync.sendToAll(server, config);
        sendAccessToAll(server);
    }

    private static void sendUpdateResult(ServerPlayer player,
                                         boolean success,
                                         ServerConfigUpdateStatus status,
                                         String message) {
        Services.NETWORK.sendToPlayerIfSupported(player, new ServerConfigUpdateResultS2CPayload(
                success,
                serverConfigRevision,
                status,
                message
        ));
    }

    private static void applyField(SeamlessSleepServerConfig config,
                                   ServerConfigField field,
                                   String value,
                                   int simulationDistance) {
        switch (field) {
            case SLEEP_WEATHER_CLEAR_CHANCE_PERCENT ->
                    config.sleepWeatherClearChancePercent = parseInt(value, 0, 100, field);
            case SLEEP_ANIMATION_DURATION_MULTIPLIER ->
                    config.sleepAnimationDurationMultiplier = parseDouble(value, 0.25D, 8.0D, field);
            case FALL_ASLEEP_DELAY_TICKS -> config.fallAsleepDelayTicks = parseInt(
                    value,
                    SeamlessSleepServerConfig.MIN_FALL_ASLEEP_DELAY_TICKS,
                    SeamlessSleepServerConfig.MAX_FALL_ASLEEP_DELAY_TICKS,
                    field
            );
            case OVERRIDE_OVERLAY_TEXT -> config.overrideOverlayText = parseBoolean(value, field);
            case OVERLAY_CUSTOM_TEXT -> config.overlayCustomText = SeamlessSleepServerConfig.sanitizeOverlayText(value);
            case SLEEP_ELIGIBILITY -> config.sleepEligibility = parseEnum(value, SleepEligibilityMode.class, field);
            case MADE_IN_HEAVEN_CHANCE_PERCENT ->
                    config.madeInHeavenChancePercent = parseInt(value, 0, 100, field);
            case BETTER_DAYS_COMPATIBILITY_ENABLED -> config.betterDaysCompatibilityEnabled = parseBoolean(value, field);
            case WORLD_SLEEP_ACCELERATION_MODE ->
                    config.worldSleepAcceleration.mode = parseEnum(value, WorldSleepAccelerationMode.class, field);
            case WORLD_SLEEP_AUTOMATIC_MODE ->
                    config.worldSleepAcceleration.automaticMode = parseEnum(value, WorldSleepAutomaticMode.class, field);
            case WORLD_SLEEP_ACCELERATION_PLAYERS_AFFECTED -> config.worldSleepAcceleration.playersAffected =
                    parseEnum(value, WorldSleepAccelerationPlayersAffected.class, field);
            case MANUAL_ACCELERATION_RADIUS_CHUNKS -> config.worldSleepAcceleration.manualAccelerationRadiusChunks =
                    parseInt(value, 1, Math.max(1, simulationDistance), field);
            case MANUAL_ACCELERATION_SPEED_PERCENT -> config.worldSleepAcceleration.manualAccelerationSpeedPercent =
                    parseInt(value, 0, 100, field);
            case GRASS_AND_FOLIAGE_ACCELERATION_ENABLED -> config.worldSleepAcceleration.grassAndFoliageAccelerationEnabled =
                    parseBoolean(value, field);
            case CROPS_AND_SAPLINGS_ACCELERATION_ENABLED -> config.worldSleepAcceleration.cropsAndSaplingsAccelerationEnabled =
                    parseBoolean(value, field);
            case VINES_AND_BAMBOO_ACCELERATION_ENABLED ->
                    config.worldSleepAcceleration.vinesAndBambooAccelerationEnabled =
                            parseBoolean(value, field);
            case KELP_ACCELERATION_ENABLED -> config.worldSleepAcceleration.kelpAccelerationEnabled =
                    parseBoolean(value, field);
            case VANILLA_ONLY_ACCELERATION -> config.worldSleepAcceleration.vanillaOnlyAcceleration =
                    parseBoolean(value, field);
            case RECHECK_IRRELEVANT_NATURE_SECTIONS_DURING_ACCELERATION ->
                    config.worldSleepAcceleration.recheckIrrelevantNatureSectionsDuringAcceleration =
                            parseBoolean(value, field);
            case WORLD_SLEEP_ACCELERATION_TELEMETRY_ENABLED ->
                    config.worldSleepAcceleration.accelerationTelemetryEnabled =
                            parseBoolean(value, field);
            case PROCESSES_ACCELERATION_ENABLED -> config.worldSleepAcceleration.processesAccelerationEnabled =
                    parseBoolean(value, field);
            case PROCESSES_SPEED_PERCENT -> config.worldSleepAcceleration.processesSpeedPercent =
                    parseInt(value, 0, 100, field);
        }
    }

    private static int parseInt(String value, int min, int max, ServerConfigField field) {
        try {
            int parsed = Integer.parseInt(value);
            return SeamlessSleepServerConfig.clampInt(parsed, min, max);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer for " + field.name() + ".");
        }
    }

    private static double parseDouble(String value, double min, double max, ServerConfigField field) {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                throw new NumberFormatException("Non-finite value");
            }
            if (parsed < min) {
                return min;
            }
            return Math.min(parsed, max);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid decimal for " + field.name() + ".");
        }
    }

    private static boolean parseBoolean(String value, ServerConfigField field) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean for " + field.name() + ".");
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> enumType, ServerConfigField field) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid enum for " + field.name() + ".");
        }
    }

    private static SeamlessSleepServerConfig copyOf(SeamlessSleepServerConfig source) {
        SeamlessSleepServerConfig copy = new SeamlessSleepServerConfig();
        copyInto(source, copy);
        return copy;
    }

    private static void copyInto(SeamlessSleepServerConfig source, SeamlessSleepServerConfig target) {
        target.sleepWeatherClearChancePercent = source.sleepWeatherClearChancePercent;
        target.sleepAnimationDurationMultiplier = source.sleepAnimationDurationMultiplier;
        target.fallAsleepDelayTicks = source.fallAsleepDelayTicks;
        target.overrideOverlayText = source.overrideOverlayText;
        target.overlayCustomText = source.overlayCustomText;
        target.sleepEligibility = source.sleepEligibility;
        target.madeInHeavenChancePercent = source.madeInHeavenChancePercent;
        target.betterDaysCompatibilityEnabled = source.betterDaysCompatibilityEnabled;
        target.worldSleepAcceleration.mode = source.worldSleepAcceleration.mode;
        target.worldSleepAcceleration.automaticMode = source.worldSleepAcceleration.automaticMode;
        target.worldSleepAcceleration.playersAffected = source.worldSleepAcceleration.playersAffected;
        target.worldSleepAcceleration.manualAccelerationRadiusChunks = source.worldSleepAcceleration.manualAccelerationRadiusChunks;
        target.worldSleepAcceleration.manualAccelerationSpeedPercent = source.worldSleepAcceleration.manualAccelerationSpeedPercent;
        target.worldSleepAcceleration.grassAndFoliageAccelerationEnabled = source.worldSleepAcceleration.grassAndFoliageAccelerationEnabled;
        target.worldSleepAcceleration.cropsAndSaplingsAccelerationEnabled = source.worldSleepAcceleration.cropsAndSaplingsAccelerationEnabled;
        target.worldSleepAcceleration.vinesAndBambooAccelerationEnabled =
                source.worldSleepAcceleration.vinesAndBambooAccelerationEnabled;
        target.worldSleepAcceleration.kelpAccelerationEnabled = source.worldSleepAcceleration.kelpAccelerationEnabled;
        target.worldSleepAcceleration.vanillaOnlyAcceleration = source.worldSleepAcceleration.vanillaOnlyAcceleration;
        target.worldSleepAcceleration.recheckIrrelevantNatureSectionsDuringAcceleration =
                source.worldSleepAcceleration.recheckIrrelevantNatureSectionsDuringAcceleration;
        target.worldSleepAcceleration.accelerationTelemetryEnabled =
                source.worldSleepAcceleration.accelerationTelemetryEnabled;
        target.worldSleepAcceleration.processesAccelerationEnabled = source.worldSleepAcceleration.processesAccelerationEnabled;
        target.worldSleepAcceleration.processesSpeedPercent = source.worldSleepAcceleration.processesSpeedPercent;
    }
}
