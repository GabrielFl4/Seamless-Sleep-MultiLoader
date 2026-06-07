package net.aqualoco.sec.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationGovernorSnapshot;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationManager;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationModuleStatus;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationStatus;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.SeamlessSleepCommon;
import net.aqualoco.sec.config.ServerConfigMutationService;
import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.SleepEligibilityMode;
import net.aqualoco.sec.config.WorldSleepAccelerationMode;
import net.aqualoco.sec.config.WorldSleepAccelerationPlayersAffected;
import net.aqualoco.sec.config.WorldSleepAutomaticMode;
import net.aqualoco.sec.diagnostic.IntegrityHealth;
import net.aqualoco.sec.diagnostic.IntegrityReport;
import net.aqualoco.sec.diagnostic.SeamlessSleepIntegrityReporter;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.sleep.SleepAnimationMode;
import net.aqualoco.sec.sleep.SleepAnimationPhase;
import net.aqualoco.sec.sleep.SleepAnimationSoundMode;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.aqualoco.sec.sleep.SleepAnimationVisualContext;
import net.aqualoco.sec.sleep.SleepWeatherHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Locale;

public final class SeamlessSleepCommands {

    private SeamlessSleepCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("sleep")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .then(Commands.literal("reload")
                                .executes(SeamlessSleepCommands::reload))
                        .then(Commands.literal("integrity")
                                .executes(SeamlessSleepCommands::integrity))
                        .then(Commands.literal("timelapse")
                                .then(Commands.literal("stop")
                                        .executes(SeamlessSleepCommands::stopTimelapse))
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 30))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 300))
                                                .executes(ctx -> startTimelapse(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(ctx, "days"),
                                                        IntegerArgumentType.getInteger(ctx, "seconds"),
                                                        SleepAnimationSoundMode.DEFAULT
                                                ))
                                                .then(Commands.literal("muted")
                                                        .executes(ctx -> startTimelapse(
                                                                ctx,
                                                                IntegerArgumentType.getInteger(ctx, "days"),
                                                                IntegerArgumentType.getInteger(ctx, "seconds"),
                                                                SleepAnimationSoundMode.MUTED
                                                        )))
                                                .then(Commands.literal("default")
                                                        .executes(ctx -> startTimelapse(
                                                                ctx,
                                                                IntegerArgumentType.getInteger(ctx, "days"),
                                                                IntegerArgumentType.getInteger(ctx, "seconds"),
                                                                SleepAnimationSoundMode.DEFAULT
                                                        )))
                                                .then(Commands.literal("epic")
                                                        .executes(ctx -> startTimelapse(
                                                                ctx,
                                                                IntegerArgumentType.getInteger(ctx, "days"),
                                                                IntegerArgumentType.getInteger(ctx, "seconds"),
                                                                SleepAnimationSoundMode.EPIC
                                                        ))))))
                        .then(Commands.literal("set")
                                .then(Commands.literal("sleepClearsWeather")
                                        .executes(SeamlessSleepCommands::getSleepClearsWeather)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setSleepClearsWeather(
                                                        ctx,
                                                        BoolArgumentType.getBool(ctx, "value")
                                                )))
                                        .then(Commands.argument("chance", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> setSleepWeatherClearChancePercent(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(ctx, "chance")
                                                ))))
                                .then(Commands.literal("sleepDurationMultiplier")
                                        .executes(SeamlessSleepCommands::getSleepDurationMultiplier)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.25D, 8.0D))
                                                .executes(ctx -> setSleepDurationMultiplier(
                                                        ctx,
                                                        DoubleArgumentType.getDouble(ctx, "value")
                                ))))
                                .then(Commands.literal("fallAsleepDelayTicks")
                                        .executes(SeamlessSleepCommands::getFallAsleepDelayTicks)
                                        .then(Commands.argument(
                                                        "value",
                                                        IntegerArgumentType.integer(
                                                                SeamlessSleepServerConfig.MIN_FALL_ASLEEP_DELAY_TICKS,
                                                                SeamlessSleepServerConfig.MAX_FALL_ASLEEP_DELAY_TICKS
                                                        )
                                                )
                                                .executes(ctx -> setFallAsleepDelayTicks(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(ctx, "value")
                                                ))))
                                .then(Commands.literal("textIndicatorOverride")
                                        .executes(SeamlessSleepCommands::getOverrideOverlayText)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setOverrideOverlayText(
                                                        ctx,
                                                        BoolArgumentType.getBool(ctx, "value")
                                                ))))
                                .then(Commands.literal("overrideOverlayText")
                                        .executes(SeamlessSleepCommands::getOverrideOverlayText)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setOverrideOverlayText(
                                                        ctx,
                                                        BoolArgumentType.getBool(ctx, "value")
                                                ))))
                                .then(Commands.literal("textIndicatorCustomText")
                                        .executes(SeamlessSleepCommands::getOverlayCustomText)
                                        .then(Commands.literal("clear")
                                                .executes(SeamlessSleepCommands::clearOverlayCustomText))
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(ctx -> setOverlayCustomText(
                                                        ctx,
                                                        StringArgumentType.getString(ctx, "text")
                                                ))))
                                .then(Commands.literal("overlayCustomText")
                                        .executes(SeamlessSleepCommands::getOverlayCustomText)
                                        .then(Commands.literal("clear")
                                                .executes(SeamlessSleepCommands::clearOverlayCustomText))
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(ctx -> setOverlayCustomText(
                                                        ctx,
                                                        StringArgumentType.getString(ctx, "text")
                                                ))))
                                .then(Commands.literal("sleepEligibility")
                                        .executes(SeamlessSleepCommands::getSleepEligibility)
                                        .then(Commands.literal("insomnia")
                                                .executes(ctx -> setSleepEligibility(ctx, SleepEligibilityMode.INSOMNIA)))
                                        .then(Commands.literal("vanilla")
                                                .executes(ctx -> setSleepEligibility(ctx, SleepEligibilityMode.VANILLA)))
                                        .then(Commands.literal("day_included")
                                                .executes(ctx -> setSleepEligibility(ctx, SleepEligibilityMode.DAY_INCLUDED)))
                                        .then(Commands.literal("dayIncluded")
                                                .executes(ctx -> setSleepEligibility(ctx, SleepEligibilityMode.DAY_INCLUDED))))
                                .then(Commands.literal("madeInHeavenChance")
                                        .executes(SeamlessSleepCommands::getMadeInHeavenChance)
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> setMadeInHeavenChance(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(ctx, "value")
                                                ))))
                                .then(Commands.literal("worldAccelerationMode")
                                        .then(Commands.literal("NONE")
                                                .executes(ctx -> setWorldAccelerationMode(ctx, WorldSleepAccelerationMode.OFF)))
                                        .then(Commands.literal("AUTO")
                                                .executes(ctx -> setWorldAccelerationMode(ctx, WorldSleepAccelerationMode.AUTOMATIC)))
                                        .then(Commands.literal("MANUAL")
                                                .executes(ctx -> setWorldAccelerationMode(ctx, WorldSleepAccelerationMode.MANUAL))))
                                .then(Commands.literal("worldAccelerationAutomaticMode")
                                        .then(Commands.literal("performance")
                                                .executes(ctx -> setWorldAccelerationAutomaticMode(ctx, WorldSleepAutomaticMode.PERFORMANCE)))
                                        .then(Commands.literal("balanced")
                                                .executes(ctx -> setWorldAccelerationAutomaticMode(ctx, WorldSleepAutomaticMode.BALANCED)))
                                        .then(Commands.literal("aggressive")
                                                .executes(ctx -> setWorldAccelerationAutomaticMode(ctx, WorldSleepAutomaticMode.AGGRESSIVE))))
                                .then(Commands.literal("worldAccelerationPlayersAffected")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationPlayersAffected)
                                        .then(Commands.literal("sleepers")
                                                .executes(ctx -> setWorldAccelerationPlayersAffected(
                                                        ctx,
                                                        WorldSleepAccelerationPlayersAffected.SLEEPERS
                                                )))
                                        .then(Commands.literal("allPlayers")
                                                .executes(ctx -> setWorldAccelerationPlayersAffected(
                                                        ctx,
                                                        WorldSleepAccelerationPlayersAffected.ALL_PLAYERS
                                                )))
                                        .then(Commands.literal("all_players")
                                                .executes(ctx -> setWorldAccelerationPlayersAffected(
                                                        ctx,
                                                        WorldSleepAccelerationPlayersAffected.ALL_PLAYERS
                                                )))
                                        .then(Commands.literal("all")
                                                .executes(ctx -> setWorldAccelerationPlayersAffected(
                                                        ctx,
                                                        WorldSleepAccelerationPlayersAffected.ALL_PLAYERS
                                                ))))
                                .then(Commands.literal("worldAccelerationManualRadius")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationManualRadius)
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 32))
                                                .executes(ctx -> setWorldAccelerationManualRadius(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(ctx, "value")
                                                ))))
                                .then(Commands.literal("worldAccelerationManualSpeed")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationManualSpeed)
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> setWorldAccelerationManualSpeed(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(ctx, "value")
                                                ))))
                                .then(Commands.literal("worldAccelerationGrassAndFoliage")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationGrassAndFoliage)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setWorldAccelerationGrassAndFoliage(
                                                        ctx,
                                                        BoolArgumentType.getBool(ctx, "value")
                                                ))))
                                .then(Commands.literal("worldAccelerationCropsAndSaplings")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationCropsAndSaplings)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setWorldAccelerationCropsAndSaplings(
                                                        ctx,
                                                        BoolArgumentType.getBool(ctx, "value")
                                                ))))
                                .then(Commands.literal("worldAccelerationKelp")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationKelp)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setWorldAccelerationKelp(
                                                        ctx,
                                                        BoolArgumentType.getBool(ctx, "value")
                                                ))))
                                .then(Commands.literal("worldAccelerationVanillaOnly")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationVanillaOnly)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setWorldAccelerationVanillaOnly(
                                                        ctx,
                                                        BoolArgumentType.getBool(ctx, "value")
                                                ))))
                                .then(Commands.literal("worldAccelerationProcesses")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationProcesses)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setWorldAccelerationProcesses(
                                                        ctx,
                                                        BoolArgumentType.getBool(ctx, "value")
                                                ))))
                                .then(Commands.literal("worldAccelerationProcessesSpeed")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationProcessesSpeed)
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> setWorldAccelerationProcessesSpeed(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(ctx, "value")
                                                )))))
                        .then(Commands.literal("acceleration")
                                .then(Commands.literal("status")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationStatus))
                                .then(Commands.literal("governor")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationGovernor)))
        );
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        int oldRevision = ServerConfigMutationService.currentRevision();
        SeamlessSleepServerConfigManager.ReloadReport report = SeamlessSleepServerConfigManager.reloadWithReport();
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        ServerConfigMutationService.syncAfterMutation(context.getSource().getServer(), config);
        int newRevision = ServerConfigMutationService.currentRevision();

        Constants.LOG.info(
                "[Seamless Sleep Reload] status={}, path={}, loadedDefaults={}, createdFile={}, savedCanonicalFile={}, revision={} -> {}, activeSleepSessionPresent={}, worldAccelerationStatePreserved={}.",
                report.status(),
                report.path(),
                report.loadedDefaults(),
                report.createdFile(),
                report.savedCanonicalFile(),
                oldRevision,
                newRevision,
                SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION.isActive(),
                true
        );

        String messageKey = switch (report.status()) {
            case SUCCESS -> "command.seamlesssleep.reload.success";
            case CREATED -> "command.seamlesssleep.reload.created";
            case ERROR -> "command.seamlesssleep.reload.error";
        };
        sendCommandFeedback(context, Component.translatable(messageKey));
        return report.status() == SeamlessSleepServerConfigManager.ReloadResult.ERROR ? 0 : 1;
    }

    private static int integrity(CommandContext<CommandSourceStack> context) {
        IntegrityReport report = SeamlessSleepIntegrityReporter.create(context.getSource().getServer());
        String messageKey = switch (report.health()) {
            case STABLE -> "command.seamlesssleep.integrity.result.stable";
            case WARN -> "command.seamlesssleep.integrity.result.warn";
            case FATAL -> "command.seamlesssleep.integrity.result.fatal";
        };
        sendCommandFeedback(context, Component.translatable(messageKey, integrityHealthComponent(report.health())));
        return report.health() == IntegrityHealth.FATAL ? 0 : 1;
    }

    private static MutableComponent seamlessPrefix() {
        return Component.literal("[")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("Seamless Sleep").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static MutableComponent commandFeedback(Component body) {
        return seamlessPrefix().append(body.copy().withStyle(ChatFormatting.WHITE));
    }

    private static void sendCommandFeedback(CommandContext<CommandSourceStack> context, Component body) {
        context.getSource().sendSuccess(() -> commandFeedback(body), false);
    }

    private static MutableComponent integrityHealthComponent(IntegrityHealth health) {
        return switch (health) {
            case STABLE -> Component.translatable("command.seamlesssleep.integrity.health.stable")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            case WARN -> Component.translatable("command.seamlesssleep.integrity.health.warn")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            case FATAL -> Component.translatable("command.seamlesssleep.integrity.health.fatal")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        };
    }

    private static int getSleepClearsWeather(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Weather Clear Chance is set to " + formatWeatherChance(config.sleepWeatherClearChancePercent) + "."),
                false
        );
        return 1;
    }

    private static int setSleepClearsWeather(CommandContext<CommandSourceStack> context, boolean value) {
        return setSleepWeatherClearChancePercent(context, value ? 100 : 0);
    }

    private static int setSleepWeatherClearChancePercent(CommandContext<CommandSourceStack> context, int value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.sleepWeatherClearChancePercent = value;
        return saveAndSyncConfig(
                context,
                config,
                "Weather Clear Chance updated to " + formatWeatherChance(config.sleepWeatherClearChancePercent) + "."
        );
    }

    private static int getSleepDurationMultiplier(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.seamlesssleep.set.sleep_duration.current",
                        formatMultiplier(config.sleepAnimationDurationMultiplier),
                        "0.25x",
                        "8.00x"
                ),
                false
        );
        return 1;
    }

    private static int setSleepDurationMultiplier(CommandContext<CommandSourceStack> context, double value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.sleepAnimationDurationMultiplier = value;
        return saveAndSyncConfig(
                context,
                config,
                Component.translatable(
                        "command.seamlesssleep.set.sleep_duration.updated",
                        formatMultiplier(config.sleepAnimationDurationMultiplier),
                        "0.25x",
                        "8.00x"
                )
        );
    }

    private static int getFallAsleepDelayTicks(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Fall asleep delay is " + formatFallAsleepDelay(config.fallAsleepDelayTicks) + "."),
                false
        );
        return 1;
    }

    private static int setFallAsleepDelayTicks(CommandContext<CommandSourceStack> context, int value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.fallAsleepDelayTicks = value;
        return saveAndSyncConfig(
                context,
                config,
                "Fall asleep delay set to " + formatFallAsleepDelay(config.fallAsleepDelayTicks) + "."
        );
    }

    private static int getOverrideOverlayText(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Text Indicator Override is " + formatOnOff(config.overrideOverlayText) + "."),
                false
        );
        return 1;
    }

    private static int setOverrideOverlayText(CommandContext<CommandSourceStack> context, boolean value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.overrideOverlayText = value;
        return saveAndSyncConfig(
                context,
                config,
                "Text Indicator Override updated to " + formatOnOff(value) + "."
        );
    }

    private static int getOverlayCustomText(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Text Indicator Custom Text is \"" + config.overlayCustomText + "\"."),
                false
        );
        return 1;
    }

    private static int setOverlayCustomText(CommandContext<CommandSourceStack> context, String value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.overlayCustomText = SeamlessSleepServerConfig.sanitizeOverlayText(value);
        return saveAndSyncConfig(
                context,
                config,
                "Text Indicator Custom Text updated to \"" + config.overlayCustomText + "\"."
        );
    }

    private static int clearOverlayCustomText(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.overrideOverlayText = false;
        config.overlayCustomText = SeamlessSleepServerConfig.DEFAULT_OVERLAY_CUSTOM_TEXT;
        return saveAndSyncConfig(
                context,
                config,
                "Text Indicator Custom Text cleared. Translatable text indicator text is active."
        );
    }

    private static int getSleepEligibility(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Sleep Eligibility is " + formatSleepEligibility(config.sleepEligibility) + "."),
                false
        );
        return 1;
    }

    private static int setSleepEligibility(CommandContext<CommandSourceStack> context, SleepEligibilityMode mode) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.sleepEligibility = mode == null ? SleepEligibilityMode.VANILLA : mode;
        return saveAndSyncConfig(
                context,
                config,
                "Sleep Eligibility updated to " + formatSleepEligibility(config.sleepEligibility) + "."
        );
    }

    private static int getMadeInHeavenChance(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Made In Heaven Chance is " + config.madeInHeavenChancePercent + "%."),
                false
        );
        return 1;
    }

    private static int setMadeInHeavenChance(CommandContext<CommandSourceStack> context, int value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.madeInHeavenChancePercent = value;
        return saveAndSyncConfig(
                context,
                config,
                "Made In Heaven Chance updated to " + config.madeInHeavenChancePercent + "%."
        );
    }

    private static int startTimelapse(CommandContext<CommandSourceStack> context,
                                      int days,
                                      int seconds,
                                      SleepAnimationSoundMode soundMode) {
        ServerLevel overworld = context.getSource().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            context.getSource().sendFailure(Component.translatable("command.seamlesssleep.timelapse.error.overworld_unavailable"));
            return 0;
        }

        SleepAnimationState state = SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
        if (state.isActive()) {
            context.getSource().sendFailure(Component.translatable("command.seamlesssleep.timelapse.error.animation_active"));
            return 0;
        }

        long currentTime = overworld.getDayTime();
        long targetTime = currentTime + days * 24000L;
        int durationTicks = seconds * 20;
        SleepAnimationSoundMode resolvedSoundMode = SleepAnimationSoundMode.canonical(
                soundMode == null ? SleepAnimationSoundMode.DEFAULT : soundMode
        );
        if (!state.startExplicit(
                overworld,
                currentTime,
                targetTime,
                durationTicks,
                SleepAnimationMode.COMMAND_TIMELAPSE,
                SleepAnimationVisualContext.MADE_IN_HEAVEN,
                resolvedSoundMode
        )) {
            context.getSource().sendFailure(Component.translatable("command.seamlesssleep.timelapse.error.start_failed"));
            return 0;
        }

        WorldSleepAccelerationManager.refreshForLevelTick(overworld);
        SleepWeatherHelper.clearWeatherCycle(overworld);
        SleepAnimationNetworking.sendStart(overworld, state);
        return 1;
    }

    private static int stopTimelapse(CommandContext<CommandSourceStack> context) {
        ServerLevel overworld = context.getSource().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            context.getSource().sendFailure(Component.translatable("command.seamlesssleep.timelapse.error.overworld_unavailable"));
            return 0;
        }

        SleepAnimationState state = SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
        if (!state.isActive() || state.getMode() != SleepAnimationMode.COMMAND_TIMELAPSE) {
            return 0;
        }

        if (state.getPhase() == SleepAnimationPhase.BRAKING) {
            return 1;
        }

        if (!state.startCommandTimelapseStopBraking(overworld)) {
            context.getSource().sendFailure(Component.translatable("command.seamlesssleep.timelapse.stop.failed"));
            return 0;
        }

        WorldSleepAccelerationManager.refreshForLevelTick(overworld);
        SleepAnimationNetworking.sendStart(overworld, state);
        return 1;
    }

    private static int getWorldAccelerationStatus(CommandContext<CommandSourceStack> context) {
        ServerLevel overworld = context.getSource().getServer().overworld();
        if (overworld == null) {
            context.getSource().sendFailure(Component.literal("World acceleration status is unavailable right now."));
            return 0;
        }

        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        WorldSleepAccelerationStatus status = WorldSleepAccelerationManager.getStatus(overworld);
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "World Acceleration -> mode "
                                + formatAccelerationMode(config.worldSleepAcceleration.mode)
                                + ", automatic mode "
                                + config.worldSleepAcceleration.automaticMode.name()
                                + ", players affected "
                                + config.worldSleepAcceleration.resolveEffectivePlayersAffected().name()
                                + "."
                ),
                false
        );

        if (!status.isActive()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("World acceleration is currently inactive."),
                    false
            );
            return 1;
        }

        context.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        Locale.ROOT,
                        "World rate %.2fx, avg %.2f MSPT, p95 %.2f MSPT, simulation distance %d, tracked players %d, governor %s.",
                        status.getWorldSleepRate(),
                        status.getAverageMspt(),
                        status.getP95Mspt(),
                        status.getSimulationDistance(),
                        status.getActivePlayerCount(),
                        status.getGovernorAction().name()
                )),
                false
        );
        context.getSource().sendSuccess(() -> formatNatureLine(status.getNature()), false);
        context.getSource().sendSuccess(() -> formatProcessLine(status.getProcess()), false);
        return 1;
    }

    private static int getWorldAccelerationGovernor(CommandContext<CommandSourceStack> context) {
        ServerLevel overworld = context.getSource().getServer().overworld();
        if (overworld == null) {
            context.getSource().sendFailure(Component.literal("World acceleration status is unavailable right now."));
            return 0;
        }

        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        WorldSleepAccelerationStatus status = WorldSleepAccelerationManager.getDiagnosticStatus(overworld);
        WorldSleepAccelerationGovernorSnapshot governor = status.getGovernorSnapshot();
        int randomTickSpeed = Math.max(0, overworld.getGameRules().get(GameRules.RANDOM_TICK_SPEED));

        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Governor -> mode "
                                + formatAccelerationMode(config.worldSleepAcceleration.mode)
                                + ", automatic mode "
                                + config.worldSleepAcceleration.automaticMode.name()
                                + ", players affected "
                                + config.worldSleepAcceleration.resolveEffectivePlayersAffected().name()
                                + "."
                ),
                false
        );

        if (config.worldSleepAcceleration.mode != WorldSleepAccelerationMode.AUTOMATIC) {
            context.getSource().sendSuccess(
                    () -> Component.literal("Governor bypassed because the current mode is " + formatAccelerationMode(config.worldSleepAcceleration.mode) + "."),
                    false
            );
            context.getSource().sendSuccess(() -> formatNatureGovernorLine(status.getNature(), randomTickSpeed), false);
            context.getSource().sendSuccess(() -> formatProcessGovernorLine(status.getProcess()), false);
            return 1;
        }

        if (!governor.isActive()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("Governor has no live data right now."),
                    false
            );
            return 1;
        }

        context.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        Locale.ROOT,
                        "Inputs -> world rate %.2fx, avg %.2f MSPT, p95 %.2f MSPT, players affected %s, tracked players %d, simulation distance %d, randomTickSpeed %d.",
                        status.getWorldSleepRate(),
                        status.getAverageMspt(),
                        status.getP95Mspt(),
                        status.getPlayersAffected().name(),
                        status.getActivePlayerCount(),
                        status.getSimulationDistance(),
                        randomTickSpeed
                )),
                false
        );
        context.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        Locale.ROOT,
                        "Pressure -> avg %s, p95 %s, health %s, player risk %s, sim risk %s, area risk %s, rate risk %s, factor %.2fx, raw %s, smoothed %s.",
                        formatPercent(governor.getAverageMsptPressure()),
                        formatPercent(governor.getP95MsptPressure()),
                        formatPercent(governor.getHealthPressure()),
                        formatSignedPercent(governor.getActivePlayerRiskBonus()),
                        formatSignedPercent(governor.getSimulationDistanceRiskBonus()),
                        formatSignedPercent(governor.getCandidateAreaRiskBonus()),
                        formatSignedPercent(governor.getWorldSleepRateRiskBonus()),
                        governor.getRiskFactor(),
                        formatPercent(governor.getRawPressure()),
                        formatPercent(governor.getSmoothedPressure())
                )),
                false
        );
        context.getSource().sendSuccess(() -> formatNatureGovernorLine(status.getNature(), randomTickSpeed), false);
        context.getSource().sendSuccess(() -> formatProcessGovernorLine(status.getProcess()), false);
        return 1;
    }

    private static Component formatNatureLine(WorldSleepAccelerationModuleStatus status) {
        if (!status.isActive()) {
            return Component.literal("Nature -> inactive.");
        }
        return Component.literal(String.format(
                Locale.ROOT,
                "Nature -> radius %d/%d, speed %d%%/%d%%, extra attempts/section %.2f, covered chunks %d.",
                status.getEffectiveRadiusChunks(),
                status.getConfiguredRadiusChunks(),
                status.getEffectiveSpeedPercent(),
                status.getConfiguredSpeedPercent(),
                status.getExtraRandomTickAttemptsPerSection(),
                status.getCoveredChunkCount()
        ));
    }

    private static Component formatNatureGovernorLine(WorldSleepAccelerationModuleStatus status, int randomTickSpeed) {
        if (!status.isActive()) {
            return Component.literal("Nature -> inactive.");
        }

        double totalAttempts = randomTickSpeed + status.getExtraRandomTickAttemptsPerSection();
        return Component.literal(String.format(
                Locale.ROOT,
                "Nature -> action %s, radius %d/%d, speed %d%%/%d%%, extra/section %.2f (%d + frac %.2f), total/section %.2f, chunks %d.",
                status.getGovernorAction().name(),
                status.getEffectiveRadiusChunks(),
                status.getConfiguredRadiusChunks(),
                status.getEffectiveSpeedPercent(),
                status.getConfiguredSpeedPercent(),
                status.getExtraRandomTickAttemptsPerSection(),
                status.getExtraRandomTickWholeAttemptsPerSection(),
                status.getExtraRandomTickFractionalAttemptsPerSection(),
                totalAttempts,
                status.getCoveredChunkCount()
        ));
    }

    private static Component formatProcessLine(WorldSleepAccelerationModuleStatus status) {
        if (status.isTemporarilySuppressed()) {
            return Component.literal("Processes -> temporarily suppressed by the governor.");
        }
        if (!status.isActive()) {
            return Component.literal("Processes -> inactive.");
        }
        return Component.literal(String.format(
                Locale.ROOT,
                "Processes -> radius %d/%d, speed %d%%, multiplier %.2fx, covered chunks %d.",
                status.getEffectiveRadiusChunks(),
                status.getConfiguredRadiusChunks(),
                status.getConfiguredSpeedPercent(),
                status.getEffectiveTickMultiplier(),
                status.getCoveredChunkCount()
        ));
    }

    private static Component formatProcessGovernorLine(WorldSleepAccelerationModuleStatus status) {
        if (status.isTemporarilySuppressed()) {
            return Component.literal("Processes -> temporarily suppressed by the governor.");
        }
        if (!status.isActive()) {
            return Component.literal("Processes -> inactive.");
        }

        return Component.literal(String.format(
                Locale.ROOT,
                "Processes -> action %s, radius %d/%d, speed %d%%, multiplier %.2fx, chunks %d.",
                status.getGovernorAction().name(),
                status.getEffectiveRadiusChunks(),
                status.getConfiguredRadiusChunks(),
                status.getConfiguredSpeedPercent(),
                status.getEffectiveTickMultiplier(),
                status.getCoveredChunkCount()
        ));
    }

    private static int getWorldAccelerationManualRadius(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        int simulationDistance = resolveCurrentSimulationDistance(context);
        int resolvedRadius = config.worldSleepAcceleration.resolveManualRadiusChunks(simulationDistance);
        context.getSource().sendSuccess(
                () -> Component.literal("Manual Acceleration Radius is " + resolvedRadius + " chunk(s)."),
                false
        );
        return 1;
    }

    private static int getWorldAccelerationPlayersAffected(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        WorldSleepAccelerationPlayersAffected configured = config.worldSleepAcceleration.playersAffected;
        WorldSleepAccelerationPlayersAffected effective = config.worldSleepAcceleration.resolveEffectivePlayersAffected();
        if (config.worldSleepAcceleration.mode == WorldSleepAccelerationMode.AUTOMATIC) {
            context.getSource().sendSuccess(
                    () -> Component.literal(
                            "Players Affected is "
                                    + effective.name()
                                    + " because Automatic Mode "
                                    + config.worldSleepAcceleration.automaticMode.name()
                                    + " currently forces it. Accepted MANUAL values: SLEEPERS, ALL_PLAYERS."
                    ),
                    false
            );
            context.getSource().sendSuccess(
                    () -> Component.literal("Stored MANUAL preference is " + configured.name() + "."),
                    false
            );
            return 1;
        }

        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Players Affected is " + effective.name() + ". Accepted values: SLEEPERS, ALL_PLAYERS."
                ),
                false
        );
        return 1;
    }

    private static int setWorldAccelerationPlayersAffected(CommandContext<CommandSourceStack> context,
                                                           WorldSleepAccelerationPlayersAffected playersAffected) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.worldSleepAcceleration.playersAffected = playersAffected;

        if (config.worldSleepAcceleration.mode == WorldSleepAccelerationMode.AUTOMATIC) {
            WorldSleepAccelerationPlayersAffected forcedValue = config.worldSleepAcceleration.resolveEffectivePlayersAffected();
            return saveAndSyncAcceleration(
                    context,
                    config,
                    "Players Affected preference updated to "
                            + playersAffected.name()
                            + ". Automatic Mode "
                            + config.worldSleepAcceleration.automaticMode.name()
                            + " currently forces "
                            + forcedValue.name()
                            + "."
            );
        }

        return saveAndSyncAcceleration(context, config, "Players Affected updated to " + playersAffected.name() + ".");
    }

    private static int setWorldAccelerationManualRadius(CommandContext<CommandSourceStack> context, int value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        int clampedValue = Math.max(1, Math.min(value, resolveCurrentSimulationDistance(context)));
        config.worldSleepAcceleration.manualAccelerationRadiusChunks = clampedValue;
        return saveAndSyncAcceleration(context, config, "Manual Acceleration Radius updated to " + clampedValue + " chunk(s).");
    }

    private static int getWorldAccelerationManualSpeed(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Manual Acceleration Speed is " + config.worldSleepAcceleration.manualAccelerationSpeedPercent + "%."),
                false
        );
        return 1;
    }

    private static int setWorldAccelerationManualSpeed(CommandContext<CommandSourceStack> context, int value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.worldSleepAcceleration.manualAccelerationSpeedPercent = value;
        return saveAndSyncAcceleration(context, config, "Manual Acceleration Speed updated to " + value + "%.");
    }

    private static int getWorldAccelerationGrassAndFoliage(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Grass & Foliage Acceleration is " + formatOnOff(config.worldSleepAcceleration.grassAndFoliageAccelerationEnabled) + "."),
                false
        );
        return 1;
    }

    private static int setWorldAccelerationGrassAndFoliage(CommandContext<CommandSourceStack> context, boolean value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.worldSleepAcceleration.grassAndFoliageAccelerationEnabled = value;
        return saveAndSyncAcceleration(context, config, "Grass & Foliage Acceleration updated to " + formatOnOff(value) + ".");
    }

    private static int getWorldAccelerationCropsAndSaplings(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Crops & Saplings Acceleration is " + formatOnOff(config.worldSleepAcceleration.cropsAndSaplingsAccelerationEnabled) + "."),
                false
        );
        return 1;
    }

    private static int setWorldAccelerationCropsAndSaplings(CommandContext<CommandSourceStack> context, boolean value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.worldSleepAcceleration.cropsAndSaplingsAccelerationEnabled = value;
        return saveAndSyncAcceleration(context, config, "Crops & Saplings Acceleration updated to " + formatOnOff(value) + ".");
    }

    private static int getWorldAccelerationKelp(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Kelp. is " + formatOnOff(config.worldSleepAcceleration.kelpAccelerationEnabled) + "."),
                false
        );
        return 1;
    }

    private static int setWorldAccelerationKelp(CommandContext<CommandSourceStack> context, boolean value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.worldSleepAcceleration.kelpAccelerationEnabled = value;
        return saveAndSyncAcceleration(context, config, "Kelp. updated to " + formatOnOff(value) + ".");
    }

    private static int getWorldAccelerationVanillaOnly(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Vanilla Only is " + formatOnOff(config.worldSleepAcceleration.vanillaOnlyAcceleration) + "."),
                false
        );
        return 1;
    }

    private static int setWorldAccelerationVanillaOnly(CommandContext<CommandSourceStack> context, boolean value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.worldSleepAcceleration.vanillaOnlyAcceleration = value;
        return saveAndSyncAcceleration(context, config, "Vanilla Only updated to " + formatOnOff(value) + ".");
    }

    private static int getWorldAccelerationProcesses(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Processes Acceleration is " + formatOnOff(config.worldSleepAcceleration.processesAccelerationEnabled) + "."),
                false
        );
        return 1;
    }

    private static int setWorldAccelerationProcesses(CommandContext<CommandSourceStack> context, boolean value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.worldSleepAcceleration.processesAccelerationEnabled = value;
        return saveAndSyncAcceleration(context, config, "Processes Acceleration updated to " + formatOnOff(value) + ".");
    }

    private static int getWorldAccelerationProcessesSpeed(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Processes Speed is " + config.worldSleepAcceleration.processesSpeedPercent + "%."),
                false
        );
        return 1;
    }

    private static int setWorldAccelerationProcessesSpeed(CommandContext<CommandSourceStack> context, int value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.worldSleepAcceleration.processesSpeedPercent = value;
        return saveAndSyncAcceleration(context, config, "Processes Speed updated to " + value + "%.");
    }

    private static int setWorldAccelerationMode(CommandContext<CommandSourceStack> context, WorldSleepAccelerationMode mode) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.worldSleepAcceleration.mode = mode;
        if (mode == WorldSleepAccelerationMode.AUTOMATIC) {
            return saveAndSyncAcceleration(
                    context,
                    config,
                    "World Acceleration Mode updated to AUTO. Players Affected is now forced to "
                            + config.worldSleepAcceleration.resolveEffectivePlayersAffected().name()
                            + "."
            );
        }
        return saveAndSyncAcceleration(context, config, "World Acceleration Mode updated to " + formatAccelerationMode(mode) + ".");
    }

    private static int setWorldAccelerationAutomaticMode(CommandContext<CommandSourceStack> context, WorldSleepAutomaticMode automaticMode) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.worldSleepAcceleration.automaticMode = automaticMode;
        String message = "Automatic Mode updated to " + automaticMode.name() + ".";
        if (config.worldSleepAcceleration.mode == WorldSleepAccelerationMode.AUTOMATIC) {
            message += " Players Affected is now forced to " + config.worldSleepAcceleration.resolveEffectivePlayersAffected().name() + ".";
        }
        return saveAndSyncAcceleration(context, config, message);
    }

    private static int saveAndSyncAcceleration(CommandContext<CommandSourceStack> context,
                                               SeamlessSleepServerConfig config,
                                               String message) {
        return saveAndSyncConfig(context, config, message);
    }

    private static int saveAndSyncConfig(CommandContext<CommandSourceStack> context,
                                         SeamlessSleepServerConfig config,
                                         String message) {
        return saveAndSyncConfig(context, config, Component.literal(message));
    }

    private static int saveAndSyncConfig(CommandContext<CommandSourceStack> context,
                                         SeamlessSleepServerConfig config,
                                         Component message) {
        config.clamp();
        ServerConfigMutationService.saveAndSync(context.getSource().getServer(), config);
        context.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100.0D);
    }

    private static String formatSignedPercent(double value) {
        return (value >= 0.0D ? "+" : "") + formatPercent(value);
    }

    private static String formatMultiplier(double value) {
        return String.format(Locale.ROOT, "%.2fx", value);
    }

    private static String formatFallAsleepDelay(int ticks) {
        int clamped = SeamlessSleepServerConfig.clampInt(
                ticks,
                SeamlessSleepServerConfig.MIN_FALL_ASLEEP_DELAY_TICKS,
                SeamlessSleepServerConfig.MAX_FALL_ASLEEP_DELAY_TICKS
        );
        if (clamped == 0) {
            return "0 ticks (Instant)";
        }
        if (clamped == SeamlessSleepServerConfig.DEFAULT_FALL_ASLEEP_DELAY_TICKS) {
            return clamped + " ticks (Vanilla, " + formatSeconds(clamped) + ")";
        }
        return clamped + " ticks (" + formatSeconds(clamped) + ")";
    }

    private static String formatSeconds(int ticks) {
        double seconds = ticks / 20.0D;
        if (Math.abs(seconds - Math.rint(seconds)) < 0.0001D) {
            return String.format(Locale.ROOT, "%.0fs", seconds);
        }
        return String.format(Locale.ROOT, "%.2fs", seconds).replaceAll("0+s$", "s").replace(".s", "s");
    }

    private static String formatWeatherChance(int value) {
        if (value <= 0) {
            return "NEVER";
        }
        if (value >= 100) {
            return "ALWAYS";
        }
        return value + "%";
    }

    private static String formatOnOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String formatAccelerationMode(WorldSleepAccelerationMode mode) {
        WorldSleepAccelerationMode resolved = mode == null ? WorldSleepAccelerationMode.AUTOMATIC : mode;
        return switch (resolved) {
            case OFF -> "NONE";
            case AUTOMATIC -> "AUTO";
            case MANUAL -> "MANUAL";
        };
    }

    private static String formatSleepEligibility(SleepEligibilityMode mode) {
        SleepEligibilityMode resolved = mode == null ? SleepEligibilityMode.VANILLA : mode;
        return switch (resolved) {
            case INSOMNIA -> "INSOMNIA";
            case VANILLA -> "VANILLA";
            case DAY_INCLUDED -> "DAY_INCLUDED";
            case ALWAYS -> "ALWAYS";
        };
    }

    private static int resolveCurrentSimulationDistance(CommandContext<CommandSourceStack> context) {
        return Math.max(1, context.getSource().getServer().getPlayerList().getSimulationDistance());
    }
}
