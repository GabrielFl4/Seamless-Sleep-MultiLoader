package net.aqualoco.sec.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Loads, validates, and saves the server config TOML with reload status reporting.
public final class SeamlessSleepServerConfigManager {

    private static final String FILE_NAME = "seamless_sleep-server.toml";
    private static final String LEGACY_JSON_FILE = "seamless_sleep-server.json";
    private static final String LEGACY_JSONC_FILE = "seamless_sleep-server.jsonc";
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

        try (CommentedFileConfig fileConfig = createTomlConfig(path)) {
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

    private static void save(Path path, SeamlessSleepServerConfig cfg) {
        cfg.clamp();

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (CommentedFileConfig fileConfig = createTomlConfig(path)) {
                fileConfig.clear();

                fileConfig.setComment("config_version", "Schema version for this config.");
                fileConfig.set("config_version", CONFIG_VERSION);
                fileConfig.setComment("mod_version", "Mod version that last wrote this file.");
                fileConfig.set("mod_version", resolveModVersion());

                fileConfig.setComment("sleep", "Server-authoritative sleep settings.");
                fileConfig.set("sleep.clears_weather", cfg.sleepClearsWeather);
                fileConfig.set("sleep.duration_multiplier", cfg.sleepAnimationDurationMultiplier);

                fileConfig.save();
            }
        } catch (IOException e) {
            Constants.warn("Failed to save server config {}: {}", path, e.getMessage());
        } catch (Exception e) {
            Constants.warn("Failed to write server TOML {}: {}", path, e.getMessage());
        }
    }

    private static SeamlessSleepServerConfig readConfig(CommentedFileConfig fileConfig) {
        SeamlessSleepServerConfig cfg = defaultConfig();

        cfg.sleepClearsWeather = readBoolean(
                fileConfig,
                cfg.sleepClearsWeather,
                "sleep.clears_weather",
                "sleep.sleep_clears_weather",
                "sleepClearsWeather"
        );
        cfg.sleepAnimationDurationMultiplier = readDouble(
                fileConfig,
                cfg.sleepAnimationDurationMultiplier,
                "sleep.duration_multiplier",
                "sleep.sleep_animation_duration_multiplier",
                "sleepAnimationDurationMultiplier"
        );

        return cfg;
    }

    private static Boolean readBoolean(CommentedFileConfig fileConfig, Boolean fallback, String... keys) {
        for (String key : keys) {
            Boolean parsed = parseBoolean(fileConfig.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    private static double readDouble(CommentedFileConfig fileConfig, double fallback, String... keys) {
        for (String key : keys) {
            Double parsed = parseDouble(fileConfig.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    private static Boolean parseBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                return false;
            }
        }
        return null;
    }

    private static Double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
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
        deleteLegacyFile(parent.resolve(LEGACY_JSON_FILE));
        deleteLegacyFile(parent.resolve(LEGACY_JSONC_FILE));
    }

    private static void deleteLegacyFile(Path legacyPath) {
        if (Files.notExists(legacyPath)) {
            return;
        }
        try {
            Files.delete(legacyPath);
            Constants.info("Deleted legacy config file {}", legacyPath);
        } catch (IOException e) {
            Constants.warn("Failed to delete legacy config file {}: {}", legacyPath, e.getMessage());
        }
    }

    private static String resolveModVersion() {
        String version = Services.PLATFORM.getModVersion(Constants.MOD_ID);
        if (version == null || version.isBlank()) {
            return "unknown";
        }
        return version;
    }

    private static CommentedFileConfig createTomlConfig(Path path) {
        return CommentedFileConfig.builder(path, TomlFormat.instance())
                .sync()
                .preserveInsertionOrder()
                .build();
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
