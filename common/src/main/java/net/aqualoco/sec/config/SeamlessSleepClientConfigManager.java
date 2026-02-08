package net.aqualoco.sec.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
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

        try (Reader reader = Files.newBufferedReader(path)) {
            SeamlessSleepClientConfig cfg = GSON.fromJson(reader, SeamlessSleepClientConfig.class);
            if (cfg == null) {
                cfg = defaultConfig();
            }
            cfg.clamp();
            save(path, cfg);
            return cfg;
        } catch (Exception e) {
            Constants.LOG.warn("Falha ao ler config {}, usando padrao. Erro: {}", path, e.getMessage());
            SeamlessSleepClientConfig cfg = defaultConfig();
            save(path, cfg);
            return cfg;
        }
    }

    public static void save() {
        if (configPath == null || config == null) {
            return;
        }
        save(configPath, config);
    }

    private static void save(Path path, SeamlessSleepClientConfig cfg) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(cfg, writer);
            }
        } catch (IOException e) {
            Constants.LOG.warn("Nao foi possivel salvar config {}: {}", path, e.getMessage());
        }
    }

    private static SeamlessSleepClientConfig defaultConfig() {
        return new SeamlessSleepClientConfig();
    }
}
