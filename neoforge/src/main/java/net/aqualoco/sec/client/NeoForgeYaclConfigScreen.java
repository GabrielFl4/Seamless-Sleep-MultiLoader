package net.aqualoco.sec.client;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
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
import net.aqualoco.sec.config.WorldSleepAccelerationMode;
import net.aqualoco.sec.config.WorldSleepAccelerationPlayersAffected;
import net.aqualoco.sec.config.WorldSleepAutomaticMode;
import net.aqualoco.sec.network.ServerConfigSync;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class NeoForgeYaclConfigScreen {

    private NeoForgeYaclConfigScreen() {
    }

    public static Screen create(Screen parent) {
        SeamlessSleepClientConfig clientCfg = SeamlessSleepClientConfigManager.get();
        clientCfg.clamp();

        SeamlessSleepServerConfig serverCfg = SeamlessSleepServerConfigManager.get();
        serverCfg.clamp();

        Minecraft client = Minecraft.getInstance();
        boolean connectedToRemote = client.getConnection() != null && !client.hasSingleplayerServer();
        boolean canEditServerConfig = !connectedToRemote;
        int simulationDistance = resolveSimulationDistance(client, serverCfg);
        ServerConfigUiState serverUiState = canEditServerConfig
                ? ServerConfigUiState.fromLocal(serverCfg, simulationDistance)
                : ServerConfigUiState.fromSnapshot();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("config.seamlesssleep.title"))
                .category(buildClientConfigCategory(clientCfg))
                .category(buildServerConfigCategory(serverUiState, canEditServerConfig))
                .category(buildAdvancedCategory(clientCfg))
                .save(() -> {
                    clientCfg.clamp();
                    SeamlessSleepClientConfigManager.save();
                    if (canEditServerConfig) {
                        serverUiState.applyTo(serverCfg);
                        serverCfg.clamp();
                        SeamlessSleepServerConfigManager.save();
                        syncLocalServerConfigIfPresent(client, serverCfg);
                    }
                })
                .build()
                .generateScreen(parent);
    }

    private static void syncLocalServerConfigIfPresent(Minecraft client, SeamlessSleepServerConfig serverCfg) {
        if (client.getSingleplayerServer() != null) {
            ServerConfigSync.sendToAll(client.getSingleplayerServer(), serverCfg);
        }
    }

    private static ConfigCategory buildClientConfigCategory(SeamlessSleepClientConfig cfg) {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("config.seamlesssleep.category.client"))
                .group(buildOverlayGroup(cfg))
                .group(buildChatGroup(cfg))
                .group(buildCameraGroup(cfg))
                .build();
    }

    private static ConfigCategory buildServerConfigCategory(ServerConfigUiState uiState, boolean canEditServerConfig) {
        Option<Integer> weatherChanceOption = buildIntSlider(
                Component.translatable("config.seamlesssleep.sleep.clears_weather"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.sleep.clears_weather.desc"),
                        "config.seamlesssleep.sleep.clears_weather.command"
                ),
                Component.empty(),
                100,
                0,
                100,
                5,
                () -> uiState.boundSleepWeatherClearChancePercent,
                value -> uiState.boundSleepWeatherClearChancePercent = value,
                canEditServerConfig,
                NeoForgeYaclConfigScreen::formatWeatherChanceValue
        );
        listen(weatherChanceOption, value -> uiState.sleepWeatherClearChancePercent = value);

        Option<Double> animationDurationOption = buildDoubleSlider(
                Component.translatable("config.seamlesssleep.sleep.duration_multiplier"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.sleep.duration_multiplier.desc"),
                        "config.seamlesssleep.sleep.duration_multiplier.command"
                ),
                Component.empty(),
                1.0D,
                0.25D,
                8.0D,
                0.05D,
                () -> uiState.boundSleepAnimationDurationMultiplier,
                value -> uiState.boundSleepAnimationDurationMultiplier = value,
                canEditServerConfig,
                NeoForgeYaclConfigScreen::formatMultiplierValue
        );
        listen(animationDurationOption, value -> uiState.sleepAnimationDurationMultiplier = value);

        Option<WorldSleepAccelerationMode> modeOption = buildEnumOption(
                Component.translatable("config.seamlesssleep.world_acceleration.mode"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.world_acceleration.mode.desc"),
                        "config.seamlesssleep.world_acceleration.mode.command"
                ),
                Component.empty(),
                WorldSleepAccelerationMode.AUTOMATIC,
                WorldSleepAccelerationMode.class,
                () -> uiState.boundMode,
                value -> uiState.boundMode = value == null ? WorldSleepAccelerationMode.AUTOMATIC : value,
                canEditServerConfig,
                value -> enumText("config.seamlesssleep.world_acceleration.mode", value)
        );
        Option<Integer> radiusOption = buildIntSlider(
                Component.translatable("config.seamlesssleep.world_acceleration.radius"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.world_acceleration.radius.desc"),
                        "config.seamlesssleep.world_acceleration.radius.command"
                ),
                Component.empty(),
                uiState.resolveDisplayedAccelerationRadius(),
                1,
                uiState.simulationDistance,
                1,
                () -> uiState.boundDisplayedAccelerationRadius,
                value -> uiState.boundDisplayedAccelerationRadius = Mth.clamp(value, 1, uiState.simulationDistance),
                canEditServerConfig && uiState.mode == WorldSleepAccelerationMode.MANUAL,
                value -> formatRadiusValue(value, uiState.simulationDistance)
        );
        Option<Integer> speedOption = buildIntSlider(
                Component.translatable("config.seamlesssleep.world_acceleration.speed"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.world_acceleration.speed.desc"),
                        "config.seamlesssleep.world_acceleration.speed.command"
                ),
                Component.empty(),
                WorldSleepAccelerationConfig.DEFAULT_MANUAL_SPEED_PERCENT,
                0,
                100,
                1,
                () -> uiState.boundDisplayedAccelerationSpeedPercent,
                value -> uiState.boundDisplayedAccelerationSpeedPercent = Mth.clamp(value, 0, 100),
                canEditServerConfig && uiState.mode == WorldSleepAccelerationMode.MANUAL,
                NeoForgeYaclConfigScreen::formatPercentValue
        );
        Option<WorldSleepAccelerationPlayersAffected> playersAffectedOption = buildEnumOption(
                Component.translatable("config.seamlesssleep.world_acceleration.players_affected"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.world_acceleration.players_affected.desc"),
                        "config.seamlesssleep.world_acceleration.players_affected.command"
                ),
                Component.empty(),
                WorldSleepAccelerationPlayersAffected.ALL_PLAYERS,
                WorldSleepAccelerationPlayersAffected.class,
                () -> uiState.boundDisplayedPlayersAffected,
                value -> uiState.boundDisplayedPlayersAffected = value == null
                        ? WorldSleepAccelerationPlayersAffected.ALL_PLAYERS
                        : value,
                canEditServerConfig && uiState.mode == WorldSleepAccelerationMode.MANUAL,
                value -> enumText("config.seamlesssleep.world_acceleration.players_affected", value)
        );
        Option<WorldSleepAutomaticMode> automaticModeOption = buildEnumOption(
                Component.translatable("config.seamlesssleep.world_acceleration.automatic_mode"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.world_acceleration.automatic_mode.desc"),
                        "config.seamlesssleep.world_acceleration.automatic_mode.command"
                ),
                Component.empty(),
                WorldSleepAutomaticMode.AGGRESSIVE,
                WorldSleepAutomaticMode.class,
                () -> uiState.boundAutomaticMode,
                value -> uiState.boundAutomaticMode = value == null ? WorldSleepAutomaticMode.AGGRESSIVE : value,
                canEditServerConfig && uiState.mode == WorldSleepAccelerationMode.AUTOMATIC,
                value -> enumText("config.seamlesssleep.world_acceleration.automatic_mode", value)
        );
        Option<Boolean> grassOption = buildToggle(
                Component.translatable("config.seamlesssleep.world_acceleration.grass_and_foliage"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.world_acceleration.grass_and_foliage.desc"),
                        "config.seamlesssleep.world_acceleration.grass_and_foliage.command"
                ),
                Component.empty(),
                true,
                () -> uiState.boundGrassAndFoliageAccelerationEnabled,
                value -> uiState.boundGrassAndFoliageAccelerationEnabled = value,
                canEditServerConfig && uiState.mode != WorldSleepAccelerationMode.OFF
        );
        Option<Boolean> cropsOption = buildToggle(
                Component.translatable("config.seamlesssleep.world_acceleration.crops_and_saplings"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.world_acceleration.crops_and_saplings.desc"),
                        "config.seamlesssleep.world_acceleration.crops_and_saplings.command"
                ),
                Component.empty(),
                true,
                () -> uiState.boundCropsAndSaplingsAccelerationEnabled,
                value -> uiState.boundCropsAndSaplingsAccelerationEnabled = value,
                canEditServerConfig && uiState.mode != WorldSleepAccelerationMode.OFF
        );
        Option<Boolean> kelpOption = buildToggle(
                Component.translatable("config.seamlesssleep.world_acceleration.kelp"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.world_acceleration.kelp.desc"),
                        "config.seamlesssleep.world_acceleration.kelp.command"
                ),
                Component.empty(),
                false,
                () -> uiState.boundKelpAccelerationEnabled,
                value -> uiState.boundKelpAccelerationEnabled = value,
                canEditServerConfig && uiState.mode != WorldSleepAccelerationMode.OFF
        );
        Option<Boolean> vanillaOnlyOption = buildToggle(
                Component.translatable("config.seamlesssleep.world_acceleration.vanilla_only"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.world_acceleration.vanilla_only.desc"),
                        "config.seamlesssleep.world_acceleration.vanilla_only.command"
                ),
                Component.empty(),
                WorldSleepAccelerationConfig.DEFAULT_VANILLA_ONLY_ACCELERATION,
                () -> uiState.boundVanillaOnlyAcceleration,
                value -> uiState.boundVanillaOnlyAcceleration = value,
                canEditServerConfig && uiState.mode != WorldSleepAccelerationMode.OFF
        );
        Option<Boolean> processesOption = buildToggle(
                Component.translatable("config.seamlesssleep.world_acceleration.processes_enabled"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.world_acceleration.processes_enabled.desc"),
                        "config.seamlesssleep.world_acceleration.processes_enabled.command"
                ),
                Component.empty(),
                true,
                () -> uiState.boundProcessesAccelerationEnabled,
                value -> uiState.boundProcessesAccelerationEnabled = value,
                canEditServerConfig && uiState.mode != WorldSleepAccelerationMode.OFF
        );
        Option<Integer> processesSpeedOption = buildIntSlider(
                Component.translatable("config.seamlesssleep.world_acceleration.processes_speed"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.world_acceleration.processes_speed.desc"),
                        "config.seamlesssleep.world_acceleration.processes_speed.command"
                ),
                Component.empty(),
                uiState.processesSpeedPercent,
                0,
                100,
                1,
                () -> uiState.boundProcessesSpeedPercent,
                value -> uiState.boundProcessesSpeedPercent = Mth.clamp(value, 0, 100),
                canEditServerConfig && uiState.mode != WorldSleepAccelerationMode.OFF && uiState.processesAccelerationEnabled,
                NeoForgeYaclConfigScreen::formatPercentValue
        );

        Runnable refreshAccelerationOptions = () -> refreshAccelerationOptions(
                canEditServerConfig,
                uiState,
                radiusOption,
                speedOption,
                playersAffectedOption,
                automaticModeOption,
                grassOption,
                cropsOption,
                kelpOption,
                vanillaOnlyOption,
                processesOption,
                processesSpeedOption
        );

        listen(modeOption, value -> {
            uiState.mode = value;
            refreshAccelerationOptions.run();
        });
        listen(radiusOption, value -> {
            if (uiState.mode == WorldSleepAccelerationMode.MANUAL) {
                uiState.manualAccelerationRadiusChunks = value;
            }
        });
        listen(speedOption, value -> {
            if (uiState.mode == WorldSleepAccelerationMode.MANUAL) {
                uiState.manualAccelerationSpeedPercent = value;
            }
        });
        listen(playersAffectedOption, value -> {
            if (uiState.mode == WorldSleepAccelerationMode.MANUAL) {
                uiState.playersAffected = value;
            }
        });
        listen(automaticModeOption, value -> {
            uiState.automaticMode = value;
            refreshAccelerationOptions.run();
        });
        listen(grassOption, value -> uiState.grassAndFoliageAccelerationEnabled = value);
        listen(cropsOption, value -> uiState.cropsAndSaplingsAccelerationEnabled = value);
        listen(kelpOption, value -> uiState.kelpAccelerationEnabled = value);
        listen(vanillaOnlyOption, value -> uiState.vanillaOnlyAcceleration = value);
        listen(processesOption, value -> {
            uiState.processesAccelerationEnabled = value;
            refreshAccelerationOptions.run();
        });
        listen(processesSpeedOption, value -> uiState.processesSpeedPercent = value);
        refreshAccelerationOptions.run();

        OptionGroup generalGroup = OptionGroup.createBuilder()
                .name(Component.translatable("config.seamlesssleep.server.group.general"))
                .description(description(
                        Component.translatable("config.seamlesssleep.server.group.general.desc"),
                        canEditServerConfig
                ))
                .collapsed(false)
                .option(weatherChanceOption)
                .option(animationDurationOption)
                .build();

        OptionGroup accelerationGroup = OptionGroup.createBuilder()
                .name(Component.translatable("config.seamlesssleep.server.group.acceleration"))
                .description(description(
                        Component.translatable("config.seamlesssleep.server.group.acceleration.desc"),
                        canEditServerConfig
                ))
                .collapsed(false)
                .option(modeOption)
                .option(LabelOption.create(Component.translatable("config.seamlesssleep.world_acceleration.manual_section")))
                .option(radiusOption)
                .option(speedOption)
                .option(playersAffectedOption)
                .option(LabelOption.create(Component.translatable("config.seamlesssleep.world_acceleration.automatic_section")))
                .option(automaticModeOption)
                .option(grassOption)
                .option(cropsOption)
                .option(kelpOption)
                .option(vanillaOnlyOption)
                .option(processesOption)
                .option(processesSpeedOption)
                .build();

        return ConfigCategory.createBuilder()
                .name(Component.translatable("config.seamlesssleep.category.server"))
                .group(generalGroup)
                .group(accelerationGroup)
                .build();
    }

    private static ConfigCategory buildAdvancedCategory(SeamlessSleepClientConfig cfg) {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("config.seamlesssleep.category.advanced"))
                .option(LabelOption.create(Component.translatable("config.seamlesssleep.advanced.notice")))
                .option(buildToggle(
                        Component.translatable("config.seamlesssleep.misc.debug_logs"),
                        Component.translatable("config.seamlesssleep.misc.debug_logs.desc"),
                        Component.empty(),
                        false,
                        () -> cfg.debugLogsEnabled,
                        value -> cfg.debugLogsEnabled = value,
                        true
                ))
                .option(buildToggle(
                        Component.translatable("config.seamlesssleep.misc.replay_compatibility"),
                        Component.translatable("config.seamlesssleep.misc.replay_compatibility.desc"),
                        Component.empty(),
                        true,
                        () -> cfg.replayCompatibilityEnabled,
                        value -> cfg.replayCompatibilityEnabled = value,
                        true
                ))
                .build();
    }

    private static OptionGroup buildOverlayGroup(SeamlessSleepClientConfig cfg) {
        return OptionGroup.createBuilder()
                .name(Component.translatable("config.seamlesssleep.client.group.overlay"))
                .description(OptionDescription.of(Component.translatable("config.seamlesssleep.client.group.overlay.desc")))
                .collapsed(false)
                .option(buildToggle(
                        Component.translatable("config.seamlesssleep.overlay.enabled"),
                        Component.translatable("config.seamlesssleep.overlay.enabled.desc"),
                        Component.empty(),
                        true,
                        () -> cfg.sleepOverlayEnabled,
                        value -> cfg.sleepOverlayEnabled = value,
                        true
                ))
                .option(buildIntSlider(
                        Component.translatable("config.seamlesssleep.overlay.darkness"),
                        Component.translatable("config.seamlesssleep.overlay.darkness.desc"),
                        Component.empty(),
                        35,
                        0,
                        100,
                        1,
                        () -> toPercent(cfg.sleepOverlayDarknessMultiplier),
                        value -> cfg.sleepOverlayDarknessMultiplier = fromPercent(value),
                        true,
                        NeoForgeYaclConfigScreen::formatVanillaHiddenPercentValue
                ))
                .option(buildToggle(
                        Component.translatable("config.seamlesssleep.overlay.leave_bed_hint"),
                        Component.translatable("config.seamlesssleep.overlay.leave_bed_hint.desc"),
                        Component.empty(),
                        true,
                        () -> cfg.leaveBedHintEnabled,
                        value -> cfg.leaveBedHintEnabled = value,
                        true
                ))
                .option(buildToggle(
                        Component.translatable("config.seamlesssleep.overlay.sleep_context"),
                        Component.translatable("config.seamlesssleep.overlay.sleep_context.desc"),
                        Component.empty(),
                        true,
                        () -> cfg.sleepContextEnabled,
                        value -> cfg.sleepContextEnabled = value,
                        true
                ))
                .build();
    }

    private static OptionGroup buildChatGroup(SeamlessSleepClientConfig cfg) {
        return OptionGroup.createBuilder()
                .name(Component.translatable("config.seamlesssleep.client.group.chat"))
                .description(OptionDescription.of(Component.translatable("config.seamlesssleep.client.group.chat.desc")))
                .collapsed(false)
                .option(buildIntSlider(
                        Component.translatable("config.seamlesssleep.chat.dim_multiplier"),
                        Component.translatable("config.seamlesssleep.chat.dim_multiplier.desc"),
                        Component.empty(),
                        50,
                        0,
                        100,
                        1,
                        () -> toPercent(cfg.sleepChatOpacityMultiplier),
                        value -> cfg.sleepChatOpacityMultiplier = fromPercent(value),
                        true,
                        NeoForgeYaclConfigScreen::formatVanillaHiddenPercentValue
                ))
                .option(buildIntSlider(
                        Component.translatable("config.seamlesssleep.chat.max_lines"),
                        Component.translatable("config.seamlesssleep.chat.max_lines.desc"),
                        Component.empty(),
                        4,
                        0,
                        12,
                        1,
                        () -> cfg.sleepChatMaxLines,
                        value -> cfg.sleepChatMaxLines = value,
                        true,
                        NeoForgeYaclConfigScreen::formatVanillaHiddenLinesValue
                ))
                .build();
    }

    private static OptionGroup buildCameraGroup(SeamlessSleepClientConfig cfg) {
        return OptionGroup.createBuilder()
                .name(Component.translatable("config.seamlesssleep.client.group.camera"))
                .description(OptionDescription.of(Component.translatable("config.seamlesssleep.client.group.camera.desc")))
                .collapsed(false)
                .option(buildIntSlider(
                        Component.translatable("config.seamlesssleep.camera.tilt_degrees"),
                        Component.translatable("config.seamlesssleep.camera.tilt_degrees.desc"),
                        Component.empty(),
                        10,
                        0,
                        90,
                        1,
                        () -> Mth.clamp((int) Math.round(cfg.sleepCameraTiltDegrees), 0, 90),
                        value -> cfg.sleepCameraTiltDegrees = value,
                        true,
                        NeoForgeYaclConfigScreen::formatDegreesValue
                ))
                .option(buildIntSlider(
                        Component.translatable("config.seamlesssleep.camera.mouse_smoothness"),
                        Component.translatable("config.seamlesssleep.camera.mouse_smoothness.desc"),
                        Component.empty(),
                        100,
                        0,
                        100,
                        1,
                        () -> cfg.mouseSmoothnessPercent,
                        value -> cfg.mouseSmoothnessPercent = value,
                        true,
                        NeoForgeYaclConfigScreen::formatPercentValue
                ))
                .build();
    }

    private static void refreshAccelerationOptions(boolean canEditServerConfig,
                                                   ServerConfigUiState uiState,
                                                   Option<Integer> radiusOption,
                                                   Option<Integer> speedOption,
                                                   Option<WorldSleepAccelerationPlayersAffected> playersAffectedOption,
                                                   Option<WorldSleepAutomaticMode> automaticModeOption,
                                                   Option<Boolean> grassOption,
                                                   Option<Boolean> cropsOption,
                                                   Option<Boolean> kelpOption,
                                                   Option<Boolean> vanillaOnlyOption,
                                                   Option<Boolean> processesOption,
                                                   Option<Integer> processesSpeedOption) {
        boolean systemEnabled = canEditServerConfig && uiState.mode != WorldSleepAccelerationMode.OFF;
        boolean automaticModeAvailable = canEditServerConfig && uiState.mode == WorldSleepAccelerationMode.AUTOMATIC;
        boolean manualControlsAvailable = canEditServerConfig && uiState.mode == WorldSleepAccelerationMode.MANUAL;

        radiusOption.setAvailable(manualControlsAvailable);
        speedOption.setAvailable(manualControlsAvailable);
        playersAffectedOption.setAvailable(manualControlsAvailable);
        automaticModeOption.setAvailable(automaticModeAvailable);
        grassOption.setAvailable(systemEnabled);
        cropsOption.setAvailable(systemEnabled);
        kelpOption.setAvailable(systemEnabled);
        vanillaOnlyOption.setAvailable(systemEnabled);
        processesOption.setAvailable(systemEnabled);
        processesSpeedOption.setAvailable(systemEnabled && uiState.processesAccelerationEnabled);

        setPendingIfChanged(radiusOption, uiState.resolveDisplayedAccelerationRadius());
        setPendingIfChanged(speedOption, uiState.resolveDisplayedAccelerationSpeedPercent());
        setPendingIfChanged(playersAffectedOption, uiState.resolveDisplayedPlayersAffected());
    }

    private static int resolveSimulationDistance(Minecraft client, SeamlessSleepServerConfig serverCfg) {
        if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
            return Math.max(1, client.getSingleplayerServer().getPlayerList().getSimulationDistance());
        }
        int snapshotDistance = Math.max(1, SeamlessSleepServerConfigSnapshot.getServerSimulationDistance());
        int configuredRadius = serverCfg.worldSleepAcceleration.resolveManualRadiusChunks(
                WorldSleepAccelerationConfig.DEFAULT_MANUAL_RADIUS_CHUNKS
        );
        return Math.max(snapshotDistance, configuredRadius);
    }

    private static <T> void listen(Option<T> option, Consumer<T> consumer) {
        option.addEventListener((opt, event) -> {
            if (event == dev.isxander.yacl3.api.OptionEventListener.Event.STATE_CHANGE) {
                consumer.accept(opt.pendingValue());
            }
        });
    }

    private static <T> void setPendingIfChanged(Option<T> option, T value) {
        if (!Objects.equals(option.pendingValue(), value)) {
            option.requestSet(value);
        }
    }

    private static int toPercent(double value) {
        return Mth.clamp((int) Math.round(value * 100.0D), 0, 100);
    }

    private static double fromPercent(int value) {
        return Mth.clamp(value, 0, 100) / 100.0D;
    }

    private static Component serverDescription(Component description, String commandKey) {
        return Component.empty()
                .append(description)
                .append("\n\n")
                .append(serverControlledBadge())
                .append("\n")
                .append(serverCommandHint(commandKey));
    }

    private static Component serverControlledBadge() {
        return Component.translatable("config.seamlesssleep.server_controlled").withStyle(ChatFormatting.YELLOW);
    }

    private static Component serverCommandHint(String commandKey) {
        return Component.translatable(
                "config.seamlesssleep.server_command",
                Component.translatable(commandKey)
        ).withStyle(ChatFormatting.GRAY);
    }

    private static OptionDescription description(Component description, boolean available) {
        return available
                ? OptionDescription.of(description)
                : OptionDescription.of(description, serverControlledBadge());
    }

    private static OptionDescription optionDescription(Component description, Component disabledReason, boolean available) {
        if (available || disabledReason.getString().isBlank()) {
            return OptionDescription.of(description);
        }
        return OptionDescription.of(description, disabledReason);
    }

    private static Option<Double> buildDoubleSlider(Component name,
                                                    Component description,
                                                    Component disabledReason,
                                                    double def,
                                                    double min,
                                                    double max,
                                                    double step,
                                                    Supplier<Double> getter,
                                                    Consumer<Double> setter,
                                                    boolean available,
                                                    Function<Double, Component> formatter) {
        OptionDescription optionDescription = optionDescription(description, disabledReason, available);
        Option.Builder<Double> builder = Option.<Double>createBuilder()
                .name(name)
                .description(optionDescription)
                .binding(def, getter::get, setter::accept)
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
                                                  Component disabledReason,
                                                  int def,
                                                  int min,
                                                  int max,
                                                  int step,
                                                  Supplier<Integer> getter,
                                                  Consumer<Integer> setter,
                                                  boolean available,
                                                  Function<Integer, Component> formatter) {
        OptionDescription optionDescription = optionDescription(description, disabledReason, available);
        int safeStep = Math.max(1, step);
        int safeMax = max <= min ? min + safeStep : max;
        Option.Builder<Integer> builder = Option.<Integer>createBuilder()
                .name(name)
                .description(optionDescription)
                .binding(def, getter::get, setter::accept)
                .controller(opt -> {
                    IntegerSliderControllerBuilder slider = IntegerSliderControllerBuilder.create(opt)
                            .range(min, safeMax)
                            .step(safeStep);
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
                                               Supplier<Boolean> getter,
                                               Consumer<Boolean> setter,
                                               boolean available) {
        OptionDescription optionDescription = optionDescription(description, disabledReason, available);
        Option.Builder<Boolean> builder = Option.<Boolean>createBuilder()
                .name(name)
                .description(optionDescription)
                .binding(def, getter::get, setter::accept)
                .controller(BooleanControllerBuilder::create);
        builder.available(available);
        return builder.build();
    }

    private static <E extends Enum<E>> Option<E> buildEnumOption(Component name,
                                                                 Component description,
                                                                 Component disabledReason,
                                                                 E def,
                                                                 Class<E> enumClass,
                                                                 Supplier<E> getter,
                                                                 Consumer<E> setter,
                                                                 boolean available,
                                                                 Function<E, Component> formatter) {
        OptionDescription optionDescription = optionDescription(description, disabledReason, available);
        Option.Builder<E> builder = Option.<E>createBuilder()
                .name(name)
                .description(optionDescription)
                .binding(def, getter::get, setter::accept)
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(enumClass)
                        .formatValue(formatter::apply));
        builder.available(available);
        return builder.build();
    }

    private static Component enumText(String keyPrefix, Enum<?> value) {
        return Component.translatable(keyPrefix + "." + value.name().toLowerCase(Locale.ROOT));
    }

    private static Component formatPercentValue(Integer value) {
        if (value == null || value <= 0) {
            return Component.translatable("config.seamlesssleep.value.none");
        }
        if (value >= 100) {
            return Component.translatable("config.seamlesssleep.value.max");
        }
        return Component.literal(value + "%");
    }

    private static Component formatVanillaHiddenPercentValue(Integer value) {
        if (value == null || value <= 0) {
            return Component.translatable("config.seamlesssleep.value.hidden");
        }
        if (value >= 100) {
            return Component.translatable("config.seamlesssleep.value.vanilla");
        }
        return Component.literal(value + "%");
    }

    private static Component formatVanillaHiddenLinesValue(Integer value) {
        if (value == null || value <= 0) {
            return Component.translatable("config.seamlesssleep.value.hidden");
        }
        if (value >= 12) {
            return Component.translatable("config.seamlesssleep.value.vanilla");
        }
        return Component.literal(Integer.toString(value));
    }

    private static Component formatWeatherChanceValue(Integer value) {
        if (value == null || value <= 0) {
            return Component.translatable("config.seamlesssleep.value.never");
        }
        if (value >= 100) {
            return Component.translatable("config.seamlesssleep.value.always");
        }
        return Component.literal(value + "%");
    }

    private static Component formatRadiusValue(Integer value, int simulationDistance) {
        int resolvedSimulationDistance = Math.max(1, simulationDistance);
        int resolvedValue = Mth.clamp(value == null ? resolvedSimulationDistance : value, 1, resolvedSimulationDistance);
        if (resolvedValue >= resolvedSimulationDistance) {
            return Component.translatable(
                    "config.seamlesssleep.value.simulation_distance",
                    resolvedSimulationDistance
            );
        }
        if (resolvedValue == 1) {
            return Component.translatable("config.seamlesssleep.value.chunk.single", resolvedValue);
        }
        return Component.translatable("config.seamlesssleep.value.chunk.multiple", resolvedValue);
    }

    private static Component formatDegreesValue(Integer value) {
        int resolved = value == null ? 0 : value;
        return Component.literal(resolved + "\u00B0");
    }

    private static Component formatMultiplierValue(Double value) {
        double resolved = value == null ? 1.0D : value;
        return Component.literal(String.format(Locale.ROOT, "%.2fx", resolved));
    }

    private static final class ServerConfigUiState {
        private final int simulationDistance;

        private int sleepWeatherClearChancePercent;
        private double sleepAnimationDurationMultiplier;
        private WorldSleepAccelerationMode mode;
        private WorldSleepAutomaticMode automaticMode;
        private WorldSleepAccelerationPlayersAffected playersAffected;
        private int manualAccelerationRadiusChunks;
        private int manualAccelerationSpeedPercent;
        private boolean grassAndFoliageAccelerationEnabled;
        private boolean cropsAndSaplingsAccelerationEnabled;
        private boolean kelpAccelerationEnabled;
        private boolean vanillaOnlyAcceleration;
        private boolean processesAccelerationEnabled;
        private int processesSpeedPercent;
        private int boundSleepWeatherClearChancePercent;
        private double boundSleepAnimationDurationMultiplier;
        private WorldSleepAccelerationMode boundMode;
        private int boundDisplayedAccelerationRadius;
        private int boundDisplayedAccelerationSpeedPercent;
        private WorldSleepAccelerationPlayersAffected boundDisplayedPlayersAffected;
        private WorldSleepAutomaticMode boundAutomaticMode;
        private boolean boundGrassAndFoliageAccelerationEnabled;
        private boolean boundCropsAndSaplingsAccelerationEnabled;
        private boolean boundKelpAccelerationEnabled;
        private boolean boundVanillaOnlyAcceleration;
        private boolean boundProcessesAccelerationEnabled;
        private int boundProcessesSpeedPercent;

        private ServerConfigUiState(int simulationDistance) {
            this.simulationDistance = Math.max(1, simulationDistance);
        }

        private static ServerConfigUiState fromLocal(SeamlessSleepServerConfig serverCfg, int simulationDistance) {
            ServerConfigUiState state = new ServerConfigUiState(simulationDistance);
            state.sleepWeatherClearChancePercent = serverCfg.sleepWeatherClearChancePercent;
            state.sleepAnimationDurationMultiplier = serverCfg.sleepAnimationDurationMultiplier;
            state.mode = serverCfg.worldSleepAcceleration.mode;
            state.automaticMode = serverCfg.worldSleepAcceleration.automaticMode;
            state.playersAffected = serverCfg.worldSleepAcceleration.playersAffected;
            state.manualAccelerationRadiusChunks = serverCfg.worldSleepAcceleration.manualAccelerationRadiusChunks;
            state.manualAccelerationSpeedPercent = serverCfg.worldSleepAcceleration.manualAccelerationSpeedPercent;
            state.grassAndFoliageAccelerationEnabled = serverCfg.worldSleepAcceleration.grassAndFoliageAccelerationEnabled;
            state.cropsAndSaplingsAccelerationEnabled = serverCfg.worldSleepAcceleration.cropsAndSaplingsAccelerationEnabled;
            state.kelpAccelerationEnabled = serverCfg.worldSleepAcceleration.kelpAccelerationEnabled;
            state.vanillaOnlyAcceleration = serverCfg.worldSleepAcceleration.vanillaOnlyAcceleration;
            state.processesAccelerationEnabled = serverCfg.worldSleepAcceleration.processesAccelerationEnabled;
            state.processesSpeedPercent = serverCfg.worldSleepAcceleration.processesSpeedPercent;
            state.snapshotBoundValues();
            return state;
        }

        private static ServerConfigUiState fromSnapshot() {
            ServerConfigUiState state = new ServerConfigUiState(SeamlessSleepServerConfigSnapshot.getServerSimulationDistance());
            state.sleepWeatherClearChancePercent = SeamlessSleepServerConfigSnapshot.getSleepWeatherClearChancePercent();
            state.sleepAnimationDurationMultiplier = SeamlessSleepServerConfigSnapshot.getSleepAnimationDurationMultiplier();
            state.mode = SeamlessSleepServerConfigSnapshot.getWorldSleepAccelerationMode();
            state.automaticMode = SeamlessSleepServerConfigSnapshot.getWorldSleepAutomaticMode();
            state.playersAffected = SeamlessSleepServerConfigSnapshot.getWorldSleepAccelerationPlayersAffected();
            state.manualAccelerationRadiusChunks = SeamlessSleepServerConfigSnapshot.getManualAccelerationRadiusChunks();
            state.manualAccelerationSpeedPercent = SeamlessSleepServerConfigSnapshot.getManualAccelerationSpeedPercent();
            state.grassAndFoliageAccelerationEnabled = SeamlessSleepServerConfigSnapshot.isGrassAndFoliageAccelerationEnabled();
            state.cropsAndSaplingsAccelerationEnabled = SeamlessSleepServerConfigSnapshot.isCropsAndSaplingsAccelerationEnabled();
            state.kelpAccelerationEnabled = SeamlessSleepServerConfigSnapshot.isKelpAccelerationEnabled();
            state.vanillaOnlyAcceleration = SeamlessSleepServerConfigSnapshot.isVanillaOnlyAcceleration();
            state.processesAccelerationEnabled = SeamlessSleepServerConfigSnapshot.isProcessesAccelerationEnabled();
            state.processesSpeedPercent = SeamlessSleepServerConfigSnapshot.getProcessesSpeedPercent();
            state.snapshotBoundValues();
            return state;
        }

        private void snapshotBoundValues() {
            this.boundSleepWeatherClearChancePercent = this.sleepWeatherClearChancePercent;
            this.boundSleepAnimationDurationMultiplier = this.sleepAnimationDurationMultiplier;
            this.boundMode = this.mode == null ? WorldSleepAccelerationMode.AUTOMATIC : this.mode;
            this.boundDisplayedAccelerationRadius = this.resolveDisplayedAccelerationRadius();
            this.boundDisplayedAccelerationSpeedPercent = this.resolveDisplayedAccelerationSpeedPercent();
            this.boundDisplayedPlayersAffected = this.resolveDisplayedPlayersAffected();
            this.boundAutomaticMode = this.automaticMode == null ? WorldSleepAutomaticMode.AGGRESSIVE : this.automaticMode;
            this.boundGrassAndFoliageAccelerationEnabled = this.grassAndFoliageAccelerationEnabled;
            this.boundCropsAndSaplingsAccelerationEnabled = this.cropsAndSaplingsAccelerationEnabled;
            this.boundKelpAccelerationEnabled = this.kelpAccelerationEnabled;
            this.boundVanillaOnlyAcceleration = this.vanillaOnlyAcceleration;
            this.boundProcessesAccelerationEnabled = this.processesAccelerationEnabled;
            this.boundProcessesSpeedPercent = Mth.clamp(this.processesSpeedPercent, 0, 100);
        }

        private int resolveManualRadius() {
            int configured = manualAccelerationRadiusChunks <= 0
                    ? WorldSleepAccelerationConfig.DEFAULT_MANUAL_RADIUS_CHUNKS
                    : manualAccelerationRadiusChunks;
            return Mth.clamp(configured, 1, simulationDistance);
        }

        private int resolveAutomaticRadius() {
            WorldSleepAccelerationConfig config = new WorldSleepAccelerationConfig();
            config.automaticMode = automaticMode;
            return config.getAutomaticCeiling(simulationDistance).radiusChunks();
        }

        private WorldSleepAccelerationPlayersAffected resolveAutomaticPlayersAffected() {
            WorldSleepAccelerationConfig config = new WorldSleepAccelerationConfig();
            config.automaticMode = automaticMode;
            return config.resolveAutomaticPlayersAffected();
        }

        private int resolveAutomaticSpeedPercent() {
            WorldSleepAccelerationConfig config = new WorldSleepAccelerationConfig();
            config.automaticMode = automaticMode;
            return config.getAutomaticCeiling(simulationDistance).speedPercent();
        }

        private WorldSleepAccelerationPlayersAffected resolveDisplayedPlayersAffected() {
            return mode == WorldSleepAccelerationMode.AUTOMATIC
                    ? resolveAutomaticPlayersAffected()
                    : (playersAffected == null ? WorldSleepAccelerationPlayersAffected.ALL_PLAYERS : playersAffected);
        }

        private int resolveDisplayedAccelerationRadius() {
            return mode == WorldSleepAccelerationMode.AUTOMATIC
                    ? resolveAutomaticRadius()
                    : resolveManualRadius();
        }

        private int resolveDisplayedAccelerationSpeedPercent() {
            return mode == WorldSleepAccelerationMode.AUTOMATIC
                    ? resolveAutomaticSpeedPercent()
                    : Mth.clamp(manualAccelerationSpeedPercent, 0, 100);
        }

        private void applyTo(SeamlessSleepServerConfig serverCfg) {
            serverCfg.sleepWeatherClearChancePercent = sleepWeatherClearChancePercent;
            serverCfg.sleepAnimationDurationMultiplier = sleepAnimationDurationMultiplier;
            serverCfg.worldSleepAcceleration.mode = mode;
            serverCfg.worldSleepAcceleration.automaticMode = automaticMode;
            serverCfg.worldSleepAcceleration.playersAffected = playersAffected == null
                    ? WorldSleepAccelerationPlayersAffected.ALL_PLAYERS
                    : playersAffected;
            serverCfg.worldSleepAcceleration.manualAccelerationRadiusChunks = resolveManualRadius();
            serverCfg.worldSleepAcceleration.manualAccelerationSpeedPercent = manualAccelerationSpeedPercent;
            serverCfg.worldSleepAcceleration.grassAndFoliageAccelerationEnabled = grassAndFoliageAccelerationEnabled;
            serverCfg.worldSleepAcceleration.cropsAndSaplingsAccelerationEnabled = cropsAndSaplingsAccelerationEnabled;
            serverCfg.worldSleepAcceleration.kelpAccelerationEnabled = kelpAccelerationEnabled;
            serverCfg.worldSleepAcceleration.vanillaOnlyAcceleration = vanillaOnlyAcceleration;
            serverCfg.worldSleepAcceleration.processesAccelerationEnabled = processesAccelerationEnabled;
            serverCfg.worldSleepAcceleration.processesSpeedPercent = processesSpeedPercent;
        }
    }
}
