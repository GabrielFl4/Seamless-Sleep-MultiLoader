package net.aqualoco.sec.client;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// Builds the NeoForge config UI with YACL; the option/controller setup follows patterns from YACL docs examples.
public final class NeoForgeYaclConfigScreen {

    private NeoForgeYaclConfigScreen() {
    }

    public static Screen create(Screen parent) {
        SeamlessSleepClientConfig cfg = SeamlessSleepClientConfigManager.get();
        cfg.clamp();
        SeamlessSleepServerConfig serverCfg = SeamlessSleepServerConfigManager.get();
        serverCfg.clamp();

        Minecraft client = Minecraft.getInstance();
        boolean connectedToRemote = client.getConnection() != null && !client.hasSingleplayerServer();
        // Remote servers sync these values, so server-controlled options stay read-only in the UI.
        boolean canEditServerConfig = !connectedToRemote;

        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("config.seamlesssleep.title"))
                .category(
                        ConfigCategory.createBuilder()
                                .name(Component.translatable("config.seamlesssleep.category.overlay"))
                                .option(buildToggle(
                                        Component.translatable("config.seamlesssleep.overlay.enabled"),
                                        Component.translatable("config.seamlesssleep.overlay.enabled.desc"),
                                        Component.empty(),
                                        true,
                                        () -> cfg.sleepOverlayEnabled,
                                        val -> cfg.sleepOverlayEnabled = val,
                                        true
                                ))
                                .option(buildDoubleSlider(
                                        Component.translatable("config.seamlesssleep.overlay.darkness"),
                                        Component.translatable("config.seamlesssleep.overlay.darkness.desc"),
                                        Component.empty(),
                                        0.35D,
                                        0.0D,
                                        1.0D,
                                        0.05D,
                                        () -> cfg.sleepOverlayDarknessMultiplier,
                                        val -> cfg.sleepOverlayDarknessMultiplier = val,
                                        true
                                ))
                                .build()
                )
                .category(
                        ConfigCategory.createBuilder()
                                .name(Component.translatable("config.seamlesssleep.category.chat"))
                                .option(buildDoubleSlider(
                                        Component.translatable("config.seamlesssleep.chat.dim_multiplier"),
                                        Component.translatable("config.seamlesssleep.chat.dim_multiplier.desc"),
                                        Component.empty(),
                                        1.0D,
                                        0.1D,
                                        2.0D,
                                        0.05D,
                                        () -> cfg.sleepChatOpacityMultiplier,
                                        val -> cfg.sleepChatOpacityMultiplier = val,
                                        true
                                ))
                                .option(buildIntSlider(
                                        Component.translatable("config.seamlesssleep.chat.max_lines"),
                                        Component.translatable("config.seamlesssleep.chat.max_lines.desc"),
                                        4,
                                        0,
                                        12,
                                        () -> cfg.sleepChatMaxLines,
                                        val -> cfg.sleepChatMaxLines = val
                                ))
                                .build()
                )
                .category(
                        ConfigCategory.createBuilder()
                                .name(Component.translatable("config.seamlesssleep.category.camera"))
                                .option(buildDoubleSlider(
                                        Component.translatable("config.seamlesssleep.camera.tilt_degrees"),
                                        Component.translatable("config.seamlesssleep.camera.tilt_degrees.desc"),
                                        Component.empty(),
                                        10.0D,
                                        -45.0D,
                                        45.0D,
                                        1.0D,
                                        () -> cfg.sleepCameraTiltDegrees,
                                        val -> cfg.sleepCameraTiltDegrees = val,
                                        true
                                ))
                                .build()
                )
                .category(
                        ConfigCategory.createBuilder()
                                .name(Component.translatable("config.seamlesssleep.category.misc"))
                                .option(buildToggle(
                                        Component.translatable("config.seamlesssleep.misc.debug_logs"),
                                        Component.translatable("config.seamlesssleep.misc.debug_logs.desc"),
                                        Component.empty(),
                                        false,
                                        () -> cfg.debugLogsEnabled,
                                        val -> cfg.debugLogsEnabled = val,
                                        true
                                ))
                                .option(buildToggle(
                                        Component.translatable("config.seamlesssleep.misc.replay_compatibility"),
                                        Component.translatable("config.seamlesssleep.misc.replay_compatibility.desc"),
                                        Component.empty(),
                                        true,
                                        () -> cfg.replayCompatibilityEnabled,
                                        val -> cfg.replayCompatibilityEnabled = val,
                                        true
                                ))
                                .build()
                )
                .category(
                        ConfigCategory.createBuilder()
                                .name(Component.translatable("config.seamlesssleep.category.sleep"))
                                .option(buildToggle(
                                        Component.translatable("config.seamlesssleep.sleep.clears_weather"),
                                        Component.translatable("config.seamlesssleep.sleep.clears_weather.desc"),
                                        Component.translatable("config.seamlesssleep.server_controlled"),
                                        true,
                                        () -> canEditServerConfig
                                                ? serverCfg.sleepClearsWeather
                                                : SeamlessSleepServerConfigSnapshot.getSleepClearsWeather(),
                                        val -> {
                                            if (canEditServerConfig) {
                                                serverCfg.sleepClearsWeather = val;
                                            }
                                        },
                                        canEditServerConfig
                                ))
                                .option(buildDoubleSlider(
                                        Component.translatable("config.seamlesssleep.sleep.duration_multiplier"),
                                        Component.translatable("config.seamlesssleep.sleep.duration_multiplier.desc"),
                                        Component.translatable("config.seamlesssleep.server_controlled"),
                                        1.0D,
                                        0.25D,
                                        4.0D,
                                        0.05D,
                                        () -> canEditServerConfig
                                                ? serverCfg.sleepAnimationDurationMultiplier
                                                : SeamlessSleepServerConfigSnapshot.getSleepAnimationDurationMultiplier(),
                                        val -> {
                                            if (canEditServerConfig) {
                                                serverCfg.sleepAnimationDurationMultiplier = val;
                                            }
                                        },
                                        canEditServerConfig
                                ))
                                .build()
                )
                .save(() -> {
                    // Client options are always local; server config is saved only when this client can author it.
                    cfg.clamp();
                    SeamlessSleepClientConfigManager.save();
                    if (canEditServerConfig) {
                        serverCfg.clamp();
                        SeamlessSleepServerConfigManager.save();
                    }
                })
                .build()
                .generateScreen(parent);
    }

    private static Option<Double> buildDoubleSlider(Component name,
                                                    Component description,
                                                    Component disabledReason,
                                                    double def,
                                                    double min,
                                                    double max,
                                                    double step,
                                                    java.util.function.Supplier<Double> getter,
                                                    java.util.function.Consumer<Double> setter,
                                                    boolean available) {
        // Keep descriptions readable even when an option is disabled by server control.
        OptionDescription optionDescription = available
                ? OptionDescription.of(description)
                : OptionDescription.of(description, disabledReason);
        Option.Builder<Double> builder = Option.<Double>createBuilder()
                .name(name)
                .description(optionDescription)
                .binding(def, getter::get, value -> {
                    // YACL still routes value changes through binding, so we gate writes here.
                    if (available) {
                        setter.accept(value);
                    }
                })
                .controller(opt -> DoubleSliderControllerBuilder.create(opt)
                        .range(min, max)
                        .step(step));
        builder.available(available);
        return builder.build();
    }

    private static Option<Integer> buildIntSlider(Component name,
                                                  Component description,
                                                  int def,
                                                  int min,
                                                  int max,
                                                  java.util.function.Supplier<Integer> getter,
                                                  java.util.function.Consumer<Integer> setter) {
        return Option.<Integer>createBuilder()
                .name(name)
                .description(OptionDescription.of(description))
                .binding(def, getter::get, setter::accept)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(min, max)
                        .step(1))
                .build();
    }

    private static Option<Boolean> buildToggle(Component name,
                                               Component description,
                                               Component disabledReason,
                                               boolean def,
                                               java.util.function.Supplier<Boolean> getter,
                                               java.util.function.Consumer<Boolean> setter,
                                               boolean available) {
        // Mirror the slider behavior so disabled toggles show context but cannot mutate values.
        OptionDescription optionDescription = available
                ? OptionDescription.of(description)
                : OptionDescription.of(description, disabledReason);
        Option.Builder<Boolean> builder = Option.<Boolean>createBuilder()
                .name(name)
                .description(optionDescription)
                .binding(def, getter::get, value -> {
                    // Same guard as slider options for server-controlled entries.
                    if (available) {
                        setter.accept(value);
                    }
                })
                .controller(opt -> BooleanControllerBuilder.create(opt));
        builder.available(available);
        return builder.build();
    }
}

