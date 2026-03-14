package net.aqualoco.sec.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

// Loads, validates, and saves the server config TOML with reload status reporting.
public final class SeamlessSleepServerConfigManager {

    private static final String FILE_NAME = "seamless_sleep-server.toml";
    private static final String LEGACY_FILE_JSON = "seamless_sleep-server.json";
    private static final String LEGACY_FILE_JSONC = "seamless_sleep-server.jsonc";
    private static final int CONFIG_VERSION = 1;

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
        deleteLegacyFiles(path);

        if (Files.notExists(path)) {
            SeamlessSleepServerConfig cfg = defaultConfig();
            cfg.clamp();
            save(path, cfg);
            return new LoadResult(cfg, ReloadResult.CREATED);
        }

        try (CommentedFileConfig fileConfig = CommentedFileConfig.builder(path, TomlFormat.instance())
                .sync()
                .preserveInsertionOrder()
                .build()) {
            fileConfig.load();
            SeamlessSleepServerConfig cfg = readConfig(fileConfig);
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

    private static SeamlessSleepServerConfig readConfig(CommentedFileConfig fileConfig) {
        SeamlessSleepServerConfig cfg = defaultConfig();

        cfg.sleepClearsWeather = readBoolean(
                fileConfig,
                cfg.sleepClearsWeather,
                "sleep.clears_weather",
                "sleepClearsWeather"
        );
        cfg.sleepAnimationDurationMultiplier = readDouble(
                fileConfig,
                cfg.sleepAnimationDurationMultiplier,
                "sleep.duration_multiplier",
                "sleepAnimationDurationMultiplier"
        );

        return cfg;
    }

    private static void save(Path path, SeamlessSleepServerConfig cfg) {
        cfg.clamp();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    toToml(cfg),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            Constants.warn("Failed to save server config {}: {}", path, e.getMessage());
        }
    }

    private static String toToml(SeamlessSleepServerConfig cfg) {
        StringBuilder toml = new StringBuilder();
        toml.append("# Seamless Sleep server config").append('\n');
        toml.append("config_version = ").append(CONFIG_VERSION).append('\n');
        toml.append("mod_version = \"").append(escapeToml(resolveModVersion())).append('"').append('\n');
        toml.append('\n');

        toml.append("[sleep]").append('\n');
        toml.append("clears_weather = ").append(cfg.sleepClearsWeather).append('\n');
        toml.append("duration_multiplier = ").append(cfg.sleepAnimationDurationMultiplier).append('\n');

        return toml.toString();
    }

    private static Boolean readBoolean(CommentedFileConfig fileConfig, Boolean fallback, String... keys) {
        for (String key : keys) {
            if (!fileConfig.contains(key)) {
                continue;
            }
            Object rawValue = fileConfig.get(key);
            Boolean parsed = parseBoolean(rawValue);
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    private static double readDouble(CommentedFileConfig fileConfig, double fallback, String... keys) {
        for (String key : keys) {
            if (!fileConfig.contains(key)) {
                continue;
            }
            Object rawValue = fileConfig.get(key);
            Double parsed = parseDouble(rawValue);
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    private static Boolean parseBoolean(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        if (value instanceof String stringValue) {
            if ("true".equalsIgnoreCase(stringValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(stringValue)) {
                return false;
            }
        }
        return null;
    }

    private static Double parseDouble(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void deleteLegacyFiles(Path tomlPath) {
        Path parent = tomlPath.getParent();
        if (parent == null) {
            return;
        }
        deleteLegacyFile(parent.resolve(LEGACY_FILE_JSON));
        deleteLegacyFile(parent.resolve(LEGACY_FILE_JSONC));
    }

    private static void deleteLegacyFile(Path path) {
        try {
            if (Files.deleteIfExists(path)) {
                Constants.info("Deleted legacy server config {}", path);
            }
        } catch (IOException e) {
            Constants.warn("Failed to delete legacy server config {}: {}", path, e.getMessage());
        }
    }

    private static String resolveModVersion() {
        String modVersion = Services.PLATFORM.getModVersion(Constants.MOD_ID);
        if (modVersion == null || modVersion.isBlank()) {
            return "unknown";
        }
        return modVersion;
    }

    private static String escapeToml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static SeamlessSleepServerConfig defaultConfig() {
        return new SeamlessSleepServerConfig();
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
