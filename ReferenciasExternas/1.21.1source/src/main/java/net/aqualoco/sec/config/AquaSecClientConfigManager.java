package net.aqualoco.sec.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.aqualoco.sec.AquaSec;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AquaSecClientConfigManager {

    private static final String FILE_NAME = "seamless_sleep.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static AquaSecClientConfig config = defaultConfig();
    private static Path configPath;

    private AquaSecClientConfigManager() {}

    public static void init() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        config = loadOrCreate(configPath);
    }

    public static AquaSecClientConfig get() {
        return config;
    }

    private static AquaSecClientConfig loadOrCreate(Path path) {
        if (Files.notExists(path)) {
            AquaSecClientConfig cfg = defaultConfig();
            cfg.clamp();
            save(path, cfg);
            return cfg;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            AquaSecClientConfig cfg = GSON.fromJson(reader, AquaSecClientConfig.class);
            if (cfg == null) {
                cfg = defaultConfig();
            }
            cfg.clamp();
            save(path, cfg); // rewrite to ensure normalized values
            return cfg;
        } catch (Exception e) {
            AquaSec.LOGGER.warn("Falha ao ler config {}, usando padrao. Erro: {}", path, e.getMessage());
            AquaSecClientConfig cfg = defaultConfig();
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

    private static void save(Path path, AquaSecClientConfig cfg) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(cfg, writer);
            }
        } catch (IOException e) {
            AquaSec.LOGGER.warn("Nao foi possivel salvar config {}: {}", path, e.getMessage());
        }
    }

    private static AquaSecClientConfig defaultConfig() {
        return new AquaSecClientConfig();
    }
}
