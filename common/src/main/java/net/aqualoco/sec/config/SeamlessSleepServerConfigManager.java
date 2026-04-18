package net.aqualoco.sec.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Loads, validates, and saves the server config TOML with reload status reporting.
public final class SeamlessSleepServerConfigManager {

    private static final String FILE_NAME = "seamless_sleep-server.toml";
    private static final String LEGACY_JSON_FILE_NAME = "seamless_sleep-server.json";
    private static final String LEGACY_JSONC_FILE_NAME = "seamless_sleep-server.jsonc";
    private static final int CONFIG_VERSION = 3;

    // Distinguishes a clean reload from recovery/fallback scenarios.
    public enum ReloadResult {
        SUCCESS,
        CREATED,
        ERROR
    }

    private static SeamlessSleepServerConfig config = defaultConfig();
    private static Path configPath;

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
        if (configPath == null) {
            configPath = Services.PLATFORM.getConfigDir().resolve(FILE_NAME);
        }
        LoadResult result = loadOrCreate(configPath);
        config = result.config;
        return result.status;
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
            save(path, cfg); // Canonicalize order/comments and keep metadata fresh.
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

    private static CommentedFileConfig openConfig(Path path) {
        return CommentedFileConfig.builder(path, TomlFormat.instance())
                .sync()
                .preserveInsertionOrder()
                .build();
    }

    private static SeamlessSleepServerConfig readServerConfig(CommentedFileConfig file) {
        SeamlessSleepServerConfig cfg = defaultConfig();
        cfg.sleepWeatherClearChancePercent = readWeatherClearChancePercent(file, cfg.sleepWeatherClearChancePercent);
        cfg.sleepAnimationDurationMultiplier = readDouble(file, List.of("sleep", "sleepAnimationDurationMultiplier"), "sleepAnimationDurationMultiplier", cfg.sleepAnimationDurationMultiplier);
        readWorldSleepAcceleration(file, cfg.worldSleepAcceleration);
        cfg.clamp();
        return cfg;
    }

    private static String toServerToml(SeamlessSleepServerConfig cfg, String modVersion) {
        StringBuilder sb = new StringBuilder(2048);

        sb.append("config_version = ").append(CONFIG_VERSION).append('\n');
        sb.append("mod_version = ").append(toTomlString(modVersion)).append('\n');

        appendSectionGap(sb, 2);
        appendSectionHeader(sb, "sleep");
        appendEntry(sb,
                "Chance to clear rain/thunder after sleeping. Range: 0 to 100. 0=never, 100=always",
                "weather_clear_chance_percent",
                Integer.toString(cfg.sleepWeatherClearChancePercent));
        appendEntry(sb,
                "Sleep animation duration multiplier. Range: 0.25 to 8.0. Default: 1.0",
                "sleepAnimationDurationMultiplier",
                Double.toString(cfg.sleepAnimationDurationMultiplier));

        appendSectionGap(sb, 1);
        appendSectionHeader(sb, "world_sleep_acceleration");
        appendEntry(sb,
                "Global world acceleration mode during sleep. Values: OFF, AUTO, CUSTOM",
                "mode",
                toTomlString(cfg.worldSleepAcceleration.mode.name()));
        appendEntry(sb,
                "Preset bundle for acceleration tuning. Values: ECO, BALANCED, AGGRESSIVE, CUSTOM",
                "preset",
                toTomlString(cfg.worldSleepAcceleration.preset.name()));
        appendEntry(sb,
                "Enable extra random tick / nature acceleration while sleep animation is active",
                "random_tick_acceleration_enabled",
                Boolean.toString(cfg.worldSleepAcceleration.randomTickAccelerationEnabled));
        appendEntry(sb,
                "Enable furnace, smoker and blast furnace acceleration while sleep animation is active",
                "process_acceleration_enabled",
                Boolean.toString(cfg.worldSleepAcceleration.processAccelerationEnabled));
        appendEntry(sb,
                "AUTO governor aggressiveness. Values: CONSERVATIVE, BALANCED, AGGRESSIVE",
                "governor_aggressiveness",
                toTomlString(cfg.worldSleepAcceleration.governorAggressiveness.name()));
        appendEntry(sb,
                "Nature filter profile. Values: ALL, VANILLA_ONLY, FARM_ONLY",
                "nature_filter_profile",
                toTomlString(cfg.worldSleepAcceleration.natureFilterProfile.name()));

        appendSectionGap(sb, 1);
        appendSectionHeader(sb, "world_sleep_acceleration.nature");
        appendEntry(sb,
                "Base chunk radius around each active player for extra random ticks. Use 0 to follow simulation distance",
                "base_radius_chunks",
                Integer.toString(cfg.worldSleepAcceleration.nature.baseRadiusChunks));
        appendEntry(sb,
                "AUTO minimum chunk radius for nature acceleration. Use 0 to follow simulation distance",
                "auto_min_radius_chunks",
                Integer.toString(cfg.worldSleepAcceleration.nature.autoMinRadiusChunks));
        appendEntry(sb,
                "Base fraction of the logical world sleep rate applied to nature acceleration. Range: 0.0 to 1.0",
                "base_rate_fraction",
                Double.toString(cfg.worldSleepAcceleration.nature.baseRateFraction));
        appendEntry(sb,
                "AUTO minimum fraction of the logical world sleep rate applied to nature acceleration. Range: 0.0 to 1.0",
                "auto_min_rate_fraction",
                Double.toString(cfg.worldSleepAcceleration.nature.autoMinRateFraction));

        appendSectionGap(sb, 1);
        appendSectionHeader(sb, "world_sleep_acceleration.process");
        appendEntry(sb,
                "Base chunk radius around each active player for process acceleration. Use 0 to follow simulation distance",
                "base_radius_chunks",
                Integer.toString(cfg.worldSleepAcceleration.process.baseRadiusChunks));
        appendEntry(sb,
                "AUTO minimum chunk radius for process acceleration. Use 0 to follow simulation distance",
                "auto_min_radius_chunks",
                Integer.toString(cfg.worldSleepAcceleration.process.autoMinRadiusChunks));
        appendEntry(sb,
                "Base fraction of the logical world sleep rate applied to process acceleration. Range: 0.0 to 1.0",
                "base_rate_fraction",
                Double.toString(cfg.worldSleepAcceleration.process.baseRateFraction));
        appendEntry(sb,
                "AUTO minimum fraction of the logical world sleep rate applied to process acceleration. Range: 0.0 to 1.0",
                "auto_min_rate_fraction",
                Double.toString(cfg.worldSleepAcceleration.process.autoMinRateFraction));

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

    private static double readDouble(CommentedFileConfig file, List<String> path, String legacyKey, double fallback) {
        Object value = readRaw(file, path, legacyKey);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static void readWorldSleepAcceleration(CommentedFileConfig file, WorldSleepAccelerationConfig cfg) {
        cfg.mode = readEnum(
                file,
                List.of("world_sleep_acceleration", "mode"),
                "worldSleepAccelerationMode",
                WorldSleepAccelerationMode.class,
                cfg.mode
        );
        cfg.preset = readEnum(
                file,
                List.of("world_sleep_acceleration", "preset"),
                "worldSleepAccelerationPreset",
                WorldSleepAccelerationPreset.class,
                cfg.preset
        );
        cfg.randomTickAccelerationEnabled = readBoolean(
                file,
                List.of("world_sleep_acceleration", "random_tick_acceleration_enabled"),
                "worldSleepAccelerationRandomTickEnabled",
                cfg.randomTickAccelerationEnabled
        );
        cfg.processAccelerationEnabled = readBoolean(
                file,
                List.of("world_sleep_acceleration", "process_acceleration_enabled"),
                "worldSleepAccelerationProcessEnabled",
                cfg.processAccelerationEnabled
        );
        cfg.governorAggressiveness = readEnum(
                file,
                List.of("world_sleep_acceleration", "governor_aggressiveness"),
                "worldSleepAccelerationGovernorAggressiveness",
                WorldSleepAccelerationGovernorAggressiveness.class,
                cfg.governorAggressiveness
        );
        cfg.natureFilterProfile = readEnum(
                file,
                List.of("world_sleep_acceleration", "nature_filter_profile"),
                "worldSleepAccelerationNatureFilterProfile",
                WorldSleepNatureFilterProfile.class,
                cfg.natureFilterProfile
        );
        readModuleConfig(file, List.of("world_sleep_acceleration", "nature"), "worldSleepAccelerationNature", cfg.nature);
        readModuleConfig(file, List.of("world_sleep_acceleration", "process"), "worldSleepAccelerationProcess", cfg.process);
    }

    private static void readModuleConfig(CommentedFileConfig file,
                                         List<String> pathPrefix,
                                         String legacyPrefix,
                                         WorldSleepAccelerationModuleConfig cfg) {
        cfg.baseRadiusChunks = readInt(file, append(pathPrefix, "base_radius_chunks"), legacyPrefix + "BaseRadiusChunks", cfg.baseRadiusChunks);
        cfg.autoMinRadiusChunks = readInt(file, append(pathPrefix, "auto_min_radius_chunks"), legacyPrefix + "AutoMinRadiusChunks", cfg.autoMinRadiusChunks);
        cfg.baseRateFraction = readDouble(file, append(pathPrefix, "base_rate_fraction"), legacyPrefix + "BaseRateFraction", cfg.baseRateFraction);
        cfg.autoMinRateFraction = readDouble(file, append(pathPrefix, "auto_min_rate_fraction"), legacyPrefix + "AutoMinRateFraction", cfg.autoMinRateFraction);
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

    private static List<String> append(List<String> path, String key) {
        java.util.ArrayList<String> fullPath = new java.util.ArrayList<>(path.size() + 1);
        fullPath.addAll(path);
        fullPath.add(key);
        return fullPath;
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

    // Small wrapper so reload can return both config data and a status flag.
    private static final class LoadResult {
        private final SeamlessSleepServerConfig config;
        private final ReloadResult status;

        private LoadResult(SeamlessSleepServerConfig config, ReloadResult status) {
            this.config = config;
            this.status = status;
        }
    }
}
