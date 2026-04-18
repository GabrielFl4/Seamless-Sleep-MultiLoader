package net.aqualoco.sec.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationManager;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationModuleStatus;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationStatus;
import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.WorldSleepAccelerationMode;
import net.aqualoco.sec.config.WorldSleepAccelerationPreset;
import net.aqualoco.sec.network.ServerConfigSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;

import java.util.Locale;

// Registers admin commands to reload and tweak server sleep settings in-game.
public final class SeamlessSleepCommands {

    private SeamlessSleepCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("sleep")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .then(Commands.literal("reload")
                                .executes(SeamlessSleepCommands::reload))
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
                                .then(Commands.literal("worldAccelerationMode")
                                        .then(Commands.literal("off")
                                                .executes(ctx -> setWorldAccelerationMode(ctx, WorldSleepAccelerationMode.OFF)))
                                        .then(Commands.literal("auto")
                                                .executes(ctx -> setWorldAccelerationMode(ctx, WorldSleepAccelerationMode.AUTO)))
                                        .then(Commands.literal("custom")
                                                .executes(ctx -> setWorldAccelerationMode(ctx, WorldSleepAccelerationMode.CUSTOM))))
                                .then(Commands.literal("worldAccelerationPreset")
                                        .then(Commands.literal("eco")
                                                .executes(ctx -> setWorldAccelerationPreset(ctx, WorldSleepAccelerationPreset.ECO)))
                                        .then(Commands.literal("balanced")
                                                .executes(ctx -> setWorldAccelerationPreset(ctx, WorldSleepAccelerationPreset.BALANCED)))
                                        .then(Commands.literal("aggressive")
                                                .executes(ctx -> setWorldAccelerationPreset(ctx, WorldSleepAccelerationPreset.AGGRESSIVE)))
                                        .then(Commands.literal("custom")
                                                .executes(ctx -> setWorldAccelerationPreset(ctx, WorldSleepAccelerationPreset.CUSTOM))))
                                .then(Commands.literal("worldAccelerationRandomTick")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationRandomTick)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setWorldAccelerationRandomTick(
                                                        ctx,
                                                        BoolArgumentType.getBool(ctx, "value")
                                                ))))
                                .then(Commands.literal("worldAccelerationProcess")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationProcess)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setWorldAccelerationProcess(
                                                        ctx,
                                                        BoolArgumentType.getBool(ctx, "value")
                                                )))))
                        .then(Commands.literal("acceleration")
                                .then(Commands.literal("status")
                                        .executes(SeamlessSleepCommands::getWorldAccelerationStatus)))
        );
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfigManager.ReloadResult result =
                SeamlessSleepServerConfigManager.reloadWithStatus();
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        ServerConfigSync.sendToAll(context.getSource().getServer(), config);

        Component message = switch (result) {
            case SUCCESS -> Component.translatable("command.seamlesssleep.reload.success");
            case CREATED -> Component.translatable("command.seamlesssleep.reload.created");
            case ERROR -> Component.translatable("command.seamlesssleep.reload.error");
        };
        context.getSource().sendSuccess(() -> message, true);
        context.getSource().sendSuccess(
                () -> Component.translatable("command.seamlesssleep.reload.synced"),
                true
        );
        return result == SeamlessSleepServerConfigManager.ReloadResult.ERROR ? 0 : 1;
    }

    private static int getSleepClearsWeather(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.seamlesssleep.set.sleep_clears_weather.current",
                        formatWeatherChance(config.sleepWeatherClearChancePercent)
                ),
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
        config.clamp();
        SeamlessSleepServerConfigManager.save();
        ServerConfigSync.sendToAll(context.getSource().getServer(), config);

        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.seamlesssleep.set.sleep_clears_weather.updated",
                        formatWeatherChance(config.sleepWeatherClearChancePercent)
                ),
                true
        );
        return 1;
    }

    private static int getSleepDurationMultiplier(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.seamlesssleep.set.sleep_duration.current",
                        formatDecimal(config.sleepAnimationDurationMultiplier),
                        "0.25",
                        "8.00"
                ),
                false
        );
        return 1;
    }

    private static int setSleepDurationMultiplier(CommandContext<CommandSourceStack> context, double value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.sleepAnimationDurationMultiplier = value;
        config.clamp();
        SeamlessSleepServerConfigManager.save();
        ServerConfigSync.sendToAll(context.getSource().getServer(), config);

        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.seamlesssleep.set.sleep_duration.updated",
                        formatDecimal(config.sleepAnimationDurationMultiplier),
                        "0.25",
                        "8.00"
                ),
                true
        );
        return 1;
    }

    private static int getWorldAccelerationStatus(CommandContext<CommandSourceStack> context) {
        ServerLevel overworld = context.getSource().getServer().overworld();
        if (overworld == null) {
            context.getSource().sendFailure(Component.translatable("command.seamlesssleep.acceleration.status.unavailable"));
            return 0;
        }

        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        WorldSleepAccelerationStatus status = WorldSleepAccelerationManager.getStatus(overworld);
        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.seamlesssleep.acceleration.status.header",
                        config.worldSleepAcceleration.mode.name(),
                        config.worldSleepAcceleration.preset.name()
                ),
                false
        );

        if (!status.isActive()) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("command.seamlesssleep.acceleration.status.inactive"),
                    false
            );
            return 1;
        }

        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.seamlesssleep.acceleration.status.runtime",
                        formatDecimal(status.getWorldSleepRate()),
                        formatDecimal(status.getAverageMspt()),
                        formatDecimal(status.getP95Mspt()),
                        status.getGovernorAction().name()
                ),
                false
        );
        context.getSource().sendSuccess(
                () -> formatNatureLine(status.getNature()),
                false
        );
        context.getSource().sendSuccess(
                () -> formatProcessLine(status.getProcess()),
                false
        );
        return 1;
    }

    private static Component formatNatureLine(WorldSleepAccelerationModuleStatus status) {
        if (!status.isActive()) {
            return Component.translatable("command.seamlesssleep.acceleration.status.nature.inactive");
        }
        return Component.translatable(
                "command.seamlesssleep.acceleration.status.nature.active",
                status.getEffectiveRadiusChunks(),
                status.getBaseRadiusChunks(),
                formatPercent(status.getEffectiveRateFraction()),
                formatDecimal(status.getExtraRandomTickAttemptsPerSection()),
                status.getCoveredChunkCount()
        );
    }

    private static Component formatProcessLine(WorldSleepAccelerationModuleStatus status) {
        if (!status.isActive()) {
            return Component.translatable("command.seamlesssleep.acceleration.status.process.inactive");
        }
        return Component.translatable(
                "command.seamlesssleep.acceleration.status.process.active",
                status.getEffectiveRadiusChunks(),
                status.getBaseRadiusChunks(),
                formatPercent(status.getEffectiveRateFraction()),
                formatDecimal(status.getEffectiveTickMultiplier()),
                status.getCoveredChunkCount()
        );
    }

    private static int getWorldAccelerationRandomTick(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.seamlesssleep.acceleration.random_tick.current",
                        Boolean.toString(config.worldSleepAcceleration.randomTickAccelerationEnabled)
                ),
                false
        );
        return 1;
    }

    private static int setWorldAccelerationRandomTick(CommandContext<CommandSourceStack> context, boolean value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.worldSleepAcceleration.randomTickAccelerationEnabled = value;
        config.worldSleepAcceleration.markPresetCustom();
        return saveAndSyncAcceleration(context, config, "command.seamlesssleep.acceleration.random_tick.updated", Boolean.toString(value));
    }

    private static int getWorldAccelerationProcess(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.seamlesssleep.acceleration.process.current",
                        Boolean.toString(config.worldSleepAcceleration.processAccelerationEnabled)
                ),
                false
        );
        return 1;
    }

    private static int setWorldAccelerationProcess(CommandContext<CommandSourceStack> context, boolean value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.worldSleepAcceleration.processAccelerationEnabled = value;
        config.worldSleepAcceleration.markPresetCustom();
        return saveAndSyncAcceleration(context, config, "command.seamlesssleep.acceleration.process.updated", Boolean.toString(value));
    }

    private static int setWorldAccelerationMode(CommandContext<CommandSourceStack> context, WorldSleepAccelerationMode mode) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.worldSleepAcceleration.mode = mode;
        return saveAndSyncAcceleration(context, config, "command.seamlesssleep.acceleration.mode.updated", mode.name());
    }

    private static int setWorldAccelerationPreset(CommandContext<CommandSourceStack> context, WorldSleepAccelerationPreset preset) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        if (preset == WorldSleepAccelerationPreset.CUSTOM) {
            config.worldSleepAcceleration.markPresetCustom();
        } else {
            config.worldSleepAcceleration.applyPreset(preset);
        }
        return saveAndSyncAcceleration(context, config, "command.seamlesssleep.acceleration.preset.updated", config.worldSleepAcceleration.preset.name());
    }

    private static int saveAndSyncAcceleration(CommandContext<CommandSourceStack> context,
                                               SeamlessSleepServerConfig config,
                                               String translationKey,
                                               String value) {
        config.clamp();
        SeamlessSleepServerConfigManager.save();
        ServerConfigSync.sendToAll(context.getSource().getServer(), config);
        context.getSource().sendSuccess(
                () -> Component.translatable(translationKey, value),
                true
        );
        return 1;
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100.0D);
    }

    private static String formatWeatherChance(int value) {
        if (value <= 0) {
            return "false";
        }
        if (value >= 100) {
            return "true";
        }
        return value + "%";
    }
}
