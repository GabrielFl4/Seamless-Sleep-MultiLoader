package net.aqualoco.sec;

import net.aqualoco.sec.command.SeamlessSleepCommands;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

final class SeamlessSleepCommandRegistration {

    private SeamlessSleepCommandRegistration() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        SeamlessSleepCommands.register(dispatcher)
        );
    }
}
