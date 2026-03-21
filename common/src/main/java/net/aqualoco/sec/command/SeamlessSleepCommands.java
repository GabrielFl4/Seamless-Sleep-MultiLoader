package net.aqualoco.sec.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.ServerConfigSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

// Registers admin commands to reload and tweak server sleep settings in-game.
public final class SeamlessSleepCommands {

    private SeamlessSleepCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("sleep")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("reload")
                                .executes(SeamlessSleepCommands::reload))
                        .then(Commands.literal("set")
                                .then(Commands.literal("sleepClearsWeather")
                                        .executes(SeamlessSleepCommands::getSleepWeatherClearChance)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setSleepClearsWeatherBoolean(
                                                        ctx,
                                                        BoolArgumentType.getBool(ctx, "value")
                                                )))
                                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> setSleepWeatherClearChance(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(ctx, "percent")
                                                ))))
                                .then(Commands.literal("sleepDurationMultiplier")
                                        .executes(SeamlessSleepCommands::getSleepDurationMultiplier)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.25D, 8.0D))
                                                .executes(ctx -> setSleepDurationMultiplier(
                                                        ctx,
                                                        DoubleArgumentType.getDouble(ctx, "value")
                                                )))))
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

    private static int getSleepWeatherClearChance(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.seamlesssleep.set.sleep_clears_weather.current",
                        formatPercentage(config.sleepWeatherClearChancePercent)
                ),
                false
        );
        return 1;
    }

    private static int setSleepClearsWeatherBoolean(CommandContext<CommandSourceStack> context, boolean value) {
        return setSleepWeatherClearChance(context, value ? 100 : 0);
    }

    private static int setSleepWeatherClearChance(CommandContext<CommandSourceStack> context, int value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.sleepWeatherClearChancePercent = value;
        config.clamp();
        SeamlessSleepServerConfigManager.save();
        ServerConfigSync.sendToAll(context.getSource().getServer(), config);

        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.seamlesssleep.set.sleep_clears_weather.updated",
                        formatPercentage(config.sleepWeatherClearChancePercent)
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

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatPercentage(int value) {
        return value + "%";
    }
}
