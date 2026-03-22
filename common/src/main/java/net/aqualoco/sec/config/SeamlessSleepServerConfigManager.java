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
    private static final int CONFIG_VERSION = 2;

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
        return cfg;
    }

    private static String toServerToml(SeamlessSleepServerConfig cfg, String modVersion) {
        StringBuilder sb = new StringBuilder(512);

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

    private static Object readRaw(CommentedFileConfig file, List<String> path, String legacyKey) {
        Object value = file.getRaw(path);
        return value != null ? value : file.getRaw(legacyKey);
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
