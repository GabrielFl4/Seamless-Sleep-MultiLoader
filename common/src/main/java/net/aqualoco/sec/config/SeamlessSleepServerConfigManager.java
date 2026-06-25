package net.aqualoco.sec.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SeamlessSleepServerConfigManager {

    private static final String FILE_NAME = "seamless_sleep-server.toml";
    private static final String LEGACY_JSON_FILE_NAME = "seamless_sleep-server.json";
    private static final String LEGACY_JSONC_FILE_NAME = "seamless_sleep-server.jsonc";
    private static final int CONFIG_VERSION = 10;

    public enum ReloadResult {
        SUCCESS,
        CREATED,
        ERROR
    }

    public record ReloadReport(ReloadResult status,
                               Path path,
                               boolean loadedDefaults,
                               boolean createdFile,
                               boolean savedCanonicalFile,
                               String message) {
    }

    private static SeamlessSleepServerConfig config = defaultConfig();
    private static Path configPath;
    private static ReloadReport lastReloadReport = new ReloadReport(
            ReloadResult.SUCCESS,
            null,
            false,
            false,
            false,
            "Server config has not been reloaded during this run."
    );

    private SeamlessSleepServerConfigManager() {
    }

    public static void init() {
        configPath = Services.PLATFORM.getConfigDir().resolve(FILE_NAME);
        config = loadOrCreate(configPath).config;
    }

    public static SeamlessSleepServerConfig get() {
        return config;
    }

    public static void save() {
        if (configPath == null || config == null) {
            return;
        }
        config.clamp();
        save(configPath, config);
    }

    public static void reload() {
        if (configPath == null) {
            configPath = Services.PLATFORM.getConfigDir().resolve(FILE_NAME);
        }
        LoadResult result = loadOrCreate(configPath);
        config = result.config;
    }

    public static ReloadResult reloadWithStatus() {
        return reloadWithReport().status();
    }

    public static ReloadReport reloadWithReport() {
        if (configPath == null) {
            configPath = Services.PLATFORM.getConfigDir().resolve(FILE_NAME);
        }
        LoadResult result = loadOrCreate(configPath);
        config = result.config;
        lastReloadReport = new ReloadReport(
                result.status,
                configPath,
                result.status == ReloadResult.ERROR || result.status == ReloadResult.CREATED,
                result.status == ReloadResult.CREATED,
                true,
                reloadMessage(result.status)
        );
        return lastReloadReport;
    }

    public static ReloadReport lastReloadReport() {
        return lastReloadReport;
    }

    public static Path configPath() {
        if (configPath == null) {
            configPath = Services.PLATFORM.getConfigDir().resolve(FILE_NAME);
        }
        return configPath;
    }

    private static LoadResult loadOrCreate(Path path) {
        deleteLegacyJsonFiles(path);

        if (Files.notExists(path)) {
            SeamlessSleepServerConfig cfg = defaultConfig();
            cfg.clamp();
            save(path, cfg);
            return new LoadResult(cfg, ReloadResult.CREATED);
        }

        try (CommentedFileConfig file = openConfig(path)) {
            file.load();
            logMetadataInfo(path, file);

            SeamlessSleepServerConfig cfg = readServerConfig(file);
            cfg.clamp();
            save(path, cfg);
            return new LoadResult(cfg, ReloadResult.SUCCESS);
        } catch (Exception e) {
            Constants.warn("Failed to read server config {}, using defaults. Error: {}", path, e.getMessage());
            SeamlessSleepServerConfig cfg = defaultConfig();
            cfg.clamp();
            save(path, cfg);
            return new LoadResult(cfg, ReloadResult.ERROR);
        }
    }

    private static void save(Path path, SeamlessSleepServerConfig cfg) {
        cfg.clamp();

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, toServerToml(cfg, resolveCurrentModVersion()));
        } catch (IOException e) {
            Constants.warn("Failed to save server config {}: {}", path, e.getMessage());
        } catch (Exception e) {
            Constants.warn("Failed to write server config {}: {}", path, e.getMessage());
        }
    }

    private static SeamlessSleepServerConfig defaultConfig() {
        return new SeamlessSleepServerConfig();
    }

    private static String reloadMessage(ReloadResult result) {
        return switch (result) {
            case SUCCESS -> "Server config reloaded and canonical TOML was written.";
            case CREATED -> "Server config was missing; defaults were created and loaded.";
            case ERROR -> "Server config failed to load; defaults were loaded and written.";
        };
    }

    private static CommentedFileConfig openConfig(Path path) {
        return CommentedFileConfig.builder(path, TomlFormat.instance())
                .sync()
                .preserveInsertionOrder()
                .build();
    }

    private static SeamlessSleepServerConfig readServerConfig(CommentedFileConfig file) {
        SeamlessSleepServerConfig cfg = defaultConfig();
        cfg.sleepWeatherClearChancePercent = readWeatherClearChancePercent(file, cfg.sleepWeatherClearChancePercent);
        cfg.sleepAnimationDurationMultiplier = readDouble(
                file,
                List.of("sleep", "sleepAnimationDurationMultiplier"),
                "sleepAnimationDurationMultiplier",
                cfg.sleepAnimationDurationMultiplier
        );
        cfg.fallAsleepDelayTicks = readInt(
                file,
                List.of("sleep", "fall_asleep_delay_ticks"),
                "fallAsleepDelayTicks",
                cfg.fallAsleepDelayTicks
        );
        cfg.overrideOverlayText = readBoolean(
                file,
                List.of("sleep", "text_indicator_override"),
                List.of("sleep", "override_overlay_text"),
                "overrideOverlayText",
                cfg.overrideOverlayText
        );
        cfg.overlayCustomText = readString(
                file,
                List.of("sleep", "text_indicator_custom_text"),
                List.of("sleep", "overlay_custom_text"),
                "overlayCustomText",
                cfg.overlayCustomText
        );
        cfg.sleepEligibility = readEnum(
                file,
                List.of("sleep", "sleep_eligibility"),
                "sleepEligibility",
                SleepEligibilityMode.class,
                cfg.sleepEligibility
        );
        cfg.madeInHeavenChancePercent = readInt(
                file,
                List.of("easter_eggs", "made_in_heaven_chance_percent"),
                "madeInHeavenChancePercent",
                cfg.madeInHeavenChancePercent
        );
        cfg.betterDaysCompatibilityEnabled = readBoolean(
                file,
                List.of("compatibility", "better_days_sleep_compatibility_enabled"),
                "betterDaysCompatibilityEnabled",
                cfg.betterDaysCompatibilityEnabled
        );
        readWorldSleepAcceleration(file, cfg.worldSleepAcceleration);
        cfg.clamp();
        return cfg;
    }

    private static void readWorldSleepAcceleration(CommentedFileConfig file, WorldSleepAccelerationConfig cfg) {
        LegacyAccelerationData legacy = hasLegacyAccelerationData(file)
                ? readLegacyAccelerationData(file)
                : currentAccelerationData(cfg);

        cfg.mode = readAccelerationMode(
                file,
                List.of("world_sleep_acceleration", "mode"),
                "worldSleepAccelerationMode",
                legacy.mode
        );
        cfg.automaticMode = readAutomaticMode(
                file,
                List.of("world_sleep_acceleration", "automatic_mode"),
                "worldSleepAccelerationAutomaticMode",
                legacy.automaticMode
        );
        cfg.playersAffected = readPlayersAffected(
                file,
                List.of("world_sleep_acceleration", "players_affected"),
                "worldSleepAccelerationPlayersAffected",
                legacy.playersAffected
        );
        cfg.manualAccelerationRadiusChunks = readInt(
                file,
                List.of("world_sleep_acceleration", "manual_radius_chunks"),
                "worldSleepAccelerationManualRadiusChunks",
                legacy.manualRadiusChunks
        );
        cfg.manualAccelerationSpeedPercent = readInt(
                file,
                List.of("world_sleep_acceleration", "manual_speed_percent"),
                "worldSleepAccelerationManualSpeedPercent",
                legacy.manualSpeedPercent
        );
        cfg.grassAndFoliageAccelerationEnabled = readBoolean(
                file,
                List.of("world_sleep_acceleration", "grass_and_foliage_acceleration_enabled"),
                "worldSleepAccelerationGrassAndFoliageEnabled",
                legacy.grassAndFoliageEnabled
        );
        cfg.cropsAndSaplingsAccelerationEnabled = readBoolean(
                file,
                List.of("world_sleep_acceleration", "crops_and_saplings_acceleration_enabled"),
                "worldSleepAccelerationCropsAndSaplingsEnabled",
                legacy.cropsAndSaplingsEnabled
        );
        cfg.vinesAndBambooAccelerationEnabled = readBoolean(
                file,
                List.of("world_sleep_acceleration", "vines_and_bamboo_acceleration_enabled"),
                "worldSleepAccelerationVinesAndBambooEnabled",
                cfg.vinesAndBambooAccelerationEnabled
        );
        cfg.kelpAccelerationEnabled = readBoolean(
                file,
                List.of("world_sleep_acceleration", "kelp_acceleration_enabled"),
                "worldSleepAccelerationKelpEnabled",
                legacy.kelpEnabled
        );
        cfg.vanillaOnlyAcceleration = readBoolean(
                file,
                List.of("world_sleep_acceleration", "vanilla_only_acceleration"),
                "worldSleepAccelerationVanillaOnly",
                legacy.vanillaOnlyAcceleration
        );
        cfg.recheckIrrelevantNatureSectionsDuringAcceleration = readBoolean(
                file,
                List.of("world_sleep_acceleration", "recheck_irrelevant_nature_sections_during_acceleration"),
                "recheckIrrelevantNatureSectionsDuringAcceleration",
                cfg.recheckIrrelevantNatureSectionsDuringAcceleration
        );
        cfg.accelerationTelemetryEnabled = readBoolean(
                file,
                List.of("world_sleep_acceleration", "telemetry_enabled"),
                "worldSleepAccelerationTelemetryEnabled",
                cfg.accelerationTelemetryEnabled
        );
        cfg.processesAccelerationEnabled = readBoolean(
                file,
                List.of("world_sleep_acceleration", "processes_acceleration_enabled"),
                "worldSleepAccelerationProcessesEnabled",
                legacy.processesAccelerationEnabled
        );
        cfg.processesSpeedPercent = readInt(
                file,
                List.of("world_sleep_acceleration", "processes_speed_percent"),
                "worldSleepAccelerationProcessesSpeedPercent",
                legacy.processesSpeedPercent
        );
    }

    private static LegacyAccelerationData currentAccelerationData(WorldSleepAccelerationConfig cfg) {
        return new LegacyAccelerationData(
                cfg.mode,
                cfg.automaticMode,
                cfg.playersAffected,
                cfg.manualAccelerationRadiusChunks,
                cfg.manualAccelerationSpeedPercent,
                cfg.grassAndFoliageAccelerationEnabled,
                cfg.cropsAndSaplingsAccelerationEnabled,
                cfg.kelpAccelerationEnabled,
                cfg.vanillaOnlyAcceleration,
                cfg.processesAccelerationEnabled,
                cfg.processesSpeedPercent
        );
    }

    private static boolean hasLegacyAccelerationData(CommentedFileConfig file) {
        return hasRaw(file, List.of("world_sleep_acceleration", "preset"))
                || hasRaw(file, List.of("world_sleep_acceleration", "random_tick_acceleration_enabled"))
                || hasRaw(file, List.of("world_sleep_acceleration", "process_acceleration_enabled"))
                || hasRaw(file, List.of("world_sleep_acceleration", "nature_filter_profile"))
                || hasLegacyModuleData(file, List.of("world_sleep_acceleration", "nature"), "worldSleepAccelerationNature")
                || hasLegacyModuleData(file, List.of("world_sleep_acceleration", "process"), "worldSleepAccelerationProcess")
                || hasRaw(file, "worldSleepAccelerationPreset")
                || hasRaw(file, "worldSleepAccelerationRandomTickEnabled")
                || hasRaw(file, "worldSleepAccelerationProcessEnabled")
                || hasRaw(file, "worldSleepAccelerationNatureFilterProfile");
    }

    private static boolean hasLegacyModuleData(CommentedFileConfig file, List<String> pathPrefix, String legacyPrefix) {
        return hasRaw(file, append(pathPrefix, "base_radius_chunks"))
                || hasRaw(file, append(pathPrefix, "auto_min_radius_chunks"))
                || hasRaw(file, append(pathPrefix, "base_rate_fraction"))
                || hasRaw(file, append(pathPrefix, "auto_min_rate_fraction"))
                || hasRaw(file, legacyPrefix + "BaseRadiusChunks")
                || hasRaw(file, legacyPrefix + "AutoMinRadiusChunks")
                || hasRaw(file, legacyPrefix + "BaseRateFraction")
                || hasRaw(file, legacyPrefix + "AutoMinRateFraction");
    }

    private static LegacyAccelerationData readLegacyAccelerationData(CommentedFileConfig file) {
        WorldSleepAccelerationMode legacyMode = readAccelerationMode(
                file,
                List.of("world_sleep_acceleration", "mode"),
                "worldSleepAccelerationMode",
                WorldSleepAccelerationMode.AUTOMATIC
        );
        WorldSleepAutomaticMode legacyAutomaticMode = readAutomaticMode(
                file,
                List.of("world_sleep_acceleration", "preset"),
                "worldSleepAccelerationPreset",
                WorldSleepAutomaticMode.AGGRESSIVE
        );
        boolean legacyRandomTickEnabled = readBoolean(
                file,
                List.of("world_sleep_acceleration", "random_tick_acceleration_enabled"),
                "worldSleepAccelerationRandomTickEnabled",
                true
        );
        boolean legacyProcessEnabled = readBoolean(
                file,
                List.of("world_sleep_acceleration", "process_acceleration_enabled"),
                "worldSleepAccelerationProcessEnabled",
                true
        );
        WorldSleepNatureFilterProfile legacyNatureProfile = readEnum(
                file,
                List.of("world_sleep_acceleration", "nature_filter_profile"),
                "worldSleepAccelerationNatureFilterProfile",
                WorldSleepNatureFilterProfile.class,
                WorldSleepNatureFilterProfile.ALL
        );

        WorldSleepAccelerationModuleConfig legacyNature = new WorldSleepAccelerationModuleConfig();
        readLegacyModuleConfig(
                file,
                List.of("world_sleep_acceleration", "nature"),
                "worldSleepAccelerationNature",
                legacyNature
        );
        WorldSleepAccelerationModuleConfig legacyProcess = new WorldSleepAccelerationModuleConfig();
        legacyProcess.baseRateFraction = 1.0D;
        legacyProcess.autoMinRateFraction = 0.25D;
        readLegacyModuleConfig(
                file,
                List.of("world_sleep_acceleration", "process"),
                "worldSleepAccelerationProcess",
                legacyProcess
        );

        LegacyNatureFilterMapping filters = mapLegacyNatureFilters(legacyRandomTickEnabled, legacyNatureProfile);
        int manualRadiusChunks = legacyNature.baseRadiusChunks > 0
                ? legacyNature.baseRadiusChunks
                : legacyProcess.baseRadiusChunks;
        int manualSpeedPercent = WorldSleepAccelerationConfig.DEFAULT_MANUAL_SPEED_PERCENT;
        int processesSpeedPercent = fractionToPercent(legacyProcess.baseRateFraction, 100);

        return new LegacyAccelerationData(
                legacyMode,
                legacyAutomaticMode,
                WorldSleepAccelerationPlayersAffected.ALL_PLAYERS,
                manualRadiusChunks,
                manualSpeedPercent,
                filters.grassAndFoliageEnabled,
                filters.cropsAndSaplingsEnabled,
                filters.kelpEnabled,
                filters.vanillaOnlyAcceleration,
                legacyProcessEnabled,
                processesSpeedPercent
        );
    }

    private static void readLegacyModuleConfig(CommentedFileConfig file,
                                               List<String> pathPrefix,
                                               String legacyPrefix,
                                               WorldSleepAccelerationModuleConfig cfg) {
        cfg.baseRadiusChunks = readInt(
                file,
                append(pathPrefix, "base_radius_chunks"),
                legacyPrefix + "BaseRadiusChunks",
                cfg.baseRadiusChunks
        );
        cfg.autoMinRadiusChunks = readInt(
                file,
                append(pathPrefix, "auto_min_radius_chunks"),
                legacyPrefix + "AutoMinRadiusChunks",
                cfg.autoMinRadiusChunks
        );
        cfg.baseRateFraction = readDouble(
                file,
                append(pathPrefix, "base_rate_fraction"),
                legacyPrefix + "BaseRateFraction",
                cfg.baseRateFraction
        );
        cfg.autoMinRateFraction = readDouble(
                file,
                append(pathPrefix, "auto_min_rate_fraction"),
                legacyPrefix + "AutoMinRateFraction",
                cfg.autoMinRateFraction
        );
        cfg.clamp();
    }

    private static LegacyNatureFilterMapping mapLegacyNatureFilters(boolean randomTickEnabled,
                                                                    WorldSleepNatureFilterProfile profile) {
        boolean grassAndFoliageEnabled = false;
        boolean cropsAndSaplingsEnabled = false;
        boolean kelpEnabled = false;
        boolean vanillaOnlyAcceleration = profile == WorldSleepNatureFilterProfile.VANILLA_ONLY;

        if (randomTickEnabled) {
            switch (profile) {
                case ALL -> {
                    grassAndFoliageEnabled = true;
                    cropsAndSaplingsEnabled = true;
                    kelpEnabled = true;
                }
                case VANILLA_ONLY -> {
                    grassAndFoliageEnabled = true;
                    cropsAndSaplingsEnabled = true;
                    kelpEnabled = true;
                }
                case FARM_ONLY -> {
                    grassAndFoliageEnabled = false;
                    cropsAndSaplingsEnabled = true;
                    kelpEnabled = false;
                }
            }
        }

        return new LegacyNatureFilterMapping(
                grassAndFoliageEnabled,
                cropsAndSaplingsEnabled,
                kelpEnabled,
                vanillaOnlyAcceleration
        );
    }

    private static String toServerToml(SeamlessSleepServerConfig cfg, String modVersion) {
        StringBuilder sb = new StringBuilder(2048);

        sb.append("config_version = ").append(CONFIG_VERSION).append('\n');
        sb.append("mod_version = ").append(toTomlString(modVersion)).append('\n');

        appendSectionGap(sb, 2);
        appendSectionHeader(sb, "sleep");
        appendEntry(sb,
                "Chance to clear rain or thunder after sleeping. Range: 0 to 100. 0=never, 100=always",
                "weather_clear_chance_percent",
                Integer.toString(cfg.sleepWeatherClearChancePercent));
        appendEntry(sb,
                "Sleep animation duration multiplier. Range: 0.25 to 8.0. Default: 1.0",
                "sleepAnimationDurationMultiplier",
                Double.toString(cfg.sleepAnimationDurationMultiplier));
        appendEntry(sb,
                "Ticks a counted player must stay in bed before counting as deep asleep. Range: 0 to 200. 100=vanilla",
                "fall_asleep_delay_ticks",
                Integer.toString(cfg.fallAsleepDelayTicks));
        appendEntry(sb,
                "When true, the Text indicator uses text_indicator_custom_text for normal Night/Day/Storm skips",
                "text_indicator_override",
                Boolean.toString(cfg.overrideOverlayText));
        appendEntry(sb,
                "Plain global Text indicator text. Max 128 characters. Formatting codes are stripped",
                "text_indicator_custom_text",
                toTomlString(cfg.overlayCustomText));
        appendEntry(sb,
                "Sleep eligibility policy. Values: INSOMNIA, VANILLA, DAY_INCLUDED. Default: VANILLA. Legacy manual value ALWAYS is still accepted",
                "sleep_eligibility",
                toTomlString(cfg.sleepEligibility.name()));

        appendSectionGap(sb, 1);
        appendSectionHeader(sb, "easter_eggs");
        appendEntry(sb,
                "Chance for bed sleep to use Made In Heaven. Range: 0 to 100. 0=off",
                "made_in_heaven_chance_percent",
                Integer.toString(cfg.madeInHeavenChancePercent));

        appendSectionGap(sb, 1);
        appendSectionHeader(sb, "compatibility");
        appendEntry(sb,
                "When true, Seamless Sleep disables Better Days sleep features while leaving Better Days day/night duration active",
                "better_days_sleep_compatibility_enabled",
                Boolean.toString(cfg.betterDaysCompatibilityEnabled));

        appendSectionGap(sb, 1);
        appendSectionHeader(sb, "world_sleep_acceleration");
        appendEntry(sb,
                "Acceleration mode during sleep. Values: OFF, AUTOMATIC, MANUAL. Default: AUTOMATIC",
                "mode",
                toTomlString(cfg.worldSleepAcceleration.mode.name()));
        appendEntry(sb,
                "Governor ceiling used by AUTOMATIC. Values: PERFORMANCE, BALANCED, AGGRESSIVE. Default: AGGRESSIVE",
                "automatic_mode",
                toTomlString(cfg.worldSleepAcceleration.automaticMode.name()));
        appendEntry(sb,
                "Players affected by the acceleration area in MANUAL. Values: SLEEPERS, ALL_PLAYERS. AUTOMATIC overrides this based on the automatic mode. Default: ALL_PLAYERS",
                "players_affected",
                toTomlString(cfg.worldSleepAcceleration.playersAffected.name()));
        appendEntry(sb,
                "Manual acceleration radius in chunks. Range: 1 to current simulation distance. Default fallback: 12. The effective runtime value is always clamped by the live server simulation distance",
                "manual_radius_chunks",
                Integer.toString(cfg.worldSleepAcceleration.manualAccelerationRadiusChunks));
        appendEntry(sb,
                "Manual random tick acceleration speed percent. Range: 0 to 100. 0=none, 100=max. Default: 100",
                "manual_speed_percent",
                Integer.toString(cfg.worldSleepAcceleration.manualAccelerationSpeedPercent));
        appendEntry(sb,
                "Enable grass, spreadables, foliage and copper weathering acceleration. Heavy in dense natural worlds. Default: false",
                "grass_and_foliage_acceleration_enabled",
                Boolean.toString(cfg.worldSleepAcceleration.grassAndFoliageAccelerationEnabled));
        appendEntry(sb,
                "Enable crop, farm and sapling acceleration for random ticks",
                "crops_and_saplings_acceleration_enabled",
                Boolean.toString(cfg.worldSleepAcceleration.cropsAndSaplingsAccelerationEnabled));
        appendEntry(sb,
                "Enable bamboo, jungle vines and cave vines/glow berry acceleration. Can be heavy in jungles and lush caves. Default: false",
                "vines_and_bamboo_acceleration_enabled",
                Boolean.toString(cfg.worldSleepAcceleration.vinesAndBambooAccelerationEnabled));
        appendEntry(sb,
                "Enable kelp acceleration for random ticks",
                "kelp_acceleration_enabled",
                Boolean.toString(cfg.worldSleepAcceleration.kelpAccelerationEnabled));
        appendEntry(sb,
                "When true, only vanilla blocks remain eligible for nature acceleration. Default: false",
                "vanilla_only_acceleration",
                Boolean.toString(cfg.worldSleepAcceleration.vanillaOnlyAcceleration));
        appendEntry(sb,
                "When true, nature sections marked irrelevant are rechecked every 20 ticks during the same acceleration session. Default: false",
                "recheck_irrelevant_nature_sections_during_acceleration",
                Boolean.toString(cfg.worldSleepAcceleration.recheckIrrelevantNatureSectionsDuringAcceleration));
        appendEntry(sb,
                "Collect detailed world acceleration counters for /sleep acceleration status. Adds hot-path diagnostic bookkeeping. Default: false",
                "telemetry_enabled",
                Boolean.toString(cfg.worldSleepAcceleration.accelerationTelemetryEnabled));
        appendEntry(sb,
                "Enable furnace, smoker and blast furnace acceleration during sleep",
                "processes_acceleration_enabled",
                Boolean.toString(cfg.worldSleepAcceleration.processesAccelerationEnabled));
        appendEntry(sb,
                "Process acceleration speed percent. Range: 0 to 100. 0=none, 100=max",
                "processes_speed_percent",
                Integer.toString(cfg.worldSleepAcceleration.processesSpeedPercent));

        trimTrailingBlankLines(sb);
        sb.append('\n');
        return sb.toString();
    }

    private static void appendSectionHeader(StringBuilder sb, String name) {
        sb.append('[').append(name).append("]\n");
    }

    private static void appendEntry(StringBuilder sb, String comment, String key, String valueLiteral) {
        sb.append("    #").append(comment).append('\n');
        sb.append("    ").append(key).append(" = ").append(valueLiteral).append("\n\n");
    }

    private static void appendSectionGap(StringBuilder sb, int blankLines) {
        for (int i = 0; i < blankLines; i++) {
            sb.append('\n');
        }
    }

    private static void trimTrailingBlankLines(StringBuilder sb) {
        while (sb.length() > 0) {
            char c = sb.charAt(sb.length() - 1);
            if (c != '\n' && c != '\r') {
                break;
            }
            sb.setLength(sb.length() - 1);
        }
    }

    private static String toTomlString(String value) {
        String safe = (value == null || value.isBlank()) ? "unknown" : value;
        String escaped = safe
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private static WorldSleepAccelerationMode readAccelerationMode(CommentedFileConfig file,
                                                                   List<String> path,
                                                                   String legacyKey,
                                                                   WorldSleepAccelerationMode fallback) {
        Object value = readRaw(file, path, legacyKey);
        if (!(value instanceof String string)) {
            return fallback;
        }
        return switch (string.trim().toUpperCase()) {
            case "NONE", "OFF" -> WorldSleepAccelerationMode.OFF;
            case "AUTO", "AUTOMATIC" -> WorldSleepAccelerationMode.AUTOMATIC;
            case "CUSTOM", "MANUAL" -> WorldSleepAccelerationMode.MANUAL;
            default -> fallback;
        };
    }

    private static WorldSleepAutomaticMode readAutomaticMode(CommentedFileConfig file,
                                                             List<String> path,
                                                             String legacyKey,
                                                             WorldSleepAutomaticMode fallback) {
        Object value = readRaw(file, path, legacyKey);
        if (!(value instanceof String string)) {
            return fallback;
        }
        return switch (string.trim().toUpperCase()) {
            case "ECO", "PERFORMANCE" -> WorldSleepAutomaticMode.PERFORMANCE;
            case "BALANCED" -> WorldSleepAutomaticMode.BALANCED;
            case "AGGRESSIVE" -> WorldSleepAutomaticMode.AGGRESSIVE;
            default -> fallback;
        };
    }

    private static WorldSleepAccelerationPlayersAffected readPlayersAffected(CommentedFileConfig file,
                                                                             List<String> path,
                                                                             String legacyKey,
                                                                             WorldSleepAccelerationPlayersAffected fallback) {
        Object value = readRaw(file, path, legacyKey);
        if (!(value instanceof String string)) {
            return fallback;
        }
        return switch (string.trim().toUpperCase()) {
            case "SLEEPERS", "SLEEPING" -> WorldSleepAccelerationPlayersAffected.SLEEPERS;
            case "ALL", "ALL_PLAYERS", "PLAYERS" -> WorldSleepAccelerationPlayersAffected.ALL_PLAYERS;
            default -> fallback;
        };
    }

    private static double readDouble(CommentedFileConfig file, List<String> path, String legacyKey, double fallback) {
        Object value = readRaw(file, path, legacyKey);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static int readWeatherClearChancePercent(CommentedFileConfig file, int fallback) {
        Object value = file.getRaw(List.of("sleep", "weather_clear_chance_percent"));
        if (value == null) {
            value = file.getRaw(List.of("sleep", "sleepWeatherClearChancePercent"));
        }
        if (value == null) {
            value = file.getRaw("weather_clear_chance_percent");
        }
        if (value == null) {
            value = file.getRaw("sleepWeatherClearChancePercent");
        }
        if (value == null) {
            value = file.getRaw(List.of("sleep", "sleepClearsWeather"));
        }
        if (value == null) {
            value = file.getRaw("sleepClearsWeather");
        }

        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof Boolean bool) {
            return bool ? 100 : 0;
        }
        if (value instanceof String text) {
            String normalized = text.trim();
            if (normalized.equalsIgnoreCase("true")) {
                return 100;
            }
            if (normalized.equalsIgnoreCase("false")) {
                return 0;
            }
            try {
                return Integer.parseInt(normalized);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int readInt(CommentedFileConfig file, List<String> path, String legacyKey, int fallback) {
        Object value = readRaw(file, path, legacyKey);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String readString(CommentedFileConfig file, List<String> path, String legacyKey, String fallback) {
        Object value = readRaw(file, path, legacyKey);
        return value instanceof String string ? string : fallback;
    }

    private static String readString(CommentedFileConfig file,
                                     List<String> path,
                                     List<String> legacyPath,
                                     String legacyKey,
                                     String fallback) {
        Object value = readRaw(file, path, legacyPath, legacyKey);
        return value instanceof String string ? string : fallback;
    }

    private static boolean readBoolean(CommentedFileConfig file, List<String> path, String legacyKey, boolean fallback) {
        Object value = readRaw(file, path, legacyKey);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            if (string.equalsIgnoreCase("true")) {
                return true;
            }
            if (string.equalsIgnoreCase("false")) {
                return false;
            }
        }
        return fallback;
    }

    private static boolean readBoolean(CommentedFileConfig file,
                                       List<String> path,
                                       List<String> legacyPath,
                                       String legacyKey,
                                       boolean fallback) {
        Object value = readRaw(file, path, legacyPath, legacyKey);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            if (string.equalsIgnoreCase("true")) {
                return true;
            }
            if (string.equalsIgnoreCase("false")) {
                return false;
            }
        }
        return fallback;
    }

    private static <E extends Enum<E>> E readEnum(CommentedFileConfig file,
                                                  List<String> path,
                                                  String legacyKey,
                                                  Class<E> enumType,
                                                  E fallback) {
        Object value = readRaw(file, path, legacyKey);
        if (!(value instanceof String string)) {
            return fallback;
        }

        try {
            return Enum.valueOf(enumType, string.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static Object readRaw(CommentedFileConfig file, List<String> path, String legacyKey) {
        Object value = file.getRaw(path);
        return value != null ? value : file.getRaw(legacyKey);
    }

    private static boolean hasRaw(CommentedFileConfig file, List<String> path) {
        return file.getRaw(path) != null;
    }

    private static boolean hasRaw(CommentedFileConfig file, String key) {
        return file.getRaw(key) != null;
    }

    private static Object readRaw(CommentedFileConfig file, List<String> path, List<String> legacyPath, String legacyKey) {
        Object value = file.getRaw(path);
        if (value != null) {
            return value;
        }
        value = file.getRaw(legacyPath);
        return value != null ? value : file.getRaw(legacyKey);
    }

    private static List<String> append(List<String> path, String key) {
        java.util.ArrayList<String> fullPath = new java.util.ArrayList<>(path.size() + 1);
        fullPath.addAll(path);
        fullPath.add(key);
        return fullPath;
    }

    private static int fractionToPercent(double fraction, int fallback) {
        if (Double.isNaN(fraction) || Double.isInfinite(fraction)) {
            return fallback;
        }
        return SeamlessSleepServerConfig.clampInt((int) Math.round(fraction * 100.0D), 0, 100);
    }

    private static Integer readOptionalInt(CommentedFileConfig file, String key) {
        Object value = file.getRaw(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String readOptionalString(CommentedFileConfig file, String key) {
        Object value = file.getRaw(key);
        return value instanceof String string ? string : null;
    }

    private static void logMetadataInfo(Path path, CommentedFileConfig file) {
        Integer fileConfigVersion = readOptionalInt(file, "config_version");
        String fileModVersion = readOptionalString(file, "mod_version");
        String currentModVersion = resolveCurrentModVersion();

        if (fileConfigVersion != null && fileConfigVersion.intValue() != CONFIG_VERSION) {
            Constants.warn(
                    "Server config {} uses config_version {} but current version is {}.",
                    path.getFileName(),
                    fileConfigVersion,
                    CONFIG_VERSION
            );
        }
        if (fileModVersion != null && !fileModVersion.isBlank() && !fileModVersion.equals(currentModVersion)) {
            Constants.info(
                    "Server config {} was generated by mod version {} (current {}).",
                    path.getFileName(),
                    fileModVersion,
                    currentModVersion
            );
        }
    }

    private static String resolveCurrentModVersion() {
        try {
            String version = Services.PLATFORM.getModVersion(Constants.MOD_ID);
            return (version == null || version.isBlank()) ? "unknown" : version;
        } catch (Exception e) {
            Constants.warn("Failed to resolve mod version for config metadata: {}", e.getMessage());
            return "unknown";
        }
    }

    private static void deleteLegacyJsonFiles(Path tomlPath) {
        Path parent = tomlPath.getParent();
        if (parent == null) {
            return;
        }
        deleteIfExists(parent.resolve(LEGACY_JSON_FILE_NAME));
        deleteIfExists(parent.resolve(LEGACY_JSONC_FILE_NAME));
    }

    private static void deleteIfExists(Path file) {
        try {
            if (Files.deleteIfExists(file)) {
                Constants.info("Deleted legacy config file {}", file.getFileName());
            }
        } catch (IOException e) {
            Constants.warn("Failed to delete legacy config {}: {}", file, e.getMessage());
        }
    }

    private record LegacyNatureFilterMapping(boolean grassAndFoliageEnabled,
                                             boolean cropsAndSaplingsEnabled,
                                             boolean kelpEnabled,
                                             boolean vanillaOnlyAcceleration) {
    }

    private record LegacyAccelerationData(WorldSleepAccelerationMode mode,
                                          WorldSleepAutomaticMode automaticMode,
                                          WorldSleepAccelerationPlayersAffected playersAffected,
                                          int manualRadiusChunks,
                                          int manualSpeedPercent,
                                          boolean grassAndFoliageEnabled,
                                          boolean cropsAndSaplingsEnabled,
                                          boolean kelpEnabled,
                                          boolean vanillaOnlyAcceleration,
                                          boolean processesAccelerationEnabled,
                                          int processesSpeedPercent) {
    }

    private static final class LoadResult {
        private final SeamlessSleepServerConfig config;
        private final ReloadResult status;

        private LoadResult(SeamlessSleepServerConfig config, ReloadResult status) {
            this.config = config;
            this.status = status;
        }
    }
}
