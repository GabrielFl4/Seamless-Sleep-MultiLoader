package net.aqualoco.sec.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Handles disk persistence for the client config JSON.
public final class SeamlessSleepClientConfigManager {

    private static final String FILE_NAME = "seamless_sleep.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static SeamlessSleepClientConfig config = defaultConfig();
    private static Path configPath;

    private SeamlessSleepClientConfigManager() {
    }

    public static void init() {
        configPath = Services.PLATFORM.getConfigDir().resolve(FILE_NAME);
        config = loadOrCreate(configPath);
        Constants.setDebugLogsEnabled(config.debugLogsEnabled);
    }

    public static SeamlessSleepClientConfig get() {
        return config;
    }

    private static SeamlessSleepClientConfig loadOrCreate(Path path) {
        if (Files.notExists(path)) {
            SeamlessSleepClientConfig cfg = defaultConfig();
            cfg.clamp();
            save(path, cfg);
            return cfg;
        }

        try {
            String json = stripJsonLineComments(Files.readString(path, StandardCharsets.UTF_8));
            SeamlessSleepClientConfig cfg = GSON.fromJson(json, SeamlessSleepClientConfig.class);
            if (cfg == null) {
                cfg = defaultConfig();
            }
            cfg.clamp();
            save(path, cfg);
            return cfg;
        } catch (Exception e) {
            Constants.warn("Failed to read client config {}, using defaults. Error: {}", path, e.getMessage());
            SeamlessSleepClientConfig cfg = defaultConfig();
            save(path, cfg);
            return cfg;
        }
    }

    public static void save() {
        if (configPath == null || config == null) {
            return;
        }
        Constants.setDebugLogsEnabled(config.debugLogsEnabled);
        save(configPath, config);
    }

    private static void save(Path path, SeamlessSleepClientConfig cfg) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, toCommentedJson(cfg), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.warn("Failed to save client config {}: {}", path, e.getMessage());
        }
    }

    private static SeamlessSleepClientConfig defaultConfig() {
        return new SeamlessSleepClientConfig();
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

    private static String toCommentedJson(SeamlessSleepClientConfig cfg) {
        StringBuilder sb = new StringBuilder(768);
        sb.append("{\n");
        appendComment(sb, "Sleep overlay on/off. Range: true | false. Default: true");
        appendBool(sb, "sleepOverlayEnabled", cfg.sleepOverlayEnabled, true);

        appendComment(sb, "Overlay darkness while sleeping. Range: 0.0 to 1.0. Default: 0.35");
        appendDouble(sb, "sleepOverlayDarknessMultiplier", cfg.sleepOverlayDarknessMultiplier, true);

        appendComment(sb, "Chat text opacity multiplier while sleeping. Range: 0.0 to 1.0. Default: 0.5");
        appendDouble(sb, "sleepChatTextOpacityMultiplier", cfg.sleepChatTextOpacityMultiplier, true);

        appendComment(sb, "Chat background opacity multiplier while sleeping. Range: 0.0 to 1.0. Default: 0.4");
        appendDouble(sb, "sleepChatBackgroundOpacityMultiplier", cfg.sleepChatBackgroundOpacityMultiplier, true);

        appendComment(sb, "Global chat opacity multiplier while sleeping. Range: 0.1 to 2.0. Default: 1.0");
        appendDouble(sb, "sleepChatOpacityMultiplier", cfg.sleepChatOpacityMultiplier, true);

        appendComment(sb, "Max chat lines shown in bed chat. Range: integer 0 to 12. Default: 4");
        appendInt(sb, "sleepChatMaxLines", cfg.sleepChatMaxLines, true);

        appendComment(sb, "Camera tilt angle applied during sleep. Range: -90.0 to 90.0. Default: 10.0");
        appendDouble(sb, "sleepCameraTiltDegrees", cfg.sleepCameraTiltDegrees, true);

        appendComment(sb, "Replay/recording compatibility mode. Range: true | false. Default: true");
        appendBool(sb, "replayCompatibilityEnabled", cfg.replayCompatibilityEnabled, true);

        appendComment(sb, "Enable verbose debug logs. Range: true | false. Default: false");
        appendBool(sb, "debugLogsEnabled", cfg.debugLogsEnabled, false);
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendComment(StringBuilder sb, String text) {
        sb.append("  // ").append(text).append('\n');
    }

    private static void appendBool(StringBuilder sb, String key, boolean value, boolean comma) {
        sb.append("  \"").append(key).append("\": ").append(value);
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static void appendInt(StringBuilder sb, String key, int value, boolean comma) {
        sb.append("  \"").append(key).append("\": ").append(value);
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static void appendDouble(StringBuilder sb, String key, double value, boolean comma) {
        if (!Double.isFinite(value)) {
            value = 0.0D;
        }
        sb.append("  \"").append(key).append("\": ").append(Double.toString(value));
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }
}
