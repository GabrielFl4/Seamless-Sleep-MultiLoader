package net.aqualoco.sec.client;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.ControllerBuilder;
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.image.ImageRenderer;
import dev.isxander.yacl3.gui.utils.GuiUtils;
import net.aqualoco.sec.client.sleepindicator.SleepIndicatorAnchor;
import net.aqualoco.sec.client.sleepindicator.SleepIndicatorMode;
import net.aqualoco.sec.client.sleepindicator.SleepIndicatorVisibility;
import net.aqualoco.sec.client.sleepindicator.TimestampStyle;
import net.aqualoco.sec.client.sleepvisual.SleepZzzConfigBridge;
import net.aqualoco.sec.client.sleepvisual.SleepZzzStyle;
import net.aqualoco.sec.config.ServerConfigMutationService;
import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.aqualoco.sec.config.SleepEligibilityMode;
import net.aqualoco.sec.config.WorldSleepAccelerationConfig;
import net.aqualoco.sec.config.WorldSleepAccelerationMode;
import net.aqualoco.sec.config.WorldSleepAccelerationPlayersAffected;
import net.aqualoco.sec.config.WorldSleepAutomaticMode;
import net.aqualoco.sec.network.ServerConfigField;
import net.aqualoco.sec.network.ServerConfigUpdateC2SPayload;
import net.aqualoco.sec.network.ServerConfigUpdateResultS2CPayload;
import net.aqualoco.sec.network.ServerConfigUpdateStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.awt.Color;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class FabricYaclConfigScreen {
    private static final SleepEligibilityMode[] SELECTABLE_SLEEP_ELIGIBILITY_MODES = {
            SleepEligibilityMode.INSOMNIA,
            SleepEligibilityMode.VANILLA,
            SleepEligibilityMode.DAY_INCLUDED
    };

    private FabricYaclConfigScreen() {
    }

    static Screen create(Screen parent) {
        SeamlessSleepClientConfig clientCfg = SeamlessSleepClientConfigManager.get();
        clientCfg.clamp();

        SeamlessSleepServerConfig serverCfg = SeamlessSleepServerConfigManager.get();
        serverCfg.clamp();

        Minecraft client = Minecraft.getInstance();
        boolean hasIntegratedServer = client.getSingleplayerServer() != null;
        boolean connectedToRemote = client.getConnection() != null && !client.hasSingleplayerServer();
        boolean liveServer = connectedToRemote || hasIntegratedServer;
        boolean canEditServerConfig = !connectedToRemote || RemoteServerConfigClientState.canEditServerConfig();
        int simulationDistance = resolveSimulationDistance(client, serverCfg);
        ServerConfigUiState serverUiState = connectedToRemote
                ? ServerConfigUiState.fromSnapshot()
                : ServerConfigUiState.fromLocal(serverCfg, simulationDistance);
        RemoteServerConfigScreenSession serverSession = new RemoteServerConfigScreenSession(
                connectedToRemote,
                liveServer,
                serverUiState,
                canEditServerConfig
        );

        Screen screen = YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("config.seamlesssleep.title"))
                .category(buildClientConfigCategory(clientCfg))
                .category(buildServerConfigCategory(serverSession))
                .category(buildAdvancedCategory(clientCfg, serverSession))
                .save(() -> {
                    clientCfg.clamp();
                    SeamlessSleepClientConfigManager.save();
                    if (serverSession.remote()) {
                        serverSession.sendPatch();
                    } else if (serverSession.liveServer()) {
                        serverSession.saveLocalPatch(client);
                    } else if (serverSession.canEditServerConfig()) {
                        serverUiState.applyTo(serverCfg);
                        serverCfg.clamp();
                        SeamlessSleepServerConfigManager.save();
                    }
                })
                .screenInit(serverSession::attachScreen)
                .build()
                .generateScreen(parent);

        if (connectedToRemote) {
            RemoteServerConfigClientState.requestAccessRefresh();
        }
        return screen;
    }

    private static ConfigCategory buildClientConfigCategory(SeamlessSleepClientConfig cfg) {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("config.seamlesssleep.category.client"))
                .group(buildSleepIndicatorGroup(cfg))
                .group(buildSleepZzzGroup(cfg))
                .group(buildOverlayGroup(cfg))
                .group(buildChatGroup(cfg))
                .group(buildCameraGroup(cfg))
                .build();
    }

    private static ConfigCategory buildServerConfigCategory(RemoteServerConfigScreenSession session) {
        ServerConfigUiState uiState = session.uiState();
        boolean canEditServerConfig = session.canEditServerConfig();
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
                FabricYaclConfigScreen::formatWeatherChanceValue,
                session,
                ServerConfigField.SLEEP_WEATHER_CLEAR_CHANCE_PERCENT
        );
        listenServer(session, ServerConfigField.SLEEP_WEATHER_CLEAR_CHANCE_PERCENT, weatherChanceOption, value -> uiState.sleepWeatherClearChancePercent = value);

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
                FabricYaclConfigScreen::formatMultiplierValue,
                session,
                ServerConfigField.SLEEP_ANIMATION_DURATION_MULTIPLIER
        );
        listenServer(session, ServerConfigField.SLEEP_ANIMATION_DURATION_MULTIPLIER, animationDurationOption, value -> uiState.sleepAnimationDurationMultiplier = value);

        Option<Integer> fallAsleepDelayOption = buildIntSlider(
                Component.translatable("config.seamlesssleep.sleep.fall_asleep_delay"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.sleep.fall_asleep_delay.desc"),
                        "config.seamlesssleep.sleep.fall_asleep_delay.command"
                ),
                Component.empty(),
                SeamlessSleepServerConfig.DEFAULT_FALL_ASLEEP_DELAY_TICKS,
                SeamlessSleepServerConfig.MIN_FALL_ASLEEP_DELAY_TICKS,
                SeamlessSleepServerConfig.MAX_FALL_ASLEEP_DELAY_TICKS,
                5,
                () -> uiState.boundFallAsleepDelayTicks,
                value -> uiState.boundFallAsleepDelayTicks = value,
                canEditServerConfig,
                FabricYaclConfigScreen::formatFallAsleepDelayValue,
                session,
                ServerConfigField.FALL_ASLEEP_DELAY_TICKS
        );
        listenServer(session, ServerConfigField.FALL_ASLEEP_DELAY_TICKS, fallAsleepDelayOption, value -> uiState.fallAsleepDelayTicks = value);

        Option<SleepEligibilityMode> sleepEligibilityOption = buildEnumOption(
                Component.translatable("config.seamlesssleep.sleep.eligibility"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.sleep.eligibility.desc"),
                        "config.seamlesssleep.sleep.eligibility.command"
                ),
                Component.empty(),
                SleepEligibilityMode.VANILLA,
                SleepEligibilityMode.class,
                () -> selectableSleepEligibility(uiState.boundSleepEligibility),
                value -> uiState.boundSleepEligibility = selectableSleepEligibility(value),
                canEditServerConfig,
                value -> enumText("config.seamlesssleep.sleep.eligibility", value),
                session,
                ServerConfigField.SLEEP_ELIGIBILITY,
                SELECTABLE_SLEEP_ELIGIBILITY_MODES
        );
        listenServer(session, ServerConfigField.SLEEP_ELIGIBILITY, sleepEligibilityOption, value -> uiState.sleepEligibility = value);

        Option<Boolean> overrideOverlayTextOption = buildToggle(
                Component.translatable("config.seamlesssleep.sleep.override_overlay_text"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.sleep.override_overlay_text.desc"),
                        "config.seamlesssleep.sleep.override_overlay_text.command"
                ),
                Component.empty(),
                false,
                () -> uiState.boundOverrideOverlayText,
                value -> uiState.boundOverrideOverlayText = value,
                canEditServerConfig,
                session,
                ServerConfigField.OVERRIDE_OVERLAY_TEXT
        );
        Option<String> overlayCustomTextOption = buildStringOption(
                Component.translatable("config.seamlesssleep.sleep.overlay_custom_text"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.sleep.overlay_custom_text.desc"),
                        "config.seamlesssleep.sleep.overlay_custom_text.command"
                ),
                Component.empty(),
                SeamlessSleepServerConfig.DEFAULT_OVERLAY_CUSTOM_TEXT,
                () -> uiState.boundOverlayCustomText,
                value -> uiState.boundOverlayCustomText = value,
                canEditServerConfig && uiState.overrideOverlayText,
                session,
                ServerConfigField.OVERLAY_CUSTOM_TEXT
        );
        Runnable refreshOverlayTextOptions = () -> overlayCustomTextOption.setAvailable(
                session.canEditServerConfig() && uiState.overrideOverlayText
        );
        listenServer(session, ServerConfigField.OVERRIDE_OVERLAY_TEXT, overrideOverlayTextOption, value -> {
            uiState.overrideOverlayText = value;
            session.runWithSuppressedDirtyTracking(refreshOverlayTextOptions);
        });
        listenServer(session, ServerConfigField.OVERLAY_CUSTOM_TEXT, overlayCustomTextOption, value -> uiState.overlayCustomText = value);
        session.runWithSuppressedDirtyTracking(refreshOverlayTextOptions);

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
                value -> enumText("config.seamlesssleep.world_acceleration.mode", value),
                session,
                ServerConfigField.WORLD_SLEEP_ACCELERATION_MODE
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
                value -> formatRadiusValue(value, uiState.simulationDistance),
                session,
                ServerConfigField.MANUAL_ACCELERATION_RADIUS_CHUNKS
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
                FabricYaclConfigScreen::formatPercentValue,
                session,
                ServerConfigField.MANUAL_ACCELERATION_SPEED_PERCENT
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
                value -> enumText("config.seamlesssleep.world_acceleration.players_affected", value),
                session,
                ServerConfigField.WORLD_SLEEP_ACCELERATION_PLAYERS_AFFECTED
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
                value -> enumText("config.seamlesssleep.world_acceleration.automatic_mode", value),
                session,
                ServerConfigField.WORLD_SLEEP_AUTOMATIC_MODE
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
                canEditServerConfig && uiState.mode != WorldSleepAccelerationMode.OFF,
                session,
                ServerConfigField.GRASS_AND_FOLIAGE_ACCELERATION_ENABLED
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
                canEditServerConfig && uiState.mode != WorldSleepAccelerationMode.OFF,
                session,
                ServerConfigField.CROPS_AND_SAPLINGS_ACCELERATION_ENABLED
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
                canEditServerConfig && uiState.mode != WorldSleepAccelerationMode.OFF,
                session,
                ServerConfigField.KELP_ACCELERATION_ENABLED
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
                canEditServerConfig && uiState.mode != WorldSleepAccelerationMode.OFF,
                session,
                ServerConfigField.VANILLA_ONLY_ACCELERATION
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
                canEditServerConfig && uiState.mode != WorldSleepAccelerationMode.OFF,
                session,
                ServerConfigField.PROCESSES_ACCELERATION_ENABLED
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
                FabricYaclConfigScreen::formatPercentValue,
                session,
                ServerConfigField.PROCESSES_SPEED_PERCENT
        );

        Runnable refreshAccelerationOptions = () -> refreshAccelerationOptions(
                session.canEditServerConfig(),
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

        listenServer(session, ServerConfigField.WORLD_SLEEP_ACCELERATION_MODE, modeOption, value -> {
            uiState.mode = value;
            session.runWithSuppressedDirtyTracking(refreshAccelerationOptions);
        });
        listenServer(session, ServerConfigField.MANUAL_ACCELERATION_RADIUS_CHUNKS, radiusOption, value -> {
            if (uiState.mode == WorldSleepAccelerationMode.MANUAL) {
                uiState.manualAccelerationRadiusChunks = value;
            }
        });
        listenServer(session, ServerConfigField.MANUAL_ACCELERATION_SPEED_PERCENT, speedOption, value -> {
            if (uiState.mode == WorldSleepAccelerationMode.MANUAL) {
                uiState.manualAccelerationSpeedPercent = value;
            }
        });
        listenServer(session, ServerConfigField.WORLD_SLEEP_ACCELERATION_PLAYERS_AFFECTED, playersAffectedOption, value -> {
            if (uiState.mode == WorldSleepAccelerationMode.MANUAL) {
                uiState.playersAffected = value;
            }
        });
        listenServer(session, ServerConfigField.WORLD_SLEEP_AUTOMATIC_MODE, automaticModeOption, value -> {
            uiState.automaticMode = value;
            session.runWithSuppressedDirtyTracking(refreshAccelerationOptions);
        });
        listenServer(session, ServerConfigField.GRASS_AND_FOLIAGE_ACCELERATION_ENABLED, grassOption, value -> uiState.grassAndFoliageAccelerationEnabled = value);
        listenServer(session, ServerConfigField.CROPS_AND_SAPLINGS_ACCELERATION_ENABLED, cropsOption, value -> uiState.cropsAndSaplingsAccelerationEnabled = value);
        listenServer(session, ServerConfigField.KELP_ACCELERATION_ENABLED, kelpOption, value -> uiState.kelpAccelerationEnabled = value);
        listenServer(session, ServerConfigField.VANILLA_ONLY_ACCELERATION, vanillaOnlyOption, value -> uiState.vanillaOnlyAcceleration = value);
        listenServer(session, ServerConfigField.PROCESSES_ACCELERATION_ENABLED, processesOption, value -> {
            uiState.processesAccelerationEnabled = value;
            session.runWithSuppressedDirtyTracking(refreshAccelerationOptions);
        });
        listenServer(session, ServerConfigField.PROCESSES_SPEED_PERCENT, processesSpeedOption, value -> uiState.processesSpeedPercent = value);
        session.runWithSuppressedDirtyTracking(refreshAccelerationOptions);
        session.setRefreshers(refreshOverlayTextOptions, refreshAccelerationOptions);

        OptionGroup generalGroup = OptionGroup.createBuilder()
                .name(Component.translatable("config.seamlesssleep.server.group.general"))
                .description(description(
                        Component.translatable("config.seamlesssleep.server.group.general.desc"),
                        canEditServerConfig
                ))
                .collapsed(false)
                .option(weatherChanceOption)
                .option(animationDurationOption)
                .option(fallAsleepDelayOption)
                .option(sleepEligibilityOption)
                .option(overrideOverlayTextOption)
                .option(overlayCustomTextOption)
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

    private static ConfigCategory buildAdvancedCategory(
            SeamlessSleepClientConfig cfg,
            RemoteServerConfigScreenSession session
    ) {
        ServerConfigUiState uiState = session.uiState();
        boolean canEditServerConfig = session.canEditServerConfig();
        Option<Integer> madeInHeavenChanceOption = buildIntSlider(
                Component.translatable("config.seamlesssleep.easter_eggs.made_in_heaven_chance"),
                serverDescription(
                        Component.translatable("config.seamlesssleep.easter_eggs.made_in_heaven_chance.desc"),
                        "config.seamlesssleep.easter_eggs.made_in_heaven_chance.command"
                ),
                Component.empty(),
                0,
                0,
                100,
                1,
                () -> uiState.boundMadeInHeavenChancePercent,
                value -> uiState.boundMadeInHeavenChancePercent = value,
                canEditServerConfig,
                FabricYaclConfigScreen::formatWeatherChanceValue,
                session,
                ServerConfigField.MADE_IN_HEAVEN_CHANCE_PERCENT
        );
        listenServer(session, ServerConfigField.MADE_IN_HEAVEN_CHANCE_PERCENT, madeInHeavenChanceOption, value -> uiState.madeInHeavenChancePercent = value);

        OptionGroup easterEggsGroup = OptionGroup.createBuilder()
                .name(Component.translatable("config.seamlesssleep.server.group.easter_eggs"))
                .description(description(
                        Component.translatable("config.seamlesssleep.server.group.easter_eggs.desc"),
                        canEditServerConfig
                ))
                .collapsed(false)
                .option(madeInHeavenChanceOption)
                .build();

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
                .option(buildToggle(
                        Component.translatable("config.seamlesssleep.misc.disable_sounds_during_replay"),
                        Component.translatable("config.seamlesssleep.misc.disable_sounds_during_replay.desc"),
                        Component.empty(),
                        false,
                        () -> cfg.disableSoundsDuringReplay,
                        value -> cfg.disableSoundsDuringReplay = value,
                        true
                ))
                .group(easterEggsGroup)
                .build();
    }

    private static OptionGroup buildOverlayGroup(SeamlessSleepClientConfig cfg) {
        return OptionGroup.createBuilder()
                .name(Component.translatable("config.seamlesssleep.client.group.overlay"))
                .description(OptionDescription.of(Component.translatable("config.seamlesssleep.client.group.overlay.desc")))
                .collapsed(false)
                .option(buildIntSlider(
                        Component.translatable("config.seamlesssleep.overlay.darkness"),
                        Component.translatable("config.seamlesssleep.overlay.darkness.desc"),
                        Component.empty(),
                        20,
                        0,
                        100,
                        1,
                        () -> toPercent(cfg.sleepOverlayDarknessMultiplier),
                        value -> cfg.sleepOverlayDarknessMultiplier = fromPercent(value),
                        true,
                        FabricYaclConfigScreen::formatVanillaHiddenPercentValue
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

    private static OptionGroup buildSleepIndicatorGroup(SeamlessSleepClientConfig cfg) {
        Option<SleepIndicatorMode> modeOption = buildEnumOption(
                Component.translatable("config.seamlesssleep.sleep_indicator.mode"),
                Component.translatable("config.seamlesssleep.sleep_indicator.mode.desc"),
                Component.empty(),
                SleepIndicatorMode.BIOME_CLOCK,
                SleepIndicatorMode.class,
                () -> cfg.sleepIndicatorMode,
                value -> cfg.sleepIndicatorMode = value == null ? SleepIndicatorMode.BIOME_CLOCK : value,
                true,
                value -> enumText("config.seamlesssleep.sleep_indicator.mode", value)
        );
        Option<SleepIndicatorAnchor> anchorOption = buildEnumOption(
                Component.translatable("config.seamlesssleep.sleep_indicator.anchor"),
                Component.translatable("config.seamlesssleep.sleep_indicator.anchor.desc"),
                Component.empty(),
                SleepIndicatorAnchor.TOP_LEFT,
                SleepIndicatorAnchor.class,
                () -> cfg.sleepIndicatorAnchor,
                value -> cfg.sleepIndicatorAnchor = value == null ? SleepIndicatorAnchor.TOP_LEFT : value,
                true,
                value -> enumText("config.seamlesssleep.sleep_indicator.anchor", value)
        );
        Option<SleepIndicatorVisibility> visibilityOption = buildEnumOption(
                Component.translatable("config.seamlesssleep.sleep_indicator.visibility"),
                Component.translatable("config.seamlesssleep.sleep_indicator.visibility.desc"),
                Component.empty(),
                SleepIndicatorVisibility.BED,
                SleepIndicatorVisibility.class,
                () -> cfg.sleepIndicatorVisibility,
                value -> cfg.sleepIndicatorVisibility = value == null ? SleepIndicatorVisibility.BED : value,
                true,
                value -> enumText("config.seamlesssleep.sleep_indicator.visibility", value)
        );
        Option<Double> scaleOption = buildDoubleSlider(
                Component.translatable("config.seamlesssleep.sleep_indicator.scale"),
                Component.translatable("config.seamlesssleep.sleep_indicator.scale.desc"),
                Component.empty(),
                1.0D,
                0.25D,
                4.0D,
                0.05D,
                () -> cfg.sleepIndicatorScale,
                value -> cfg.sleepIndicatorScale = value,
                true,
                FabricYaclConfigScreen::formatMultiplierValue
        );
        Option<TimestampStyle> timestampStyleOption = buildEnumOption(
                Component.translatable("config.seamlesssleep.sleep_indicator.timestamp.style"),
                Component.translatable("config.seamlesssleep.sleep_indicator.timestamp.style.desc"),
                Component.empty(),
                TimestampStyle.DAY_FIRST,
                TimestampStyle.class,
                () -> cfg.timestampStyle,
                value -> cfg.timestampStyle = value == null ? TimestampStyle.DAY_FIRST : value,
                true,
                value -> enumText("config.seamlesssleep.sleep_indicator.timestamp.style", value)
        );
        Option<Color> timestampColorOption = buildColorOption(
                Component.translatable("config.seamlesssleep.sleep_indicator.timestamp.color"),
                Component.translatable("config.seamlesssleep.sleep_indicator.timestamp.color.desc"),
                Component.empty(),
                new Color(SeamlessSleepClientConfig.DEFAULT_TIMESTAMP_COLOR | 0xFF000000, true),
                () -> new Color((cfg.timestampColor & 0x00FFFFFF) | 0xFF000000, true),
                value -> cfg.timestampColor = value == null
                        ? SeamlessSleepClientConfig.DEFAULT_TIMESTAMP_COLOR
                        : value.getRGB() & 0x00FFFFFF,
                true
        );
        Runnable refreshIndicatorOptions = () -> {
            SleepIndicatorMode mode = cfg.sleepIndicatorMode == null
                    ? SleepIndicatorMode.BIOME_CLOCK
                    : cfg.sleepIndicatorMode;
            boolean indicatorEnabled = mode != SleepIndicatorMode.OFF;
            boolean timestampEnabled = mode == SleepIndicatorMode.TIMESTAMP;
            if (mode == SleepIndicatorMode.TEXT) {
                cfg.sleepIndicatorVisibility = SleepIndicatorVisibility.SLEEP;
                setPendingIfChanged(visibilityOption, SleepIndicatorVisibility.SLEEP);
            }
            anchorOption.setAvailable(indicatorEnabled);
            scaleOption.setAvailable(indicatorEnabled);
            visibilityOption.setAvailable(mode == SleepIndicatorMode.BIOME_CLOCK || mode == SleepIndicatorMode.TIMESTAMP);
            timestampStyleOption.setAvailable(timestampEnabled);
            timestampColorOption.setAvailable(timestampEnabled);
        };

        listen(modeOption, value -> {
            cfg.sleepIndicatorMode = value == null ? SleepIndicatorMode.BIOME_CLOCK : value;
            refreshIndicatorOptions.run();
        });
        listen(anchorOption, value -> cfg.sleepIndicatorAnchor = value == null ? SleepIndicatorAnchor.TOP_LEFT : value);
        listen(visibilityOption, value -> cfg.sleepIndicatorVisibility = value == null ? SleepIndicatorVisibility.BED : value);
        listen(scaleOption, value -> cfg.sleepIndicatorScale = value);
        listen(timestampStyleOption, value -> cfg.timestampStyle = value == null ? TimestampStyle.DAY_FIRST : value);
        listen(timestampColorOption, value -> cfg.timestampColor = value == null
                ? SeamlessSleepClientConfig.DEFAULT_TIMESTAMP_COLOR
                : value.getRGB() & 0x00FFFFFF);
        refreshIndicatorOptions.run();

        return OptionGroup.createBuilder()
                .name(Component.translatable("config.seamlesssleep.client.group.sleep_indicator"))
                .description(OptionDescription.of(Component.translatable("config.seamlesssleep.client.group.sleep_indicator.desc")))
                .collapsed(false)
                .option(modeOption)
                .option(anchorOption)
                .option(visibilityOption)
                .option(scaleOption)
                .option(timestampStyleOption)
                .option(timestampColorOption)
                .build();
    }

    private static OptionGroup buildSleepZzzGroup(SeamlessSleepClientConfig cfg) {
        return OptionGroup.createBuilder()
                .name(Component.translatable("config.seamlesssleep.client.group.sleep_zzz"))
                .description(OptionDescription.of(Component.translatable("config.seamlesssleep.client.group.sleep_zzz.desc")))
                .collapsed(false)
                .option(buildIntSlider(
                        Component.translatable("config.seamlesssleep.sounds.sleep_wind"),
                        Component.translatable("config.seamlesssleep.sounds.sleep_wind.desc"),
                        Component.empty(),
                        40,
                        0,
                        100,
                        1,
                        () -> cfg.sleepWindVolumePercent,
                        value -> cfg.sleepWindVolumePercent = value,
                        true,
                        FabricYaclConfigScreen::formatSoundVolumeValue
                ))
                .option(buildIntSlider(
                        Component.translatable("config.seamlesssleep.sounds.soundtrack"),
                        Component.translatable("config.seamlesssleep.sounds.soundtrack.desc"),
                        Component.empty(),
                        40,
                        0,
                        100,
                        1,
                        () -> cfg.soundtrackVolumePercent,
                        value -> cfg.soundtrackVolumePercent = value,
                        true,
                        FabricYaclConfigScreen::formatSoundVolumeValue
                ))
                .option(buildIntSlider(
                        Component.translatable("config.seamlesssleep.sleep_zzz.chance"),
                        Component.translatable("config.seamlesssleep.sleep_zzz.chance.desc"),
                        Component.empty(),
                        SleepZzzConfigBridge.DEFAULT_CHANCE,
                        0,
                        100,
                        1,
                        () -> cfg.sleepZzzChance,
                        value -> cfg.sleepZzzChance = value,
                        true,
                        FabricYaclConfigScreen::formatWeatherChanceValue
                ))
                .option(buildEnumOption(
                        Component.translatable("config.seamlesssleep.sleep_zzz.style"),
                        Component.translatable("config.seamlesssleep.sleep_zzz.style.desc"),
                        Component.empty(),
                        SleepZzzConfigBridge.DEFAULT_STYLE,
                        SleepZzzStyle.class,
                        () -> SleepZzzConfigBridge.parseStyle(cfg.sleepZzzStyle),
                        value -> cfg.sleepZzzStyle = (value == null ? SleepZzzConfigBridge.DEFAULT_STYLE : value).name(),
                        true,
                        value -> enumText("config.seamlesssleep.sleep_zzz.style", value)
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
                        FabricYaclConfigScreen::formatVanillaHiddenPercentValue
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
                        FabricYaclConfigScreen::formatVanillaHiddenLinesValue
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
                        FabricYaclConfigScreen::formatDegreesValue
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
                        FabricYaclConfigScreen::formatPercentValue
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

    private static Option<String> buildStringOption(Component name,
                                                    Component description,
                                                    Component disabledReason,
                                                    String def,
                                                    Supplier<String> getter,
                                                    Consumer<String> setter,
                                                    boolean available) {
        return buildStringOption(name, description, disabledReason, def, getter, setter, available, null, null);
    }

    private static Option<String> buildStringOption(Component name,
                                                    Component description,
                                                    Component disabledReason,
                                                    String def,
                                                    Supplier<String> getter,
                                                    Consumer<String> setter,
                                                    boolean available,
                                                    RemoteServerConfigScreenSession session,
                                                    ServerConfigField field) {
        OptionDescription optionDescription = optionDescription(description, disabledReason, available);
        Option.Builder<String> builder = withConflictController(Option.<String>createBuilder()
                .name(name)
                .description(withConflictDescription(optionDescription, session, field))
                .binding(def, getter::get, setter::accept),
                StringControllerBuilder::create,
                session,
                field);
        builder.available(available);
        return builder.build();
    }

    private static Option<Color> buildColorOption(Component name,
                                                  Component description,
                                                  Component disabledReason,
                                                  Color def,
                                                  Supplier<Color> getter,
                                                  Consumer<Color> setter,
                                                  boolean available) {
        OptionDescription optionDescription = optionDescription(description, disabledReason, available);
        Option.Builder<Color> builder = withConflictController(Option.<Color>createBuilder()
                .name(name)
                .description(optionDescription)
                .binding(def, getter::get, setter::accept),
                opt -> ColorControllerBuilder.create(opt).allowAlpha(false),
                null,
                null);
        builder.available(available);
        return builder.build();
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

    private static <T> void listenServer(RemoteServerConfigScreenSession session,
                                         ServerConfigField field,
                                         Option<T> option,
                                         Consumer<T> consumer) {
        session.registerOption(field, option);
        option.addEventListener((opt, event) -> {
            if (event == dev.isxander.yacl3.api.OptionEventListener.Event.STATE_CHANGE) {
                consumer.accept(opt.pendingValue());
                session.markDirty(field, opt.changed(), opt.isPendingValueDefault());
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

    private static OptionDescription withConflictDescription(OptionDescription description,
                                                             RemoteServerConfigScreenSession session,
                                                             ServerConfigField field) {
        if (session == null || field == null) {
            return description;
        }
        return new ConflictAwareOptionDescription(description, () -> session.hasConflict(field), session::conflictVisualRevision);
    }

    private static <T> Option.Builder<T> withConflictController(Option.Builder<T> builder,
                                                                Function<Option<T>, ControllerBuilder<T>> controllerFactory,
                                                                RemoteServerConfigScreenSession session,
                                                                ServerConfigField field) {
        if (session == null || field == null) {
            return builder.controller(controllerFactory);
        }
        return builder.customController(option -> new ConflictAwareController<>(
                controllerFactory.apply(option).build(),
                () -> session.hasConflict(field)
        ));
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
        return buildDoubleSlider(name, description, disabledReason, def, min, max, step, getter, setter, available, formatter, null, null);
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
                                                    Function<Double, Component> formatter,
                                                    RemoteServerConfigScreenSession session,
                                                    ServerConfigField field) {
        OptionDescription optionDescription = optionDescription(description, disabledReason, available);
        Option.Builder<Double> builder = withConflictController(Option.<Double>createBuilder()
                .name(name)
                .description(withConflictDescription(optionDescription, session, field))
                .binding(def, getter::get, setter::accept),
                opt -> {
                    DoubleSliderControllerBuilder slider = DoubleSliderControllerBuilder.create(opt)
                            .range(min, max)
                            .step(step);
                    if (formatter != null) {
                        slider = slider.formatValue(formatter::apply);
                    }
                    return slider;
                },
                session,
                field);
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
        return buildIntSlider(name, description, disabledReason, def, min, max, step, getter, setter, available, formatter, null, null);
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
                                                  Function<Integer, Component> formatter,
                                                  RemoteServerConfigScreenSession session,
                                                  ServerConfigField field) {
        OptionDescription optionDescription = optionDescription(description, disabledReason, available);
        int safeStep = Math.max(1, step);
        int safeMax = max <= min ? min + safeStep : max;
        Option.Builder<Integer> builder = withConflictController(Option.<Integer>createBuilder()
                .name(name)
                .description(withConflictDescription(optionDescription, session, field))
                .binding(def, getter::get, setter::accept),
                opt -> {
                    IntegerSliderControllerBuilder slider = IntegerSliderControllerBuilder.create(opt)
                            .range(min, safeMax)
                            .step(safeStep);
                    if (formatter != null) {
                        slider = slider.formatValue(formatter::apply);
                    }
                    return slider;
                },
                session,
                field);
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
        return buildToggle(name, description, disabledReason, def, getter, setter, available, null, null);
    }

    private static Option<Boolean> buildToggle(Component name,
                                               Component description,
                                               Component disabledReason,
                                               boolean def,
                                               Supplier<Boolean> getter,
                                               Consumer<Boolean> setter,
                                               boolean available,
                                               RemoteServerConfigScreenSession session,
                                               ServerConfigField field) {
        OptionDescription optionDescription = optionDescription(description, disabledReason, available);
        Option.Builder<Boolean> builder = withConflictController(Option.<Boolean>createBuilder()
                .name(name)
                .description(withConflictDescription(optionDescription, session, field))
                .binding(def, getter::get, setter::accept),
                BooleanControllerBuilder::create,
                session,
                field);
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
        return buildEnumOption(name, description, disabledReason, def, enumClass, getter, setter, available, formatter, null, null);
    }

    private static <E extends Enum<E>> Option<E> buildEnumOption(Component name,
                                                                 Component description,
                                                                 Component disabledReason,
                                                                 E def,
                                                                 Class<E> enumClass,
                                                                 Supplier<E> getter,
                                                                 Consumer<E> setter,
                                                                 boolean available,
                                                                 Function<E, Component> formatter,
                                                                 RemoteServerConfigScreenSession session,
                                                                 ServerConfigField field) {
        OptionDescription optionDescription = optionDescription(description, disabledReason, available);
        Option.Builder<E> builder = withConflictController(Option.<E>createBuilder()
                .name(name)
                .description(withConflictDescription(optionDescription, session, field))
                .binding(def, getter::get, setter::accept),
                opt -> EnumControllerBuilder.create(opt)
                        .enumClass(enumClass)
                        .formatValue(formatter::apply),
                session,
                field);
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
                                                                 Function<E, Component> formatter,
                                                                 RemoteServerConfigScreenSession session,
                                                                 ServerConfigField field,
                                                                 E[] values) {
        OptionDescription optionDescription = optionDescription(description, disabledReason, available);
        Option.Builder<E> builder = withConflictController(Option.<E>createBuilder()
                .name(name)
                .description(withConflictDescription(optionDescription, session, field))
                .binding(def, getter::get, setter::accept),
                opt -> CyclingListControllerBuilder.create(opt)
                        .values(values)
                        .formatValue(formatter::apply),
                session,
                field);
        builder.available(available);
        return builder.build();
    }

    private static Component enumText(String keyPrefix, Enum<?> value) {
        return Component.translatable(keyPrefix + "." + value.name().toLowerCase(Locale.ROOT));
    }

    private static SleepEligibilityMode selectableSleepEligibility(SleepEligibilityMode value) {
        if (value == SleepEligibilityMode.INSOMNIA
                || value == SleepEligibilityMode.VANILLA
                || value == SleepEligibilityMode.DAY_INCLUDED) {
            return value;
        }
        if (value == SleepEligibilityMode.ALWAYS) {
            return SleepEligibilityMode.DAY_INCLUDED;
        }
        return SleepEligibilityMode.VANILLA;
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

    private static Component formatSoundVolumeValue(Integer value) {
        if (value == null || value <= 0) {
            return Component.translatable("config.seamlesssleep.value.muted").withStyle(ChatFormatting.GRAY);
        }
        return Component.literal(value + "%").withStyle(ChatFormatting.WHITE);
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

    private static Component formatFallAsleepDelayValue(Integer value) {
        int ticks = value == null
                ? SeamlessSleepServerConfig.DEFAULT_FALL_ASLEEP_DELAY_TICKS
                : Mth.clamp(
                        value,
                        SeamlessSleepServerConfig.MIN_FALL_ASLEEP_DELAY_TICKS,
                        SeamlessSleepServerConfig.MAX_FALL_ASLEEP_DELAY_TICKS
                );
        if (ticks == 0) {
            return Component.translatable("config.seamlesssleep.value.instant");
        }
        if (ticks == SeamlessSleepServerConfig.DEFAULT_FALL_ASLEEP_DELAY_TICKS) {
            return Component.translatable("config.seamlesssleep.value.vanilla");
        }

        double seconds = ticks / 20.0D;
        if (Math.abs(seconds - Math.rint(seconds)) < 0.0001D) {
            return Component.literal(String.format(Locale.ROOT, "%.0fs", seconds));
        }
        return Component.literal(String.format(Locale.ROOT, "%.2fs", seconds)
                .replaceAll("0+s$", "s")
                .replace(".s", "s"));
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

    private static final class ConflictAwareOptionDescription implements OptionDescription {
        private final OptionDescription delegate;
        private final Supplier<Boolean> conflictSupplier;
        private final IntSupplier revisionSupplier;
        private int observedRevision;

        private ConflictAwareOptionDescription(OptionDescription delegate,
                                               Supplier<Boolean> conflictSupplier,
                                               IntSupplier revisionSupplier) {
            this.delegate = delegate;
            this.conflictSupplier = conflictSupplier;
            this.revisionSupplier = revisionSupplier;
            this.observedRevision = revisionSupplier.getAsInt();
        }

        @Override
        public Component text() {
            if (!conflictSupplier.get()) {
                return delegate.text();
            }

            Component warning = Component.translatable("config.seamlesssleep.remote.conflict.option_warning")
                    .withStyle(ChatFormatting.YELLOW);
            if (delegate.text().getString().isBlank()) {
                return warning;
            }
            return Component.empty()
                    .append(delegate.text())
                    .append("\n\n")
                    .append(warning);
        }

        @Override
        public CompletableFuture<Optional<ImageRenderer>> image() {
            return delegate.image();
        }

        @Override
        public boolean equals(Object obj) {
            if (this != obj) {
                return false;
            }
            int revision = revisionSupplier.getAsInt();
            boolean unchanged = observedRevision == revision;
            observedRevision = revision;
            return unchanged;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    private static final class ConflictAwareController<T> implements Controller<T> {
        private final Controller<T> delegate;
        private final Supplier<Boolean> conflictSupplier;

        private ConflictAwareController(Controller<T> delegate, Supplier<Boolean> conflictSupplier) {
            this.delegate = delegate;
            this.conflictSupplier = conflictSupplier;
        }

        @Override
        public Option<T> option() {
            return delegate.option();
        }

        @Override
        public Component formatValue() {
            return delegate.formatValue();
        }

        @Override
        public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> widgetDimension) {
            return new ConflictAwareWidget(delegate, delegate.provideWidget(screen, widgetDimension), widgetDimension, conflictSupplier);
        }
    }

    private static final class ConflictAwareWidget extends AbstractWidget {
        private final Controller<?> controller;
        private final AbstractWidget delegate;
        private final Supplier<Boolean> conflictSupplier;

        private ConflictAwareWidget(Controller<?> controller,
                                    AbstractWidget delegate,
                                    Dimension<Integer> dimension,
                                    Supplier<Boolean> conflictSupplier) {
            super(dimension);
            this.controller = controller;
            this.delegate = delegate;
            this.conflictSupplier = conflictSupplier;
        }

        @Override
        public void setDimension(Dimension<Integer> dim) {
            super.setDimension(dim);
            delegate.setDimension(dim);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
            delegate.render(graphics, mouseX, mouseY, tickDelta);
            if (!conflictSupplier.get()) {
                return;
            }

            Dimension<Integer> dimension = getDimension();
            Component name = controller.option().name().copy().withStyle(ChatFormatting.YELLOW);
            if (controller.option().changed()) {
                name = name.copy().withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW);
            }

            int reservedControlWidth = invokeIntNoArgs(delegate, "getControlWidth", textRenderer.width(controller.formatValue()));
            int xPadding = invokeIntNoArgs(delegate, "getXPadding", 5);
            int maxNameWidth = Math.max(20, dimension.width() - reservedControlWidth - xPadding - 7);
            Component shortenedName = Component.literal(GuiUtils.shortenString(name.getString(), textRenderer, maxNameWidth, "..."))
                    .setStyle(name.getStyle());
            int textY = (int) (dimension.y() + dimension.height() / 2f - textRenderer.lineHeight / 2f);
            graphics.drawString(textRenderer, shortenedName, dimension.x() + xPadding, textY, 0xFFFFFF55, true);
        }

        private static int invokeIntNoArgs(Object target, String methodName, int fallback) {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    java.lang.reflect.Method method = type.getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    return (Integer) method.invoke(target);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    type = type.getSuperclass();
                }
            }
            return fallback;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return delegate.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            return delegate.mouseReleased(event);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
            return delegate.mouseDragged(event, deltaX, deltaY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
            return delegate.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            return delegate.keyPressed(event);
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            return delegate.charTyped(event);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return delegate.isMouseOver(mouseX, mouseY);
        }

        @Override
        public boolean canReset() {
            return delegate.canReset();
        }

        @Override
        public void unfocus() {
            delegate.unfocus();
        }

        @Override
        public boolean matchesSearch(String query) {
            return delegate.matchesSearch(query);
        }

        @Override
        public boolean isFocused() {
            return delegate.isFocused();
        }

        @Override
        public void setFocused(boolean focused) {
            delegate.setFocused(focused);
        }

        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent event) {
            if (delegate.nextFocusPath(event) == null) {
                return null;
            }
            return !isFocused() ? ComponentPath.leaf(this) : null;
        }

        @Override
        public NarrationPriority narrationPriority() {
            return delegate.narrationPriority();
        }

        @Override
        public void updateNarration(NarrationElementOutput builder) {
            delegate.updateNarration(builder);
        }
    }

    private static final class RemoteServerConfigScreenSession implements RemoteServerConfigClientState.Listener {
        private final boolean remote;
        private final boolean liveServer;
        private final ServerConfigUiState uiState;
        private final ServerConfigUiState baselineState;
        private final Map<ServerConfigField, Option<?>> options = new EnumMap<>(ServerConfigField.class);
        private final Set<ServerConfigField> dirtyFields = EnumSet.noneOf(ServerConfigField.class);
        private final Set<ServerConfigField> conflictFields = EnumSet.noneOf(ServerConfigField.class);
        private boolean canEditServerConfig;
        private boolean suppressDirtyTracking;
        private Runnable refreshOverlayTextOptions = () -> {};
        private Runnable refreshAccelerationOptions = () -> {};
        private YACLScreen screen;
        private int conflictVisualRevision;

        private RemoteServerConfigScreenSession(boolean remote,
                                                boolean liveServer,
                                                ServerConfigUiState uiState,
                                                boolean canEditServerConfig) {
            this.remote = remote;
            this.liveServer = liveServer;
            this.uiState = uiState;
            this.baselineState = ServerConfigUiState.copyOf(uiState);
            this.canEditServerConfig = canEditServerConfig;
        }

        private boolean remote() {
            return remote;
        }

        private boolean liveServer() {
            return liveServer;
        }

        private ServerConfigUiState uiState() {
            return uiState;
        }

        private boolean canEditServerConfig() {
            return canEditServerConfig;
        }

        private boolean hasConflict(ServerConfigField field) {
            return conflictFields.contains(field);
        }

        private int conflictVisualRevision() {
            return conflictVisualRevision;
        }

        private void bumpConflictVisualRevision() {
            conflictVisualRevision++;
        }

        private void clearDirtyAndConflicts() {
            dirtyFields.clear();
            if (!conflictFields.isEmpty()) {
                conflictFields.clear();
                bumpConflictVisualRevision();
            }
        }

        private void attachScreen(YACLScreen screen) {
            this.screen = screen;
            if (liveServer) {
                RemoteServerConfigClientState.setActiveListener(this);
                refreshAvailability();
            }
        }

        private void registerOption(ServerConfigField field, Option<?> option) {
            options.put(field, option);
        }

        private void setRefreshers(Runnable refreshOverlayTextOptions, Runnable refreshAccelerationOptions) {
            this.refreshOverlayTextOptions = refreshOverlayTextOptions;
            this.refreshAccelerationOptions = refreshAccelerationOptions;
        }

        private void markDirty(ServerConfigField field, boolean changed, boolean pendingDefault) {
            if (!liveServer || suppressDirtyTracking || !canEditServerConfig) {
                return;
            }
            if (changed) {
                dirtyFields.add(field);
            } else {
                dirtyFields.remove(field);
                if (conflictFields.remove(field)) {
                    bumpConflictVisualRevision();
                }
                return;
            }

            if (pendingDefault && conflictFields.remove(field)) {
                bumpConflictVisualRevision();
            }
        }

        private void runWithSuppressedDirtyTracking(Runnable action) {
            boolean previous = suppressDirtyTracking;
            suppressDirtyTracking = true;
            try {
                action.run();
            } finally {
                suppressDirtyTracking = previous;
            }
        }

        private void sendPatch() {
            if (!remote || dirtyFields.isEmpty()) {
                return;
            }

            RemoteServerConfigClientState.sendUpdate(createPatchPayload());
        }

        private void saveLocalPatch(Minecraft client) {
            if (remote || !liveServer || dirtyFields.isEmpty() || client.getSingleplayerServer() == null) {
                return;
            }

            if (ServerConfigMutationService.applyTrustedLocalUpdate(client.getSingleplayerServer(), createPatchPayload())) {
                clearDirtyAndConflicts();
                return;
            }
        }

        private ServerConfigUpdateC2SPayload createPatchPayload() {
            EnumMap<ServerConfigField, String> values = new EnumMap<>(ServerConfigField.class);
            for (ServerConfigField field : dirtyFields) {
                values.put(field, uiState.serializeField(field));
            }
            return new ServerConfigUpdateC2SPayload(
                    RemoteServerConfigClientState.serverConfigRevision(),
                    values
            );
        }

        @Override
        public boolean isActive() {
            return screen != null && (Minecraft.getInstance().screen == screen || screen.popupControllerVisible);
        }

        @Override
        public void onServerConfigSnapshotUpdated() {
            applySnapshotUpdate(true);
        }

        @Override
        public void onServerConfigAccessUpdated() {
            if (!remote) {
                return;
            }

            canEditServerConfig = RemoteServerConfigClientState.canEditServerConfig();
            if (!canEditServerConfig) {
                clearDirtyAndConflicts();
                applySnapshotUpdate(false);
                return;
            }
            refreshAvailability();
        }

        @Override
        public void onServerConfigUpdateResult(ServerConfigUpdateResultS2CPayload payload) {
            if (!remote) {
                return;
            }

            if (payload.success()) {
                clearDirtyAndConflicts();
                applySnapshotUpdate(false);
                return;
            }

            if (payload.status() == ServerConfigUpdateStatus.NO_PERMISSION) {
                canEditServerConfig = false;
                clearDirtyAndConflicts();
                applySnapshotUpdate(false);
                return;
            }
        }

        private void applySnapshotUpdate(boolean preserveDirtyFields) {
            ServerConfigUiState newServerState = ServerConfigUiState.fromSnapshot();
            EnumMap<ServerConfigField, Object> preservedDirtyValues = new EnumMap<>(ServerConfigField.class);
            if (preserveDirtyFields) {
                for (ServerConfigField field : dirtyFields) {
                    Object oldServerValue = baselineState.fieldValue(field);
                    Object newServerValue = newServerState.fieldValue(field);
                    if (!Objects.equals(oldServerValue, newServerValue) && conflictFields.add(field)) {
                        bumpConflictVisualRevision();
                    }
                    preservedDirtyValues.put(field, uiState.fieldValue(field));
                }
            }

            suppressDirtyTracking = true;
            uiState.copyFrom(newServerState);
            baselineState.copyFrom(newServerState);
            for (Map.Entry<ServerConfigField, Object> entry : preservedDirtyValues.entrySet()) {
                uiState.setFieldValue(entry.getKey(), entry.getValue());
            }
            syncOptionsFromState();
            refreshAvailability();
            suppressDirtyTracking = false;
        }

        private void syncOptionsFromState() {
            for (Map.Entry<ServerConfigField, Option<?>> entry : options.entrySet()) {
                setOptionPending(entry.getValue(), uiState.optionValue(entry.getKey()));
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static void setOptionPending(Option option, Object value) {
            if (!Objects.equals(option.pendingValue(), value)) {
                option.requestSet(value);
            }
        }

        private void refreshAvailability() {
            suppressDirtyTracking = true;
            for (Option<?> option : options.values()) {
                option.setAvailable(canEditServerConfig);
            }
            refreshOverlayTextOptions.run();
            refreshAccelerationOptions.run();
            suppressDirtyTracking = false;
        }
    }

    private static final class ServerConfigUiState {
        private int simulationDistance;

        private int sleepWeatherClearChancePercent;
        private double sleepAnimationDurationMultiplier;
        private int fallAsleepDelayTicks;
        private boolean overrideOverlayText;
        private String overlayCustomText;
        private SleepEligibilityMode sleepEligibility;
        private int madeInHeavenChancePercent;
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
        private int boundFallAsleepDelayTicks;
        private boolean boundOverrideOverlayText;
        private String boundOverlayCustomText;
        private SleepEligibilityMode boundSleepEligibility;
        private int boundMadeInHeavenChancePercent;
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
            state.fallAsleepDelayTicks = serverCfg.fallAsleepDelayTicks;
            state.overrideOverlayText = serverCfg.overrideOverlayText;
            state.overlayCustomText = serverCfg.overlayCustomText;
            state.sleepEligibility = serverCfg.sleepEligibility;
            state.madeInHeavenChancePercent = serverCfg.madeInHeavenChancePercent;
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
            state.fallAsleepDelayTicks = SeamlessSleepServerConfigSnapshot.getFallAsleepDelayTicks();
            state.overrideOverlayText = SeamlessSleepServerConfigSnapshot.isOverrideOverlayText();
            state.overlayCustomText = SeamlessSleepServerConfigSnapshot.getOverlayCustomText();
            state.sleepEligibility = SeamlessSleepServerConfigSnapshot.getSleepEligibility();
            state.madeInHeavenChancePercent = SeamlessSleepServerConfigSnapshot.getMadeInHeavenChancePercent();
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

        private static ServerConfigUiState copyOf(ServerConfigUiState source) {
            ServerConfigUiState copy = new ServerConfigUiState(source.simulationDistance);
            copy.copyFrom(source);
            return copy;
        }

        private void copyFrom(ServerConfigUiState source) {
            this.simulationDistance = source.simulationDistance;
            this.sleepWeatherClearChancePercent = source.sleepWeatherClearChancePercent;
            this.sleepAnimationDurationMultiplier = source.sleepAnimationDurationMultiplier;
            this.fallAsleepDelayTicks = source.fallAsleepDelayTicks;
            this.overrideOverlayText = source.overrideOverlayText;
            this.overlayCustomText = source.overlayCustomText;
            this.sleepEligibility = source.sleepEligibility;
            this.madeInHeavenChancePercent = source.madeInHeavenChancePercent;
            this.mode = source.mode;
            this.automaticMode = source.automaticMode;
            this.playersAffected = source.playersAffected;
            this.manualAccelerationRadiusChunks = source.manualAccelerationRadiusChunks;
            this.manualAccelerationSpeedPercent = source.manualAccelerationSpeedPercent;
            this.grassAndFoliageAccelerationEnabled = source.grassAndFoliageAccelerationEnabled;
            this.cropsAndSaplingsAccelerationEnabled = source.cropsAndSaplingsAccelerationEnabled;
            this.kelpAccelerationEnabled = source.kelpAccelerationEnabled;
            this.vanillaOnlyAcceleration = source.vanillaOnlyAcceleration;
            this.processesAccelerationEnabled = source.processesAccelerationEnabled;
            this.processesSpeedPercent = source.processesSpeedPercent;
            this.snapshotBoundValues();
        }

        private void snapshotBoundValues() {
            this.boundSleepWeatherClearChancePercent = this.sleepWeatherClearChancePercent;
            this.boundSleepAnimationDurationMultiplier = this.sleepAnimationDurationMultiplier;
            this.boundFallAsleepDelayTicks = Mth.clamp(
                    this.fallAsleepDelayTicks,
                    SeamlessSleepServerConfig.MIN_FALL_ASLEEP_DELAY_TICKS,
                    SeamlessSleepServerConfig.MAX_FALL_ASLEEP_DELAY_TICKS
            );
            this.boundOverrideOverlayText = this.overrideOverlayText;
            this.boundOverlayCustomText = SeamlessSleepServerConfig.sanitizeOverlayText(this.overlayCustomText);
            this.boundSleepEligibility = selectableSleepEligibility(this.sleepEligibility);
            this.boundMadeInHeavenChancePercent = Mth.clamp(this.madeInHeavenChancePercent, 0, 100);
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

        private Object fieldValue(ServerConfigField field) {
            return switch (field) {
                case SLEEP_WEATHER_CLEAR_CHANCE_PERCENT -> sleepWeatherClearChancePercent;
                case SLEEP_ANIMATION_DURATION_MULTIPLIER -> sleepAnimationDurationMultiplier;
                case FALL_ASLEEP_DELAY_TICKS -> fallAsleepDelayTicks;
                case OVERRIDE_OVERLAY_TEXT -> overrideOverlayText;
                case OVERLAY_CUSTOM_TEXT -> SeamlessSleepServerConfig.sanitizeOverlayText(overlayCustomText);
                case SLEEP_ELIGIBILITY -> sleepEligibility == null ? SleepEligibilityMode.VANILLA : sleepEligibility;
                case MADE_IN_HEAVEN_CHANCE_PERCENT -> madeInHeavenChancePercent;
                case WORLD_SLEEP_ACCELERATION_MODE -> mode == null ? WorldSleepAccelerationMode.AUTOMATIC : mode;
                case WORLD_SLEEP_AUTOMATIC_MODE -> automaticMode == null ? WorldSleepAutomaticMode.AGGRESSIVE : automaticMode;
                case WORLD_SLEEP_ACCELERATION_PLAYERS_AFFECTED -> playersAffected == null
                        ? WorldSleepAccelerationPlayersAffected.ALL_PLAYERS
                        : playersAffected;
                case MANUAL_ACCELERATION_RADIUS_CHUNKS -> resolveManualRadius();
                case MANUAL_ACCELERATION_SPEED_PERCENT -> Mth.clamp(manualAccelerationSpeedPercent, 0, 100);
                case GRASS_AND_FOLIAGE_ACCELERATION_ENABLED -> grassAndFoliageAccelerationEnabled;
                case CROPS_AND_SAPLINGS_ACCELERATION_ENABLED -> cropsAndSaplingsAccelerationEnabled;
                case KELP_ACCELERATION_ENABLED -> kelpAccelerationEnabled;
                case VANILLA_ONLY_ACCELERATION -> vanillaOnlyAcceleration;
                case PROCESSES_ACCELERATION_ENABLED -> processesAccelerationEnabled;
                case PROCESSES_SPEED_PERCENT -> Mth.clamp(processesSpeedPercent, 0, 100);
            };
        }

        private Object optionValue(ServerConfigField field) {
            return switch (field) {
                case SLEEP_ELIGIBILITY -> selectableSleepEligibility(sleepEligibility);
                case MANUAL_ACCELERATION_RADIUS_CHUNKS -> resolveDisplayedAccelerationRadius();
                case MANUAL_ACCELERATION_SPEED_PERCENT -> resolveDisplayedAccelerationSpeedPercent();
                case WORLD_SLEEP_ACCELERATION_PLAYERS_AFFECTED -> resolveDisplayedPlayersAffected();
                default -> fieldValue(field);
            };
        }

        private void setFieldValue(ServerConfigField field, Object value) {
            switch (field) {
                case SLEEP_WEATHER_CLEAR_CHANCE_PERCENT -> sleepWeatherClearChancePercent = (Integer) value;
                case SLEEP_ANIMATION_DURATION_MULTIPLIER -> sleepAnimationDurationMultiplier = (Double) value;
                case FALL_ASLEEP_DELAY_TICKS -> fallAsleepDelayTicks = (Integer) value;
                case OVERRIDE_OVERLAY_TEXT -> overrideOverlayText = (Boolean) value;
                case OVERLAY_CUSTOM_TEXT -> overlayCustomText = (String) value;
                case SLEEP_ELIGIBILITY -> sleepEligibility = (SleepEligibilityMode) value;
                case MADE_IN_HEAVEN_CHANCE_PERCENT -> madeInHeavenChancePercent = (Integer) value;
                case WORLD_SLEEP_ACCELERATION_MODE -> mode = (WorldSleepAccelerationMode) value;
                case WORLD_SLEEP_AUTOMATIC_MODE -> automaticMode = (WorldSleepAutomaticMode) value;
                case WORLD_SLEEP_ACCELERATION_PLAYERS_AFFECTED -> playersAffected = (WorldSleepAccelerationPlayersAffected) value;
                case MANUAL_ACCELERATION_RADIUS_CHUNKS -> manualAccelerationRadiusChunks = (Integer) value;
                case MANUAL_ACCELERATION_SPEED_PERCENT -> manualAccelerationSpeedPercent = (Integer) value;
                case GRASS_AND_FOLIAGE_ACCELERATION_ENABLED -> grassAndFoliageAccelerationEnabled = (Boolean) value;
                case CROPS_AND_SAPLINGS_ACCELERATION_ENABLED -> cropsAndSaplingsAccelerationEnabled = (Boolean) value;
                case KELP_ACCELERATION_ENABLED -> kelpAccelerationEnabled = (Boolean) value;
                case VANILLA_ONLY_ACCELERATION -> vanillaOnlyAcceleration = (Boolean) value;
                case PROCESSES_ACCELERATION_ENABLED -> processesAccelerationEnabled = (Boolean) value;
                case PROCESSES_SPEED_PERCENT -> processesSpeedPercent = (Integer) value;
            }
        }

        private String serializeField(ServerConfigField field) {
            Object value = fieldValue(field);
            return value instanceof Enum<?> enumValue ? enumValue.name() : String.valueOf(value);
        }

        private void applyTo(SeamlessSleepServerConfig serverCfg) {
            serverCfg.sleepWeatherClearChancePercent = sleepWeatherClearChancePercent;
            serverCfg.sleepAnimationDurationMultiplier = sleepAnimationDurationMultiplier;
            serverCfg.fallAsleepDelayTicks = fallAsleepDelayTicks;
            serverCfg.overrideOverlayText = overrideOverlayText;
            serverCfg.overlayCustomText = overlayCustomText;
            serverCfg.sleepEligibility = sleepEligibility == null ? SleepEligibilityMode.VANILLA : sleepEligibility;
            serverCfg.madeInHeavenChancePercent = madeInHeavenChancePercent;
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
