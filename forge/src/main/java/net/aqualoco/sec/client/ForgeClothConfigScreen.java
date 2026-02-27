package net.aqualoco.sec.client;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

// Builds the Forge config UI with Cloth Config; the entry setup follows Cloth docs examples.
public final class ForgeClothConfigScreen {
    private static final int DECIMAL_STEP_SCALE = 20; // 0.05 steps

    private ForgeClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        SeamlessSleepClientConfig cfg = SeamlessSleepClientConfigManager.get();
        cfg.clamp();
        SeamlessSleepServerConfig serverCfg = SeamlessSleepServerConfigManager.get();
        serverCfg.clamp();

        Minecraft client = Minecraft.getInstance();
        boolean connectedToRemote = client.getConnection() != null && !client.hasSingleplayerServer();
        boolean canEditServerConfig = !connectedToRemote;
        boolean serverToggleValue = canEditServerConfig
                ? serverCfg.sleepClearsWeather
                : SeamlessSleepServerConfigSnapshot.getSleepClearsWeather();
        double serverDurationValue = canEditServerConfig
                ? serverCfg.sleepAnimationDurationMultiplier
                : SeamlessSleepServerConfigSnapshot.getSleepAnimationDurationMultiplier();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.seamlesssleep.title"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory overlay = builder.getOrCreateCategory(
                Component.translatable("config.seamlesssleep.category.overlay")
        );
        ConfigCategory chat = builder.getOrCreateCategory(
                Component.translatable("config.seamlesssleep.category.chat")
        );
        ConfigCategory camera = builder.getOrCreateCategory(
                Component.translatable("config.seamlesssleep.category.camera")
        );
        ConfigCategory misc = builder.getOrCreateCategory(
                Component.translatable("config.seamlesssleep.category.misc")
        );
        ConfigCategory sleep = builder.getOrCreateCategory(
                Component.translatable("config.seamlesssleep.category.sleep")
        );

        overlay.addEntry(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.seamlesssleep.overlay.enabled"),
                        cfg.sleepOverlayEnabled
                )
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.seamlesssleep.overlay.enabled.desc"))
                .setSaveConsumer(value -> cfg.sleepOverlayEnabled = value)
                .build()
        );

        overlay.addEntry(buildScaledSlider(
                entryBuilder,
                Component.translatable("config.seamlesssleep.overlay.darkness"),
                cfg.sleepOverlayDarknessMultiplier,
                0.35D,
                0.0D,
                1.0D,
                new Component[]{Component.translatable("config.seamlesssleep.overlay.darkness.desc")},
                value -> cfg.sleepOverlayDarknessMultiplier = value,
                true
        ));

        chat.addEntry(buildScaledSlider(
                entryBuilder,
                Component.translatable("config.seamlesssleep.chat.dim_multiplier"),
                cfg.sleepChatOpacityMultiplier,
                1.0D,
                0.1D,
                2.0D,
                new Component[]{Component.translatable("config.seamlesssleep.chat.dim_multiplier.desc")},
                value -> cfg.sleepChatOpacityMultiplier = value,
                true
        ));

        chat.addEntry(entryBuilder
                .startIntSlider(
                        Component.translatable("config.seamlesssleep.chat.max_lines"),
                        cfg.sleepChatMaxLines,
                        0,
                        12
                )
                .setDefaultValue(4)
                .setTooltip(Component.translatable("config.seamlesssleep.chat.max_lines.desc"))
                .setSaveConsumer(value -> cfg.sleepChatMaxLines = value)
                .build()
        );

        camera.addEntry(entryBuilder
                .startIntSlider(
                        Component.translatable("config.seamlesssleep.camera.tilt_degrees"),
                        (int) Math.round(cfg.sleepCameraTiltDegrees),
                        -90,
                        90
                )
                .setDefaultValue(10)
                .setTextGetter(value -> Component.literal(value + " º"))
                .setTooltip(Component.translatable("config.seamlesssleep.camera.tilt_degrees.desc"))
                .setSaveConsumer(value -> cfg.sleepCameraTiltDegrees = value)
                .build()
        );

        misc.addEntry(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.seamlesssleep.misc.debug_logs"),
                        cfg.debugLogsEnabled
                )
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.seamlesssleep.misc.debug_logs.desc"))
                .setSaveConsumer(value -> cfg.debugLogsEnabled = value)
                .build()
        );

        misc.addEntry(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.seamlesssleep.misc.replay_compatibility"),
                        cfg.replayCompatibilityEnabled
                )
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.seamlesssleep.misc.replay_compatibility.desc"))
                .setSaveConsumer(value -> cfg.replayCompatibilityEnabled = value)
                .build()
        );

        Component clearsWeatherDesc =
                Component.translatable("config.seamlesssleep.sleep.clears_weather.desc");
        Component durationDesc =
                Component.translatable("config.seamlesssleep.sleep.duration_multiplier.desc");
        Component serverControlled =
                Component.translatable("config.seamlesssleep.server_controlled");

        sleep.addEntry(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.seamlesssleep.sleep.clears_weather"),
                        serverToggleValue
                )
                .setDefaultValue(true)
                .setTooltip(canEditServerConfig
                        ? new Component[]{clearsWeatherDesc}
                        : new Component[]{clearsWeatherDesc, serverControlled})
                .setSaveConsumer(value -> {
                    if (canEditServerConfig) {
                        serverCfg.sleepClearsWeather = value;
                    }
                })
                .build()
        );

        sleep.addEntry(buildScaledSlider(
                entryBuilder,
                Component.translatable("config.seamlesssleep.sleep.duration_multiplier"),
                serverDurationValue,
                1.0D,
                0.25D,
                8.0D,
                canEditServerConfig
                        ? new Component[]{durationDesc}
                        : new Component[]{durationDesc, serverControlled},
                value -> {
                    if (canEditServerConfig) {
                        serverCfg.sleepAnimationDurationMultiplier = value;
                    }
                },
                canEditServerConfig
        ));

        builder.setSavingRunnable(() -> {
            cfg.clamp();
            SeamlessSleepClientConfigManager.save();
            if (canEditServerConfig) {
                serverCfg.clamp();
                SeamlessSleepServerConfigManager.save();
            }
        });

        return builder.build();
    }

    private static AbstractConfigListEntry<?> buildScaledSlider(
            ConfigEntryBuilder entryBuilder,
            Component name,
            double value,
            double defaultValue,
            double min,
            double max,
            Component[] tooltip,
            Consumer<Double> saveConsumer,
            boolean available
    ) {
        // Cloth sliders are int-based, so decimal values are mapped to a fixed integer scale.
        int sliderMin = toScaled(min);
        int sliderMax = toScaled(max);
        int sliderValue = toScaled(value);
        int sliderDefault = toScaled(defaultValue);

        return entryBuilder
                .startIntSlider(name, sliderValue, sliderMin, sliderMax)
                .setDefaultValue(sliderDefault)
                .setTextGetter(raw -> Component.literal(formatScaled(raw)))
                .setTooltip(tooltip)
                .setSaveConsumer(raw -> saveConsumer.accept(fromScaled(raw)))
                .build();
    }

    private static int toScaled(double value) {
        return (int) Math.round(value * DECIMAL_STEP_SCALE);
    }

    private static double fromScaled(int value) {
        return value / (double) DECIMAL_STEP_SCALE;
    }

    private static String formatScaled(int value) {
        return String.format(Locale.ROOT, "%.2f", fromScaled(value));
    }
}
