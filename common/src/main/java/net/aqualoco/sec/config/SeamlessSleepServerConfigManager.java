package net.aqualoco.sec.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Loads, validates, and saves the server config JSON with reload status reporting.
public final class SeamlessSleepServerConfigManager {

    private static final String FILE_NAME = "seamless_sleep-server.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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
        if (Files.notExists(path)) {
            SeamlessSleepServerConfig cfg = defaultConfig();
            cfg.clamp();
            save(path, cfg);
            return new LoadResult(cfg, ReloadResult.CREATED);
        }

        try {
            String json = stripJsonLineComments(Files.readString(path, StandardCharsets.UTF_8));
            SeamlessSleepServerConfig cfg = GSON.fromJson(json, SeamlessSleepServerConfig.class);
            if (cfg == null) {
                Constants.warn("Server config {} is empty or invalid, using defaults.", path);
                cfg = defaultConfig();
                cfg.clamp();
                save(path, cfg);
                return new LoadResult(cfg, ReloadResult.ERROR);
            }
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
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, toCommentedJson(cfg), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.warn("Failed to save server config {}: {}", path, e.getMessage());
        }
    }

    private static SeamlessSleepServerConfig defaultConfig() {
        return new SeamlessSleepServerConfig();
    }

    private static String stripJsonLineComments(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.stripLeading().startsWith("//")) {
                out.append(line);
                if (i < lines.length - 1) {
                    out.append('\n');
                }
            }
        }
        return out.toString();
    }

    private static String toCommentedJson(SeamlessSleepServerConfig cfg) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\n");
        appendComment(sb, "Clear rain/thunder after sleeping. Range: true | false. Default: true");
        sb.append("  \"sleepClearsWeather\": ").append(Boolean.TRUE.equals(cfg.sleepClearsWeather)).append(",\n");

        appendComment(sb, "Sleep animation duration multiplier. Range: 0.25 to 8.0. Default: 1.0");
        double value = cfg.sleepAnimationDurationMultiplier;
        if (!Double.isFinite(value)) {
            value = 1.0D;
        }
        sb.append("  \"sleepAnimationDurationMultiplier\": ").append(Double.toString(value)).append('\n');
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendComment(StringBuilder sb, String text) {
        sb.append("  // ").append(text).append('\n');
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
