package net.aqualoco.sec.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.ServerConfigSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.network.chat.Component;

public final class SeamlessSleepCommands {

    private SeamlessSleepCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("seamlesssleep")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("reload")
                                .executes(SeamlessSleepCommands::reload))
                        .then(Commands.literal("set")
                                .then(Commands.literal("sleepClearsWeather")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setSleepClearsWeather(
                                                        ctx,
                                                        BoolArgumentType.getBool(ctx, "value")
                                                )))))
        );
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        SeamlessSleepServerConfigManager.ReloadResult result =
                SeamlessSleepServerConfigManager.reloadWithStatus();
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        ServerConfigSync.sendToAll(context.getSource().getServer(), config);

        Component message = switch (result) {
            case SUCCESS -> Component.literal("Server config reloaded.");
            case CREATED -> Component.literal("Config not found. Created default and reloaded.");
            case ERROR -> Component.literal("Failed to read config. Defaults loaded.");
        };
        context.getSource().sendSuccess(() -> message, true);
        return result == SeamlessSleepServerConfigManager.ReloadResult.ERROR ? 0 : 1;
    }

    private static int setSleepClearsWeather(CommandContext<CommandSourceStack> context, boolean value) {
        SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
        config.sleepClearsWeather = value;
        config.clamp();
        SeamlessSleepServerConfigManager.save();
        ServerConfigSync.sendToAll(context.getSource().getServer(), config);

        context.getSource().sendSuccess(
                () -> Component.literal("sleepClearsWeather = " + config.sleepClearsWeather),
                true
        );
        return 1;
    }
}
