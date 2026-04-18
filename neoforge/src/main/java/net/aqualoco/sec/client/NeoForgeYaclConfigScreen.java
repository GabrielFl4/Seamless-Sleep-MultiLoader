package net.aqualoco.sec.client;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.aqualoco.sec.config.WorldSleepAccelerationConfig;
import net.aqualoco.sec.config.WorldSleepAccelerationGovernorAggressiveness;
import net.aqualoco.sec.config.WorldSleepAccelerationMode;
import net.aqualoco.sec.config.WorldSleepAccelerationPreset;
import net.aqualoco.sec.config.WorldSleepNatureFilterProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

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
        boolean canEditServerConfig = !connectedToRemote;
        WorldSleepAccelerationConfig accelerationCfg = serverCfg.worldSleepAcceleration;

        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("config.seamlesssleep.title"))
                .category(buildOverlayCategory(cfg))
                .category(buildChatCategory(cfg))
                .category(buildCameraCategory(cfg))
                .category(buildMiscCategory(cfg))
                .category(buildSleepCategory(serverCfg, canEditServerConfig))
                .category(buildWorldAccelerationCategory(accelerationCfg, canEditServerConfig))
                .save(() -> {
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

    private static ConfigCategory buildOverlayCategory(SeamlessSleepClientConfig cfg) {
        return ConfigCategory.createBuilder()
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
                .build();
    }

    private static ConfigCategory buildChatCategory(SeamlessSleepClientConfig cfg) {
        return ConfigCategory.createBuilder()
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
                .build();
    }

    private static ConfigCategory buildCameraCategory(SeamlessSleepClientConfig cfg) {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("config.seamlesssleep.category.camera"))
                .option(buildDoubleSlider(
                        Component.translatable("config.seamlesssleep.camera.tilt_degrees"),
                        Component.translatable("config.seamlesssleep.camera.tilt_degrees.desc"),
                        Component.empty(),
                        10.0D,
                        0.0D,
                        90.0D,
                        0.1D,
                        () -> cfg.sleepCameraTiltDegrees,
                        val -> cfg.sleepCameraTiltDegrees = val,
                        true
                ))
                .build();
    }

    private static ConfigCategory buildMiscCategory(SeamlessSleepClientConfig cfg) {
        return ConfigCategory.createBuilder()
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
                .build();
    }

    private static ConfigCategory buildSleepCategory(SeamlessSleepServerConfig serverCfg, boolean canEditServerConfig) {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("config.seamlesssleep.category.sleep"))
                .option(buildServerIntSlider(
                        Component.translatable("config.seamlesssleep.sleep.clears_weather"),
                        Component.translatable("config.seamlesssleep.sleep.clears_weather.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        100,
                        0,
                        100,
                        5,
                        () -> canEditServerConfig
                                ? serverCfg.sleepWeatherClearChancePercent
                                : SeamlessSleepServerConfigSnapshot.getSleepWeatherClearChancePercent(),
                        val -> {
                            if (canEditServerConfig) {
                                serverCfg.sleepWeatherClearChancePercent = val;
                            }
                        },
                        canEditServerConfig,
                        NeoForgeYaclConfigScreen::formatWeatherChanceValue
                ))
                .option(buildDoubleSlider(
                        Component.translatable("config.seamlesssleep.sleep.duration_multiplier"),
                        Component.translatable("config.seamlesssleep.sleep.duration_multiplier.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        1.0D,
                        0.25D,
                        8.0D,
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
                .build();
    }

    private static ConfigCategory buildWorldAccelerationCategory(WorldSleepAccelerationConfig accelerationCfg,
                                                                 boolean canEditServerConfig) {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("config.seamlesssleep.category.world_acceleration"))
                .group(buildAccelerationGeneralGroup(accelerationCfg, canEditServerConfig))
                .group(buildNatureGroup(accelerationCfg, canEditServerConfig))
                .group(buildProcessGroup(accelerationCfg, canEditServerConfig))
                .build();
    }

    private static OptionGroup buildAccelerationGeneralGroup(WorldSleepAccelerationConfig accelerationCfg,
                                                             boolean canEditServerConfig) {
        return OptionGroup.createBuilder()
                .name(Component.translatable("config.seamlesssleep.world_acceleration.group.general"))
                .description(description(
                        Component.translatable("config.seamlesssleep.world_acceleration.group.general.desc"),
                        canEditServerConfig
                ))
                .option(buildEnumOption(
                        Component.translatable("config.seamlesssleep.world_acceleration.mode"),
                        Component.translatable("config.seamlesssleep.world_acceleration.mode.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        WorldSleepAccelerationMode.AUTO,
                        WorldSleepAccelerationMode.class,
                        () -> canEditServerConfig
                                ? accelerationCfg.mode
                                : SeamlessSleepServerConfigSnapshot.getWorldSleepAccelerationMode(),
                        value -> {
                            if (canEditServerConfig) {
                                accelerationCfg.mode = value;
                            }
                        },
                        canEditServerConfig,
                        value -> enumText("config.seamlesssleep.world_acceleration.mode", value)
                ))
                .option(buildEnumOption(
                        Component.translatable("config.seamlesssleep.world_acceleration.preset"),
                        Component.translatable("config.seamlesssleep.world_acceleration.preset.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        WorldSleepAccelerationPreset.BALANCED,
                        WorldSleepAccelerationPreset.class,
                        () -> canEditServerConfig
                                ? accelerationCfg.preset
                                : SeamlessSleepServerConfigSnapshot.getWorldSleepAccelerationPreset(),
                        value -> {
                            if (canEditServerConfig) {
                                if (value == WorldSleepAccelerationPreset.CUSTOM) {
                                    accelerationCfg.markPresetCustom();
                                } else {
                                    accelerationCfg.applyPreset(value);
                                }
                            }
                        },
                        canEditServerConfig,
                        value -> enumText("config.seamlesssleep.world_acceleration.preset", value)
                ))
                .option(buildToggle(
                        Component.translatable("config.seamlesssleep.world_acceleration.random_tick_enabled"),
                        Component.translatable("config.seamlesssleep.world_acceleration.random_tick_enabled.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        true,
                        () -> canEditServerConfig
                                ? accelerationCfg.randomTickAccelerationEnabled
                                : SeamlessSleepServerConfigSnapshot.isRandomTickAccelerationEnabled(),
                        val -> {
                            if (canEditServerConfig) {
                                accelerationCfg.randomTickAccelerationEnabled = val;
                                accelerationCfg.markPresetCustom();
                            }
                        },
                        canEditServerConfig
                ))
                .option(buildToggle(
                        Component.translatable("config.seamlesssleep.world_acceleration.process_enabled"),
                        Component.translatable("config.seamlesssleep.world_acceleration.process_enabled.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        true,
                        () -> canEditServerConfig
                                ? accelerationCfg.processAccelerationEnabled
                                : SeamlessSleepServerConfigSnapshot.isProcessAccelerationEnabled(),
                        val -> {
                            if (canEditServerConfig) {
                                accelerationCfg.processAccelerationEnabled = val;
                                accelerationCfg.markPresetCustom();
                            }
                        },
                        canEditServerConfig
                ))
                .option(buildEnumOption(
                        Component.translatable("config.seamlesssleep.world_acceleration.governor"),
                        Component.translatable("config.seamlesssleep.world_acceleration.governor.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        WorldSleepAccelerationGovernorAggressiveness.BALANCED,
                        WorldSleepAccelerationGovernorAggressiveness.class,
                        () -> canEditServerConfig
                                ? accelerationCfg.governorAggressiveness
                                : SeamlessSleepServerConfigSnapshot.getGovernorAggressiveness(),
                        value -> {
                            if (canEditServerConfig) {
                                accelerationCfg.governorAggressiveness = value;
                                accelerationCfg.markPresetCustom();
                            }
                        },
                        canEditServerConfig,
                        value -> enumText("config.seamlesssleep.world_acceleration.governor", value)
                ))
                .option(buildEnumOption(
                        Component.translatable("config.seamlesssleep.world_acceleration.nature_filter"),
                        Component.translatable("config.seamlesssleep.world_acceleration.nature_filter.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        WorldSleepNatureFilterProfile.ALL,
                        WorldSleepNatureFilterProfile.class,
                        () -> canEditServerConfig
                                ? accelerationCfg.natureFilterProfile
                                : SeamlessSleepServerConfigSnapshot.getNatureFilterProfile(),
                        value -> {
                            if (canEditServerConfig) {
                                accelerationCfg.natureFilterProfile = value;
                                accelerationCfg.markPresetCustom();
                            }
                        },
                        canEditServerConfig,
                        value -> enumText("config.seamlesssleep.world_acceleration.nature_filter", value)
                ))
                .build();
    }

    private static OptionGroup buildNatureGroup(WorldSleepAccelerationConfig accelerationCfg,
                                                boolean canEditServerConfig) {
        return OptionGroup.createBuilder()
                .name(Component.translatable("config.seamlesssleep.world_acceleration.group.nature"))
                .description(description(
                        Component.translatable("config.seamlesssleep.world_acceleration.group.nature.desc"),
                        canEditServerConfig
                ))
                .option(buildServerIntSlider(
                        Component.translatable("config.seamlesssleep.world_acceleration.base_radius"),
                        Component.translatable("config.seamlesssleep.world_acceleration.base_radius.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        6,
                        0,
                        32,
                        1,
                        () -> canEditServerConfig
                                ? accelerationCfg.nature.baseRadiusChunks
                                : SeamlessSleepServerConfigSnapshot.getNatureBaseRadiusChunks(),
                        val -> {
                            if (canEditServerConfig) {
                                accelerationCfg.nature.baseRadiusChunks = val;
                                accelerationCfg.markPresetCustom();
                            }
                        },
                        canEditServerConfig,
                        NeoForgeYaclConfigScreen::formatRadiusValue
                ))
                .option(buildServerIntSlider(
                        Component.translatable("config.seamlesssleep.world_acceleration.auto_min_radius"),
                        Component.translatable("config.seamlesssleep.world_acceleration.auto_min_radius.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        3,
                        0,
                        32,
                        1,
                        () -> canEditServerConfig
                                ? accelerationCfg.nature.autoMinRadiusChunks
                                : SeamlessSleepServerConfigSnapshot.getNatureAutoMinRadiusChunks(),
                        val -> {
                            if (canEditServerConfig) {
                                accelerationCfg.nature.autoMinRadiusChunks = val;
                                accelerationCfg.markPresetCustom();
                            }
                        },
                        canEditServerConfig,
                        NeoForgeYaclConfigScreen::formatRadiusValue
                ))
                .option(buildDoubleSlider(
                        Component.translatable("config.seamlesssleep.world_acceleration.base_rate_fraction"),
                        Component.translatable("config.seamlesssleep.world_acceleration.base_rate_fraction.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        0.45D,
                        0.0D,
                        1.0D,
                        0.05D,
                        () -> canEditServerConfig
                                ? accelerationCfg.nature.baseRateFraction
                                : SeamlessSleepServerConfigSnapshot.getNatureBaseRateFraction(),
                        val -> {
                            if (canEditServerConfig) {
                                accelerationCfg.nature.baseRateFraction = val;
                                accelerationCfg.markPresetCustom();
                            }
                        },
                        canEditServerConfig,
                        NeoForgeYaclConfigScreen::formatRateFraction
                ))
                .option(buildDoubleSlider(
                        Component.translatable("config.seamlesssleep.world_acceleration.auto_min_rate_fraction"),
                        Component.translatable("config.seamlesssleep.world_acceleration.auto_min_rate_fraction.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        0.20D,
                        0.0D,
                        1.0D,
                        0.05D,
                        () -> canEditServerConfig
                                ? accelerationCfg.nature.autoMinRateFraction
                                : SeamlessSleepServerConfigSnapshot.getNatureAutoMinRateFraction(),
                        val -> {
                            if (canEditServerConfig) {
                                accelerationCfg.nature.autoMinRateFraction = val;
                                accelerationCfg.markPresetCustom();
                            }
                        },
                        canEditServerConfig,
                        NeoForgeYaclConfigScreen::formatRateFraction
                ))
                .build();
    }

    private static OptionGroup buildProcessGroup(WorldSleepAccelerationConfig accelerationCfg,
                                                 boolean canEditServerConfig) {
        return OptionGroup.createBuilder()
                .name(Component.translatable("config.seamlesssleep.world_acceleration.group.process"))
                .description(description(
                        Component.translatable("config.seamlesssleep.world_acceleration.group.process.desc"),
                        canEditServerConfig
                ))
                .option(buildServerIntSlider(
                        Component.translatable("config.seamlesssleep.world_acceleration.base_radius"),
                        Component.translatable("config.seamlesssleep.world_acceleration.base_radius.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        6,
                        0,
                        32,
                        1,
                        () -> canEditServerConfig
                                ? accelerationCfg.process.baseRadiusChunks
                                : SeamlessSleepServerConfigSnapshot.getProcessBaseRadiusChunks(),
                        val -> {
                            if (canEditServerConfig) {
                                accelerationCfg.process.baseRadiusChunks = val;
                                accelerationCfg.markPresetCustom();
                            }
                        },
                        canEditServerConfig,
                        NeoForgeYaclConfigScreen::formatRadiusValue
                ))
                .option(buildServerIntSlider(
                        Component.translatable("config.seamlesssleep.world_acceleration.auto_min_radius"),
                        Component.translatable("config.seamlesssleep.world_acceleration.auto_min_radius.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        3,
                        0,
                        32,
                        1,
                        () -> canEditServerConfig
                                ? accelerationCfg.process.autoMinRadiusChunks
                                : SeamlessSleepServerConfigSnapshot.getProcessAutoMinRadiusChunks(),
                        val -> {
                            if (canEditServerConfig) {
                                accelerationCfg.process.autoMinRadiusChunks = val;
                                accelerationCfg.markPresetCustom();
                            }
                        },
                        canEditServerConfig,
                        NeoForgeYaclConfigScreen::formatRadiusValue
                ))
                .option(buildDoubleSlider(
                        Component.translatable("config.seamlesssleep.world_acceleration.base_rate_fraction"),
                        Component.translatable("config.seamlesssleep.world_acceleration.process_base_rate_fraction.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        0.75D,
                        0.0D,
                        1.0D,
                        0.05D,
                        () -> canEditServerConfig
                                ? accelerationCfg.process.baseRateFraction
                                : SeamlessSleepServerConfigSnapshot.getProcessBaseRateFraction(),
                        val -> {
                            if (canEditServerConfig) {
                                accelerationCfg.process.baseRateFraction = val;
                                accelerationCfg.markPresetCustom();
                            }
                        },
                        canEditServerConfig,
                        NeoForgeYaclConfigScreen::formatRateFraction
                ))
                .option(buildDoubleSlider(
                        Component.translatable("config.seamlesssleep.world_acceleration.auto_min_rate_fraction"),
                        Component.translatable("config.seamlesssleep.world_acceleration.process_auto_min_rate_fraction.desc"),
                        Component.translatable("config.seamlesssleep.server_controlled"),
                        0.40D,
                        0.0D,
                        1.0D,
                        0.05D,
                        () -> canEditServerConfig
                                ? accelerationCfg.process.autoMinRateFraction
                                : SeamlessSleepServerConfigSnapshot.getProcessAutoMinRateFraction(),
                        val -> {
                            if (canEditServerConfig) {
                                accelerationCfg.process.autoMinRateFraction = val;
                                accelerationCfg.markPresetCustom();
                            }
                        },
                        canEditServerConfig,
                        NeoForgeYaclConfigScreen::formatRateFraction
                ))
                .build();
    }

    private static OptionDescription description(Component description, boolean available) {
        return available
                ? OptionDescription.of(description)
                : OptionDescription.of(description, Component.translatable("config.seamlesssleep.server_controlled"));
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
        return buildDoubleSlider(name, description, disabledReason, def, min, max, step, getter, setter, available, null);
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
                                                    boolean available,
                                                    java.util.function.Function<Double, Component> formatter) {
        OptionDescription optionDescription = available
                ? OptionDescription.of(description)
                : OptionDescription.of(description, disabledReason);
        Option.Builder<Double> builder = Option.<Double>createBuilder()
                .name(name)
                .description(optionDescription)
                .binding(def, getter::get, value -> {
                    if (available) {
                        setter.accept(value);
                    }
                })
                .controller(opt -> {
                    DoubleSliderControllerBuilder slider = DoubleSliderControllerBuilder.create(opt)
                            .range(min, max)
                            .step(step);
                    if (formatter != null) {
                        slider = slider.formatValue(formatter::apply);
                    }
                    return slider;
                });
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

    private static Option<Integer> buildServerIntSlider(Component name,
                                                        Component description,
                                                        Component disabledReason,
                                                        int def,
                                                        int min,
                                                        int max,
                                                        int step,
                                                        java.util.function.Supplier<Integer> getter,
                                                        java.util.function.Consumer<Integer> setter,
                                                        boolean available) {
        return buildServerIntSlider(name, description, disabledReason, def, min, max, step, getter, setter, available, null);
    }

    private static Option<Integer> buildServerIntSlider(Component name,
                                                        Component description,
                                                        Component disabledReason,
                                                        int def,
                                                        int min,
                                                        int max,
                                                        int step,
                                                        java.util.function.Supplier<Integer> getter,
                                                        java.util.function.Consumer<Integer> setter,
                                                        boolean available,
                                                        java.util.function.Function<Integer, Component> formatter) {
        OptionDescription optionDescription = available
                ? OptionDescription.of(description)
                : OptionDescription.of(description, disabledReason);
        Option.Builder<Integer> builder = Option.<Integer>createBuilder()
                .name(name)
                .description(optionDescription)
                .binding(def, getter::get, value -> {
                    if (available) {
                        setter.accept(value);
                    }
                })
                .controller(opt -> {
                    IntegerSliderControllerBuilder slider = IntegerSliderControllerBuilder.create(opt)
                            .range(min, max)
                            .step(step);
                    if (formatter != null) {
                        slider = slider.formatValue(formatter::apply);
                    }
                    return slider;
                });
        builder.available(available);
        return builder.build();
    }

    private static Option<Boolean> buildToggle(Component name,
                                               Component description,
                                               Component disabledReason,
                                               boolean def,
                                               java.util.function.Supplier<Boolean> getter,
                                               java.util.function.Consumer<Boolean> setter,
                                               boolean available) {
        OptionDescription optionDescription = available
                ? OptionDescription.of(description)
                : OptionDescription.of(description, disabledReason);
        Option.Builder<Boolean> builder = Option.<Boolean>createBuilder()
                .name(name)
                .description(optionDescription)
                .binding(def, getter::get, value -> {
                    if (available) {
                        setter.accept(value);
                    }
                })
                .controller(opt -> BooleanControllerBuilder.create(opt));
        builder.available(available);
        return builder.build();
    }

    private static <E extends Enum<E>> Option<E> buildEnumOption(Component name,
                                                                 Component description,
                                                                 Component disabledReason,
                                                                 E def,
                                                                 Class<E> enumClass,
                                                                 java.util.function.Supplier<E> getter,
                                                                 java.util.function.Consumer<E> setter,
                                                                 boolean available,
                                                                 java.util.function.Function<E, Component> formatter) {
        OptionDescription optionDescription = available
                ? OptionDescription.of(description)
                : OptionDescription.of(description, disabledReason);
        Option.Builder<E> builder = Option.<E>createBuilder()
                .name(name)
                .description(optionDescription)
                .binding(def, getter::get, value -> {
                    if (available) {
                        setter.accept(value);
                    }
                })
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(enumClass)
                        .formatValue(formatter::apply));
        builder.available(available);
        return builder.build();
    }

    private static Component enumText(String keyPrefix, Enum<?> value) {
        return Component.translatable(keyPrefix + "." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component formatWeatherChanceValue(Integer value) {
        if (value == null || value <= 0) {
            return Component.literal("false");
        }
        if (value >= 100) {
            return Component.literal("true");
        }
        return Component.literal(value + "%");
    }

    private static Component formatRadiusValue(Integer value) {
        if (value == null || value <= 0) {
            return Component.translatable("config.seamlesssleep.world_acceleration.radius.simulation_distance");
        }
        return Component.translatable("config.seamlesssleep.world_acceleration.radius.chunks", value);
    }

    private static Component formatRateFraction(Double value) {
        if (value == null) {
            return Component.literal("0%");
        }
        int percent = (int) Math.round(value * 100.0D);
        return Component.literal(percent + "%");
    }
}
